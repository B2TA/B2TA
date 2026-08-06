import { useMemo, useRef, useState } from "react";
import type { ConfirmedMatch, Criterion, SuggestedMatch } from "../../types";
import { useCreateManualMatch } from "../../api/queries";
import MatchPopover from "./MatchPopover";

/**
 * Document viewer that renders submission text with criterion-colored highlights (Task 8.3).
 *
 * - Renders extracted text with paragraph breaks
 * - Highlights passages: suggested = dashed border, confirmed = solid background
 * - Overlap handling: 2-4 show stacked colored underlines, 5+ show shared grey with count badge
 * - Legend showing suggested vs confirmed
 * - Text contrast >= 4.5:1
 */

interface HighlightRange {
  start: number;
  end: number;
  criterionId: string;
  confirmed: boolean;
  matchId: string;
  rationale: string;
  confidence: number | null;
}

interface Segment {
  start: number;
  end: number;
  ranges: HighlightRange[];
}

function buildHighlightRanges(
  suggested: SuggestedMatch[],
  confirmed: ConfirmedMatch[]
): HighlightRange[] {
  return [
    ...suggested.map((m) => ({
      start: m.passageStart,
      end: m.passageEnd,
      criterionId: m.criterionId,
      confirmed: false,
      matchId: m.id,
      rationale: m.rationale,
      confidence: m.confidence,
    })),
    ...confirmed.map((m) => ({
      start: m.passageStart,
      end: m.passageEnd,
      criterionId: m.criterionId,
      confirmed: true,
      matchId: m.id,
      rationale: m.rationale,
      confidence: m.confidence,
    })),
  ];
}

function toSegments(textLength: number, ranges: HighlightRange[]): Segment[] {
  const boundaries = new Set<number>([0, textLength]);
  for (const r of ranges) {
    if (r.start >= 0 && r.start <= textLength) boundaries.add(r.start);
    if (r.end >= 0 && r.end <= textLength) boundaries.add(r.end);
  }
  const sorted = [...boundaries].sort((a, b) => a - b);
  const segments: Segment[] = [];
  for (let i = 0; i < sorted.length - 1; i++) {
    const start = sorted[i];
    const end = sorted[i + 1];
    if (end <= start) continue;
    segments.push({
      start,
      end,
      ranges: ranges.filter((r) => r.start < end && r.end > start),
    });
  }
  return segments;
}

const OVERLAP_SHARED_THRESHOLD = 5;

