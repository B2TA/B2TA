package com.b2ta.api.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the authenticated {@link TaPrincipal} into a controller method parameter.
 *
 * <p>Controllers take the principal as an explicit parameter rather than reading a thread-local,
 * which makes the tenant key visible in every handler signature and impossible to forget when
 * calling into a service.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentTa {
}
