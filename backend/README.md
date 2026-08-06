# B2TA API

Small Express/TypeScript backend for the standalone B2TA frontend.

## Run

```bash
pnpm install
cp .env.example .env
pnpm dev
```

The API listens on `http://localhost:3001` by default. The Vite frontend proxies `/api`
requests to it during local development.

## Current interface

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/health` | Health check |
| `GET` | `/api/sessions` | List grading sessions |
| `POST` | `/api/sessions` | Create a grading session |
| `GET` | `/api/sessions/:id` | Load a grading session |
| `DELETE` | `/api/sessions/:id` | Delete a grading session |
| `GET` | `/api/sessions/:id/rubric` | Load the session rubric |
| `PUT` | `/api/sessions/:id/rubric` | Create or replace the session rubric |
| `GET` | `/api/sessions/:id/submissions` | List session submissions |
| `GET` | `/api/sessions/:id/submissions/:submissionId/artifact` | Display an imported submission PDF |
| `GET` | `/api/sessions/:id/submissions/:submissionId/grading-record` | Load the TA's saved grading record |
| `PUT` | `/api/sessions/:id/submissions/:submissionId/grading-record` | Create or update the submission's canonical grading record |
| `GET` | `/api/sessions/:id/submissions/:submissionId/evidence-suggestions` | Load validated AI evidence highlights |
| `POST` | `/api/sessions/:id/submissions/:submissionId/evidence-suggestions` | Generate evidence highlights with Bedrock Claude Sonnet 4.6 |
| `POST` | `/api/canvas/connection` | Validate and hold a Canvas URL and personal access token |
| `GET` | `/api/canvas/courses` | List active courses visible to the connected Canvas user |
| `GET` | `/api/canvas/courses/:courseId/assignments` | List course assignments and rubric availability |
| `POST` | `/api/sessions/:id/canvas/import` | Import the selected assignment rubric into a session |
| `POST` | `/api/sessions/:id/canvas/submissions/import` | Import the selected assignment roster and submission batch |

State, including imported PDF bytes and normalized text, is intentionally in memory and
resets when the process restarts. Canvas text entries and PDF uploads up to 25 MiB are
supported. PDFs with embedded text are extracted in process; image-only, encrypted, and
malformed PDFs receive actionable per-submission errors without aborting the whole batch.
Evidence suggestions use AWS Bedrock through the SDK's default credential chain. Set
`AWS_REGION` and optionally `BEDROCK_MODEL_ID`; the default model is
`global.anthropic.claude-sonnet-4-6`. Model output is validated against exact normalized
submission text and never writes or recommends a score.

Persistent storage, broader file ingestion, and grade publication can be added behind
the same monolith interfaces as later vertical slices.
The current application is intentionally single-user and has no login.
The Canvas token is held only in process memory and must be entered again after restart.
