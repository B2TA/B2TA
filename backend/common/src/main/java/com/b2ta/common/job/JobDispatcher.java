package com.b2ta.common.job;

import com.b2ta.common.config.AwsProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Publishes a {@link JobMessage} for the Worker service to pick up.
 *
 * <p>When no queue is configured the message is handed to a {@link LocalJobExecutor} instead, if one
 * is present. That is what lets the whole flow run on a developer machine with only the API service
 * started: the same handler code runs, just on a background thread in-process rather than after an
 * SQS round trip. Deployments always have a queue, so the local path is not a second implementation
 * of the work itself — only of its delivery.
 */
@Slf4j
@Component
public class JobDispatcher {

    private final AwsProperties awsProperties;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<SqsClient> sqsClient;
    private final ObjectProvider<LocalJobExecutor> localExecutor;

    public JobDispatcher(AwsProperties awsProperties,
                         ObjectMapper objectMapper,
                         ObjectProvider<SqsClient> sqsClient,
                         ObjectProvider<LocalJobExecutor> localExecutor) {
        this.awsProperties = awsProperties;
        this.objectMapper = objectMapper;
        this.sqsClient = sqsClient;
        this.localExecutor = localExecutor;
    }

    /**
     * Dispatches the message.
     *
     * @return true when the message was accepted for processing
     */
    public boolean dispatch(JobMessage message) {
        if (awsProperties.getSqs().isConfigured()) {
            return sendToQueue(message);
        }
        LocalJobExecutor executor = localExecutor.getIfAvailable();
        if (executor != null) {
            log.warn("No SQS queue configured; running {} job {} in-process",
                    message.getJobType().getDbValue(), message.getJobId());
            executor.execute(message);
            return true;
        }
        log.error("No SQS queue configured and no local executor available; job {} was not dispatched",
                message.getJobId());
        return false;
    }

    private boolean sendToQueue(JobMessage message) {
        SqsClient client = sqsClient.getIfAvailable();
        if (client == null) {
            log.error("SQS queue is configured but no client bean is available");
            return false;
        }
        try {
            client.sendMessage(SendMessageRequest.builder()
                    .queueUrl(awsProperties.getSqs().getQueueUrl())
                    .messageBody(objectMapper.writeValueAsString(message))
                    .build());
            log.info("Enqueued {} job {}", message.getJobType().getDbValue(), message.getJobId());
            return true;
        } catch (Exception e) {
            log.error("Failed to enqueue job {}", message.getJobId(), e);
            return false;
        }
    }
}
