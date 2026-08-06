package com.b2ta.worker.extraction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TextExtractor}.
 * Validates Requirements 4.5, 4.7, 4.8, 4.12, 4.15, 5.8.
 */
class TextExtractorTest {

    private TextExtractor textExtractor;

    @BeforeEach
    void setUp() {
        textExtractor = new TextExtractor(
                new PdfTextExtractor(),
                new DocxTextExtractor(),
                new PlainTextExtractor()
        );
    }

    @Nested
    @DisplayName("Plain text extraction (TXT/MD)")
    class PlainTextTests {

        @Test
        @DisplayName("extracts single paragraph from TXT file")
        void singleParagraph() {
            String content = "Hello, this is a simple test paragraph.";
            InputStream is = toStream(content);

            ExtractionResult result = textExtractor.extract(is, "test.txt");

            assertTrue(result.isSuccess());
            assertEquals(content, result.getExtractedText());
            assertEquals(content.length(), result.getCharCount());
            assertFalse(result.isOversized());
            assertNull(result.getFailureReason());
            assertEquals(1, result.getTextRuns().size());
            assertEquals(0, result.getTextRuns().get(0).getStart());
            assertEquals(content.length(), result.getTextRuns().get(0).getEnd());
        }

        @Test
        @DisplayName("extracts multiple paragraphs from TXT file")
        void multipleParagraphs() {
            String content = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph.";
            InputStream is = toStream(content);

            ExtractionResult result = textExtractor.extract(is, "test.txt");

            assertTrue(result.isSuccess());
            assertEquals(content, result.getExtractedText());
            assertEquals(3, result.getTextRuns().size());

            // Verify runs are ascending, non-overlapping, and start < end
            for (int i = 0; i < result.getTextRuns().size(); i++) {
                ExtractionResult.TextRun run = result.getTextRuns().get(i);
                assertTrue(run.getStart() < run.getEnd(),
                        "Run " + i + ": start must be < end");
                if (i > 0) {
                    assertTrue(run.getStart() >= result.getTextRuns().get(i - 1).getEnd(),
                            "Run " + i + ": must not overlap with previous run");
                }
            }
        }

        @Test
        @DisplayName("handles MD files the same as TXT")
        void markdownFile() {
            String content = "# Title\n\nSome content here.";
            InputStream is = toStream(content);

            ExtractionResult result = textExtractor.extract(is, "README.md");

            assertTrue(result.isSuccess());
            assertEquals(content, result.getExtractedText());
        }

        @Test
        @DisplayName("fails with NO_EXTRACTABLE_TEXT for blank file")
        void blankFile() {
            String content = "   \n\n   \t  ";
            InputStream is = toStream(content);

            ExtractionResult result = textExtractor.extract(is, "empty.txt");

            assertFalse(result.isSuccess());
            assertEquals(ExtractionFailureReason.NO_EXTRACTABLE_TEXT, result.getFailureReason());
        }

        @Test
        @DisplayName("flags oversized submissions exceeding 100,000 chars")
        void oversizedFile() {
            String content = "A".repeat(100_001);
            InputStream is = toStream(content);

            ExtractionResult result = textExtractor.extract(is, "large.txt");

            assertTrue(result.isSuccess());
            assertTrue(result.isOversized());
            assertEquals(100_001, result.getCharCount());
        }

        @Test
        @DisplayName("does not flag submissions at exactly 100,000 chars as oversized")
        void exactlyAtThreshold() {
            String content = "B".repeat(100_000);
            InputStream is = toStream(content);

            ExtractionResult result = textExtractor.extract(is, "borderline.txt");

            assertTrue(result.isSuccess());
            assertFalse(result.isOversized());
            assertEquals(100_000, result.getCharCount());
        }

        @Test
        @DisplayName("single newlines within paragraph are kept in same run")
        void singleNewlinesKeptInRun() {
            String content = "Line one\nLine two\nLine three";
            InputStream is = toStream(content);

            ExtractionResult result = textExtractor.extract(is, "test.txt");

            assertTrue(result.isSuccess());
            // Single newlines don't break paragraphs, so 1 run
            assertEquals(1, result.getTextRuns().size());
            assertEquals(0, result.getTextRuns().get(0).getStart());
            assertEquals(content.length(), result.getTextRuns().get(0).getEnd());
        }
    }

    @Nested
    @DisplayName("Extension routing")
    class ExtensionRouting {

