-- V1__initial_schema.sql
-- Rubric-Linked Grading Assistant - Initial PostgreSQL 16 Schema
-- All tables, indexes, constraints, and foreign keys

-- ============================================================================
-- 1. ta_user - Core identity (TA accounts linked to Cognito)
-- ============================================================================
CREATE TABLE ta_user (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cognito_sub     VARCHAR(128) NOT NULL,
    email           VARCHAR(320) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ta_user_cognito_sub UNIQUE (cognito_sub)
);

-- ============================================================================
-- 2. grading_session - A TA's grading session for a batch of submissions
-- ============================================================================
CREATE TABLE grading_session (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ta_id               UUID NOT NULL,
    name                VARCHAR(200) NOT NULL,
    review_confirmed_at TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_session_ta FOREIGN KEY (ta_id)
        REFERENCES ta_user(id) ON DELETE CASCADE
);

CREATE INDEX idx_session_ta ON grading_session(ta_id);

-- ============================================================================
-- 3. rubric - One rubric per session (parsed from file or manual entry)
-- ============================================================================
CREATE TABLE rubric (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID NOT NULL,
    s3_key          VARCHAR(512),
    source_format   VARCHAR(10),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_rubric_session UNIQUE (session_id),
    CONSTRAINT fk_rubric_session FOREIGN KEY (session_id)
        REFERENCES grading_session(id) ON DELETE CASCADE,
    CONSTRAINT chk_rubric_source_format
        CHECK (source_format IN ('pdf', 'csv', 'xlsx', 'manual'))
);

