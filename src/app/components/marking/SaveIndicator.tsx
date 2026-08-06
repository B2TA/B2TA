import { useCallback, useEffect, useRef, useState } from "react";
import { useSaveGrading } from "../../api/queries";
import { useGradingStore } from "../../stores/gradingStore";
import type { SaveGradingRequest } from "../../types";

/**
 * Save button and status indicator (Task 8.9).
 *
 * - Save button (also bound to Ctrl+S)
 * - Shows "Unsaved changes" or "Saved at 2:34 PM"
 * - PUT /api/sessions/{id}/submissions/{subId}/grading on save
 * - beforeunload warning when unsaved
 * - 30-second timeout with retry
 */

export default function SaveIndicator({
  sessionId,
  submissionId,
}: {
  sessionId: string;
  submissionId: string;
}) {
  const store = useGradingStore();
  const saveGrading = useSaveGrading(sessionId, submissionId);
  const [timedOut, setTimedOut] = useState(false);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const buildPayload = useCallback((): SaveGradingRequest => {
    return {
      overallFeedback: store.draftFeedback,
      criterionScores: store.draftScores.map((s) => ({
        criterionId: s.criterionId,
        selectedLevelId: s.selectedLevelId,
        overridePoints: s.overridePoints,
        criterionFeedback: s.criterionFeedback,
      })),
    };
  }, [store.draftFeedback, store.draftScores]);

  const handleSave = useCallback(async () => {
    setTimedOut(false);

    // Set a 30-second timeout
    timeoutRef.current = setTimeout(() => {
      setTimedOut(true);
    }, 30000);

    try {
      await saveGrading.mutateAsync(buildPayload());
      store.setHasUnsavedChanges(false);
      store.setLastSavedAt(new Date().toISOString());
    } catch {
      // Error handled by mutation state
    } finally {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
        timeoutRef.current = null;
      }
    }
  }, [buildPayload, saveGrading, store]);

  // Ctrl+S keyboard shortcut
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === "s") {
        e.preventDefault();
        void handleSave();
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [handleSave]);

  // beforeunload warning when unsaved
  useEffect(() => {
    if (!store.hasUnsavedChanges) return;
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [store.hasUnsavedChanges]);

  // Cleanup timeout on unmount
  useEffect(() => {
    return () => {
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
    };
  }, []);

  const formatTime = (iso: string) => {
    return new Date(iso).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
  };

  return (
    <div className="flex items-center gap-2">
      {/* Status display */}
      {store.hasUnsavedChanges ? (
        <span className="rounded bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800">
          Unsaved changes
        </span>
      ) : store.lastSavedAt ? (
        <span className="text-xs text-slate-500">Saved at {formatTime(store.lastSavedAt)}</span>
      ) : null}

      {/* Timeout indicator */}
      {timedOut && (
        <span className="text-xs text-red-600">Save timed out</span>
      )}

      {/* Error state */}
      {saveGrading.isError && (
        <span className="text-xs text-red-600">Save failed</span>
      )}

      {/* Save button */}
      <button
        type="button"
        onClick={() => void handleSave()}
        disabled={saveGrading.isPending || !store.hasUnsavedChanges}
        className="rounded bg-slate-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-800 disabled:opacity-50"
        title="Save (Ctrl+S)"
      >
        {saveGrading.isPending ? "Saving..." : "Save"}
      </button>

      {/* Retry on timeout or error */}
      {(timedOut || saveGrading.isError) && (
        <button
          type="button"
          onClick={() => void handleSave()}
          className="rounded border border-red-300 px-2 py-1 text-xs text-red-700 hover:bg-red-50"
        >
          Retry
        </button>
      )}
    </div>
  );
}
