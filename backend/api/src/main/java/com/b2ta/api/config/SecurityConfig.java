package com.b2ta.api.config;

import com.b2ta.api.security.CognitoJwtAuthenticationFilter;
import com.b2ta.api.security.TaUserProvisioningService;
import com.b2ta.common.error.ErrorCode;
import com.b2ta.common.error.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

/**
 * Stateless security for the API service.
 *
 * <p>There is no session, no form login, and no CSRF token: every request authenticates itself with
 * a Cognito access token, so there is no ambient credential for a cross-site request to ride on.
 * Everything under {@code /api/**} requires authentication; only the load balancer health probes
 * are open.
 */
@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CognitoProperties cognitoProperties;
    private final AuthProperties authProperties;
    private final String activeProfiles;

    public SecurityConfig(CognitoProperties cognitoProperties,
                          AuthProperties authProperties,
                          @Value("${spring.profiles.active:}") String activeProfiles) {
        this.cognitoProperties = cognitoProperties;
        this.authProperties = authProperties;
        this.activeProfiles = activeProfiles;
        assertDevModeIsSafe();
    }

    /**
     * Refuses to start if the authentication bypass is enabled outside local development.
     *
     * <p>Dev mode accepts an email header in place of a verified token. A deployment that shipped
     * with it on would expose every TA's grading data to anyone who can reach the load balancer, so
     * this fails fast rather than logging a warning.
     */
    private void assertDevModeIsSafe() {
        if (!authProperties.isDevMode()) {
            return;
        }
        boolean localOnly = activeProfiles != null
                && (activeProfiles.contains("local") || activeProfiles.contains("test"));
        if (!localOnly) {
            throw new IllegalStateException(
                    "auth.dev-mode=true bypasses Cognito authentication and is only permitted with "
                            + "the 'local' or 'test' profile active (active profiles: '"
                            + activeProfiles + "'). Set auth.dev-mode=false.");
        }
        log.warn("auth.dev-mode is ENABLED: requests without a Bearer token are authenticated from "
                + "the X-Dev-Ta-Email header. Never enable this outside local development.");
    }

    /**
     * JWKS-backed decoder for the configured user pool.
     *
     * <p>Absent when no pool is configured, which is only viable together with dev mode; the filter
     * then rejects any request that does present a Bearer token rather than trusting it.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        if (!cognitoProperties.isConfigured()) {
            log.warn("aws.cognito.user-pool-id / region are not set; no JWT decoder is available");
            return null;
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(cognitoProperties.jwkSetUri())
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                // Signature, exp, nbf, and iss.
                JwtValidators.createDefaultWithIssuer(cognitoProperties.issuerUri()),
                clientIdValidator()));
        log.info("Cognito JWT validation active for issuer {}", cognitoProperties.issuerUri());
        return decoder;
    }

    /** Rejects a token issued to a different app client of the same pool, when a client is pinned. */
    private OAuth2TokenValidator<Jwt> clientIdValidator() {
        String expected = cognitoProperties.getClientId();
        if (expected == null || expected.isBlank()) {
            return token -> OAuth2TokenValidatorResult.success();
        }
        return token -> expected.equals(token.getClaimAsString("client_id"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Unexpected client_id claim", null));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ObjectProvider<JwtDecoder> jwtDecoderProvider,
                                                   TaUserProvisioningService taUserProvisioning,
                                                   ObjectMapper objectMapper) throws Exception {
        // getIfAvailable() yields null when jwtDecoder() returned null because no pool is set.
        CognitoJwtAuthenticationFilter authFilter = new CognitoJwtAuthenticationFilter(
                jwtDecoderProvider.getIfAvailable(), taUserProvisioning, authProperties,
                objectMapper);

        http
                .cors(Customizer.withDefaults())
                // No cookie or session is used for authentication, so there is nothing for a
                // cross-site request to replay and no CSRF token to manage.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(
                                    ErrorCode.UNAUTHORIZED, "Authentication required", Map.of()));
                        })
                        // A TA that reaches an endpoint they may not use gets 404, not 403, so the
                        // response does not confirm that the resource exists (Requirement 18.5).
                        .accessDeniedHandler((request, response, deniedException) -> {
                            response.setStatus(404);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(
                                    ErrorCode.NOT_FOUND, "Resource not found", Map.of()));
                        }))
                .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(authProperties.getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Dev-Ta-Email"));
        config.setExposedHeaders(List.of("Location"));
        // No credentials are sent as cookies; the token travels in the Authorization header.
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
