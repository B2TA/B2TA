import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { beforeEach, expect, test, vi } from "vitest"

import App from "./App"

beforeEach(() => {
  window.history.pushState({}, "", "/sessions")
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify([
          {
            id: "session-1",
            taId: "local-ta",
            name: "CPSC 310 · Assignment 1",
            reviewConfirmedAt: null,
            createdAt: "2026-08-06T17:00:00.000Z",
            updatedAt: "2026-08-06T17:00:00.000Z",
          },
        ]),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    ))
})

test("TA can see grading sessions returned by B2TA", async () => {
  render(<App />)

  expect(await screen.findByText("CPSC 310 · Assignment 1")).toBeVisible()
  expect(screen.getByRole("link", { name: /resume grading/i })).toHaveAttribute(
    "href",
    "/sessions/session-1/setup",
  )
})

test("TA sees how to recover when sessions cannot be loaded", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(new Response("Unavailable", { status: 503 })),
  )

  render(<App />)

  expect(
    await screen.findByRole("alert", {}, { timeout: 3_000 }),
  ).toHaveTextContent("Check that the B2TA API is running")
})

test("TA can create a grading session and continue to setup", async () => {
  const createdSession = {
    id: "session-new",
    taId: "local-ta",
    name: "WRDS 150 · Research essay",
    reviewConfirmedAt: null,
    createdAt: "2026-08-06T18:00:00.000Z",
    updatedAt: "2026-08-06T18:00:00.000Z",
  }
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockImplementation((_input: RequestInfo | URL, init?: RequestInit) =>
        Promise.resolve(
          new Response(
            JSON.stringify(init?.method === "POST" ? createdSession : []),
            {
              status: init?.method === "POST" ? 201 : 200,
              headers: { "Content-Type": "application/json" },
            },
          ),
        ),
      ),
  )
  const user = userEvent.setup()

  render(<App />)
  await screen.findByText("No grading sessions yet")
  await user.click(screen.getByRole("button", { name: /new grading session/i }))
  await user.type(
    screen.getByLabelText(/session name/i),
    "WRDS 150 · Research essay",
  )
  await user.click(screen.getByRole("button", { name: /create session/i }))

  expect(
    await screen.findByRole("heading", { name: "Session Setup" }),
  ).toBeVisible()
  expect(screen.getByText("Session: session-new")).toBeVisible()
})

test("TA can delete a grading session after confirming", async () => {
  let deleted = false
  const existingSession = {
    id: "session-delete",
    taId: "local-ta",
    name: "POLI 101 · Midterm essays",
    reviewConfirmedAt: null,
    createdAt: "2026-08-06T18:00:00.000Z",
    updatedAt: "2026-08-06T18:00:00.000Z",
  }
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockImplementation((_input: RequestInfo | URL, init?: RequestInit) => {
        if (init?.method === "DELETE") {
          deleted = true
          return Promise.resolve(new Response(null, { status: 204 }))
        }
        return Promise.resolve(
          new Response(JSON.stringify(deleted ? [] : [existingSession]), {
            status: 200,
            headers: { "Content-Type": "application/json" },
          }),
        )
      }),
  )
  const user = userEvent.setup()

  render(<App />)
  expect(await screen.findByText("POLI 101 · Midterm essays")).toBeVisible()
  await user.click(screen.getByRole("button", { name: /delete POLI 101/i }))
  await user.click(screen.getByRole("button", { name: "Delete session" }))

  expect(await screen.findByText("No grading sessions yet")).toBeVisible()
  expect(
    screen.queryByText("POLI 101 · Midterm essays"),
  ).not.toBeInTheDocument()
})
