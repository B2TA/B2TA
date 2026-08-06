package com.b2ta.api.canvas;

import com.b2ta.api.config.AwsProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.nio.file.Path;

/**
 * Wires the Canvas integration, selecting the live or fixture client from
 * {@code canvas.data-source}.
 */
@Configuration
@Slf4j
public class CanvasConfig {

    @Bean
    @ConditionalOnMissingBean
    public SecretsManagerClient secretsManagerClient(AwsProperties awsProperties) {
        return SecretsManagerClient.builder()
                .region(Region.of(awsProperties.getRegion()))
                .build();
    }

    @Bean
    public RestClient canvasRestClient(CanvasProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.getTimeout().toMillis());
        factory.setReadTimeout((int) properties.getTimeout().toMillis());
        return RestClient.builder().requestFactory(factory).build();
    }

    @Bean
    public CanvasClient canvasClient(CanvasProperties properties,
                                     RestClient canvasRestClient,
                                     CanvasTokenProvider tokenProvider,
                                     ObjectMapper objectMapper) {
        if (properties.getDataSource() == CanvasProperties.DataSource.CANVAS) {
            log.info("Canvas integration in LIVE mode against the configured instance.");
            return new LiveCanvasClient(canvasRestClient, tokenProvider, properties, objectMapper);
        }
        return new FixtureCanvasClient(Path.of(properties.getFixturesPath()), objectMapper);
    }
}
