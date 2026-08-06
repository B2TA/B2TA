type CanvasProfilePayload = {
  id: number
  name: string
}

type CanvasCoursePayload = {
  id: number
  name: string
  course_code?: string
}

type StoredCanvasConnection = {
  baseUrl: string
  accessToken: string
  user: CanvasProfilePayload
}

type CanvasRatingPayload = {
  id: string
  description: string
  long_description?: string
  points?: number
}

type CanvasCriterionPayload = {
  id: string
  description: string
  long_description?: string
  points?: number
  ratings?: CanvasRatingPayload[]
}

export type CanvasAssignmentPayload = {
  id: number
  course_id: number
  name: string
  points_possible?: number
  rubric?: CanvasCriterionPayload[]
}

type CanvasUserPayload = {
  id: number
  name: string
  sortable_name?: string
}

type CanvasAttachmentPayload = {
  id: number
  filename: string
  content_type?: string
  size?: number
  url: string
}

type CanvasSubmissionPayload = {
  id?: number
  user_id: number
  workflow_state?: string
  submission_type?: string
  submitted_at?: string | null
  attempt?: number | null
  body?: string | null
  attachments?: CanvasAttachmentPayload[]
  submission_history?: Array<{ attempt?: number | null }>
}

export type CanvasSubmissionImport = {
  studentDisplayName: string
  externalStudentId: string
  externalSubmissionId: string | null
  identityStatus: "verified"
  importStatus: "ready" | "missing" | "failed"
  submissionType: "pdf" | "text" | "missing" | "unsupported"
  attemptCount: number
  submittedAt: string | null
  originalFilename: string
  extractionStatus: "pending" | "success" | "failed" | "not_applicable"
  extractionFailureReason: string | null
  extractedText: string | null
  extractedCharCount: number | null
  isOversized: boolean
  artifact: {
    data: Buffer
    contentType: "application/pdf"
    filename: string
  } | null
}

const MAX_PDF_BYTES = 25 * 1024 * 1024

function htmlToText(value: string): string {
  return value
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<\/p>/gi, "\n")
    .replace(/<[^>]*>/g, "")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&#39;|&apos;/g, "'")
    .replace(/&quot;/g, '"')
    .trim()
}

export class CanvasError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message)
    this.name = "CanvasError"
  }
}

function normalizeBaseUrl(value: string): string {
  let url: URL
  try {
    url = new URL(value)
  } catch {
    throw new CanvasError(400, "invalid_canvas_url", "Enter a valid Canvas URL")
  }

  const localHost =
    url.hostname === "localhost" ||
    url.hostname === "127.0.0.1" ||
    url.hostname === "[::1]"
  if (url.protocol !== "https:" && !(url.protocol === "http:" && localHost)) {
    throw new CanvasError(
      400,
      "invalid_canvas_url",
      "Canvas URL must use HTTPS",
    )
  }
  if (
    url.username ||
    url.password ||
    (url.pathname !== "/" && url.pathname !== "")
  ) {
    throw new CanvasError(
      400,
      "invalid_canvas_url",
      "Enter the root URL of the Canvas site",
    )
  }

  return url.origin
}

function nextLink(header: string | null): string | null {
  if (!header) return null
  for (const part of header.split(",")) {
    const match = part.match(/<([^>]+)>;\s*rel="next"/)
    if (match) return match[1]
  }
  return null
}

export class CanvasAdapter {
  #connection: StoredCanvasConnection | null = null

  async connect(baseUrlValue: string, accessTokenValue: string) {
    const baseUrl = normalizeBaseUrl(baseUrlValue)
    const accessToken = accessTokenValue.trim()
    if (!accessToken || accessToken.length > 4096) {
      throw new CanvasError(
        400,
        "invalid_canvas_token",
        "Enter a Canvas access token",
      )
    }

    const user = await this.#request<CanvasProfilePayload>(
      `${baseUrl}/api/v1/users/self/profile`,
      accessToken,
      baseUrl,
    )
    if (typeof user.id !== "number" || typeof user.name !== "string") {
      throw new CanvasError(
        502,
        "invalid_canvas_response",
        "Canvas returned an unexpected profile",
      )
    }

