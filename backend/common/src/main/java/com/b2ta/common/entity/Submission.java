package com.b2ta.common.entity;

import com.b2ta.common.entity.converter.PersistableEnumConverters;
import com.b2ta.common.entity.enums.ExtractionStatus;
import com.b2ta.common.entity.enums.IdentityStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "submission")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private GradingSession session;

    @Column(name = "s3_key", nullable = false)
    private String s3Key;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "student_display_name")
    private String studentDisplayName;

    @Column(name = "canvas_submission_id")
    private String canvasSubmissionId;

    @Convert(converter = PersistableEnumConverters.IdentityStatusConverter.class)
    @Column(name = "identity_status", nullable = false, length = 20)
    private IdentityStatus identityStatus;

    @Convert(converter = PersistableEnumConverters.ExtractionStatusConverter.class)
    @Column(name = "extraction_status", nullable = false, length = 20)
    private ExtractionStatus extractionStatus;

    @Column(name = "extraction_failure_reason")
    private String extractionFailureReason;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "extracted_char_count")
    private Integer extractedCharCount;

    @Column(name = "is_oversized", nullable = false)
    private Boolean isOversized;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (isOversized == null) {
            isOversized = false;
        }
    }
}
