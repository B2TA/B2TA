import { useState, useCallback } from "react";
import { useJobStatus } from "../api/queries";
import type { AsyncJob } from "../types";

interface UseJobPollingResult {
  /** Start polling a new job */
  startPolling: (jobId: string) => void;
  /** Stop polling and reset */
  reset: () => void;
  /** The current job data (null if not polling) */
  job: AsyncJob | null | undefined;
  /** Whether a job is currently active (pending or in_progress) */
  isActive: boolean;
  /** Whether the job completed successfully */
  isComplete: boolean;
  /** Whether the job failed */
  isFailed: boolean;
  /** Error from polling (network error) */
  error: unknown;
  /** The job ID currently being polled */
  jobId: string | null;
}

/**
 * Hook for polling an async job until it reaches a terminal state.
 * Uses useJobStatus under the hood, which stops refetching once the job is complete or failed.
 */
export function useJobPolling(): UseJobPollingResult {
  const [jobId, setJobId] = useState<string | null>(null);

  const query = useJobStatus(jobId);

  const startPolling = useCallback((id: string) => {
    setJobId(id);
  }, []);

  const reset = useCallback(() => {
    setJobId(null);
  }, []);

  const job = query.data ?? null;
  const status = job?.status;
  const isActive = status === "pending" || status === "in_progress";
  const isComplete = status === "complete";
  const isFailed = status === "failed";

  return {
    startPolling,
    reset,
    job,
    isActive,
    isComplete,
    isFailed,
    error: query.error,
    jobId,
  };
}