    this.#connection = {
      baseUrl,
      accessToken,
      user: { id: user.id, name: user.name },
    }
    return { connected: true as const, baseUrl, user: this.#connection.user }
  }

  async listCourses() {
    const connection = this.#requireConnection()
    const courses = await this.#requestAll<CanvasCoursePayload>(
      `${connection.baseUrl}/api/v1/courses?enrollment_state=active&per_page=100`,
      connection,
    )
    return courses
      .filter(
        (course) =>
          typeof course.id === "number" && typeof course.name === "string",
      )
      .map((course) => ({
        id: course.id,
        name: course.name,
        courseCode: course.course_code ?? "",
      }))
  }

  async listAssignments(courseId: number) {
    const connection = this.#requireConnection()
    const assignments = await this.#requestAll<CanvasAssignmentPayload>(
      `${connection.baseUrl}/api/v1/courses/${courseId}/assignments?per_page=100`,
      connection,
    )
    return assignments
      .filter(
        (assignment) =>
          typeof assignment.id === "number" &&
          typeof assignment.name === "string",
      )
      .map((assignment) => ({
        id: assignment.id,
        name: assignment.name,
        pointsPossible: assignment.points_possible ?? null,
        hasRubric:
          Array.isArray(assignment.rubric) && assignment.rubric.length > 0,
      }))
  }

  async getAssignment(courseId: number, assignmentId: number) {
    const connection = this.#requireConnection()
    return this.#request<CanvasAssignmentPayload>(
      `${connection.baseUrl}/api/v1/courses/${courseId}/assignments/${assignmentId}`,
      connection.accessToken,
      connection.baseUrl,
    )
  }

  async importSubmissions(
    courseId: number,
    assignmentId: number,
  ): Promise<CanvasSubmissionImport[]> {
    const connection = this.#requireConnection()
    const [users, submissions] = await Promise.all([
      this.#requestAll<CanvasUserPayload>(
        `${connection.baseUrl}/api/v1/courses/${courseId}/users?enrollment_type%5B%5D=student&enrollment_state%5B%5D=active&per_page=100`,
        connection,
      ),
      this.#requestAll<CanvasSubmissionPayload>(
        `${connection.baseUrl}/api/v1/courses/${courseId}/assignments/${assignmentId}/submissions?include%5B%5D=submission_history&per_page=100`,
        connection,
      ),
    ])
    const submissionByUser = new Map(
      submissions
        .filter((submission) => typeof submission.user_id === "number")
        .map((submission) => [submission.user_id, submission]),
    )
    const roster = users
      .filter(
        (user) => typeof user.id === "number" && typeof user.name === "string",
      )
      .sort((a, b) =>
        (a.sortable_name ?? a.name).localeCompare(b.sortable_name ?? b.name),
      )

    const imported: CanvasSubmissionImport[] = []
    for (const user of roster) {
      imported.push(
        await this.#normalizeSubmission(
          user,
          submissionByUser.get(user.id),
          connection,
        ),
      )
    }
    return imported
  }

  async #normalizeSubmission(
    user: CanvasUserPayload,
    submission: CanvasSubmissionPayload | undefined,
    connection: StoredCanvasConnection,
  ): Promise<CanvasSubmissionImport> {
    const attemptCount = Math.max(
      submission?.attempt ?? 0,
      ...(submission?.submission_history ?? []).map(
        (attempt) => attempt.attempt ?? 0,
      ),
    )
    const base = {
      studentDisplayName: user.name,
      externalStudentId: String(user.id),
      externalSubmissionId:
        typeof submission?.id === "number" ? String(submission.id) : null,
      identityStatus: "verified" as const,
      attemptCount,
      submittedAt: submission?.submitted_at ?? null,
      isOversized: false,
    }
    if (
      !submission ||
      submission.workflow_state === "unsubmitted" ||
      !submission.submitted_at
    ) {
      return {
        ...base,
        importStatus: "missing",
        submissionType: "missing",
        originalFilename: "",
        extractionStatus: "not_applicable",
        extractionFailureReason: null,
        extractedText: null,
        extractedCharCount: null,
        artifact: null,
      }
    }

    if (submission.submission_type === "online_text_entry" && submission.body) {
      const extractedText = htmlToText(submission.body)
      return {
        ...base,
        importStatus: "ready",
        submissionType: "text",
        originalFilename: "Canvas text entry",
        extractionStatus: "success",
        extractionFailureReason: null,
        extractedText,
        extractedCharCount: extractedText.length,
        artifact: null,
      }
    }

    const attachment = submission.attachments?.find(
      (item) =>
        item.content_type === "application/pdf" ||
        item.filename.toLowerCase().endsWith(".pdf"),
    )
    if (!attachment) {
      return {
        ...base,
        importStatus: "failed",
        submissionType: "unsupported",
        originalFilename: submission.attachments?.[0]?.filename ?? "",
        extractionStatus: "failed",
        extractionFailureReason: "unsupported_submission_type",
        extractedText: null,
        extractedCharCount: null,
        artifact: null,
      }
    }
    if ((attachment.size ?? 0) > MAX_PDF_BYTES) {
      return {
        ...base,
        importStatus: "failed",
        submissionType: "pdf",
        originalFilename: attachment.filename,
        extractionStatus: "failed",
        extractionFailureReason: "file_too_large",
        extractedText: null,
        extractedCharCount: null,
        isOversized: true,
        artifact: null,
      }
    }

    try {
      if (new URL(attachment.url).origin !== connection.baseUrl) {
        throw new CanvasError(
          502,
          "invalid_canvas_response",
          "Canvas returned an unsafe attachment URL",
        )
      }
      const response = await this.#fetch(attachment.url, connection.accessToken)
      const data = Buffer.from(await response.arrayBuffer())
      if (data.length > MAX_PDF_BYTES) {
        return {
          ...base,
          importStatus: "failed",
          submissionType: "pdf",
          originalFilename: attachment.filename,
          extractionStatus: "failed",
          extractionFailureReason: "file_too_large",
          extractedText: null,
          extractedCharCount: null,
          isOversized: true,
          artifact: null,
        }
      }
      if (
        response.headers.get("content-type")?.split(";", 1)[0] !==
          "application/pdf" ||
        !data.subarray(0, 5).equals(Buffer.from("%PDF-"))
      ) {
        throw new CanvasError(
          502,
          "invalid_canvas_response",
          "Canvas returned an invalid PDF attachment",
        )
      }
      const extraction = await extractPdfText(data)
      if (extraction.status === "failed") {
        return {
          ...base,
          importStatus: "failed",
          submissionType: "pdf",
          originalFilename: attachment.filename,
          extractionStatus: "failed",
          extractionFailureReason: extraction.reason,
          extractedText: null,
          extractedCharCount: null,
          artifact: {
            data,
            contentType: "application/pdf",
            filename: attachment.filename,
          },
        }
      }
      return {
        ...base,
        importStatus: "ready",
        submissionType: "pdf",
        originalFilename: attachment.filename,
        extractionStatus: "success",
        extractionFailureReason: null,
        extractedText: extraction.text,
        extractedCharCount: extraction.charCount,
        artifact: {
          data,
          contentType: "application/pdf",
          filename: attachment.filename,
        },
      }
    } catch {
      return {
        ...base,
        importStatus: "failed",
        submissionType: "pdf",
        originalFilename: attachment.filename,
        extractionStatus: "failed",
        extractionFailureReason: "attachment_download_failed",
        extractedText: null,
        extractedCharCount: null,
        artifact: null,
      }
    }
  }

  #requireConnection() {
    if (!this.#connection) {
      throw new CanvasError(
        409,
        "canvas_not_connected",
        "Connect Canvas before continuing",
      )
    }
    return this.#connection
  }

  async #requestAll<T,>(
    initialUrl: string,
    connection: Pick<StoredCanvasConnection, "baseUrl" | "accessToken">,
  ): Promise<T[]> {
    const results: T[] = []
    let url: string | null = initialUrl
    for (let page = 0; url && page < 20; page += 1) {
      const response = await this.#fetch(url, connection.accessToken)
      const payload = (await response.json()) as unknown
      if (!Array.isArray(payload)) {
        throw new CanvasError(
          502,
          "invalid_canvas_response",
          "Canvas returned an unexpected list",
        )
      }
      results.push(...payload as T[])
      const next = nextLink(response.headers.get("Link"))
      if (next && new URL(next).origin !== connection.baseUrl) {
        throw new CanvasError(
          502,
          "invalid_canvas_response",
          "Canvas returned an unsafe pagination link",
        )
      }
      url = next
    }
    if (url) {
      throw new CanvasError(
        502,
        "canvas_pagination_limit",
        "Canvas returned too many result pages",
      )
    }
    return results
  }

  async #request<T,>(
    url: string,
    accessToken: string,
    expectedOrigin: string,
  ): Promise<T> {
    if (new URL(url).origin !== expectedOrigin) {
      throw new CanvasError(
        400,
        "invalid_canvas_url",
        "Canvas request changed origin",
      )
    }
    const response = await this.#fetch(url, accessToken)
    return (await response.json()) as T
  }

  async #fetch(url: string, accessToken: string): Promise<Response> {
    let response: Response
    try {
      response = await fetch(url, {
        headers: { Authorization: `Bearer ${accessToken}` },
        signal: AbortSignal.timeout(15_000),
      })
    } catch {
      throw new CanvasError(
        502,
        "canvas_unreachable",
        "B2TA could not reach Canvas",
      )
    }

    if (response.status === 401) {
      throw new CanvasError(
        401,
        "canvas_token_rejected",
        "Canvas rejected this access token",
      )
    }
    if (!response.ok) {
      throw new CanvasError(
        502,
        "canvas_request_failed",
        `Canvas request failed with status ${response.status}`,
      )
    }
    return response
  }
}
import { extractPdfText } from "./pdf.js"
