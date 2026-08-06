/**
 * React Query hooks over {@link ./endpoints}.
 *
 * Query keys are declared once in {@link queryKeys} so an invalidation cannot drift from the key it
 * is meant to match — the usual cause of a mutation that succeeds but leaves stale data on screen.
 */

import { useMutation, useQuery, useQueryClient, type UseQueryOptions } from "@tanstack/react-query";
import * as endpoints from "./endpoints";
import { ApiError } from "./client";
import type {
  AsyncJob,
  CommentSuggestRequest,
  CreateManualMatchRequest,
  GradingRecord,
  ReviewData,
  Rubric,
  SaveGradingRequest,
  SaveRubricRequest,
  Session,
  Submission,
} from "../types";

export const queryKeys = {
  me: ["me"] as const,
  sessions: ["sessions"] as const,
  session: (id: string) => ["sessions", id] as const,
  rubric: (id: string) => ["sessions", id, "rubric"] as const,
  submissions: (id: string) => ["sessions", id, "submissions"] as const,
  grading: (sessionId: string, submissionId: string) =>
    ["sessions", sessionId, "submissions", submissionId, "grading"] as const,
  review: (id: string) => ["sessions", id, "review"] as const,
  job: (id: string) => ["jobs", id] as const,
};

/**
 * Never retry an error the server has already decided on.
 *
 * A 400 or 404 will fail identically on a second attempt and only delays the message the user
 * needs; a network failure or 5xx is worth one more try.
 */
function retryOnlyTransient(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && !error.isRetryable) {
    return false;
  }
  return failureCount < 1;
}

// --- Identity ---

export function useMe(enabled = true) {
  return useQuery({
    queryKey: queryKeys.me,
    queryFn: endpoints.getMe,
    enabled,
    retry: false,
    staleTime: Infinity,
  });
}

// --- Sessions ---

export function useSessions() {
  return useQuery({
    queryKey: queryKeys.sessions,
    queryFn: endpoints.listSessions,
    retry: retryOnlyTransient,
  });
}

export function useSession(sessionId: string | undefined) {
  return useQuery({
    queryKey: queryKeys.session(sessionId ?? ""),
    queryFn: () => endpoints.getSession(sessionId!),
    enabled: Boolean(sessionId),
    retry: retryOnlyTransient,
  });
}

export function useCreateSession() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => endpoints.createSession({ name }),
    onSuccess: (created: Session) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.sessions });
      queryClient.setQueryData(queryKeys.session(created.id), created);
    },
  });
}

export function useDeleteSession() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (sessionId: string) => endpoints.deleteSession(sessionId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.sessions }),
  });
}

// --- Rubric ---

export function useRubric(sessionId: string | undefined) {
  return useQuery<Rubric | null>({
    queryKey: queryKeys.rubric(sessionId ?? ""),
    queryFn: () => endpoints.getRubric(sessionId!),
    enabled: Boolean(sessionId),
    retry: retryOnlyTransient,
  });
}

export function useSaveRubric(sessionId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: SaveRubricRequest) => endpoints.saveRubric(sessionId, body),
    onSuccess: (saved) => {
      queryClient.setQueryData(queryKeys.rubric(sessionId), saved);
      // Criterion ids can change, so any cached grading record now references stale criteria.
      queryClient.invalidateQueries({ queryKey: ["sessions", sessionId] });
    },
  });
}

// --- Submissions ---

export function useSubmissions(sessionId: string | undefined) {
  return useQuery<Submission[]>({
    queryKey: queryKeys.submissions(sessionId ?? ""),
    queryFn: () => endpoints.listSubmissions(sessionId!),
    enabled: Boolean(sessionId),
    retry: retryOnlyTransient,
  });
}

export function useUpdateIdentity(sessionId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { submissionId: string; studentDisplayName: string }) =>
      endpoints.updateSubmissionIdentity(sessionId, input.submissionId, input.studentDisplayName),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.submissions(sessionId) });
      queryClient.invalidateQueries({ queryKey: queryKeys.review(sessionId) });
    },
  });
}

// --- Grading ---

export function useGradingRecord(
  sessionId: string | undefined,
  submissionId: string | undefined,
  options?: Partial<UseQueryOptions<GradingRecord, ApiError>>
) {
  return useQuery<GradingRecord, ApiError>({
    queryKey: queryKeys.grading(sessionId ?? "", submissionId ?? ""),
    queryFn: () => endpoints.getGradingRecord(sessionId!, submissionId!),
    enabled: Boolean(sessionId && submissionId),
    retry: retryOnlyTransient,
    ...options,
  });
}

