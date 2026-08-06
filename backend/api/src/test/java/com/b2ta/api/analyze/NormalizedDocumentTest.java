package com.b2ta.api.analyze;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizedDocumentTest {

    private static final String RAW = """
            The question of whether platforms bear moral responsibility has moved
            into courtrooms.

            According to Pariser (2011), the filter bubble emerges from curation.

            In conclusion, the architecture of engagement is not value-neutral.""";

    @Nested
    @DisplayName("Paragraph splitting")
    class Splitting {

        @Test
        void splitsOnBlankLinesAndLabelsBodyParagraphs() {
            NormalizedDocument doc = NormalizedDocument.of(RAW, null);

            assertThat(doc.paragraphs()).hasSize(3);
            assertThat(doc.paragraphs()).extracting(NormalizedDocument.Paragraph::label)
                    .containsExactly("¶1", "¶2", "¶3");
        }

        @Test
        void rendersTheTitleAsParagraphZero() {
            NormalizedDocument doc = NormalizedDocument.of(RAW, "Essay 3: Argumentative Analysis");

            assertThat(doc.paragraphs()).hasSize(4);
            assertThat(doc.paragraphs().get(0).isTitle()).isTrue();
            assertThat(doc.paragraphs().get(0).label()).isNull();
            assertThat(doc.paragraphs().get(0).text()).isEqualTo("Essay 3: Argumentative Analysis");
            // Body numbering restarts at 1 regardless of the title occupying index 0.
            assertThat(doc.paragraphs().get(1).label()).isEqualTo("¶1");
        }

        @Test
        void dropsEmptyParagraphs() {
            NormalizedDocument doc = NormalizedDocument.of("one\n\n\n\n\ntwo", null);

            assertThat(doc.paragraphs()).hasSize(2);
        }

        @Test
        void handlesEmptyInput() {
            NormalizedDocument doc = NormalizedDocument.of("", null);

            assertThat(doc.isEmpty()).isTrue();
            assertThat(doc.paragraphs()).isEmpty();
        }

        @Test
        void handlesNullInput() {
            assertThat(NormalizedDocument.of(null, null).isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("Normalization")
    class Normalization {

        @Test
        void foldsLigaturesSoQuotesMatch() {
            // A PDF extractor emits ligatures; the model quotes plain ASCII. Without
            // folding, every quote containing "fi" fails to locate.
            assertThat(NormalizedDocument.normalize("The ﬁnal draft was diﬃcult."))
                    .isEqualTo("The final draft was difficult.");
        }

        @Test
        void stripsSoftHyphensAndBom() {
            assertThat(NormalizedDocument.normalize("﻿con­clusion"))
                    .isEqualTo("conclusion");
        }

        @Test
        void foldsSmartPunctuationToAscii() {
            assertThat(NormalizedDocument.normalize("“the author’s point” — clear"))
                    .isEqualTo("\"the author's point\" - clear");
        }

        @Test
        void unifiesLineEndings() {
            assertThat(NormalizedDocument.normalize("a\r\nb\rc")).isEqualTo("a\nb\nc");
        }
    }

    @Nested
    @DisplayName("Offset conversion")
    class Offsets {

        @Test
        void mapsAnOffsetToTheRightParagraph() {
            NormalizedDocument doc = NormalizedDocument.of(RAW, null);
            int absolute = doc.text().indexOf("Pariser");

            NormalizedDocument.ParagraphOffset offset = doc.toParagraphOffset(absolute);

            assertThat(offset.paragraphIdx()).isEqualTo(1);
            assertThat(doc.paragraphs().get(1).text().substring(offset.offsetInParagraph()))
                    .startsWith("Pariser");
        }

        @Test
        void mapsAnOffsetInTheFirstParagraph() {
            NormalizedDocument doc = NormalizedDocument.of(RAW, null);

            NormalizedDocument.ParagraphOffset offset = doc.toParagraphOffset(0);

            assertThat(offset.paragraphIdx()).isZero();
            assertThat(offset.offsetInParagraph()).isZero();
        }

        @Test
        void accountsForTheTitleShiftingBodyOffsets() {
            NormalizedDocument doc = NormalizedDocument.of(RAW, "A Title");
            int absolute = doc.text().indexOf("Pariser");

            NormalizedDocument.ParagraphOffset offset = doc.toParagraphOffset(absolute);

            // Title occupies index 0, so the second body paragraph is index 2.
            assertThat(offset.paragraphIdx()).isEqualTo(2);
            assertThat(doc.paragraphs().get(2).text().substring(offset.offsetInParagraph()))
                    .startsWith("Pariser");
        }

        @Test
        void rejectsOutOfRangeOffsets() {
            NormalizedDocument doc = NormalizedDocument.of(RAW, null);

            assertThat(doc.toParagraphOffset(-1)).isNull();
            assertThat(doc.toParagraphOffset(doc.length() + 1)).isNull();
        }
    }

    /**
     * The invariant the whole highlighting mechanic rests on: an absolute offset mapped
     * to (paragraph, offset) and read back must yield the same text. Getting this wrong
     * shifts every highlight in the essay.
     */
    @Property(tries = 500)
    void absoluteOffsetRoundTripsThroughParagraphCoordinates(
            @ForAll @IntRange(min = 0, max = 200) int rawOffset) {

        NormalizedDocument doc = NormalizedDocument.of(RAW, "A Title");
        int absolute = Math.min(rawOffset, doc.length() - 1);
        if (absolute < 0) {
            return;
        }

        NormalizedDocument.ParagraphOffset offset = doc.toParagraphOffset(absolute);
        if (offset == null) {
            // Offset landed inside a paragraph separator — no paragraph owns it.
            return;
        }

        NormalizedDocument.Paragraph paragraph = doc.paragraphs().get(offset.paragraphIdx());
        if (offset.offsetInParagraph() >= paragraph.text().length()) {
            return;
        }

        assertThat(paragraph.text().charAt(offset.offsetInParagraph()))
                .as("char at absolute offset %d", absolute)
                .isEqualTo(doc.text().charAt(absolute));
    }
}
