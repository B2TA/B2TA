package com.b2ta.common.entity.enums;

/**
 * Implemented by every persisted enum in the domain model.
 *
 * <p>The PostgreSQL schema constrains these columns with CHECK constraints using the
 * lower-snake-case vocabulary of the specification (for example {@code 'ta_confirmed'},
 * {@code 'in_progress'}). The Java enum constant names do not always match that vocabulary,
 * so every enum carries an explicit {@code dbValue} and is mapped through an
 * {@link jakarta.persistence.AttributeConverter} rather than {@code @Enumerated(STRING)}.
 *
 * <p>The same {@code dbValue} is used for JSON serialization so the wire format matches the
 * TypeScript union types declared in {@code src/app/types/index.ts}.
 */
public interface PersistableEnum {

    /** The value stored in PostgreSQL and emitted over JSON. */
    String getDbValue();
}
