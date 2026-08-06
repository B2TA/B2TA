package com.b2ta.common.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 5.3 — Requirement 18.11: no log record may contain an access token, a student display name,
 * or feedback text.
 *
 * <p>Asserts on absence rather than on the exact redacted form, because what the requirement forbids
 * is the value appearing at all.
 */
class SensitiveDataFilterTest {

    private static final String FAKE_JWT =
            "eyJhbGciOiJSUzI1NiIsImtpZCI6ImFiYyJ9.eyJzdWIiOiIxMjM0NTYifQ.c2lnbmF0dXJlLWhlcmU";

    @Test
    void stripsBearerTokensFromAuthorizationHeaders() {
        String sanitized = SensitiveDataFilter.sanitize(
                "Rejected request with Authorization: Bearer " + FAKE_JWT);

        assertThat(sanitized).doesNotContain(FAKE_JWT);
        assertThat(sanitized).contains("Bearer [REDACTED]");
    }

    @Test
    void stripsBareJwtsWithoutAHeaderName() {
        String sanitized = SensitiveDataFilter.sanitize("token=" + FAKE_JWT + " expired");

        assertThat(sanitized).doesNotContain(FAKE_JWT);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"studentDisplayName\":\"Ada Lovelace\",\"submissionId\":\"abc\"}",
            "{\"student_display_name\": \"Ada Lovelace\"}",
            "studentDisplayName=Ada",
    })
    void stripsStudentNames(String message) {
        assertThat(SensitiveDataFilter.sanitize(message))
                .doesNotContain("Ada")
                .contains("[REDACTED]");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"overallFeedback\":\"Your thesis is underdeveloped\"}",
            "{\"criterion_feedback\": \"Your thesis is underdeveloped\"}",
            "{\"feedback\":\"Your thesis is underdeveloped\"}",
    })
    void stripsFeedbackText(String message) {
        assertThat(SensitiveDataFilter.sanitize(message))
                .doesNotContain("thesis")
                .contains("[REDACTED]");
    }

    @Test
    void stripsExtractedSubmissionText() {
        String sanitized = SensitiveDataFilter.sanitize(
                "{\"extractedText\":\"It was the best of times\",\"charCount\":24}");

        assertThat(sanitized).doesNotContain("best of times");
        // Non-sensitive fields in the same payload are left intact so the record stays useful.
        assertThat(sanitized).contains("charCount");
    }

    @Test
    void leavesIdentifiersIntact() {
        String message = "Saved grading record 550e8400-e29b-41d4-a716-446655440000 "
                + "for submission 550e8400-e29b-41d4-a716-446655440111";

        assertThat(SensitiveDataFilter.sanitize(message)).isEqualTo(message);
    }

    @Test
    void handlesNullAndEmptyInput() {
        assertThat(SensitiveDataFilter.sanitize(null)).isNull();
        assertThat(SensitiveDataFilter.sanitize("")).isEmpty();
    }

    @Test
    void redactReportsLengthWithoutTheValue() {
        assertThat(SensitiveDataFilter.redact("Ada Lovelace"))
                .isEqualTo("[REDACTED](len=12)")
                .doesNotContain("Ada");
        assertThat(SensitiveDataFilter.redact(null)).isEqualTo("null");
    }
}
