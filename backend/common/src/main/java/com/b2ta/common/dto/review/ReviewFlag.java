package com.b2ta.common.dto.review;

import com.b2ta.common.entity.enums.EnumLookupSupport;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A condition on a submission that a TA should look at before exporting
 * (Requirements 15.3, 15.4, 15.5).
 *
 * <p>The flag names are part of the API contract: the review screen maps each to its own label and
 * count, so adding a condition here requires a matching case in the UI rather than a generic
 * "flagged" badge that tells the grader nothing about what to check.
 */
public enum ReviewFlag {

    /** One or more criteria have neither a selected level nor an override (Req 15.3). */
    INCOMPLETE_GRADING("incomplete_grading"),

    /** Text extraction failed, so the submission was graded without a readable document (Req 15.4). */
    EXTRACTION_FAILED("extraction_failed"),

    /** Only part of the document was analysed because of its size (Req 15.4). */
    OVERSIZED("oversized"),

    /** The student name was derived from the filename but not verified (Req 15.4). */
    UNVERIFIED_IDENTITY("unverified_identity"),

    /** The filename matched more than one roster entry (Req 15.4). */
    DISAMBIGUATION_REQUIRED("disambiguation_required"),

    /** At least one criterion carries a manual point override (Req 15.5). */
    MANUAL_OVERRIDES("manual_overrides");

    private final String wireValue;

    ReviewFlag(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String getWireValue() {
        return wireValue;
    }

    @JsonCreator
    public static ReviewFlag fromWireValue(String raw) {
        return EnumLookupSupport.parse(ReviewFlag.class, raw, ReviewFlag::getWireValue);
    }
}
