import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import AppShell from "../components/AppShell";
import { Badge, EmptyState, ErrorBanner, LoadingPanel } from "../components/Feedback";
import ExportDialog from "../components/ExportDialog";
import { useConfirmReview, useReview } from "../api/queries";
import type { ReviewFlag } from "../types";

const FLAG_LABELS: Record<ReviewFlag, string> = {
  incomplete_grading: "Incomplete",
  extraction_failed: "Extraction Failed",
  oversized: "Oversized",
  unverified_identity: "Unverified",
  disambiguation_required: "Disambiguation",
  manual_overrides: "Override",
};

const FLAG_TONES: Record<ReviewFlag, "warn" | "danger" | "info"> = {
  incomplete_grading: "warn",
  extraction_failed: "danger",
  oversized: "info",
  unverified_identity: "warn",
  disambiguation_required: "warn",
  manual_overrides: "info",
};

/**
 * Pre-export review screen (Requirements 15.1-15.12, 16.1-16.10).
 *
 * Shows a table of all submissions with per-criterion scores, flags, and totals.
 * Export is gated on confirmation, enforced by the API as well.
 * When flags are present, confirming requires an explicit acknowledgement (Requirement 15.8).
 */
export default function ReviewPage() {
  const { id: sessionId = "" } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const review = useReview(sessionId);
  const confirmReview = useConfirmReview(sessionId);

  const [showExportDialog, setShowExportDialog] = useState(false);
  const [showFlagWarning, setShowFlagWarning] = useState(false);

  const data = review.data;
  const confirmed = Boolean(data?.reviewConfirmedAt);

  // Handle confirm with flag warning
  const handleConfirm = () => {
    if ((data?.flaggedCount ?? 0) > 0 && !showFlagWarning) {
      setShowFlagWarning(true);
      return;
    }
    setShowFlagWarning(false);
    void confirmReview.mutateAsync();
  };

  if (review.isLoading) {
    return (
      <AppShell title="Review">
        <LoadingPanel label="Building the review..." />
      </AppShell>
    );
  }

  if (review.error) {
    return (
      <AppShell title="Review">
        <ErrorBanner error={review.error} onRetry={() => void review.refetch()} />
      </AppShell>
    );
  }

  if (!data || data.totalSubmissions === 0) {
    return (
      <AppShell title="Review">
        <EmptyState title="No submissions available for review">
          This session has no submissions. Add submissions before reviewing or exporting.{" "}
          <Link className="underline" to={`/sessions/${sessionId}/setup`}>
            Add submissions
          </Link>
          .
        </EmptyState>
      </AppShell>
    );
  }

  return (
    <AppShell
      title="Review before export"
      subtitle={`${data.totalSubmissions} submissions`}
      actions={
        <div className="flex items-center gap-2">
          <Link
            to={`/sessions/${sessionId}/mark`}
            className="rounded border border-slate-300 px-3 py-1.5 text-xs text-slate-700"
          >
            Back to marking
          </Link>
        </div>
      }
    >
      {/* Summary bar */}
      <div className="mb-4 flex flex-wrap items-center gap-3 rounded border border-slate-200 bg-white px-4 py-3">
        <span className="text-sm font-semibold text-slate-900">
          {data.flaggedCount} flagged / {data.unflaggedCount} unflagged
        </span>
        <div className="ml-auto flex items-center gap-2">
          <button
            type="button"
            onClick={handleConfirm}
            disabled={confirmReview.isPending}
            className="rounded bg-slate-900 px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50"
          >
            {confirmReview.isPending
              ? "Confirming..."
              : confirmed
                ? "Re-confirm"
                : "Confirm & Export"}
          </button>
          {confirmed && (
            <button
              type="button"
              onClick={() => setShowExportDialog(true)}
              className="rounded border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700"
            >
              Export
            </button>
          )}
        </div>
      </div>

      {/* Confirm status */}
      {confirmed && (
        <div className="mb-4 rounded border border-emerald-200 bg-emerald-50 px-4 py-2 text-xs text-emerald-900">
          Review confirmed {new Date(data.reviewConfirmedAt!).toLocaleString()}. Changing any
          grade clears this confirmation.
        </div>
      )}

      <ErrorBanner error={confirmReview.error} className="mb-4" />

      {/* Flag warning dialog */}
      {showFlagWarning && (
        <div className="mb-4 rounded border border-amber-300 bg-amber-50 p-4">
          <p className="text-sm font-medium text-amber-900">
            {data.flaggedCount} submission{data.flaggedCount !== 1 ? "s" : ""} flagged
          </p>
          <ul className="mt-2 space-y-1 text-xs text-amber-800">
            {Object.entries(flagCounts(data.submissions)).map(([flag, count]) => (
              <li key={flag}>
                {FLAG_LABELS[flag as ReviewFlag]}: {count}
              </li>
            ))}
          </ul>
          <p className="mt-2 text-xs text-amber-700">
            Submissions with incomplete grading export with empty scores (not zero).
          </p>
          <div className="mt-3 flex gap-2">
            <button
              type="button"
              onClick={() => {
                setShowFlagWarning(false);
                void confirmReview.mutateAsync();
              }}
              disabled={confirmReview.isPending}
              className="rounded bg-amber-700 px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50"
            >
              Confirm anyway
            </button>
            <button
              type="button"
              onClick={() => setShowFlagWarning(false)}
              className="rounded border border-amber-400 px-3 py-1.5 text-xs text-amber-900"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Submissions table */}
      <div className="overflow-x-auto rounded border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <caption className="sr-only">Grades for every submission in this session</caption>
          <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th scope="col" className="px-3 py-2">#</th>
              <th scope="col" className="px-3 py-2">Student</th>
              {data.criteria.map((criterion) => (
                <th key={criterion.criterionId} scope="col" className="px-3 py-2">
                  {criterion.title}
                  {criterion.maxPoints !== null && (
                    <span className="block font-normal normal-case text-slate-400">
                      max {criterion.maxPoints}
                    </span>
                  )}
                </th>
              ))}
              <th scope="col" className="px-3 py-2">Total</th>
              <th scope="col" className="px-3 py-2">Max</th>
              <th scope="col" className="px-3 py-2">Flags</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {data.submissions.map((submission) => (
              <tr
                key={submission.submissionId}
                onClick={() =>
                  navigate(
                    `/sessions/${sessionId}/mark?submission=${submission.submissionId}`
                  )
                }
                className="cursor-pointer hover:bg-slate-50 focus-within:ring-2 focus-within:ring-inset focus-within:ring-slate-400"
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    navigate(
                      `/sessions/${sessionId}/mark?submission=${submission.submissionId}`
                    );
                  }
                }}
                role="link"
                aria-label={`Open marking for ${submission.studentDisplayName}`}
              >
                <td className="px-3 py-2 text-slate-500">{submission.position}</td>
                <td className="px-3 py-2 font-medium text-slate-900">
                  {submission.studentDisplayName}
                </td>
                {data.criteria.map((criterion) => {
                  const score = submission.criterionScores.find(
                    (item) => item.criterionId === criterion.criterionId
                  );
                  return (
                    <td key={criterion.criterionId} className="px-3 py-2 text-slate-700">
                      {score?.points === null || score?.points === undefined ? (
                        <span className="text-slate-400">&mdash;</span>
                      ) : (
                        <>
                          {score.points.toFixed(2)}
                          {score.overridden && (
                            <span
                              className="ml-1 text-xs text-amber-700"
                              title="Manual point override"
                            >
                              (override)
                            </span>
                          )}
                        </>
                      )}
                    </td>
                  );
                })}
                <td className="px-3 py-2 font-semibold text-slate-900">
                  {submission.totalPoints.toFixed(2)}
                </td>
                <td className="px-3 py-2 text-slate-500">
                  {submission.maxPoints.toFixed(2)}
                </td>
                <td className="px-3 py-2">
                  <div className="flex flex-wrap gap-1">
                    {submission.flags.length === 0 && (
                      <span className="text-xs text-slate-400">None</span>
                    )}
                    {submission.flags.map((flag) => (
                      <Badge key={flag} tone={FLAG_TONES[flag]}>
                        {FLAG_LABELS[flag]}
                      </Badge>
                    ))}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Export Dialog */}
      {showExportDialog && (
        <ExportDialog
          sessionId={sessionId}
          reviewConfirmed={confirmed}
          flaggedCount={data.flaggedCount}
          totalSubmissions={data.totalSubmissions}
          onClose={() => setShowExportDialog(false)}
        />
      )}
    </AppShell>
  );
}

/** Count how many submissions carry each flag type for the warning display. */
function flagCounts(
  submissions: Array<{ flags: ReviewFlag[] }>
): Partial<Record<ReviewFlag, number>> {
  const counts: Partial<Record<ReviewFlag, number>> = {};
  for (const sub of submissions) {
    for (const flag of sub.flags) {
      counts[flag] = (counts[flag] ?? 0) + 1;
    }
  }
  return counts;
}
