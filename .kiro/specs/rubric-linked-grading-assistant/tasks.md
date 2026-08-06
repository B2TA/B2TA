# Implementation Plan: Rubric-Linked Grading Assistant

## Overview

This plan converts the existing static Figma Make prototype into a production system with a Java Spring Boot backend on AWS ECS Fargate, PostgreSQL on RDS, S3 file storage, Amazon Bedrock AI analysis, and Cognito authentication. The frontend marking view UI exists as a static mockup — tasks reflect converting it to use real data and API calls. All backend and infrastructure work is new.

Backend work is split into two parallel tracks:
- **Backend Team A**: Core data layer, session/rubric/submission CRUD, upload/ingestion pipeline
- **Backend Team B**: AI integration (Match_Engine + Comment_Assistant), grading/review/export endpoints, auth

## Tasks

- [x] 1. Project scaffolding and shared interfaces
  - [x] 1.1 Initialize Java Spring Boot backend project with multi-module Maven structure
    - Create root Maven POM with modules: `api`, `worker`, `common`
    - Configure Java 21, Spring Boot 3.x, Spring profiles (`api`, `worker`)
    - Add shared dependencies: Spring Web, Spring Data JPA, PostgreSQL driver, AWS SDK v2, Jackson, jqwik, Testcontainers
    - _Requirements: 19.9, Design: ECS service design_

  - [x] 1.2 Define shared domain model classes and DTOs in `common` module
    - Create JPA entities: `TaUser`, `GradingSession`, `Rubric`, `Criterion`, `PerformanceLevel`, `Submission`, `SuggestedMatch`, `ConfirmedMatch`, `GradingRecord`, `CriterionScore`, `AsyncJob`
    - Create request/response DTOs for all API contracts
    - _Requirements: Design Data Models section_

  - [x] 1.3 Create PostgreSQL schema migration scripts (Flyway)
    - Write V1 migration with all CREATE TABLE statements from design
    - Include all indexes, constraints, and foreign keys
    - Configure Flyway in Spring Boot application properties
    - _Requirements: Design Data Models section_

  - [x] 1.4 Set up frontend production project structure alongside prototype
    - Create `src/app/` directory for production React SPA
    - Add dependencies: `@aws-amplify/auth`, `@tanstack/react-query`, `react-router`, `zustand`, `@dnd-kit/core`
    - Set up React Router with route stubs: `/login`, `/sessions`, `/sessions/:id/setup`, `/sessions/:id/mark`, `/sessions/:id/review`
    - Create shared TypeScript interfaces matching backend DTOs
    - _Requirements: Design Frontend Components section_

  - [x] 1.5 Configure AWS infrastructure as code (CDK or CloudFormation)
    - Define VPC with public/private subnets
    - Define RDS PostgreSQL 16 instance (db.t4g.medium, Multi-AZ)
    - Define ECS Fargate cluster with API and Worker services
    - Define ALB with HTTPS listener and target group
    - Define S3 bucket with lifecycle rules (30-day exports, 180-day uploads)
    - Define SQS queue and DLQ with configured timeouts
    - Define Cognito User Pool (no self-signup, email+password)
    - Define security groups per design specification
    - _Requirements: 18.1-11, 19.3-5, Design Architecture section_

- [~] 2. Checkpoint - Ensure project compiles and infrastructure plan is reviewable
  - Ensure all tests pass, ask the user if questions arise.

