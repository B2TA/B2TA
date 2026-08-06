import assert from "node:assert/strict"
import { createServer } from "node:http"
import type { AddressInfo } from "node:net"
import { after, before, test } from "node:test"

import { createApp } from "../src/app.js"

const canvasAssignment = {
  id: 99,
  course_id: 42,
  name: "Argumentative essay",
  points_possible: 10,
  rubric: [
    {
      id: "criterion-1",
      description: "Thesis clarity",
      long_description: "States a focused, arguable claim.",
      points: 5,
      ratings: [
        {
          id: "level-1",
          description: "Strong",
          long_description: "Clear and specific.",
          points: 5,
        },
        {
          id: "level-2",
          description: "Developing",
          long_description: "Present but broad.",
          points: 3,
        },
      ],
    },
  ],
}

const canvasServer = createServer((request, response) => {
  if (request.headers.authorization !== "Bearer canvas-pat") {
    response.setHeader("Content-Type", "application/json")
    response
      .writeHead(401)
      .end(JSON.stringify({ errors: [{ message: "Invalid access token" }] }))
    return
  }

  const url = new URL(request.url ?? "/", "http://canvas.test")
  const path = url.pathname
  const canvasOrigin = `http://${request.headers.host}`
  if (path === "/files/501/download") {
    response.setHeader("Content-Type", "application/pdf")
    response.end(Buffer.from("%PDF-1.4\nHW1 submission\n%%EOF"))
    return
  }
  if (path === "/files/599/download") {
    response.writeHead(500).end("file unavailable")
    return
  }

  response.setHeader("Content-Type", "application/json")
  if (path === "/api/v1/users/self/profile") {
    response.end(JSON.stringify({ id: 7, name: "Ada TA" }))
    return
  }
  if (path === "/api/v1/courses") {
    response.end(
      JSON.stringify([{ id: 42, name: "CPSC 310", course_code: "CPSC 310" }]),
    )
    return
  }
  if (path === "/api/v1/courses/42/assignments") {
    response.end(JSON.stringify([canvasAssignment]))
    return
  }
  if (path === "/api/v1/courses/42/assignments/99") {
    response.end(JSON.stringify(canvasAssignment))
    return
  }
  if (path === "/api/v1/courses/42/users") {
    if (url.searchParams.get("page") === "2") {
      response.end(
        JSON.stringify([
          {
            id: 14,
            name: "Casey Clark",
            sortable_name: "Clark, Casey",
            login_id: "casey",
          },
          {
            id: 15,
            name: "Devon Diaz",
            sortable_name: "Diaz, Devon",
            sis_user_id: "s15",
          },
        ]),
      )
      return
    }
    response.setHeader(
      "Link",
      `<${canvasOrigin}/api/v1/courses/42/users?page=2>; rel="next"`,
    )
    response.end(
      JSON.stringify([
        {
          id: 12,
          name: "Alex Able",
          sortable_name: "Able, Alex",
          sis_user_id: "s12",
        },
        {
          id: 13,
          name: "Blair Baker",
          sortable_name: "Baker, Blair",
          login_id: "blair",
        },
      ]),
    )
    return
  }
  if (path === "/api/v1/courses/42/assignments/99/submissions") {
    response.end(
      JSON.stringify([
        {
          id: 812,
          user_id: 12,
          workflow_state: "submitted",
          submission_type: "online_upload",
          submitted_at: "2026-08-06T18:00:00Z",
          attempt: 2,
          submission_history: [{ attempt: 1 }, { attempt: 2 }],
          attachments: [
            {
              id: 501,
              filename: "alex-hw1.pdf",
              content_type: "application/pdf",
              size: 31,
              url: `${canvasOrigin}/files/501/download`,
            },
          ],
        },
        {
          id: 813,
          user_id: 13,
          workflow_state: "unsubmitted",
          attempt: null,
        },
        {
          id: 814,
          user_id: 14,
          workflow_state: "submitted",
          submission_type: "online_text_entry",
          submitted_at: "2026-08-06T19:00:00Z",
          attempt: 1,
          body: "<p>Casey's answer</p>",
        },
        {
          id: 815,
          user_id: 15,
          workflow_state: "submitted",
          submission_type: "online_upload",
          submitted_at: "2026-08-06T20:00:00Z",
          attempt: 1,
          attachments: [
            {
              id: 599,
              filename: "devon-hw1.pdf",
              content_type: "application/pdf",
              size: 20,
              url: `${canvasOrigin}/files/599/download`,
            },
          ],
        },
      ]),
    )
    return
  }

  response.writeHead(404).end(JSON.stringify({ error: "Not found" }))
})

const app = createApp()
const apiServer = app.listen(0, "127.0.0.1")
let apiBaseUrl = ""
let canvasBaseUrl = ""

before(async () => {
  await Promise.all([
    new Promise<void>((resolve) => apiServer.once("listening", resolve)),
    new Promise<void>((resolve) =>
      canvasServer.listen(0, "127.0.0.1", resolve),
    ),
  ])
  apiBaseUrl = `http://127.0.0.1:${(apiServer.address() as AddressInfo).port}`
  canvasBaseUrl = `http://127.0.0.1:${(canvasServer.address() as AddressInfo).port}`
})

