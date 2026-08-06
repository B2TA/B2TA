package com.b2ta.common.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Lifecycle of an AI-produced match.
 *
 * <p>{@code PENDING} is stored as {@code 'suggested'} because that is the vocabulary used by the
 * {@code chk_suggested_match_state} constraint and by the frontend {@code MatchState} union.
 */
public enum MatchState implements PersistableEnum {

    PENDING("suggested"),
    CONFIRMED("confirmed"),
    REJECTED("rejected");

    private final String dbValue;

    MatchState(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @JsonCreator
    public static MatchState fromDbValue(String raw) {
        return EnumLookup.parse(MatchState.class, raw);
    }
}
