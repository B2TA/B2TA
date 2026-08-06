package com.b2ta.common.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Per (submission, criterion) state of Match_Engine analysis.
 *
 * <p>Requirements 6.6-6.8 and 6.14 need this distinct from "zero matches found": a criterion with
 * no evidence renders the no-evidence-found state, while a criterion whose Bedrock invocations all
 * failed renders the analysis-unavailable state.
 */
public enum AnalysisState implements PersistableEnum {

    /** Queued but not yet started. */
    PENDING("pending"),
    /** A worker is currently analysing this pair. */
    IN_PROGRESS("in_progress"),
    /** Analysis finished; retained matches (possibly zero) are authoritative. */
    COMPLETE("complete"),
    /** Bedrock failed on every attempt; no analysis is available (Req 6.7, 6.8). */
    UNAVAILABLE("unavailable");

    private final String dbValue;

    AnalysisState(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @JsonCreator
    public static AnalysisState fromDbValue(String raw) {
        return EnumLookup.parse(AnalysisState.class, raw);
    }
}
