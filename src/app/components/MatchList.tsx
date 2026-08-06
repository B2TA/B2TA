import { Badge } from "./Feedback";
import type { ConfirmedMatch, SuggestedMatch } from "../types";

/**
 * Suggested and confirmed matches for the selected criterion (Requirements 10.1-10.4, 6.12).
 *
 * Each row shows the passage text, the model's rationale, and its confidence, because a TA confirming
 * evidence is judging the model's reasoning, not just the highlight — the rationale is the part that
 * makes a suggestion checkable.
 */
export default function MatchList({
  criterionTitle,
  text,
  suggested,
  confirmed,
  onConfirm,
  onReject,
  onDelete,
  busy,
}: {
  criterionTitle: string;
  text: string;
  suggested: SuggestedMatch[];
  confirmed: ConfirmedMatch[];
  onConfirm: (matchId: string) => void;
  onReject: (matchId: string) => void;
  onDelete: (confirmedMatchId: string) => void;
  busy: boolean;
}) {
  const excerpt = (start: number, end: number) => {
    const slice = text.slice(Math.max(0, start), Math.min(text.length, end));
    return slice.length > 240 ? `${slice.slice(0, 240)}...` : slice;
  };

  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-slate-900">Evidence for {criterionTitle}</h3>

      {confirmed.length === 0 && suggested.length === 0 && (
        <p className="text-xs text-slate-500">
          No passages are associated with this criterion yet. Select text in the document to add one.
        </p>
      )}

      {confirmed.map((match) => (
        <article key={match.id} className="rounded border border-emerald-300 bg-emerald-50 p-3">
          <div className="flex items-center justify-between gap-2">
            <Badge tone="ok">
              {match.origin === "ta_authored" ? "Added by you" : "Confirmed"}
            </Badge>
            <button
              type="button"
              onClick={() => onDelete(match.id)}
              disabled={busy}
              className="rounded border border-emerald-400 bg-white px-2 py-0.5 text-xs text-emerald-900 disabled:opacity-50"
            >
              Remove
            </button>
          </div>
          <blockquote className="mt-2 border-l-2 border-emerald-400 pl-2 text-xs italic text-slate-700">
            {excerpt(match.passageStart, match.passageEnd)}
          </blockquote>
          <p className="mt-1 text-xs text-slate-600">{match.rationale}</p>
        </article>
      ))}

      {suggested.map((match) => (
        <article key={match.id} className="rounded border border-sky-300 bg-sky-50 p-3">
          <div className="flex items-center justify-between gap-2">
            <Badge tone="info">
              Suggested · confidence {(match.confidence * 100).toFixed(0)}%
            </Badge>
            <div className="flex gap-1">
              <button
                type="button"
                onClick={() => onConfirm(match.id)}
                disabled={busy}
                className="rounded bg-slate-900 px-2 py-0.5 text-xs font-medium text-white disabled:opacity-50"
              >
                Confirm
              </button>
              <button
                type="button"
                onClick={() => onReject(match.id)}
                disabled={busy}
                className="rounded border border-slate-300 bg-white px-2 py-0.5 text-xs text-slate-700 disabled:opacity-50"
              >
                Reject
              </button>
            </div>
          </div>
          <blockquote className="mt-2 border-l-2 border-sky-400 pl-2 text-xs italic text-slate-700">
            {excerpt(match.passageStart, match.passageEnd)}
          </blockquote>
          <p className="mt-1 text-xs text-slate-600">{match.rationale}</p>
        </article>
      ))}
    </div>
  );
}
