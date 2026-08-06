package com.b2ta.api.service;

import com.b2ta.api.repository.GradingSessionRepository;
import com.b2ta.api.security.SecurityContextHelper;
import com.b2ta.common.dto.session.CreateSessionRequest;
import com.b2ta.common.dto.session.SessionResponse;
import com.b2ta.common.entity.GradingSession;
import com.b2ta.common.entity.TaUser;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final GradingSessionRepository sessionRepository;
    private final SecurityContextHelper securityContextHelper;

    /**
     * Creates a new grading session owned by the current TA.
     */
    @Transactional
    public SessionResponse createSession(CreateSessionRequest request) {
        UUID taId = securityContextHelper.getCurrentTaId();

        TaUser ta = new TaUser();
        ta.setId(taId);

        GradingSession session = GradingSession.builder()
                .ta(ta)
                .name(request.getName())
                .build();

        GradingSession saved = sessionRepository.save(session);
        return toResponse(saved);
    }

    /**
     * Lists all sessions belonging to the current TA, ordered by creation date descending.
     */
    @Transactional(readOnly = true)
    public List<SessionResponse> listSessions() {
        UUID taId = securityContextHelper.getCurrentTaId();
        List<GradingSession> sessions = sessionRepository.findAllByTaIdOrderByCreatedAtDesc(taId);
        return sessions.stream().map(this::toResponse).toList();
    }

    /**
     * Gets a single session by ID. Returns 404 if the session does not exist or
     * does not belong to the current TA (tenant isolation).
     */
    @Transactional(readOnly = true)
    public SessionResponse getSession(UUID sessionId) {
        UUID taId = securityContextHelper.getCurrentTaId();
        GradingSession session = sessionRepository.findByIdAndTaId(sessionId, taId)
                .orElseThrow(() -> new EntityNotFoundException("Session not found"));
        return toResponse(session);
    }

    /**
     * Deletes a session by ID. Only succeeds if the session belongs to the current TA.
     * Database cascades handle related records. S3 cleanup is scheduled separately.
     */
    @Transactional
    public void deleteSession(UUID sessionId) {
        UUID taId = securityContextHelper.getCurrentTaId();
        GradingSession session = sessionRepository.findByIdAndTaId(sessionId, taId)
                .orElseThrow(() -> new EntityNotFoundException("Session not found"));
        sessionRepository.delete(session);
    }

    private SessionResponse toResponse(GradingSession session) {
        return SessionResponse.builder()
                .id(session.getId())
                .name(session.getName())
                .reviewConfirmedAt(session.getReviewConfirmedAt())
                .submissionCount(0) // Will be populated with real count once submissions are implemented
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }
}
