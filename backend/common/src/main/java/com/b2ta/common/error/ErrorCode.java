package com.b2ta.common.error;

/**
 * Machine-readable error codes carried in the {@code error.code} field of every failure response.
 *
 * <p>The frontend switches on these rather than on message text, so the strings are part of the
 * API contract.
 */
public final class ErrorCode {

    private ErrorCode() {
    }

    // Authentication and authorization
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";

    // Generic resource outcomes
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String CONFLICT = "CONFLICT";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    // Grading and matching
    public static final String INVALID_OVERRIDE = "INVALID_OVERRIDE";
    public static final String INVALID_PASSAGE_RANGE = "INVALID_PASSAGE_RANGE";
    public static final String PASSAGE_ALREADY_ASSOCIATED = "PASSAGE_ALREADY_ASSOCIATED";
    public static final String NO_EXTRACTED_TEXT = "NO_EXTRACTED_TEXT";
    public static final String ANALYSIS_UNAVAILABLE = "ANALYSIS_UNAVAILABLE";

    // Comment assistant
    public static final String NO_LEVELS_SELECTED = "NO_LEVELS_SELECTED";
    public static final String COMMENT_GENERATION_FAILED = "COMMENT_GENERATION_FAILED";
    public static final String COMMENT_GENERATION_TIMEOUT = "COMMENT_GENERATION_TIMEOUT";

    // Review and export
    public static final String REVIEW_NOT_CONFIRMED = "REVIEW_NOT_CONFIRMED";
    public static final String EMPTY_SESSION = "EMPTY_SESSION";
    public static final String EXPORT_FAILED = "EXPORT_FAILED";

    // Rubric and submissions
    public static final String RUBRIC_NOT_READY = "RUBRIC_NOT_READY";
    public static final String BATCH_LIMIT_EXCEEDED = "BATCH_LIMIT_EXCEEDED";
}
