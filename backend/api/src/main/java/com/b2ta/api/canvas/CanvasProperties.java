package com.b2ta.api.canvas;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the Canvas LMS integration.
 * Bound to the "canvas" prefix in application.yml.
 *
 * <p>The API token is deliberately absent from this class — it is resolved at runtime
 * from Secrets Manager by {@link CanvasTokenProvider} so it never lands in configuration
 * that could be logged or shipped to the browser (Requirement 6.1).
 */
@Data
@Component
@ConfigurationProperties(prefix = "canvas")
public class CanvasProperties {

    /**
     * Selects the {@link CanvasClient} implementation: {@code canvas} for the live
     * instance, {@code fixtures} for committed response bodies. A demo runs on fixtures
     * unless the live instance is confirmed healthy (Requirement 6.5).
     */
    private DataSource dataSource = DataSource.FIXTURES;

    /** Base URL of the Canvas instance, without the /api/v1 suffix. */
    private String baseUrl = "";

    /** Secrets Manager secret id holding {"baseUrl": "...", "token": "..."}. */
    private String secretId = "b2ta/canvas";

    /** Directory holding captured Canvas response bodies, used in FIXTURES mode. */
    private String fixturesPath = "fixtures";

    /** The single course this deployment grades. */
    private String courseId = "1";

    /** Request timeout for Canvas calls. */
    private java.time.Duration timeout = java.time.Duration.ofSeconds(30);

    /**
     * Page size for paginated Canvas collections. Canvas caps this at 100.
     */
    private int perPage = 100;

    public enum DataSource {
        CANVAS,
        FIXTURES
    }
}
