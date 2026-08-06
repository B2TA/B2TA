package com.b2ta.common.ai;

import com.b2ta.common.config.BedrockProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import software.amazon.awssdk.core.document.Document;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Invokes a Bedrock model and returns a structured JSON object.
 *
 * <p>Three behaviours live here rather than at the call sites, because both the Match_Engine and the
 * Comment_Assistant need all three and getting them subtly different would be a source of
 * inconsistent failure handling:
 *
 * <ul>
 *   <li><b>Schema enforcement.</b> The request declares a single tool with a JSON schema and forces
 *       the model to call it. That is how the Converse API expresses structured output: the model
 *       answers with a {@code toolUse} block whose input already conforms, so there is no prose to
 *       strip and no partial JSON to repair.
 *   <li><b>Retry with backoff.</b> Up to {@code maxAttempts} tries with 1s, 2s, 4s delays, per the
 *       design retry table. Throttling and transient service errors are retried; a malformed
 *       response is not, because retrying an unparseable answer usually produces another one.
 *   <li><b>Concurrency limit.</b> A semaphore caps concurrent invocations per instance so a batch of
 *       150 submissions does not trip the account-level Bedrock rate limit and turn retryable
 *       throttling into exhausted attempts.
 * </ul>
 */
@Slf4j
@Component
public class BedrockJsonClient {

    /** Name of the forced tool. Arbitrary, but must match between schema and response lookup. */
    private static final String RESPONSE_TOOL = "emit_result";

    private final BedrockRuntimeClient bedrock;
    private final BedrockProperties properties;
    private final ObjectMapper objectMapper;
    private final Semaphore concurrencyLimit;

    public BedrockJsonClient(BedrockRuntimeClient bedrock,
                             BedrockProperties properties,
                             ObjectMapper objectMapper) {
        this.bedrock = bedrock;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.concurrencyLimit = new Semaphore(Math.max(1, properties.getMaxConcurrentInvocations()));
    }

