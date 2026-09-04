import { extractPdfText } from "./pdf.js"
import { MAX_PDF_BYTES, type ObjectStore } from "./object-store.js"
import type { PostgresStore } from "./store.js"

function isPdf(bytes: Uint8Array, contentType: string | undefined): boolean {
  return (
    contentType === "application/pdf" &&
    bytes.length >= 5 &&
    String.fromCharCode(...bytes.slice(0, 5)) === "%PDF-"
  )
}

export async function processSubmissionIngestJob(
  store: PostgresStore,
  objectStore: ObjectStore,
  jobId: string,
): Promise<void> {
  const job = await store.getJob(jobId)
  if (!job || job.type !== "submission_ingest" || job.status === "completed") {
    return
  }

  await store.updateJob(job.id, { status: "running", error: null })
  let completedItems = 0
  let failedItems = 0

  try {
    const submissions = (await store.listSubmissions(job.sessionId)).filter(
      (submission) => submission.extractionStatus !== "not_applicable",
    )
    for (const submission of submissions) {
      if (submission.extractionStatus === "success") {
        completedItems += 1
      } else if (!submission.storageKey) {
        failedItems += 1
        await store.updateSubmission(job.sessionId, submission.id, {
          importStatus: "failed",
          extractionStatus: "failed",
          extractionFailureReason: "upload_missing",
        })
      } else {
        try {
          const object = await objectStore.get(submission.storageKey)
          if (object.bytes.length > MAX_PDF_BYTES) {
            failedItems += 1
            await store.updateSubmission(job.sessionId, submission.id, {
              importStatus: "failed",
              extractionStatus: "failed",
              extractionFailureReason: "file_too_large",
              isOversized: true,
            })
            await store.updateJob(job.id, { completedItems, failedItems })
            continue
          }
          if (!isPdf(object.bytes, object.contentType)) {
            throw new Error("uploaded_file_is_not_pdf")
          }
          const result = await extractPdfText(Buffer.from(object.bytes))
          if (result.status === "success") {
            completedItems += 1
            await store.updateSubmission(job.sessionId, submission.id, {
              importStatus: "ready",
              extractionStatus: "success",
              extractionFailureReason: null,
              extractedText: result.text,
              extractedCharCount: result.charCount,
            })
          } else {
            failedItems += 1
            await store.updateSubmission(job.sessionId, submission.id, {
              importStatus: "failed",
              extractionStatus: "failed",
              extractionFailureReason: result.reason,
            })
          }
        } catch (error) {
          failedItems += 1
          await store.updateSubmission(job.sessionId, submission.id, {
            importStatus: "failed",
            extractionStatus: "failed",
            extractionFailureReason:
              error instanceof Error &&
              error.message === "uploaded_file_is_not_pdf"
                ? "unreadable_file"
                : "upload_missing",
          })
        }
      }
      await store.updateJob(job.id, { completedItems, failedItems })
    }
    await store.updateJob(job.id, {
      status: "completed",
      completedItems,
      failedItems,
    })
  } catch (error) {
    await store.updateJob(job.id, {
      status: "failed",
      completedItems,
      failedItems,
      error: error instanceof Error ? error.message : "Ingestion failed",
    })
  }
}
