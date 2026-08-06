import type { ReactNode } from "react";
import { ApiError } from "../api/client";

/**
 * Error banner with an optional retry control.
 *
 * Shows the server's own message rather than a generic one: the API writes messages a grader can act
 * on ("Open and confirm the review screen before exporting"), and replacing them with "Something went
 * wrong" would throw that away.
 */
export function ErrorBanner({
  error,
  onRetry,
  className = "",
}: {
  error: unknown;
  onRetry?: () => void;
  className?: string;
}) {
  if (!error) {
    return null;
  }
  const message =
    error instanceof ApiError
      ? error.message
      : error instanceof Error
        ? error.message
        : "An unexpected error occurred.";
  const code = error instanceof ApiError ? error.code : null;

  return (
    <div
      role="alert"
      className={`rounded border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-800 ${className}`}
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="font-medium">{message}</p>
          {code && <p className="mt-0.5 text-xs text-red-600">Error code: {code}</p>}
        </div>
        {onRetry && (
          <button
            type="button"
            onClick={onRetry}
            className="shrink-0 rounded border border-red-400 bg-white px-3 py-1 text-xs font-medium text-red-800"
          >
            Retry
          </button>
        )}
      </div>
    </div>
  );
}

export function LoadingPanel({ label = "Loading..." }: { label?: string }) {
  return (
    <div className="flex items-center gap-2 p-6 text-sm text-slate-500" role="status" aria-live="polite">
      <span
        className="inline-block h-3 w-3 animate-spin rounded-full border-2 border-slate-400 border-t-transparent"
        aria-hidden="true"
      />
      {label}
    </div>
  );
}

export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="rounded border border-dashed border-slate-300 p-8 text-center">
      <p className="font-medium text-slate-700">{title}</p>
      {children && <div className="mt-2 text-sm text-slate-500">{children}</div>}
    </div>
  );
}

/**
 * Small status pill.
 *
 * Every tone pairs a colour with text; nothing here relies on colour alone to carry meaning.
 */
export function Badge({
  tone = "neutral",
  children,
}: {
  tone?: "neutral" | "warn" | "danger" | "ok" | "info";
  children: ReactNode;
}) {
  const tones: Record<string, string> = {
    neutral: "bg-slate-100 text-slate-700 border-slate-300",
    warn: "bg-amber-50 text-amber-800 border-amber-300",
    danger: "bg-red-50 text-red-800 border-red-300",
    ok: "bg-emerald-50 text-emerald-800 border-emerald-300",
    info: "bg-sky-50 text-sky-800 border-sky-300",
  };
  return (
    <span
      className={`inline-flex items-center rounded border px-2 py-0.5 text-xs font-medium ${tones[tone]}`}
    >
      {children}
    </span>
  );
}
