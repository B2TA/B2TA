import { expect, test } from "@playwright/test"

test("TA imports a Canvas rubric, roster, and PDF submission batch", async ({
  page,
}) => {
  const sessionName = `Canvas rubric import ${Date.now()}`

  await page.goto("/sessions")
  await page.getByRole("button", { name: "New grading session" }).click()
  await page.getByLabel("Session name").fill(sessionName)
  await page.getByRole("button", { name: "Create session" }).click()

  await page.getByLabel("Canvas URL").fill("http://127.0.0.1:3002")
  await page.getByLabel("Personal access token").fill("canvas-pat")
  await page.getByRole("button", { name: "Connect Canvas" }).click()
  await expect(page.getByText("Connected as Ada TA")).toBeVisible()

  await page.getByLabel("Course").selectOption("42")
  await page.getByLabel("Assignment").selectOption("99")
  await page.getByRole("button", { name: "Import assignment" }).click()

  await expect(
    page.getByRole("heading", { name: "Thesis clarity" }),
  ).toBeVisible()
  await expect(page.getByText(/^Strong/)).toBeVisible()

  await expect(page.getByText("1 ready")).toBeVisible()
  await expect(page.getByText("1 missing")).toBeVisible()
  await expect(page.getByText("Alex Able")).toBeVisible()
  await expect(
    page.getByText("32 characters ready for evidence matching"),
  ).toBeVisible()
  const pdfUrl = await page
    .getByRole("link", { name: "View PDF" })
    .getAttribute("href")
  expect(pdfUrl).toBeTruthy()
  const pdfResponse = await page.request.get(pdfUrl!)
  expect(pdfResponse.ok()).toBe(true)
  expect(pdfResponse.headers()["content-type"]).toBe("application/pdf")
  expect((await pdfResponse.body()).toString().startsWith("%PDF-1.4")).toBe(
    true,
  )

  const sessionId = page.url().match(/\/sessions\/([^/]+)/)?.[1]
  expect(sessionId).toBeTruthy()
  const rubric = await page.request
    .get(`/api/sessions/${sessionId}/rubric`)
    .then((response) => response.json())
  const submissions = await page.request
    .get(`/api/sessions/${sessionId}/submissions`)
    .then((response) => response.json())
  await page.route(
    `**/api/sessions/${sessionId}/submissions/${submissions[0].id}/evidence-suggestions`,
    async (route) => {
      const suggestions =
        route.request().method() === "POST"
          ? [
              {
                id: "e2e-suggestion",
                submissionId: submissions[0].id,
                criterionId: rubric.criteria[0].id,
                passageStart: 0,
                passageEnd: 31,
                rationale: "This passage appears to state the central claim.",
                confidence: 0.9,
                createdAt: "2026-08-06T20:00:00.000Z",
              },
            ]
          : []
      await route.fulfill({ json: suggestions })
    },
  )

  await page.getByRole("link", { name: "Grade submission" }).click()
  await expect(page.getByRole("heading", { name: "Alex Able" })).toBeVisible()
  await expect(page.getByLabel("Alex Able submission PDF")).toBeVisible()
  const evidenceOverlay = page.getByRole("button", {
    name: "Why this may match Thesis clarity",
  })
  await expect(evidenceOverlay).toBeVisible()
  await evidenceOverlay.hover()
  await expect(
    page.getByText("This passage appears to state the central claim."),
  ).toBeVisible()
  await expect(
    page.getByRole("button", { name: "Find rubric evidence" }),
  ).not.toBeVisible()
  await expect(
    page.getByRole("radio", { name: "Strong — 5 points" }),
  ).not.toBeChecked()
  await page.getByRole("radio", { name: "Strong — 5 points" }).click()
  await page
    .getByLabel("Feedback for Thesis clarity")
    .fill("The thesis is focused and specific.")
  await page
    .getByLabel("Overall feedback")
    .fill("Clear argument with well-chosen evidence.")
  await page.getByRole("button", { name: "Save grading" }).click()
  await expect(page.getByText("Saved just now")).toBeVisible()
  await page.reload()
  await expect(page.getByLabel("Overall feedback")).toHaveValue(
    "Clear argument with well-chosen evidence.",
  )
  await expect(
    page.getByRole("radio", { name: "Strong — 5 points" }),
  ).toBeChecked()

  await page.goto("/sessions")
  await page.getByRole("button", { name: `Delete ${sessionName}` }).click()
  await page.getByRole("button", { name: "Delete session" }).click()
  await expect(
    page.getByRole("heading", { name: sessionName, exact: true }),
  ).not.toBeVisible()
})
