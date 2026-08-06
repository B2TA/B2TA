package com.b2ta.worker.extraction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Main text extraction service that routes to format-specific extractors.
 * Enforces a 120-second timeout per file extraction.
 *
 * Accepts an InputStream and filename extension, selects the appropriate
 * format extractor, and returns an ExtractionResult with text content,
 * paragraph-level text runs, oversized flag, and failure reason when applicable.
 */
@Service
@Slf4j
public class TextExtractor {

    private static final long EXTRACTION_TIMEOUT_SECONDS = 120;
    private static final int OVERSIZED_THRESHOLD = 100_000;
    private static final Set<String> PDF_EXTENSIONS = Set.of(".pdf");
    private static final Set<String> DOCX_EXTENSIONS = Set.of(".docx");
    private static final Set<String> PLAIN_TEXT_EXTENSIONS = Set.of(".txt", ".md");

    private final PdfTextExtractor pdfTextExtractor;
    private final DocxTextExtractor docxTextExtractor;
    private final PlainTextExtractor plainTextExtractor;
    private final ExecutorService extractionExecutor;

    public TextExtractor(PdfTextExtractor pdfTextExtractor,
                         DocxTextExtractor docxTextExtractor,
                         PlainTextExtractor plainTextExtractor) {
        this.pdfTextExtractor = pdfTextExtractor;
        this.docxTextExtractor = docxTextExtractor;
        this.plainTextExtractor = plainTextExtractor;
        this.extractionExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "text-extraction");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Extracts text from the given input stream based on the file extension.
     *
     * @param inputStream the file content
     * @param filename    the original filename (used to determine format by extension)
     * @return ExtractionResult with extracted text, text runs, oversized flag, or failure reason
     */
    public ExtractionResult extract(InputStream inputStream, String filename) {
        String extension = getExtension(filename);
        FormatExtractor extractor = resolveExtractor(extension);

        if (extractor == null) {
            log.warn("No extractor available for extension '{}' (file: {})", extension, filename);
            return ExtractionResult.failure(ExtractionFailureReason.UNREADABLE_FILE);
        }

        return extractWithTimeout(extractor, inputStream, filename);
    }

    /**
     * Runs the format-specific extraction within a 120-second timeout.
     * If the timeout expires, returns a failure result with EXTRACTION_TIMEOUT reason.
     */
    private ExtractionResult extractWithTimeout(FormatExtractor extractor,
                                                 InputStream inputStream,
                                                 String filename) {
        Future<ExtractionResult> future = extractionExecutor.submit(
                () -> extractor.extract(inputStream)
        );

        try {
            return future.get(EXTRACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Text extraction timed out after {}s for file: {}",
                    EXTRACTION_TIMEOUT_SECONDS, filename);
            return ExtractionResult.failure(ExtractionFailureReason.EXTRACTION_TIMEOUT);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            log.error("Text extraction failed for file: {}", filename, cause);
            return ExtractionResult.failure(ExtractionFailureReason.UNREADABLE_FILE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Text extraction interrupted for file: {}", filename);
            return ExtractionResult.failure(ExtractionFailureReason.EXTRACTION_TIMEOUT);
        }
    }

    /**
     * Resolves the appropriate format extractor based on the file extension.
     *
     * @param extension the file extension in lowercase (e.g., ".pdf")
     * @return the format extractor, or null if the extension is not supported
     */
    private FormatExtractor resolveExtractor(String extension) {
        if (PDF_EXTENSIONS.contains(extension)) {
            return pdfTextExtractor;
        } else if (DOCX_EXTENSIONS.contains(extension)) {
            return docxTextExtractor;
        } else if (PLAIN_TEXT_EXTENSIONS.contains(extension)) {
            return plainTextExtractor;
        }
        return null;
    }

    /**
     * Extracts the lowercase file extension from a filename (including the dot).
     *
     * @param filename the filename
     * @return the extension in lowercase (e.g., ".pdf"), or empty string if none
     */
    private String getExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot).toLowerCase(Locale.ROOT);
    }
}
