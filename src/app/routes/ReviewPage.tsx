import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import AppShell from "../components/AppShell";
import { Badge, EmptyState, ErrorBanner, LoadingPanel } from "../components/Feedback";
import { useConfirmReview, useExportGrades, useReview } from "../api/queries";
import type { ExportResult, ReviewFlag } from "../types";

const FLAG_LABELS: Record<ReviewFlag, string> = {
  incomplete_grading: "Incomplete grading",
  extraction_failed: "Extraction failed",
  oversized: "Oversized",
  unverified_identity: "Identity unverified",
  disambiguation_required: "Needs disambiguation",
  manual_overrides: "Manual override",
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
 * Pre-export review (Requirements 15.1-15.12, 16.1-16.10).
 *
 * Export is gated on confirmation, and the gate is enforced by the API as well — this screen makes it
 * visible rather than being the only thing preventing an unreviewed export. When flags are present,
 * exporting takes a second, explicit acknowledgement (Requirement 15.8).
 */
export default function ReviewPage() {
  const { id: sessionId = "" } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const review = useReview(sessionId);
  const confirmReview = useConfirmReview(sessionId);
  const exportGrades = useExportGrades(sessionId);

  const [exported, setExported] = useState<ExportResult | null>(null);
  const [flagAcknowledged, setFlagAcknowledged] = useState(false);

  const data = review.data;
  const confirmed = Boolean(data?.reviewConfirmedAt);
  const blockedByFlags = (data?.flaggedCount ?? 0) > 0 && !flagAcknowledged;

  const handleExport = async (format: "generic" | "canvas") => {
    const result = await exportGrades.mutateAsync(format);
    setExported(result);
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
        <EmptyState title="Zero submissions are available for review">
          Export is blocked until this session holds at least one submission.{" "}
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
      subtitle={`${data.totalSubmissions} submissions · ${data.flaggedCount} flagged · ${data.unflaggedCount} clear`}
      actions={
        <Link
          to={`/sessions/${sessionId}/mark`}
          className="rounded border border-slate-300 px-3 py-1.5 text-xs text-slate-700"
        >
          Back to marking
        </Link>
      }
    >
      <section className="mb-4 rounded border border-slate-200 bg-white p-4">
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex-1">
            <h2 className="text-sm font-semibold text-slate-900">Confirmation</h2>
            <p className="mt-0.5 text-xs text-slate-600">
              {confirmed
                ? `Confirmed ${new Date(data.reviewConfirmedAt!).toLocaleString()}. Changing any grade clears this.`
                : "Confirm this review to unlock the export."}
            </p>
          </div>
          <button
            type="button"
            onClick={() => void confirmReview.mutateAsync()}
            disabled={confirmReview.isPending}
            className="rounded bg-slate-900 px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50"
          >
            {confirmReview.isPending
              ? "Confirming..."
              : confirmed
                ? "Re-confirm"
                : "Confirm review"}
          </button>
        </div>
        <ErrorBanner error={confirmReview.error} className="mt-3" />
      </section>

      <section className="mb-4 rounded border border-slate-200 bg-white p-4">
        <h2 className="text-sm font-semibold text-slate-900">Export</h2>

        {data.flaggedCount > 0 && (
          <label className="mt-2 flex items-start gap-2 text-xs text-amber-900">
            <input
              type="checkbox"
              checked={flagAcknowledged}
              onChange={(event) => setFlagAcknowledged(event.target.checked)}
              className="mt-0.5"
            />
            <span>
              {data.flaggedCount} of {data.totalSubmissions} submissions are flagged. I have reviewed
              them and want to export anyway. Submissions with no score export as an empty value, not
              a zero.
            </span>
          </label>
        )}

        <div className="mt-3 flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={() => void handleExport("generic")}
            disabled={!confirmed || blockedByFlags || exportGrades.isPending}
            className="rounded bg-slate-900 px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50"
          >
            {exportGrades.isPending ? "Generating..." : "Export generic CSV"}
          </button>
          <button
            type="button"
            onClick={() => void handleExport("canvas")}
            disabled={!confirmed || blockedByFlags || exportGrades.isPending}
            className="rounded border border-slate-300 px-3 py-1.5 text-xs text-slate-700 disabled:opacity-50"
          >
            Export Canvas CSV
          </button>
          {!confirmed && (
            <span className="text-xs text-slate-500">Confirm the review to enable export.</span>
          )}
        </div>

        <ErrorBanner error={exportGrades.error} className="mt-3" />

        {exported && (
          <p className="mt-3 text-xs text-slate-700">
            <a
              href={exported.downloadUrl}
              className="font-medium underline"
              download={exported.filename}
            >
              Download {exported.filename}
            </a>{" "}
            — this link expires 15 minutes after it was issued.
          </p>
        )}
      </section>

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
                className="cursor-pointer hover:bg-slate-50"
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
                        <span className="text-slate-400">—</span>
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
                          {score.selectedLevelLabel && (
                            <span className="block text-xs text-slate-400">
                              {score.selectedLevelLabel}
                            </span>
                          )}
                        </>
                      )}
                    </td>
                  );
                })}
                <td className="px-3 py-2 font-semibold text-slate-900">
                  {submission.totalPoints.toFixed(2)} / {submission.maxPoints.toFixed(2)}
                </td>
                <td className="px-3 py-2">
                  <div className="flex flex-wrap gap-1">
                    {submission.flags.length === 0 && (
                      <span className="text-xs text-slate-400">None</span>
                    )}
                    {submission.flags.map((flag) => (
                      <Badge key={flag} tone={FLAG_TONES[flag]}>
                        {FLAG_LABELS[flag]}
                        {flag === "incomplete_grading" &&
                          ` (${submission.unscoredCriterionCount})`}
                        {flag === "manual_overrides" && ` (${submission.overrideCount})`}
                      </Badge>
                    ))}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </AppShell>
  );
}
