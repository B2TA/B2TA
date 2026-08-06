package com.b2ta.api.canvas;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Round-trips real PDF and DOCX bytes through the attachment extractors.
 *
 * <p>Documents are generated here rather than committed as binary fixtures, so the test
 * is self-contained and the expected text is visible next to the assertion. HW1 accepts
 * {@code online_upload}, so these formats are on the live path even though the demo
 * fixture currently only carries text entries.
 */
class SubmissionAttachmentExtractionTest {

    private static final List<String> PARAGRAPHS = List.of(
            "I will argue that platforms are not neutral conduits but active architects "
                    + "of epistemic bubbles.",
            "According to Pariser (2011), the filter bubble emerges from algorithmic "
                    + "curation that optimizes for engagement.",
            "In conclusion, the architecture of engagement is not value-neutral.");

    @Nested
    @DisplayName("PDF")
    class Pdf {

        @Test
        void extractsTextFromARealPdf() throws Exception {
            byte[] pdf = buildPdf(PARAGRAPHS);

            String text = SubmissionTextExtractor.PdfText.extract(pdf);

            // Raw PDF text carries the page's hard line wraps, so assert on
            // whitespace-insensitive content rather than exact phrasing.
            assertThat(text.replaceAll("\\s+", " "))
                    .contains("active architects of epistemic bubbles")
                    .contains("Pariser (2011)")
                    .contains("not value-neutral");
        }

        @Test
        void extractedPdfTextSurvivesNormalizationAndQuoteLocation() throws Exception {
            // The end-to-end concern: a quote located against the normalized form of
            // extracted PDF text must still resolve. PDF extraction re-wraps lines,
            // which is exactly what the locator is built to tolerate.
            byte[] pdf = buildPdf(PARAGRAPHS);
            String raw = SubmissionTextExtractor.PdfText.extract(pdf);

            var document = com.b2ta.api.analyze.NormalizedDocument.of(raw, null);
            var span = com.b2ta.api.analyze.EvidenceLocator.locate(
                    "active architects of epistemic bubbles", document.text());

            assertThat(span).isPresent();
            // The span may straddle a PDF line wrap, so it matches the quote modulo
            // whitespace — which is exactly what the locator is built to tolerate.
            assertThat(document.text().substring(span.get().start(), span.get().end()))
                    .isEqualToIgnoringWhitespace("active architects of epistemic bubbles");
        }

        @Test
        void recoversParagraphBreaksFromLineSpacing() throws Exception {
            // PDFBox emits one newline per line, losing the blank lines between
            // paragraphs. Without recovering them the essay collapses into a single
            // paragraph and every highlight offset is measured against that blob.
            byte[] pdf = buildPdf(PARAGRAPHS);

            String text = SubmissionTextExtractor.PdfText.extract(pdf);
            var document = com.b2ta.api.analyze.NormalizedDocument.of(text, null);

            assertThat(document.paragraphs()).hasSize(3);
            assertThat(document.paragraphs().get(1).text())
                    .containsIgnoringWhitespaces("Pariser (2011)");
        }

        @Test
        void failsOnBytesThatAreNotAPdf() {
            assertThatThrownBy(() -> SubmissionTextExtractor.PdfText.extract(
                    "this is not a PDF".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("DOCX")
    class Docx {

        @Test
        void extractsTextFromARealDocx() throws Exception {
            byte[] docx = buildDocx(PARAGRAPHS);

            String text = SubmissionTextExtractor.DocxText.extract(docx);

            assertThat(text).contains("active architects of epistemic bubbles");
            assertThat(text).contains("not value-neutral");
        }

        @Test
        void separatesDocxParagraphsSoTheySplitCorrectly() throws Exception {
            byte[] docx = buildDocx(PARAGRAPHS);

            String text = SubmissionTextExtractor.DocxText.extract(docx);
            var document = com.b2ta.api.analyze.NormalizedDocument.of(text, null);

            // Without blank-line separation every paragraph would collapse into one.
            assertThat(document.paragraphs()).hasSize(3);
        }

        @Test
        void failsOnBytesThatAreNotADocx() {
            assertThatThrownBy(() -> SubmissionTextExtractor.DocxText.extract(
                    "not a docx".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .isInstanceOf(Exception.class);
        }
    }

    // --- document builders ---

    private static byte[] buildPdf(List<String> paragraphs) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                content.setLeading(16f);
                content.newLineAtOffset(50, 720);
                for (String paragraph : paragraphs) {
                    // Wrap by hand: PDFBox does not, and a single long line would run
                    // off the page and change what the extractor reads back.
                    for (String line : wrap(paragraph, 80)) {
                        content.showText(line);
                        content.newLine();
                    }
                    content.newLine();
                }
                content.endText();
            }

            document.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] buildDocx(List<String> paragraphs) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String paragraph : paragraphs) {
                document.createParagraph().createRun().setText(paragraph);
            }
            document.write(out);
            return out.toByteArray();
        }
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() + word.length() + 1 > width) {
                lines.add(line.toString());
                line = new StringBuilder();
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }
}
