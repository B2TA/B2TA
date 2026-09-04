import assert from "node:assert/strict"
import { after, test } from "node:test"

import { PostgresStore } from "../src/store.js"
import { createDatabase, closeDatabase } from "../src/db/connect.js"

const connectionString = process.env.TEST_DATABASE_URL

if (!connectionString) {
  test(
    "PostgresStore integration tests require TEST_DATABASE_URL",
    { skip: true },
    () => {},
  )
} else {
  const database = createDatabase(connectionString)
  const firstStore = new PostgresStore(database)

  after(async () => {
    await closeDatabase(database)
  })

  test("grading metadata survives a new PostgresStore instance", async () => {
    const session = await firstStore.createSession("CPSC 310 Assignment 1")
    const rubric = await firstStore.saveRubric(session.id, {
      criteria: [
        {
          title: "Thesis",
          maxPoints: 5,
          performanceLevels: [{ label: "Strong", points: 5 }],
        },
      ],
    })

    const reloaded = await new PostgresStore(database).getRubric(session.id)

    assert.equal(reloaded?.id, rubric.id)
    assert.equal(reloaded?.criteria[0].title, "Thesis")
    assert.equal(reloaded?.criteria[0].performanceLevels[0].label, "Strong")
  })

  test("submission ingestion progress survives a new PostgresStore instance", async () => {
    const session = await firstStore.createSession("WRDS 150 Upload")
    const job = await firstStore.createJob(session.id, "submission_ingest", 2)

    await firstStore.updateJob(job.id, {
      status: "running",
      completedItems: 1,
      failedItems: 0,
    })

    const reloaded = await new PostgresStore(database).getJob(job.id)
    assert.equal(reloaded?.status, "running")
    assert.equal(reloaded?.totalItems, 2)
    assert.equal(reloaded?.completedItems, 1)
  })

  test("replacing a rubric transactionally invalidates grades and review", async () => {
    const session = await firstStore.createSession("CPSC 310 Rubric Revision")
    const rubric = await firstStore.saveRubric(session.id, {
      criteria: [{ title: "Correctness", maxPoints: 5 }],
    })
    const [submission] = await firstStore.saveSubmissionBatch(session.id, [
      {
        storageKey: "sessions/submission.pdf",
        originalFilename: "submission.pdf",
        studentDisplayName: "Alex Able",
        externalStudentId: "alex",
        externalSubmissionId: null,
        identityStatus: "verified",
        importStatus: "ready",
        submissionType: "pdf",
        attemptCount: 1,
        submittedAt: null,
        extractionStatus: "pending",
        extractionFailureReason: null,
        extractedText: null,
        extractedCharCount: null,
        isOversized: false,
      },
    ])
    await firstStore.saveGradingRecord(session.id, submission.id, {
      overallFeedback: "Good work.",
      criterionScores: [
        {
          criterionId: rubric.criteria[0].id,
          selectedLevelId: null,
          overridePoints: 4,
          criterionFeedback: "Mostly correct.",
        },
      ],
    })
    await firstStore.confirmReview(session.id)

    await firstStore.saveRubric(session.id, {
      criteria: [{ title: "Revised correctness", maxPoints: 10 }],
    })

    assert.equal(await firstStore.getGradingRecord(submission.id), undefined)
    assert.equal(
      (await firstStore.getSession(session.id))?.reviewConfirmedAt,
      null,
    )
  })
}
