package com.b2ta.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Custom configuration properties for AWS resources.
 * Bound to the "aws" prefix in application.yml.
 */
@Data
@Component
@ConfigurationProperties(prefix = "aws")
public class AwsProperties {

    private String region = "us-east-1";
    private S3Properties s3 = new S3Properties();
    private SqsProperties sqs = new SqsProperties();
    private CognitoProperties cognito = new CognitoProperties();

    @Data
    public static class S3Properties {
        private String bucket = "rubric-grading-assistant";
    }

    @Data
    public static class SqsProperties {
        private String queueUrl = "";
    }

    @Data
    public static class CognitoProperties {
        private String userPoolId = "";
        private String region = "";
    }
}
