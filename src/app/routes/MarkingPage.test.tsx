import { render, screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { beforeEach, expect, test, vi } from "vitest"

vi.mock("../components/PdfSubmissionViewer", () => ({
  default: ({
    artifactUrl,
    rubric,
    studentDisplayName,
    suggestions,
  }: {
    artifactUrl: string
    rubric: { criteria: Array<{ id: string title: string }> }
    studentDisplayName: string
    suggestions: Array<{
      id: string
      criterionId: string
      rationale: string
    }>
  }) => (
    <div aria-label={`${studentDisplayName} submission PDF`}>
      <iframe
        src={artifactUrl}
        title={`${studentDisplayName} submission PDF`}
      />
      {suggestions.map((suggestion) => (
        <button
          aria-label={`Why this may match ${rubric.criteria.find(
            (item) => item.id === suggestion.criterionId,
          )?.title}`}
          data-testid={`pdf-evidence-${suggestion.id}`}
          key={suggestion.id}
          type="button"
        >
          <span role="tooltip">{suggestion.rationale}</span>
        </button>
      ))}
    </div>
  ),
}))

import App from "../App"

const session = {
  id: "session-1",
  taId: "local-ta",
  name: "WRDS 150 Essay",
  reviewConfirmedAt: null,
  createdAt: "2026-08-06T17:00:00.000Z",
  updatedAt: "2026-08-06T17:00:00.000Z",
}
const rubric = {
  id: "rubric-1",
  sessionId: "session-1",
  storageKey: null,
  sourceFormat: "canvas",
  createdAt: session.createdAt,
  updatedAt: session.updatedAt,
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
      createdAt: session.createdAt,
      performanceLevels: [
        {
          id: "level-1",
          criterionId: "criterion-1",
          label: "Strong",
          description: "Clear and specific.",
          points: 5,
          position: 0,
        },
        {
          id: "level-2",
          criterionId: "criterion-1",
          label: "Developing",
          description: "Present but broad.",
          points: 3,
          position: 1,
        },
      ],
    },
    {
      id: "criterion-2",
      rubricId: "rubric-1",
      title: "Use of evidence",
      description: "Uses relevant evidence.",
      maxPoints: 5,
      displayColor: "#0F766E",
      position: 1,
      requiresCompletion: false,
      createdAt: session.createdAt,
      performanceLevels: [
        {
          id: "level-3",
          criterionId: "criterion-2",
          label: "Strong",
          description: "Evidence supports the claim.",
          points: 5,
          position: 0,
        },
      ],
    },
  ],
}
const submission = {
  id: "submission-1",
  sessionId: "session-1",
  storageKey: "memory://submission-1",
  originalFilename: "alex-essay.pdf",
  studentDisplayName: "Alex Able",
  externalStudentId: "12",
  externalSubmissionId: "812",
  identityStatus: "verified",
  importStatus: "ready",
  submissionType: "pdf",
  attemptCount: 1,
  submittedAt: "2026-08-06T18:00:00.000Z",
  artifactUrl: "/api/sessions/session-1/submissions/submission-1/artifact",
  extractionStatus: "success",
  extractionFailureReason: null,
  extractedText: "A clear thesis.",
  extractedCharCount: 15,
  isOversized: false,
  position: 0,
  createdAt: "2026-08-06T18:00:00.000Z",
}

let savedRecord: Record<string, unknown> | null
let evidenceSuggestions: Array<Record<string, unknown>>

beforeEach(() => {
  savedRecord = null
  evidenceSuggestions = []
  window.history.pushState({}, "", "/sessions/session-1/mark/submission-1")
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith("/sessions/session-1")) return Response.json(session)
      if (url.endsWith("/rubric")) return Response.json(rubric)
      if (url.endsWith("/submissions")) return Response.json([submission])
      if (url.endsWith("/evidence-suggestions") && init?.method === "POST") {
        evidenceSuggestions = [
          {
            id: "suggestion-1",
            submissionId: submission.id,
            criterionId: "criterion-1",
            passageStart: 2,
            passageEnd: 14,
            rationale: "This passage states the central claim.",
            confidence: 0.91,
            createdAt: "2026-08-06T20:00:00.000Z",
          },
        ]
        return Response.json(evidenceSuggestions)
      }
      if (url.endsWith("/evidence-suggestions"))
        return Response.json(evidenceSuggestions)
      if (url.endsWith("/grading-record") && init?.method === "PUT") {
        const body = JSON.parse(String(init.body))
        savedRecord = {
          id: "record-1",
          submissionId: submission.id,
          ...body,
          savedAt: "2026-08-06T20:00:00.000Z",
          createdAt: "2026-08-06T20:00:00.000Z",
        }
        return Response.json(savedRecord)
      }
      if (url.endsWith("/grading-record"))
        return savedRecord
          ? Response.json(savedRecord)
          : new Response("Not found", { status: 404 })
      return new Response("Not found", { status: 404 })
    }),
  )
})

test("AI highlights likely rubric evidence without selecting a mark", async () => {
  render(<App />)

  await screen.findByRole("heading", { name: "Alex Able" })

  const pdf = await screen.findByLabelText("Alex Able submission PDF")
  expect(
    within(pdf).getByRole("button", {
      name: "Why this may match Thesis clarity",
    }),
  ).toBeInTheDocument()
  expect(within(pdf).getByRole("tooltip")).toHaveTextContent(
    "This passage states the central claim.",
  )
  expect(
    within(screen.getByRole("group", { name: /Thesis clarity/ })).queryByText(
      "This passage states the central claim.",
    ),
  ).not.toBeInTheDocument()
  expect(screen.getAllByRole("radio", { checked: false })).toHaveLength(3)
  expect(screen.queryByText(/recommended score/i)).not.toBeInTheDocument()
  expect(
    screen.queryByRole("button", { name: "Find rubric evidence" }),
  ).not.toBeInTheDocument()
})

test("TA grades one submission and restores the saved record", async () => {
  const user = userEvent.setup()
  const firstRender = render(<App />)

  expect(
    await screen.findByRole("heading", { name: "Alex Able" }),
  ).toBeVisible()
  expect(await screen.findByLabelText("Alex Able submission PDF")).toBeVisible()
  expect(screen.getByRole("link", { name: "Open PDF" })).toHaveAttribute(
    "href",
    submission.artifactUrl,
  )
  const strongLevels = screen.getAllByRole("radio", {
    name: "Strong — 5 points",
    exact: true,
  })
  await user.click(strongLevels[0])
  await user.click(strongLevels[1])
  await user.type(
    screen.getByLabelText("Feedback for Thesis clarity"),
    "The claim is precise.",
  )
  await user.type(
    screen.getByLabelText("Overall feedback"),
    "Focused argument.",
  )
  await user.click(screen.getByRole("button", { name: "Save grading" }))
  expect(await screen.findByText("Saved just now")).toBeVisible()

  firstRender.unmount()
  render(<App />)
  expect(await screen.findByDisplayValue("Focused argument.")).toBeVisible()
  expect(screen.getByDisplayValue("The claim is precise.")).toBeVisible()
  expect(
    screen.getAllByRole("radio", { name: "Strong — 5 points", checked: true }),
  ).toHaveLength(2)
})
