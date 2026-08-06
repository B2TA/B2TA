package com.b2ta.common.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Logback conversion rule that runs every rendered message through
 * {@link SensitiveDataFilter#sanitize(String)}.
 *
 * <p>Registered as {@code %sanitized} in {@code logback-spring.xml} and used in place of
 * {@code %message}, so the redaction applies to log output from dependencies too, not only to
 * calls made by this codebase.
 */
public class SanitizedMessageConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return SensitiveDataFilter.sanitize(event.getFormattedMessage());
    }
}
