package com.b2ta.worker.extraction;

/**
 * Reasons why text extraction from a submission file can fail.
 * These map directly to the extraction_failure_reason column in the submission table.
 */
public enum ExtractionFailureReason {

    /**
     * The file could not be read or parsed (corrupted, unsupported internal format).
     */
    UNREADABLE_FILE("unreadable_file"),

    /**
     * The file is password-protected and cannot be opened without credentials.
     */
    PASSWORD_PROTECTED("password_protected"),

    /**
     * The file was parsed successfully but contains no extractable text
     * (e.g., a scanned PDF with no text layer).
     */
    NO_EXTRACTABLE_TEXT("no_extractable_text"),

    /**
     * Text extraction did not complete within the 120-second timeout.
     */
    EXTRACTION_TIMEOUT("extraction_timeout");

    private final String databaseValue;

    ExtractionFailureReason(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    /**
     * Returns the string value stored in the database extraction_failure_reason column.
     */
    public String getDatabaseValue() {
        return databaseValue;
    }
}
