package com.b2ta.worker.sqs;

import com.b2ta.common.config.AwsProperties;
import com.b2ta.common.entity.enums.JobType;
import com.b2ta.common.job.JobMessage;
import com.b2ta.common.job.JobService;
import com.b2ta.worker.handler.MatchEngineHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Long-polls the job queue and routes messages to handlers.
 *
 * <p>A message is deleted only after its handler returns. A worker task that dies mid-message leaves
 * the message in flight, and SQS redelivers it once the visibility timeout expires — which is what
 * gives the resumability Requirement 19.7 asks for without any bookkeeping of our own.
 *
 * <p>Long polling with a 20-second wait means an idle worker makes three requests a minute rather than
 * spinning, and a message is picked up within milliseconds of arriving.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqsJobListener {

    private final AwsProperties awsProperties;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final MatchEngineHandler matchEngineHandler;
    private final JobService jobService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService pollerThread;

    @PostConstruct
    void start() {
        if (!awsProperties.getSqs().isConfigured()) {
            log.warn("aws.sqs.queue-url is not set; the worker will not poll for jobs");
            return;
        }
        running.set(true);
        pollerThread = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sqs-job-poller");
            thread.setDaemon(true);
            return thread;
        });
        pollerThread.submit(this::pollLoop);
        log.info("Polling {} for jobs", awsProperties.getSqs().getQueueUrl());
    }

    @PreDestroy
    void stop() {
        running.set(false);
        if (pollerThread != null) {
            pollerThread.shutdownNow();
            try {
                pollerThread.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void pollLoop() {
        while (running.get()) {
            try {
                List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                                .queueUrl(awsProperties.getSqs().getQueueUrl())
                                .maxNumberOfMessages(1)
                                .waitTimeSeconds(awsProperties.getSqs().getWaitTimeSeconds())
                                .visibilityTimeout(
                                        awsProperties.getSqs().getVisibilityTimeoutSeconds())
                                .build())
                        .messages();

                for (Message message : messages) {
                    process(message);
                }
            } catch (Exception e) {
                if (!running.get()) {
                    return;
                }
                // A transient SQS or network failure must not kill the poller. Back off briefly so a
                // persistent failure does not become a hot loop against the API.
                log.error("Job polling failed; retrying in 5 seconds", e);
                sleep(5000);
            }
        }
    }

    private void process(Message message) {
        JobMessage job;
        try {
            job = objectMapper.readValue(message.body(), JobMessage.class);
        } catch (Exception e) {
            // Unparseable body: retrying cannot help, so it is deleted rather than left to be
            // redelivered until it reaches the dead-letter queue.
            log.error("Discarding unparseable job message {}", message.messageId(), e);
            delete(message);
            return;
        }

        try {
            if (job.getJobType() == JobType.MATCH_ANALYSIS) {
                matchEngineHandler.handle(job);
            } else {
                log.warn("No handler for job type {}; marking job {} failed",
                        job.getJobType(), job.getJobId());
                jobService.markFailed(job.getJobId(),
                        "This deployment cannot process " + job.getJobType().getDbValue() + " jobs");
            }
            delete(message);
        } catch (RuntimeException e) {
            // Left in flight on purpose: SQS redelivers after the visibility timeout, and after the
            // configured receive count the message lands in the dead-letter queue.
            log.error("Job {} failed; leaving the message for redelivery", job.getJobId(), e);
        }
    }

    private void delete(Message message) {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(awsProperties.getSqs().getQueueUrl())
                .receiptHandle(message.receiptHandle())
                .build());
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
