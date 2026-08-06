import express, { type NextFunction, type Request, type Response } from "express"

import { MemoryStore, type RubricInput } from "./store.js"

type ErrorBody = {
  error: {
    code: string
    message: string
  }
}

function sendError(response: Response, status: number, code: string, message: string) {
  const body: ErrorBody = { error: { code, message } }
  return response.status(status).json(body)
}

function isRubricInput(value: unknown): value is RubricInput {
  if (typeof value !== "object" || value === null || !("criteria" in value)) return false
  const criteria = (value as { criteria?: unknown }).criteria
  if (!Array.isArray(criteria) || criteria.length === 0) return false

  return criteria.every((criterion) => {
    if (typeof criterion !== "object" || criterion === null) return false
    const candidate = criterion as { title?: unknown; performanceLevels?: unknown }
    if (typeof candidate.title !== "string" || candidate.title.trim().length === 0) return false
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

export function createApp(store = new MemoryStore()) {
  const app = express()

  app.disable("x-powered-by")
  app.use(express.json({ limit: "1mb" }))
  app.use((request, response, next) => {
    response.setHeader("Access-Control-Allow-Origin", process.env.WEB_ORIGIN ?? "http://localhost:8443")
    response.setHeader("Access-Control-Allow-Headers", "Content-Type")
    response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
    if (request.method === "OPTIONS") return response.sendStatus(204)
    next()
  })

  app.get("/api/health", (_request, response) => {
    response.json({ status: "ok" })
  })

  app.get("/api/sessions", (_request, response) => {
    response.json(store.listSessions())
  })

  app.post("/api/sessions", (request, response) => {
    const name = typeof request.body?.name === "string" ? request.body.name.trim() : ""
    if (name.length === 0 || name.length > 200) {
      return sendError(response, 400, "invalid_session_name", "Session name must be 1 to 200 characters")
    }

    return response.status(201).json(store.createSession(name))
  })

  app.get("/api/sessions/:id", (request, response) => {
    const session = store.getSession(request.params.id)
    if (!session) return sendError(response, 404, "session_not_found", "Session not found")
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
    if (!rubric) return sendError(response, 404, "rubric_not_found", "Rubric not found")
    return response.json(rubric)
  })

  app.put("/api/sessions/:id/rubric", (request, response) => {
    if (!store.getSession(request.params.id)) {
      return sendError(response, 404, "session_not_found", "Session not found")
    }
    if (!isRubricInput(request.body)) {
      return sendError(response, 400, "invalid_rubric", "Rubric requires at least one titled criterion")
    }

    return response.json(store.saveRubric(request.params.id, request.body))
  })

  app.get("/api/sessions/:id/submissions", (request, response) => {
    if (!store.getSession(request.params.id)) {
      return sendError(response, 404, "session_not_found", "Session not found")
    }
    return response.json(store.listSubmissions(request.params.id))
  })

  app.use((_request, response) => sendError(response, 404, "route_not_found", "Route not found"))
  app.use((error: unknown, _request: Request, response: Response, _next: NextFunction) => {
    console.error(error)
    return sendError(response, 500, "internal_error", "Unexpected server error")
  })

  return app
}
