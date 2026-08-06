# B2TA — Back to TA

**AI-assisted rubric grading for Canvas LMS.** B2TA reads a student's submission,
cross-references it against the assignment rubric, and shows the TA the exact passages
that justify each criterion — so grading becomes verifying a judgment instead of
reconstructing one from a blank page.

Built for the UBC CIC Summer 2026 Hackathon (theme: *Student Success Tools*).

**Live demo:** https://main.dpezcexvnbo0g.amplifyapp.com

---

## The problem

A TA marking 40 essays against a 5-criterion rubric re-reads the same document five
times, once per criterion, hunting for the passage that settles each one. That work is
mechanical, and it is where grading time actually goes.

The cost lands on students. Feedback comes back slowly, and it comes back inconsistently —
the essay marked at 9 AM and the one marked at 11 PM are not held to the same standard,
and neither student can tell which passage of their own writing earned the score.

## What B2TA does

For each rubric criterion, B2TA proposes a rating and highlights the specific sentences
in the submission that support it, colour-coded to match the rubric sidebar. The TA
confirms, edits, or overrides — then syncs to the Canvas gradebook in one click.

**Students get** faster turnaround, consistent standards across TAs, and feedback
anchored to quoted evidence from their own work.

### Two design commitments

1. **The AI never grades.** It proposes; the TA disposes. No score reaches Canvas
   without an explicit TA selection.
2. **Every highlight is verified.** Quotes returned by the model are checked against the
   submission text server-side, and any passage that does not appear verbatim is
   discarded before it ever reaches the screen. The tool cannot show a TA a quotation the
   student did not write.

---

## Architecture

React on Amplify → API Gateway → Lambda → Bedrock, with Canvas behind the backend so the
API token never reaches the browser. Full detail and diagram in
**[ARCHITECTURE.md](./ARCHITECTURE.md)**.

| | |
|---|---|
| Frontend | React 19, Vite 8, Tailwind v4 — Amplify Hosting |
| API | API Gateway (HTTP API) |
| Compute | Lambda: `canvas-adapter`, `analyze`, `batch-worker` |
| AI | Amazon Bedrock — Claude Sonnet 4.5 |
| Data | DynamoDB (analysis cache, TA overrides), S3 (artifacts), SQS, Secrets Manager |
| LMS | Canvas LMS REST API |

---

## Status

| Component | State |
|---|---|
| Grading UI | ✅ Built, deployed to Amplify |
| Canvas connection | ✅ Verified against live instance |
| Rubric seeded in Canvas | ✅ 5 criteria, 20 pts (`scripts/seed_canvas.py`) |
| PDF/DOCX text extraction | ✅ Verified with `pypdf` |
| Bedrock access | ✅ Claude Sonnet 4.5 confirmed |
| `analyze` Lambda | 🚧 In progress |
| Canvas grade write-back | 🚧 In progress |
| UI wired to live data | 🚧 Currently renders from fixtures in `src/App.tsx` |

---

## Running locally

Requires [mise](https://mise.jdx.dev/) — the toolchain is pinned (Node 22, pnpm 10.34.3).

```bash
mise trust && mise install
mise exec -- pnpm install
mise exec -- pnpm dev          # http://localhost:8443
```

Plain `pnpm` will use your global Node and may rewrite the lockfile — prefer
`mise exec -- pnpm`.

### Deploying

```bash
./scripts/deploy.sh            # build, upload to Amplify, poll to completion
```

### Seeding Canvas

```bash
export CANVAS_URL=https://canvas.cic.wtarit.me
export CANVAS_TOKEN=...        # TA token; needs manage_rubrics
python3 scripts/seed_canvas.py --dry-run
python3 scripts/seed_canvas.py --course 1 --assignment 1
```

---

## Repository layout

```
src/App.tsx                        Grading UI (SpeedGrader overlay)
scripts/deploy.sh                  Build + deploy to Amplify
scripts/seed_canvas.py             Create the essay rubric in Canvas
fixtures/                          Canvas API responses for offline demo
.kiro/specs/canvas-integration/    Spec-driven plan: requirements, design, tasks
ARCHITECTURE.md                    System design and diagram
```

Built spec-first with [Kiro](https://kiro.dev/): requirements → design → tasks live in
`.kiro/specs/` and were written before the integration code.

## Security notes

- The Canvas API token lives in AWS Secrets Manager and is never exposed to the browser.
- Student submissions are not committed to this repository. `fixtures/` contains only
  generated rubric structure — no personally identifiable information.

## License

MIT — see [LICENSE](./LICENSE).
