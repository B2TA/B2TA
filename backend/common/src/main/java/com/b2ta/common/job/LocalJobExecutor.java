package com.b2ta.common.job;

/**
 * Runs a job in-process when no SQS queue is configured.
 *
 * <p>Implemented by whichever service is capable of doing the work; the dispatcher does not care
 * which. Implementations must return immediately and run the job on another thread, because the
 * caller is an HTTP request thread that has to answer with a job id inside its own budget.
 */
public interface LocalJobExecutor {

    void execute(JobMessage message);
}
