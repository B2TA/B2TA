package com.b2ta.api.service;

import com.b2ta.common.config.AwsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

/**
 * S3 reads, writes, and pre-signed URL issuance.
 *
 * <p>Object keys reaching this class are already scoped to the requesting TA by
 * {@link S3KeyBuilder}. Nothing here derives a key from request input, which is what keeps a
 * pre-signed URL from ever pointing outside the caller's own prefix (Requirement 18.6).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService {

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final AwsProperties properties;

    /** Writes a CSV export with server-side encryption (design: bucket policy). */
    public void putCsv(String objectKey, byte[] content) {
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(properties.getS3().getBucket())
                        .key(objectKey)
                        .contentType("text/csv; charset=utf-8")
                        .serverSideEncryption(ServerSideEncryption.AES256)
                        .build(),
                RequestBody.fromBytes(content));
    }

    /** Pre-signed GET valid for 15 minutes (Requirement 16.4). */
    public String presignedDownloadUrl(String objectKey) {
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(
                                properties.getS3().getDownloadUrlTtlMinutes()))
                        .getObjectRequest(GetObjectRequest.builder()
                                .bucket(properties.getS3().getBucket())
                                .key(objectKey)
                                .build())
                        .build())
                .url()
                .toString();
    }

    /** Pre-signed PUT valid for 15 minutes (Requirement 4.2). */
    public String presignedUploadUrl(String objectKey, String contentType) {
        return presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(
                                properties.getS3().getUploadUrlTtlMinutes()))
                        .putObjectRequest(PutObjectRequest.builder()
                                .bucket(properties.getS3().getBucket())
                                .key(objectKey)
                                .contentType(contentType)
                                .build())
                        .build())
                .url()
                .toString();
    }
}
