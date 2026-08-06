package com.b2ta.api.canvas;

import lombok.Getter;

/**
 * A Canvas call failed.
 *
 * <p>Carries the Canvas-side status and body so the TA sees the actual reason rather
 * than a generic failure — a sync that fails must surface Canvas's own error and permit
 * retry (Requirement 5.5).
 */
@Getter
public class CanvasException extends RuntimeException {

    /** HTTP status from Canvas, or 0 when the call never completed. */
    private final int status;

    /** Whether retrying unchanged could plausibly succeed. */
    private final boolean retryable;

    public CanvasException(String message, int status, boolean retryable) {
        super(message);
        this.status = status;
        this.retryable = retryable;
    }

    public CanvasException(String message, int status, boolean retryable, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.retryable = retryable;
    }

    /**
     * 401 means the token in Secrets Manager is wrong or expired — a configuration
     * problem, not something the TA can fix by retrying.
     */
    public static CanvasException unauthorized(String body) {
        return new CanvasException(
                "Canvas rejected the API token (401). Check the token in Secrets Manager. " + body,
                401, false);
    }

    /** 404 means the course or assignment id is misconfigured. Never fall back to demo data. */
    public static CanvasException notFound(String what) {
        return new CanvasException(
                "Canvas returned 404 for " + what + ". Check the configured course and assignment ids.",
                404, false);
    }
}
