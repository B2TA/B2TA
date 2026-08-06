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
  response.setHeader("Content-Type", "application/json")
  if (request.headers.authorization !== "Bearer canvas-pat") {
    response
      .writeHead(401)
      .end(JSON.stringify({ errors: [{ message: "Invalid access token" }] }))
    return
  }

  const path = new URL(request.url ?? "/", "http://canvas.test").pathname
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
