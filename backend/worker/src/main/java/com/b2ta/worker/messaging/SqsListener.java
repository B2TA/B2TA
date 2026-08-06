package com.b2ta.worker.messaging;

import com.b2ta.common.entity.enums.JobStatus;
import com.b2ta.common.entity.enums.JobType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Long-polls SQS for job messages and routes them to the appropriate JobHandler.
 * <p>
 * Polling uses a 20-second wait time. On success, the message is deleted from the queue.
 * On failure, the message is left in the queue and will become visible again after the
 * visibility timeout (300s) expires. After maxReceiveCount=3 retries, the message moves
 * to the dead letter queue.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SqsListener {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final JobStatusUpdater jobStatusUpdater;
    private final List<JobHandler> jobHandlers;

    @Value("${aws.sqs.queue-url}")
    private String queueUrl;

    @Value("${aws.sqs.wait-time-seconds:20}")
    private int waitTimeSeconds;

    @Value("${aws.sqs.visibility-timeout-seconds:300}")
    private int visibilityTimeoutSeconds;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService pollingExecutor;
    private Map<JobType, JobHandler> handlerMap;

    @PostConstruct
    public void start() {
        handlerMap = jobHandlers.stream()
                .collect(Collectors.toMap(JobHandler::getJobType, Function.identity()));

        log.info("SqsListener starting with {} registered handler(s): {}",
                handlerMap.size(), handlerMap.keySet());

        running.set(true);
        pollingExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sqs-listener");
            t.setDaemon(true);
            return t;
        });
        pollingExecutor.submit(this::pollLoop);
    }

    @PreDestroy
    public void stop() {
        log.info("SqsListener shutting down");
        running.set(false);
        if (pollingExecutor != null) {
            pollingExecutor.shutdownNow();
        }
    }

    private void pollLoop() {
        while (running.get()) {
            try {
                pollAndProcess();
            } catch (Exception e) {
                if (running.get()) {
                    log.error("Error in SQS poll loop, retrying in 5s", e);
                    sleep(5000);
                }
            }
        }
    }

    private void pollAndProcess() {
        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(waitTimeSeconds)
                .visibilityTimeout(visibilityTimeoutSeconds)
                .build();

        ReceiveMessageResponse response = sqsClient.receiveMessage(request);
        List<Message> messages = response.messages();

        if (messages.isEmpty()) {
            return;
        }

        log.debug("Received {} message(s) from SQS", messages.size());

        for (Message message : messages) {
            processMessage(message);
        }
    }

    private void processMessage(Message message) {
        JobMessage jobMessage;
        try {
            jobMessage = objectMapper.readValue(message.body(), JobMessage.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse SQS message body, deleting invalid message. messageId={}",
                    message.messageId(), e);
            deleteMessage(message.receiptHandle());
            return;
        }

        UUID jobId = UUID.fromString(jobMessage.getJobId());
        JobType jobType;
        try {
            jobType = JobType.valueOf(jobMessage.getJobType());
        } catch (IllegalArgumentException e) {
            log.error("Unknown job type '{}' for jobId={}, deleting message",
                    jobMessage.getJobType(), jobId);
            deleteMessage(message.receiptHandle());
            return;
        }

        JobHandler handler = handlerMap.get(jobType);
        if (handler == null) {
            log.error("No handler registered for job type {} (jobId={}), deleting message",
                    jobType, jobId);
            deleteMessage(message.receiptHandle());
            return;
        }

        try {
            jobStatusUpdater.updateStatus(jobId, JobStatus.IN_PROGRESS, null);

            log.info("Processing job id={} type={}", jobId, jobType);
            handler.handle(jobMessage);

            jobStatusUpdater.updateStatus(jobId, JobStatus.COMPLETED, null);
            deleteMessage(message.receiptHandle());

            log.info("Successfully completed job id={} type={}", jobId, jobType);
        } catch (Exception e) {
            log.error("Failed to process job id={} type={}", jobId, jobType, e);
            jobStatusUpdater.updateStatus(jobId, JobStatus.FAILED, truncate(e.getMessage(), 500));
            // Do NOT delete the message — let visibility timeout expire so SQS can redeliver.
            // After maxReceiveCount=3 retries, the message moves to the DLQ.
        }
    }

    private void deleteMessage(String receiptHandle) {
        try {
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(receiptHandle)
                    .build());
        } catch (Exception e) {
            log.error("Failed to delete SQS message", e);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
