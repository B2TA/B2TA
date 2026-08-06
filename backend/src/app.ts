import express, {
  type NextFunction,
  type Request,
  type Response,
} from "express"

import { CanvasAdapter, CanvasError } from "./canvas.js"
import { MemoryStore, type RubricInput } from "./store.js"

type ErrorBody = {
  error: {
    code: string
    message: string
  }
}

function sendError(
  response: Response,
  status: number,
  code: string,
  message: string,
) {
  const body: ErrorBody = { error: { code, message } }
  return response.status(status).json(body)
}

function isRubricInput(value: unknown): value is RubricInput {
  if (typeof value !== "object" || value === null || !("criteria" in value))
    return false
  const criteria = (value as { criteria?: unknown }).criteria
  if (!Array.isArray(criteria) || criteria.length === 0) return false

  return criteria.every((criterion) => {
    if (typeof criterion !== "object" || criterion === null) return false
    const candidate = criterion as {
      title?: unknown
      performanceLevels?: unknown
    }
    if (
      typeof candidate.title !== "string" ||
      candidate.title.trim().length === 0
    )
      return false
    if (candidate.performanceLevels === undefined) return true
    if (!Array.isArray(candidate.performanceLevels)) return false
    return candidate.performanceLevels.every(
      (level) =>
        typeof level === "object" &&
        level !== null &&
        typeof (level as { label?: unknown }).label === "string" &&
        (level as { label: string }).label.trim().length > 0,
    )
  })
}

function parseNumericId(value: unknown): number | null {
  if (typeof value !== "string" && typeof value !== "number") return null
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}

