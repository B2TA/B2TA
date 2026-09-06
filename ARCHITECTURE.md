# B2TA — Architecture

B2TA is a standalone grading web application with optional LMS integrations. It owns the
grading experience; Canvas is the first external system connected to it, not the host of
the product.

## Current system

```mermaid
flowchart LR
    TA[TA browser] --> SPA[React SPA]
    SPA -->|JSON over /api| API[Express and TypeScript monolith]
    API --> DB[PostgreSQL metadata]
    SPA -->|signed PUT| OBJECTS[S3 documents]
    API --> OBJECTS

    API --> CANVAS[Canvas adapter]
    API --> AI[AWS Bedrock Claude Sonnet 4.6]
    API --> FILES[Durable PDF ingestion jobs]
```

The current Express process is a local development host. Canonical metadata, grading
records, review state, external links, publication outcomes, and ingestion jobs live in
PostgreSQL. PDF bytes live in object storage. The next runtime milestone replaces the
Express-specific HTTP edge with a Fetch-shaped application and thin Lambda and Worker
hosts; it does not change these persistence boundaries.

The current deployment model has one trusted operator. It has no login or multi-user
authorization boundary. Authentication becomes required only if B2TA is opened to
multiple users.

## Runtime boundary

- The browser communicates only with B2TA's `/api` interface.
- LMS credentials and provider calls belong in the backend, never in the browser.
- The backend translates provider data into B2TA's canonical domain objects.
- A reviewed grade is published only after an explicit TA action.
- AWS resources are provisioned manually when needed; this repository does not define
  infrastructure templates.

## Product workflow

A grading session can begin through either manual import or an LMS adapter. Both paths
produce the same canonical rubric, submission batch, and student identity records.

```mermaid
flowchart LR
    MANUAL[Upload rubric and submissions]
    LMSIMPORT[Import through an LMS adapter]
    SESSION[B2TA grading session]
    ANALYZE[Extract and suggest evidence]
    GRADE[TA reviews suggestions and assigns scores]
    REVIEW[TA reviews the batch]
    EXPORT[Download export]
    PUBLISH[Publish through source LMS adapter]

    MANUAL --> SESSION
    LMSIMPORT --> SESSION
    SESSION --> ANALYZE --> GRADE --> REVIEW
    REVIEW --> EXPORT
    REVIEW --> PUBLISH
```

Import source does not change the marking experience. A manually created session can be
exported. A session linked to an LMS can also publish results when it retains the external
course, assignment, student, criterion, and submission references required by that LMS.

## Backend modules

The backend stays monolithic while keeping clear internal ownership:

| Module | Responsibility | State |
|---|---|---|
| HTTP API | Request validation, response formatting, and frontend contract | Initial routes implemented |
| Grading core | Sessions, rubrics, submissions, evidence, scores, feedback, and review | Sessions, rubrics, and Canvas submission batches started |
| Persistence | Store and retrieve canonical state | PostgreSQL through Drizzle and `pg`; committed migrations |
| Object storage | Issue signed URLs and retrieve/delete documents | S3 implementation behind `ObjectStore` |
| File ingestion | Accept files and normalize their text | Persisted per-batch job state; PDF extraction records per-file outcomes |
| AI assistance | Suggest evidence without assigning scores | Bedrock Claude Sonnet 4.6 integration implemented |
| LMS adapters | Import external data and publish reviewed results | Canvas PAT, rubric, roster, attempt, text-entry, and PDF import implemented |

An internal module boundary is not a deployment boundary. New capabilities should remain
in the Express application unless operational evidence creates a concrete need to split
them later.

## Grading core

The core uses B2TA-owned concepts rather than Canvas payloads:

- a **Grading Session** contains one Rubric and one Submission Batch;
- a **Rubric** contains ordered Criteria and Performance Levels;
- a **Submission** contains normalized text and Student Identity metadata;
- a **Suggested Match** relates one Criterion to a real passage with rationale and
  confidence;
- a **Grading Record** contains TA-selected scores and feedback;
- a **Review Confirmation** gates export or LMS publication.

Provider identifiers are external references alongside canonical entities. They are not
the primary identity of a B2TA domain object.

## LMS adapter contract

Each LMS adapter translates the same provider-neutral operations:

| Operation | Canonical result |
|---|---|
| List accessible courses and assignments | External references and display metadata |
| Import rubric | Rubric, Criteria, Performance Levels, and provider links |
| Import roster and submissions | Student Identities, Submission Batch, artifacts, and provider links |
| Refresh source state | New attempts or metadata without overwriting TA work |
| Publish reviewed results | Per-student scores and feedback from saved Grading Records |

Canvas criterion IDs, pagination links, attachment verifiers, and grade-publication
payloads belong only to the Canvas adapter.

## Safety invariants

- AI output can highlight possible evidence but cannot author or recommend a score.
- Suggested passages must resolve to normalized submission text before display.
- Missing evidence never prevents manual grading.
- Results leave B2TA only after TA review and an explicit export or publish action.
- Sensitive student content, access tokens, and LMS credentials do not belong in normal
  application logs.
- The single-user scope is not a safe multi-user deployment; authentication and
  per-session authorization must precede any expansion to multiple users.

## Decisions

| Decision | Rationale |
|---|---|
| Standalone product with LMS adapters | B2TA owns a consistent grading workflow while supporting Canvas first and other LMSs later. |
| Express as the temporary local host | It keeps existing development working while the Fetch-shaped host is implemented separately. |
| PostgreSQL plus object storage | Metadata and restart-safe work are durable; large PDFs bypass serverless request limits. |
| Drizzle without a broad repository abstraction | Typed schema/SQL with one production persistence behavior and no `MemoryStore`. |
| Canonical models plus external links | Provider payloads can evolve without leaking through the grading core. |
| TA-authored scores only | AI assistance speeds evidence review without transferring grading authority. |
| Review before export or publication | The TA has an explicit checkpoint before results leave B2TA. |

See [ADR 0001](./docs/adr/0001-standalone-product-with-lms-adapters.md) for the product
boundary decision. See [ADR 0002](./docs/adr/0002-durable-portable-serverless-core.md)
for the durable serverless direction and [the implementation TODO](./docs/implementation-todo.md)
for the ordered work.
