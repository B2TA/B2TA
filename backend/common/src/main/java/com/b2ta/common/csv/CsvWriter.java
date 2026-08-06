package com.b2ta.common.csv;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Writes RFC 4180 CSV (Requirements 16.6, 16.7, 16.11, design Property 10).
 *
 * <p>Written by hand rather than pulled from a library because the round-trip guarantee is a stated
 * correctness property and the rules are short enough to state exactly:
 *
 * <ul>
 *   <li>Records end with CRLF, including the last one (Requirement 16.11).
 *   <li>A field is quoted when it contains a comma, a double quote, CR, LF, or leading or trailing
 *       whitespace. Leading and trailing whitespace is not in the RFC's own list, but Requirement
 *       16.6 names it: without quoting, a parser that trims is free to discard it, and a student
 *       name entered as {@code " Ada"} would come back as {@code "Ada"}.
 *   <li>A double quote inside a quoted field is written twice.
 *   <li>Output is UTF-8 with no byte order mark. A BOM would appear as stray characters in the first
 *       header cell of a strict parser.
 * </ul>
 */
public final class CsvWriter {

    private static final String CRLF = "\r\n";

    private final StringBuilder out = new StringBuilder();

    /** Appends one record. */
    public CsvWriter writeRow(List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(escape(fields.get(i)));
        }
        out.append(CRLF);
        return this;
    }

    /** The document as a string. */
    public String toCsv() {
        return out.toString();
    }

    /** The document encoded as UTF-8, ready to be written to S3 (Requirement 16.11). */
    public byte[] toUtf8Bytes() {
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Renders one field.
     *
     * <p>A null value becomes an empty field, which is how an unscored criterion is represented:
     * empty rather than zero (Requirement 16.5).
     */
    static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (!needsQuoting(value)) {
            return value;
        }
        StringBuilder quoted = new StringBuilder(value.length() + 8);
        quoted.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') {
                quoted.append("\"\"");
            } else {
                quoted.append(c);
            }
        }
        quoted.append('"');
        return quoted.toString();
    }

    private static boolean needsQuoting(String value) {
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if (Character.isWhitespace(first) || Character.isWhitespace(last)) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ',' || c == '"' || c == '\r' || c == '\n') {
                return true;
            }
        }
        return false;
    }
}
