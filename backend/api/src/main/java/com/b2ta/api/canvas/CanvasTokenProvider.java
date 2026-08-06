package com.b2ta.api.canvas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Resolves the Canvas API credentials from Secrets Manager.
 *
 * <p>The secret is JSON: {@code {"baseUrl": "...", "token": "..."}}. The token is cached
 * in memory after first read — it is fetched per process, never per request, and never
 * returned to the browser (Requirement 6.1).
 */
@Component
@Slf4j
public class CanvasTokenProvider {

    private final SecretsManagerClient secretsManagerClient;
    private final CanvasProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicReference<Credentials> cached = new AtomicReference<>();

    public CanvasTokenProvider(SecretsManagerClient secretsManagerClient,
                               CanvasProperties properties,
                               ObjectMapper objectMapper) {
        this.secretsManagerClient = secretsManagerClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the Canvas credentials, reading from Secrets Manager on first call.
     *
     * @throws CanvasException when the secret is missing or malformed — fail loudly, as
     * a silent fallback would mean grading against the wrong instance
     */
    public Credentials get() {
        Credentials existing = cached.get();
        if (existing != null) {
            return existing;
        }

        Credentials loaded = load();
        cached.compareAndSet(null, loaded);
        return cached.get();
    }

    /** Drops the cached credentials so the next call re-reads the secret. */
    public void invalidate() {
        cached.set(null);
    }

    private Credentials load() {
        String secretId = properties.getSecretId();
        try {
            GetSecretValueResponse response = secretsManagerClient.getSecretValue(
                    GetSecretValueRequest.builder().secretId(secretId).build());

            JsonNode node = objectMapper.readTree(response.secretString());
            String token = text(node, "token");
            if (token == null || token.isBlank()) {
                throw new CanvasException(
                        "Secret " + secretId + " has no \"token\" field.", 0, false);
            }

            // Secret baseUrl wins over configuration so rotating instances does not
            // require a redeploy; fall back to the configured value when absent.
            String baseUrl = text(node, "baseUrl");
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = properties.getBaseUrl();
            }
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new CanvasException(
                        "No Canvas baseUrl in secret " + secretId + " or in canvas.base-url.", 0, false);
            }

            log.info("Loaded Canvas credentials from secret {} for instance {}", secretId, baseUrl);
            return new Credentials(stripTrailingSlash(baseUrl), token);

        } catch (ResourceNotFoundException e) {
            throw new CanvasException("Canvas secret " + secretId + " not found.", 0, false, e);
        } catch (CanvasException e) {
            throw e;
        } catch (Exception e) {
            throw new CanvasException(
                    "Could not read Canvas credentials from secret " + secretId + ".", 0, false, e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Canvas base URL and bearer token. Deliberately not logged — {@link #toString()}
     * is overridden so an accidental interpolation cannot leak the token.
     */
    public record Credentials(String baseUrl, String token) {
        @Override
        public String toString() {
            return "Credentials[baseUrl=" + baseUrl + ", token=***]";
        }
    }
}
