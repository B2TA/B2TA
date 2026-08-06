# Canvas Integration — Design

## Overview

Three Lambdas behind one HTTP API. The frontend never talks to Canvas or Bedrock; it
talks only to our API, which holds the Canvas token and the Bedrock permissions.

```
React (Amplify)
      │  GET  /assignments/{courseId}/{assignmentId}      → canvas-adapter
      │  GET  /submissions/{courseId}/{assignmentId}      → canvas-adapter
      │  GET  /submissions/{...}/{userId}                 → canvas-adapter
      │  POST /analyze                                    → analyze
      │  POST /sync                                       → canvas-adapter
      ▼
API Gateway (HTTP API)
      ├── canvas-adapter ──► Secrets Manager ──► Canvas REST (canvas.cic.wtarit.me)
      │                  └─► S3 (fixture mode)
      ├── analyze ──► Bedrock (Claude Sonnet 4.5) ──► DynamoDB (cache)
      └── batch-worker ◄── SQS
```

**Model id:** `us.anthropic.claude-sonnet-4-5-20250929-v1:0`.
The `us.` inference-profile prefix is required — the bare `anthropic.*` ids returned by
`list-foundation-models` fail at invoke time. Opus 5 and Sonnet 5 are **not** granted on
the workshop account (verified 2026-08-06); do not reach for them.

---

## Canvas API surface

Base: `https://canvas.cic.wtarit.me/api/v1`
Auth: `Authorization: Bearer <token>` on every request.

| Purpose | Call |
|---|---|
| Assignment + rubric | `GET /courses/{course}/assignments/{id}` |
| Submissions | `GET /courses/{course}/assignments/{id}/submissions?include[]=user&include[]=rubric_assessment&include[]=submission_comments&per_page=100` |
| One submission | `GET /courses/{course}/assignments/{id}/submissions/{user_id}?include[]=user&include[]=rubric_assessment` |
| Attachment bytes | `GET` on `submission.attachments[].url` (pre-authorized; send no bearer token) |
| Write grade | `PUT /courses/{course}/assignments/{id}/submissions/{user_id}` |

### Pagination

Canvas paginates via RFC 5988 `Link` headers, **not** a body field. Follow `rel="next"`
until absent. A 24-student course fits in one page at `per_page=100`, but a real course
will not — implement the loop once, in `canvas_client.paginate()`.

### Write-back body

```
PUT /courses/1/assignments/{id}/submissions/{user_id}
{
  "rubric_assessment": {
    "_1234": { "points": 4, "rating_id": "blank", "comments": "" },
    "_5678": { "points": 5 }
  },
  "comment": { "text_comment": "Overall feedback…" }
}
```

Criterion keys are Canvas's own ids (`_1234`), which is why Requirement 1.5 insists on
preserving them verbatim. Sending our internal slugs (`thesis`, `evidence`) silently
no-ops — Canvas accepts the request and records nothing.

---

## Field mapping: Canvas → existing UI shapes

`src/App.tsx` already defines the target shapes. The adapter's job is to produce these
exactly, so the UI needs no restructuring.

### Criterion

| UI field (`CRITERIA[]`) | Canvas source |
|---|---|
| `id` | `rubric[i].id` (e.g. `_1234`) — **keep verbatim** |
| `label` | `rubric[i].description` |
| `description` | `rubric[i].long_description` (fall back to `description`) |
| `maxPts` | `rubric[i].points` |
| `levels[]` | `rubric[i].ratings[]` sorted by `points` desc |
| `levels[].pts` / `.label` / `.desc` | `ratings[j].points` / `.description` / `.long_description` |
| `color`, `bg`, `border` | assigned from a fixed 8-color palette by criterion index |

The palette must be indexed, not hashed — a hash over changing ids reshuffles colors
between reloads and breaks the color-coding mechanic that the whole design rests on.

### Submission text

