package com.b2ta.api.controller;

import com.b2ta.api.security.CurrentTa;
import com.b2ta.api.security.TaPrincipal;
import com.b2ta.api.service.AnalysisRequestService;
import com.b2ta.api.service.GradingService;
import com.b2ta.common.dto.grading.GradingRecordResponse;
import com.b2ta.common.dto.grading.SaveGradingRecordRequest;
import com.b2ta.common.dto.job.JobCreatedResponse;
import com.b2ta.common.dto.match.ConfirmedMatchDto;
import com.b2ta.common.dto.match.CreateManualMatchRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Grading record and match management for one submission (task 5.7).
 *
 * <p>Every handler takes the authenticated TA as an explicit parameter and passes it to the service,
 * which resolves the submission by (id, session id, TA id). There is no path that reaches a grading
 * record without that check.
 */
@RestController
@RequestMapping("/api/sessions/{sessionId}/submissions/{submissionId}")
@RequiredArgsConstructor
public class GradingController {

    private final GradingService gradingService;
    private final AnalysisRequestService analysisRequestService;

    /** Loads the grading record together with matches, analysis state, and extracted text. */
    @GetMapping("/grading")
    public GradingRecordResponse load(@CurrentTa TaPrincipal ta,
                                      @PathVariable UUID sessionId,
                                      @PathVariable UUID submissionId) {
        return gradingService.load(ta, sessionId, submissionId);
    }

    /** Saves the grading record atomically (Requirement 14.2). */
    @PutMapping("/grading")
    public GradingRecordResponse save(@CurrentTa TaPrincipal ta,
                                      @PathVariable UUID sessionId,
                                      @PathVariable UUID submissionId,
                                      @Valid @RequestBody SaveGradingRecordRequest request) {
        return gradingService.save(ta, sessionId, submissionId, request);
    }

    @PostMapping("/matches/{matchId}/confirm")
    public ConfirmedMatchDto confirmMatch(@CurrentTa TaPrincipal ta,
                                          @PathVariable UUID sessionId,
                                          @PathVariable UUID submissionId,
                                          @PathVariable UUID matchId) {
        return gradingService.confirmMatch(ta, sessionId, submissionId, matchId);
    }

    @PostMapping("/matches/{matchId}/reject")
    public ResponseEntity<Void> rejectMatch(@CurrentTa TaPrincipal ta,
                                            @PathVariable UUID sessionId,
                                            @PathVariable UUID submissionId,
                                            @PathVariable UUID matchId) {
        gradingService.rejectMatch(ta, sessionId, submissionId, matchId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/matches/manual")
    @ResponseStatus(HttpStatus.CREATED)
    public ConfirmedMatchDto createManualMatch(@CurrentTa TaPrincipal ta,
                                               @PathVariable UUID sessionId,
                                               @PathVariable UUID submissionId,
                                               @Valid @RequestBody CreateManualMatchRequest request) {
        return gradingService.createManualMatch(ta, sessionId, submissionId,
                request.getCriterionId(), request.getPassageStart(), request.getPassageEnd(),
                request.getRationale());
    }

    /** Removes a confirmed match. The path id is a {@code confirmed_match} id. */
    @DeleteMapping("/matches/{confirmedMatchId}")
    public ResponseEntity<Void> deleteConfirmedMatch(@CurrentTa TaPrincipal ta,
                                                     @PathVariable UUID sessionId,
                                                     @PathVariable UUID submissionId,
                                                     @PathVariable UUID confirmedMatchId) {
        gradingService.deleteConfirmedMatch(ta, sessionId, submissionId, confirmedMatchId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Re-runs analysis for one criterion (Requirement 6.14).
     *
     * <p>Returns a job id rather than the new matches: a Bedrock invocation per chunk can exceed the
     * request budget, so the browser polls the job instead of holding the connection open.
     */
    @PostMapping("/reanalyze/{criterionId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobCreatedResponse reanalyze(@CurrentTa TaPrincipal ta,
                                        @PathVariable UUID sessionId,
                                        @PathVariable UUID submissionId,
                                        @PathVariable UUID criterionId) {
        return analysisRequestService.requestCriterionReanalysis(ta, sessionId, submissionId,
                criterionId);
    }

    /** Queues analysis for every criterion of this submission that has not been analysed yet. */
    @PostMapping("/analyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobCreatedResponse analyze(@CurrentTa TaPrincipal ta,
                                      @PathVariable UUID sessionId,
                                      @PathVariable UUID submissionId,
                                      @RequestParam(defaultValue = "false") boolean force) {
        return analysisRequestService.requestSubmissionAnalysis(ta, sessionId, submissionId, force);
    }
}
