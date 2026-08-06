package com.b2ta.common.error;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base class for failures that map to a defined HTTP status and error code.
 *
 * <p>Anything thrown that is not an {@code ApiException} is treated as an unexpected server error
 * and reported as a generic 500, so no internal detail leaks into a response body.
 */
@Getter
public class ApiException extends RuntimeException {

    private final int status;
    private final String code;
    private final Map<String, Object> details;

    protected ApiException(int status, String code, String message) {
        this(status, code, message, new LinkedHashMap<>());
    }

    protected ApiException(int status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details);
    }

    /** Adds a contextual field to the {@code error.details} object. */
    public ApiException with(String key, Object value) {
        details.put(key, value);
        return this;
    }

    /** 401 — absent, expired, or invalid access token (Requirement 18.4). */
    public static ApiException unauthorized(String code, String message) {
        return new ApiException(401, code, message);
    }

    /** 404 — resource absent, or owned by a different TA (Requirement 18.5). */
    public static ApiException notFound(String message) {
        return new ApiException(404, ErrorCode.NOT_FOUND, message);
    }

    /** 400 — the request is malformed or violates a stated bound. */
    public static ApiException badRequest(String code, String message) {
        return new ApiException(400, code, message);
    }

    /** 409 — the request conflicts with current state. */
    public static ApiException conflict(String code, String message) {
        return new ApiException(409, code, message);
    }

    /** 422 — the request was understood but processing failed (parse, extraction, analysis). */
    public static ApiException unprocessable(String code, String message) {
        return new ApiException(422, code, message);
    }

    /** 504 — an upstream dependency did not answer inside its budget. */
    public static ApiException gatewayTimeout(String code, String message) {
        return new ApiException(504, code, message);
    }

    /** 500 — unexpected server-side failure. */
    public static ApiException internal(String message) {
        return new ApiException(500, ErrorCode.INTERNAL_ERROR, message);
    }
}
