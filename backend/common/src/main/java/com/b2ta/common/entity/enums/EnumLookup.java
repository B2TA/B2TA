package com.b2ta.common.entity.enums;

import java.util.Locale;

/** Shared parsing helper for {@link PersistableEnum} implementations. */
final class EnumLookup {

    private EnumLookup() {
    }

    /**
     * Resolves a stored or wire value to an enum constant.
     *
     * <p>Accepts the canonical {@code dbValue} as well as the Java constant name, so payloads
     * produced before the converter existed still deserialize.
     */
    static <E extends Enum<E> & PersistableEnum> E parse(Class<E> type, String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        for (E candidate : type.getEnumConstants()) {
            if (candidate.getDbValue().equalsIgnoreCase(trimmed)
                    || candidate.name().equalsIgnoreCase(trimmed)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException(
                "Unknown %s value: %s".formatted(type.getSimpleName(), trimmed.toLowerCase(Locale.ROOT)));
    }
}