    /**
     * Calls the model and returns the structured result.
     *
     * @param modelId      Bedrock model identifier
     * @param systemPrompt system instructions
     * @param userPrompt   the request content
     * @param schema       JSON schema the response must conform to, as a nested map
     * @param maxTokens    output token ceiling
     * @param timeout      total budget across all attempts
     * @return the tool input the model produced, parsed as a JSON tree
     * @throws BedrockUnavailableException when the budget or the attempt count is exhausted
     */
    public JsonNode invokeStructured(String modelId,
                                     String systemPrompt,
                                     String userPrompt,
                                     Map<String, Object> schema,
                                     int maxTokens,
                                     Duration timeout) {
        if (!properties.isEnabled()) {
            throw new BedrockUnavailableException(
                    "Bedrock invocation is disabled by configuration", false, null);
        }

        long deadline = System.nanoTime() + timeout.toNanos();
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
            if (System.nanoTime() >= deadline) {
                throw new BedrockUnavailableException(
                        "Bedrock did not respond within " + timeout.toSeconds() + " seconds",
                        true, lastFailure);
            }
            try {
                return attemptInvocation(modelId, systemPrompt, userPrompt, schema, maxTokens,
                        remaining(deadline));
            } catch (RetryableFailure e) {
                lastFailure = e;
                log.warn("Bedrock attempt {}/{} failed for model {}: {}",
                        attempt, properties.getMaxAttempts(), modelId, e.getMessage());
                if (attempt < properties.getMaxAttempts() && !sleepBeforeRetry(attempt, deadline)) {
                    throw new BedrockUnavailableException(
                            "Bedrock did not respond within " + timeout.toSeconds() + " seconds",
                            true, e);
                }
            } catch (BedrockUnavailableException e) {
                throw e;
            } catch (RuntimeException e) {
                // Non-retryable: a schema violation, an unparseable response, or a permanent
                // client error such as access denied. Retrying will not change the outcome.
                throw new BedrockUnavailableException(
                        "Bedrock invocation failed: " + e.getMessage(), false, e);
            }
        }
        throw new BedrockUnavailableException(
                "Bedrock invocation failed after " + properties.getMaxAttempts() + " attempts",
                false, lastFailure);
    }

    private JsonNode attemptInvocation(String modelId,
                                       String systemPrompt,
                                       String userPrompt,
                                       Map<String, Object> schema,
                                       int maxTokens,
                                       Duration remaining) {
        boolean acquired;
        try {
            acquired = concurrencyLimit.tryAcquire(remaining.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BedrockUnavailableException("Interrupted waiting for a Bedrock slot", false, e);
        }
        if (!acquired) {
            throw new BedrockUnavailableException(
                    "Timed out waiting for a Bedrock invocation slot", true, null);
        }

        try {
            ConverseRequest request = ConverseRequest.builder()
                    .modelId(modelId)
                    .system(SystemContentBlock.fromText(systemPrompt))
                    .messages(Message.builder()
                            .role(ConversationRole.USER)
                            .content(ContentBlock.fromText(userPrompt))
                            .build())
                    .inferenceConfig(InferenceConfiguration.builder()
                            .maxTokens(maxTokens)
                            // Low temperature: the task is extraction against a fixed schema, not
                            // generation, and variability across identical inputs would make the
                            // suggested matches for one submission unreproducible.
                            .temperature(0.2f)
                            .build())
                    .toolConfig(ToolConfiguration.builder()
                            .tools(Tool.fromToolSpec(ToolSpecification.builder()
                                    .name(RESPONSE_TOOL)
                                    .description("Return the structured result.")
                                    .inputSchema(ToolInputSchema.fromJson(toDocument(schema)))
                                    .build()))
                            .toolChoice(ToolChoice.fromTool(
                                    software.amazon.awssdk.services.bedrockruntime.model
                                            .SpecificToolChoice.builder()
                                            .name(RESPONSE_TOOL).build()))
                            .build())
                    .build();

            ConverseResponse response;
            try {
                response = bedrock.converse(request);
            } catch (ThrottlingException e) {
                throw new RetryableFailure("throttled by Bedrock", e);
            } catch (software.amazon.awssdk.core.exception.SdkServiceException e) {
                // 5xx and 429 are worth another attempt; 4xx is a permanent problem with the request.
                if (e.statusCode() >= 500 || e.statusCode() == 429) {
                    throw new RetryableFailure("Bedrock returned " + e.statusCode(), e);
                }
                throw e;
            } catch (software.amazon.awssdk.core.exception.SdkClientException e) {
                throw new RetryableFailure("Bedrock connection failure", e);
            }

            return extractToolInput(response);
        } finally {
            concurrencyLimit.release();
        }
    }

    /** Pulls the forced tool's input out of the response and parses it. */
    private JsonNode extractToolInput(ConverseResponse response) {
        Optional<ToolUseBlock> toolUse = response.output().message().content().stream()
                .filter(block -> block.toolUse() != null)
                .map(ContentBlock::toolUse)
                .findFirst();

        if (toolUse.isEmpty()) {
            throw new IllegalStateException(
                    "Model returned no tool use block; stop reason was " + response.stopReasonAsString());
        }
        try {
            // Document -> JSON string -> tree. Going through the SDK's own serializer avoids
            // hand-walking the Document union and its numeric type distinctions.
            String json = toolUse.get().input().toString();
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Model response was not valid JSON", e);
        }
    }

    /**
     * Sleeps the backoff for the given attempt.
     *
     * @return false when the remaining budget is shorter than the backoff, meaning the caller should
     *         stop rather than sleep past its own deadline
     */
    private boolean sleepBeforeRetry(int attempt, long deadline) {
        long delayMillis = properties.getRetryBaseDelayMillis() * (1L << (attempt - 1));
        if (remaining(deadline).toMillis() <= delayMillis) {
            return false;
        }
        try {
            Thread.sleep(delayMillis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Duration remaining(long deadline) {
        long nanos = deadline - System.nanoTime();
        return nanos <= 0 ? Duration.ZERO : Duration.ofNanos(nanos);
    }

    /** Converts a plain nested map schema into the SDK's {@code Document} union. */
    @SuppressWarnings("unchecked")
    private Document toDocument(Object value) {
        if (value == null) {
            return Document.fromNull();
        }
        if (value instanceof String s) {
            return Document.fromString(s);
        }
        if (value instanceof Boolean b) {
            return Document.fromBoolean(b);
        }
        if (value instanceof Integer i) {
            return Document.fromNumber(i);
        }
        if (value instanceof Long l) {
            return Document.fromNumber(l);
        }
        if (value instanceof Double d) {
            return Document.fromNumber(d);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Document> converted = new java.util.LinkedHashMap<>();
            map.forEach((k, v) -> converted.put(String.valueOf(k), toDocument(v)));
            return Document.fromMap(converted);
        }
        if (value instanceof List<?> list) {
            return Document.fromList(list.stream().map(this::toDocument).toList());
        }
        throw new IllegalArgumentException(
                "Unsupported schema value type: " + value.getClass().getName());
    }

    /** Marks a failure worth another attempt. */
    private static final class RetryableFailure extends RuntimeException {
        private RetryableFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
