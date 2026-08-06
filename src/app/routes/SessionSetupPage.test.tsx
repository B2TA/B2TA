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

const importedBatch = {
  summary: {
    totalStudents: 5,
    imported: 1,
    missing: 1,
    failed: 3,
    multipleAttempts: 1,
  },
  submissions: [
    {
      id: "submission-1",
      sessionId: "session-1",
      storageKey: "memory://submission-1",
      originalFilename: "alex-hw1.pdf",
      studentDisplayName: "Alex Able",
      externalStudentId: "12",
      externalSubmissionId: "812",
      identityStatus: "verified",
      importStatus: "ready",
      submissionType: "pdf",
      attemptCount: 2,
      submittedAt: "2026-08-06T18:00:00Z",
      artifactUrl: "/api/sessions/session-1/submissions/submission-1/artifact",
      extractionStatus: "success",
      extractionFailureReason: null,
      extractedText: "A precise thesis supported by course evidence.",
      extractedCharCount: 46,
      isOversized: false,
      position: 0,
      createdAt: "2026-08-06T18:00:00Z",
    },
    {
      id: "submission-2",
      sessionId: "session-1",
      storageKey: "memory://submission-3",
      originalFilename: "",
      studentDisplayName: "Blair Baker",
      externalStudentId: "13",
      externalSubmissionId: "813",
      identityStatus: "verified",
      importStatus: "missing",
      submissionType: "missing",
      attemptCount: 0,
      submittedAt: null,
      artifactUrl: null,
      extractionStatus: "not_applicable",
      extractionFailureReason: null,
      extractedText: null,
      extractedCharCount: null,
      isOversized: false,
      position: 1,
      createdAt: "2026-08-06T18:00:00Z",
    },
    {
      id: "submission-3",
      sessionId: "session-1",
      storageKey: null,
      originalFilename: "devon-hw1.pdf",
      studentDisplayName: "Devon Diaz",
      externalStudentId: "15",
      externalSubmissionId: "815",
      identityStatus: "verified",
      importStatus: "failed",
      submissionType: "pdf",
      attemptCount: 1,
      submittedAt: "2026-08-06T20:00:00Z",
      artifactUrl: "/api/sessions/session-1/submissions/submission-3/artifact",
      extractionStatus: "failed",
      extractionFailureReason: "no_extractable_text",
      extractedText: null,
      extractedCharCount: null,
      isOversized: false,
      position: 2,
      createdAt: "2026-08-06T18:00:00Z",
    },
    {
      id: "submission-4",
      sessionId: "session-1",
      storageKey: "memory://submission-4",
      originalFilename: "encrypted-hw1.pdf",
      studentDisplayName: "Erin Evans",
      externalStudentId: "16",
      externalSubmissionId: "816",
      identityStatus: "verified",
      importStatus: "failed",
      submissionType: "pdf",
      attemptCount: 1,
      submittedAt: "2026-08-06T20:00:00Z",
      artifactUrl: "/api/sessions/session-1/submissions/submission-4/artifact",
      extractionStatus: "failed",
      extractionFailureReason: "password_protected",
      extractedText: null,
      extractedCharCount: null,
      isOversized: false,
      position: 3,
      createdAt: "2026-08-06T18:00:00Z",
    },
    {
      id: "submission-5",
      sessionId: "session-1",
      storageKey: "memory://submission-5",
      originalFilename: "malformed-hw1.pdf",
      studentDisplayName: "Frank Fox",
      externalStudentId: "17",
      externalSubmissionId: "817",
      identityStatus: "verified",
      importStatus: "failed",
      submissionType: "pdf",
      attemptCount: 1,
      submittedAt: "2026-08-06T20:00:00Z",
      artifactUrl: "/api/sessions/session-1/submissions/submission-5/artifact",
      extractionStatus: "failed",
      extractionFailureReason: "unreadable_file",
      extractedText: null,
      extractedCharCount: null,
      isOversized: false,
      position: 4,
      createdAt: "2026-08-06T18:00:00Z",
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
        if (
          url.endsWith("/api/sessions/session-1/submissions") &&
          !init?.method
        )
          return json([])
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
        if (
          url.endsWith("/api/sessions/session-1/canvas/submissions/import") &&
          init?.method === "POST"
        ) {
          return json(importedBatch)
        }
        return json({ error: "Unexpected request" }, 500)
      }),
  )
})

test("TA imports the Canvas rubric and submissions in one assignment flow", async () => {
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
  await user.click(screen.getByRole("button", { name: "Import assignment" }))

  expect(
    await screen.findByRole("heading", { name: "Thesis clarity" }),
  ).toBeVisible()
  expect(screen.getByText(/^Strong/)).toBeVisible()
  expect(screen.queryByDisplayValue("secret-pat")).not.toBeInTheDocument()

  expect(await screen.findByText("1 ready")).toBeVisible()
  expect(screen.getByText("1 missing")).toBeVisible()
  expect(screen.getByText("3 failed")).toBeVisible()
  expect(screen.getByText("Alex Able")).toBeVisible()
  expect(screen.getByText(/2 attempts/)).toBeVisible()
  expect(screen.getAllByRole("link", { name: "View PDF" })[0]).toHaveAttribute(
    "href",
    "/api/sessions/session-1/submissions/submission-1/artifact",
  )
  expect(screen.getByText("Blair Baker")).toBeVisible()
  expect(screen.getByText("Missing submission")).toBeVisible()
  expect(screen.getByText("Devon Diaz")).toBeVisible()
  expect(screen.getByText("No selectable text · OCR needed")).toBeVisible()
  expect(
    screen.getByText("Password-protected PDF · ask for an unlocked copy"),
  ).toBeVisible()
  expect(screen.getByText("Unreadable PDF · ask for a new copy")).toBeVisible()
  expect(
    screen.getByText("46 characters ready for evidence matching"),
  ).toBeVisible()
  expect(
    screen.queryByRole("button", { name: "Import roster and submissions" }),
  ).not.toBeInTheDocument()
})
