import assert from "node:assert/strict"
import { readFile } from "node:fs/promises"
import type { AddressInfo } from "node:net"
import { after, before, test } from "node:test"
import PDFDocument from "pdfkit"

import { createApp } from "../src/app.js"
import { closeDatabase, createDatabase } from "../src/db/connect.js"
import { PostgresStore } from "../src/store.js"
import type { AsyncJob, Rubric, Submission } from "../src/types.js"
import { FakeObjectStore } from "./fake-object-store.js"

const database = createDatabase(
  process.env.TEST_DATABASE_URL ?? "postgresql://b2ta:b2ta@localhost:5432/b2ta",
)
const objectStore = new FakeObjectStore()
const app = createApp(
  new PostgresStore(database),
  undefined,
  undefined,
  objectStore,
)
const server = app.listen(0, "127.0.0.1")
let baseUrl = ""

function makePdf(text: string): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const document = new PDFDocument()
    const chunks: Buffer[] = []
    document.on("data", (chunk) => chunks.push(Buffer.from(chunk)))
    document.on("error", reject)
    document.on("end", () => resolve(Buffer.concat(chunks)))
    document.fontSize(14).text(text)
    document.end()
  })
}

before(async () => {
  await new Promise<void>((resolve) => server.once("listening", resolve))
  baseUrl = `http://127.0.0.1:${(server.address() as AddressInfo).port}`
})

after(async () => {
  await new Promise<void>((resolve, reject) =>
    server.close((error) => (error ? reject(error) : resolve())),
  )
  await closeDatabase(database)
})

test("TA completes a grading workflow without an LMS connection", async () => {
  const session = await fetch(`${baseUrl}/api/sessions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name: "Standalone document analysis" }),
  }).then((response) => response.json() as Promise<{ id: string }>)

  const rubricCsv = await readFile(
    new URL("../../public/templates/canvas-rubric-template.csv", import.meta.url),
    "utf8",
  )
  const previewResponse = await fetch(
    `${baseUrl}/api/sessions/${session.id}/rubric/import/preview`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ format: "canvas_csv", csv: rubricCsv }),
    },
  )
  assert.equal(previewResponse.status, 200)
  const preview = (await previewResponse.json()) as {
    sourceFormat: "csv"
    criteria: unknown[]
  }
  const rubricResponse = await fetch(
    `${baseUrl}/api/sessions/${session.id}/rubric`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(preview),
    },
  )
  assert.equal(rubricResponse.status, 200)
  const rubric = (await rubricResponse.json()) as Rubric
  assert.equal(rubric.sourceFormat, "csv")

  const pdf = await makePdf("A standalone submission with extractable text.")
  const uploadResponse = await fetch(
    `${baseUrl}/api/sessions/${session.id}/submission-uploads`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        files: [
          {
            filename: "Barilla Case Study COMR 398.pdf",
            size: pdf.length,
            studentDisplayName: "Test Student",
          },
        ],
      }),
    },
  )
  assert.equal(uploadResponse.status, 201)
  const upload = (await uploadResponse.json()) as {
    uploads: Array<{
      submission: Submission
      uploadUrl: string
    }>
  }
  const submission = upload.uploads[0].submission
  assert.equal(submission.externalStudentId, null)
  assert.ok(submission.storageKey)
  await objectStore.put(submission.storageKey!, pdf, "application/pdf")

  const jobResponse = await fetch(
    `${baseUrl}/api/sessions/${session.id}/submission-import-jobs`,
    { method: "POST" },
  )
  assert.equal(jobResponse.status, 202)
  let job = (await jobResponse.json()) as AsyncJob
  for (
    let attempt = 0;
    attempt < 100 && job.status !== "completed";
    attempt++
  ) {
    await new Promise((resolve) => setTimeout(resolve, 25))
    job = await fetch(`${baseUrl}/api/jobs/${job.id}`).then(
      (response) => response.json() as Promise<AsyncJob>,
    )
  }
  assert.equal(job.status, "completed")
  assert.equal(job.completedItems, 1)
  assert.equal(job.failedItems, 0)

  const gradeResponse = await fetch(
    `${baseUrl}/api/sessions/${session.id}/submissions/${submission.id}/grading-record`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        overallFeedback: "Well-supported analysis.",
        criterionScores: rubric.criteria.map((criterion) => ({
          criterionId: criterion.id,
          selectedLevelId: criterion.performanceLevels[0].id,
          overridePoints: null,
          criterionFeedback: "Meets this criterion.",
        })),
      }),
    },
  )
  assert.equal(gradeResponse.status, 200)
  assert.equal(
    (
      await fetch(`${baseUrl}/api/sessions/${session.id}/review/confirm`, {
        method: "POST",
      })
    ).status,
    200,
  )
  const exportResponse = await fetch(
    `${baseUrl}/api/sessions/${session.id}/export.csv`,
  )
  assert.equal(exportResponse.status, 200)
  assert.match(
    await exportResponse.text(),
    /Test Student.*Well-supported analysis/s,
  )
})
