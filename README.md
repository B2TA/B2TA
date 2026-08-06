# B2TA — Back to TA

**A standalone, AI-assisted grading workspace for teaching assistants.** B2TA reads a
student submission, cross-references it against the assignment rubric, and shows the TA
the exact passages relevant to each criterion—so grading becomes verifying evidence
instead of reconstructing it from a blank page.

Built for the UBC CIC Summer 2026 Hackathon (theme: *Student Success Tools*).

**Live demo:** https://main.dpezcexvnbo0g.amplifyapp.com

## Product boundary

B2TA owns the grading experience. A TA creates or resumes a grading session, reviews a
rubric beside each submission and AI-highlighted passages, selects scores, writes
feedback, reviews the batch, and publishes or exports the completed results.

Learning management systems connect through adapters:

- **Canvas is the first supported LMS.** The Canvas adapter imports the rubric, roster,
  and submissions and publishes TA-approved grades and feedback back to Canvas. The TA
  stays in B2TA throughout the grading workflow.
- **Manual import remains supported.** A TA can upload a rubric and submission batch and
  export results without connecting an LMS.
- **Other LMSs are a future extension.** The grading core uses B2TA's canonical models;
  LMS-specific credentials, identifiers, and payloads stay inside each adapter.

This is similar in product shape to Gradescope: B2TA is its own web application, while
LMS connectivity removes the need to download submissions and re-enter grades manually.

The boundary and vocabulary are recorded in [CONTEXT.md](./CONTEXT.md) and
[ADR 0001](./docs/adr/0001-standalone-product-with-lms-adapters.md).

## The problem

A TA marking 40 essays against a five-criterion rubric repeatedly hunts through the same
document for the passage that settles each criterion. That mechanical work slows feedback
and makes consistent grading harder across a long batch.

## What B2TA does

For each rubric criterion, B2TA highlights potentially relevant passages and explains why
they may apply. The TA may use or ignore those reading aids, then explicitly selects a
performance level or enters a score. Before anything leaves B2TA, a review screen
summarizes the full batch and flags incomplete or exceptional records.

Students receive faster feedback, more consistent application of the rubric, and comments
anchored to evidence from their own work.

### Product commitments

1. **The AI never grades.** It highlights possible evidence; the TA assigns every score
   and writes the feedback.
2. **Evidence must be traceable.** A suggested passage must resolve to text in the stored
   submission before B2TA displays it.
3. **Publication is deliberate.** No grade is exported or published to an LMS until the
   TA completes review and explicitly initiates the action.
4. **The grading core is LMS-neutral.** Canvas-specific concepts do not define B2TA's
   internal grading model.

## Architecture

The application has two runtime pieces: the React SPA and one Express/TypeScript backend
process. The frontend calls `/api`; during local development Vite proxies those requests
to the backend. All grading features, file processing, AI calls, and LMS adapters belong
inside that backend monolith as ordinary modules.

The backend uses an in-memory store so the frontend has a real HTTP contract without
committing to production infrastructure too early. State resets whenever the backend
restarts. Durable persistence and production file storage are not implemented yet.

The current product scope is a trusted, single-user application. B2TA does not require a
login or multi-user authorization for this phase. If it becomes a shared deployment,
identity and per-session ownership enforcement must be added before other users receive
access.

| Layer | Technology |
|---|---|
| Frontend | React 19, Vite 8, Tailwind CSS v4 — Amplify Hosting |
| Backend | Express 5 and TypeScript — one monolithic process |
| Current state | In-memory store for sessions, rubrics, submissions, grading records, PDF artifacts, and evidence suggestions |
| AI runtime | AWS Bedrock with Claude Sonnet 4.6, called from the backend monolith |
| Planned modules | Durable persistence and grade publication |
| LMS integration | Provider-neutral adapter boundary; Canvas first |

See [ARCHITECTURE.md](./ARCHITECTURE.md) for the system and integration boundaries.

## Current state

| Area | State |
|---|---|
| Session dashboard | Active routed frontend with real API-backed list, create, resume, and delete flows |
| Marking workspace | Real PDF-and-rubric grading with TA-authored scores and feedback |
| Backend monolith | Express API with sessions, rubrics, Canvas submission batches, PDF delivery, and grading records |
| Backend persistence | Not implemented; current state is local and in memory |
| Canvas API feasibility | Verified against the hackathon Canvas instance |
| Canvas connection | Personal access token validation, course/assignment selection, and rubric import implemented |
| Canvas submissions | Roster, attempts, text entries, missing work, PDF display, and embedded-text extraction implemented |
| Canvas grade publication | Not implemented |
| AI evidence suggestions | Bedrock Claude Sonnet 4.6 highlights validated rubric-linked passages; the AI never assigns scores |
| End-to-end production workflow | In progress |

`src/main.tsx` mounts the routed standalone application. The marking route is active;
batch review remains a placeholder until its full-stack slice is implemented.

## Running locally

The Vite development server is normally already running in Figma Make. For a fresh local
checkout, use the toolchain pinned by mise:

```bash
mise trust && mise install
mise exec -- pnpm install
mise exec -- pnpm dev
```

Plain `pnpm` may use a different global Node version and rewrite the lockfile, so prefer
`mise exec -- pnpm`.

Run the backend in a second terminal:

```bash
pnpm --dir backend install
pnpm run dev:api
```

The SPA runs on port `8443` by default, the API runs on port `3001`, and Vite proxies
`/api` to the API process. Use `pnpm run build:api` and `pnpm run test:api` to validate
the backend.

### Deploying the frontend

```bash
./scripts/deploy.sh
```

### Preparing the hackathon Canvas instance

`scripts/seed_canvas.py` creates the demo rubric used to exercise the Canvas adapter. It
is development tooling, not part of B2TA's product boundary.

```bash
export CANVAS_URL=https://canvas.cic.wtarit.me
export CANVAS_TOKEN=...        # TA token; needs manage_rubrics
python3 scripts/seed_canvas.py --dry-run
python3 scripts/seed_canvas.py --course 1 --assignment 1
```

## Repository layout

```text
src/App.tsx                                  High-fidelity marking prototype
src/app/                                     Production standalone SPA shell
backend/                                     Express/TypeScript monolith
fixtures/                                    Generated development fixtures
scripts/seed_canvas.py                       Canvas adapter development helper
CONTEXT.md                                   Product language and boundaries
docs/adr/                                    Architectural decisions
ARCHITECTURE.md                              System architecture
```

## Security notes

- The current deployment is for one trusted operator and intentionally has no login.
- The Canvas personal access token is sent in an authorization header, held only in the
  backend process, never returned to the browser, and forgotten when the backend stops.
- Manual Canvas tokens are limited to this trusted single-user phase. A multi-user version
  must use Canvas OAuth instead of asking users to paste tokens.
- LMS credentials must remain backend-only and never be exposed to the browser.
- Student submissions, names, feedback, and access tokens must not be logged at INFO.
- Student work must not be committed. Fixtures contain generated structures without PII.
- Before any multi-user deployment, add authentication and enforce ownership of every
  grading session and associated file.

## License

MIT — see [LICENSE](./LICENSE).
