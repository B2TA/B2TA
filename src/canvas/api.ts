/**
 * Client for the backend's Canvas endpoints.
 *
 * The browser never calls Canvas directly — the API token lives server-side in Secrets
 * Manager, and every Canvas call is proxied through our backend.
 */

import type { CriterionSelection, RubricView, Student, SubmissionView, SyncResult } from './types'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'

/** An error carrying the backend's message so the TA sees the real reason. */
export class ApiError extends Error {
  status: number
  /** Criteria the backend named as blocking, when a sync was rejected. */
  criteria: string[]
  retryable: boolean

  constructor(message: string, status: number, criteria: string[] = [], retryable = false) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.criteria = criteria
    this.retryable = retryable
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...(options.headers as Record<string, string>),
      },
    })
  } catch (cause) {
    throw new ApiError(
      "Could not reach the grading service. Check that the backend is running.",
      0,
      [],
      true,
    )
  }

  if (!response.ok) {
    // The backend returns a structured body for Canvas and validation failures; fall
    // back to the status text when it does not.
    let message = `Request failed (${response.status})`
    let criteria: string[] = []
    let retryable = response.status >= 500

    try {
      const body = await response.json()
      if (body.message) message = body.message
      if (Array.isArray(body.criteria)) criteria = body.criteria
      if (typeof body.retryable === 'boolean') retryable = body.retryable
    } catch {
      // Body was not JSON — keep the status-based message.
    }

    throw new ApiError(message, response.status, criteria, retryable)
  }

  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

/** The assignment's rubric. `hasRubric: false` means show the empty state. */
export function fetchRubric(assignmentId: string): Promise<RubricView> {
  return request<RubricView>(`/canvas/assignments/${assignmentId}/rubric`)
}

/** The grading queue, already filtered of unsubmitted entries. */
export function fetchQueue(assignmentId: string): Promise<Student[]> {
  return request<Student[]>(`/canvas/assignments/${assignmentId}/queue`)
}

/** One student's submission text, evidence spans, and any existing scores. */
export function fetchSubmission(assignmentId: string, userId: string): Promise<SubmissionView> {
  return request<SubmissionView>(`/canvas/assignments/${assignmentId}/submissions/${userId}`)
}

/** Writes the TA's scores to the Canvas gradebook. */
export function syncToCanvas(
  assignmentId: string,
  userId: string,
  selections: CriterionSelection[],
  comment: string,
): Promise<SyncResult> {
  return request<SyncResult>(
    `/canvas/assignments/${assignmentId}/submissions/${userId}/sync`,
    {
      method: 'POST',
      body: JSON.stringify({ selections, comment }),
    },
  )
}
