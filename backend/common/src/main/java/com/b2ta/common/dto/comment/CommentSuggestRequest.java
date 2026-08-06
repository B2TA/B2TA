package com.b2ta.common.dto.comment;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request for comment suggestions.
 *
 * <p>Both fields are optional. The suggestion is generated from the saved grading record, so a TA who
 * has just saved can send an empty body. {@code criterionId} narrows the request to one criterion's
 * feedback field, and {@code currentDraft} lets the assistant avoid repeating what the TA already
 * wrote.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentSuggestRequest {

    /** Narrow suggestions to one criterion; null for overall feedback. */
    private UUID criterionId;

    @Size(max = 10000, message = "Draft feedback must be 10,000 characters or fewer")
    private String currentDraft;
}
