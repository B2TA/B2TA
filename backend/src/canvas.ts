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