export function useSaveGrading(sessionId: string, submissionId: string) {
  const queryClient = useQueryClient();
  return useMutation<GradingRecord, ApiError, SaveGradingRequest>({
    mutationFn: (body) => endpoints.saveGradingRecord(sessionId, submissionId, body),
    // No automatic retry: a save is not idempotent from the user's point of view and Requirement
    // 14.6 asks for an explicit retry control instead.
    retry: false,
    onSuccess: (saved) => {
      queryClient.setQueryData(queryKeys.grading(sessionId, submissionId), saved);
      // A save clears the session's review confirmation, so the review data is now wrong.
      queryClient.invalidateQueries({ queryKey: queryKeys.review(sessionId) });
      queryClient.invalidateQueries({ queryKey: queryKeys.session(sessionId) });
    },
  });
}

// --- Matches ---

/** Match mutations all invalidate the grading record, which is the single source for highlights. */
function useMatchMutation<TInput>(
  sessionId: string,
  submissionId: string,
  mutationFn: (input: TInput) => Promise<unknown>
) {
  const queryClient = useQueryClient();
  return useMutation<unknown, ApiError, TInput>({
    mutationFn,
    retry: false,
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.grading(sessionId, submissionId),
      });
      queryClient.invalidateQueries({ queryKey: queryKeys.review(sessionId) });
    },
  });
}

export function useConfirmMatch(sessionId: string, submissionId: string) {
  return useMatchMutation<string>(sessionId, submissionId, (matchId) =>
    endpoints.confirmMatch(sessionId, submissionId, matchId)
  );
}

export function useRejectMatch(sessionId: string, submissionId: string) {
  return useMatchMutation<string>(sessionId, submissionId, (matchId) =>
    endpoints.rejectMatch(sessionId, submissionId, matchId)
  );
}

export function useCreateManualMatch(sessionId: string, submissionId: string) {
  return useMatchMutation<CreateManualMatchRequest>(sessionId, submissionId, (body) =>
    endpoints.createManualMatch(sessionId, submissionId, body)
  );
}

export function useDeleteConfirmedMatch(sessionId: string, submissionId: string) {
  return useMatchMutation<string>(sessionId, submissionId, (confirmedMatchId) =>
    endpoints.deleteConfirmedMatch(sessionId, submissionId, confirmedMatchId)
  );
}

// --- Analysis ---

export function useAnalyzeSubmission(sessionId: string, submissionId: string) {
  return useMutation<{ jobId: string }, ApiError, boolean | undefined>({
    mutationFn: (force) => endpoints.analyzeSubmission(sessionId, submissionId, force ?? false),
    retry: false,
  });
}

export function useReanalyzeCriterion(sessionId: string, submissionId: string) {
  return useMutation<{ jobId: string }, ApiError, string>({
    mutationFn: (criterionId) =>
      endpoints.reanalyzeCriterion(sessionId, submissionId, criterionId),
    retry: false,
  });
}

// --- Comments ---

export function useSuggestComments(sessionId: string, submissionId: string) {
  return useMutation<Awaited<ReturnType<typeof endpoints.suggestComments>>, ApiError,
    CommentSuggestRequest | undefined>({
    mutationFn: (body) => endpoints.suggestComments(sessionId, submissionId, body ?? {}),
    retry: false,
  });
}

// --- Review and export ---

export function useReview(sessionId: string | undefined) {
  return useQuery<ReviewData, ApiError>({
    queryKey: queryKeys.review(sessionId ?? ""),
    queryFn: () => endpoints.getReview(sessionId!),
    enabled: Boolean(sessionId),
    retry: retryOnlyTransient,
  });
}

export function useConfirmReview(sessionId: string) {
  const queryClient = useQueryClient();
  return useMutation<ReviewData, ApiError, void>({
    mutationFn: () => endpoints.confirmReview(sessionId),
    retry: false,
    onSuccess: (data) => {
      queryClient.setQueryData(queryKeys.review(sessionId), data);
      queryClient.invalidateQueries({ queryKey: queryKeys.session(sessionId) });
    },
  });
}

export function useExportGrades(sessionId: string) {
  return useMutation<Awaited<ReturnType<typeof endpoints.exportGrades>>, ApiError,
    "generic" | "canvas">({
    mutationFn: (format) => endpoints.exportGrades(sessionId, format),
    retry: false,
  });
}

// --- Jobs ---

/**
 * Polls a job until it reaches a terminal status (Requirement 19.8).
 *
 * Polling stops on its own once the job is complete or failed, so a finished job does not keep
 * making requests for as long as the page stays open.
 */
export function useJobStatus(jobId: string | null) {
  return useQuery<AsyncJob, ApiError>({
    queryKey: queryKeys.job(jobId ?? ""),
    queryFn: () => endpoints.getJob(jobId!),
    enabled: Boolean(jobId),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === "complete" || status === "failed" ? false : 2000;
    },
    retry: retryOnlyTransient,
  });
}
