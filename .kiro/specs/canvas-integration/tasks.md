# Canvas Integration — Tasks

Ordered so that something demoable exists after every task. Each task names the
requirements it satisfies.

---

- [ ] **1. Capture real Canvas payloads as fixtures**
  - Call the five endpoints in design.md against `canvas.cic.wtarit.me` course `1` with a
    TA token and save raw JSON to `fixtures/`.
  - Confirm the assignment actually has a rubric attached; if not, attach one in Canvas
    before going further — everything downstream depends on it.
  - Record the real criterion id format observed (`_1234` vs something else).
  - _Requirements: 1.1, 6.5_
  - _Blocker: needs a Canvas API token._

- [ ] **2. Canvas client module**
  - `baseUrl` + bearer auth from Secrets Manager, `paginate()` following `Link` rel=next.
  - Two implementations behind one interface, selected by `DATA_SOURCE`.
  - Unit-test `paginate()` against a two-page `Link` header fixture.
  - _Requirements: 2.1, 6.1, 6.2, 6.5_

- [ ] **3. Rubric + roster adapter**
  - Map assignment `rubric[]` → `CRITERIA[]` shape, preserving Canvas ids verbatim.
  - Sort ratings by points desc; assign palette colors by index.
  - Filter `unsubmitted` from the queue; expose ordered roster with names.
  - _Requirements: 1.1–1.5, 2.1–2.5_

- [ ] **4. Text extraction**
  - `online_text_entry` → strip HTML. `online_upload` → dispatch by content type
    (PDF `pypdf`, DOCX `python-docx`, `.ipynb` cell sources, plain text).
  - Normalize once; return `{ docText, paragraphs, paragraphStarts }`.
  - Textract fallback below 200 chars.
  - Unit-test that `paragraphStarts` round-trips: absolute offset → (para, offset) → same
    substring.
  - _Requirements: 3.1–3.5_

- [ ] **5. Analyze Lambda**
  - Bedrock Converse against `us.anthropic.claude-sonnet-4-5-20250929-v1:0` with the
    forced tool schema from design.md.
  - Verify every quote with `locate()`; drop failures and count them.
  - Convert absolute spans → `(paragraphIdx, offsetInParagraph)`; set `confirmed: false`.
  - Cache to DynamoDB keyed by attempt; serve cache on repeat.
  - _Requirements: 4.1–4.7_

- [ ] **6. HTTP API wiring**
  - API Gateway routes from design.md, CORS for the Amplify origin, IAM for Bedrock,
    DynamoDB, S3, Secrets Manager.
  - _Requirements: 6.1, 6.2_

- [ ] **7. Frontend data layer**
  - Replace the four hardcoded constants in `src/App.tsx` with fetched state; keep the
    existing shapes so rendering is untouched.
  - Loading, empty-rubric, and extraction-failure states.
  - Prev/next student without full reload.
  - _Requirements: 1.3, 2.3, 3.5, 4.6_

- [ ] **8. Sync to Canvas**
  - `POST /sync` → Canvas `PUT` with `rubric_assessment` keyed by Canvas criterion ids
    plus `comment.text_comment`.
  - Block on unscored criteria; preserve input and surface Canvas errors on failure;
    record `syncedAt`/`syncedBy`.
  - _Requirements: 5.1–5.6_

- [ ] **9. End-to-end verification**
  - Grade one real submission through the UI, then confirm the score appears in the
    Canvas gradebook for that student.
  - Assert every rendered highlight's text equals the substring at its stated offset.
  - Confirm a hallucinated quote is dropped (inject one in a test).
  - _Requirements: 4.2, 4.3, 5.4_

---

## Stretch — only if 1–9 are done

- [ ] **10. Batch pre-analysis** — SQS fan-out so the whole queue is warm before the TA opens it. _(4.7)_
- [ ] **11. Cognito auth** on the API instead of an open endpoint. _(6.2)_
- [ ] **12. Bedrock Knowledge Base** over past graded exemplars to match course grading norms.

---

## Open questions

1. **Canvas API token** — needed for task 1 and everything after. A TA-or-higher token
   from `canvas.cic.wtarit.me/profile/settings`. Nothing real can be built without it.
2. **Does course 1's assignment have a rubric attached?** If not, the core mechanic has
   no data. Verify first.
3. **Runtime — Python or Node?** Design assumes Python 3.12 (`pypdf`, `python-docx` are
   the cleaner libraries). Switch to Node + `unpdf` if the team prefers one language.
4. **Real student PII?** If the instance holds real submissions, hackathon rules forbid
   PII in any committed dataset — fixtures must be anonymized before commit.
