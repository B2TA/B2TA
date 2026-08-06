import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { beforeEach, expect, test, vi } from "vitest"

import App from "../App"

const review = {
  sessionId: "session-1",
  submissions: [
    {
      submissionId: "submission-1",
      studentDisplayName: "Alex Able",
      criterionScores: [
        { criterionId: "criterion-1", points: 5, levelLabel: "Strong" },
      ],
      total: 5,
      maxPossible: 5,
      flags: [],
    },
    {
      submissionId: "submission-2",
      studentDisplayName: "Blair Baker",
      criterionScores: [
        { criterionId: "criterion-1", points: null, levelLabel: null },
      ],
      total: null,
      maxPossible: 5,
      flags: ["extraction_failed"],
    },
  ],
  reviewConfirmedAt: null,
  flaggedCount: 1,
  unflaggedCount: 1,
}

const emptyPublication = {
  summary: { total: 1, published: 0, failed: 0, skipped: 0 },
  outcomes: [],
}

beforeEach(() => {
  window.history.pushState({}, "", "/sessions/session-1/review")
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input)
        const json = (body: unknown) =>
          Promise.resolve(
            new Response(JSON.stringify(body), {
              status: 200,
              headers: { "Content-Type": "application/json" },
            }),
          )
        if (url.endsWith("/review/confirm") && init?.method === "POST") {
          return json({
            ...review,
            reviewConfirmedAt: "2026-08-06T22:00:00.000Z",
          })
        }
        if (url.endsWith("/canvas/publication") && init?.method === "POST") {
          return json({
            summary: { total: 1, published: 1, failed: 0, skipped: 0 },
            outcomes: [
              {
                submissionId: "submission-1",
                studentDisplayName: "Alex Able",
                status: "published",
                error: null,
                publishedAt: "2026-08-06T22:01:00.000Z",
                gradingRecordSavedAt: "2026-08-06T21:00:00.000Z",
              },
            ],
          })
        }
        if (url.endsWith("/canvas/publication")) return json(emptyPublication)
        if (url.endsWith("/review")) return json(review)
        return json({ error: "Unexpected request" })
      }),
  )
})

test("TA confirms the review before publishing grades to Canvas", async () => {
  const user = userEvent.setup()
  render(<App />)

  expect(
    await screen.findByRole("heading", { name: "Review grading batch" }),
  ).toBeVisible()
  expect(screen.getByText("Alex Able")).toBeVisible()
  expect(screen.getByText("5/5 pts")).toBeVisible()
  expect(screen.getByText("Blair Baker")).toBeVisible()
  expect(
    screen.queryByRole("button", { name: "Publish grades to Canvas" }),
  ).not.toBeInTheDocument()

  await user.click(screen.getByRole("button", { name: "Confirm review" }))
  expect(await screen.findByText("Review confirmed")).toBeVisible()
  await user.click(
    screen.getByRole("button", { name: "Publish grades to Canvas" }),
  )

  expect(await screen.findByText(/1 published/)).toBeVisible()
  expect(screen.getByText("Published to Canvas")).toBeVisible()
})
