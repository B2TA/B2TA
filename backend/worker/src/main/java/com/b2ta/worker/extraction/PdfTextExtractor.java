package com.b2ta.worker.extraction;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts text from PDF files using Apache PDFBox.
 * Detects password-protected documents and empty (no-text) PDFs.
 * Produces paragraph-level text runs based on line/paragraph boundaries.
 */
@Component
@Slf4j
public class PdfTextExtractor implements FormatExtractor {

    @Override
    public ExtractionResult extract(InputStream inputStream) throws Exception {
        byte[] bytes;
        try {
            bytes = inputStream.readAllBytes();
        } catch (IOException e) {
            log.warn("Failed to read PDF input stream", e);
            return ExtractionResult.failure(ExtractionFailureReason.UNREADABLE_FILE);
        }

        PDDocument document;
        try {
            document = Loader.loadPDF(bytes);
        } catch (InvalidPasswordException e) {
            log.info("PDF is password-protected");
            return ExtractionResult.failure(ExtractionFailureReason.PASSWORD_PROTECTED);
        } catch (IOException e) {
            log.warn("Failed to parse PDF document", e);
            return ExtractionResult.failure(ExtractionFailureReason.UNREADABLE_FILE);
        }

        try (document) {
            PDFTextStripper stripper = new PDFTextStripper();
            String fullText = stripper.getText(document);

            if (fullText == null || fullText.isBlank()) {
                return ExtractionResult.failure(ExtractionFailureReason.NO_EXTRACTABLE_TEXT);
            }

            // Trim trailing whitespace but preserve paragraph structure
            String text = fullText.stripTrailing();

            if (text.isEmpty()) {
                return ExtractionResult.failure(ExtractionFailureReason.NO_EXTRACTABLE_TEXT);
            }

            List<ExtractionResult.TextRun> textRuns = buildParagraphRuns(text);
            return ExtractionResult.success(text, textRuns);
        }
    }

    /**
     * Splits the extracted text into paragraph-level text runs.
     * A paragraph is delimited by one or more consecutive newline characters.
     * Each run covers a non-empty paragraph (start inclusive, end exclusive).
     */
    private List<ExtractionResult.TextRun> buildParagraphRuns(String text) {
        List<ExtractionResult.TextRun> runs = new ArrayList<>();
        int length = text.length();
        int i = 0;

        while (i < length) {
            // Skip whitespace-only gaps between paragraphs
            while (i < length && (text.charAt(i) == '\n' || text.charAt(i) == '\r')) {
                i++;
            }

            if (i >= length) {
                break;
            }

            int start = i;

            // Find end of this paragraph (next double newline or end of text)
            while (i < length) {
                if (text.charAt(i) == '\n' || text.charAt(i) == '\r') {
                    // Check if this is a paragraph break (consecutive newlines)
                    int newlineStart = i;
                    while (i < length && (text.charAt(i) == '\n' || text.charAt(i) == '\r')) {
                        i++;
                    }
                    // If we found 2+ newline chars, it's a paragraph break
                    if (i - newlineStart >= 2) {
                        break;
                    }
                    // Single newline — continue same paragraph
                } else {
                    i++;
                }
            }

            // Trim trailing whitespace from paragraph end
            int end = (i < length) ? i - countTrailingNewlines(text, start, i) : i;
            // Ensure end > start for paragraph boundaries
            while (end > start && (text.charAt(end - 1) == '\n' || text.charAt(end - 1) == '\r')) {
                end--;
            }

            if (end > start) {
                runs.add(ExtractionResult.TextRun.builder()
                        .start(start)
                        .end(end)
                        .build());
            }
        }

        return runs;
    }

    private int countTrailingNewlines(String text, int start, int end) {
        int count = 0;
        for (int i = end - 1; i >= start; i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                count++;
            } else {
                break;
            }
        }
        return count;
    }
}
