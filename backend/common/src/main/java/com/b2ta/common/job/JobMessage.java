package com.b2ta.common.job;

import com.b2ta.common.entity.enums.JobType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * SQS message body describing one unit of asynchronous work.
 *
 * <p>Carries identifiers only. The worker reloads everything it needs from the database, so a message
 * that sits in the queue while the session is edited processes the current state rather than a stale
 * snapshot, and no student text or feedback ever passes through the queue.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobMessage {

    /** The {@code async_job} row this message reports progress against. */
    private UUID jobId;

    private JobType jobType;

    private UUID sessionId;

    /** Target submission for {@link JobType#MATCH_ANALYSIS}; null for session-wide jobs. */
    private UUID submissionId;

    /** Single criterion to re-analyse; null to analyse every criterion of the rubric. */
    private UUID criterionId;

    /** True to re-analyse pairs that already completed, marking prior suggestions stale. */
    private boolean force;
}
