package com.b2ta.common.logging;

import java.util.regex.Pattern;

/**
 * Redacts values that Requirement 18.11 forbids from appearing in any log record: access tokens,
 * student display names, and feedback text.
 *
 * <p>Two mechanisms work together, because a regex alone cannot recognise a student name:
 *
 * <ul>
 *   <li><b>Pattern redaction</b> catches things that are self-identifying — {@code Bearer} headers,
 *       bare JWTs, and any {@code key=value} or {@code "key": "value"} pair whose key names a
 *       forbidden field. This is the safety net for third-party libraries and stack traces that
 *       this codebase does not control.
 *   <li><b>Explicit wrapping</b> via {@link #redact(String)} is what application code uses. Service
 *       code never interpolates a name or feedback body into a message; it logs identifiers, and
 *       passes anything human-readable through {@code redact} so the intent is visible at the call
 *       site.
 * </ul>
 *
 * <p>Applied to every log line through the {@code %sanitized} conversion rule registered in
 * {@code logback-spring.xml}, so it covers messages emitted by dependencies as well.
 */
public final class SensitiveDataFilter {

    /** Placeholder written in place of a redacted value. */
    public static final String REDACTED = "[REDACTED]";

    /** Field names whose values must never be logged. */
    private static final String FORBIDDEN_KEYS =
            "access[_-]?token|id[_-]?token|refresh[_-]?token|authorization|password|secret"
                    + "|student[_-]?display[_-]?name|studentDisplayName|student[_-]?name"
                    + "|overall[_-]?feedback|overallFeedback|criterion[_-]?feedback|criterionFeedback"
                    + "|feedback|feedback[_-]?text|snippet|extracted[_-]?text|extractedText"
                    + "|passage[_-]?text|rationale";

    /** {@code Authorization: Bearer <token>} in any casing. */
    private static final Pattern BEARER =
            Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9\\-._~+/]+=*");

    /** A bare three-segment JWT, in case a token is logged without its header name. */
    private static final Pattern JWT =
            Pattern.compile("\\beyJ[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]*");

    /** {@code "key": "value"} or {@code "key":"value"} in JSON-shaped text. */
    private static final Pattern JSON_FIELD = Pattern.compile(
            "(?i)(\"(?:" + FORBIDDEN_KEYS + ")\"\\s*:\\s*)\"(?:\\\\.|[^\"\\\\])*\"");

    /**
     * {@code key=value} in key-value shaped text, up to the next separator.
     *
     * <p>The lookahead skips values already handled by an earlier pass, so an
     * {@code Authorization: Bearer <token>} header is not redacted twice into
     * {@code Authorization: [REDACTED] [REDACTED]}.
     */
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)\\b((?:" + FORBIDDEN_KEYS + ")\\s*[=:]\\s*)(?!\\[REDACTED]|Bearer\\b)[^,;\\s}\\]]+");

    private SensitiveDataFilter() {
    }

    /**
     * Removes forbidden values from a rendered log message.
     *
     * @param message the message as rendered by Logback; may be {@code null}
     * @return the message with every recognised sensitive value replaced by {@link #REDACTED}
     */
    public static String sanitize(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        String result = message;
        result = BEARER.matcher(result).replaceAll("Bearer " + REDACTED);
        result = JWT.matcher(result).replaceAll(REDACTED);
        // REDACTED contains no '$' or '\', so it needs no replacement-string escaping.
        result = JSON_FIELD.matcher(result).replaceAll("$1\"" + REDACTED + "\"");
        result = KEY_VALUE.matcher(result).replaceAll("$1" + REDACTED);
        return result;
    }

    /**
     * Wraps a value that must not be logged.
     *
     * <p>Returns the placeholder rather than the value, and reports the length so a log reader can
     * still tell an empty field from a populated one when debugging.
     */
    public static String redact(String value) {
        if (value == null) {
            return "null";
        }
        return REDACTED + "(len=" + value.length() + ")";
    }
}