- [x] 3. Backend Team A — Core Data Layer and Session Management
  - [x] 3.1 Implement `SessionService` and `SessionController` (CRUD)
    - POST/GET/DELETE `/api/sessions` endpoints
    - Enforce TA ownership on all queries (tenant isolation)
    - Store/retrieve grading sessions with created/updated timestamps
    - _Requirements: 14.8, 14.9, 18.5, 19.6_

  - [x] 3.2 Implement `RubricController` and rubric CRUD endpoints
    - GET/PUT `/api/sessions/{id}/rubric` for loading and saving rubrics
    - Validate criterion count (1-30), title length (1-200), max points (0.01-1000), level count (1-10)
    - Assign unique display colors from palette (30+ colors, 3:1 contrast ratio)
    - _Requirements: 1.4, 1.5, 1.6, 2.1-4, 2.6-10_

  - [x] 3.3 Implement `UploadService` with pre-signed S3 URL generation
    - POST `/api/sessions/{id}/rubric/upload-url` — single object key, 15-min TTL
    - POST `/api/sessions/{id}/submissions/upload-urls` — batch URLs (1-300 files)
    - Scope all URLs to TA's S3 prefix: `uploads/{ta_id}/{session_id}/...`
    - Validate file extensions and sizes before issuing URLs
    - _Requirements: 1.2, 1.3, 1.9, 4.2, 4.3, 4.13, 4.14, 18.6_

  - [x] 3.4 Implement `RubricParseHandler` in Worker service
    - Parse PDF rubrics (extract text, identify table structure, map rows to criteria)
    - Parse CSV rubrics (header row = level labels, data rows = criteria)
    - Parse XLSX rubrics (same logic as CSV on first sheet)
    - Handle unresolved point values (retain criterion, mark as requiring completion)
    - Return parse failure with filename, format, and reason on failure
    - 10-second timeout with parse-timeout failure
    - _Requirements: 1.4, 1.5, 1.7, 1.8, 1.10, 1.11, 1.12, 1.14_

  - [ ]* 3.5 Write property tests for Rubric_Parser and Rubric_Printer
    - **Property 1: Rubric Serialization Round-Trip**
    - **Property 2: Rubric Parse Idempotence**
    - **Validates: Requirements 3.3, 3.4, 3.5**

  - [x] 3.6 Implement `RubricPrinter` (CSV serialization) and export endpoint
    - POST `/api/sessions/{id}/rubric/export` — serialize to CSV, upload to S3, return pre-signed download URL
    - RFC 4180 quoting for special characters (commas, quotes, line breaks, whitespace)
    - Validate rubric has ≥1 criterion before export
    - _Requirements: 3.1, 3.2, 3.5, 3.6, 3.7, 3.8_

  - [x] 3.7 Implement `SubmissionController` and submission management endpoints
    - GET `/api/sessions/{id}/submissions` — list with extraction status
    - PUT `/api/sessions/{id}/submissions/{subId}/identity` — update display name
    - POST `/api/sessions/{id}/submissions/confirm` — confirm identities
    - Enforce 150-submission batch limit
    - _Requirements: 5.5, 5.6, 5.9, 5.10, 5.11, 19.1, 19.2_

  - [x] 3.8 Implement `SubmissionIngestHandler` in Worker service
    - Expand ZIP archives (validate entries, reject path traversal, enforce 300-entry and 1GB limits)
    - Skip non-supported extensions in ZIPs, record in ingestion report
    - Track per-file progress for resumability (idempotency key: session_id + filename)
    - Update job progress for polling
    - _Requirements: 4.4, 4.7, 4.9, 4.10, 4.11, 4.16, 19.7_

  - [x] 3.9 Implement `TextExtractor` for PDF, DOCX, TXT, MD formats
    - Extract plain text with zero-based character offsets (start < end, ascending, non-overlapping)
    - 120-second timeout per file
    - Mark extraction-failed with appropriate reason (unreadable, password-protected, no text, timeout)
    - Flag oversized submissions (>100,000 chars)
    - _Requirements: 4.5, 4.7, 4.8, 4.12, 4.15, 5.8_

  - [ ]* 3.10 Write property tests for TextExtractor and RosterResolver
    - **Property 3: Text Run Ordering Invariant**
    - **Property 4: Student Identity Normalization**
    - **Validates: Requirements 4.5, 5.1, 5.2**

  - [x] 3.11 Implement `RosterResolver` for student identity derivation
    - Parse Canvas filename convention (student name + late marker + numeric ID)
    - Fallback: filename stem with whitespace normalization (trim, collapse, truncate to 200)
    - Mark verified vs unverified identity status
    - _Requirements: 5.1, 5.2, 5.3_

  - [x] 3.12 Implement `AsyncJob` tracking and polling endpoint
    - GET `/api/jobs/{jobId}` — return status, progress_current, progress_total, failure_reason
    - POST endpoints that trigger async work return job ID immediately
    - SQS message publishing from API service
    - SQS long-polling listener in Worker service (20s wait, 300s visibility timeout)
    - _Requirements: 4.6, 19.8_

