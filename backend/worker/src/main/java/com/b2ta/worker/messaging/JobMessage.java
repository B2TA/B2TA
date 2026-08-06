package com.b2ta.worker.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Message payload received from SQS representing an async job to be processed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobMessage {

    private String jobId;
    private String jobType;
    private String sessionId;
    private Map<String, Object> payload;
}
