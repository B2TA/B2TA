package com.b2ta.api.controller;

import com.b2ta.api.security.CurrentTa;
import com.b2ta.api.security.TaPrincipal;
import com.b2ta.api.service.ExportService;
import com.b2ta.common.dto.export.ExportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Grade exports (task 5.11).
 *
 * <p>Both endpoints require a confirmed review (Requirement 15.1) and return a pre-signed download
 * URL rather than the file bytes, so the browser downloads from S3 directly and the API is not a
 * proxy for a 150-row file.
 */
@RestController
@RequestMapping("/api/sessions/{sessionId}/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @PostMapping("/generic")
    public ExportResponse exportGeneric(@CurrentTa TaPrincipal ta, @PathVariable UUID sessionId) {
        return exportService.exportGeneric(ta, sessionId);
    }

    @PostMapping("/canvas")
    public ExportResponse exportCanvas(@CurrentTa TaPrincipal ta, @PathVariable UUID sessionId) {
        return exportService.exportCanvas(ta, sessionId);
    }
}