- [~] 4. Checkpoint - Core data layer and ingestion pipeline complete
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 5. Backend Team B — Authentication and AI Integration
  - [~] 5.1 Implement `AuthFilter` with Cognito JWT validation
    - Validate JWT signature via Cognito JWKS endpoint
    - Check `exp`, `iss`, `token_use=access`
    - Extract `sub` claim, resolve to `TaUser` entity
    - Set Spring Security context with TA identity
    - Return 401 for absent/expired/invalid tokens
    - _Requirements: 18.1, 18.3, 18.4_

  - [~] 5.2 Implement tenant isolation enforcement across all repositories
    - Every query includes `ta_id` filter
    - Return 404 (not 403) for cross-tenant access attempts
    - Scope pre-signed URLs to TA's S3 prefix
    - _Requirements: 18.5, 18.6, 18.7_

  - [~] 5.3 Implement `SensitiveDataFilter` for structured logging
    - Strip access tokens, student display names, feedback text from all log records
    - Only IDs (session_id, submission_id, criterion_id) appear in logs
    - Configure Logback + logstash-encoder for JSON logging
    - _Requirements: 18.11_

  - [~] 5.4 Implement `MatchEngineHandler` in Worker service
    - Chunk submissions at 4000-char boundaries (prefer sentence breaks within 200-char window)
    - Add 400-char overlap between consecutive chunks
    - Call Bedrock Claude Sonnet 4 with structured output schema enforcement
    - Map chunk-local offsets back to global offset space
    - Produce 0-5 suggested matches per criterion (top 5 by confidence)
    - Validate all offsets within submission bounds, passage length 20-1500 chars
    - _Requirements: 6.1, 6.2, 6.3, 6.5, 6.9, 6.10_

  - [~] 5.5 Implement match deduplication and persistence
    - Discard matches overlapping ≥50% of shorter range with a retained match
    - Persist suggested matches in DB (reuse on reopen, no re-analysis)
    - Mark criterion+submission as analysis-unavailable after 4 failed Bedrock calls
    - Retry with exponential backoff: 1s, 2s, 4s
    - Semaphore: max 5 concurrent Bedrock invocations per worker
    - _Requirements: 6.4, 6.7, 6.8, 6.11_

  - [ ]* 5.6 Write property tests for Match_Engine components
    - **Property 5: Match Output Field Invariant**
    - **Property 6: Match Overlap Deduplication**
    - **Property 7: Chunk Offset Remapping Correctness**
    - **Validates: Requirements 6.2, 6.3, 6.4, 6.5**

  - [~] 5.7 Implement `GradingController` — save/load grading records
    - GET `/api/sessions/{id}/submissions/{subId}/grading` — load record + matches
    - PUT `/api/sessions/{id}/submissions/{subId}/grading` — atomic save (transaction: upsert grading_record + criterion_scores + confirmed_matches)
    - Validate point values within bounds, override ≤ max_points
    - Match management: confirm, reject, create manual, delete
    - POST `/api/sessions/{id}/submissions/{subId}/reanalyze/{criterionId}` — re-run analysis, mark old matches as stale
    - _Requirements: 14.1-6, 14.10-12, 10.1-9, 6.14_

  - [ ]* 5.8 Write property tests for ScoreCalculator and GradingRecord persistence
    - **Property 8: Score Total Arithmetic**
    - **Property 9: Grading Record Persistence Round-Trip**
    - **Validates: Requirements 11.3, 11.9, 14.10**

  - [~] 5.9 Implement `CommentController` — AI comment suggestions
    - POST `/api/sessions/{id}/submissions/{subId}/comments/suggest`
    - Call Bedrock Claude Haiku 4.5 with selected levels + confirmed matches
    - Return 1-5 feedback snippets (1-1000 chars each)
    - 15-second timeout, block if zero performance levels selected
    - _Requirements: 12.3, 12.6, 12.7, 12.8_

  - [~] 5.10 Implement `ReviewController` — review screen data
    - GET `/api/sessions/{id}/review` — all submissions with per-criterion scores, totals, flags
    - POST `/api/sessions/{id}/review/confirm` — record confirmation timestamp
    - Clear confirmation when any grading record changes post-confirmation
    - Block export without review confirmation
    - _Requirements: 15.1-12_

  - [~] 5.11 Implement `ExportController` and `ExportService`
    - POST `/api/sessions/{id}/export/generic` — CSV with student name, per-criterion points, level labels, total, max, feedback
    - POST `/api/sessions/{id}/export/canvas` — Canvas gradebook format
    - RFC 4180 encoding (UTF-8, CRLF line endings, proper quoting)
    - Empty score for incomplete submissions (not zero)
    - Store in S3 under `exports/` prefix, return pre-signed download URL (15-min TTL)
    - 30-second timeout for 150-submission batch
    - _Requirements: 16.1-11_

  - [ ]* 5.12 Write property tests for ExportService
    - **Property 10: Export CSV Round-Trip**
    - **Validates: Requirements 16.6, 16.7**

