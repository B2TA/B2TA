package com.b2ta.api.service;

import com.b2ta.api.repository.GradingSessionRepository;
import com.b2ta.api.repository.SubmissionRepository;
import com.b2ta.api.security.SecurityContextHelper;
import com.b2ta.common.dto.submission.SubmissionResponse;
import com.b2ta.common.dto.submission.UpdateIdentityRequest;
import com.b2ta.common.entity.GradingSession;
import com.b2ta.common.entity.Submission;
import com.b2ta.common.entity.TaUser;
import com.b2ta.common.entity.enums.ExtractionStatus;
import com.b2ta.common.entity.enums.IdentityStatus;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private GradingSessionRepository sessionRepository;

    @Mock
    private SecurityContextHelper securityContextHelper;

    @InjectMocks
    private SubmissionService submissionService;

    private static final UUID TA_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SUB_ID_1 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SUB_ID_2 = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private GradingSession session;

    @BeforeEach
    void setUp() {
        TaUser ta = new TaUser();
        ta.setId(TA_ID);
        session = GradingSession.builder()
                .id(SESSION_ID)
                .ta(ta)
                .name("Test Session")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Stubs the current TA identity. Called only by tests that go through a
     * tenant-scoped path — the batch-limit checks query by session id alone, and
     * stubbing for them would trip Mockito's strict-stub checking.
     */
    private void stubCurrentTa() {
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
    }

    @Test
    void listSubmissions_returnsOrderedSubmissions() {
        stubCurrentTa();
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));

        Submission sub1 = buildSubmission(SUB_ID_1, "student1.pdf", "Alice", 0);
        Submission sub2 = buildSubmission(SUB_ID_2, "student2.pdf", "Bob", 1);
        when(submissionRepository.findAllBySessionIdOrderByPositionAsc(SESSION_ID))
                .thenReturn(List.of(sub1, sub2));

        List<SubmissionResponse> result = submissionService.listSubmissions(SESSION_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getOriginalFilename()).isEqualTo("student1.pdf");
        assertThat(result.get(0).getPosition()).isEqualTo(0);
        assertThat(result.get(1).getOriginalFilename()).isEqualTo("student2.pdf");
        assertThat(result.get(1).getPosition()).isEqualTo(1);
    }

    @Test
    void listSubmissions_throws404_whenSessionNotOwned() {
        stubCurrentTa();
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> submissionService.listSubmissions(SESSION_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Session not found");
    }

    @Test
    void updateIdentity_updatesNameAndMarksVerified() {
        stubCurrentTa();
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));

        Submission submission = buildSubmission(SUB_ID_1, "file.pdf", "OldName", 0);
        when(submissionRepository.findByIdAndSessionId(SUB_ID_1, SESSION_ID))
                .thenReturn(Optional.of(submission));
        when(submissionRepository.findAllBySessionIdOrderByPositionAsc(SESSION_ID))
                .thenReturn(List.of(submission));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateIdentityRequest request = UpdateIdentityRequest.builder()
                .studentDisplayName("NewName")
                .build();

        SubmissionResponse response = submissionService.updateIdentity(SESSION_ID, SUB_ID_1, request);

        assertThat(response.getStudentDisplayName()).isEqualTo("NewName");
        assertThat(response.getIdentityStatus()).isEqualTo(IdentityStatus.VERIFIED);
    }

    @Test
    void updateIdentity_throws404_whenSubmissionNotFound() {
        stubCurrentTa();
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));
        when(submissionRepository.findByIdAndSessionId(SUB_ID_1, SESSION_ID)).thenReturn(Optional.empty());

        UpdateIdentityRequest request = UpdateIdentityRequest.builder()
                .studentDisplayName("Name")
                .build();

        assertThatThrownBy(() -> submissionService.updateIdentity(SESSION_ID, SUB_ID_1, request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Submission not found");
    }

    @Test
    void updateIdentity_flagsDuplicatesAfterRename() {
        stubCurrentTa();
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));

        Submission sub1 = buildSubmission(SUB_ID_1, "file1.pdf", "Alice", 0);
        Submission sub2 = buildSubmission(SUB_ID_2, "file2.pdf", "Alice", 1);

        when(submissionRepository.findByIdAndSessionId(SUB_ID_1, SESSION_ID))
                .thenReturn(Optional.of(sub1));
        when(submissionRepository.findAllBySessionIdOrderByPositionAsc(SESSION_ID))
                .thenReturn(List.of(sub1, sub2));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateIdentityRequest request = UpdateIdentityRequest.builder()
                .studentDisplayName("Alice")
                .build();

        submissionService.updateIdentity(SESSION_ID, SUB_ID_1, request);

        // Both should be flagged as disambiguation required
        assertThat(sub1.getIdentityStatus()).isEqualTo(IdentityStatus.DISAMBIGUATION_REQUIRED);
        assertThat(sub2.getIdentityStatus()).isEqualTo(IdentityStatus.DISAMBIGUATION_REQUIRED);
    }

    @Test
    void updateIdentity_clearsDuplicateFlagWhenNoLongerDuplicated() {
        stubCurrentTa();
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));

        Submission sub1 = buildSubmission(SUB_ID_1, "file1.pdf", "Alice", 0);
        sub1.setIdentityStatus(IdentityStatus.DISAMBIGUATION_REQUIRED);
        Submission sub2 = buildSubmission(SUB_ID_2, "file2.pdf", "Bob", 1);

        when(submissionRepository.findByIdAndSessionId(SUB_ID_1, SESSION_ID))
                .thenReturn(Optional.of(sub1));
        when(submissionRepository.findAllBySessionIdOrderByPositionAsc(SESSION_ID))
                .thenReturn(List.of(sub1, sub2));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateIdentityRequest request = UpdateIdentityRequest.builder()
                .studentDisplayName("Charlie")
                .build();

        submissionService.updateIdentity(SESSION_ID, SUB_ID_1, request);

        // sub1 should be cleared from DISAMBIGUATION_REQUIRED since "Charlie" is unique
        assertThat(sub1.getIdentityStatus()).isEqualTo(IdentityStatus.VERIFIED);
    }

    @Test
    void confirmIdentities_setsAllToVerified() {
        stubCurrentTa();
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));

        Submission sub1 = buildSubmission(SUB_ID_1, "file1.pdf", "Alice", 0);
        sub1.setIdentityStatus(IdentityStatus.UNVERIFIED);
        Submission sub2 = buildSubmission(SUB_ID_2, "file2.pdf", "Bob", 1);
        sub2.setIdentityStatus(IdentityStatus.UNVERIFIED);

        when(submissionRepository.findAllBySessionIdOrderByPositionAsc(SESSION_ID))
                .thenReturn(List.of(sub1, sub2));

        submissionService.confirmIdentities(SESSION_ID);

        assertThat(sub1.getIdentityStatus()).isEqualTo(IdentityStatus.VERIFIED);
        assertThat(sub2.getIdentityStatus()).isEqualTo(IdentityStatus.VERIFIED);
        verify(submissionRepository).saveAll(List.of(sub1, sub2));
    }

    @Test
    void validateBatchLimit_throwsWhenExceeded() {
        when(submissionRepository.countBySessionId(SESSION_ID)).thenReturn(145L);

        assertThatThrownBy(() -> submissionService.validateBatchLimit(SESSION_ID, 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Batch limit exceeded");
    }

    @Test
    void validateBatchLimit_allowsWithinLimit() {
        when(submissionRepository.countBySessionId(SESSION_ID)).thenReturn(100L);

        // Should not throw
        submissionService.validateBatchLimit(SESSION_ID, 50);
    }

    private Submission buildSubmission(UUID id, String filename, String displayName, int position) {
        return Submission.builder()
                .id(id)
                .session(session)
                .s3Key("uploads/" + TA_ID + "/" + SESSION_ID + "/" + filename)
                .originalFilename(filename)
                .studentDisplayName(displayName)
                .identityStatus(IdentityStatus.VERIFIED)
                .extractionStatus(ExtractionStatus.COMPLETED)
                .isOversized(false)
                .position(position)
                .createdAt(Instant.now())
                .build();
    }
}