-- ============================================================================
-- 4. criterion - Individual rubric criteria within a rubric
-- ============================================================================
CREATE TABLE criterion (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rubric_id           UUID NOT NULL,
    title               VARCHAR(200) NOT NULL,
    description         VARCHAR(2000) NOT NULL DEFAULT '',
    max_points          DECIMAL(7,2),
    display_color       VARCHAR(7) NOT NULL,
    position            SMALLINT NOT NULL,
    requires_completion BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_criterion_rubric FOREIGN KEY (rubric_id)
        REFERENCES rubric(id) ON DELETE CASCADE,
    CONSTRAINT chk_criterion_color CHECK (display_color ~ '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT chk_criterion_position CHECK (position >= 0),
    CONSTRAINT chk_criterion_max_points CHECK (max_points IS NULL OR max_points >= 0)
);

CREATE INDEX idx_criterion_rubric ON criterion(rubric_id, position);

-- ============================================================================
-- 5. performance_level - Score levels within a criterion
-- ============================================================================
CREATE TABLE performance_level (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    criterion_id    UUID NOT NULL,
    label           VARCHAR(100) NOT NULL,
    description     VARCHAR(2000) NOT NULL DEFAULT '',
    points          DECIMAL(7,2),
    position        SMALLINT NOT NULL,
    CONSTRAINT fk_level_criterion FOREIGN KEY (criterion_id)
        REFERENCES criterion(id) ON DELETE CASCADE,
    CONSTRAINT chk_level_position CHECK (position >= 0),
    CONSTRAINT chk_level_points CHECK (points IS NULL OR points >= 0)
);

CREATE INDEX idx_level_criterion ON performance_level(criterion_id, position);

-- ============================================================================
-- 6. submission - Student submissions within a grading session
-- ============================================================================
CREATE TABLE submission (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id              UUID NOT NULL,
    s3_key                  VARCHAR(512) NOT NULL,
    original_filename       VARCHAR(512) NOT NULL,
    student_display_name    VARCHAR(200) NOT NULL,
    canvas_submission_id    VARCHAR(100),
    identity_status         VARCHAR(20) NOT NULL DEFAULT 'unverified',
    extraction_status       VARCHAR(20) NOT NULL DEFAULT 'pending',
    extraction_failure_reason VARCHAR(50),
    extracted_text          TEXT,
    extracted_char_count    INTEGER,
    is_oversized            BOOLEAN NOT NULL DEFAULT false,
    position                INTEGER NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_submission_session FOREIGN KEY (session_id)
        REFERENCES grading_session(id) ON DELETE CASCADE,
    CONSTRAINT chk_submission_identity_status
        CHECK (identity_status IN ('verified', 'unverified', 'disambiguation_required')),
    CONSTRAINT chk_submission_extraction_status
        CHECK (extraction_status IN ('pending', 'success', 'failed')),
    CONSTRAINT chk_submission_extraction_failure_reason
        CHECK (extraction_failure_reason IS NULL OR extraction_failure_reason IN (
            'unreadable_file', 'password_protected', 'no_extractable_text', 'extraction_timeout'
        )),
    CONSTRAINT chk_submission_position CHECK (position >= 0),
    CONSTRAINT chk_submission_char_count CHECK (extracted_char_count IS NULL OR extracted_char_count >= 0)
);

CREATE INDEX idx_submission_session ON submission(session_id, position);

-- ============================================================================
-- 7. suggested_match - AI-generated passage matches
-- ============================================================================
CREATE TABLE suggested_match (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id   UUID NOT NULL,
    criterion_id    UUID NOT NULL,
    passage_start   INTEGER NOT NULL,
    passage_end     INTEGER NOT NULL,
    rationale       VARCHAR(300) NOT NULL,
    confidence      DECIMAL(3,2) NOT NULL,
    match_state     VARCHAR(20) NOT NULL DEFAULT 'suggested',
    is_stale        BOOLEAN NOT NULL DEFAULT false,
    discard_reason  VARCHAR(200),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_suggested_match_submission FOREIGN KEY (submission_id)
        REFERENCES submission(id) ON DELETE CASCADE,
    CONSTRAINT fk_suggested_match_criterion FOREIGN KEY (criterion_id)
        REFERENCES criterion(id) ON DELETE CASCADE,
    CONSTRAINT chk_suggested_match_state
        CHECK (match_state IN ('suggested', 'confirmed', 'rejected')),
    CONSTRAINT chk_suggested_match_offsets
        CHECK (passage_start >= 0 AND passage_end > passage_start),
    CONSTRAINT chk_suggested_match_confidence
        CHECK (confidence >= 0.00 AND confidence <= 1.00)
);

CREATE INDEX idx_match_submission_criterion
    ON suggested_match(submission_id, criterion_id);

-- ============================================================================
-- 8. confirmed_match - TA-confirmed or TA-authored matches
-- ============================================================================
CREATE TABLE confirmed_match (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id   UUID NOT NULL,
    criterion_id    UUID NOT NULL,
    passage_start   INTEGER NOT NULL,
    passage_end     INTEGER NOT NULL,
    rationale       VARCHAR(300) NOT NULL,
    confidence      DECIMAL(3,2),
    origin          VARCHAR(20) NOT NULL,
    source_match_id UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_confirmed_match_submission FOREIGN KEY (submission_id)
        REFERENCES submission(id) ON DELETE CASCADE,
    CONSTRAINT fk_confirmed_match_criterion FOREIGN KEY (criterion_id)
        REFERENCES criterion(id) ON DELETE CASCADE,
    CONSTRAINT fk_confirmed_match_source FOREIGN KEY (source_match_id)
        REFERENCES suggested_match(id) ON DELETE SET NULL,
    CONSTRAINT chk_confirmed_match_origin
        CHECK (origin IN ('ta_confirmed', 'ta_authored')),
    CONSTRAINT chk_confirmed_match_offsets
        CHECK (passage_start >= 0 AND passage_end > passage_start),
    CONSTRAINT chk_confirmed_match_confidence
        CHECK (confidence IS NULL OR (confidence >= 0.00 AND confidence <= 1.00))
);

CREATE INDEX idx_confirmed_submission_criterion
    ON confirmed_match(submission_id, criterion_id);

-- ============================================================================
-- 9. grading_record - Per-submission grading record
-- ============================================================================
CREATE TABLE grading_record (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id   UUID NOT NULL,
    overall_feedback TEXT NOT NULL DEFAULT '',
    saved_at        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_grading_record_submission UNIQUE (submission_id),
    CONSTRAINT fk_grading_record_submission FOREIGN KEY (submission_id)
        REFERENCES submission(id) ON DELETE CASCADE
);

-- ============================================================================
-- 10. criterion_score - Per-criterion scores within a grading record
-- ============================================================================
CREATE TABLE criterion_score (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    grading_record_id   UUID NOT NULL,
    criterion_id        UUID NOT NULL,
    selected_level_id   UUID,
    override_points     DECIMAL(7,2),
    criterion_feedback  VARCHAR(2000) NOT NULL DEFAULT '',
    CONSTRAINT uq_criterion_score_record_criterion UNIQUE (grading_record_id, criterion_id),
    CONSTRAINT fk_score_grading_record FOREIGN KEY (grading_record_id)
        REFERENCES grading_record(id) ON DELETE CASCADE,
    CONSTRAINT fk_score_criterion FOREIGN KEY (criterion_id)
        REFERENCES criterion(id) ON DELETE CASCADE,
    CONSTRAINT fk_score_selected_level FOREIGN KEY (selected_level_id)
        REFERENCES performance_level(id) ON DELETE SET NULL
);

CREATE INDEX idx_score_record ON criterion_score(grading_record_id);

-- ============================================================================
-- 11. async_job - Async job tracking for long-running operations
-- ============================================================================
CREATE TABLE async_job (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID NOT NULL,
    job_type        VARCHAR(30) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',
    progress_current INTEGER NOT NULL DEFAULT 0,
    progress_total  INTEGER NOT NULL DEFAULT 0,
    failure_reason  VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_job_session FOREIGN KEY (session_id)
        REFERENCES grading_session(id) ON DELETE CASCADE,
    CONSTRAINT chk_job_type
        CHECK (job_type IN ('rubric_parse', 'submission_ingest', 'match_analysis')),
    CONSTRAINT chk_job_status
        CHECK (status IN ('pending', 'in_progress', 'complete', 'failed')),
    CONSTRAINT chk_job_progress
        CHECK (progress_current >= 0 AND progress_total >= 0)
);

CREATE INDEX idx_job_session ON async_job(session_id, status);
