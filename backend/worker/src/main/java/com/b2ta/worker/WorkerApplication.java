package com.b2ta.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.b2ta.worker", "com.b2ta.common"})
@EntityScan(basePackages = "com.b2ta.common")
@EnableJpaRepositories(basePackages = {"com.b2ta.worker", "com.b2ta.common"})
public class WorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}
