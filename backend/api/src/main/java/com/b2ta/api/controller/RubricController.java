package com.b2ta.api.controller;

import com.b2ta.api.security.CurrentTa;
import com.b2ta.api.security.TaPrincipal;
import com.b2ta.api.service.RubricService;
import com.b2ta.common.dto.rubric.RubricExportResponse;
import com.b2ta.common.dto.rubric.RubricResponse;
import com.b2ta.common.dto.rubric.SaveRubricRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Rubric read and write for one session. */
@RestController
@RequestMapping("/api/sessions/{sessionId}/rubric")
@RequiredArgsConstructor
public class RubricController {

    private final RubricService rubricService;

    /**
     * Returns the rubric, or 204 when the session has none yet.
     *
     * <p>204 rather than 404: the session exists and the client is asking a legitimate question during
     * setup, so an empty success is more accurate than "not found" and does not have to be handled as
     * an error case in the SPA.
     */
    @GetMapping
    public ResponseEntity<RubricResponse> get(@CurrentTa TaPrincipal ta,
                                              @PathVariable UUID sessionId) {
        return rubricService.find(ta, sessionId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping
    public RubricResponse save(@CurrentTa TaPrincipal ta,
                               @PathVariable UUID sessionId,
                               @Valid @RequestBody SaveRubricRequest request) {
        return rubricService.save(ta, sessionId, request);
    }

    @PostMapping("/export")
    public RubricExportResponse export(@CurrentTa TaPrincipal ta, @PathVariable UUID sessionId) {
        return rubricService.exportCsv(ta, sessionId);
    }
}
