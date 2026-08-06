/**
 * Wire types for the Grading API.
 *
 * These mirror the DTOs in `backend/common/src/main/java/com/b2ta/common/dto`. Enum-valued fields
 * are string unions using the same lower-snake-case vocabulary the backend serializes (its enums
 * carry an explicit `dbValue` exposed through `@JsonValue`), so a value that appears here can be
 * compared directly against what the API returns.
 */

// --- Identity ---

export interface Me {
  taId: string;
  email: string;
}

// --- Session ---

export interface Session {
  id: string;
  name: string;
  reviewConfirmedAt: string | null;
  submissionCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateSessionRequest {
  name: string;
}

// --- Rubric ---

export type RubricSourceFormat = "pdf" | "csv" | "xlsx" | "manual";

export interface PerformanceLevel {
  id: string | null;
  label: string;
  description: string | null;
  /** Null means the value could not be resolved from the source file and needs TA input. */
  points: number | null;
  position: number;
}

export interface Criterion {
  id: string | null;
  title: string;
  description: string | null;
  maxPoints: number | null;
  displayColor: string;
  position: number;
  requiresCompletion: boolean;
  performanceLevels: PerformanceLevel[];
}

export interface Rubric {
  id: string;
  sessionId: string;
  sourceFormat: RubricSourceFormat | null;
  createdAt: string;
  updatedAt: string;
  criteria: Criterion[];
}

export interface SaveRubricRequest {
  criteria: Criterion[];
}

// --- Submission ---

export type IdentityStatus = "verified" | "unverified" | "disambiguation_required";

export type ExtractionStatus = "pending" | "success" | "failed" | "oversized";

export interface Submission {
  id: string;
  originalFilename: string;
  studentDisplayName: string;
  canvasSubmissionId: string | null;
  identityStatus: IdentityStatus;
  extractionStatus: ExtractionStatus;
  extractionFailureReason: string | null;
  extractedCharCount: number | null;
  isOversized: boolean;
  position: number;
  createdAt: string;
}

export interface UploadUrl {
  filename: string;
  uploadUrl: string;
  objectKey: string;
}

// --- Matches ---

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
  createdAt: string;
}

/** `ta_confirmed` came from an AI suggestion; `ta_authored` was selected by the TA. */
export type ConfirmedMatchOrigin = "ta_confirmed" | "ta_authored";

export interface ConfirmedMatch {
  id: string;
  submissionId: string;
  criterionId: string;
  passageStart: number;
  passageEnd: number;
  rationale: string;
  /** Null for a TA-authored match, where confidence is not applicable. */
  confidence: number | null;
  origin: ConfirmedMatchOrigin;
  sourceMatchId: string | null;
  createdAt: string;
}

export interface CreateManualMatchRequest {
  criterionId: string;
  passageStart: number;
  passageEnd: number;
  rationale?: string;
}

// --- Analysis state ---

/**
 * Match_Engine state for one criterion.
 *
 * `complete` with zero matches is "no evidence found"; `unavailable` means analysis itself failed.
 * The two render differently, so they must not be collapsed.
 */
export type AnalysisState = "pending" | "in_progress" | "complete" | "unavailable";

export interface CriterionAnalysis {
  criterionId: string;
  state: AnalysisState;
  failureReason: string | null;
  analyzedCharCount: number | null;
}

// --- Grading ---

export interface CriterionScore {
  id: string | null;
  criterionId: string;
  selectedLevelId: string | null;
  overridePoints: number | null;
  criterionFeedback: string;
}

