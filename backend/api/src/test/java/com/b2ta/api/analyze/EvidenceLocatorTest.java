package com.b2ta.api.analyze;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceLocatorTest {

    private static final String DOC = """
            The question of whether social media platforms bear moral responsibility \
            has moved into courtrooms. I will argue that platforms are not neutral \
            conduits but active architects of epistemic bubbles.

            According to Pariser (2011), the "filter bubble" emerges not from user \
            choice alone but from algorithmic curation.""";

    @Nested
    @DisplayName("Rejecting fabricated quotes")
    class Fabricated {

        @Test
        void returnsEmptyForAQuoteTheStudentNeverWrote() {
            // The single guarantee worth naming out loud: a hallucinated quotation
            // cannot reach the TA's screen.
            assertThat(EvidenceLocator.locate(
                    "Platforms are unambiguously the sole cause of polarization.", DOC))
                    .isEmpty();
        }

        @Test
        void returnsEmptyWhenOnlyMostOfTheQuoteMatches() {
            // A near-miss is still a fabrication — partial credit would let the model
            // put words in the student's mouth.
            assertThat(EvidenceLocator.locate(
                    "platforms are not neutral conduits but passive architects", DOC))
                    .isEmpty();
        }

        @Test
        void returnsEmptyForBlankOrNullInput() {
            assertThat(EvidenceLocator.locate("", DOC)).isEmpty();
            assertThat(EvidenceLocator.locate("   \n  ", DOC)).isEmpty();
            assertThat(EvidenceLocator.locate(null, DOC)).isEmpty();
            assertThat(EvidenceLocator.locate("anything", null)).isEmpty();
        }

        @Test
        void returnsEmptyForAnAbsurdlyLongQuote() {
            assertThat(EvidenceLocator.locate("word ".repeat(2000), DOC)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Locating genuine quotes")
    class Genuine {

        @Test
        void findsAnExactQuote() {
            Optional<EvidenceLocator.Span> span =
                    EvidenceLocator.locate("active architects of epistemic bubbles", DOC);

            assertThat(span).isPresent();
            assertThat(DOC.substring(span.get().start(), span.get().end()))
                    .isEqualTo("active architects of epistemic bubbles");
        }

        @Test
        void toleratesWhitespaceRewrapping() {
            // Extraction re-wraps lines, so a faithfully-copied quote can differ from
            // the document only in where its breaks fall.
            Optional<EvidenceLocator.Span> span = EvidenceLocator.locate(
                    "platforms are\n   not neutral\tconduits", DOC);

            assertThat(span).isPresent();
            assertThat(DOC.substring(span.get().start(), span.get().end()))
                    .isEqualTo("platforms are not neutral conduits");
        }

        @Test
        void treatsRegexMetacharactersInProseLiterally() {
            String doc = "The result (a+b)* was unexpected. See [note] for details.";

            Optional<EvidenceLocator.Span> span = EvidenceLocator.locate("(a+b)*", doc);

            assertThat(span).isPresent();
            assertThat(doc.substring(span.get().start(), span.get().end())).isEqualTo("(a+b)*");
        }

        @Test
        void handlesQuotesContainingPunctuation() {
            Optional<EvidenceLocator.Span> span =
                    EvidenceLocator.locate("According to Pariser (2011), the \"filter bubble\"", DOC);

            assertThat(span).isPresent();
        }

        @Test
        void returnedSpanAlwaysMatchesTheDocumentSubstring() {
            // The invariant every rendered highlight depends on.
            String[] quotes = {
                    "moral responsibility",
                    "algorithmic curation",
                    "I will argue that platforms",
                    "courtrooms",
            };

            for (String quote : quotes) {
                EvidenceLocator.Span span = EvidenceLocator.locate(quote, DOC).orElseThrow();
                assertThat(DOC.substring(span.start(), span.end()))
                        .as("span for %s", quote)
                        .isEqualToIgnoringWhitespace(quote);
            }
        }

        @Test
        void findsTheFirstOccurrenceWhenAQuoteRepeats() {
            String doc = "alpha beta gamma. alpha beta delta.";

            EvidenceLocator.Span span = EvidenceLocator.locate("alpha beta", doc).orElseThrow();

            assertThat(span.start()).isZero();
        }
    }
}
