package com.b2ta.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Authentication settings for the API service. */
@Data
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    /**
     * When true, the API accepts an {@code X-Dev-Ta-Email} header instead of a Cognito token and
     * auto-provisions a TA for it.
     *
     * <p>This exists so the SPA can be run against a local API without a Cognito user pool. It
     * bypasses authentication completely, so it defaults to false and
     * {@link SecurityConfig} refuses to start with it enabled outside the {@code local} profile.
     */
    private boolean devMode = false;

    /** Email used when dev mode is on and the request carries no {@code X-Dev-Ta-Email} header. */
    private String devEmail = "dev-ta@example.com";

    /** Browser origins permitted to call the API. */
    private List<String> allowedOrigins = List.of(
            "http://localhost:8443",
            "http://localhost:5173",
            "http://127.0.0.1:8443");
}
