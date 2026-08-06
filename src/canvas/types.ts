/**
 * Types mirroring the backend Canvas view DTOs.
 *
 * These deliberately match the shapes the marking view was already built against, so
 * swapping hardcoded constants for fetched data leaves rendering untouched.
 */

export interface Level {
  id: string | null
  pts: number
  label: string
  desc: string | null
}

export interface Criterion {
  /** Verbatim Canvas criterion id, e.g. `_1838`. Required for write-back. */
  id: string
  label: string
  description: string | null
  maxPts: number
  color: string
  bg: string
  border: string
  levels: Level[]
}

export interface RubricView {
  assignmentId: string
  assignmentName: string
  pointsPossible: number | null
  /** False when the assignment has no rubric — render the empty state, never demo data. */
  hasRubric: boolean
  criteria: Criterion[]
}

export interface Student {
  userId: string
  name: string
  submissionId: number | null
  submissionType: string | null
  workflowState: string
  position: number
  alreadyGraded: boolean
  attempt: number | null
}

export interface Paragraph {
  idx: number
  label: string | null
  text: string
  isTitle: boolean
}

/**
 * An evidence span.
 *
 * Offsets are paragraph-relative, not document-global, and are computed server-side.
 * `confirmed` is always false on arrival from the AI until the TA confirms it.
 */
export interface HSpan {
  id: string
  criterionId: string
  text: string
  confirmed: boolean
  tooltip: string
  paragraphIdx: number
  offsetInParagraph: number
}

export interface SubmissionView {
  userId: string
  studentName: string
  paragraphs: Paragraph[]
  spans: HSpan[]
  /** Suggested comment text keyed by criterion id, then by level label. */
  comments: Record<string, Record<string, string>>
  /** Set when text extraction failed; the TA can still grade manually. */
  extractionError: string | null
  alreadyGraded: boolean
  existingScores: Record<string, number | null>
}

export interface CriterionSelection {
  criterionId: string
  points: number | null
  ratingId?: string | null
  comments?: string | null
}

export interface SyncResult {
  synced: boolean
  userId: string
  canvasTotal: number | null
  syncedAt: string
  syncedBy: string
  criteriaWritten: number
  /** True when the write was recorded locally but never reached Canvas. */
  fixtureMode: boolean
}
