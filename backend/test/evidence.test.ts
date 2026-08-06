import assert from "node:assert/strict"
import { after, before, test } from "node:test"
import type { AddressInfo } from "node:net"

import { createApp } from "../src/app.js"
import {
  BedrockEvidenceSuggester,
  type EvidenceSuggester,
} from "../src/evidence.js"
import { MemoryStore } from "../src/store.js"

const store = new MemoryStore()
const model: EvidenceSuggester = {
  async suggest({ rubric, submissionText }) {
    assert.equal(submissionText, "A clear thesis. Evidence follows.")
    return [
      {
        criterionId: rubric.criteria[0].id,
        passageStart: 2,
        passageEnd: 14,
        passageText: "clear thesis",
        rationale: "This passage states the central claim.",
        confidence: 0.91,
      },
      {
        criterionId: rubric.criteria[0].id,
        passageStart: 0,
        passageEnd: 999,
        passageText: "invented text",
        rationale: "This candidate is invalid.",
        confidence: 0.99,
      },
    ]
  },
}
const app = createApp(store, undefined, model)
const server = app.listen(0, "127.0.0.1")
let baseUrl = ""
let endpoint = ""

before(async () => {
  await new Promise<void>((resolve) => server.once("listening", resolve))
  baseUrl = `http://127.0.0.1:${(server.address() as AddressInfo).port}`
  const session = store.createSession("WRDS 150 Essay")
  const rubric = store.saveRubric(session.id, {
    criteria: [{ title: "Thesis clarity", maxPoints: 5 }],
  })
  const [submission] = store.saveSubmissionBatch(session.id, [
    {
      originalFilename: "alex.pdf",
      studentDisplayName: "Alex Able",
      externalStudentId: "12",
      externalSubmissionId: "812",
      identityStatus: "verified",
      importStatus: "ready",
      submissionType: "pdf",
      attemptCount: 1,
      submittedAt: "2026-08-06T18:00:00.000Z",
      extractionStatus: "success",
      extractionFailureReason: null,
      extractedText: "A clear thesis. Evidence follows.",
      extractedCharCount: 33,
      isOversized: false,
      artifact: null,
    },
  ])
  endpoint = `${baseUrl}/api/sessions/${session.id}/submissions/${submission.id}/evidence-suggestions`
  assert.ok(rubric.criteria[0].id)
})

after(async () => {
  await new Promise<void>((resolve, reject) =>
    server.close((error) => (error ? reject(error) : resolve())),
  )
})

test("AI suggestions are validated and never author a score", async () => {
  const generated = await fetch(endpoint, { method: "POST" })
  assert.equal(generated.status, 200)
  const suggestions = (await generated.json()) as Array<{
    id: string
    passageStart: number
    passageEnd: number
    rationale: string
    confidence: number
  }>
  assert.equal(suggestions.length, 1)
  assert.deepEqual(
    {
      passageStart: suggestions[0].passageStart,
      passageEnd: suggestions[0].passageEnd,
      rationale: suggestions[0].rationale,
      confidence: suggestions[0].confidence,
    },
    {
      passageStart: 2,
      passageEnd: 14,
      rationale: "This passage states the central claim.",
      confidence: 0.91,
    },
  )

  const reloaded = await fetch(endpoint)
  assert.equal(reloaded.status, 200)
  assert.deepEqual(await reloaded.json(), suggestions)
  assert.ok(!("score" in suggestions[0]))
})

test("Bedrock requests evidence-only JSON from Claude Sonnet 4.6", async () => {
  const suggester = new BedrockEvidenceSuggester(
    {
      async send(command) {
        assert.equal(
          command.input.modelId,
          "global.anthropic.claude-sonnet-4-6",
        )
        const systemPrompt = command.input.system?.[0]?.text ?? ""
        const userPrompt = command.input.messages?.[0]?.content?.[0]?.text ?? ""
        assert.match(
          systemPrompt,
          /Never assign, recommend, infer, or discuss scores/i,
        )
        assert.match(userPrompt, /Do not include grades, scores/i)
        return {
          $metadata: {},
          output: {
            message: {
              role: "assistant",
              content: [
                {
                  text: JSON.stringify({
                    suggestions: [
                      {
                        criterionId: "criterion-1",
                        passageText: "clear thesis",
                        rationale: "Likely thesis statement.",
                        confidence: 0.91,
                      },
                    ],
                  }),
                },
              ],
            },
          },
        }
      },
    },
    "global.anthropic.claude-sonnet-4-6",
  )

  const candidates = await suggester.suggest({
    rubric: {
      id: "rubric-1",
      sessionId: "session-1",
      storageKey: null,
      sourceFormat: "canvas",
      criteria: [],
      createdAt: "2026-08-06T20:00:00.000Z",
      updatedAt: "2026-08-06T20:00:00.000Z",
    },
    submissionText: "A clear thesis.",
  })
  assert.equal(candidates[0].passageText, "clear thesis")
})
