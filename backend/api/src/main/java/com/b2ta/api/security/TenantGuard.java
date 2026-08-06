package com.b2ta.api.security;

import com.b2ta.common.entity.Criterion;
import com.b2ta.common.entity.GradingSession;
import com.b2ta.common.entity.Rubric;
import com.b2ta.common.entity.Submission;
import com.b2ta.common.error.ApiException;
import com.b2ta.common.repository.CriterionRepository;
import com.b2ta.common.repository.GradingSessionRepository;
import com.b2ta.common.repository.RubricRepository;
import com.b2ta.common.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Single entry point for loading a tenant-owned resource (Requirement 18.5).
 *
 * <p>Services never call {@code findById} on a session, submission, rubric, or criterion. They come
 * through here, which resolves by (resource id, TA id) and raises 404 when the pair does not match.
 * A TA probing another TA's session id therefore gets the same response as for an id that was never
 * issued: no 403, no differing message, nothing that confirms the resource exists.
 *
 * <p>Keeping the check in one class means adding an endpoint cannot accidentally omit it — the
 * loader is the only way to obtain the entity.
 */
@Component
@RequiredArgsConstructor
public class TenantGuard {

    private final GradingSessionRepository sessionRepository;
    private final SubmissionRepository submissionRepository;
    private final RubricRepository rubricRepository;
    private final CriterionRepository criterionRepository;

    @Transactional(readOnly = true)
    public GradingSession requireSession(TaPrincipal ta, UUID sessionId) {
        return sessionRepository.findByIdAndTaId(sessionId, ta.taId())
                .orElseThrow(() -> notFound("Grading session", sessionId));
    }

    @Transactional(readOnly = true)
    public Submission requireSubmission(TaPrincipal ta, UUID sessionId, UUID submissionId) {
        return submissionRepository.findByIdAndSessionIdAndTaId(submissionId, sessionId, ta.taId())
                .orElseThrow(() -> notFound("Submission", submissionId));
    }

    @Transactional(readOnly = true)
    public Rubric requireRubric(TaPrincipal ta, UUID sessionId) {
        return rubricRepository.findBySessionIdAndTaId(sessionId, ta.taId())
                .orElseThrow(() -> notFound("Rubric for session", sessionId));
    }

    @Transactional(readOnly = true)
    public Criterion requireCriterion(TaPrincipal ta, UUID sessionId, UUID criterionId) {
        return criterionRepository.findByIdAndSessionIdAndTaId(criterionId, sessionId, ta.taId())
                .orElseThrow(() -> notFound("Criterion", criterionId));
    }

    /**
     * Uniform 404.
     *
     * <p>The message names the resource type and the id the caller already supplied, and nothing
     * else — in particular it never reveals whether the id exists under another owner.
     */
    private ApiException notFound(String resource, UUID id) {
        return ApiException.notFound(resource + " " + id + " was not found");
    }
}
