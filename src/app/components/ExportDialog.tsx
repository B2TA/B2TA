import { useState } from "react";
import { ErrorBanner, LoadingPanel } from "./Feedback";
import { useExportGrades } from "../api/queries";
import type { ExportFormat, ExportResult } from "../types";

interface ExportDialogProps {
  sessionId: string;
  /** Whether the review has been confirmed. Export is blocked until confirmed. */
  reviewConfirmed: boolean;
  /** Number of flagged submissions (shown in warning). */
  flaggedCount: number;
  /** Total number of submissions. */
  totalSubmissions: number;
  /** Close the dialog. */
  onClose: () => void;
}

/**
 * Modal dialog presenting export options (Generic CSV / Canvas Gradebook CSV).
 *
 * Export is gated on review confirmation (Requirement 16.2). When flagged submissions exist,
 * the dialog displays their count but does not block export — that acknowledgement is handled
 * on the ReviewPage before opening the dialog.
 *
 * Requirements: 16.1-16.11
 */
export default function ExportDialog({
  sessionId,
  reviewConfirmed,
  flaggedCount,
  totalSubmissions,
  onClose,
}: ExportDialogProps) {
  const exportGrades = useExportGrades(sessionId);
  const [result, setResult] = useState<ExportResult | null>(null);

  const handleExport = async (format: ExportFormat) => {
    try {
      const data = await exportGrades.mutateAsync(format);
      setResult(data);
    } catch {
      // Error is rendered through the mutation state below.
    }
  };

  if (totalSubmissions === 0) {
    return (
      <DialogShell onClose={onClose} title="Export Grades">
        <div className="px-6 py-8 text-center">
          <p className="text-sm font-medium text-slate-700">No submissions to export</p>
          <p className="mt-1 text-xs text-slate-500">
            This session has no submissions. Add submissions before exporting.
          </p>
        </div>
      </DialogShell>
    );
  }

  return (
    <DialogShell onClose={onClose} title="Export Grades">
      <div className="px-6 py-4">
        {/* Confirmation status */}
        <div className="mb-4 rounded border border-slate-200 bg-slate-50 p-3">
          <div className="flex items-center gap-2">
            {reviewConfirmed ? (
              <>
                <span className="inline-block h-2.5 w-2.5 rounded-full bg-emerald-500" aria-hidden="true" />
                <span className="text-sm font-medium text-emerald-800">Review confirmed</span>
              </>
            ) : (
              <>
                <span className="inline-block h-2.5 w-2.5 rounded-full bg-amber-500" aria-hidden="true" />
                <span className="text-sm font-medium text-amber-800">Review not confirmed</span>
              </>
            )}
          </div>
          {!reviewConfirmed && (
            <p className="mt-1 text-xs text-slate-600">
              Review must be confirmed first. Return to the review screen to confirm.
            </p>
          )}
        </div>

        {/* Flag warning */}
        {flaggedCount > 0 && (
          <div className="mb-4 rounded border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900">
            {flaggedCount} of {totalSubmissions} submissions are flagged. Incomplete submissions
            export with an empty score (not zero).
          </div>
        )}

        {/* Export buttons */}
        <div className="flex flex-col gap-3">
          <button
            type="button"
            onClick={() => void handleExport("generic")}
            disabled={!reviewConfirmed || exportGrades.isPending}
            className="flex w-full items-center justify-center gap-2 rounded bg-slate-900 px-4 py-2.5 text-sm font-medium text-white disabled:opacity-50"
          >
            {exportGrades.isPending && exportGrades.variables === "generic" ? (
              <Spinner />
            ) : null}
            Generic CSV
          </button>
          <button
            type="button"
            onClick={() => void handleExport("canvas")}
            disabled={!reviewConfirmed || exportGrades.isPending}
            className="flex w-full items-center justify-center gap-2 rounded border border-slate-300 px-4 py-2.5 text-sm font-medium text-slate-700 disabled:opacity-50"
          >
            {exportGrades.isPending && exportGrades.variables === "canvas" ? (
              <Spinner />
            ) : null}
            Canvas Gradebook CSV
          </button>
        </div>

        {/* Error with retry */}
        {exportGrades.error && (
          <div className="mt-4">
            <ErrorBanner
              error={exportGrades.error}
              onRetry={() => void handleExport(exportGrades.variables ?? "generic")}
            />
          </div>
        )}

        {/* Success — download link */}
        {result && (
          <div className="mt-4 rounded border border-emerald-200 bg-emerald-50 p-3">
            <p className="text-sm font-medium text-emerald-900">Export ready</p>
            <p className="mt-1 text-xs text-slate-600">
              This link expires 15 minutes after generation.
            </p>
            <a
              href={result.downloadUrl}
              download={result.filename}
              className="mt-2 inline-flex items-center gap-1.5 rounded bg-emerald-700 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-800"
            >
              <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v2a2 2 0 002 2h12a2 2 0 002-2v-2M7 10l5 5m0 0l5-5m-5 5V3" />
              </svg>
              Download {result.filename}
            </a>
          </div>
        )}
      </div>
    </DialogShell>
  );
}

function DialogShell({
  onClose,
  title,
  children,
}: {
  onClose: () => void;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label={title}
    >
      <div
        className="mx-4 w-full max-w-md overflow-hidden rounded-lg bg-white shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
          <h2 className="text-base font-semibold text-slate-900">{title}</h2>
          <button
            type="button"
            onClick={onClose}
            className="rounded p-1 text-slate-500 hover:bg-slate-100 hover:text-slate-700 focus:outline-none focus:ring-2 focus:ring-slate-400"
            aria-label="Close"
          >
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

function Spinner() {
  return (
    <span
      className="inline-block h-3.5 w-3.5 animate-spin rounded-full border-2 border-white border-t-transparent"
      aria-hidden="true"
    />
  );
}
