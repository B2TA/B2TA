package com.b2ta.worker.parsing;

/**
 * Thrown when a rubric file cannot be parsed successfully.
 * The message contains the specific reason for the failure.
 */
public class RubricParseException extends Exception {

    public RubricParseException(String message) {
        super(message);
    }

    public RubricParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
