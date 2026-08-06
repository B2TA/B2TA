import { defineConfig } from "@playwright/test"

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  use: {
    baseURL: "http://127.0.0.1:8443",
    trace: "retain-on-failure",
  },
  webServer: [
    {
      command: "node e2e/fixtures/canvas-server.mjs",
      reuseExistingServer: true,
      url: "http://127.0.0.1:3002/api/v1/users/self/profile",
    },
    {
      command: "pnpm dev:api",
      reuseExistingServer: true,
      url: "http://127.0.0.1:3001/api/health",
    },
    {
      command: "pnpm dev",
      reuseExistingServer: true,
      url: "http://127.0.0.1:8443/sessions",
    },
  ],
})
