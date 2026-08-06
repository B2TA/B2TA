package com.b2ta.api.analyze;

import com.b2ta.api.canvas.dto.CanvasCriterionView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import software.amazon.awssdk.services.bedrockruntime.model.SpecificToolChoice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cross-references a submission against a rubric using Amazon Bedrock.
 *
 * <p>Uses the Converse API with a <em>forced</em> tool choice, so the model must return
 * structured output through the tool schema rather than prose we would have to parse.
 *
 * <p>The model returns quotes only — never offsets. It cannot count characters
 * reliably, so asking it to try invites silent misalignment. Offsets are computed here
 * by locating each quote in the real document.
 */
@Service
@Slf4j
public class BedrockAnalyzer {

    private static final String TOOL_NAME = "record_rubric_analysis";

    private static final String SYSTEM_PROMPT = """
            You are helping a teaching assistant grade an essay against a rubric.

            For each rubric criterion, judge how well the submission meets it and \
            support that judgment with evidence quoted from the submission.

            Rules you must follow:
            - Quote evidence VERBATIM from the submission. Copy the text exactly as it \
              appears. Do not paraphrase, correct, summarize, or join separated passages.
            - If the submission contains no evidence for a criterion, return an empty \
              evidence list for it. An empty list is correct and expected; inventing a \
              quote is not.
            - Do not report character offsets or positions. Quote the text only.
            - Keep each rationale to one sentence addressed to the teaching assistant.
            - You are proposing, not deciding. The teaching assistant makes the final call.
            """;

    private final BedrockRuntimeClient bedrockClient;
    private final AnalyzeProperties properties;
    private final ObjectMapper objectMapper;

