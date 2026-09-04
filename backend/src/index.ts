import { createApp } from "./app.js"
import { createDatabaseFromEnv } from "./db/connect.js"
import { S3ObjectStore } from "./object-store.js"
import { PostgresStore } from "./store.js"
import { processSubmissionIngestJob } from "./submission-ingest.js"

const host = process.env.API_HOST ?? "0.0.0.0"
const port = Number.parseInt(process.env.API_PORT ?? "3001", 10)

const store = new PostgresStore(createDatabaseFromEnv())
const objectStore = S3ObjectStore.fromEnv()

async function resumeOpenJobs() {
  const openJobs = await store.listOpenJobs()
  await Promise.all(
    openJobs.map((job) =>
      processSubmissionIngestJob(store, objectStore, job.id),
    ),
  )
}

createApp(store, undefined, undefined, objectStore).listen(
  port,
  host,
  () => {
    console.log(`B2TA API listening on http://${host}:${port}`)
    void resumeOpenJobs().catch((error) =>
      console.error("Failed to resume submission ingestion jobs", error),
    )
  },
)
