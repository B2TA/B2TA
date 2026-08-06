package com.b2ta.api.controller;

import com.b2ta.api.security.CurrentTa;
import com.b2ta.api.security.TaPrincipal;
import com.b2ta.api.service.ReviewService;
import com.b2ta.common.dto.review.ReviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Pre-export review screen (task 5.10). */
@RestController
@RequestMapping("/api/sessions/{sessionId}/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** Every submission with per-criterion scores, totals, and flags (Requirement 15.2-15.6). */
    @GetMapping
    public ReviewResponse getReview(@CurrentTa TaPrincipal ta, @PathVariable UUID sessionId) {
        return reviewService.buildReview(ta, sessionId);
    }

    /**
     * Records the confirmation and returns the reviewed state.
     *
     * <p>Returning the review rather than an empty body means the client renders the confirmation
     * timestamp from the server's clock, so what it displays matches what gates the export.
     */
    @PostMapping("/confirm")
    public ReviewResponse confirm(@CurrentTa TaPrincipal ta, @PathVariable UUID sessionId) {
        return reviewService.confirm(ta, sessionId);
    }
}
