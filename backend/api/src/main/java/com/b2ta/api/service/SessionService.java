package com.b2ta.api.service;

import com.b2ta.api.security.TaPrincipal;
import com.b2ta.api.security.TenantGuard;
import com.b2ta.common.dto.session.CreateSessionRequest;
import com.b2ta.common.dto.session.SessionResponse;
import com.b2ta.common.entity.GradingSession;
import com.b2ta.common.entity.TaUser;
import com.b2ta.common.repository.GradingSessionRepository;
import com.b2ta.common.repository.SubmissionRepository;
import com.b2ta.common.repository.TaUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Grading session CRUD (Requirements 14.8, 14.9, 19.6).
 *
 * <p>Sessions are the tenant root: every rubric, submission, and grading record hangs off one, and
 * ownership is checked here so the rest of the API can take a session id as trusted once it has been
 * resolved through {@link TenantGuard}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final GradingSessionRepository sessionRepository;
    private final SubmissionRepository submissionRepository;
    private final TaUserRepository taUserRepository;
    private final TenantGuard tenantGuard;

    @Transactional
    public SessionResponse create(TaPrincipal ta, CreateSessionRequest request) {
        TaUser owner = taUserRepository.getReferenceById(ta.taId());
        GradingSession session = sessionRepository.save(GradingSession.builder()
                .ta(owner)
                .name(request.getName().trim())
                .build());
        log.info("Created grading session {}", session.getId());
        return toResponse(session, 0);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> list(TaPrincipal ta) {
        return sessionRepository.findAllByTaId(ta.taId()).stream()
                .map(session -> toResponse(session,
                        submissionRepository.countBySessionId(session.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public SessionResponse get(TaPrincipal ta, UUID sessionId) {
        GradingSession session = tenantGuard.requireSession(ta, sessionId);
        return toResponse(session, submissionRepository.countBySessionId(sessionId));
    }

    /**
     * Deletes a session and everything under it (Requirement 19.6).
     *
     * <p>Database rows go immediately through the {@code ON DELETE CASCADE} chain. The S3 objects are
     * removed by the bucket lifecycle rules rather than synchronously here, which is why the
     * requirement allows 24 hours: deleting up to 300 objects inline would put an unbounded S3 loop on
     * an HTTP request thread.
     */
    @Transactional
    public void delete(TaPrincipal ta, UUID sessionId) {
        GradingSession session = tenantGuard.requireSession(ta, sessionId);
        sessionRepository.delete(session);
        log.info("Deleted grading session {}", sessionId);
    }

    private SessionResponse toResponse(GradingSession session, int submissionCount) {
        return SessionResponse.builder()
                .id(session.getId())
                .name(session.getName())
                .reviewConfirmedAt(session.getReviewConfirmedAt())
                .submissionCount(submissionCount)
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }
}
