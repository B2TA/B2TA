package com.b2ta.api.canvas;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LinkHeaderParserTest {

    /**
     * A real two-page header as Canvas emits it, captured from the live instance.
     */
    private static final String TWO_PAGE_FIRST = """
            <https://canvas.cic.wtarit.me/api/v1/courses/1/assignments/1/submissions?page=1&per_page=100>; rel="current",\
            <https://canvas.cic.wtarit.me/api/v1/courses/1/assignments/1/submissions?page=2&per_page=100>; rel="next",\
            <https://canvas.cic.wtarit.me/api/v1/courses/1/assignments/1/submissions?page=1&per_page=100>; rel="first",\
            <https://canvas.cic.wtarit.me/api/v1/courses/1/assignments/1/submissions?page=2&per_page=100>; rel="last"\
            """;

    private static final String LAST_PAGE = """
            <https://canvas.cic.wtarit.me/api/v1/courses/1/assignments/1/submissions?page=2&per_page=100>; rel="current",\
            <https://canvas.cic.wtarit.me/api/v1/courses/1/assignments/1/submissions?page=1&per_page=100>; rel="first",\
            <https://canvas.cic.wtarit.me/api/v1/courses/1/assignments/1/submissions?page=2&per_page=100>; rel="last"\
            """;

    @Nested
    @DisplayName("Pagination")
    class Pagination {

        @Test
        void findsNextOnAFirstPage() {
            assertThat(LinkHeaderParser.next(TWO_PAGE_FIRST))
                    .contains("https://canvas.cic.wtarit.me/api/v1/courses/1/assignments/1/submissions?page=2&per_page=100");
        }

        @Test
        void returnsEmptyOnTheLastPage() {
            // The loop terminates on absence of rel=next, not on rel=last being present.
            assertThat(LinkHeaderParser.next(LAST_PAGE)).isEmpty();
        }

        @Test
        void returnsEmptyWhenHeaderAbsent() {
            assertThat(LinkHeaderParser.next(null)).isEmpty();
            assertThat(LinkHeaderParser.next("")).isEmpty();
            assertThat(LinkHeaderParser.next("   ")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Parsing")
    class Parsing {

        @Test
        void extractsEveryRel() {
            Map<String, String> links = LinkHeaderParser.parse(TWO_PAGE_FIRST);

            assertThat(links).containsOnlyKeys("current", "next", "first", "last");
            assertThat(links.get("first")).endsWith("page=1&per_page=100");
            assertThat(links.get("last")).endsWith("page=2&per_page=100");
        }

        @Test
        void acceptsUnquotedRelValues() {
            // RFC 5988 permits unquoted rel values even though Canvas quotes them.
            String header = "<https://example.test/page2>; rel=next";

            assertThat(LinkHeaderParser.next(header)).contains("https://example.test/page2");
        }

        @Test
        void toleratesSpacingVariations() {
            String header = "<https://example.test/p2>;rel=\"next\" , <https://example.test/p1>;  rel=\"prev\"";

            Map<String, String> links = LinkHeaderParser.parse(header);

            assertThat(links.get("next")).isEqualTo("https://example.test/p2");
            assertThat(links.get("prev")).isEqualTo("https://example.test/p1");
        }

        @Test
        void preservesQueryParametersVerbatim() {
            // Canvas signs some pagination URLs; dropping or reordering query params
            // would produce a 401 on the second page.
            String header = "<https://example.test/api?page=2&per_page=100&verifier=abc123>; rel=\"next\"";

            assertThat(LinkHeaderParser.next(header))
                    .contains("https://example.test/api?page=2&per_page=100&verifier=abc123");
        }

        @Test
        void ignoresGarbage() {
            assertThat(LinkHeaderParser.parse("not a link header at all")).isEmpty();
        }

        @Test
        void firstOccurrenceOfARelWins() {
            String header = "<https://example.test/a>; rel=\"next\", <https://example.test/b>; rel=\"next\"";

            assertThat(LinkHeaderParser.next(header)).contains("https://example.test/a");
        }

        @Test
        void handlesEmptyOptionalConsistently() {
            assertThat(LinkHeaderParser.parse(null)).isEmpty();
            assertThat(LinkHeaderParser.next("<https://example.test/a>; rel=\"prev\""))
                    .isEqualTo(Optional.empty());
        }
    }
}