- [~] 6. Checkpoint - Backend API and AI integration complete
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 7. Frontend — Authentication and Session Management
  - [~] 7.1 Implement AuthProvider with Cognito integration
    - Configure `@aws-amplify/auth` with user pool settings
    - Sign-in flow, token management, transparent refresh
    - Re-authentication modal on 401 (retain unsaved state)
    - Sign-out: discard tokens and all in-memory data within 1 second
    - Protected route wrapper (redirect to `/login` if unauthenticated)
    - _Requirements: 18.1, 18.3, 18.8, 18.9, 18.10_

  - [~] 7.2 Implement SessionListPage
    - List TA's grading sessions with name, creation date, submission count
    - Create new session, delete session with confirmation
    - Resume previously saved sessions
    - _Requirements: 14.8, 14.9, 19.6_

  - [~] 7.3 Implement SessionSetupPage — Rubric upload and editor
    - RubricUploadZone: drag-drop or file picker, validate extension/size client-side
    - Pre-signed URL upload flow (show progress, handle expiry/retry)
    - Trigger parse job, poll for completion, display parsed criteria
    - RubricEditor: full CRUD for criteria and performance levels, validation, reordering via drag-drop
    - Block transition to marking if rubric is invalid (zero criteria, unresolved values, empty titles)
    - Rubric export button
    - _Requirements: 1.1-15, 2.1-10, 3.1-8_

  - [~] 7.4 Implement SessionSetupPage — Submission upload and ingestion
    - SubmissionUploadZone: accept 1-300 files, validate extensions/sizes
    - Batch pre-signed URL upload (parallel, max 5 concurrent)
    - Trigger ingestion job, poll progress (files ingested/remaining/total)
    - IngestionReport: display extraction failures, skipped entries, rejected files with reasons
    - StudentConfirmation: list all submissions with resolved names, edit names, acknowledge unverified/disambiguation
    - _Requirements: 4.1-16, 5.1-11_