        @Test
        @DisplayName("routes .txt to PlainTextExtractor")
        void routesTxt() {
            ExtractionResult result = textExtractor.extract(toStream("hello"), "file.txt");
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("routes .md to PlainTextExtractor")
        void routesMd() {
            ExtractionResult result = textExtractor.extract(toStream("hello"), "file.md");
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("routes .TXT (case-insensitive) to PlainTextExtractor")
        void routesCaseInsensitive() {
            ExtractionResult result = textExtractor.extract(toStream("hello"), "FILE.TXT");
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("returns UNREADABLE_FILE for unsupported extension")
        void unsupportedExtension() {
            ExtractionResult result = textExtractor.extract(toStream("data"), "file.xyz");
            assertFalse(result.isSuccess());
            assertEquals(ExtractionFailureReason.UNREADABLE_FILE, result.getFailureReason());
        }

        @Test
        @DisplayName("returns UNREADABLE_FILE for file with no extension")
        void noExtension() {
            ExtractionResult result = textExtractor.extract(toStream("data"), "file");
            assertFalse(result.isSuccess());
            assertEquals(ExtractionFailureReason.UNREADABLE_FILE, result.getFailureReason());
        }
    }

    @Nested
    @DisplayName("Text runs invariants (Requirement 4.5)")
    class TextRunInvariants {

        @Test
        @DisplayName("all runs have start < end")
        void startLessThanEnd() {
            String content = "Para 1\n\nPara 2\n\nPara 3";
            ExtractionResult result = textExtractor.extract(toStream(content), "test.txt");

            assertTrue(result.isSuccess());
            for (ExtractionResult.TextRun run : result.getTextRuns()) {
                assertTrue(run.getStart() < run.getEnd(),
                        "start=" + run.getStart() + " must be < end=" + run.getEnd());
            }
        }

        @Test
        @DisplayName("runs are in ascending start offset order")
        void ascendingOrder() {
            String content = "Alpha\n\nBeta\n\nGamma\n\nDelta";
            ExtractionResult result = textExtractor.extract(toStream(content), "test.md");

            assertTrue(result.isSuccess());
            for (int i = 1; i < result.getTextRuns().size(); i++) {
                assertTrue(
                        result.getTextRuns().get(i).getStart() > result.getTextRuns().get(i - 1).getStart(),
                        "Runs must be in ascending order");
            }
        }

        @Test
        @DisplayName("runs do not overlap")
        void nonOverlapping() {
            String content = "First\n\nSecond\n\nThird";
            ExtractionResult result = textExtractor.extract(toStream(content), "test.txt");

            assertTrue(result.isSuccess());
            for (int i = 1; i < result.getTextRuns().size(); i++) {
                assertTrue(
                        result.getTextRuns().get(i).getStart() >= result.getTextRuns().get(i - 1).getEnd(),
                        "Run " + i + " start must be >= previous run end");
            }
        }
    }

    @Nested
    @DisplayName("PDF extraction error handling")
    class PdfErrorHandling {

        @Test
        @DisplayName("returns UNREADABLE_FILE for invalid PDF bytes")
        void invalidPdfBytes() {
            byte[] garbage = "this is not a PDF".getBytes(StandardCharsets.UTF_8);
            InputStream is = new ByteArrayInputStream(garbage);

            ExtractionResult result = textExtractor.extract(is, "broken.pdf");

            assertFalse(result.isSuccess());
            assertEquals(ExtractionFailureReason.UNREADABLE_FILE, result.getFailureReason());
        }
    }

    @Nested
    @DisplayName("DOCX extraction error handling")
    class DocxErrorHandling {

        @Test
        @DisplayName("returns UNREADABLE_FILE for invalid DOCX bytes")
        void invalidDocxBytes() {
            byte[] garbage = "this is not a DOCX".getBytes(StandardCharsets.UTF_8);
            InputStream is = new ByteArrayInputStream(garbage);

            ExtractionResult result = textExtractor.extract(is, "broken.docx");

            assertFalse(result.isSuccess());
            assertEquals(ExtractionFailureReason.UNREADABLE_FILE, result.getFailureReason());
        }
    }

    @Nested
    @DisplayName("Failure result properties")
    class FailureResults {

        @Test
        @DisplayName("failure result has null text, 0 charCount, empty runs, not oversized")
        void failureDefaults() {
            ExtractionResult result = ExtractionResult.failure(ExtractionFailureReason.EXTRACTION_TIMEOUT);

            assertNull(result.getExtractedText());
            assertEquals(0, result.getCharCount());
            assertTrue(result.getTextRuns().isEmpty());
            assertFalse(result.isOversized());
            assertEquals(ExtractionFailureReason.EXTRACTION_TIMEOUT, result.getFailureReason());
            assertFalse(result.isSuccess());
        }
    }

    private InputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
