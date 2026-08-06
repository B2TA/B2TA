package com.b2ta.api.analyze;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Locates a model-supplied quote inside the normalized submission text.
 *
 * <p>This is the load-bearing guardrail of the whole analysis path: the model returns
 * quotes, never offsets, and every quote is checked against the real document before it
 * can be shown. A quote that cannot be located is discarded, so the system cannot
 * display to a TA a passage the student did not write.
 *
 * <p>Matching is whitespace-insensitive because extraction re-wraps lines: a quote the
 * model copied faithfully may differ from the document only in where its line breaks
 * fall. Everything else must match exactly.
 */
public final class EvidenceLocator {

    /**
     * Guards against pathological regex cost on absurd quotes. A legitimate evidence
     * span is a sentence or two; anything past this is not a quote.
     */
    private static final int MAX_QUOTE_LENGTH = 4000;

    private EvidenceLocator() {
    }

    /**
     * Finds {@code quote} in {@code document}, tolerating whitespace re-wrapping.
     *
     * @return the absolute span of the match, or empty when the quote does not appear —
     * which means the model fabricated it
     */
    public static Optional<Span> locate(String quote, String document) {
        if (quote == null || document == null) {
            return Optional.empty();
        }

        String[] tokens = quote.strip().split("\\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) {
            // A blank quote is not evidence of anything.
            return Optional.empty();
        }
        if (quote.length() > MAX_QUOTE_LENGTH) {
            return Optional.empty();
        }

        // Join the quote's tokens with a whitespace matcher so re-wrapped line breaks,
        // double spaces, and tabs all match. Every token is quoted, so regex
        // metacharacters in the student's prose are treated literally.
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) {
                pattern.append("\\s+");
            }
            pattern.append(Pattern.quote(tokens[i]));
        }

        Matcher matcher = Pattern.compile(pattern.toString()).matcher(document);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(new Span(matcher.start(), matcher.end()));
    }

    /** An absolute character range in the normalized document. */
    public record Span(int start, int end) {
        public Span {
            if (start < 0 || end < start) {
                throw new IllegalArgumentException("Invalid span: " + start + ".." + end);
            }
        }

        public int length() {
            return end - start;
        }
    }
}
