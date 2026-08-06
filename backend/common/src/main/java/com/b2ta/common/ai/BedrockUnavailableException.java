package com.b2ta.common.ai;

/** Raised when every permitted Bedrock attempt failed, or Bedrock is disabled by configuration. */
public class BedrockUnavailableException extends RuntimeException {

    private final boolean timeout;

    public BedrockUnavailableException(String message, boolean timeout, Throwable cause) {
        super(message, cause);
        this.timeout = timeout;
    }

    /** True when the failure was a timeout rather than an error response, so callers can map 504. */
    public boolean isTimeout() {
        return timeout;
    }
}
