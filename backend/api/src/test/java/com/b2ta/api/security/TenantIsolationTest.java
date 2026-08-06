package com.b2ta.api.security;

import com.b2ta.common.entity.Criterion;
import com.b2ta.common.entity.GradingSession;
import com.b2ta.common.entity.Submission;
import com.b2ta.common.entity.TaUser;
import com.b2ta.common.error.ApiException;
import com.b2ta.common.error.ErrorCode;
import com.b2ta.common.repository.CriterionRepository;
import com.b2ta.common.repository.GradingSessionRepository;
import com.b2ta.common.repository.RubricRepository;
import com.b2ta.common.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task 5.2 — Requirement 18.5: a TA requesting another TA's resource gets 404, not 403, and the
 * response reveals nothing about whether that resource exists.
 *
 * <p>The tests assert two things that matter independently: the status and code are the same as for a
 * resource that never existed, and the repository was queried with the authenticated TA's id, so the
 * isolation is enforced in the query rather than by a check after loading the row.
 */
@ExtendWith(MockitoExtension.class)
class TenantIsolationTest {

    private static final UUID OWNER_TA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_TA = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private GradingSessionRepository sessionRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private RubricRepository rubricRepository;
    @Mock
    private CriterionRepository criterionRepository;

    @InjectMocks
    private TenantGuard tenantGuard;

    private TaPrincipal owner;
    private TaPrincipal other;
    private UUID sessionId;
    private UUID submissionId;

    @BeforeEach
    void setUp() {
        owner = new TaPrincipal(OWNER_TA, "sub-owner", "owner@example.com");
        other = new TaPrincipal(OTHER_TA, "sub-other", "other@example.com");
        sessionId = UUID.randomUUID();
        submissionId = UUID.randomUUID();
    }

    @Test
    void ownerCanLoadTheirOwnSession() {
        GradingSession session = session(sessionId, OWNER_TA);
        when(sessionRepository.findByIdAndTaId(sessionId, OWNER_TA)).thenReturn(Optional.of(session));

        assertThat(tenantGuard.requireSession(owner, sessionId)).isSameAs(session);
    }

    @Test
    void anotherTaGets404ForAnExistingSession() {
        // The row exists, but the tenant-scoped query does not match it, so the repository answers
        // empty exactly as it would for an unknown id.
        when(sessionRepository.findByIdAndTaId(sessionId, OTHER_TA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantGuard.requireSession(other, sessionId))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> {
                    ApiException error = (ApiException) thrown;
                    assertThat(error.getStatus()).isEqualTo(404);
                    assertThat(error.getCode()).isEqualTo(ErrorCode.NOT_FOUND);
                });

        verify(sessionRepository).findByIdAndTaId(sessionId, OTHER_TA);
    }

    @Test
    void aMissingSessionAndAnotherTasSessionAreIndistinguishable() {
        UUID unknownId = UUID.randomUUID();
        when(sessionRepository.findByIdAndTaId(any(), eq(OTHER_TA))).thenReturn(Optional.empty());

        ApiException forOtherTa = catchApiException(() -> tenantGuard.requireSession(other, sessionId));
        ApiException forUnknown = catchApiException(() -> tenantGuard.requireSession(other, unknownId));

        assertThat(forOtherTa.getStatus()).isEqualTo(forUnknown.getStatus());
        assertThat(forOtherTa.getCode()).isEqualTo(forUnknown.getCode());
        // Only the id the caller already supplied differs; nothing in the message hints that one of
        // them exists under another owner.
        assertThat(forOtherTa.getMessage().replace(sessionId.toString(), "ID"))
                .isEqualTo(forUnknown.getMessage().replace(unknownId.toString(), "ID"));
    }

    @Test
    void submissionLookupIsScopedByBothSessionAndTa() {
        when(submissionRepository.findByIdAndSessionIdAndTaId(submissionId, sessionId, OTHER_TA))
                .thenReturn(Optional.empty());

        assertThat(catchApiException(
                () -> tenantGuard.requireSubmission(other, sessionId, submissionId)).getStatus())
                .isEqualTo(404);

        // Both ids are in the predicate: a submission id belonging to another session of the same TA
        // must not be readable through a guessed session id either.
        verify(submissionRepository)
                .findByIdAndSessionIdAndTaId(submissionId, sessionId, OTHER_TA);
    }

    @Test
    void submissionLoadsForTheOwner() {
        Submission submission = new Submission();
        when(submissionRepository.findByIdAndSessionIdAndTaId(submissionId, sessionId, OWNER_TA))
                .thenReturn(Optional.of(submission));

        assertThat(tenantGuard.requireSubmission(owner, sessionId, submissionId))
                .isSameAs(submission);
    }

    @Test
    void rubricLookupIsTenantScoped() {
        when(rubricRepository.findBySessionIdAndTaId(sessionId, OTHER_TA)).thenReturn(Optional.empty());

        assertThat(catchApiException(() -> tenantGuard.requireRubric(other, sessionId)).getStatus())
                .isEqualTo(404);
        verify(rubricRepository).findBySessionIdAndTaId(sessionId, OTHER_TA);
    }

    @Test
    void criterionLookupIsTenantScoped() {
        UUID criterionId = UUID.randomUUID();
        when(criterionRepository.findByIdAndSessionIdAndTaId(criterionId, sessionId, OTHER_TA))
                .thenReturn(Optional.empty());

        assertThat(catchApiException(
                () -> tenantGuard.requireCriterion(other, sessionId, criterionId)).getStatus())
                .isEqualTo(404);
        verify(criterionRepository).findByIdAndSessionIdAndTaId(criterionId, sessionId, OTHER_TA);
    }

    @Test
    void criterionLoadsForTheOwner() {
        UUID criterionId = UUID.randomUUID();
        Criterion criterion = new Criterion();
        when(criterionRepository.findByIdAndSessionIdAndTaId(criterionId, sessionId, OWNER_TA))
                .thenReturn(Optional.of(criterion));

        assertThat(tenantGuard.requireCriterion(owner, sessionId, criterionId)).isSameAs(criterion);
    }

    @Test
    void principalToStringOmitsTheEmail() {
        // The principal can end up in a log line through a framework message; Requirement 18.11
        // forbids the email appearing there.
        assertThat(owner.toString()).doesNotContain("owner@example.com").contains(OWNER_TA.toString());
    }

    private GradingSession session(UUID id, UUID taId) {
        return GradingSession.builder()
                .id(id)
                .ta(TaUser.builder().id(taId).build())
                .name("Session")
                .build();
    }

    private ApiException catchApiException(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected an ApiException");
        } catch (ApiException e) {
            return e;
        }
    }
}
