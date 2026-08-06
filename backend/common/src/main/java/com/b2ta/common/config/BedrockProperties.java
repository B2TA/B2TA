package com.b2ta.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Amazon Bedrock settings, bound from {@code aws.bedrock.*}.
 *
 * <p>Model ids are configuration rather than constants because Bedrock model identifiers carry a
 * date and a version suffix that changes independently of this code, and because the two services
 * are granted different models by their IAM task roles: the Worker may invoke Sonnet for match
 * analysis, the API may invoke only Haiku for comment generation.
 */
@Data
@ConfigurationProperties(prefix = "aws.bedrock")
public class BedrockProperties {

    /** Region offering the chosen models; may differ from the service region. */
    private String region = "us-east-1";

    /** Match_Engine model: nuanced reading comprehension and structured passage extraction. */
    private String matchModelId = "anthropic.claude-sonnet-4-20250514-v1:0";

    /** Comment_Assistant model: short feedback snippets, chosen for latency and cost. */
    private String commentModelId = "anthropic.claude-haiku-4-5-20250401-v1:0";

    /** Per-invocation timeout for match analysis. */
    private long matchTimeoutSeconds = 30;

    /** Per-request budget for comment suggestions (Requirement 12.6, 12.7). */
    private long commentTimeoutSeconds = 15;

    /** Total attempts per invocation: 1 initial call plus 3 retries (design retry table). */
    private int maxAttempts = 4;

    /** Backoff before retry n, in milliseconds: 1s, 2s, 4s. */
    private long retryBaseDelayMillis = 1000;

    /** Concurrent Bedrock invocations permitted per instance, to respect account rate limits. */
    private int maxConcurrentInvocations = 5;

    /**
     * When false, Bedrock is never called and the Match_Engine and Comment_Assistant report that
     * analysis is unavailable instead.
     *
     * <p>Lets the rest of the system run locally without Bedrock model access, while keeping the
     * unavailable path exercised rather than silently returning fabricated matches.
     */
    private boolean enabled = true;
}
