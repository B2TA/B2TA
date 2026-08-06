import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { beforeEach, expect, test, vi } from "vitest"

import App from "../App"

const session = {
  id: "session-1",
  taId: "local-ta",
  name: "CPSC 310 · Argumentative essay",
  reviewConfirmedAt: null,
  createdAt: "2026-08-06T17:00:00.000Z",
  updatedAt: "2026-08-06T17:00:00.000Z",
}

const importedRubric = {
  id: "rubric-1",
  sessionId: "session-1",
  storageKey: null,
  sourceFormat: "canvas",
  createdAt: "2026-08-06T17:00:00.000Z",
  updatedAt: "2026-08-06T17:00:00.000Z",
  criteria: [
    {
      id: "criterion-1",
      rubricId: "rubric-1",
      title: "Thesis clarity",
      description: "States a focused claim.",
      maxPoints: 5,
      displayColor: "#B45309",
      position: 0,
      requiresCompletion: false,
      createdAt: "2026-08-06T17:00:00.000Z",
      performanceLevels: [
        {
          id: "level-1",
          criterionId: "criterion-1",
          label: "Strong",
          description: "Clear and specific.",
          points: 5,
          position: 0,
        },
      ],
    },
  ],
}

beforeEach(() => {
  window.history.pushState({}, "", "/sessions/session-1/setup")
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input)
        const json = (body: unknown, status = 200) =>
          Promise.resolve(
            new Response(JSON.stringify(body), {
              status,
              headers: { "Content-Type": "application/json" },
            }),
          )

        if (url.endsWith("/api/sessions/session-1/rubric") && !init?.method)
          return json({}, 404)
        if (url.endsWith("/api/sessions/session-1")) return json(session)
        if (url.endsWith("/api/canvas/connection") && init?.method === "POST") {
          return json(
            {
              connected: true,
              baseUrl: "https://canvas.example.edu",
              user: { id: 7, name: "Ada TA" },
            },
            201,
          )
        }
        if (url.endsWith("/api/canvas/courses")) {
          return json([{ id: 42, name: "CPSC 310", courseCode: "CPSC 310" }])
        }
        if (url.endsWith("/api/canvas/courses/42/assignments")) {
          return json([
            {
              id: 99,
              name: "Argumentative essay",
              pointsPossible: 10,
              hasRubric: true,
            },
          ])
        }
        if (
          url.endsWith("/api/sessions/session-1/canvas/import") &&
          init?.method === "POST"
        ) {
          return json(importedRubric)
        }
        return json({ error: "Unexpected request" }, 500)
      }),
  )
})

test("TA connects Canvas and imports the assignment rubric", async () => {
  const user = userEvent.setup()
  render(<App />)

  expect(
    await screen.findByRole("heading", {
      name: "Connect this session to Canvas",
    }),
  ).toBeVisible()
  await user.type(
    screen.getByLabelText("Canvas URL"),
    "https://canvas.example.edu",
  )
  await user.type(screen.getByLabelText("Personal access token"), "secret-pat")
  await user.click(screen.getByRole("button", { name: "Connect Canvas" }))

  expect(await screen.findByText("Connected as Ada TA")).toBeVisible()
  await user.selectOptions(screen.getByLabelText("Course"), "42")
  await user.selectOptions(await screen.findByLabelText("Assignment"), "99")
  await user.click(screen.getByRole("button", { name: "Import rubric" }))

  expect(
    await screen.findByRole("heading", { name: "Thesis clarity" }),
  ).toBeVisible()
  expect(screen.getByText(/^Strong/)).toBeVisible()
  expect(screen.queryByDisplayValue("secret-pat")).not.toBeInTheDocument()
})