- [ ] 8. Frontend — Convert Marking View from Static to Dynamic
  - [~] 8.1 Refactor MarkingView to load data from API
    - Replace hardcoded `CRITERIA`, `ESSAY_PARAGRAPHS`, `HIGHLIGHT_SPANS` with API calls via React Query
    - Load rubric, submission text, suggested matches, grading record on mount
    - Implement loading and error states
    - Wire score state to API save endpoint
    - _Requirements: 7.9, 8.8, 13.8, 14.8_
    - _Note: The existing prototype (App.tsx) provides the UI structure; this task wires it to real data_

  - [~] 8.2 Implement RubricPanel with dynamic data and all states
    - Render criterion cards from API data (variable count, colors, levels)
    - Display match counts (suggested + confirmed) per criterion per submission
    - Show all states: unscored, scored, no-evidence-found, analysis-unavailable, analysis-in-progress, stale
    - Performance level selection with score update (<100ms)
    - Manual point override input with validation
    - _Requirements: 7.1-9, 11.1-11_

  - [~] 8.3 Implement DocumentViewer with dynamic highlighting
    - Render extracted text with paragraph breaks preserved
    - Highlight passages from suggested and confirmed matches using criterion colors
    - Distinct visual treatment for suggested vs confirmed (labeled in legend)
    - Overlap handling: 2-4 overlaps show all colors, 5+ show shared treatment with count label
    - Text contrast ≥4.5:1 over all highlights
    - _Requirements: 8.1-5, 8.9, 8.11_

  - [~] 8.4 Implement match interaction (hover, confirm, reject, manual)
    - Hover tooltip: criterion title, rationale, confidence (show/dismiss within 300ms)
    - Click/activate: confirm/reject controls per match
    - Text selection → assign to criterion (create manual confirmed match)
    - Validate selection length (1-5000 chars, non-whitespace)
    - Update highlight treatment within 200ms of state change
    - _Requirements: 8.5, 8.6, 10.1-10_

  - [~] 8.5 Implement criterion-to-passage navigation
    - Click criterion card → scroll to first passage, highlight criterion's passages distinctly
    - Next/previous passage controls with position display
    - Click passage → scroll rubric panel to associated criterion card
    - Multi-criterion passage: show criterion picker
    - Handle zero-passage and analysis-unavailable states
    - _Requirements: 9.1-8_

  - [~] 8.6 Implement confidence threshold filtering and stale match UI
    - Slider/control to set confidence threshold
    - Hide matches below threshold (retain in store), show hidden count per criterion
    - Display stale state on criterion card when rubric is edited post-analysis
    - Provide re-analyze control per criterion
    - _Requirements: 6.12, 6.13, 6.14_

  - [~] 8.7 Implement FeedbackEditor and CommentAssistant
    - Overall feedback textarea (0-10,000 chars) and per-criterion feedback (0-2,000 chars)
    - Request AI comment suggestions (POST to API), display 1-5 snippets
    - Insert snippet at cursor position, label as AI-generated until edited
    - Handle timeout/failure (retain entered text, show retry)
    - Block request if zero levels selected
    - _Requirements: 12.1-10_

  - [~] 8.8 Implement BatchNavigator with save-and-advance flow
    - Display position (X of N), progress counts (complete vs incomplete)
    - Save-and-advance / save-and-previous controls
    - Unsaved changes warning with confirmation before discard
    - Unscored criteria warning with confirmation before advance
    - Submission list picker (by name and position)
    - Restore stored grading data when navigating to previously graded submission
    - End-of-batch handling (stay on current, show message)
    - _Requirements: 13.1-10_

  - [~] 8.9 Implement explicit save, unsaved indicator, and session resume
    - Save control (Ctrl+S and button) → PUT grading record to API
    - Unsaved indicator when changes exist, saved timestamp when saved
    - Browser beforeunload confirmation when unsaved changes present
    - 30-second save timeout with retry
    - _Requirements: 14.1-7, 14.10-12_

