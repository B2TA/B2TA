package com.b2ta.api.controller;

import com.b2ta.api.security.CurrentTa;
import com.b2ta.api.security.TaPrincipal;
import com.b2ta.api.service.SessionService;
import com.b2ta.common.dto.session.CreateSessionRequest;
import com.b2ta.common.dto.session.SessionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Grading session CRUD. */
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse create(@CurrentTa TaPrincipal ta,
                                  @Valid @RequestBody CreateSessionRequest request) {
        return sessionService.create(ta, request);
    }

    /** Lists the requesting TA's sessions only (Requirement 14.9). */
    @GetMapping
    public List<SessionResponse> list(@CurrentTa TaPrincipal ta) {
        return sessionService.list(ta);
    }

    @GetMapping("/{sessionId}")
    public SessionResponse get(@CurrentTa TaPrincipal ta, @PathVariable UUID sessionId) {
        return sessionService.get(ta, sessionId);
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> delete(@CurrentTa TaPrincipal ta, @PathVariable UUID sessionId) {
        sessionService.delete(ta, sessionId);
        return ResponseEntity.noContent().build();
    }
}
