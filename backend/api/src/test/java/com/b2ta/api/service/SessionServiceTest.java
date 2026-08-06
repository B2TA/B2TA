package com.b2ta.api.service;

import com.b2ta.api.repository.GradingSessionRepository;
import com.b2ta.api.security.SecurityContextHelper;
import com.b2ta.common.dto.session.CreateSessionRequest;
import com.b2ta.common.dto.session.SessionResponse;
import com.b2ta.common.entity.GradingSession;
import com.b2ta.common.entity.TaUser;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private GradingSessionRepository sessionRepository;

    @Mock
    private SecurityContextHelper securityContextHelper;

    @InjectMocks
    private SessionService sessionService;

    private static final UUID TA_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
    }

    @Test
    void createSession_returnsNewSession() {
        CreateSessionRequest request = CreateSessionRequest.builder()
                .name("Assignment 1")
                .build();

        GradingSession savedSession = buildSession(SESSION_ID, "Assignment 1");
        when(sessionRepository.save(any(GradingSession.class))).thenReturn(savedSession);

        SessionResponse response = sessionService.createSession(request);

        assertThat(response.getId()).isEqualTo(SESSION_ID);
        assertThat(response.getName()).isEqualTo("Assignment 1");
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();
        verify(sessionRepository).save(any(GradingSession.class));
    }

    @Test
    void listSessions_returnsSortedSessions() {
        GradingSession s1 = buildSession(SESSION_ID, "Session 1");
        GradingSession s2 = buildSession(UUID.randomUUID(), "Session 2");

        when(sessionRepository.findAllByTaIdOrderByCreatedAtDesc(TA_ID))
                .thenReturn(List.of(s1, s2));

        List<SessionResponse> sessions = sessionService.listSessions();

        assertThat(sessions).hasSize(2);
        assertThat(sessions.get(0).getName()).isEqualTo("Session 1");
        assertThat(sessions.get(1).getName()).isEqualTo("Session 2");
    }

    @Test
    void getSession_returnsSession_whenOwnedByTa() {
        GradingSession session = buildSession(SESSION_ID, "My Session");
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID))
                .thenReturn(Optional.of(session));

        SessionResponse response = sessionService.getSession(SESSION_ID);

        assertThat(response.getId()).isEqualTo(SESSION_ID);
        assertThat(response.getName()).isEqualTo("My Session");
    }

    @Test
    void getSession_throws404_whenNotOwnedByTa() {
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.getSession(SESSION_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Session not found");
    }

    @Test
    void deleteSession_deletesSession_whenOwnedByTa() {
        GradingSession session = buildSession(SESSION_ID, "To Delete");
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID))
                .thenReturn(Optional.of(session));

        sessionService.deleteSession(SESSION_ID);

        verify(sessionRepository).delete(session);
    }

    @Test
    void deleteSession_throws404_whenNotOwnedByTa() {
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.deleteSession(SESSION_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Session not found");
    }

    private GradingSession buildSession(UUID id, String name) {
        TaUser ta = new TaUser();
        ta.setId(TA_ID);

        Instant now = Instant.now();
        return GradingSession.builder()
                .id(id)
                .ta(ta)
                .name(name)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