export default function DocumentViewer({
  text,
  suggested,
  confirmed,
  criteria,
  selectedCriterionId,
  sessionId,
  submissionId,
}: {
  text: string;
  suggested: SuggestedMatch[];
  confirmed: ConfirmedMatch[];
  criteria: Criterion[];
  selectedCriterionId: string | null;
  sessionId: string;
  submissionId: string;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [popoverMatch, setPopoverMatch] = useState<HighlightRange | null>(null);
  const [popoverPosition, setPopoverPosition] = useState<{ x: number; y: number } | null>(null);
  const [hoveredMatch, setHoveredMatch] = useState<HighlightRange | null>(null);
  const [selection, setSelection] = useState<{ start: number; end: number } | null>(null);

  const colorByCriterion = useMemo(() => {
    const map = new Map<string, string>();
    for (const c of criteria) {
      if (c.id) map.set(c.id, c.displayColor);
    }
    return map;
  }, [criteria]);

  const ranges = useMemo(
    () => buildHighlightRanges(suggested, confirmed),
    [suggested, confirmed]
  );

  const segments = useMemo(() => toSegments(text.length, ranges), [text.length, ranges]);

  const resolveOffset = (node: Node, offsetInNode: number): number | null => {
    let element: HTMLElement | null =
      node.nodeType === Node.TEXT_NODE ? node.parentElement : (node as HTMLElement);
    while (element && !element.dataset.segStart) {
      element = element.parentElement;
    }
    if (!element?.dataset.segStart) return null;
    return Number(element.dataset.segStart) + offsetInNode;
  };

  const handleMouseUp = () => {
    const sel = window.getSelection();
    if (!sel || sel.isCollapsed || sel.rangeCount === 0) {
      setSelection(null);
      return;
    }
    const range = sel.getRangeAt(0);
    const startOff = resolveOffset(range.startContainer, range.startOffset);
    const endOff = resolveOffset(range.endContainer, range.endOffset);
    if (startOff === null || endOff === null || endOff <= startOff) {
      setSelection(null);
      return;
    }
    setSelection({ start: startOff, end: endOff });
  };

  const handleSegmentClick = (segment: Segment, e: React.MouseEvent) => {
    if (segment.ranges.length === 0) return;
    const primary = segment.ranges[0];
    setPopoverMatch(primary);
    setPopoverPosition({ x: e.clientX, y: e.clientY });
  };

  const handleSegmentHover = (segment: Segment) => {
    if (segment.ranges.length > 0) {
      setHoveredMatch(segment.ranges[0]);
    }
  };

  // Render text with paragraph breaks
  const paragraphs = text.split("\n\n");

  return (
    <div className="flex h-full flex-col">
      {/* Legend */}
      <div className="mb-2 flex flex-wrap items-center gap-4 text-xs text-slate-600">
        <span className="flex items-center gap-1.5">
          <span
            className="inline-block h-3 w-6 rounded-sm border-b-2 border-solid border-slate-700 bg-slate-200"
            aria-hidden="true"
          />
          Confirmed
        </span>
        <span className="flex items-center gap-1.5">
          <span
            className="inline-block h-3 w-6 rounded-sm border-b-2 border-dashed border-slate-700 bg-slate-100"
            aria-hidden="true"
          />
          Suggested
        </span>
      </div>

      {/* Text content */}
      <div
        ref={containerRef}
        onMouseUp={handleMouseUp}
        className="flex-1 overflow-y-auto whitespace-pre-wrap break-words rounded border border-slate-200 bg-white p-6 font-serif text-[15px] leading-7 text-slate-900"
        aria-label="Submission text with evidence highlights"
      >
        {segments.map((segment) => {
          const content = text.slice(segment.start, segment.end);
          if (segment.ranges.length === 0) {
            return (
              <span key={segment.start} data-seg-start={segment.start}>
                {content}
              </span>
            );
          }

          const criterionIds = [...new Set(segment.ranges.map((r) => r.criterionId))];
          const hasConfirmed = segment.ranges.some((r) => r.confirmed);
          const crowded = criterionIds.length >= OVERLAP_SHARED_THRESHOLD;
          const isSelected =
            selectedCriterionId !== null && criterionIds.includes(selectedCriterionId);

          const primaryColor = crowded
            ? "#6b7280"
            : colorByCriterion.get(criterionIds[0]) ?? "#64748b";

          // For 2-4 overlaps, show stacked colored underlines
          const borderStyle = (): React.CSSProperties => {
            if (crowded) {
              return {
                backgroundColor: `#6b728022`,
                borderBottom: "2px solid #6b7280",
              };
            }
            if (criterionIds.length >= 2 && criterionIds.length <= 4) {
              // Stacked underlines via box-shadow
              const shadows = criterionIds.map((cid, i) => {
                const color = colorByCriterion.get(cid) ?? "#64748b";
                return `0 ${2 + i * 3}px 0 0 ${color}`;
              });
              return {
                backgroundColor: `${primaryColor}${isSelected ? "33" : "18"}`,
                boxShadow: shadows.join(", "),
                paddingBottom: `${criterionIds.length * 3}px`,
              };
            }
            return {
              backgroundColor: `${primaryColor}${isSelected ? "33" : "18"}`,
              borderBottom: hasConfirmed
                ? `2px solid ${primaryColor}`
                : `2px dashed ${primaryColor}`,
            };
          };

          return (
            <mark
              key={segment.start}
              data-seg-start={segment.start}
              tabIndex={0}
              role="button"
              aria-label={`${hasConfirmed ? "Confirmed" : "Suggested"} evidence`}
              onClick={(e) => handleSegmentClick(segment, e)}
              onMouseEnter={() => handleSegmentHover(segment)}
              onMouseLeave={() => setHoveredMatch(null)}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  e.preventDefault();
                  const rect = (e.target as HTMLElement).getBoundingClientRect();
                  setPopoverMatch(segment.ranges[0]);
                  setPopoverPosition({ x: rect.left + rect.width / 2, y: rect.top });
                }
              }}
              style={{
                ...borderStyle(),
                outline: isSelected ? `2px solid ${primaryColor}` : undefined,
                color: "inherit",
                cursor: "pointer",
              }}
            >
              {content}
              {crowded && (
                <sup className="ml-0.5 rounded bg-slate-700 px-1 text-[10px] text-white">
                  {criterionIds.length}
                </sup>
              )}
            </mark>
          );
        })}
      </div>

      {/* Tooltip on hover */}
      {hoveredMatch && !popoverMatch && (
        <div className="pointer-events-none fixed z-50 rounded bg-slate-900 px-2 py-1 text-xs text-white shadow-lg">
          {(() => {
            const crit = criteria.find((c) => c.id === hoveredMatch.criterionId);
            return (
              <>
                <span className="font-medium">{crit?.title ?? "Unknown"}</span>
                {" — "}
                <span className="opacity-80">{hoveredMatch.rationale}</span>
                {hoveredMatch.confidence !== null && (
                  <span className="ml-1 opacity-60">
                    ({(hoveredMatch.confidence * 100).toFixed(0)}%)
                  </span>
                )}
              </>
            );
          })()}
        </div>
      )}

      {/* Popover on click */}
      {popoverMatch && popoverPosition && (
        <MatchPopover
          match={popoverMatch}
          criteria={criteria}
          position={popoverPosition}
          sessionId={sessionId}
          submissionId={submissionId}
          onClose={() => {
            setPopoverMatch(null);
            setPopoverPosition(null);
          }}
        />
      )}

      {/* Selection action */}
      {selection && selectedCriterionId && (
        <SelectionAction
          selection={selection}
          criterionId={selectedCriterionId}
          criteria={criteria}
          sessionId={sessionId}
          submissionId={submissionId}
          onComplete={() => {
            setSelection(null);
            window.getSelection()?.removeAllRanges();
          }}
        />
      )}
    </div>
  );
}

