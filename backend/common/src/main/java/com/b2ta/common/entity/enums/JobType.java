package com.b2ta.common.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Kind of long-running operation tracked by an {@link com.b2ta.common.entity.AsyncJob}. */
public enum JobType implements PersistableEnum {

    RUBRIC_PARSE("rubric_parse"),
    SUBMISSION_INGEST("submission_ingest"),
    MATCH_ANALYSIS("match_analysis");

    private final String dbValue;

    JobType(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @JsonCreator
    public static JobType fromDbValue(String raw) {
        return EnumLookup.parse(JobType.class, raw);
    }
}
