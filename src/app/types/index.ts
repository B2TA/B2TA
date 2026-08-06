/**
 * Shared TypeScript interfaces matching backend DTOs.
 * These define the frontend view of B2TA's canonical grading model.
 */

// --- Core Identity ---

export interface TaUser {
  id: string;
  cognitoSub: string;
  email: string;
  createdAt: string;
}

// --- Session ---

export interface Session {
  id: string;
  taId: string;
  name: string;
  reviewConfirmedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateSessionRequest {
  name: string;
}

// --- Rubric ---

export interface Rubric {
  id: string;
  sessionId: string;
  storageKey: string | null;
  sourceFormat: "pdf" | "csv" | "xlsx" | "manual" | "canvas" | null;
  criteria: Criterion[];
  createdAt: string;
  updatedAt: string;
}

export interface Criterion {
  id: string;
  rubricId: string;
  title: string;
  description: string;
  maxPoints: number | null;
  displayColor: string;
  position: number;
  requiresCompletion: boolean;
  performanceLevels: PerformanceLevel[];
  createdAt: string;
}

export interface PerformanceLevel {
  id: string;
  criterionId: string;
  label: string;
  description: string;
  points: number | null;
  position: number;
}

// --- Submission ---

export type IdentityStatus = "verified" | "unverified" | "disambiguation_required";

export type ExtractionStatus = "pending" | "success" | "failed";

export type ExtractionFailureReason =
  | "unreadable_file"
  | "password_protected"
  | "no_extractable_text"
  | "extraction_timeout";

export interface Submission {
  id: string;
  sessionId: string;
  storageKey: string;
  originalFilename: string;
  studentDisplayName: string;
  externalSubmissionId: string | null;
  identityStatus: IdentityStatus;
  extractionStatus: ExtractionStatus;
  extractionFailureReason: ExtractionFailureReason | null;
  extractedText: string | null;
  extractedCharCount: number | null;
  isOversized: boolean;
  position: number;
  createdAt: string;
}

// --- AI Matches ---

export type MatchState = "suggested" | "confirmed" | "rejected";

export interface SuggestedMatch {
  id: string;
  submissionId: string;
  criterionId: string;
  passageStart: number;
  passageEnd: number;
  rationale: string;
  confidence: number;
  matchState: MatchState;
  isStale: boolean;
  discardReason: string | null;
  createdAt: string;
}

export type ConfirmedMatchOrigin = "ta_confirmed" | "ta_authored";

export interface ConfirmedMatch {
  id: string;
  submissionId: string;
  criterionId: string;
  passageStart: number;
  passageEnd: number;
  rationale: string;
  confidence: number | null;
  origin: ConfirmedMatchOrigin;
  sourceMatchId: string | null;
  createdAt: string;
}

// --- Grading ---

export interface GradingRecord {
  id: string;
  submissionId: string;
  overallFeedback: string;
  criterionScores: CriterionScore[];
  confirmedMatches: ConfirmedMatch[];
  suggestedMatches: SuggestedMatch[];
  savedAt: string | null;
  createdAt: string;
}

export interface CriterionScore {
  id: string;
  gradingRecordId: string;
  criterionId: string;
  selectedLevelId: string | null;
  overridePoints: number | null;
  criterionFeedback: string;
}

export interface SaveGradingRequest {
  overallFeedback: string;
  criterionScores: Array<{
    criterionId: string;
    selectedLevelId: string | null;
    overridePoints: number | null;
    criterionFeedback: string;
  }>;
  confirmedMatches: Array<{
    criterionId: string;
    passageStart: number;
    passageEnd: number;
    rationale: string;
    confidence: number | null;
    origin: ConfirmedMatchOrigin;
    sourceMatchId: string | null;
  }>;
}

// --- Async Jobs ---

export type JobType = "rubric_parse" | "submission_ingest" | "match_analysis";

export type JobStatus = "pending" | "in_progress" | "complete" | "failed";

export interface AsyncJob {
  id: string;
  sessionId: string;
  jobType: JobType;
  status: JobStatus;
  progressCurrent: number;
  progressTotal: number;
  failureReason: string | null;
  createdAt: string;
  updatedAt: string;
}

// --- Upload ---

export interface UploadUrlResponse {
  uploadUrl: string;
  objectKey: string;
}

export interface BatchUploadUrlsResponse {
  uploads: UploadUrlResponse[];
}

// --- Review ---

export interface ReviewSubmissionSummary {
  submissionId: string;
  studentDisplayName: string;
  criterionScores: Array<{
    criterionId: string;
    points: number | null;
    levelLabel: string | null;
  }>;
  total: number | null;
  maxPossible: number | null;
  flags: ReviewFlag[];
}

export type ReviewFlag =
  | "incomplete_grading"
  | "extraction_failed"
  | "oversized"
  | "unverified_identity"
  | "disambiguation_required"
  | "manual_overrides";

export interface ReviewData {
  sessionId: string;
  submissions: ReviewSubmissionSummary[];
  reviewConfirmedAt: string | null;
  flaggedCount: number;
  unflaggedCount: number;
}

// --- Export ---

export type ExportFormat = "generic" | "canvas";

export interface ExportResponse {
  downloadUrl: string;
}

// --- Comment Assistant ---

export interface CommentSuggestion {
  text: string;
}

export interface CommentSuggestResponse {
  suggestions: CommentSuggestion[];
}
