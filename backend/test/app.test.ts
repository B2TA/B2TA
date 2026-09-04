import assert from "node:assert/strict"
import { after, before, test } from "node:test"
import type { AddressInfo } from "node:net"

import { createApp } from "../src/app.js"
import { createDatabase, closeDatabase } from "../src/db/connect.js"
import { PostgresStore } from "../src/store.js"

const database = createDatabase(
  process.env.TEST_DATABASE_URL ?? "postgresql://b2ta:b2ta@localhost:5432/b2ta",
)
const app = createApp(new PostgresStore(database))
const server = app.listen(0, "127.0.0.1")
let baseUrl = ""

before(async () => {
  await new Promise<void>((resolve) => server.once("listening", resolve))
  const address = server.address() as AddressInfo
  baseUrl = `http://127.0.0.1:${address.port}`
})

after(async () => {
  await new Promise<void>((resolve, reject) => {
    server.close((error) => (error ? reject(error) : resolve()))
  })
  await closeDatabase(database)
})

test("health check", async () => {
  const response = await fetch(`${baseUrl}/api/health`)
  assert.equal(response.status, 200)
  assert.deepEqual(await response.json(), { status: "ok" })
})

test("session, rubric, and submission workflow", async () => {
  const invalid = await fetch(`${baseUrl}/api/sessions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name: "" }),
  })
  assert.equal(invalid.status, 400)

  const createdResponse = await fetch(`${baseUrl}/api/sessions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name: "CPSC 310 Assignment 1" }),
  })
  assert.equal(createdResponse.status, 201)
  const session = (await createdResponse.json()) as {
    id: string
    name: string
  }
  assert.equal(session.name, "CPSC 310 Assignment 1")

  const listResponse = await fetch(`${baseUrl}/api/sessions`)
  const sessions = (await listResponse.json()) as Array<{ id: string }>
  assert.ok(sessions.some((item) => item.id === session.id))

  const rubricResponse = await fetch(
    `${baseUrl}/api/sessions/${session.id}/rubric`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        criteria: [
          {
            title: "Thesis",
            maxPoints: 5,
            performanceLevels: [
              { label: "Strong", points: 5 },
              { label: "Developing", points: 3 },
            ],
          },
        ],
      }),
    },
  )
  assert.equal(rubricResponse.status, 200)
  const rubric = (await rubricResponse.json()) as {
    criteria: Array<{ title: string }>
  }
  assert.equal(rubric.criteria[0].title, "Thesis")

  const submissionsResponse = await fetch(
    `${baseUrl}/api/sessions/${session.id}/submissions`,
  )
  assert.equal(submissionsResponse.status, 200)
  assert.deepEqual(await submissionsResponse.json(), [])

  const deleteResponse = await fetch(`${baseUrl}/api/sessions/${session.id}`, {
    method: "DELETE",
  })
  assert.equal(deleteResponse.status, 204)

  const missingResponse = await fetch(`${baseUrl}/api/sessions/${session.id}`)
  assert.equal(missingResponse.status, 404)
})

test("TA saves, updates, and reloads one canonical grading record", async () => {
  const gradingStore = new PostgresStore(database)
  const gradingApp = createApp(gradingStore)
  const gradingServer = gradingApp.listen(0, "127.0.0.1")
  await new Promise<void>((resolve) => gradingServer.once("listening", resolve))
  const address = gradingServer.address() as AddressInfo
  const gradingBaseUrl = `http://127.0.0.1:${address.port}`

  try {
    const session = await gradingStore.createSession("WRDS 150 Essay")
    const rubric = await gradingStore.saveRubric(session.id, {
      criteria: [
        {
          title: "Thesis clarity",
          maxPoints: 5,
          performanceLevels: [
            { label: "Strong", points: 5 },
            { label: "Developing", points: 3 },
          ],
        },
      ],
    })
    const [submission] = await gradingStore.saveSubmissionBatch(session.id, [
      {
        originalFilename: "alex-essay.pdf",
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
        extractedText: "A clear thesis.",
        extractedCharCount: 15,
        isOversized: false,
        storageKey: "test/alex-essay.pdf",
      },
    ])

    const endpoint = `${gradingBaseUrl}/api/sessions/${session.id}/submissions/${submission.id}/grading-record`
    const firstSave = await fetch(endpoint, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        overallFeedback: "Focused argument.",
        criterionScores: [
          {
            criterionId: rubric.criteria[0].id,
            selectedLevelId: rubric.criteria[0].performanceLevels[0].id,
            overridePoints: null,
            criterionFeedback: "The claim is precise.",
          },
        ],
      }),
    })
    assert.equal(firstSave.status, 200)
    const created = (await firstSave.json()) as {
      id: string
      savedAt: string
    }
    assert.ok(created.id)
    assert.ok(created.savedAt)

    const update = await fetch(endpoint, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        overallFeedback: "Focused argument with room to develop.",
        criterionScores: [
          {
            criterionId: rubric.criteria[0].id,
            selectedLevelId: null,
            overridePoints: 4,
            criterionFeedback: "The claim is precise but could be narrower.",
          },
        ],
      }),
    })
    assert.equal(update.status, 200)
    const updated = (await update.json()) as { id: string }
    assert.equal(updated.id, created.id)

    const reload = await fetch(endpoint)
    assert.equal(reload.status, 200)
    assert.deepEqual(await reload.json(), updated)
  } finally {
    await new Promise<void>((resolve, reject) =>
      gradingServer.close((error) => (error ? reject(error) : resolve())),
    )
  }
})
