package com.b2ta.common.entity.enums;

import java.util.function.Function;

/**
 * Public parsing helper for enums that carry an explicit wire value but are not persisted.
 *
 * <p>{@link PersistableEnum} covers the persisted enums; this covers the DTO-only ones, so both kinds
 * accept either the wire value or the Java constant name when deserializing.
 */
public final class EnumLookupSupport {

    private EnumLookupSupport() {
    }

    public static <E extends Enum<E>> E parse(Class<E> type, String raw,
                                              Function<E, String> wireValue) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        for (E candidate : type.getEnumConstants()) {
            if (wireValue.apply(candidate).equalsIgnoreCase(trimmed)
                    || candidate.name().equalsIgnoreCase(trimmed)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException(
                "Unknown %s value: %s".formatted(type.getSimpleName(), trimmed));
    }
}
