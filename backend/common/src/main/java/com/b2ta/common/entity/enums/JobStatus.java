package com.b2ta.common.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Status of an {@link com.b2ta.common.entity.AsyncJob}. */
public enum JobStatus implements PersistableEnum {

    QUEUED("pending"),
    IN_PROGRESS("in_progress"),
    COMPLETED("complete"),
    FAILED("failed");

    private final String dbValue;

    JobStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    /** True once the job reached a state that will not change again. */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }

    @JsonCreator
    public static JobStatus fromDbValue(String raw) {
        return EnumLookup.parse(JobStatus.class, raw);
    }
}
