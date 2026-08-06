package com.b2ta.common.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Why a candidate match produced by the Match_Engine was not retained. */
public enum DiscardReason implements PersistableEnum {

    OVERLAP_DEDUPLICATION("overlap_deduplication"),
    LOW_CONFIDENCE("low_confidence"),
    USER_REJECTED("user_rejected"),
    STALE("stale");

    private final String dbValue;

    DiscardReason(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @JsonCreator
    public static DiscardReason fromDbValue(String raw) {
        return EnumLookup.parse(DiscardReason.class, raw);
    }
}
