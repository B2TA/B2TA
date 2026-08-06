import { Badge } from "./Feedback";
import type {
  ConfirmedMatch,
  Criterion,
  CriterionAnalysis,
  CriterionScore,
  SuggestedMatch,
} from "../types";

/**
 * Criterion cards with level selection, point override, and per-criterion state
 * (Requirements 7.1-7.9, 11.1-11.11).
 *
 * The state shown per criterion distinguishes three situations that all look like "no highlights":
 * analysis still running, analysis complete with nothing found (Requirement 6.6), and analysis
 * unavailable (Requirements 6.7-6.8). Collapsing them would tell a grader there is no evidence when
 * in fact nothing was ever read.
 */

export interface CriterionDraft {
  selectedLevelId: string | null;
  overridePoints: number | null;
  criterionFeedback: string;
  /** Set when the entered override is not a valid value; the stored score is left unchanged. */
  overrideError: string | null;
}

export default function RubricPanel({
  criteria,
  drafts,
  suggested,
  confirmed,
  analysis,
  selectedCriterionId,
  onSelectCriterion,
  onSelectLevel,
  onOverrideChange,
  onCriterionFeedbackChange,
  onReanalyze,
  reanalyzingCriterionId,
}: {
  criteria: Criterion[];
  drafts: Record<string, CriterionDraft>;
  suggested: SuggestedMatch[];
  confirmed: ConfirmedMatch[];
  analysis: CriterionAnalysis[];
  selectedCriterionId: string | null;
  onSelectCriterion: (criterionId: string) => void;
  onSelectLevel: (criterionId: string, levelId: string | null) => void;
  onOverrideChange: (criterionId: string, raw: string) => void;
  onCriterionFeedbackChange: (criterionId: string, text: string) => void;
  onReanalyze: (criterionId: string) => void;
  reanalyzingCriterionId: string | null;
}) {
  const analysisByCriterion = new Map(analysis.map((item) => [item.criterionId, item]));

  return (
    <div className="flex h-full flex-col gap-3 overflow-y-auto pr-1">
      {criteria.map((criterion) => {
        const criterionId = criterion.id!;
        const draft = drafts[criterionId];
        const state = analysisByCriterion.get(criterionId);
        const suggestedCount = suggested.filter((m) => m.criterionId === criterionId).length;
        const confirmedCount = confirmed.filter((m) => m.criterionId === criterionId).length;
        const isSelected = selectedCriterionId === criterionId;
        const awarded = awardedPoints(criterion, draft);

        return (
          <section
            key={criterionId}
            aria-labelledby={`criterion-${criterionId}-title`}
            onClick={() => onSelectCriterion(criterionId)}
            className={`rounded border bg-white p-3 ${
              isSelected ? "border-slate-900 ring-1 ring-slate-900" : "border-slate-200"
            }`}
          >
            <div className="flex items-start gap-2">
              <span
                aria-hidden="true"
                className="mt-1 inline-block h-3 w-3 shrink-0 rounded"
                style={{ backgroundColor: criterion.displayColor }}
              />
              <div className="min-w-0 flex-1">
                <h3
                  id={`criterion-${criterionId}-title`}
                  className="text-sm font-semibold text-slate-900"
                >
                  {criterion.title}
                </h3>
                {criterion.description && (
                  <p className="mt-0.5 text-xs text-slate-500">{criterion.description}</p>
                )}
              </div>
              <div className="shrink-0 text-right text-sm">
                <span className="font-semibold text-slate-900">
                  {awarded === null ? "—" : awarded.toFixed(2)}
                </span>
                <span className="text-slate-400">
                  {" / "}
                  {criterion.maxPoints === null ? "?" : criterion.maxPoints.toFixed(2)}
                </span>
              </div>
            </div>

            <div className="mt-2 flex flex-wrap items-center gap-1.5">
              <MatchCountBadges
                state={state}
                suggestedCount={suggestedCount}
                confirmedCount={confirmedCount}
              />
              {isSelected && <Badge tone="info">Selected</Badge>}
              {draft?.overridePoints !== null && draft?.overridePoints !== undefined && (
                <Badge tone="warn">Manual override</Badge>
              )}
            </div>

            {state?.state === "unavailable" && state.failureReason && (
              <p className="mt-2 text-xs text-amber-800">{state.failureReason}</p>
            )}

            <fieldset className="mt-3">
              <legend className="text-xs font-medium text-slate-600">Performance level</legend>
              <div className="mt-1 flex flex-wrap gap-1.5">
                {criterion.performanceLevels.map((level, index) => {
                  const levelId = level.id!;
                  const active = draft?.selectedLevelId === levelId;
                  return (
                    <button
                      key={levelId}
                      type="button"
                      aria-pressed={active}
                      onClick={(event) => {
                        event.stopPropagation();
                        onSelectLevel(criterionId, active ? null : levelId);
                      }}
                      title={level.description ?? undefined}
                      className={`rounded border px-2 py-1 text-xs ${
                        active
                          ? "border-slate-900 bg-slate-900 text-white"
                          : "border-slate-300 bg-white text-slate-700"
                      }`}
                    >
                      <span className="mr-1 text-[10px] opacity-70">
                        {index < 9 ? index + 1 : index === 9 ? 0 : ""}
                      </span>
                      {level.label}
                      {level.points !== null && (
                        <span className="ml-1 opacity-70">({level.points})</span>
                      )}
                    </button>
                  );
                })}
              </div>
            </fieldset>

            <div className="mt-3 grid gap-2 sm:grid-cols-2">
              <div>
                <label
                  htmlFor={`override-${criterionId}`}
                  className="block text-xs font-medium text-slate-600"
                >
                  Override points
                </label>
                <input
                  id={`override-${criterionId}`}
                  inputMode="decimal"
                  value={draft?.overridePoints === null || draft?.overridePoints === undefined
                    ? ""
                    : String(draft.overridePoints)}
                  onClick={(event) => event.stopPropagation()}
                  onChange={(event) => onOverrideChange(criterionId, event.target.value)}
                  aria-invalid={Boolean(draft?.overrideError)}
                  aria-describedby={
                    draft?.overrideError ? `override-${criterionId}-error` : undefined
                  }
                  placeholder={criterion.maxPoints === null ? "" : `0 – ${criterion.maxPoints}`}
                  className={`mt-1 w-full rounded border px-2 py-1 text-sm ${
                    draft?.overrideError ? "border-red-400" : "border-slate-300"
                  }`}
                />
                {draft?.overrideError && (
                  <p
                    id={`override-${criterionId}-error`}
                    role="alert"
                    className="mt-1 text-xs text-red-700"
                  >
                    {draft.overrideError}
                  </p>
                )}
              </div>
              <div>
                <label
                  htmlFor={`feedback-${criterionId}`}
                  className="block text-xs font-medium text-slate-600"
                >
                  Criterion feedback
                  <span className="ml-1 font-normal text-slate-400">
                    {(draft?.criterionFeedback ?? "").length}/2000
                  </span>
                </label>
                <textarea
                  id={`feedback-${criterionId}`}
                  rows={2}
                  maxLength={2000}
                  value={draft?.criterionFeedback ?? ""}
                  onClick={(event) => event.stopPropagation()}
                  onChange={(event) => onCriterionFeedbackChange(criterionId, event.target.value)}
                  className="mt-1 w-full rounded border border-slate-300 px-2 py-1 text-sm"
                />
              </div>
            </div>

            <div className="mt-2 flex justify-end">
              <button
                type="button"
                onClick={(event) => {
                  event.stopPropagation();
                  onReanalyze(criterionId);
                }}
                disabled={reanalyzingCriterionId === criterionId}
                className="rounded border border-slate-300 px-2 py-1 text-xs text-slate-700 disabled:opacity-50"
              >
                {reanalyzingCriterionId === criterionId ? "Re-analysing..." : "Re-analyse evidence"}
              </button>
            </div>
          </section>
        );
      })}
    </div>
  );
}

