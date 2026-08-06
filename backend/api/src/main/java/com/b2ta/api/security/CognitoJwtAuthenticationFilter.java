package com.b2ta.api.security;

import com.b2ta.api.config.AuthProperties;
import com.b2ta.common.error.ErrorCode;
import com.b2ta.common.error.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Validates the Cognito access token on every request and puts the resolved TA into the security
 * context (Requirements 18.1, 18.3, 18.4).
 *
 * <p>Verification is delegated to a JWKS-backed {@link JwtDecoder}, which covers the RS256
 * signature, {@code exp}, {@code nbf}, and the {@code iss} claim, and handles key rotation and
 * caching. This filter adds the checks the decoder cannot express:
 *
 * <ul>
 *   <li>{@code token_use} must be {@code access}. An id token is signed by the same pool and would
 *       otherwise pass, but it is not an authorization credential and may be handed to third
 *       parties by the client.
 *   <li>{@code sub} must resolve to a {@code ta_user} row.
 * </ul>
 *
 * <p>Every failure produces 401 with an empty body apart from the error envelope, so a rejected
 * request never carries session data (Requirement 18.4). The token itself is never logged; only
 * the resolved TA id reaches the MDC (Requirement 18.11).
 */
@Slf4j
@RequiredArgsConstructor
public class CognitoJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String DEV_EMAIL_HEADER = "X-Dev-Ta-Email";

    /** Null when no user pool is configured and dev mode is carrying authentication instead. */
    @Nullable
    private final JwtDecoder jwtDecoder;

    private final TaUserProvisioningService taUserProvisioning;
    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Health and info are probed by the load balancer before any token exists. CORS preflight
        // requests never carry an Authorization header by specification.
        return path.startsWith("/actuator/health")
                || path.startsWith("/actuator/info")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        TaPrincipal principal;
        try {
            principal = authenticate(request);
        } catch (AuthFailure failure) {
            reject(response, failure.code, failure.getMessage());
            return;
        }

        if (principal == null) {
            reject(response, ErrorCode.UNAUTHORIZED, "Missing access token");
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(new TaAuthentication(principal));
        MDC.put("taId", principal.taId().toString());
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("taId");
            SecurityContextHolder.clearContext();
        }
    }

    @Nullable
    private TaPrincipal authenticate(HttpServletRequest request) throws AuthFailure {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authProperties.isDevMode() && !StringUtils.hasText(header)) {
            String email = request.getHeader(DEV_EMAIL_HEADER);
            if (!StringUtils.hasText(email)) {
                email = authProperties.getDevEmail();
            }
            // Dev mode identifies a TA by email only. Guarded by SecurityConfig, which refuses to
            // start with dev mode enabled outside the local profile.
            log.warn("Dev-mode authentication used for request {} {}",
                    request.getMethod(), request.getRequestURI());
            return taUserProvisioning.resolveByDevEmail(email);
        }

        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return null;
        }
        if (jwtDecoder == null) {
            throw new AuthFailure(ErrorCode.UNAUTHORIZED,
                    "Token validation is not configured on this deployment");
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException e) {
            // The decoder's message distinguishes expiry from a bad signature, and the frontend
            // needs that distinction to decide between a silent refresh and a full re-login.
            String message = e.getMessage() == null ? "" : e.getMessage();
            boolean expired = message.contains("expired") || message.contains("Jwt expired");
            log.debug("Access token rejected: {}", expired ? "expired" : "invalid");
            throw new AuthFailure(expired ? ErrorCode.TOKEN_EXPIRED : ErrorCode.UNAUTHORIZED,
                    expired ? "Access token has expired" : "Access token is invalid");
        }

        String tokenUse = jwt.getClaimAsString("token_use");
        if (!"access".equals(tokenUse)) {
            throw new AuthFailure(ErrorCode.UNAUTHORIZED,
                    "Expected a Cognito access token; received token_use=" + tokenUse);
        }

        String subject = jwt.getSubject();
        if (!StringUtils.hasText(subject)) {
            throw new AuthFailure(ErrorCode.UNAUTHORIZED, "Access token carries no subject");
        }

        return taUserProvisioning.resolveByCognitoSub(subject, jwt.getClaimAsString("username"));
    }

    private void reject(HttpServletResponse response, String code, String message)
            throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ErrorResponse.of(code, message, Map.of()));
    }

    /** Internal signal carrying the error code to report; never escapes this filter. */
    private static final class AuthFailure extends Exception {
        private final String code;

        private AuthFailure(String code, String message) {
            super(message, null, false, false);
            this.code = code;
        }
    }
}
