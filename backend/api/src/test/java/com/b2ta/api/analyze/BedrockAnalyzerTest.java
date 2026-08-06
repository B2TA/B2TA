package com.b2ta.api.analyze;

import com.b2ta.api.canvas.dto.CanvasCriterionView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the verification stage — the step that decides what a TA is allowed to see.
 * The Bedrock call itself is not exercised here; {@link BedrockAnalyzer#verify} is
 * package-private precisely so this logic can be tested without a live model.
 */
@ExtendWith(MockitoExtension.class)
class BedrockAnalyzerTest {

    @Mock
    private BedrockRuntimeClient bedrockClient;

    private BedrockAnalyzer analyzer;
    private ObjectMapper objectMapper;
    private NormalizedDocument document;
    private List<CanvasCriterionView> criteria;

    private static final String RAW = """
            I will argue that platforms are not neutral conduits but active architects \
            of epistemic bubbles.

            According to Pariser (2011), the filter bubble emerges from algorithmic \
            curation that optimizes for engagement.""";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        analyzer = new BedrockAnalyzer(bedrockClient, new AnalyzeProperties(), objectMapper);
        document = NormalizedDocument.of(RAW, null);
        criteria = List.of(
                CanvasCriterionView.builder().id("_1838").label("Thesis Clarity").maxPts(5.0).build(),
                CanvasCriterionView.builder().id("_7746").label("Use of Evidence").maxPts(5.0).build());
    }

    @Nested
    @DisplayName("Discarding fabricated evidence")
    class Fabricated {

        @Test
        void dropsAQuoteThatIsNotInTheSubmission() throws Exception {
            JsonNode raw = json("""
                    {"criteria": [
                      {"criterion_id": "_1838", "suggested_points": 5, "confidence": 0.9,
                       "rationale": "Clear thesis.",
                       "evidence": [{"quote": "Platforms are solely to blame for everything."}]}
                    ]}""");

            AnalysisResult result = analyzer.verify(raw, document, criteria);

            assertThat(result.getCriteria().get(0).getEvidence()).isEmpty();
            assertThat(result.getDroppedSpanCount()).isEqualTo(1);
            assertThat(result.getProposedSpanCount()).isEqualTo(1);
        }

        @Test
        void keepsRealEvidenceWhileDroppingFabricatedEvidenceInTheSameCriterion() throws Exception {
            JsonNode raw = json("""
                    {"criteria": [
                      {"criterion_id": "_1838", "suggested_points": 4, "confidence": 0.8,
                       "rationale": "Mixed.",
                       "evidence": [
                         {"quote": "active architects of epistemic bubbles"},
                         {"quote": "a sentence the student never wrote at all"}]}
                    ]}""");

            AnalysisResult result = analyzer.verify(raw, document, criteria);

            assertThat(result.getCriteria().get(0).getEvidence()).hasSize(1);
            assertThat(result.getDroppedSpanCount()).isEqualTo(1);
            assertThat(result.getProposedSpanCount()).isEqualTo(2);
        }

        @Test
        void stillReportsTheCriterionWhenAllItsEvidenceIsDropped() throws Exception {
            // The criterion must survive so the UI can render the
            // "No matching passage found — flag manually" state rather than an
            // unsupported score silently vanishing.
            JsonNode raw = json("""
                    {"criteria": [
                      {"criterion_id": "_1838", "suggested_points": 3, "confidence": 0.5,
                       "rationale": "Weak.",
                       "evidence": [{"quote": "invented"}, {"quote": "also invented"}]}
                    ]}""");

            AnalysisResult result = analyzer.verify(raw, document, criteria);

            assertThat(result.getCriteria()).hasSize(1);
            assertThat(result.getCriteria().get(0).getSuggestedPoints()).isEqualTo(3.0);
            assertThat(result.getCriteria().get(0).getEvidence()).isEmpty();
        }

        @Test
        void discardsAnalysisForACriterionNotOnTheRubric() throws Exception {
            JsonNode raw = json("""
                    {"criteria": [
                      {"criterion_id": "_NOPE", "suggested_points": 5, "confidence": 0.9,
                       "rationale": "Invented criterion.", "evidence": []}
                    ]}""");

            assertThat(analyzer.verify(raw, document, criteria).getCriteria()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Verified spans")
    class Verified {

        @Test
        void storesTheDocumentTextNotTheModelTranscription() throws Exception {
            // The model re-wrapped the quote across lines. What gets stored must be the
            // document's own text, so the rendered highlight matches the essay exactly.
            JsonNode raw = json("""
                    {"criteria": [
                      {"criterion_id": "_1838", "suggested_points": 5, "confidence": 0.9,
                       "rationale": "Clear thesis.",
                       "evidence": [{"quote": "active   architects\\nof epistemic bubbles"}]}
                    ]}""");

            AnalysisResult result = analyzer.verify(raw, document, criteria);

            assertThat(result.getCriteria().get(0).getEvidence().get(0).getText())
                    .isEqualTo("active architects of epistemic bubbles");
        }

        @Test
        void spanTextMatchesTheParagraphAtItsStatedOffset() throws Exception {
            // The end-to-end invariant: every rendered highlight's text equals the
            // substring at its stated position.
            JsonNode raw = json("""
                    {"criteria": [
                      {"criterion_id": "_7746", "suggested_points": 4, "confidence": 0.7,
                       "rationale": "Cited.",
                       "evidence": [{"quote": "algorithmic curation that optimizes for engagement"}]}
                    ]}""");

            AnalysisResult result = analyzer.verify(raw, document, criteria);
            AnalysisResult.VerifiedSpan span = result.getCriteria().get(0).getEvidence().get(0);

            String paragraphText = document.paragraphs().get(span.getParagraphIdx()).text();
            assertThat(paragraphText.substring(
                    span.getOffsetInParagraph(),
                    span.getOffsetInParagraph() + span.getText().length()))
                    .isEqualTo(span.getText());
        }

        @Test
        void marksEverySpanUnconfirmed() throws Exception {
            // The AI proposes; the TA disposes. Nothing arrives pre-confirmed.
            JsonNode raw = json("""
                    {"criteria": [
                      {"criterion_id": "_1838", "suggested_points": 5, "confidence": 1.0,
                       "rationale": "Certain.",
                       "evidence": [{"quote": "active architects of epistemic bubbles"}]}
                    ]}""");

            AnalysisResult result = analyzer.verify(raw, document, criteria);

            assertThat(result.getCriteria().get(0).getEvidence())
                    .allMatch(span -> !span.isConfirmed());
        }

        @Test
        void ignoresAnyOffsetsTheModelSupplied() throws Exception {
            // The model cannot count characters; its offsets must never be trusted.
            JsonNode raw = json("""
                    {"criteria": [
                      {"criterion_id": "_1838", "suggested_points": 5, "confidence": 0.9,
                       "rationale": "Clear.",
                       "evidence": [{"quote": "active architects of epistemic bubbles",
                                     "paragraph_idx": 99, "offset_in_paragraph": 12345}]}
                    ]}""");

            AnalysisResult result = analyzer.verify(raw, document, criteria);
            AnalysisResult.VerifiedSpan span = result.getCriteria().get(0).getEvidence().get(0);

            assertThat(span.getParagraphIdx()).isZero();
            assertThat(span.getOffsetInParagraph()).isNotEqualTo(12345);
        }

        @Test
        void assignsUniqueSpanIds() throws Exception {
            JsonNode raw = json("""
                    {"criteria": [
                      {"criterion_id": "_1838", "suggested_points": 5, "confidence": 0.9,
                       "rationale": "A.",
                       "evidence": [{"quote": "active architects of epistemic bubbles"}]},
                      {"criterion_id": "_7746", "suggested_points": 4, "confidence": 0.7,
                       "rationale": "B.",
                       "evidence": [{"quote": "algorithmic curation"}]}
                    ]}""");

            AnalysisResult result = analyzer.verify(raw, document, criteria);

            assertThat(result.getCriteria())
                    .flatExtracting(AnalysisResult.CriterionAnalysis::getEvidence)
                    .extracting(AnalysisResult.VerifiedSpan::getId)
                    .doesNotHaveDuplicates();
        }
    }

    @Nested
    @DisplayName("Field handling")
    class Fields {

        @Test
        void clampsConfidenceIntoRange() throws Exception {
            JsonNode raw = json("""
                    {"criteria": [
                      {"criterion_id": "_1838", "suggested_points": 5, "confidence": 4.2,
                       "rationale": "Overconfident.", "evidence": []}
                    ]}""");

            assertThat(analyzer.verify(raw, document, criteria)
                    .getCriteria().get(0).getConfidence()).isEqualTo(1.0);
        }

        @Test
        void capturesTheOverallNote() throws Exception {
            JsonNode raw = json("""
                    {"criteria": [],
                     "overall_note": "Strong argument, thin evidence in the third paragraph."}""");

            assertThat(analyzer.verify(raw, document, criteria).getOverallNote())
                    .isEqualTo("Strong argument, thin evidence in the third paragraph.");
        }

        @Test
        void defaultsFlagToNoneWhenAbsent() throws Exception {
            JsonNode raw = json("""
                    {"criteria": [
                      {"criterion_id": "_1838", "suggested_points": 5, "confidence": 0.9,
                       "rationale": "Clear.", "evidence": []}
                    ]}""");

            assertThat(analyzer.verify(raw, document, criteria)
                    .getCriteria().get(0).getFlag()).isEqualTo("none");
        }

        @Test
        void handlesAnEmptyCriteriaArray() throws Exception {
            AnalysisResult result = analyzer.verify(json("{\"criteria\": []}"), document, criteria);

            assertThat(result.getCriteria()).isEmpty();
            assertThat(result.getDroppedSpanCount()).isZero();
        }
    }

    @Nested
    @DisplayName("Degrading safely")
    class Degrading {

        @Test
        void returnsEmptyWhenAnalysisIsDisabled() {
            AnalyzeProperties disabled = new AnalyzeProperties();
            disabled.setEnabled(false);
            BedrockAnalyzer off = new BedrockAnalyzer(bedrockClient, disabled, objectMapper);

            assertThat(off.analyze(document, criteria)).isEmpty();
        }

        @Test
        void returnsEmptyForAnEmptyDocument() {
            assertThat(analyzer.analyze(NormalizedDocument.of("", null), criteria)).isEmpty();
        }

        @Test
        void returnsEmptyWhenTheRubricHasNoCriteria() {
            assertThat(analyzer.analyze(document, List.of())).isEmpty();
        }
    }

    private JsonNode json(String value) throws Exception {
        return objectMapper.readTree(value);
    }
}
