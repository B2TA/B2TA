package com.b2ta.common.ai;

import com.b2ta.common.config.BedrockProperties;
import com.b2ta.common.entity.Criterion;
import com.b2ta.common.entity.PerformanceLevel;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Identifies passages in a submission that provide evidence for a rubric criterion
 * (Requirements 6.1-6.5, 6.9, 6.10).
 *
 * <p>Pipeline per criterion:
 *
 * <ol>
 *   <li>Chunk the extracted text ({@link TextChunker}).
 *   <li>Invoke Bedrock once per chunk, asking for offsets relative to that chunk.
 *   <li>Remap chunk-local offsets into the global offset space.
 *   <li>Drop anything violating the field invariants — out of bounds, wrong length, missing
 *       rationale, confidence outside 0..1.
 *   <li>Deduplicate and keep the top five ({@link MatchDeduplicator}).
 * </ol>
 *
 * <p>Asking for chunk-relative offsets and adding the chunk start here, rather than asking the model
 * to add a base offset itself, is deliberate: arithmetic on large offsets is exactly the kind of
 * thing a language model gets wrong occasionally, and a silently shifted offset would highlight the
 * wrong sentence with no visible error. The remapping is one addition in code and is verified by a
 * property test.
 *
 * <p>A chunk whose invocation fails does not fail the criterion: the matches found in the other
 * chunks are still returned. A criterion is only reported as unavailable when every chunk failed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchEngine {

    private static final String SYSTEM_PROMPT = """
            You are a rubric-matching assistant. Given a rubric criterion and a text chunk from a \
            student submission, identify passages that provide evidence for or against meeting this \
            criterion. Return structured JSON only.""";

    private static final int MAX_OUTPUT_TOKENS = 2048;

    private final TextChunker chunker;
    private final MatchDeduplicator deduplicator;
    private final BedrockJsonClient bedrock;
    private final BedrockProperties properties;

    /**
     * Analyses one submission against one criterion.
     *
     * @param criterion     the criterion, with its performance levels loaded
     * @param extractedText full extracted submission text; offsets in the result index into this
     * @return retained matches in ascending start-offset order, possibly empty
     * @throws BedrockUnavailableException when every chunk invocation failed
     */
    public List<CandidateMatch> findMatches(Criterion criterion, String extractedText) {
        if (extractedText == null || extractedText.isBlank()) {
            return List.of();
        }

        List<TextChunk> chunks = chunker.chunk(extractedText);
        List<CandidateMatch> candidates = new ArrayList<>();
        int failedChunks = 0;
        BedrockUnavailableException lastFailure = null;

        for (TextChunk chunk : chunks) {
            try {
                candidates.addAll(analyzeChunk(criterion, chunk, extractedText.length()));
            } catch (BedrockUnavailableException e) {
                failedChunks++;
                lastFailure = e;
                log.warn("Chunk {} of criterion {} could not be analysed: {}",
                        chunk.index(), criterion.getId(), e.getMessage());
            }
        }

        if (failedChunks == chunks.size() && !chunks.isEmpty()) {
            throw lastFailure;
        }
        return deduplicator.deduplicate(candidates);
    }

    private List<CandidateMatch> analyzeChunk(Criterion criterion, TextChunk chunk,
                                              int analyzedCharCount) {
        JsonNode result = bedrock.invokeStructured(
                properties.getMatchModelId(),
                SYSTEM_PROMPT,
                buildUserPrompt(criterion, chunk),
                responseSchema(),
                MAX_OUTPUT_TOKENS,
                Duration.ofSeconds(properties.getMatchTimeoutSeconds()));

        JsonNode matches = result.path("matches");
        if (!matches.isArray()) {
            return List.of();
        }

        List<CandidateMatch> parsed = new ArrayList<>();
        for (JsonNode node : matches) {
            CandidateMatch candidate = toCandidate(node, chunk);
            if (candidate == null) {
                continue;
            }
            if (!candidate.isValidFor(analyzedCharCount)) {
                // Recorded at debug rather than warn: the model returning an occasional
                // out-of-bounds or too-short range is expected and already handled by discarding it.
                log.debug("Discarded invalid candidate {}-{} for criterion {}",
                        candidate.start(), candidate.end(), criterion.getId());
                continue;
            }
            parsed.add(candidate);
        }
        return parsed;
    }

    /**
     * Converts one response element into a candidate in global offset space.
     *
     * @return null when a required field is absent or non-numeric
     */
    private CandidateMatch toCandidate(JsonNode node, TextChunk chunk) {
        JsonNode startNode = node.get("start_offset");
        JsonNode endNode = node.get("end_offset");
        if (startNode == null || endNode == null
                || !startNode.isNumber() || !endNode.isNumber()) {
            return null;
        }
        int localStart = startNode.asInt();
        int localEnd = endNode.asInt();

        // Clamp to the chunk before remapping. A model that overshoots the chunk end would
        // otherwise produce a global offset pointing into the following chunk's text, which is a
        // real range in the document but not the passage the rationale describes.
        localStart = Math.max(0, Math.min(localStart, chunk.length()));
        localEnd = Math.max(0, Math.min(localEnd, chunk.length()));
        if (localEnd <= localStart) {
            return null;
        }

        String rationale = node.path("rationale").asText("").trim();
        if (rationale.length() > CandidateMatch.MAX_RATIONALE_LENGTH) {
            rationale = rationale.substring(0, CandidateMatch.MAX_RATIONALE_LENGTH);
        }

        JsonNode confidenceNode = node.get("confidence");
        if (confidenceNode == null || !confidenceNode.isNumber()) {
            return null;
        }
        BigDecimal confidence = BigDecimal.valueOf(confidenceNode.asDouble())
                .setScale(2, RoundingMode.HALF_UP);
        if (confidence.compareTo(BigDecimal.ZERO) < 0) {
            confidence = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        } else if (confidence.compareTo(BigDecimal.ONE) > 0) {
            confidence = BigDecimal.ONE.setScale(2, RoundingMode.UNNECESSARY);
        }

        return new CandidateMatch(
                chunk.toGlobal(localStart),
                chunk.toGlobal(localEnd),
                rationale,
                confidence);
    }

    private String buildUserPrompt(Criterion criterion, TextChunk chunk) {
        StringBuilder levels = new StringBuilder();
        List<PerformanceLevel> performanceLevels = criterion.getPerformanceLevels();
        if (performanceLevels != null) {
            for (PerformanceLevel level : performanceLevels) {
                levels.append("- ").append(level.getLabel());
                if (level.getPoints() != null) {
                    levels.append(" (").append(level.getPoints()).append(" points)");
                }
                if (level.getDescription() != null && !level.getDescription().isBlank()) {
                    levels.append(": ").append(level.getDescription());
                }
                levels.append('\n');
            }
        }

        return """
                ## Criterion
                Title: %s
                Description: %s
                Performance Levels:
                %s
                ## Text Chunk
                The chunk below is an excerpt of a longer submission. Character offset 0 refers to \
                the first character of this chunk, not of the whole submission.

                %s

                ## Instructions
                Identify up to 5 passages (%d-%d characters each) in the chunk above that provide \
                evidence for or against this criterion. For each passage return:
                - start_offset: inclusive character offset within this chunk
                - end_offset: exclusive character offset within this chunk
                - rationale: 1-%d character explanation of why the passage is relevant
                - confidence: 0.00-1.00 assessment of match strength

                Offsets must satisfy 0 <= start_offset < end_offset <= %d. Return an empty matches \
                array if no passage in this chunk is relevant. Do not invent passages that are not \
                present in the text above."""
                .formatted(
                        criterion.getTitle(),
                        criterion.getDescription() == null ? "" : criterion.getDescription(),
                        levels.toString(),
                        chunk.text(),
                        CandidateMatch.MIN_PASSAGE_LENGTH,
                        CandidateMatch.MAX_PASSAGE_LENGTH,
                        CandidateMatch.MAX_RATIONALE_LENGTH,
                        chunk.length());
    }

    /** JSON schema the model's tool input must conform to (design: Match_Engine output schema). */
    private Map<String, Object> responseSchema() {
        Map<String, Object> matchProperties = new LinkedHashMap<>();
        matchProperties.put("start_offset", Map.of("type", "integer", "minimum", 0));
        matchProperties.put("end_offset", Map.of("type", "integer", "minimum", 1));
        matchProperties.put("rationale", Map.of(
                "type", "string",
                "minLength", 1,
                "maxLength", CandidateMatch.MAX_RATIONALE_LENGTH));
        matchProperties.put("confidence", Map.of("type", "number", "minimum", 0.0, "maximum", 1.0));

        Map<String, Object> matchItem = new LinkedHashMap<>();
        matchItem.put("type", "object");
        matchItem.put("properties", matchProperties);
        matchItem.put("required",
                List.of("start_offset", "end_offset", "rationale", "confidence"));

        Map<String, Object> matches = new LinkedHashMap<>();
        matches.put("type", "array");
        matches.put("items", matchItem);
        matches.put("maxItems", MatchDeduplicator.MAX_MATCHES_PER_CRITERION);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of("matches", matches));
        schema.put("required", List.of("matches"));
        return schema;
    }
}