/**
 * Awarded points for one criterion, mirroring the server's ScoreCalculator.
 *
 * An override wins over a selected level. Null means unscored, which is deliberately distinct from
 * zero: an unscored criterion is excluded from the total (Requirement 11.10) and exported as an empty
 * cell (Requirement 16.5).
 */
export function awardedPoints(
  criterion: Criterion,
  draft: CriterionDraft | undefined
): number | null {
  if (!draft) {
    return null;
  }
  if (draft.overridePoints !== null) {
    return draft.overridePoints;
  }
  if (draft.selectedLevelId) {
    const level = criterion.performanceLevels.find((item) => item.id === draft.selectedLevelId);
    if (level?.points !== null && level?.points !== undefined) {
      return level.points;
    }
  }
  return null;
}

function MatchCountBadges({
  state,
  suggestedCount,
  confirmedCount,
}: {
  state: CriterionAnalysis | undefined;
  suggestedCount: number;
  confirmedCount: number;
}) {
  if (state?.state === "unavailable") {
    return <Badge tone="warn">Analysis unavailable</Badge>;
  }
  if (state?.state === "in_progress" || state?.state === "pending") {
    return <Badge tone="info">Analysis in progress</Badge>;
  }
  if (suggestedCount === 0 && confirmedCount === 0) {
    return state?.state === "complete" ? (
      <Badge tone="neutral">No evidence found</Badge>
    ) : (
      <Badge tone="neutral">Not analysed</Badge>
    );
  }
  return (
    <>
      {confirmedCount > 0 && <Badge tone="ok">{confirmedCount} confirmed</Badge>}
      {suggestedCount > 0 && <Badge tone="info">{suggestedCount} suggested</Badge>}
    </>
  );
}
