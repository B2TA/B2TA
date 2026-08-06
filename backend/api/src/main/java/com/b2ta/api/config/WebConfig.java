package com.b2ta.api.config;

import com.b2ta.api.security.CurrentTaArgumentResolver;
import com.b2ta.common.config.AwsProperties;
import com.b2ta.common.config.BedrockProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** Registers the {@code @CurrentTa} resolver and binds the configuration property classes. */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({
        AuthProperties.class,
        CognitoProperties.class,
        AwsProperties.class,
        BedrockProperties.class
})
public class WebConfig implements WebMvcConfigurer {

    private final CurrentTaArgumentResolver currentTaArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentTaArgumentResolver);
    }
}
