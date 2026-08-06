package com.b2ta.worker.extraction;

import java.io.InputStream;

/**
 * Strategy interface for format-specific text extraction.
 * Implementations handle a single file format (PDF, DOCX, TXT/MD).
 */
public interface FormatExtractor {

    /**
     * Extracts text from the given input stream.
     *
     * @param inputStream the file content to extract from
     * @return the extraction result containing text, runs, and metadata
     * @throws Exception if extraction fails for any reason
     */
    ExtractionResult extract(InputStream inputStream) throws Exception;
}
