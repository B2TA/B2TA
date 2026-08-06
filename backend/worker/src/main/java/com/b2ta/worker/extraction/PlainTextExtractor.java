package com.b2ta.worker.extraction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts text from plain text files (TXT, MD).
 * Reads the input as UTF-8 and produces paragraph-level text runs
 * based on double-newline paragraph boundaries.
 */
@Component
@Slf4j
public class PlainTextExtractor implements FormatExtractor {

    @Override
    public ExtractionResult extract(InputStream inputStream) throws Exception {
        String text;
        try {
            byte[] bytes = inputStream.readAllBytes();
            text = new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to read plain text input stream", e);
            return ExtractionResult.failure(ExtractionFailureReason.UNREADABLE_FILE);
        }

        if (text.isBlank()) {
            return ExtractionResult.failure(ExtractionFailureReason.NO_EXTRACTABLE_TEXT);
        }

        // Strip trailing whitespace
        text = text.stripTrailing();

        if (text.isEmpty()) {
            return ExtractionResult.failure(ExtractionFailureReason.NO_EXTRACTABLE_TEXT);
        }

        List<ExtractionResult.TextRun> textRuns = buildParagraphRuns(text);
        return ExtractionResult.success(text, textRuns);
    }

    /**
     * Splits the text into paragraph-level text runs.
     * A paragraph boundary is defined as two or more consecutive newline characters.
     * Single newlines within a paragraph are preserved as part of the same run.
     */
    private List<ExtractionResult.TextRun> buildParagraphRuns(String text) {
        List<ExtractionResult.TextRun> runs = new ArrayList<>();
        int length = text.length();
        int i = 0;

        while (i < length) {
            // Skip newline-only gaps between paragraphs
            while (i < length && (text.charAt(i) == '\n' || text.charAt(i) == '\r')) {
                i++;
            }

            if (i >= length) {
                break;
            }

            int start = i;

            // Find end of this paragraph
            while (i < length) {
                if (text.charAt(i) == '\n' || text.charAt(i) == '\r') {
                    int newlineStart = i;
                    while (i < length && (text.charAt(i) == '\n' || text.charAt(i) == '\r')) {
                        i++;
                    }
                    // Two or more newline characters indicate a paragraph break
                    if (i - newlineStart >= 2) {
                        break;
                    }
                    // Single newline — continue same paragraph
                } else {
                    i++;
                }
            }

            // Trim trailing newlines from the paragraph end
            int end = i;
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
}