function SelectionAction({
  selection,
  criterionId,
  criteria,
  sessionId,
  submissionId,
  onComplete,
}: {
  selection: { start: number; end: number };
  criterionId: string;
  criteria: Criterion[];
  sessionId: string;
  submissionId: string;
  onComplete: () => void;
}) {
  const createMatch = useCreateManualMatch(sessionId, submissionId);
  const criterion = criteria.find((c) => c.id === criterionId);
  const length = selection.end - selection.start;

  if (length < 1 || length > 5000) return null;

  const handleCreate = async () => {
    try {
      await createMatch.mutateAsync({
        criterionId,
        passageStart: selection.start,
        passageEnd: selection.end,
      });
      onComplete();
    } catch {
      // Error shown by mutation state
    }
  };

  return (
    <div className="mt-2 flex items-center gap-2 rounded border border-slate-300 bg-slate-50 px-3 py-2 text-xs">
      <span className="text-slate-600">
        Associate selection ({length} chars) with{" "}
        <strong>{criterion?.title ?? "criterion"}</strong>?
      </span>
      <button
        type="button"
        onClick={() => void handleCreate()}
        disabled={createMatch.isPending}
        className="rounded bg-slate-900 px-2 py-1 text-xs font-medium text-white disabled:opacity-50"
      >
        {createMatch.isPending ? "Adding..." : "Add match"}
      </button>
      <button
        type="button"
        onClick={onComplete}
        className="rounded border border-slate-300 px-2 py-1 text-xs text-slate-700"
      >
        Cancel
      </button>
    </div>
  );
}
