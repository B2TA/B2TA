import { expect, test } from "@playwright/test"

test("TA can create, resume, and delete a grading session", async ({
  page,
}) => {
  const sessionName = `E2E grading session ${Date.now()}`

  await page.goto("/sessions")
  await expect(
    page.getByRole("heading", { name: "Pick up where you left off." }),
  ).toBeVisible()

  await page.getByRole("button", { name: "New grading session" }).click()
  await page.getByLabel("Session name").fill(sessionName)
  await page.getByRole("button", { name: "Create session" }).click()

  await expect(
    page.getByRole("heading", { name: "Session Setup" }),
  ).toBeVisible()

  await page.goto("/sessions")
  await expect(page.getByText(sessionName)).toBeVisible()
  await page
    .getByRole("link", { name: `Resume grading ${sessionName}` })
    .click()
  await expect(
    page.getByRole("heading", { name: "Session Setup" }),
  ).toBeVisible()

  await page.goto("/sessions")
  await page.getByRole("button", { name: `Delete ${sessionName}` }).click()
  await page.getByRole("button", { name: "Delete session" }).click()
  await expect(
    page.getByRole("heading", { name: sessionName, exact: true }),
  ).not.toBeVisible()
})
