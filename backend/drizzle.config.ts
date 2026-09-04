import { defineConfig } from "drizzle-kit"

export default defineConfig({
  dialect: "postgresql",
  schema: "./src/db/schema.ts",
  out: "./drizzle",
  dbCredentials: {
    url:
      process.env.DATABASE_URL ?? "postgresql://b2ta:b2ta@localhost:5432/b2ta",
  },
  strict: true,
  verbose: true,
})
