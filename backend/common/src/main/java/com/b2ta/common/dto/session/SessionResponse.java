package com.b2ta.common.dto.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {

    private UUID id;
    private String name;
    private Instant reviewConfirmedAt;
    private int submissionCount;
    private Instant createdAt;
    private Instant updatedAt;
}
