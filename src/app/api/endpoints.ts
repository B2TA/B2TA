/**
 * One function per API route.
 *
 * Paths live only here, so a route change is a single edit and no component builds a URL by hand.
 */

import api from "./client";
import type {
  AsyncJob,
  CommentSuggestRequest,
  CommentSuggestResponse,
  ConfirmedMatch,
  CreateManualMatchRequest,
  CreateSessionRequest,
  ExportResult,
  GradingRecord,
  JobCreated,
  Me,
  ReviewData,
  Rubric,
  SaveGradingRequest,
  SaveRubricRequest,
  Session,
  Submission,
  UploadUrl,
} from "../types";

// --- Identity ---

export const getMe = () => api.get<Me>("/me");

// --- Sessions ---

export const listSessions = () => api.get<Session[]>("/sessions");

export const getSession = (sessionId: string) => api.get<Session>(`/sessions/${sessionId}`);

export const createSession = (body: CreateSessionRequest) =>
  api.post<Session>("/sessions", body);

export const deleteSession = (sessionId: string) =>
  api.delete<void>(`/sessions/${sessionId}`);

// --- Rubric ---

/** Resolves to null when the session has no rubric yet (the API answers 204). */
export const getRubric = (sessionId: string) =>
  api.get<Rubric | undefined>(`/sessions/${sessionId}/rubric`).then((r) => r ?? null);

export const saveRubric = (sessionId: string, body: SaveRubricRequest) =>
  api.put<Rubric>(`/sessions/${sessionId}/rubric`, body);

export const exportRubric = (sessionId: string) =>
  api.post<ExportResult>(`/sessions/${sessionId}/rubric/export`);

export const getRubricUploadUrl = (sessionId: string, filename: string) =>
  api.post<UploadUrl>(`/sessions/${sessionId}/rubric/upload-url`, { filename });

export const parseRubric = (sessionId: string, objectKey: string) =>
  api.post<JobCreated>(`/sessions/${sessionId}/rubric/parse`, { objectKey });

// --- Submissions ---

export const listSubmissions = (sessionId: string) =>
  api.get<Submission[]>(`/sessions/${sessionId}/submissions`);

export const updateSubmissionIdentity = (
  sessionId: string,
  submissionId: string,
  studentDisplayName: string
) =>
  api.put<Submission>(`/sessions/${sessionId}/submissions/${submissionId}/identity`, {
    studentDisplayName,
  });

export const confirmSubmissionIdentities = (sessionId: string) =>
  api.post<Submission[]>(`/sessions/${sessionId}/submissions/confirm`);

export const getSubmissionUploadUrls = (sessionId: string, filenames: string[]) =>
  api.post<{ uploads: UploadUrl[] }>(`/sessions/${sessionId}/submissions/upload-urls`, {
    filenames,
  });

export const ingestSubmissions = (sessionId: string, objectKeys: string[]) =>
  api.post<JobCreated>(`/sessions/${sessionId}/submissions/ingest`, { objectKeys });

// --- Grading ---

export const getGradingRecord = (sessionId: string, submissionId: string) =>
  api.get<GradingRecord>(`/sessions/${sessionId}/submissions/${submissionId}/grading`);

export const saveGradingRecord = (
  sessionId: string,
  submissionId: string,
  body: SaveGradingRequest
) =>
  api.put<GradingRecord>(`/sessions/${sessionId}/submissions/${submissionId}/grading`, body, {
    // Requirement 14.12 ends a save that has not answered in 30 seconds and offers a retry.
    timeoutMs: 30_000,
  });

// --- Matches ---

export const confirmMatch = (sessionId: string, submissionId: string, matchId: string) =>
  api.post<ConfirmedMatch>(
    `/sessions/${sessionId}/submissions/${submissionId}/matches/${matchId}/confirm`
  );

export const rejectMatch = (sessionId: string, submissionId: string, matchId: string) =>
  api.post<void>(
    `/sessions/${sessionId}/submissions/${submissionId}/matches/${matchId}/reject`
  );

export const createManualMatch = (
  sessionId: string,
  submissionId: string,
  body: CreateManualMatchRequest
) =>
  api.post<ConfirmedMatch>(
    `/sessions/${sessionId}/submissions/${submissionId}/matches/manual`,
    body
  );

export const deleteConfirmedMatch = (
  sessionId: string,
  submissionId: string,
  confirmedMatchId: string
) =>
  api.delete<void>(
    `/sessions/${sessionId}/submissions/${submissionId}/matches/${confirmedMatchId}`
  );

// --- Analysis ---

export const analyzeSubmission = (sessionId: string, submissionId: string, force = false) =>
  api.post<JobCreated>(
    `/sessions/${sessionId}/submissions/${submissionId}/analyze?force=${force}`
  );

export const reanalyzeCriterion = (
  sessionId: string,
  submissionId: string,
  criterionId: string
) =>
  api.post<JobCreated>(
    `/sessions/${sessionId}/submissions/${submissionId}/reanalyze/${criterionId}`
  );

// --- Comments ---

export const suggestComments = (
  sessionId: string,
  submissionId: string,
  body: CommentSuggestRequest = {}
) =>
  api.post<CommentSuggestResponse>(
    `/sessions/${sessionId}/submissions/${submissionId}/comments/suggest`,
    body,
    // The server's own budget is 15 seconds; allow a little beyond it so a 504 from the API is
    // what the user sees rather than a client-side abort with a vaguer message.
    { timeoutMs: 20_000 }
  );

// --- Review ---

export const getReview = (sessionId: string) =>
  api.get<ReviewData>(`/sessions/${sessionId}/review`);

export const confirmReview = (sessionId: string) =>
  api.post<ReviewData>(`/sessions/${sessionId}/review/confirm`);

// --- Export ---

export const exportGrades = (sessionId: string, format: "generic" | "canvas") =>
  api.post<ExportResult>(`/sessions/${sessionId}/export/${format}`, undefined, {
    // Requirement 16.8 gives a 150-submission export 30 seconds.
    timeoutMs: 40_000,
  });

// --- Jobs ---

export const getJob = (jobId: string) => api.get<AsyncJob>(`/jobs/${jobId}`);
