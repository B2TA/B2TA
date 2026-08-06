package com.b2ta.api.controller;

import com.b2ta.api.security.CurrentTa;
import com.b2ta.api.security.TaPrincipal;
import com.b2ta.api.service.CommentService;
import com.b2ta.common.dto.comment.CommentSuggestRequest;
import com.b2ta.common.dto.comment.CommentSuggestResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** AI comment suggestions for one submission (task 5.9). */
@RestController
@RequestMapping("/api/sessions/{sessionId}/submissions/{submissionId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /**
     * Returns 1-5 candidate feedback snippets.
     *
     * <p>Answers within the 15-second budget of Requirement 12.7 or fails with 504; either way the
     * client keeps the feedback the TA has already typed.
     */
    @PostMapping("/suggest")
    public CommentSuggestResponse suggest(@CurrentTa TaPrincipal ta,
                                          @PathVariable UUID sessionId,
                                          @PathVariable UUID submissionId,
                                          @Valid @RequestBody(required = false)
                                          CommentSuggestRequest request) {
        return commentService.suggest(ta, sessionId, submissionId, request);
    }
}
