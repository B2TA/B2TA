package com.b2ta.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.b2ta.api", "com.b2ta.common"})
@ConfigurationPropertiesScan(basePackages = "com.b2ta.api.config")
@EntityScan(basePackages = "com.b2ta.common")
@EnableJpaRepositories(basePackages = {"com.b2ta.api", "com.b2ta.common"})
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
