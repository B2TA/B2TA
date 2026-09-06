---
status: accepted
---

# Build a durable portable core before adding serverless hosts

## Context

B2TA must run as a standalone grading application without Canvas and must be deployable
through both AWS Lambda and Cloudflare Workers. The current backend stores metadata,
Canvas credentials, and PDF bytes in one process. It also performs a whole Canvas import
inside one HTTP request. Those assumptions are incompatible with disposable serverless
invocations and prevent independent testing when no Canvas instance is available.

A Fetch `Request`/`Response` interface makes HTTP routing portable, but it does not make
persistence, file storage, jobs, credentials, or AI providers portable.

## Decision

Implement a durable, manual-import grading slice before changing the HTTP framework or
adding cloud-specific entry points.

- Store canonical metadata in PostgreSQL: grading sessions, rubrics, criteria,
  submissions, extraction state, grading records, review confirmations, external LMS
  references, jobs, and publication outcomes.
- Store original PDFs and derived binary artifacts in object storage, referenced by
  opaque object keys from PostgreSQL. Do not store PDF bytes in PostgreSQL.
- Replace `MemoryStore` with a concrete asynchronous PostgreSQL persistence module shaped
  around grading operations and transactional invariants rather than generic table CRUD.
  Do not maintain an in-memory production or test implementation.
- Use Drizzle as a narrow typed SQL and schema layer with the `pg` driver. Use Drizzle
  Kit to generate reviewable SQL migrations and commit those migrations. Do not use
  Drizzle relational queries as a second domain model; canonical grading types remain
  owned by the grading core.
- Run repository integration tests against a disposable PostgreSQL database. Unit tests
  for pure grading logic do not require a repository fake.
- Maintain one standard PostgreSQL schema and migration history for all deployments.
- Put files, resumable work, credentials, LMS integrations, and AI behind separate,
  narrow interfaces: `ObjectStore`, `JobDispatcher`, `CredentialVault`, `LMSAdapter`,
  and `EvidenceSuggester`.
- Upload manually supplied PDFs directly from the browser to object storage using
  short-lived signed targets. Submission ingestion records durable per-file outcomes and
  does not depend on a browser request remaining open.
- Make manual import the first complete path. It must create the same canonical grading
  records that a future Canvas or PrairieLearn adapter creates.
- After the standalone slice is durable, expose the application through a Fetch-shaped
  HTTP interface. Cloudflare Workers call it directly; AWS Lambda uses a thin event
  adapter.

The application core must not receive AWS event objects, Cloudflare bindings, database
clients, S3/R2 clients, or provider-specific rubric and submission payloads.

AWS Lambda connects with `pg` through an RDS Proxy endpoint. Cloudflare Workers use the
same driver and persistence implementation with a per-request client created from the
Hyperdrive connection string. Workers compatibility dates from 2026-08-04 enable Node.js
compatibility by default, so the Cloudflare composition uses a current compatibility
date and Wrangler version rather than adding a redundant `nodejs_compat` flag. This
runtime compatibility stays an implementation detail and does not change the
Fetch-shaped application interface.

Schema migrations run as an explicit deployment step, never during a Lambda cold start
or Worker request.

## Initial independent test path

The first production-shaped workflow requires no Canvas or PrairieLearn access:

1. A TA creates a grading session.
2. The TA imports a Canvas-compatible rubric CSV from disk or enters the same
   structured rubric manually.
3. The TA uploads one or more machine-readable PDFs.
4. B2TA processes each submission through a restart-safe job and reports per-file status.
5. The TA grades the ready submissions, confirms review, and exports results.

LMS import and publication remain optional adapters and must not be required to exercise
the grading workflow locally, in CI, or in either cloud deployment.

Canvas-compatible CSV is the initial rubric file interchange format because Canvas can
export it, B2TA can parse it without Canvas credentials, and it maps to B2TA's current
criterion-and-performance-level model. PrairieLearn rubric import is deferred because
PrairieLearn does not document a portable rubric export and its additive rubric items do
not map losslessly to that model.

## Consequences

- PostgreSQL is part of the production architecture, while object storage owns binary
  artifacts.
- AWS and Cloudflare deployments use different infrastructure adapters while sharing the
  grading core, schema, migrations, and application tests.
- The old `MemoryStore` is deleted after PostgreSQL reaches feature parity; tests use real
  PostgreSQL instead of maintaining two persistence behaviours.
- Canvas integration remains supported, but its credential state, pagination,
  throttling, and publication outcomes must become durable before it is serverless-safe.
- PDF extraction and AI work can move between queue/compute implementations without
  changing the grading workflow.
- The Express server may remain as a temporary local host while the durable standalone
  slice is built.
