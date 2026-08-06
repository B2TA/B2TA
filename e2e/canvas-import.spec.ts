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
  await page.getByRole("button", { name: "Import rubric" }).click()

  await expect(
    page.getByRole("heading", { name: "Thesis clarity" }),
  ).toBeVisible()
  await expect(page.getByText(/^Strong/)).toBeVisible()

  await page
    .getByRole("button", { name: "Import roster and submissions" })
    .click()
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

  await page.reload()
  await expect(
    page.getByRole("heading", { name: "Thesis clarity" }),
  ).toBeVisible()
  await expect(page.getByText("Alex Able")).toBeVisible()

  await page.goto("/sessions")
  await page.getByRole("button", { name: `Delete ${sessionName}` }).click()
  await page.getByRole("button", { name: "Delete session" }).click()
  await expect(
    page.getByRole("heading", { name: sessionName, exact: true }),
  ).not.toBeVisible()
})
