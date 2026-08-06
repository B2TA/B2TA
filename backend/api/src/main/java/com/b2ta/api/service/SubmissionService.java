package com.b2ta.api.service;

import com.b2ta.api.repository.GradingSessionRepository;
import com.b2ta.api.repository.SubmissionRepository;
import com.b2ta.api.security.SecurityContextHelper;
import com.b2ta.common.dto.submission.SubmissionResponse;
import com.b2ta.common.dto.submission.UpdateIdentityRequest;
import com.b2ta.common.entity.GradingSession;
import com.b2ta.common.entity.Submission;
import com.b2ta.common.entity.enums.IdentityStatus;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubmissionService {

    private static final int BATCH_LIMIT = 150;

    private final SubmissionRepository submissionRepository;
    private final GradingSessionRepository sessionRepository;
    private final SecurityContextHelper securityContextHelper;

    /**
     * Lists all submissions for a session ordered by position.
     * Enforces tenant isolation by verifying session ownership.
     */
    @Transactional(readOnly = true)
    public List<SubmissionResponse> listSubmissions(UUID sessionId) {
        GradingSession session = resolveSession(sessionId);
        List<Submission> submissions = submissionRepository.findAllBySessionIdOrderByPositionAsc(session.getId());
        return submissions.stream().map(this::toResponse).toList();
    }

    /**
     * Updates the student display name on a submission.
     * Validates the name (1-200 chars, non-blank), marks as VERIFIED,
     * then re-evaluates case-insensitive duplicates across all submissions in the batch.
     */
    @Transactional
    public SubmissionResponse updateIdentity(UUID sessionId, UUID submissionId, UpdateIdentityRequest request) {
        GradingSession session = resolveSession(sessionId);

        Submission submission = submissionRepository.findByIdAndSessionId(submissionId, session.getId())
                .orElseThrow(() -> new EntityNotFoundException("Submission not found"));

        String newName = request.getStudentDisplayName().trim();
        submission.setStudentDisplayName(newName);
        submission.setIdentityStatus(IdentityStatus.VERIFIED);

        submissionRepository.save(submission);

        // Re-evaluate duplicate detection across all submissions in the batch
        reevaluateDuplicates(session.getId());

        // Return the updated submission
        Submission refreshed = submissionRepository.findByIdAndSessionId(submissionId, session.getId())
                .orElseThrow(() -> new EntityNotFoundException("Submission not found"));
        return toResponse(refreshed);
    }

    /**
     * Confirms all student identities in the batch. Sets all submissions' identity
     * status to VERIFIED (acknowledging their current display names).
     */
    @Transactional
    public void confirmIdentities(UUID sessionId) {
        GradingSession session = resolveSession(sessionId);
        List<Submission> submissions = submissionRepository.findAllBySessionIdOrderByPositionAsc(session.getId());

        for (Submission submission : submissions) {
            if (submission.getIdentityStatus() != IdentityStatus.VERIFIED) {
                submission.setIdentityStatus(IdentityStatus.VERIFIED);
            }
        }

        submissionRepository.saveAll(submissions);
    }

    /**
     * Validates that adding more submissions would not exceed the batch limit.
     * Throws IllegalStateException if the limit would be exceeded.
     */
    public void validateBatchLimit(UUID sessionId, int additionalCount) {
        long currentCount = submissionRepository.countBySessionId(sessionId);
        if (currentCount + additionalCount > BATCH_LIMIT) {
            throw new IllegalStateException(
                    String.format("Batch limit exceeded: current %d + requested %d exceeds maximum of %d submissions",
                            currentCount, additionalCount, BATCH_LIMIT));
        }
    }

    /**
     * Re-evaluates case-insensitive duplicate detection across all submissions in a session.
     * Submissions with names that match another submission (case-insensitive) are marked
     * DISAMBIGUATION_REQUIRED. Submissions with unique names that were previously flagged
     * have their disambiguation flag cleared back to VERIFIED.
     */
    private void reevaluateDuplicates(UUID sessionId) {
        List<Submission> submissions = submissionRepository.findAllBySessionIdOrderByPositionAsc(sessionId);

        // Count occurrences of each name (case-insensitive)
        java.util.Map<String, Long> nameCounts = submissions.stream()
                .filter(s -> s.getStudentDisplayName() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        s -> s.getStudentDisplayName().toLowerCase(),
                        java.util.stream.Collectors.counting()));

        for (Submission submission : submissions) {
            if (submission.getStudentDisplayName() == null) {
                continue;
            }

            String nameLower = submission.getStudentDisplayName().toLowerCase();
            long count = nameCounts.getOrDefault(nameLower, 0L);

            if (count > 1) {
                submission.setIdentityStatus(IdentityStatus.DISAMBIGUATION_REQUIRED);
            } else if (submission.getIdentityStatus() == IdentityStatus.DISAMBIGUATION_REQUIRED) {
                // No longer duplicated — clear the flag
                submission.setIdentityStatus(IdentityStatus.VERIFIED);
            }
        }

        submissionRepository.saveAll(submissions);
    }

    /**
     * Resolves a session by ID, enforcing tenant isolation.
     * Returns 404 if session does not exist or does not belong to the current TA.
     */
    private GradingSession resolveSession(UUID sessionId) {
        UUID taId = securityContextHelper.getCurrentTaId();
        return sessionRepository.findByIdAndTaId(sessionId, taId)
                .orElseThrow(() -> new EntityNotFoundException("Session not found"));
    }

    private SubmissionResponse toResponse(Submission submission) {
        return SubmissionResponse.builder()
                .id(submission.getId())
                .originalFilename(submission.getOriginalFilename())
                .studentDisplayName(submission.getStudentDisplayName())
                .canvasSubmissionId(submission.getCanvasSubmissionId())
                .identityStatus(submission.getIdentityStatus())
                .extractionStatus(submission.getExtractionStatus())
                .extractionFailureReason(submission.getExtractionFailureReason())
                .extractedCharCount(submission.getExtractedCharCount())
                .isOversized(submission.getIsOversized())
                .position(submission.getPosition())
                .createdAt(submission.getCreatedAt())
                .build();
    }
}
