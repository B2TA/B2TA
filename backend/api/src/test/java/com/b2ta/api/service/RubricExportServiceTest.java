package com.b2ta.api.service;

import com.b2ta.api.config.AwsProperties;
import com.b2ta.api.repository.GradingSessionRepository;
import com.b2ta.api.repository.RubricRepository;
import com.b2ta.api.security.SecurityContextHelper;
import com.b2ta.common.dto.export.ExportResponse;
import com.b2ta.common.entity.Criterion;
import com.b2ta.common.entity.GradingSession;
import com.b2ta.common.entity.PerformanceLevel;
import com.b2ta.common.entity.Rubric;
import com.b2ta.common.entity.TaUser;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.math.BigDecimal;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RubricExportServiceTest {

    @Mock
    private RubricRepository rubricRepository;

    @Mock
    private GradingSessionRepository sessionRepository;

    @Mock
    private SecurityContextHelper securityContextHelper;

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private RubricPrinter rubricPrinter;
    private AwsProperties awsProperties;
    private RubricExportService rubricExportService;

    private static final UUID TA_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RUBRIC_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String BUCKET = "test-bucket";

    @BeforeEach
    void setUp() {
        rubricPrinter = new RubricPrinter();
        awsProperties = new AwsProperties();
        awsProperties.getS3().setBucket(BUCKET);

        rubricExportService = new RubricExportService(
                rubricRepository,
                sessionRepository,
                securityContextHelper,
                rubricPrinter,
                s3Client,
                s3Presigner,
                awsProperties
        );
    }

    @Test
    void exportRubric_success_uploadsToS3AndReturnsUrl() throws Exception {
        // Arrange
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
        GradingSession session = buildSession();
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));

        Rubric rubric = buildRubricWithCriteria();
        when(rubricRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(rubric));

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(new URL("https://s3.amazonaws.com/test-bucket/exports/file.csv?signed=true"));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);

        // Act
        ExportResponse response = rubricExportService.exportRubric(SESSION_ID);

        // Assert
        assertThat(response.getDownloadUrl()).contains("https://s3.amazonaws.com");
        assertThat(response.getFilename()).startsWith("rubric-export-");
        assertThat(response.getFilename()).endsWith(".csv");

        // Verify S3 upload was called with correct bucket and key pattern
        ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(putCaptor.capture(), any(RequestBody.class));
        PutObjectRequest capturedPut = putCaptor.getValue();
        assertThat(capturedPut.bucket()).isEqualTo(BUCKET);
        assertThat(capturedPut.key()).startsWith("exports/" + TA_ID + "/" + SESSION_ID + "/rubric-export-");
        assertThat(capturedPut.key()).endsWith(".csv");
        assertThat(capturedPut.contentType()).isEqualTo("text/csv; charset=UTF-8");
    }

    @Test
    void exportRubric_sessionNotFound_throws() {
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rubricExportService.exportRubric(SESSION_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Session not found");
    }

    @Test
    void exportRubric_rubricNotFound_throws() {
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
        GradingSession session = buildSession();
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));
        when(rubricRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rubricExportService.exportRubric(SESSION_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Rubric not found");
    }

    @Test
    void exportRubric_zeroCriteria_returns400() {
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
        GradingSession session = buildSession();
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));

        Rubric rubric = Rubric.builder()
                .id(RUBRIC_ID)
                .session(session)
                .criteria(new ArrayList<>())
                .build();
        when(rubricRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(rubric));

        assertThatThrownBy(() -> rubricExportService.exportRubric(SESSION_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("at least one criterion");
    }

    // --- Helper methods ---

    private GradingSession buildSession() {
        TaUser ta = new TaUser();
        ta.setId(TA_ID);
        GradingSession session = new GradingSession();
        session.setId(SESSION_ID);
        session.setTa(ta);
        return session;
    }

    private Rubric buildRubricWithCriteria() {
        PerformanceLevel level = PerformanceLevel.builder()
                .label("Excellent")
                .description("Outstanding work")
                .points(new BigDecimal("10"))
                .position(0)
                .build();

        Criterion criterion = Criterion.builder()
                .title("Thesis")
                .description("Clear thesis statement")
                .maxPoints(new BigDecimal("10"))
                .displayColor("#D32F2F")
                .position(0)
                .performanceLevels(new ArrayList<>(List.of(level)))
                .build();

        Rubric rubric = Rubric.builder()
                .id(RUBRIC_ID)
                .criteria(new ArrayList<>(List.of(criterion)))
                .build();

        GradingSession session = buildSession();
        rubric.setSession(session);

        return rubric;
    }
}
