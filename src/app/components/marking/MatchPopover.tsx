import { useEffect, useRef } from "react";
import { useConfirmMatch, useRejectMatch } from "../../api/queries";
import type { Criterion } from "../../types";

/**
 * Popover shown on click/Enter for a highlighted passage (Task 8.4).
 *
 * Shows criterion title, rationale, confidence, and confirm/reject buttons.
 */

interface MatchInfo {
  matchId: string;
  criterionId: string;
  rationale: string;
  confidence: number | null;
  confirmed: boolean;
}

export default function MatchPopover({
  match,
  criteria,
  position,
  sessionId,
  submissionId,
  onClose,
}: {
  match: MatchInfo;
  criteria: Criterion[];
  position: { x: number; y: number };
  sessionId: string;
  submissionId: string;
  onClose: () => void;
}) {
  const popoverRef = useRef<HTMLDivElement>(null);
  const confirmMutation = useConfirmMatch(sessionId, submissionId);
  const rejectMutation = useRejectMatch(sessionId, submissionId);

  const criterion = criteria.find((c) => c.id === match.criterionId);

  // Close on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (popoverRef.current && !popoverRef.current.contains(e.target as Node)) {
        onClose();
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [onClose]);

  // Close on Escape
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [onClose]);

  const handleConfirm = async () => {
    try {
      await confirmMutation.mutateAsync(match.matchId);
      onClose();
    } catch {
      // Error displayed by mutation state
    }
  };

  const handleReject = async () => {
    try {
      await rejectMutation.mutateAsync(match.matchId);
      onClose();
    } catch {
      // Error displayed by mutation state
    }
  };

  return (
    <div
      ref={popoverRef}
      className="fixed z-50 w-72 rounded-lg border border-slate-200 bg-white p-4 shadow-xl"
      style={{
        left: Math.min(position.x, window.innerWidth - 300),
        top: Math.max(position.y - 160, 8),
      }}
      role="dialog"
      aria-label="Match details"
    >
      {/* Criterion title */}
      <div className="mb-2 flex items-center gap-2">
        <span
          className="inline-block h-2.5 w-2.5 rounded-sm"
          style={{ backgroundColor: criterion?.displayColor ?? "#64748b" }}
          aria-hidden="true"
        />
        <span className="text-sm font-semibold text-slate-900">
          {criterion?.title ?? "Unknown criterion"}
        </span>
      </div>

      {/* Rationale */}
      <p className="mb-2 text-xs leading-relaxed text-slate-600">{match.rationale}</p>

      {/* Confidence */}
      {match.confidence !== null && (
        <div className="mb-3 flex items-center gap-2">
          <span className="text-xs text-slate-500">Confidence:</span>
          <span className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-xs font-medium text-slate-700">
            {(match.confidence * 100).toFixed(0)}%
          </span>
        </div>
      )}

      {/* Actions */}
      {!match.confirmed && (
        <div className="flex items-center gap-2 border-t border-slate-100 pt-3">
          <button
            type="button"
            onClick={() => void handleConfirm()}
            disabled={confirmMutation.isPending}
            className="flex-1 rounded bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
          >
            {confirmMutation.isPending ? "Confirming..." : "Confirm"}
          </button>
          <button
            type="button"
            onClick={() => void handleReject()}
            disabled={rejectMutation.isPending}
            className="flex-1 rounded border border-red-300 px-3 py-1.5 text-xs font-medium text-red-700 hover:bg-red-50 disabled:opacity-50"
          >
            {rejectMutation.isPending ? "Rejecting..." : "Reject"}
          </button>
        </div>
      )}

      {match.confirmed && (
        <div className="border-t border-slate-100 pt-3">
          <span className="inline-flex items-center gap-1 rounded bg-emerald-50 px-2 py-1 text-xs font-medium text-emerald-700">
            Confirmed
          </span>
        </div>
      )}

      {/* Close button */}
      <button
        type="button"
        onClick={onClose}
        className="absolute right-2 top-2 rounded p-1 text-slate-400 hover:text-slate-700"
        aria-label="Close"
      >
        <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </div>
  );
}