    public BedrockAnalyzer(BedrockRuntimeClient bedrockClient,
                           AnalyzeProperties properties,
                           ObjectMapper objectMapper) {
        this.bedrockClient = bedrockClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Analyzes one submission against one rubric.
     *
     * @return the verified analysis, or empty when analysis is disabled, the document is
     * empty, or Bedrock could not be reached — callers degrade to manual grading rather
     * than failing the whole submission
     */
    public Optional<AnalysisResult> analyze(NormalizedDocument document,
                                            List<CanvasCriterionView> criteria) {
        if (!properties.isEnabled()) {
            log.debug("Analysis disabled; serving submission without suggested matches.");
            return Optional.empty();
        }
        if (document.isEmpty() || criteria.isEmpty()) {
            return Optional.empty();
        }

        JsonNode raw;
        try {
            raw = invokeWithRetry(document, criteria);
        } catch (Exception e) {
            // A failed analysis must not block grading — the TA can still score manually.
            log.warn("Bedrock analysis failed; serving submission without suggested matches", e);
            return Optional.empty();
        }
        if (raw == null) {
            return Optional.empty();
        }

        return Optional.of(verify(raw, document, criteria));
    }

    /**
     * Calls Converse with a forced tool choice, retrying on throttling.
     */
    private JsonNode invokeWithRetry(NormalizedDocument document,
                                     List<CanvasCriterionView> criteria) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt <= properties.getMaxRetries(); attempt++) {
            try {
                return invoke(document, criteria);
            } catch (ThrottlingException e) {
                last = e;
                long backoffMillis = (long) Math.pow(2, attempt) * 1000L;
                log.warn("Bedrock throttled (attempt {}); retrying in {}ms",
                        attempt + 1, backoffMillis);
                Thread.sleep(backoffMillis);
            }
        }
        throw last;
    }

    private JsonNode invoke(NormalizedDocument document, List<CanvasCriterionView> criteria)
            throws Exception {

        ConverseRequest request = ConverseRequest.builder()
                .modelId(properties.getModelId())
                .system(SystemContentBlock.fromText(SYSTEM_PROMPT))
                .messages(Message.builder()
                        .role(ConversationRole.USER)
                        .content(ContentBlock.fromText(buildPrompt(document, criteria)))
                        .build())
                .toolConfig(ToolConfiguration.builder()
                        .tools(Tool.fromToolSpec(ToolSpecification.builder()
                                .name(TOOL_NAME)
                                .description("Record the per-criterion analysis of this submission.")
                                .inputSchema(ToolInputSchema.fromJson(buildSchema(criteria)))
                                .build()))
                        // Forcing the tool is what makes the output structured — without
                        // it the model may answer in prose and there is nothing to parse.
                        .toolChoice(ToolChoice.fromTool(
                                SpecificToolChoice.builder().name(TOOL_NAME).build()))
                        .build())
                .inferenceConfig(InferenceConfiguration.builder()
                        .maxTokens(properties.getMaxTokens())
                        .build())
                .build();

        ConverseResponse response = bedrockClient.converse(request);

        // ContentBlock is a union; toolUse() is null on every other variant.
        Optional<ToolUseBlock> toolUse = response.output().message().content().stream()
                .map(ContentBlock::toolUse)
                .filter(java.util.Objects::nonNull)
                .findFirst();

        if (toolUse.isEmpty()) {
            log.warn("Bedrock returned no tool_use block despite a forced tool choice");
            return null;
        }

        return objectMapper.readTree(toolUse.get().input().toString());
    }

    private String buildPrompt(NormalizedDocument document, List<CanvasCriterionView> criteria) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("# Rubric\n\n");
        for (CanvasCriterionView criterion : criteria) {
            prompt.append("## ").append(criterion.getLabel())
                    .append(" (id: ").append(criterion.getId())
                    .append(", max ").append(criterion.getMaxPts()).append(" points)\n");
            if (criterion.getDescription() != null) {
                prompt.append(criterion.getDescription()).append("\n");
            }
            if (criterion.getLevels() != null && !criterion.getLevels().isEmpty()) {
                prompt.append("Levels:\n");
                criterion.getLevels().forEach(level -> prompt
                        .append("  - ").append(level.getPts()).append(" — ")
                        .append(level.getLabel())
                        .append(level.getDesc() == null ? "" : ": " + level.getDesc())
                        .append("\n"));
            }
            prompt.append("\n");
        }

        prompt.append("# Submission\n\n");
        for (NormalizedDocument.Paragraph paragraph : document.paragraphs()) {
            if (paragraph.label() != null) {
                prompt.append("[").append(paragraph.label()).append("] ");
            }
            prompt.append(paragraph.text()).append("\n\n");
        }

        prompt.append("Analyze the submission against every criterion above and record "
                + "your analysis using the ").append(TOOL_NAME).append(" tool.");
        return prompt.toString();
    }

    /**
     * Builds the tool input schema. Criterion ids are pinned to an enum so the model
     * cannot invent one — an unrecognized id would be silently dropped downstream.
     */
    private Document buildSchema(List<CanvasCriterionView> criteria) {
        List<Document> criterionIds = criteria.stream()
                .map(c -> Document.fromString(c.getId()))
                .toList();

        Map<String, Document> evidenceItem = new LinkedHashMap<>();
        evidenceItem.put("type", Document.fromString("object"));
        evidenceItem.put("properties", Document.fromMap(Map.of(
                "quote", Document.fromMap(Map.of(
                        "type", Document.fromString("string"),
                        "description", Document.fromString(
                                "A verbatim span copied exactly from the submission."))))));
        evidenceItem.put("required", Document.fromList(List.of(Document.fromString("quote"))));

        Map<String, Document> criterionProps = new LinkedHashMap<>();
        criterionProps.put("criterion_id", Document.fromMap(Map.of(
                "type", Document.fromString("string"),
                "enum", Document.fromList(criterionIds))));
        criterionProps.put("suggested_points", Document.fromMap(Map.of(
                "type", Document.fromString("number"))));
        criterionProps.put("confidence", Document.fromMap(Map.of(
                "type", Document.fromString("number"),
                "description", Document.fromString("Between 0 and 1."))));
        criterionProps.put("rationale", Document.fromMap(Map.of(
                "type", Document.fromString("string"),
                "description", Document.fromString("One sentence for the teaching assistant."))));
        criterionProps.put("flag", Document.fromMap(Map.of(
                "type", Document.fromString("string"),
                "enum", Document.fromList(List.of(
                        Document.fromString("none"),
                        Document.fromString("missing"),
                        Document.fromString("possible_misconception"))))));
        criterionProps.put("evidence", Document.fromMap(Map.of(
                "type", Document.fromString("array"),
                "items", Document.fromMap(evidenceItem))));

        Map<String, Document> criterionItem = new LinkedHashMap<>();
        criterionItem.put("type", Document.fromString("object"));
        criterionItem.put("properties", Document.fromMap(criterionProps));
        criterionItem.put("required", Document.fromList(List.of(
                Document.fromString("criterion_id"),
                Document.fromString("suggested_points"),
                Document.fromString("confidence"),
                Document.fromString("rationale"),
                Document.fromString("evidence"))));

        Map<String, Document> root = new LinkedHashMap<>();
        root.put("type", Document.fromString("object"));
        root.put("properties", Document.fromMap(Map.of(
                "criteria", Document.fromMap(Map.of(
                        "type", Document.fromString("array"),
                        "items", Document.fromMap(criterionItem))),
                "overall_note", Document.fromMap(Map.of(
                        "type", Document.fromString("string"),
                        "description", Document.fromString(
                                "Draft feedback comment for the student."))))));
        root.put("required", Document.fromList(List.of(Document.fromString("criteria"))));

        return Document.fromMap(root);
    }

    /**
     * Verifies every model-supplied quote against the document, discards the ones that
     * cannot be located, and converts the survivors to paragraph-relative offsets.
     */
    AnalysisResult verify(JsonNode raw,
                          NormalizedDocument document,
                          List<CanvasCriterionView> criteria) {

        java.util.Set<String> knownIds = criteria.stream()
                .map(CanvasCriterionView::getId)
                .collect(java.util.stream.Collectors.toSet());

        List<AnalysisResult.CriterionAnalysis> analyses = new ArrayList<>();
        int dropped = 0;
        int proposed = 0;
        int spanCounter = 0;

        for (JsonNode node : raw.path("criteria")) {
            String criterionId = node.path("criterion_id").asText(null);
            if (criterionId == null || !knownIds.contains(criterionId)) {
                log.warn("Discarding analysis for unknown criterion id");
                continue;
            }

            List<AnalysisResult.VerifiedSpan> verified = new ArrayList<>();
            String rationale = node.path("rationale").asText("");

            for (JsonNode evidenceNode : node.path("evidence")) {
                proposed++;
                String quote = evidenceNode.path("quote").asText(null);

                Optional<EvidenceLocator.Span> span =
                        EvidenceLocator.locate(quote, document.text());
                if (span.isEmpty()) {
                    // The model produced text that is not in the submission. Drop it —
                    // this is the guarantee that a TA never sees a fabricated quotation.
                    dropped++;
                    continue;
                }

                NormalizedDocument.ParagraphOffset offset =
                        document.toParagraphOffset(span.get().start());
                if (offset == null) {
                    dropped++;
                    continue;
                }

                verified.add(AnalysisResult.VerifiedSpan.builder()
                        .id("h" + (++spanCounter))
                        .criterionId(criterionId)
                        // Store the document's text, not the model's transcription of it.
                        .text(document.text().substring(span.get().start(), span.get().end()))
                        .confirmed(false)
                        .tooltip(rationale)
                        .paragraphIdx(offset.paragraphIdx())
                        .offsetInParagraph(offset.offsetInParagraph())
                        .build());
            }

            analyses.add(AnalysisResult.CriterionAnalysis.builder()
                    .criterionId(criterionId)
                    .suggestedPoints(node.has("suggested_points")
                            ? node.get("suggested_points").asDouble() : null)
                    .confidence(node.has("confidence")
                            ? clamp(node.get("confidence").asDouble()) : null)
                    .rationale(rationale)
                    .flag(node.path("flag").asText("none"))
                    .evidence(verified)
                    .build());
        }

        if (dropped > 0) {
            // Worth watching: a rising drop rate means the model is fabricating quotes.
            log.info("Evidence verification dropped {} of {} proposed spans", dropped, proposed);
        }

        return AnalysisResult.builder()
                .criteria(analyses)
                .overallNote(raw.path("overall_note").asText(null))
                .droppedSpanCount(dropped)
                .proposedSpanCount(proposed)
                .build();
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
