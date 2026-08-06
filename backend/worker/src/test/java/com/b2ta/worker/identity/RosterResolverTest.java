package com.b2ta.worker.identity;

import com.b2ta.common.entity.enums.IdentityStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RosterResolver}.
 * Validates Requirements 5.1, 5.2, 5.3.
 */
class RosterResolverTest {

    private RosterResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new RosterResolver();
    }

    @Nested
    @DisplayName("Canvas filename convention (Requirement 5.2)")
    class CanvasFilenames {

        @Test
        @DisplayName("simple single-word name with two numeric segments")
        void singleWordName() {
            ResolvedIdentity result = resolver.resolve("lastfirst_12345_6789012_assignment.pdf");

            assertEquals("lastfirst", result.studentDisplayName());
            assertEquals("6789012", result.canvasSubmissionId());
            assertEquals(IdentityStatus.VERIFIED, result.identityStatus());
        }

        @Test
        @DisplayName("two-word name with underscore separators")
        void twoWordName() {
            ResolvedIdentity result = resolver.resolve("john_doe_67890_1234567_essay.pdf");

            assertEquals("john doe", result.studentDisplayName());
            assertEquals("1234567", result.canvasSubmissionId());
            assertEquals(IdentityStatus.VERIFIED, result.identityStatus());
        }

        @Test
        @DisplayName("two-word name with late marker")
        void twoWordNameWithLateMarker() {
            ResolvedIdentity result = resolver.resolve("john_doe_late_67890_1234567_essay.pdf");

            assertEquals("john doe", result.studentDisplayName());
            assertEquals("1234567", result.canvasSubmissionId());
            assertEquals(IdentityStatus.VERIFIED, result.identityStatus());
        }

        @Test
        @DisplayName("single-word name with late marker")
        void singleWordNameWithLate() {
            ResolvedIdentity result = resolver.resolve("smith_late_98765_4567890_homework.docx");

            assertEquals("smith", result.studentDisplayName());
            assertEquals("4567890", result.canvasSubmissionId());
            assertEquals(IdentityStatus.VERIFIED, result.identityStatus());
        }

        @Test
        @DisplayName("three-word name without late marker")
        void threeWordName() {
            ResolvedIdentity result = resolver.resolve("jane_smith_98765_1111111_assignment-1.docx");

            assertEquals("jane smith", result.studentDisplayName());
            assertEquals("1111111", result.canvasSubmissionId());
            assertEquals(IdentityStatus.VERIFIED, result.identityStatus());
        }

        @Test
        @DisplayName("name with original filename containing hyphens")
        void filenameWithHyphens() {
            ResolvedIdentity result = resolver.resolve("alice_bob_11111_2222222_my-great-essay.pdf");

            assertEquals("alice bob", result.studentDisplayName());
            assertEquals("2222222", result.canvasSubmissionId());
            assertEquals(IdentityStatus.VERIFIED, result.identityStatus());
        }

        @Test
        @DisplayName("late marker is case-insensitive")
        void lateMarkerCaseInsensitive() {
            ResolvedIdentity result = resolver.resolve("jane_doe_LATE_55555_6666666_paper.pdf");

            assertEquals("jane doe", result.studentDisplayName());
            assertEquals("6666666", result.canvasSubmissionId());
            assertEquals(IdentityStatus.VERIFIED, result.identityStatus());
        }

        @Test
        @DisplayName("canvas submission ID is captured correctly")
        void canvasSubmissionIdCaptured() {
            ResolvedIdentity result = resolver.resolve("student_99999_8888888_file.txt");

            assertEquals("student", result.studentDisplayName());
            assertEquals("8888888", result.canvasSubmissionId());
            assertEquals(IdentityStatus.VERIFIED, result.identityStatus());
        }

        @Test
        @DisplayName("single numeric segment (no user ID, only submission ID)")
        void singleNumericSegment() {
            // From task examples: jane_smith_98765_assignment-1.docx → name: "jane smith", id: "98765"
            ResolvedIdentity result = resolver.resolve("jane_smith_98765_assignment-1.docx");

            assertEquals("jane smith", result.studentDisplayName());
            assertEquals("98765", result.canvasSubmissionId());
            assertEquals(IdentityStatus.VERIFIED, result.identityStatus());
        }
    }

    @Nested
    @DisplayName("Fallback: non-Canvas filenames (Requirement 5.3)")
    class FallbackFilenames {

        @Test
        @DisplayName("simple filename with extension removed")
        void simpleFilename() {
            ResolvedIdentity result = resolver.resolve("my_essay.pdf");

            assertEquals("my_essay", result.studentDisplayName());
            assertNull(result.canvasSubmissionId());
            assertEquals(IdentityStatus.UNVERIFIED, result.identityStatus());
        }

        @Test
        @DisplayName("filename with spaces preserved after trim")
        void filenameWithSpaces() {
            ResolvedIdentity result = resolver.resolve("John Doe Assignment.docx");

            assertEquals("John Doe Assignment", result.studentDisplayName());
            assertNull(result.canvasSubmissionId());
            assertEquals(IdentityStatus.UNVERIFIED, result.identityStatus());
        }

        @Test
        @DisplayName("filename with no extension")
        void noExtension() {
            ResolvedIdentity result = resolver.resolve("studentwork");

            assertEquals("studentwork", result.studentDisplayName());
            assertNull(result.canvasSubmissionId());
            assertEquals(IdentityStatus.UNVERIFIED, result.identityStatus());
        }

        @Test
        @DisplayName("filename with multiple dots takes last extension")
        void multipleDots() {
            ResolvedIdentity result = resolver.resolve("student.name.v2.pdf");

            assertEquals("student.name.v2", result.studentDisplayName());
            assertNull(result.canvasSubmissionId());
            assertEquals(IdentityStatus.UNVERIFIED, result.identityStatus());
        }

        @Test
        @DisplayName("filename that looks like Canvas but has wrong structure")
        void partialCanvasLike() {
            // Only one numeric segment — doesn't match Canvas pattern
            ResolvedIdentity result = resolver.resolve("john_doe_12345.pdf");

            assertEquals("john_doe_12345", result.studentDisplayName());
            assertNull(result.canvasSubmissionId());
            assertEquals(IdentityStatus.UNVERIFIED, result.identityStatus());
        }
    }

    @Nested
    @DisplayName("Whitespace normalization (Requirement 5.1)")
    class WhitespaceNormalization {

        @Test
        @DisplayName("leading and trailing whitespace trimmed")
        void leadingTrailingWhitespace() {
            ResolvedIdentity result = resolver.resolve("  spaced name  .pdf");

            assertEquals("spaced name", result.studentDisplayName());
        }

        @Test
        @DisplayName("consecutive whitespace collapsed to single space")
        void consecutiveWhitespace() {
            ResolvedIdentity result = resolver.resolve("lots   of    spaces.pdf");

            assertEquals("lots of spaces", result.studentDisplayName());
        }

        @Test
        @DisplayName("tabs and newlines collapsed")
        void tabsAndNewlines() {
            ResolvedIdentity result = resolver.resolve("tab\there\nnewline.pdf");

            assertEquals("tab here newline", result.studentDisplayName());
        }
    }

    @Nested
    @DisplayName("Truncation to 200 characters (Requirement 5.1)")
    class Truncation {

        @Test
        @DisplayName("name longer than 200 chars is truncated")
        void longNameTruncated() {
            String longName = "a".repeat(250) + ".pdf";
            ResolvedIdentity result = resolver.resolve(longName);

            assertEquals(200, result.studentDisplayName().length());
            assertEquals("a".repeat(200), result.studentDisplayName());
        }

        @Test
        @DisplayName("name exactly 200 chars is not truncated")
        void exactlyMaxLength() {
            String name = "b".repeat(200) + ".pdf";
            ResolvedIdentity result = resolver.resolve(name);

            assertEquals(200, result.studentDisplayName().length());
            assertEquals("b".repeat(200), result.studentDisplayName());
        }

        @Test
        @DisplayName("name under 200 chars is preserved")
        void underMaxLength() {
            String name = "c".repeat(50) + ".pdf";
            ResolvedIdentity result = resolver.resolve(name);

            assertEquals(50, result.studentDisplayName().length());
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("null filename returns empty unverified identity")
        void nullFilename() {
            ResolvedIdentity result = resolver.resolve(null);

            assertEquals("", result.studentDisplayName());
            assertNull(result.canvasSubmissionId());
            assertEquals(IdentityStatus.UNVERIFIED, result.identityStatus());
        }

        @Test
        @DisplayName("empty filename returns empty unverified identity")
        void emptyFilename() {
            ResolvedIdentity result = resolver.resolve("");

            assertEquals("", result.studentDisplayName());
            assertNull(result.canvasSubmissionId());
            assertEquals(IdentityStatus.UNVERIFIED, result.identityStatus());
        }

        @Test
        @DisplayName("blank filename returns empty unverified identity")
        void blankFilename() {
            ResolvedIdentity result = resolver.resolve("   ");

            assertEquals("", result.studentDisplayName());
            assertNull(result.canvasSubmissionId());
            assertEquals(IdentityStatus.UNVERIFIED, result.identityStatus());
        }

        @Test
        @DisplayName("dot-only filename")
        void dotOnlyFilename() {
            ResolvedIdentity result = resolver.resolve(".hidden");

            assertEquals(".hidden", result.studentDisplayName());
            assertNull(result.canvasSubmissionId());
            assertEquals(IdentityStatus.UNVERIFIED, result.identityStatus());
        }
    }
}
