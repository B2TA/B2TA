package com.b2ta.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** AWS coordinates shared by the API and Worker services, bound from {@code aws.*}. */
@Data
@ConfigurationProperties(prefix = "aws")
public class AwsProperties {

    /** Region the service runs in and where S3/SQS resources live. */
    private String region = "us-east-1";

    private S3 s3 = new S3();
    private Sqs sqs = new Sqs();

    @Data
    public static class S3 {
        /** Bucket holding uploads and generated exports. */
        private String bucket;

        /** Validity of a pre-signed upload URL (Requirement 4.2). */
        private long uploadUrlTtlMinutes = 15;

        /** Validity of a pre-signed download URL (Requirement 16.4). */
        private long downloadUrlTtlMinutes = 15;

        /**
         * When set, overrides the S3 endpoint. Used to point at a local S3-compatible server so the
         * upload and export flows can be exercised without an AWS account.
         */
        private String endpointOverride;
    }

    @Data
    public static class Sqs {
        /** Job queue URL. When blank, jobs run in-process instead of being enqueued. */
        private String queueUrl;

        private int waitTimeSeconds = 20;
        private int visibilityTimeoutSeconds = 300;

        /** True when a queue is configured and messages should be published. */
        public boolean isConfigured() {
            return queueUrl != null && !queueUrl.isBlank();
        }
    }

    /** True when a bucket is configured and S3-backed operations can run. */
    public boolean isS3Configured() {
        return s3.bucket != null && !s3.bucket.isBlank();
    }
}
