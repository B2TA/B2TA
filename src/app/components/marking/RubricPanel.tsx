import { useMemo } from "react";
import { Badge } from "../Feedback";
import { useGradingStore } from "../../stores/gradingStore";
import type {
  ConfirmedMatch,
  Criterion,
  CriterionAnalysis,
  GradingRecord,
  SuggestedMatch,
} from "../../types";

/**
 * Scrollable rubric panel showing criterion cards (Task 8.2).
 *
 * Renders from API data with variable count/colors/levels.
 * Shows match counts, analysis states, level selection, and override inputs.
 */

type CriterionState =
  | "unscored"
  | "scored"
  | "no-evidence-found"
  | "analysis-unavailable"
  | "analysis-in-progress"
  | "stale";

function getCriterionState(
  criterion: Criterion,
  analysis: CriterionAnalysis | undefined,
  suggested: SuggestedMatch[],
  confirmed: ConfirmedMatch[],
  hasScore: boolean,
  hasStale: boolean
): CriterionState {
  if (hasStale) return "stale";
  if (analysis?.state === "unavailable") return "analysis-unavailable";
  if (analysis?.state === "in_progress" || analysis?.state === "pending") return "analysis-in-progress";
  if (analysis?.state === "complete" && suggested.length === 0 && confirmed.length === 0) {
    return "no-evidence-found";
  }
  if (hasScore) return "scored";
  return "unscored";
}

export default function RubricPanel({
  criteria,
  record,
  filteredSuggested,
  selectedCriterionId,
  onSelectCriterion,
}: {
  criteria: Criterion[];
  record: GradingRecord;
  filteredSuggested: SuggestedMatch[];
  selectedCriterionId: string | null;
  onSelectCriterion: (id: string) => void;
}) {
  const store = useGradingStore();
  const analysisByCriterion = useMemo(
    () => new Map(record.criterionAnalysis.map((a) => [a.criterionId, a])),
    [record.criterionAnalysis]
  );

  const totalScore = useMemo(() => {
    let sum = 0;
    for (const criterion of criteria) {
      const cid = criterion.id!;
      const draft = store.draftScores.find((s) => s.criterionId === cid);
      if (!draft) continue;
      if (draft.overridePoints !== null) {
        sum += draft.overridePoints;
      } else if (draft.selectedLevelId) {
        const level = criterion.performanceLevels.find((l) => l.id === draft.selectedLevelId);
        if (level?.points !== null && level?.points !== undefined) {
          sum += level.points;
        }
      }
    }
    return sum;
  }, [criteria, store.draftScores]);

  const maxScore = useMemo(
    () => criteria.reduce((sum, c) => sum + (c.maxPoints ?? 0), 0),
    [criteria]
  );

  return (
    <div className="flex h-full flex-col">
      {/* Score total */}
      <div className="mb-3 flex items-center justify-between rounded bg-slate-50 px-3 py-2">
        <span className="text-sm font-medium text-slate-600">Total Score</span>
        <span className="font-mono text-lg font-bold text-slate-900">
          {totalScore.toFixed(1)} / {maxScore.toFixed(1)}
        </span>
      </div>

      {/* Scrollable criterion cards */}
      <div className="flex-1 space-y-3 overflow-y-auto pr-1">
        {criteria.map((criterion) => {
          const cid = criterion.id!;
          const analysis = analysisByCriterion.get(cid);
          const criterionSuggested = filteredSuggested.filter((m) => m.criterionId === cid);
          const criterionConfirmed = record.confirmedMatches.filter((m) => m.criterionId === cid);
          const draft = store.draftScores.find((s) => s.criterionId === cid);
          const hasScore =
            draft?.overridePoints !== null ||
            (draft?.selectedLevelId !== null && draft?.selectedLevelId !== undefined);
          const hasStale = record.suggestedMatches.some(
            (m) => m.criterionId === cid && m.isStale
          );
          const state = getCriterionState(
            criterion,
            analysis,
            criterionSuggested,
            criterionConfirmed,
            Boolean(hasScore),
            hasStale
          );
          const isSelected = selectedCriterionId === cid;

          return (
            <CriterionCard
              key={cid}
              criterion={criterion}
              state={state}
              suggestedCount={criterionSuggested.length}
              confirmedCount={criterionConfirmed.length}
              isSelected={isSelected}
              onSelect={() => onSelectCriterion(cid)}
              draft={draft}
            />
          );
        })}
      </div>
    </div>
  );
}

