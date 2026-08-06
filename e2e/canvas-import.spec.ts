import { expect, test } from "@playwright/test"

test("TA imports a Canvas assignment rubric with a personal access token", async ({
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

  await page.reload()
  await expect(
    page.getByRole("heading", { name: "Thesis clarity" }),
  ).toBeVisible()

  await page.goto("/sessions")
  await page.getByRole("button", { name: `Delete ${sessionName}` }).click()
  await page.getByRole("button", { name: "Delete session" }).click()
  await expect(
    page.getByRole("heading", { name: sessionName, exact: true }),
  ).not.toBeVisible()
})
