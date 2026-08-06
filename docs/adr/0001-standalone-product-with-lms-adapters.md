---
status: accepted
---

# Build a standalone product with LMS adapters

B2TA is a standalone grading workspace, not a Canvas extension or Canvas-specific frontend. It owns grading sessions, rubric-linked evidence review, scoring, feedback, and review; Canvas is the first LMS adapter used to import rubrics, rosters, and submissions and to publish TA-approved grades without making the TA leave B2TA. LMS-specific identifiers, credentials, pagination, and payloads remain behind the adapter boundary so additional LMS adapters can be added later without changing the grading core.
