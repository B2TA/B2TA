package com.b2ta.worker.extraction;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.UnsupportedFileFormatException;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts text from DOCX files using Apache POI.
 * Produces paragraph-level text runs based on the document's paragraph structure.
 */
@Component
@Slf4j
public class DocxTextExtractor implements FormatExtractor {

    @Override
    public ExtractionResult extract(InputStream inputStream) throws Exception {
        XWPFDocument document;
        try {
            document = new XWPFDocument(inputStream);
        } catch (POIXMLException | UnsupportedFileFormatException e) {
            // POIXMLException = corrupt OOXML; UnsupportedFileFormatException = not an
            // OOXML file at all (e.g. a .doc or PDF renamed to .docx). Both unchecked.
            log.warn("Failed to parse DOCX document", e);
            return ExtractionResult.failure(ExtractionFailureReason.UNREADABLE_FILE);
        } catch (IOException e) {
            log.warn("Failed to read DOCX input stream", e);
            return ExtractionResult.failure(ExtractionFailureReason.UNREADABLE_FILE);
        }

        try (document) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            StringBuilder fullText = new StringBuilder();
            List<ExtractionResult.TextRun> textRuns = new ArrayList<>();

            for (XWPFParagraph paragraph : paragraphs) {
                String paragraphText = paragraph.getText();

                if (paragraphText == null || paragraphText.isEmpty()) {
                    // Empty paragraph — add a newline separator if we have content
                    if (!fullText.isEmpty()) {
                        fullText.append('\n');
                    }
                    continue;
                }

                // Add paragraph separator if not the first content
                if (!fullText.isEmpty()) {
                    fullText.append('\n');
                }

                int start = fullText.length();
                fullText.append(paragraphText);
                int end = fullText.length();

                if (end > start) {
                    textRuns.add(ExtractionResult.TextRun.builder()
                            .start(start)
                            .end(end)
                            .build());
                }
            }

            String text = fullText.toString();

            if (text.isBlank()) {
                return ExtractionResult.failure(ExtractionFailureReason.NO_EXTRACTABLE_TEXT);
            }

            return ExtractionResult.success(text, textRuns);
        }
    }
}
