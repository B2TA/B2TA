package com.b2ta.api.security;

import com.b2ta.common.error.ApiException;
import com.b2ta.common.error.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Resolves {@link CurrentTa}-annotated parameters from the security context. */
@Component
public class CurrentTaArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentTa.class)
                && TaPrincipal.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof TaAuthentication taAuthentication)) {
            // Reaching a @CurrentTa handler without a TaAuthentication means the security filter
            // chain was misconfigured; failing closed with 401 is the safe outcome.
            throw ApiException.unauthorized(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        return taAuthentication.getPrincipal();
    }
}