export function createApp(
  store = new MemoryStore(),
  canvas = new CanvasAdapter(),
) {
  const app = express()

  app.disable("x-powered-by")
  app.use(express.json({ limit: "1mb" }))
  app.use((request, response, next) => {
    response.setHeader(
      "Access-Control-Allow-Origin",
      process.env.WEB_ORIGIN ?? "http://localhost:8443",
    )
    response.setHeader("Access-Control-Allow-Headers", "Content-Type")
    response.setHeader(
      "Access-Control-Allow-Methods",
      "GET, POST, PUT, DELETE, OPTIONS",
    )
    if (request.method === "OPTIONS") return response.sendStatus(204)
    next()
  })

  app.get("/api/health", (_request, response) => {
    response.json({ status: "ok" })
  })

  app.post("/api/canvas/connection", async (request, response) => {
    const baseUrl =
      typeof request.body?.baseUrl === "string" ? request.body.baseUrl : ""
    const accessToken =
      typeof request.body?.accessToken === "string"
        ? request.body.accessToken
        : ""
    const connection = await canvas.connect(baseUrl, accessToken)
    return response.status(201).json(connection)
  })

  app.get("/api/canvas/courses", async (_request, response) => {
    return response.json(await canvas.listCourses())
  })

  app.get(
    "/api/canvas/courses/:courseId/assignments",
    async (request, response) => {
      const courseId = parseNumericId(request.params.courseId)
      if (!courseId)
        return sendError(
          response,
          400,
          "invalid_course_id",
          "Course id must be a positive integer",
        )
      return response.json(await canvas.listAssignments(courseId))
    },
  )

  app.get("/api/sessions", (_request, response) => {
    response.json(store.listSessions())
  })

  app.post("/api/sessions", (request, response) => {
    const name =
      typeof request.body?.name === "string" ? request.body.name.trim() : ""
    if (name.length === 0 || name.length > 200) {
      return sendError(
        response,
        400,
        "invalid_session_name",
        "Session name must be 1 to 200 characters",
      )
    }

    return response.status(201).json(store.createSession(name))
  })

  app.get("/api/sessions/:id", (request, response) => {
    const session = store.getSession(request.params.id)
    if (!session)
      return sendError(response, 404, "session_not_found", "Session not found")
    return response.json(session)
  })

  app.delete("/api/sessions/:id", (request, response) => {
    if (!store.deleteSession(request.params.id)) {
      return sendError(response, 404, "session_not_found", "Session not found")
    }
    return response.sendStatus(204)
  })

  app.get("/api/sessions/:id/rubric", (request, response) => {
    if (!store.getSession(request.params.id)) {
      return sendError(response, 404, "session_not_found", "Session not found")
    }

    const rubric = store.getRubric(request.params.id)
    if (!rubric)
      return sendError(response, 404, "rubric_not_found", "Rubric not found")
    return response.json(rubric)
  })

  app.put("/api/sessions/:id/rubric", (request, response) => {
    if (!store.getSession(request.params.id)) {
      return sendError(response, 404, "session_not_found", "Session not found")
    }
    if (!isRubricInput(request.body)) {
      return sendError(
        response,
        400,
        "invalid_rubric",
        "Rubric requires at least one titled criterion",
      )
    }

    return response.json(store.saveRubric(request.params.id, request.body))
  })

  app.post("/api/sessions/:id/canvas/import", async (request, response) => {
    if (!store.getSession(request.params.id)) {
      return sendError(response, 404, "session_not_found", "Session not found")
    }
    const courseId = parseNumericId(request.body?.courseId)
    const assignmentId = parseNumericId(request.body?.assignmentId)
    if (!courseId || !assignmentId) {
      return sendError(
        response,
        400,
        "invalid_canvas_source",
        "Choose a Canvas course and assignment",
      )
    }

    const assignment = await canvas.getAssignment(courseId, assignmentId)
    if (!Array.isArray(assignment.rubric) || assignment.rubric.length === 0) {
      return sendError(
        response,
        422,
        "canvas_rubric_missing",
        "This Canvas assignment has no rubric",
      )
    }

    const rubric = store.saveRubric(request.params.id, {
      sourceFormat: "canvas",
      criteria: assignment.rubric.map((criterion) => ({
        title: criterion.description,
        description: criterion.long_description ?? "",
        maxPoints: criterion.points ?? null,
        performanceLevels: (criterion.ratings ?? []).map((rating) => ({
          label: rating.description,
          description: rating.long_description ?? "",
          points: rating.points ?? null,
        })),
      })),
    })
    return response.json(rubric)
  })

  app.post(
    "/api/sessions/:id/canvas/submissions/import",
    async (request, response) => {
      if (!store.getSession(request.params.id)) {
        return sendError(
          response,
          404,
          "session_not_found",
          "Session not found",
        )
      }
      const courseId = parseNumericId(request.body?.courseId)
      const assignmentId = parseNumericId(request.body?.assignmentId)
      if (!courseId || !assignmentId) {
        return sendError(
          response,
          400,
          "invalid_canvas_source",
          "Choose a Canvas course and assignment",
        )
      }

      const imported = await canvas.importSubmissions(courseId, assignmentId)
      const submissions = store.saveSubmissionBatch(request.params.id, imported)
      return response.json({
        summary: {
          totalStudents: submissions.length,
          imported: submissions.filter((item) => item.importStatus === "ready")
            .length,
          missing: submissions.filter((item) => item.importStatus === "missing")
            .length,
          failed: submissions.filter((item) => item.importStatus === "failed")
            .length,
          multipleAttempts: submissions.filter((item) => item.attemptCount > 1)
            .length,
        },
        submissions,
      })
    },
  )

  app.get("/api/sessions/:id/submissions", (request, response) => {
    if (!store.getSession(request.params.id)) {
      return sendError(response, 404, "session_not_found", "Session not found")
    }
    return response.json(store.listSubmissions(request.params.id))
  })

  app.get(
    "/api/sessions/:id/submissions/:submissionId/artifact",
    (request, response) => {
      if (!store.getSession(request.params.id)) {
        return sendError(
          response,
          404,
          "session_not_found",
          "Session not found",
        )
      }
      const artifact = store.getSubmissionArtifact(
        request.params.id,
        request.params.submissionId,
      )
      if (!artifact) {
        return sendError(
          response,
          404,
          "artifact_not_found",
          "Submission artifact not found",
        )
      }
      const filename = artifact.filename.replace(
        /[^\x20-\x21\x23-\x5b\x5d-\x7e]/g,
        "_",
      )
      response.setHeader("Content-Type", artifact.contentType)
      response.setHeader(
        "Content-Disposition",
        `inline; filename="${filename}"`,
      )
      return response.send(artifact.data)
    },
  )

  app.use((_request, response) =>
    sendError(response, 404, "route_not_found", "Route not found"),
  )
  app.use(
    (
      error: unknown,
      _request: Request,
      response: Response,
      _next: NextFunction,
    ) => {
      if (error instanceof CanvasError) {
        return sendError(response, error.status, error.code, error.message)
      }
      console.error(error)
      return sendError(
        response,
        500,
        "internal_error",
        "Unexpected server error",
      )
    },
  )

  return app
}
