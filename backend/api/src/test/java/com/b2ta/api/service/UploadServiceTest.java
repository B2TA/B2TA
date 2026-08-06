package com.b2ta.api.service;

import com.b2ta.api.config.AwsProperties;
import com.b2ta.common.dto.rubric.RubricUploadUrlRequest;
import com.b2ta.common.dto.rubric.RubricUploadUrlResponse;
import com.b2ta.common.dto.submission.SubmissionUploadUrlsRequest;
import com.b2ta.common.dto.submission.SubmissionUploadUrlsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadServiceTest {

    @Mock
    private S3Presigner s3Presigner;

    private AwsProperties awsProperties;
    private UploadService uploadService;

    private static final UUID TA_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID SESSION_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeEach
    void setUp() throws MalformedURLException {
        awsProperties = new AwsProperties();
        awsProperties.getS3().setBucket("test-bucket");
        uploadService = new UploadService(s3Presigner, awsProperties);

        // Mock the presigner to return a dummy URL
        PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
        when(presignedRequest.url()).thenReturn(URI.create("https://test-bucket.s3.amazonaws.com/test-key").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);
    }

    // --- Rubric Upload URL Tests ---

    @Test
    void generateRubricUploadUrl_validPdf_returnsUrlAndKey() {
        RubricUploadUrlRequest request = RubricUploadUrlRequest.builder()
                .filename("rubric.pdf")
                .size(1024L)
                .build();

        RubricUploadUrlResponse response = uploadService.generateRubricUploadUrl(TA_ID, SESSION_ID, request);

        assertThat(response.getUploadUrl()).isNotBlank();
        assertThat(response.getObjectKey()).startsWith("uploads/" + TA_ID + "/" + SESSION_ID + "/rubrics/");
        assertThat(response.getObjectKey()).endsWith(".pdf");
    }

    @Test
    void generateRubricUploadUrl_validCsv_returnsUrlAndKey() {
        RubricUploadUrlRequest request = RubricUploadUrlRequest.builder()
                .filename("grades.CSV")
                .size(500L)
                .build();

        RubricUploadUrlResponse response = uploadService.generateRubricUploadUrl(TA_ID, SESSION_ID, request);

        assertThat(response.getObjectKey()).endsWith(".csv");
    }

    @Test
    void generateRubricUploadUrl_validXlsx_returnsUrlAndKey() {
        RubricUploadUrlRequest request = RubricUploadUrlRequest.builder()
                .filename("Rubric.XLSX")
                .size(5_242_880L)
                .build();

        RubricUploadUrlResponse response = uploadService.generateRubricUploadUrl(TA_ID, SESSION_ID, request);

        assertThat(response.getObjectKey()).endsWith(".xlsx");
    }

    @Test
    void generateRubricUploadUrl_invalidExtension_throws() {
        RubricUploadUrlRequest request = RubricUploadUrlRequest.builder()
                .filename("rubric.docx")
                .size(1024L)
                .build();

        assertThatThrownBy(() -> uploadService.generateRubricUploadUrl(TA_ID, SESSION_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Accepted rubric formats");
    }

    @Test
    void generateRubricUploadUrl_sizeZero_throws() {
        RubricUploadUrlRequest request = RubricUploadUrlRequest.builder()
                .filename("rubric.pdf")
                .size(0L)
                .build();

        assertThatThrownBy(() -> uploadService.generateRubricUploadUrl(TA_ID, SESSION_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 byte and 5,242,880 bytes");
    }

    @Test
    void generateRubricUploadUrl_sizeExceedsMax_throws() {
        RubricUploadUrlRequest request = RubricUploadUrlRequest.builder()
                .filename("rubric.pdf")
                .size(5_242_881L)
                .build();

        assertThatThrownBy(() -> uploadService.generateRubricUploadUrl(TA_ID, SESSION_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 byte and 5,242,880 bytes");
    }

    @Test
    void generateRubricUploadUrl_nullFilename_throws() {
        RubricUploadUrlRequest request = RubricUploadUrlRequest.builder()
                .filename(null)
                .size(1024L)
                .build();

        assertThatThrownBy(() -> uploadService.generateRubricUploadUrl(TA_ID, SESSION_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Filename is required");
    }

    @Test
    void generateRubricUploadUrl_nullSize_throws() {
        RubricUploadUrlRequest request = RubricUploadUrlRequest.builder()
                .filename("rubric.pdf")
                .size(null)
                .build();

        assertThatThrownBy(() -> uploadService.generateRubricUploadUrl(TA_ID, SESSION_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File size is required");
    }

    @Test
    void generateRubricUploadUrl_noExtension_throws() {
        RubricUploadUrlRequest request = RubricUploadUrlRequest.builder()
                .filename("rubric")
                .size(1024L)
                .build();

        assertThatThrownBy(() -> uploadService.generateRubricUploadUrl(TA_ID, SESSION_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Accepted rubric formats");
    }

    @Test
    void generateRubricUploadUrl_caseInsensitiveExtension_works() {
        RubricUploadUrlRequest request = RubricUploadUrlRequest.builder()
                .filename("my_rubric.PdF")
                .size(2048L)
                .build();

        RubricUploadUrlResponse response = uploadService.generateRubricUploadUrl(TA_ID, SESSION_ID, request);

        assertThat(response.getObjectKey()).endsWith(".pdf");
    }

    // --- Submission Upload URLs Tests ---

    @Test
    void generateSubmissionUploadUrls_validSingleFile_returnsOneUrl() {
        SubmissionUploadUrlsRequest request = SubmissionUploadUrlsRequest.builder()
                .files(List.of(
                        SubmissionUploadUrlsRequest.FileUploadEntry.builder()
                                .filename("essay.pdf")
                                .size(10_000L)
                                .build()
                ))
                .build();

        SubmissionUploadUrlsResponse response = uploadService.generateSubmissionUploadUrls(TA_ID, SESSION_ID, request);

        assertThat(response.getUrls()).hasSize(1);
        assertThat(response.getUrls().get(0).getFilename()).isEqualTo("essay.pdf");
        assertThat(response.getUrls().get(0).getObjectKey()).startsWith("uploads/" + TA_ID + "/" + SESSION_ID + "/submissions/");
        assertThat(response.getUrls().get(0).getObjectKey()).endsWith(".pdf");
        assertThat(response.getUrls().get(0).getUploadUrl()).isNotBlank();
    }

    @Test
    void generateSubmissionUploadUrls_multipleFiles_returnsMultipleUrls() {
        SubmissionUploadUrlsRequest request = SubmissionUploadUrlsRequest.builder()
                .files(List.of(
                        SubmissionUploadUrlsRequest.FileUploadEntry.builder()
                                .filename("essay1.pdf").size(5000L).build(),
                        SubmissionUploadUrlsRequest.FileUploadEntry.builder()
                                .filename("essay2.docx").size(3000L).build(),
                        SubmissionUploadUrlsRequest.FileUploadEntry.builder()
                                .filename("readme.md").size(500L).build()
                ))
                .build();

        SubmissionUploadUrlsResponse response = uploadService.generateSubmissionUploadUrls(TA_ID, SESSION_ID, request);

        assertThat(response.getUrls()).hasSize(3);
    }

    @Test
    void generateSubmissionUploadUrls_emptyList_throws() {
        SubmissionUploadUrlsRequest request = SubmissionUploadUrlsRequest.builder()
                .files(Collections.emptyList())
                .build();

        assertThatThrownBy(() -> uploadService.generateSubmissionUploadUrls(TA_ID, SESSION_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one file is required");
    }

    @Test
    void generateSubmissionUploadUrls_nullList_throws() {
        SubmissionUploadUrlsRequest request = SubmissionUploadUrlsRequest.builder()
                .files(null)
                .build();

        assertThatThrownBy(() -> uploadService.generateSubmissionUploadUrls(TA_ID, SESSION_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one file is required");
    }

    @Test
    void generateSubmissionUploadUrls_exceeds300Files_throws() {
        List<SubmissionUploadUrlsRequest.FileUploadEntry> files = IntStream.rangeClosed(1, 301)
                .mapToObj(i -> SubmissionUploadUrlsRequest.FileUploadEntry.builder()
                        .filename("file" + i + ".pdf").size(1000L).build())
                .toList();

        SubmissionUploadUrlsRequest request = SubmissionUploadUrlsRequest.builder()
                .files(files)
                .build();

        assertThatThrownBy(() -> uploadService.generateSubmissionUploadUrls(TA_ID, SESSION_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Maximum 300 files per batch");
    }

    @Test
    void generateSubmissionUploadUrls_invalidExtensionInBatch_throws() {
        SubmissionUploadUrlsRequest request = SubmissionUploadUrlsRequest.builder()
                .files(List.of(
                        SubmissionUploadUrlsRequest.FileUploadEntry.builder()
                                .filename("essay.pdf").size(1000L).build(),
                        SubmissionUploadUrlsRequest.FileUploadEntry.builder()
                                .filename("virus.exe").size(1000L).build()
                ))
                .build();

        assertThatThrownBy(() -> uploadService.generateSubmissionUploadUrls(TA_ID, SESSION_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Accepted submission formats");
    }

    @Test
    void generateSubmissionUploadUrls_fileTooLarge_throws() {
        SubmissionUploadUrlsRequest request = SubmissionUploadUrlsRequest.builder()
                .files(List.of(
                        SubmissionUploadUrlsRequest.FileUploadEntry.builder()
                                .filename("huge.pdf").size(52_428_801L).build()
                ))
                .build();

        assertThatThrownBy(() -> uploadService.generateSubmissionUploadUrls(TA_ID, SESSION_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 byte and 52,428,800 bytes");
    }

    @Test
    void generateSubmissionUploadUrls_zipExtension_accepted() {
        SubmissionUploadUrlsRequest request = SubmissionUploadUrlsRequest.builder()
                .files(List.of(
                        SubmissionUploadUrlsRequest.FileUploadEntry.builder()
                                .filename("submissions.zip").size(10_000_000L).build()
                ))
                .build();

        SubmissionUploadUrlsResponse response = uploadService.generateSubmissionUploadUrls(TA_ID, SESSION_ID, request);

        assertThat(response.getUrls()).hasSize(1);
        assertThat(response.getUrls().get(0).getObjectKey()).endsWith(".zip");
    }

    @Test
    void generateSubmissionUploadUrls_txtExtension_accepted() {
        SubmissionUploadUrlsRequest request = SubmissionUploadUrlsRequest.builder()
                .files(List.of(
                        SubmissionUploadUrlsRequest.FileUploadEntry.builder()
                                .filename("paper.TXT").size(5000L).build()
                ))
                .build();

        SubmissionUploadUrlsResponse response = uploadService.generateSubmissionUploadUrls(TA_ID, SESSION_ID, request);

        assertThat(response.getUrls().get(0).getObjectKey()).endsWith(".txt");
    }

    @Test
    void generateSubmissionUploadUrls_exactly300Files_accepted() {
        List<SubmissionUploadUrlsRequest.FileUploadEntry> files = IntStream.rangeClosed(1, 300)
                .mapToObj(i -> SubmissionUploadUrlsRequest.FileUploadEntry.builder()
                        .filename("file" + i + ".pdf").size(1000L).build())
                .toList();

        SubmissionUploadUrlsRequest request = SubmissionUploadUrlsRequest.builder()
                .files(files)
                .build();

        SubmissionUploadUrlsResponse response = uploadService.generateSubmissionUploadUrls(TA_ID, SESSION_ID, request);

        assertThat(response.getUrls()).hasSize(300);
    }

    @Test
    void generateRubricUploadUrl_objectKeyContainsTaAndSessionPrefix() {
        RubricUploadUrlRequest request = RubricUploadUrlRequest.builder()
                .filename("rubric.pdf")
                .size(1024L)
                .build();

        RubricUploadUrlResponse response = uploadService.generateRubricUploadUrl(TA_ID, SESSION_ID, request);

        // Key should match pattern: uploads/{ta_id}/{session_id}/rubrics/{uuid}.pdf
        String key = response.getObjectKey();
        assertThat(key).matches("uploads/" + TA_ID + "/" + SESSION_ID + "/rubrics/[a-f0-9\\-]+\\.pdf");
    }
}
