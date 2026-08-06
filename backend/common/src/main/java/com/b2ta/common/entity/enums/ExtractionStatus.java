package com.b2ta.common.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Text extraction outcome for a {@link com.b2ta.common.entity.Submission}. */
public enum ExtractionStatus implements PersistableEnum {

    PENDING("pending"),
    COMPLETED("success"),
    FAILED("failed"),
    OVERSIZED("oversized");

    private final String dbValue;

    ExtractionStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    /** True when extracted text is available for matching and highlighting. */
    public boolean hasText() {
        return this == COMPLETED || this == OVERSIZED;
    }

    @JsonCreator
    public static ExtractionStatus fromDbValue(String raw) {
        return EnumLookup.parse(ExtractionStatus.class, raw);
    }
}
