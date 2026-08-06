package com.b2ta.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;
import java.time.Duration;

/**
 * AWS SDK clients shared by the API and Worker services.
 *
 * <p>Credentials come from the default provider chain, which resolves to the ECS task role in
 * deployment and to the developer's local profile otherwise — no access key ever appears in
 * configuration.
 *
 * <p>Every client is declared {@code @ConditionalOnMissingBean} so a test can substitute a stub
 * without excluding this configuration.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({AwsProperties.class, BedrockProperties.class})
public class AwsClientConfig {

    @Bean
    @ConditionalOnMissingBean
    public S3Client s3Client(AwsProperties properties) {
        var builder = S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create());
        applyEndpointOverride(builder, properties);
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public S3Presigner s3Presigner(AwsProperties properties) {
        var builder = S3Presigner.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create());
        String override = properties.getS3().getEndpointOverride();
        if (override != null && !override.isBlank()) {
            builder.endpointOverride(URI.create(override));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public SqsClient sqsClient(AwsProperties properties) {
        return SqsClient.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * Bedrock runtime client.
     *
     * <p>The API call timeout is set above the longest model budget (30s for match analysis) so the
     * service's own timeout handling decides the outcome rather than the SDK aborting first with a
     * less specific error.
     */
    @Bean
    @ConditionalOnMissingBean
    public BedrockRuntimeClient bedrockRuntimeClient(BedrockProperties properties) {
        long ceiling = Math.max(properties.getMatchTimeoutSeconds(),
                properties.getCommentTimeoutSeconds());
        return BedrockRuntimeClient.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(ceiling + 10))
                        .apiCallAttemptTimeout(Duration.ofSeconds(ceiling + 5))
                        .build())
                .build();
    }

    private void applyEndpointOverride(S3ClientBuilder builder, AwsProperties properties) {
        String override = properties.getS3().getEndpointOverride();
        if (override == null || override.isBlank()) {
            return;
        }
        log.info("Using S3 endpoint override {}", override);
        builder.endpointOverride(URI.create(override))
                // Local S3-compatible servers generally do not support virtual-host addressing.
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
    }
}