after(async () => {
  await Promise.all([
    new Promise<void>((resolve, reject) =>
      apiServer.close((error) => (error ? reject(error) : resolve())),
    ),
    new Promise<void>((resolve, reject) =>
      canvasServer.close((error) => (error ? reject(error) : resolve())),
    ),
  ])
})

test("TA connects Canvas and imports an assignment rubric", async () => {
  const rejectedResponse = await fetch(`${apiBaseUrl}/api/canvas/connection`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      baseUrl: canvasBaseUrl,
      accessToken: "wrong-token",
    }),
  })
  assert.equal(rejectedResponse.status, 401)
  assert.equal((await rejectedResponse.text()).includes("wrong-token"), false)

  const connectionResponse = await fetch(
    `${apiBaseUrl}/api/canvas/connection`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        baseUrl: canvasBaseUrl,
        accessToken: "canvas-pat",
      }),
    },
  )
  assert.equal(connectionResponse.status, 201)
  const connection =
    (await connectionResponse.json()) as Record<string, unknown>
  assert.deepEqual(connection, {
    connected: true,
    baseUrl: canvasBaseUrl,
    user: { id: 7, name: "Ada TA" },
  })
  assert.equal(JSON.stringify(connection).includes("canvas-pat"), false)

  const coursesResponse = await fetch(`${apiBaseUrl}/api/canvas/courses`)
  assert.equal(coursesResponse.status, 200)
  assert.deepEqual(await coursesResponse.json(), [
    { id: 42, name: "CPSC 310", courseCode: "CPSC 310" },
  ])

  const assignmentsResponse = await fetch(
    `${apiBaseUrl}/api/canvas/courses/42/assignments`,
  )
  assert.equal(assignmentsResponse.status, 200)
  assert.deepEqual(await assignmentsResponse.json(), [
    {
      id: 99,
      name: "Argumentative essay",
      pointsPossible: 10,
      hasRubric: true,
    },
  ])

  const sessionResponse = await fetch(`${apiBaseUrl}/api/sessions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name: "Canvas import" }),
  })
  const session = (await sessionResponse.json()) as { id: string }

  const importResponse = await fetch(
    `${apiBaseUrl}/api/sessions/${session.id}/canvas/import`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ courseId: 42, assignmentId: 99 }),
    },
  )
  assert.equal(importResponse.status, 200)
  const rubric = (await importResponse.json()) as {
    sourceFormat: string
    criteria: Array<{
      title: string
      performanceLevels: Array<{ label: string }>
    }>
  }
  assert.equal(rubric.sourceFormat, "canvas")
  assert.equal(rubric.criteria[0].title, "Thesis clarity")
  assert.deepEqual(
    rubric.criteria[0].performanceLevels.map((level) => level.label),
    ["Strong", "Developing"],
  )
})

test("TA imports a stable Canvas submission batch with displayable PDFs", async () => {
  await fetch(`${apiBaseUrl}/api/canvas/connection`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ baseUrl: canvasBaseUrl, accessToken: "canvas-pat" }),
  })
  const sessionResponse = await fetch(`${apiBaseUrl}/api/sessions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name: "HW1 grading" }),
  })
  const session = (await sessionResponse.json()) as { id: string }

  const response = await fetch(
    `${apiBaseUrl}/api/sessions/${session.id}/canvas/submissions/import`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ courseId: 42, assignmentId: 99 }),
    },
  )

  assert.equal(response.status, 200)
  const batch = (await response.json()) as {
    summary: Record<string, number>
    submissions: Array<{
      id: string
      studentDisplayName: string
      externalStudentId: string
      externalSubmissionId: string | null
      importStatus: string
      attemptCount: number
      originalFilename: string
      extractedText: string | null
      artifactUrl: string | null
    }>
  }
  assert.deepEqual(batch.summary, {
    totalStudents: 4,
    imported: 2,
    missing: 1,
    failed: 1,
    multipleAttempts: 1,
  })
  assert.deepEqual(
    batch.submissions.map((submission) => [
      submission.studentDisplayName,
      submission.importStatus,
    ]),
    [
      ["Alex Able", "ready"],
      ["Blair Baker", "missing"],
      ["Casey Clark", "ready"],
      ["Devon Diaz", "failed"],
    ],
  )
  assert.equal(batch.submissions[0].externalStudentId, "12")
  assert.equal(batch.submissions[0].externalSubmissionId, "812")
  assert.equal(batch.submissions[0].attemptCount, 2)
  assert.equal(batch.submissions[0].originalFilename, "alex-hw1.pdf")
  assert.equal(batch.submissions[2].extractedText, "Casey's answer")

  const artifactResponse = await fetch(
    `${apiBaseUrl}${batch.submissions[0].artifactUrl}`,
  )
  assert.equal(artifactResponse.status, 200)
  assert.equal(artifactResponse.headers.get("content-type"), "application/pdf")
  assert.match(
    artifactResponse.headers.get("content-disposition") ?? "",
    /^inline; filename="alex-hw1.pdf"$/,
  )
  assert.equal((await artifactResponse.text()).startsWith("%PDF-1.4"), true)

  const persistedResponse = await fetch(
    `${apiBaseUrl}/api/sessions/${session.id}/submissions`,
  )
  assert.deepEqual(await persistedResponse.json(), batch.submissions)
})
