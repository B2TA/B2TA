export type Session = {
  id: string
  taId: string
  name: string
  reviewConfirmedAt: string | null
  createdAt: string
  updatedAt: string
}

export type PerformanceLevel = {
  id: string
  criterionId: string
  label: string
  description: string
  points: number | null
  position: number
}

export type Criterion = {
  id: string
  rubricId: string
  title: string
  description: string
  maxPoints: number | null
  displayColor: string
  position: number
  requiresCompletion: boolean
  performanceLevels: PerformanceLevel[]
  createdAt: string
}

export type Rubric = {
  id: string
  sessionId: string
  storageKey: string | null
  sourceFormat: "manual" | "canvas"
  criteria: Criterion[]
  createdAt: string
  updatedAt: string
}

export type Submission = {
  id: string
  sessionId: string
  storageKey: string | null
  originalFilename: string
  studentDisplayName: string
  externalStudentId: string
  externalSubmissionId: string | null
  identityStatus: "verified" | "unverified" | "disambiguation_required"
  importStatus: "ready" | "missing" | "failed"
  submissionType: "pdf" | "text" | "missing" | "unsupported"
  attemptCount: number
  submittedAt: string | null
  artifactUrl: string | null
  extractionStatus: "pending" | "success" | "failed" | "not_applicable"
  extractionFailureReason: string | null
  extractedText: string | null
  extractedCharCount: number | null
  isOversized: boolean
  position: number
  createdAt: string
}

export type CriterionScore = {
  id: string
  gradingRecordId: string
  criterionId: string
  selectedLevelId: string | null
  overridePoints: number | null
  criterionFeedback: string
}

export type GradingRecord = {
  id: string
  submissionId: string
  overallFeedback: string
  criterionScores: CriterionScore[]
  savedAt: string
  createdAt: string
}

export type CanvasPublicationOutcome = {
  submissionId: string
  studentDisplayName: string
  status: "published" | "failed"
  error: string | null
  publishedAt: string | null
  gradingRecordSavedAt: string
}

export type SuggestedMatch = {
  id: string
  submissionId: string
  criterionId: string
  passageStart: number
  passageEnd: number
  rationale: string
  confidence: number
  createdAt: string
}
