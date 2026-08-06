package com.b2ta.common.entity.converter;

import com.b2ta.common.entity.enums.AnalysisState;
import com.b2ta.common.entity.enums.DiscardReason;
import com.b2ta.common.entity.enums.ExtractionStatus;
import com.b2ta.common.entity.enums.IdentityStatus;
import com.b2ta.common.entity.enums.JobStatus;
import com.b2ta.common.entity.enums.JobType;
import com.b2ta.common.entity.enums.MatchOrigin;
import com.b2ta.common.entity.enums.MatchState;
import com.b2ta.common.entity.enums.PersistableEnum;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converters mapping each domain enum to the lower-snake-case vocabulary enforced by the
 * PostgreSQL CHECK constraints in {@code V1__initial_schema.sql}.
 *
 * <p>{@code @Enumerated(EnumType.STRING)} would write the Java constant name (for example
 * {@code MANUAL}) and every insert would violate its CHECK constraint. Converters are declared
 * with {@code autoApply = true} so entity fields need no per-field annotation, but the entities
 * also carry an explicit {@code @Convert} for readability.
 */
public final class PersistableEnumConverters {

    private PersistableEnumConverters() {
    }

    /** Base class handling the null-safe {@code dbValue} round trip. */
    private abstract static class Base<E extends Enum<E> & PersistableEnum>
            implements AttributeConverter<E, String> {

        @Override
        public String convertToDatabaseColumn(E attribute) {
            return attribute == null ? null : attribute.getDbValue();
        }
    }

    @Converter(autoApply = true)
    public static class MatchStateConverter extends Base<MatchState> {
        @Override
        public MatchState convertToEntityAttribute(String dbData) {
            return MatchState.fromDbValue(dbData);
        }
    }

    @Converter(autoApply = true)
    public static class MatchOriginConverter extends Base<MatchOrigin> {
        @Override
        public MatchOrigin convertToEntityAttribute(String dbData) {
            return MatchOrigin.fromDbValue(dbData);
        }
    }

    @Converter(autoApply = true)
    public static class JobTypeConverter extends Base<JobType> {
        @Override
        public JobType convertToEntityAttribute(String dbData) {
            return JobType.fromDbValue(dbData);
        }
    }

    @Converter(autoApply = true)
    public static class JobStatusConverter extends Base<JobStatus> {
        @Override
        public JobStatus convertToEntityAttribute(String dbData) {
            return JobStatus.fromDbValue(dbData);
        }
    }

    @Converter(autoApply = true)
    public static class ExtractionStatusConverter extends Base<ExtractionStatus> {
        @Override
        public ExtractionStatus convertToEntityAttribute(String dbData) {
            return ExtractionStatus.fromDbValue(dbData);
        }
    }

    @Converter(autoApply = true)
    public static class IdentityStatusConverter extends Base<IdentityStatus> {
        @Override
        public IdentityStatus convertToEntityAttribute(String dbData) {
            return IdentityStatus.fromDbValue(dbData);
        }
    }

    @Converter(autoApply = true)
    public static class DiscardReasonConverter extends Base<DiscardReason> {
        @Override
        public DiscardReason convertToEntityAttribute(String dbData) {
            return DiscardReason.fromDbValue(dbData);
        }
    }

    @Converter(autoApply = true)
    public static class AnalysisStateConverter extends Base<AnalysisState> {
        @Override
        public AnalysisState convertToEntityAttribute(String dbData) {
            return AnalysisState.fromDbValue(dbData);
        }
    }
}
