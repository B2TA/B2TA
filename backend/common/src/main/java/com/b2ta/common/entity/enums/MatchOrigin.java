package com.b2ta.common.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How a {@link com.b2ta.common.entity.ConfirmedMatch} came to exist.
 *
 * <p>{@code AI_SUGGESTED} means the TA confirmed a Suggested_Match (Req 10.1) and is stored as
 * {@code 'ta_confirmed'}. {@code MANUAL} means the TA selected the passage themselves
 * (Req 10.3) and is stored as {@code 'ta_authored'}.
 */
public enum MatchOrigin implements PersistableEnum {

    AI_SUGGESTED("ta_confirmed"),
    MANUAL("ta_authored");

    private final String dbValue;

    MatchOrigin(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @JsonCreator
    public static MatchOrigin fromDbValue(String raw) {
        return EnumLookup.parse(MatchOrigin.class, raw);
    }
}