- [~] 9. Checkpoint - Marking view fully functional with API integration
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 10. Frontend — Keyboard Shortcuts, Review, and Export
  - [~] 10.1 Implement full keyboard shortcut system
    - Save+advance, save+previous, save-only shortcuts
    - Next/previous criterion card focus
    - Number keys (1-9, 0 for 10th) to select performance level on focused criterion
    - Next/previous passage of focused criterion
    - Confirm/reject match on focused passage
    - Focus feedback field shortcut
    - Shortcut reference overlay (opened via shortcut)
    - Suppress shortcuts when text input is focused
    - Focus indicators (3:1 contrast ratio) on all focusable elements
    - _Requirements: 17.1-14_

  - [~] 10.2 Implement ReviewScreen
    - Table: one row per submission (name, per-criterion points, total, max)
    - Flag labels: incomplete grading, extraction-failed, oversized, unverified identity, disambiguation, manual overrides
    - Flagged/unflagged counts displayed
    - Click row → open MarkingView for that submission
    - Confirm button → record confirmation in API
    - Zero-submissions guard
    - _Requirements: 15.1-12_

  - [~] 10.3 Implement ExportDialog
    - Generic CSV export and Canvas CSV export buttons
    - Require review confirmation before export (show flag counts, require explicit confirmation if flagged)
    - Trigger export API, receive pre-signed download URL
    - Handle failure with retry
    - Zero-submissions guard
    - _Requirements: 16.1-11_

  - [ ]* 10.4 Write frontend component tests (Vitest + React Testing Library)
    - ScoreCalculator: correct total computation, <100ms update
    - HighlightLayer: correct colors, overlap handling
    - Keyboard shortcuts: all shortcuts trigger correct actions, no conflicts
    - Unsaved changes: warning on navigation
    - Accessibility: focus indicators, ARIA roles, text labels on all states
    - _Requirements: 7.5, 8.10, 8.11, 11.7, 17.11_

- [ ] 11. Integration wiring and end-to-end flow
  - [~] 11.1 Wire frontend API client with auth headers and error handling
    - Axios/fetch wrapper that attaches Cognito access token to all requests
    - Global error interceptor: 401 → re-auth modal, 404/500 → error display with retry
    - React Query configuration: polling for job status, cache invalidation on saves
    - _Requirements: 18.3, 18.4, 18.9, 18.10_

  - [~] 11.2 Implement S3 direct upload flow end-to-end
    - Frontend: request pre-signed URL → upload file bytes to S3 → confirm completion to API
    - Progress tracking during upload
    - Handle URL expiry (15-min TTL) with retry
    - _Requirements: 1.2, 1.15, 4.3_

  - [~] 11.3 Implement async job polling UI pattern
    - Generic polling hook: poll `/api/jobs/{id}` every 2s, display progress, stop on complete/failed
    - Used by: rubric parse, submission ingestion, match analysis
    - _Requirements: 4.6, 19.8_

  - [ ]* 11.4 Write integration tests for critical flows
    - Full upload → parse → display flow
    - Save grading record → reload → verify round-trip
    - Export → download → verify CSV content
    - Auth flow: login → access data → isolation from other TAs
    - _Requirements: 14.10, 16.7, 18.5_

- [~] 12. Final checkpoint - Full system integration verified
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- The existing `src/App.tsx` prototype provides the UI structure for the Marking View — task 8.x converts it from static to dynamic rather than rebuilding from scratch
- Backend Team A (tasks 3.x) and Backend Team B (tasks 5.x) can work in parallel after shared scaffolding (task 1.x) is complete
- Frontend work (tasks 7.x, 8.x, 10.x) can begin on auth and setup pages while backend teams build APIs, using mock data initially
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- All backend code is Java 21 + Spring Boot 3.x; all frontend code is TypeScript + React 19

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.4", "1.5"] },
    { "id": 1, "tasks": ["1.2", "1.3"] },
    { "id": 2, "tasks": ["3.1", "3.3", "3.12", "5.1", "5.3", "7.1"] },
    { "id": 3, "tasks": ["3.2", "3.7", "3.8", "5.2", "5.4", "7.2"] },
    { "id": 4, "tasks": ["3.4", "3.9", "3.11", "5.5", "5.7", "7.3"] },
    { "id": 5, "tasks": ["3.5", "3.6", "3.10", "5.6", "5.9", "5.10", "7.4"] },
    { "id": 6, "tasks": ["5.8", "5.11", "8.1", "11.1", "11.2", "11.3"] },
    { "id": 7, "tasks": ["5.12", "8.2", "8.3", "8.7"] },
    { "id": 8, "tasks": ["8.4", "8.5", "8.6", "8.8", "8.9"] },
    { "id": 9, "tasks": ["10.1", "10.2", "10.3"] },
    { "id": 10, "tasks": ["10.4", "11.4"] }
  ]
}
```
