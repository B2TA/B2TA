package com.b2ta.api.canvas;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses RFC 5988 {@code Link} headers.
 *
 * <p>Canvas paginates via this header rather than a body field, so the only way to know
 * whether more pages exist is to look for {@code rel="next"}. A 24-student course fits
 * in one page at {@code per_page=100}; a real course will not.
 */
public final class LinkHeaderParser {

    /**
     * Matches one {@code <url>; rel="name"} entry. Canvas quotes its rel values, but the
     * RFC permits them unquoted, so both are accepted.
     */
    private static final Pattern LINK_ENTRY = Pattern.compile(
            "<([^>]*)>\\s*;\\s*rel=\"?([^\",;\\s]+)\"?");

    private LinkHeaderParser() {
    }

    /**
     * Extracts every {@code rel -> url} pair from a Link header value.
     *
     * @param headerValue raw header value, or null when the header is absent
     * @return map of rel name to URL; empty when the header is absent or unparseable
     */
    public static Map<String, String> parse(String headerValue) {
        Map<String, String> links = new HashMap<>();
        if (headerValue == null || headerValue.isBlank()) {
            return links;
        }

        Matcher matcher = LINK_ENTRY.matcher(headerValue);
        while (matcher.find()) {
            String url = matcher.group(1).trim();
            String rel = matcher.group(2).trim();
            if (!url.isEmpty() && !rel.isEmpty()) {
                // First occurrence wins — a malformed header repeating a rel should not
                // silently redirect pagination to a later duplicate.
                links.putIfAbsent(rel, url);
            }
        }
        return links;
    }

    /**
     * Returns the {@code rel="next"} URL, or empty when this is the last page.
     */
    public static Optional<String> next(String headerValue) {
        return Optional.ofNullable(parse(headerValue).get("next"));
    }
}
