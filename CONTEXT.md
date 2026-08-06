# B2TA Grading

B2TA is a standalone grading workspace for teaching assistants. It owns the grading workflow and connects to external learning management systems through adapters.

## Language

**Grading Session**:
A TA-owned workspace that combines one rubric, one batch of submissions, and the grading records produced for that batch.
_Avoid_: Canvas session, assignment session

**Submission Batch**:
The ordered set of student submissions graded within one Grading Session, regardless of whether the submissions were uploaded or imported from an LMS.
_Avoid_: Canvas queue

**Grading Record**:
The TA-authored scores, confirmed evidence, and feedback for one submission.
_Avoid_: AI grade

**Suggested Match**:
An AI-proposed association between a rubric criterion and a passage in a submission. It is evidence for TA review, not a score.
_Avoid_: AI grade, automatic assessment

**LMS Integration**:
The capability to import course data into B2TA and publish completed grading results back to a learning management system while the TA remains in B2TA.
_Avoid_: embedded LMS app

**LMS Adapter**:
A boundary component that translates between an LMS-specific API and B2TA's canonical Rubric, Submission, Student Identity, and Grading Record models.
_Avoid_: Canvas core, LMS backend

**Canvas Adapter**:
The first LMS Adapter, responsible only for Canvas authentication, import mapping, pagination, attachment retrieval, and grade publication.
_Avoid_: Canvas mode

**Manual Import**:
Creation of a Grading Session from rubric and submission files supplied directly by a TA rather than fetched through an LMS Adapter.
_Avoid_: offline mode
