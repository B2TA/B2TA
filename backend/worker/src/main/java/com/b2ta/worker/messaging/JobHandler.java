package com.b2ta.worker.messaging;

import com.b2ta.common.entity.enums.JobType;

/**
 * Interface for job handlers that process async work.
 * Each handler handles a specific job type and is routed to by the SqsListener.
 */
public interface JobHandler {

    /**
     * Returns the job type this handler processes.
     */
    JobType getJobType();

    /**
     * Processes the job message.
     *
     * @param message the job message received from SQS
     * @throws Exception if processing fails; the message will return to the queue
     *                   via visibility timeout for retry (up to maxReceiveCount=3 before DLQ)
     */
    void handle(JobMessage message) throws Exception;
}