`ESSAY_PARAGRAPHS` entries are `{ idx, label, text, isTitle }`. Paragraph 0 is the title;
body paragraphs are labelled `¶1`, `¶2`, … Build by splitting normalized text on blank
lines.

### Evidence span

`HSpan` is `{ id, criterionId, text, confirmed, tooltip, paragraphIdx, offsetInParagraph }`.

**Offsets are paragraph-relative, not document-global.** The analyze Lambda reasons over
the whole document, so it must convert: locate the quote in the full normalized text, map
the absolute offset back to `(paragraphIdx, offsetInParagraph)` using the paragraph start
table built during splitting. Getting this wrong shifts every highlight in the essay.

`confirmed` is always `false` on arrival from the AI (Requirement 4.5).

---

## Analyze contract

Bedrock is called through the Converse API with a forced tool schema, so the model must
return structured output rather than prose.

```json
{
  "criteria": [{
    "criterion_id": "_1234",
    "suggested_points": 4,
    "confidence": 0.82,
    "rationale": "One sentence for the TA.",
    "flag": "none | missing | possible_misconception",
    "evidence": [{ "quote": "verbatim span from the submission" }]
  }],
  "overall_note": "Draft feedback comment for the student."
}
```

The model returns **quotes only** — no offsets. It cannot count characters reliably, so
asking it to try invites silent misalignment.

### Evidence verification (the load-bearing step)

```python
def locate(quote: str, doc: str) -> tuple[int, int] | None:
    """Find quote in doc, tolerating whitespace re-wrapping. None => hallucinated."""
    if not quote.strip():
        return None
    pattern = re.compile(r"\s+".join(map(re.escape, quote.split())))
    m = pattern.search(doc)
    return m.span() if m else None
```

Any span that fails to locate is dropped before the response leaves the Lambda, and the
drop is counted. A criterion whose evidence is entirely dropped renders the
"No matching passage found" empty state rather than an unsupported score.

This is the single guardrail worth naming out loud in the presentation: the system cannot
show the TA a quotation the student did not write.

---

## Data model

**DynamoDB** — one table, `b2ta-analyses`:

- `PK = COURSE#{courseId}#ASSIGN#{assignmentId}`
- `SK = USER#{userId}#ATTEMPT#{attempt}`
- Attributes: `analysis` (verified JSON), `docText`, `paragraphs`, `taOverrides`,
  `syncedAt`, `syncedBy`, `modelId`, `ttl`

Keying on `attempt` means a resubmission produces a fresh analysis instead of serving a
stale one for work that has changed.

**S3** — `b2ta-artifacts`: downloaded attachments under
`raw/{courseId}/{assignmentId}/{userId}/{attempt}/`, fixtures under `fixtures/`.

**Secrets Manager** — `b2ta/canvas` → `{ "baseUrl": "...", "token": "..." }`.

---

## Fixture mode

`canvas_client` is an interface with two implementations selected by `DATA_SOURCE`
(`canvas` | `fixtures`). Fixtures are real Canvas response bodies captured from
`canvas.cic.wtarit.me` and committed under `fixtures/`, so both paths exercise identical
parsing code — a fixture that drifts from the real payload shape is worse than no fixture.

Demo runs on `fixtures` unless the live instance is confirmed healthy at rehearsal.

---

## Error handling

| Condition | Behavior |
|---|---|
| Canvas 401 | Fail fast, log "check Secrets Manager token", surface as config error |
| Canvas 404 | Assignment/course misconfigured — explicit message, no fallback |
| Assignment has no rubric | Requirement 1.3 empty state |
| Bedrock throttle | Retry twice with exponential backoff, then serve cache-or-degrade |
| All evidence dropped | Render scores with the empty-evidence state, log the drop rate |
| Sync failure | Preserve TA input, show Canvas's error message, allow retry |

Never silently substitute demo data for failed live data — a demo that appears to work
while disconnected is the worst outcome on stage.
