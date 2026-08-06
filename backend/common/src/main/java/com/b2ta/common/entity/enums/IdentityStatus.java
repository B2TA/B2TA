package com.b2ta.common.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Confidence in the student identity resolved for a submission. */
public enum IdentityStatus implements PersistableEnum {

    VERIFIED("verified"),
    UNVERIFIED("unverified"),
    DISAMBIGUATION_REQUIRED("disambiguation_required");

    private final String dbValue;

    IdentityStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @JsonCreator
    public static IdentityStatus fromDbValue(String raw) {
        return EnumLookup.parse(IdentityStatus.class, raw);
    }
}
