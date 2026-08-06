package com.b2ta.api.canvas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Result of a gradebook sync.
 *
 * <p>{@code canvasTotal} is what Canvas reports after the write, not what the client
 * computed — showing our own arithmetic back to the TA would hide a mismatch between
 * what we sent and what Canvas recorded (Requirement 5.4).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncResponse {

    private boolean synced;

    private String userId;

    private Double canvasTotal;

    private Instant syncedAt;

    private String syncedBy;

    private int criteriaWritten;

    /**
     * True when the write was recorded locally but never reached Canvas because the
     * integration is running in fixture mode.
     */
    private boolean fixtureMode;
}