export interface GradingRecord {
  id: string | null;
  submissionId: string;
  studentDisplayName: string;
  overallFeedback: string;
  savedAt: string | null;
  criterionScores: CriterionScore[];
  /** Unconfirmed suggestions only; confirmed and rejected ones are not re-sent. */
  suggestedMatches: SuggestedMatch[];
  confirmedMatches: ConfirmedMatch[];
  criterionAnalysis: CriterionAnalysis[];
  /** Null when extraction failed. Match offsets index into this string. */
  extractedText: string | null;
  extractionStatus: ExtractionStatus;
  extractionFailureReason: string | null;
  isOversized: boolean;
  /** 1-based position in the batch. */
  position: number;
  batchSize: number;
  totalScore: number;
  maxScore: number;
  unscoredCriterionCount: number;
}

export interface SaveGradingRequest {
  overallFeedback: string;
  criterionScores: Array<{
    criterionId: string;
    selectedLevelId: string | null;
    overridePoints: number | null;
    criterionFeedback: string;
  }>;
  /**
   * Omit to leave confirmed matches untouched; send an empty array to clear them.
   * The dedicated match endpoints own the set when this field is absent.
   */
  confirmedMatches?: Array<{
    criterionId: string;
    passageStart: number;
    passageEnd: number;
    rationale: string;
    confidence: number | null;
    sourceMatchId: string | null;
  }>;
}

// --- Async jobs ---

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

export interface JobCreated {
  jobId: string;
}

// --- Review ---

export type ReviewFlag =
  | "incomplete_grading"
  | "extraction_failed"
  | "oversized"
  | "unverified_identity"
  | "disambiguation_required"
  | "manual_overrides";

export interface ReviewCriterionHeader {
  criterionId: string;
  title: string;
  maxPoints: number | null;
  position: number;
}

export interface ReviewCriterionScore {
  criterionId: string;
  criterionTitle: string;
  /** Null when unscored. Never zero for an unscored criterion. */
  points: number | null;
  selectedLevelLabel: string | null;
  overridden: boolean;
}

export interface ReviewSubmissionSummary {
  submissionId: string;
  studentDisplayName: string;
  position: number;
  totalPoints: number;
  maxPoints: number;
  unscoredCriterionCount: number;
  overrideCount: number;
  criterionScores: ReviewCriterionScore[];
  flags: ReviewFlag[];
}

export interface ReviewData {
  sessionId: string;
  /** Null means an export is still blocked. */
  reviewConfirmedAt: string | null;
  totalSubmissions: number;
  flaggedCount: number;
  unflaggedCount: number;
  criteria: ReviewCriterionHeader[];
  submissions: ReviewSubmissionSummary[];
}

// --- Export ---

export type ExportFormat = "generic" | "canvas";

export interface ExportResult {
  downloadUrl: string;
  filename: string;
}

// --- Comment assistant ---

export interface FeedbackSnippet {
  text: string;
  isAiGenerated: boolean;
}

export interface CommentSuggestResponse {
  snippets: FeedbackSnippet[];
}

export interface CommentSuggestRequest {
  criterionId?: string | null;
  currentDraft?: string;
}

// --- Errors ---

/** Error codes the API returns in `error.code`; the UI branches on these, not on message text. */
export type ApiErrorCode =
  | "UNAUTHORIZED"
  | "TOKEN_EXPIRED"
  | "NOT_FOUND"
  | "VALIDATION_FAILED"
  | "CONFLICT"
  | "INTERNAL_ERROR"
  | "INVALID_OVERRIDE"
  | "INVALID_PASSAGE_RANGE"
  | "PASSAGE_ALREADY_ASSOCIATED"
  | "NO_EXTRACTED_TEXT"
  | "ANALYSIS_UNAVAILABLE"
  | "NO_LEVELS_SELECTED"
  | "COMMENT_GENERATION_FAILED"
  | "COMMENT_GENERATION_TIMEOUT"
  | "REVIEW_NOT_CONFIRMED"
  | "EMPTY_SESSION"
  | "EXPORT_FAILED"
  | "RUBRIC_NOT_READY"
  | "BATCH_LIMIT_EXCEEDED";

export interface ApiErrorBody {
  error: {
    code: ApiErrorCode | string;
    message: string;
    details?: Record<string, unknown>;
  };
}
