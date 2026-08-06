package com.b2ta.api.canvas;

import com.b2ta.api.canvas.dto.CanvasSubmission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionTextExtractorTest {

    private SubmissionTextExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new SubmissionTextExtractor();
    }

    @Nested
    @DisplayName("HTML stripping")
    class HtmlStripping {

        @Test
        void preservesParagraphBoundaries() {
            // If block tags are dropped without becoming blank lines, the whole essay
            // collapses into a single paragraph and every ¶ label is wrong.
            String html = "<p>First paragraph.</p><p>Second paragraph.</p>";

            assertThat(SubmissionTextExtractor.stripHtml(html))
                    .isEqualTo("First paragraph.\n\nSecond paragraph.");
        }

        @Test
        void convertsLineBreaksToNewlines() {
            assertThat(SubmissionTextExtractor.stripHtml("one<br>two<br/>three"))
                    .isEqualTo("one\ntwo\nthree");
        }

        @Test
        void removesScriptAndStyleContentEntirely() {
            String html = "<p>Visible.</p><script>alert('x')</script><style>p{color:red}</style>";

            assertThat(SubmissionTextExtractor.stripHtml(html)).isEqualTo("Visible.");
        }

        @Test
        void unescapesEntities() {
            assertThat(SubmissionTextExtractor.stripHtml("<p>Tom &amp; Jerry &quot;quoted&quot;</p>"))
                    .isEqualTo("Tom & Jerry \"quoted\"");
        }

        @Test
        void doesNotDoubleUnescapeAmpersands() {
            // "&amp;lt;" is a literal "&lt;", not a less-than sign.
            assertThat(SubmissionTextExtractor.stripHtml("<p>&amp;lt;</p>")).isEqualTo("&lt;");
        }

        @Test
        void collapsesExcessBlankLines() {
            assertThat(SubmissionTextExtractor.stripHtml("<p>a</p><p></p><p></p><p>b</p>"))
                    .isEqualTo("a\n\nb");
        }

        @Test
        void handlesHeadingsAndListItems() {
            String html = "<h1>Title</h1><ul><li>one</li><li>two</li></ul>";

            assertThat(SubmissionTextExtractor.stripHtml(html))
                    .isEqualTo("Title\n\none\n\ntwo");
        }
    }

    @Nested
    @DisplayName("Dispatch by submission type")
    class Dispatch {

        @Test
        void extractsAnOnlineTextEntry() {
            CanvasSubmission submission = CanvasSubmission.builder()
                    .submissionType("online_text_entry")
                    .body("<p>The thesis is clear.</p>")
                    .build();

            SubmissionTextExtractor.Extraction result = extractor.extract(submission);

            assertThat(result.failed()).isFalse();
            assertThat(result.text()).isEqualTo("The thesis is clear.");
        }

        @Test
        void reportsAnEmptyTextEntry() {
            CanvasSubmission submission = CanvasSubmission.builder()
                    .submissionType("online_text_entry").body("  ").build();

            assertThat(extractor.extract(submission).failed()).isTrue();
        }

        @Test
        void reportsAnUploadWithNoAttachment() {
            CanvasSubmission submission = CanvasSubmission.builder()
                    .submissionType("online_upload").attachments(List.of()).build();

            SubmissionTextExtractor.Extraction result = extractor.extract(submission);

            assertThat(result.failed()).isTrue();
            assertThat(result.error()).contains("no attached file");
        }

        @Test
        void reportsAnUnsupportedSubmissionType() {
            CanvasSubmission submission = CanvasSubmission.builder()
                    .submissionType("online_quiz").build();

            SubmissionTextExtractor.Extraction result = extractor.extract(submission);

            assertThat(result.failed()).isTrue();
            assertThat(result.error()).contains("online_quiz");
        }

        @Test
        void reportsAUrlSubmissionWithActionableText() {
            CanvasSubmission submission = CanvasSubmission.builder()
                    .submissionType("online_url").build();

            assertThat(extractor.extract(submission).error()).contains("Open it in Canvas");
        }

        @Test
        void reportsAMissingSubmissionType() {
            assertThat(extractor.extract(CanvasSubmission.builder().build()).failed()).isTrue();
        }
    }
}
