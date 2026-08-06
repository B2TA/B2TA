package com.b2ta.api.controller;

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

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @PostMapping
    public ResponseEntity<SessionResponse> createSession(@Valid @RequestBody CreateSessionRequest request) {
        SessionResponse response = sessionService.createSession(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<SessionResponse>> listSessions() {
        List<SessionResponse> sessions = sessionService.listSessions();
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable UUID id) {
        SessionResponse response = sessionService.getSession(id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable UUID id) {
        sessionService.deleteSession(id);
        return ResponseEntity.noContent().build();
    }
}
