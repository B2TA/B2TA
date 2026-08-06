package com.b2ta.api.service;

import com.b2ta.api.repository.AsyncJobRepository;
import com.b2ta.api.security.SecurityContextHelper;
import com.b2ta.common.dto.job.JobCreatedResponse;
import com.b2ta.common.dto.job.JobStatusResponse;
import com.b2ta.common.entity.AsyncJob;
import com.b2ta.common.entity.GradingSession;
import com.b2ta.common.entity.enums.JobStatus;
import com.b2ta.common.entity.enums.JobType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final AsyncJobRepository asyncJobRepository;
    private final SecurityContextHelper securityContextHelper;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.queue-url}")
    private String queueUrl;

    /**
     * Creates an AsyncJob record with status QUEUED and publishes a message to SQS.
     * Returns the job ID immediately so the client can poll for status.
     */
    @Transactional
    public JobCreatedResponse createAndPublishJob(GradingSession session, JobType jobType, Map<String, Object> payload) {
        AsyncJob job = AsyncJob.builder()
                .session(session)
                .jobType(jobType)
                .status(JobStatus.QUEUED)
                .progressCurrent(0)
                .progressTotal(0)
                .build();

        job = asyncJobRepository.save(job);

        publishToSqs(job.getId(), jobType, session.getId(), payload);

        log.info("Created async job id={} type={} sessionId={}", job.getId(), jobType, session.getId());

        return JobCreatedResponse.builder()
                .jobId(job.getId())
                .build();
    }

    /**
     * Returns the current status of a job, enforcing tenant isolation via the session's TA ownership.
     */
    @Transactional(readOnly = true)
    public JobStatusResponse getJobStatus(UUID jobId) {
        UUID taId = securityContextHelper.getCurrentTaId();

        AsyncJob job = asyncJobRepository.findByIdAndSessionTaId(jobId, taId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

        return JobStatusResponse.builder()
                .id(job.getId())
                .sessionId(job.getSession().getId())
                .jobType(job.getJobType())
                .status(job.getStatus())
                .progressCurrent(job.getProgressCurrent())
                .progressTotal(job.getProgressTotal())
                .failureReason(job.getFailureReason())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    private void publishToSqs(UUID jobId, JobType jobType, UUID sessionId, Map<String, Object> payload) {
        try {
            Map<String, Object> messageBody = Map.of(
                    "jobId", jobId.toString(),
                    "jobType", jobType.name(),
                    "sessionId", sessionId.toString(),
                    "payload", payload != null ? payload : Map.of()
            );

            String messageJson = objectMapper.writeValueAsString(messageBody);

            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageJson)
                    .build();

            sqsClient.sendMessage(request);

            log.info("Published SQS message for job id={} type={}", jobId, jobType);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize SQS message for job id={}", jobId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to enqueue job");
        }
    }
}
