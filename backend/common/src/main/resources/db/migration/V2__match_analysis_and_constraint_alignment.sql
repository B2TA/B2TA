-- V2__match_analysis_and_constraint_alignment.sql
-- Backend Team B (tasks 5.x) schema additions and corrections to V1.
--
-- 1. criterion_analysis: per (submission, criterion) Match_Engine state, needed to distinguish
--    "no evidence found" from "analysis unavailable" (Requirements 6.6-6.8, 6.14).
-- 2. confirmed_match uniqueness: at most one confirmed match per criterion per passage
--    (Requirements 10.5, 10.9).
-- 3. extraction_status gains 'oversized' so the ExtractionStatus enum round-trips.
-- 4. Column widths aligned with the DTO validation limits so a request that passes bean
--    validation cannot fail on a database length constraint.

-- ============================================================================
-- 1. criterion_analysis - Match_Engine state per (submission, criterion)
-- ============================================================================
CREATE TABLE criterion_analysis (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id   UUID NOT NULL,
    criterion_id    UUID NOT NULL,
    state           VARCHAR(20) NOT NULL DEFAULT 'pending',
    failure_count   SMALLINT NOT NULL DEFAULT 0,
    failure_reason  VARCHAR(500),
    analyzed_char_count INTEGER,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_criterion_analysis UNIQUE (submission_id, criterion_id),
    CONSTRAINT fk_analysis_submission FOREIGN KEY (submission_id)
        REFERENCES submission(id) ON DELETE CASCADE,
    CONSTRAINT fk_analysis_criterion FOREIGN KEY (criterion_id)
        REFERENCES criterion(id) ON DELETE CASCADE,
    CONSTRAINT chk_analysis_state
        CHECK (state IN ('pending', 'in_progress', 'complete', 'unavailable')),
    CONSTRAINT chk_analysis_failure_count CHECK (failure_count >= 0)
);

CREATE INDEX idx_analysis_submission ON criterion_analysis(submission_id);

-- ============================================================================
-- 2. One confirmed match per criterion per passage range (Req 10.5, 10.9)
-- ============================================================================
ALTER TABLE confirmed_match
    ADD CONSTRAINT uq_confirmed_match_passage
        UNIQUE (submission_id, criterion_id, passage_start, passage_end);

-- ============================================================================
-- 3. Allow the 'oversized' extraction outcome (Req 4.13, 6.10)
-- ============================================================================
ALTER TABLE submission DROP CONSTRAINT IF EXISTS chk_submission_extraction_status;
ALTER TABLE submission
    ADD CONSTRAINT chk_submission_extraction_status
        CHECK (extraction_status IN ('pending', 'success', 'failed', 'oversized'));

-- ============================================================================
-- 4. Column width alignment with DTO validation limits
-- ============================================================================
-- PerformanceLevelDto permits a 200-character label; V1 allowed only 100.
ALTER TABLE performance_level ALTER COLUMN label TYPE VARCHAR(200);

-- Criterion and level descriptions are validated at 2000 characters in the DTO layer and
-- declared as TEXT on the JPA entities.
ALTER TABLE criterion ALTER COLUMN description TYPE TEXT;
ALTER TABLE performance_level ALTER COLUMN description TYPE TEXT;

-- Per-criterion feedback is capped at 2000 characters by Requirement 12.2 and validated in
-- CriterionScoreDto; TEXT keeps the entity mapping and the DTO limit as the single source.
ALTER TABLE criterion_score ALTER COLUMN criterion_feedback TYPE TEXT;

-- ============================================================================
-- 5. Review screen and export read paths
-- ============================================================================
-- The review screen joins every submission of a session to its grading record; the export
-- service walks the same path. An index on the FK keeps that a single indexed lookup per row.
CREATE INDEX IF NOT EXISTS idx_grading_record_submission ON grading_record(submission_id);
