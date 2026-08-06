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
  sourceFormat: "manual"
  criteria: Criterion[]
  createdAt: string
  updatedAt: string
}

export type Submission = {
  id: string
  sessionId: string
  storageKey: string
  originalFilename: string
  studentDisplayName: string
  externalSubmissionId: string | null
  identityStatus: "verified" | "unverified" | "disambiguation_required"
  extractionStatus: "pending" | "success" | "failed"
  extractionFailureReason: string | null
  extractedText: string | null
  extractedCharCount: number | null
  isOversized: boolean
  position: number
  createdAt: string
}
