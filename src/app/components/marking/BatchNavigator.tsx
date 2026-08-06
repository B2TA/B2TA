import { useState } from "react";
import { useGradingStore } from "../../stores/gradingStore";
import type { Submission } from "../../types";

/**
 * Student navigation between submissions in the batch (Task 8.8).
 *
 * - "Student 7 of 24" display with prev/next buttons
 * - Save-and-advance / save-and-previous
 * - Unsaved changes warning dialog
 * - Unscored criteria warning dialog
 * - Submission dropdown picker (by name)
 */

export default function BatchNavigator({
  sessionId,
  submissionId,
  submissions,
  position,
  batchSize,
  onNavigate,
}: {
  sessionId: string;
  submissionId: string;
  submissions: Submission[];
  position: number;
  batchSize: number;
  onNavigate: (submissionId: string) => void;
}) {
  const store = useGradingStore();
  const [showWarning, setShowWarning] = useState(false);
  const [pendingTarget, setPendingTarget] = useState<string | null>(null);

  const currentIndex = submissions.findIndex((s) => s.id === submissionId);
  const hasPrev = currentIndex > 0;
  const hasNext = currentIndex < submissions.length - 1;

  const navigateWithCheck = (targetId: string) => {
    if (store.hasUnsavedChanges) {
      setPendingTarget(targetId);
      setShowWarning(true);
      return;
    }
    onNavigate(targetId);
  };

  const confirmNavigate = () => {
    if (pendingTarget) {
      store.setHasUnsavedChanges(false);
      onNavigate(pendingTarget);
    }
    setShowWarning(false);
    setPendingTarget(null);
  };

  const cancelNavigate = () => {
    setShowWarning(false);
    setPendingTarget(null);
  };

  const handlePrev = () => {
    if (hasPrev) navigateWithCheck(submissions[currentIndex - 1].id);
  };

  const handleNext = () => {
    if (hasNext) navigateWithCheck(submissions[currentIndex + 1].id);
  };

  return (
    <div className="flex items-center gap-3">
      {/* Navigation buttons */}
      <button
        type="button"
        onClick={handlePrev}
        disabled={!hasPrev}
        className="rounded border border-slate-300 px-2 py-1 text-xs disabled:opacity-40"
        aria-label="Previous submission"
      >
        &larr; Prev
      </button>

      {/* Position indicator */}
      <span className="text-sm font-medium text-slate-700">
        Student {position} of {batchSize}
      </span>

      <button
        type="button"
        onClick={handleNext}
        disabled={!hasNext}
        className="rounded border border-slate-300 px-2 py-1 text-xs disabled:opacity-40"
        aria-label="Next submission"
      >
        Next &rarr;
      </button>

      {/* Dropdown picker */}
      <select
        value={submissionId}
        onChange={(e) => navigateWithCheck(e.target.value)}
        className="rounded border border-slate-300 px-2 py-1 text-sm"
        aria-label="Select submission"
      >
        {submissions.map((sub, idx) => (
          <option key={sub.id} value={sub.id}>
            {idx + 1}. {sub.studentDisplayName || sub.originalFilename}
          </option>
        ))}
      </select>

      {/* Unsaved changes warning dialog */}
      {showWarning && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
          role="alertdialog"
          aria-label="Unsaved changes warning"
        >
          <div className="w-80 rounded-lg border border-slate-200 bg-white p-6 shadow-xl">
            <h3 className="text-sm font-semibold text-slate-900">Unsaved Changes</h3>
            <p className="mt-2 text-xs text-slate-600">
              You have unsaved changes on this submission. If you navigate away, your changes will
              be lost.
            </p>
            <div className="mt-4 flex justify-end gap-2">
              <button
                type="button"
                onClick={cancelNavigate}
                className="rounded border border-slate-300 px-3 py-1.5 text-xs text-slate-700"
              >
                Stay here
              </button>
              <button
                type="button"
                onClick={confirmNavigate}
                className="rounded bg-red-600 px-3 py-1.5 text-xs font-medium text-white"
              >
                Discard and navigate
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