function CriterionCard({
  criterion,
  state,
  suggestedCount,
  confirmedCount,
  isSelected,
  onSelect,
  draft,
}: {
  criterion: Criterion;
  state: CriterionState;
  suggestedCount: number;
  confirmedCount: number;
  isSelected: boolean;
  onSelect: () => void;
  draft: { criterionId: string; selectedLevelId: string | null; overridePoints: number | null; criterionFeedback: string } | undefined;
}) {
  const store = useGradingStore();
  const cid = criterion.id!;

  const handleLevelSelect = (levelId: string | null) => {
    store.updateScore(cid, { selectedLevelId: levelId, overridePoints: null });
  };

  const handleOverrideChange = (raw: string) => {
    if (raw.trim() === "") {
      store.updateScore(cid, { overridePoints: null });
      return;
    }
    const parsed = Number(raw);
    if (!Number.isFinite(parsed)) return;
    if (parsed < 0) return;
    if (criterion.maxPoints !== null && parsed > criterion.maxPoints) return;
    store.updateScore(cid, { overridePoints: parsed });
  };

  const stateBadge = () => {
    switch (state) {
      case "analysis-in-progress":
        return <Badge tone="info">Analysing...</Badge>;
      case "analysis-unavailable":
        return <Badge tone="warn">Analysis unavailable</Badge>;
      case "no-evidence-found":
        return <Badge tone="neutral">No evidence found</Badge>;
      case "stale":
        return <Badge tone="warn">Stale</Badge>;
      default:
        return null;
    }
  };

  return (
    <section
      onClick={onSelect}
      className={`cursor-pointer rounded-lg border p-3 transition-all ${
        isSelected
          ? "border-slate-800 bg-white ring-1 ring-slate-800"
          : "border-slate-200 bg-white hover:border-slate-300"
      }`}
    >
      {/* Header */}
      <div className="flex items-start gap-2">
        <span
          className="mt-1 inline-block h-3 w-3 shrink-0 rounded-sm"
          style={{ backgroundColor: criterion.displayColor }}
          aria-hidden="true"
        />
        <div className="min-w-0 flex-1">
          <h3 className="text-sm font-semibold text-slate-900">{criterion.title}</h3>
          {criterion.description && (
            <p className="mt-0.5 text-xs text-slate-500 line-clamp-2">{criterion.description}</p>
          )}
        </div>
        <span className="shrink-0 font-mono text-sm font-semibold text-slate-700">
          {draft?.overridePoints !== null && draft?.overridePoints !== undefined
            ? draft.overridePoints.toFixed(1)
            : draft?.selectedLevelId
              ? (() => {
                  const level = criterion.performanceLevels.find(
                    (l) => l.id === draft.selectedLevelId
                  );
                  return level?.points !== null && level?.points !== undefined
                    ? level.points.toFixed(1)
                    : "—";
                })()
              : "—"}
          <span className="text-slate-400"> / {criterion.maxPoints ?? "?"}</span>
        </span>
      </div>

      {/* Match counts + state badges */}
      <div className="mt-2 flex flex-wrap items-center gap-1.5">
        {confirmedCount > 0 && <Badge tone="ok">{confirmedCount} confirmed</Badge>}
        {suggestedCount > 0 && <Badge tone="info">{suggestedCount} suggested</Badge>}
        {stateBadge()}
      </div>

      {/* Performance level selection */}
      {isSelected && (
        <div className="mt-3">
          <fieldset>
            <legend className="text-xs font-medium text-slate-600">Performance Level</legend>
            <div className="mt-1 flex flex-wrap gap-1.5">
              {criterion.performanceLevels.map((level) => {
                const levelId = level.id!;
                const active = draft?.selectedLevelId === levelId;
                return (
                  <button
                    key={levelId}
                    type="button"
                    aria-pressed={active}
                    onClick={(e) => {
                      e.stopPropagation();
                      handleLevelSelect(active ? null : levelId);
                    }}
                    title={level.description ?? undefined}
                    className={`rounded border px-2 py-1 text-xs transition-colors ${
                      active
                        ? "border-slate-900 bg-slate-900 text-white"
                        : "border-slate-300 bg-white text-slate-700 hover:bg-slate-50"
                    }`}
                  >
                    {level.label}
                    {level.points !== null && (
                      <span className="ml-1 opacity-70">({level.points})</span>
                    )}
                  </button>
                );
              })}
            </div>
          </fieldset>

          {/* Manual point override */}
          <div className="mt-3">
            <label
              htmlFor={`override-${cid}`}
              className="block text-xs font-medium text-slate-600"
            >
              Override points (0 – {criterion.maxPoints ?? "max"})
            </label>
            <input
              id={`override-${cid}`}
              type="number"
              min={0}
              max={criterion.maxPoints ?? undefined}
              step="0.01"
              value={
                draft?.overridePoints !== null && draft?.overridePoints !== undefined
                  ? draft.overridePoints
                  : ""
              }
              onClick={(e) => e.stopPropagation()}
              onChange={(e) => handleOverrideChange(e.target.value)}
              placeholder={`0 – ${criterion.maxPoints ?? "max"}`}
              className="mt-1 w-full rounded border border-slate-300 px-2 py-1 text-sm"
            />
          </div>

          {/* Per-criterion feedback */}
          <div className="mt-3">
            <label
              htmlFor={`criterion-fb-${cid}`}
              className="block text-xs font-medium text-slate-600"
            >
              Criterion feedback
              <span className="ml-1 text-slate-400">
                {(draft?.criterionFeedback ?? "").length}/2000
              </span>
            </label>
            <textarea
              id={`criterion-fb-${cid}`}
              rows={2}
              maxLength={2000}
              value={draft?.criterionFeedback ?? ""}
              onClick={(e) => e.stopPropagation()}
              onChange={(e) => store.updateScore(cid, { criterionFeedback: e.target.value })}
              className="mt-1 w-full rounded border border-slate-300 px-2 py-1 text-sm"
            />
          </div>
        </div>
      )}
    </section>
  );
}
