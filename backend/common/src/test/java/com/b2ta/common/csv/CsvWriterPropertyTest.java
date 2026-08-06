package com.b2ta.common.csv;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 5.12 — design Property 10: Export CSV Round-Trip.
 *
 * <p>Any value written must come back character for character when the file is parsed as RFC 4180,
 * including values containing commas, double quotes, line breaks, and leading or trailing whitespace.
 * The test writes generated rows and parses them with an independent RFC 4180 reader written for this
 * test, rather than reusing the writer's own logic, so a shared misunderstanding of the format cannot
 * make the test pass.
 */
@Tag("pbt")
class CsvWriterPropertyTest {

    /** Values deliberately weighted towards the characters that require quoting. */
    @Provide
    Arbitrary<String> awkwardValue() {
        Arbitrary<Character> chars = Arbitraries.of(
                'a', 'b', 'Z', '1', '9', ' ', ',', '"', '\r', '\n', '\t', ';', 'é', '中', '\'');
        return chars.list().ofMinSize(0).ofMaxSize(24)
                .map(list -> {
                    StringBuilder builder = new StringBuilder();
                    list.forEach(builder::append);
                    return builder.toString();
                });
    }

    @Provide
    Arbitrary<List<List<String>>> rows() {
        return awkwardValue().list().ofMinSize(1).ofMaxSize(6)
                .list().ofMinSize(1).ofMaxSize(10)
                .filter(table -> table.stream()
                        .map(List::size)
                        .distinct()
                        .count() == 1);
    }

    @Property(tries = 400)
    void everyValueSurvivesTheRoundTrip(@ForAll("rows") List<List<String>> rows) {
        CsvWriter writer = new CsvWriter();
        rows.forEach(writer::writeRow);

        List<List<String>> parsed = Rfc4180Reader.parse(writer.toCsv());

        assertThat(parsed).hasSameSizeAs(rows);
        for (int r = 0; r < rows.size(); r++) {
            for (int c = 0; c < rows.get(r).size(); c++) {
                assertThat(parsed.get(r).get(c))
                        .as("row %d column %d", r, c)
                        .isEqualTo(rows.get(r).get(c));
            }
        }
    }

    @Property(tries = 400)
    void everyRecordEndsWithCrlf(@ForAll("rows") List<List<String>> rows) {
        CsvWriter writer = new CsvWriter();
        rows.forEach(writer::writeRow);

        assertThat(writer.toCsv()).endsWith("\r\n");
    }

    @Property(tries = 200)
    void outputIsUtf8WithoutAByteOrderMark(@ForAll("rows") List<List<String>> rows) {
        CsvWriter writer = new CsvWriter();
        rows.forEach(writer::writeRow);

        byte[] bytes = writer.toUtf8Bytes();
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo(writer.toCsv());
        if (bytes.length >= 3) {
            assertThat(bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF)
                    .isFalse();
        }
    }

    @Test
    void quotesValuesWithLeadingOrTrailingWhitespace() {
        // Requirement 16.6 names whitespace explicitly: unquoted, a trimming parser would drop it and
        // " Ada " would come back as "Ada".
        assertThat(CsvWriter.escape(" Ada ")).isEqualTo("\" Ada \"");
        assertThat(CsvWriter.escape("Ada")).isEqualTo("Ada");
    }

    @Test
    void doublesEmbeddedQuotes() {
        assertThat(CsvWriter.escape("She said \"good\"")).isEqualTo("\"She said \"\"good\"\"\"");
    }

    @Test
    void quotesValuesContainingCommasAndNewlines() {
        assertThat(CsvWriter.escape("Doe, Jane")).isEqualTo("\"Doe, Jane\"");
        assertThat(CsvWriter.escape("line1\nline2")).isEqualTo("\"line1\nline2\"");
    }

    @Test
    void emptyAndNullValuesRenderAsEmptyFields() {
        assertThat(CsvWriter.escape(null)).isEmpty();
        assertThat(CsvWriter.escape("")).isEmpty();
    }

    /**
     * Minimal RFC 4180 reader used only by this test.
     *
     * <p>Independent of {@link CsvWriter} on purpose: parsing with the writer's own rules would only
     * prove it is self-consistent, not that it produces RFC 4180.
     */
    static final class Rfc4180Reader {

        static List<List<String>> parse(String input) {
            List<List<String>> records = new ArrayList<>();
            List<String> record = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean inQuotes = false;
            int i = 0;

            while (i < input.length()) {
                char c = input.charAt(i);

                if (inQuotes) {
                    if (c == '"') {
                        if (i + 1 < input.length() && input.charAt(i + 1) == '"') {
                            field.append('"');
                            i += 2;
                            continue;
                        }
                        inQuotes = false;
                        i++;
                        continue;
                    }
                    field.append(c);
                    i++;
                    continue;
                }

                if (c == '"' && field.isEmpty()) {
                    inQuotes = true;
                    i++;
                } else if (c == ',') {
                    record.add(field.toString());
                    field.setLength(0);
                    i++;
                } else if (c == '\r' && i + 1 < input.length() && input.charAt(i + 1) == '\n') {
                    record.add(field.toString());
                    field.setLength(0);
                    records.add(List.copyOf(record));
                    record.clear();
                    i += 2;
                } else {
                    field.append(c);
                    i++;
                }
            }

            if (!field.isEmpty() || !record.isEmpty()) {
                record.add(field.toString());
                records.add(List.copyOf(record));
            }
            return records;
        }
    }
}
