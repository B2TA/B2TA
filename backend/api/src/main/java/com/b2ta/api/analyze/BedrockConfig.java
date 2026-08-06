package com.b2ta.api.analyze;

import com.b2ta.api.config.AwsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Configuration
public class BedrockConfig {

    @Bean
    @ConditionalOnMissingBean
    public BedrockRuntimeClient bedrockRuntimeClient(AwsProperties awsProperties,
                                                     AnalyzeProperties analyzeProperties) {
        return BedrockRuntimeClient.builder()
                .region(Region.of(awsProperties.getRegion()))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(analyzeProperties.getTimeout())
                        .build())
                .build();
    }
}
