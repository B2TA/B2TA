# Canvas Integration — Requirements

## Introduction

B2TA currently renders a high-fidelity SpeedGrader overlay from hardcoded constants in
`src/App.tsx` (`CRITERIA`, `ESSAY_PARAGRAPHS`, `HIGHLIGHT_SPANS`, `AI_COMMENTS`). This
feature replaces those constants with live data from a Canvas LMS instance and writes
TA decisions back to the Canvas gradebook.

Target instance: `https://canvas.cic.wtarit.me` (course `1`).

The AI proposes; the TA disposes. No score reaches Canvas without an explicit TA action.

## Glossary

| Term | Meaning |
|---|---|
| **Criterion** | One row of a Canvas rubric. Canvas ids look like `_1234`, not integers. |
| **Rating** | One achievement level within a criterion, with `points` and `description`. |
| **Evidence span** | A verbatim passage of the submission supporting a criterion score. |
| **Assessment** | The full set of per-criterion scores + comments for one submission. |

---

## Requirement 1: Load rubric from Canvas

**User story:** As a TA, I want the rubric sidebar to show my assignment's real rubric,
so that I am grading against the criteria my course actually uses.

#### Acceptance criteria

1. WHEN the app loads with a `courseId` and `assignmentId` THE SYSTEM SHALL fetch the
   assignment from Canvas and derive rubric criteria from its `rubric` array.
2. WHERE a criterion defines `ratings` THE SYSTEM SHALL present them in descending
   points order as the selectable levels.
3. IF the assignment has no rubric attached THE SYSTEM SHALL display an explicit
   "No rubric attached to this assignment" state and SHALL NOT fall back to demo data.
4. THE SYSTEM SHALL assign each criterion a stable highlight color derived from its
   position, so colors do not change between reloads.
5. THE SYSTEM SHALL preserve each criterion's Canvas `id` verbatim for write-back.

## Requirement 2: Load submissions and student roster

**User story:** As a TA, I want to page through real student submissions, so that the
"Student 7 of 24" control reflects my actual grading queue.

#### Acceptance criteria

1. WHEN the app loads THE SYSTEM SHALL fetch all submissions for the assignment,
   following Canvas `Link` header pagination until exhausted.
2. THE SYSTEM SHALL exclude submissions with `workflow_state == "unsubmitted"` from
   the grading queue.
3. WHEN the TA advances to the next or previous student THE SYSTEM SHALL load that
   submission without a full page reload.
4. THE SYSTEM SHALL display each student's name and the submission count in the header.
5. IF a submission was already graded THE SYSTEM SHALL pre-populate the sidebar from
   its existing `rubric_assessment` and mark it as already-graded.

## Requirement 3: Extract submission text

**User story:** As a TA, I want to read the student's actual work in the center panel,
so that highlights land on real prose.

#### Acceptance criteria

1. WHERE `submission_type == "online_text_entry"` THE SYSTEM SHALL strip HTML from
   `body` and split it into paragraphs.
2. WHERE `submission_type == "online_upload"` THE SYSTEM SHALL download the attachment
   and extract text by content type: PDF via `pypdf`, DOCX via `python-docx`, `.ipynb`
   by concatenating cell sources, plain text as-is.
3. THE SYSTEM SHALL normalize extracted text exactly once (ligatures, soft hyphens,
   collapsed whitespace) and SHALL persist that normalized form as the single source of
   truth for prompting, evidence verification, and rendering.
4. IF extracted text is shorter than 200 characters THE SYSTEM SHALL treat the document
   as scanned and fall back to Amazon Textract.
5. IF extraction fails entirely THE SYSTEM SHALL surface the failure to the TA and
   still allow manual grading.

## Requirement 4: AI rubric cross-reference with verified evidence

**User story:** As a TA, I want each criterion to arrive with a suggested level and the
exact passages that justify it, so that I can verify a judgment instead of reconstructing
it from scratch.

#### Acceptance criteria

1. WHEN a submission is opened THE SYSTEM SHALL request an analysis containing, per
   criterion, a suggested rating, a confidence score, a one-sentence rationale, and zero
   or more evidence spans.
2. THE SYSTEM SHALL verify every returned evidence quote appears verbatim in the
   normalized submission text, matching whitespace-insensitively.
3. IF a quote cannot be located THE SYSTEM SHALL discard that evidence span and SHALL
   NOT display it. Discards SHALL be counted in logs.
4. THE SYSTEM SHALL compute each span's paragraph index and in-paragraph character
   offset server-side and SHALL ignore any offsets supplied by the model.
5. THE SYSTEM SHALL mark all AI-produced spans as unconfirmed (dashed border) until the
   TA confirms them.
6. WHERE a criterion has no verified evidence THE SYSTEM SHALL show
   "No matching passage found — flag manually."
7. THE SYSTEM SHALL cache analyses keyed by assignment, student, and submission attempt,
   and SHALL serve the cache on repeat opens.

## Requirement 5: Write grades back to Canvas

**User story:** As a TA, I want "Sync to Canvas gradebook" to post my scores and comment,
so that I never retype what I already decided in the overlay.

#### Acceptance criteria

1. WHEN the TA clicks Sync THE SYSTEM SHALL submit all selected criterion scores as a
   Canvas `rubric_assessment`, plus the comment text if non-empty.
2. THE SYSTEM SHALL block the sync and explain why IF any criterion is still unscored.
3. THE SYSTEM SHALL never write a score the TA did not explicitly select. An AI
   suggestion alone SHALL NOT be sufficient.
4. WHEN the sync succeeds THE SYSTEM SHALL show the confirmed state and the Canvas-side
   total.
5. IF the sync fails THE SYSTEM SHALL preserve all TA input, surface the Canvas error,
   and permit retry.
6. THE SYSTEM SHALL record who synced what and when.

## Requirement 6: Credentials and safety

**User story:** As a course administrator, I want student work and API credentials
handled correctly, so that using this tool does not create a privacy incident.

#### Acceptance criteria

1. THE SYSTEM SHALL store the Canvas API token in AWS Secrets Manager and SHALL NOT
   expose it to the browser.
2. THE SYSTEM SHALL route every Canvas call through the backend; the frontend SHALL
   never call Canvas directly.
3. THE SYSTEM SHALL NOT log submission text or student names at INFO level.
4. THE SYSTEM SHALL scope the token to a dedicated Canvas service account with only
   the permissions this feature needs.
5. WHERE the demo runs without Canvas THE SYSTEM SHALL serve equivalent fixtures behind
   the identical interface, selected by a single environment variable.

---

## Out of scope

- Canvas OAuth2 authorization-code flow (token-based auth only for the hackathon).
- Grading anything but the single assignment configured at load time.
- Group assignments, peer review, moderated grading, anonymous grading.
- Editing rubrics from within B2TA.
