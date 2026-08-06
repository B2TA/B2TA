import assert from "node:assert/strict"
import { after, before, test } from "node:test"
import type { AddressInfo } from "node:net"

import { createApp } from "../src/app.js"

const app = createApp()
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
  const session = (await createdResponse.json()) as { id: string; name: string }
  assert.equal(session.name, "CPSC 310 Assignment 1")

  const listResponse = await fetch(`${baseUrl}/api/sessions`)
  const sessions = (await listResponse.json()) as Array<{ id: string }>
  assert.equal(sessions.length, 1)
  assert.equal(sessions[0].id, session.id)

  const rubricResponse = await fetch(`${baseUrl}/api/sessions/${session.id}/rubric`, {
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
  })
  assert.equal(rubricResponse.status, 200)
  const rubric = (await rubricResponse.json()) as { criteria: Array<{ title: string }> }
  assert.equal(rubric.criteria[0].title, "Thesis")

  const submissionsResponse = await fetch(`${baseUrl}/api/sessions/${session.id}/submissions`)
  assert.equal(submissionsResponse.status, 200)
  assert.deepEqual(await submissionsResponse.json(), [])

  const deleteResponse = await fetch(`${baseUrl}/api/sessions/${session.id}`, { method: "DELETE" })
  assert.equal(deleteResponse.status, 204)

  const missingResponse = await fetch(`${baseUrl}/api/sessions/${session.id}`)
  assert.equal(missingResponse.status, 404)
})
