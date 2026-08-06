package com.b2ta.api.analyze;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the Bedrock-backed rubric analysis.
 */
@Data
@Component
@ConfigurationProperties(prefix = "analyze")
public class AnalyzeProperties {

    /**
     * Bedrock model id.
     *
     * <p>The {@code us.} inference-profile prefix is required — the bare
     * {@code anthropic.*} ids returned by {@code list-foundation-models} fail at invoke
     * time. This default was verified against the workshop account on 2026-08-06, where
     * newer Claude models are not granted; raise it once the account has access.
     */
    private String modelId = "us.anthropic.claude-sonnet-4-5-20250929-v1:0";

    /** Cap on the model's response. Structured tool output is small. */
    private int maxTokens = 8000;

    /** Wall-clock budget for a single analysis before giving up. */
    private java.time.Duration timeout = java.time.Duration.ofSeconds(90);

    /** Retries on Bedrock throttling before degrading to an unanalyzed submission. */
    private int maxRetries = 2;

    /**
     * When false, the analyze step is skipped and submissions are served with no
     * suggested matches. Lets the app run without Bedrock access.
     */
    private boolean enabled = true;
}
