import { useMemo } from "react";
import type { ConfirmedMatch, Criterion, SuggestedMatch } from "../../types";

/**
 * Navigation between criteria and passages (Task 8.5).
 *
 * - Click criterion -> scroll document viewer to first passage
 * - Next/previous passage buttons with "2 of 5" indicator
 * - Zero-passage state: "No evidence found" message
 */

export default function PassageNavigation({
  criteria,
  suggested,
  confirmed,
  selectedCriterionId,
  activePassageIndex,
  onPassageIndexChange,
  onSelectCriterion,
}: {
  criteria: Criterion[];
  suggested: SuggestedMatch[];
  confirmed: ConfirmedMatch[];
  selectedCriterionId: string | null;
  activePassageIndex: number;
  onPassageIndexChange: (index: number) => void;
  onSelectCriterion: (id: string) => void;
}) {
  // Get all passages for the selected criterion
  const passages = useMemo(() => {
    if (!selectedCriterionId) return [];
    const all = [
      ...confirmed
        .filter((m) => m.criterionId === selectedCriterionId)
        .map((m) => ({ start: m.passageStart, end: m.passageEnd, type: "confirmed" as const })),
      ...suggested
        .filter((m) => m.criterionId === selectedCriterionId)
        .map((m) => ({ start: m.passageStart, end: m.passageEnd, type: "suggested" as const })),
    ];
    return all.sort((a, b) => a.start - b.start);
  }, [selectedCriterionId, suggested, confirmed]);

  const selectedCriterion = criteria.find((c) => c.id === selectedCriterionId);
  const totalPassages = passages.length;
  const safeIndex = Math.min(activePassageIndex, Math.max(0, totalPassages - 1));

  const handlePrev = () => {
    if (safeIndex > 0) onPassageIndexChange(safeIndex - 1);
  };

  const handleNext = () => {
    if (safeIndex < totalPassages - 1) onPassageIndexChange(safeIndex + 1);
  };

  return (
    <div className="flex items-center gap-3 rounded border border-slate-200 bg-white px-3 py-2">
      {/* Criterion chips */}
      <div className="flex flex-wrap items-center gap-1">
        {criteria.map((c) => {
          const cid = c.id!;
          const isActive = cid === selectedCriterionId;
          const matchCount =
            suggested.filter((m) => m.criterionId === cid).length +
            confirmed.filter((m) => m.criterionId === cid).length;
          return (
            <button
              key={cid}
              type="button"
              onClick={() => {
                onSelectCriterion(cid);
                onPassageIndexChange(0);
              }}
              className={`flex items-center gap-1 rounded-full px-2 py-0.5 text-xs transition-colors ${
                isActive
                  ? "bg-slate-900 text-white"
                  : "bg-slate-100 text-slate-600 hover:bg-slate-200"
              }`}
              title={c.title}
            >
              <span
                className="inline-block h-2 w-2 rounded-full"
                style={{ backgroundColor: c.displayColor }}
                aria-hidden="true"
              />
              <span className="max-w-[60px] truncate">{c.title}</span>
              {matchCount > 0 && (
                <span className={`ml-0.5 ${isActive ? "opacity-70" : "text-slate-400"}`}>
                  {matchCount}
                </span>
              )}
            </button>
          );
        })}
      </div>

      {/* Navigation controls */}
      <div className="ml-auto flex items-center gap-2">
        {selectedCriterion && totalPassages === 0 && (
          <span className="text-xs italic text-slate-400">No evidence found</span>
        )}

        {totalPassages > 0 && (
          <>
            <button
              type="button"
              onClick={handlePrev}
              disabled={safeIndex <= 0}
              className="rounded border border-slate-300 px-2 py-1 text-xs disabled:opacity-40"
              aria-label="Previous passage"
            >
              &larr;
            </button>
            <span className="font-mono text-xs text-slate-600">
              {safeIndex + 1} of {totalPassages}
            </span>
            <button
              type="button"
              onClick={handleNext}
              disabled={safeIndex >= totalPassages - 1}
              className="rounded border border-slate-300 px-2 py-1 text-xs disabled:opacity-40"
              aria-label="Next passage"
            >
              &rarr;
            </button>
          </>
        )}
      </div>
    </div>
  );
}
