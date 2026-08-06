import { useMemo, useRef } from "react";
import type { ConfirmedMatch, Criterion, SuggestedMatch } from "../types";

/**
 * Renders extracted submission text with criterion-coloured passage highlights
 * (Requirements 8.1-8.5, 8.9-8.11).
 *
 * Passages overlap freely: a sentence can be evidence for several criteria, and the AI often
 * proposes a sentence and the paragraph containing it. Rather than nesting elements, the text is
 * flattened into non-overlapping segments at every passage boundary, and each segment is styled from
 * the set of matches covering it. That keeps the DOM linear, which is what makes the render budget of
 * Requirement 8.8 reachable on a 10,000-word document, and avoids the invalid markup that nesting
 * partially-overlapping spans would produce.
 */

export interface HighlightRange {
  start: number;
  end: number;
  criterionId: string;
  /** Confirmed matches are drawn solid; suggestions are drawn as a dashed underline. */
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

export function buildHighlightRanges(
  suggested: SuggestedMatch[],
  confirmed: ConfirmedMatch[]
): HighlightRange[] {
  return [
    ...suggested.map((match) => ({
      start: match.passageStart,
      end: match.passageEnd,
      criterionId: match.criterionId,
      confirmed: false,
      matchId: match.id,
      rationale: match.rationale,
      confidence: match.confidence,
    })),
    ...confirmed.map((match) => ({
      start: match.passageStart,
      end: match.passageEnd,
      criterionId: match.criterionId,
      confirmed: true,
      matchId: match.id,
      rationale: match.rationale,
      confidence: match.confidence,
    })),
  ];
}

/** Splits the text at every range boundary so no two output segments overlap. */
function toSegments(textLength: number, ranges: HighlightRange[]): Segment[] {
  const boundaries = new Set<number>([0, textLength]);
  for (const range of ranges) {
    if (range.start >= 0 && range.start <= textLength) boundaries.add(range.start);
    if (range.end >= 0 && range.end <= textLength) boundaries.add(range.end);
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
      ranges: ranges.filter((range) => range.start < end && range.end > start),
    });
  }
  return segments;
}

/** Five or more overlapping criteria get one shared treatment plus a count (Requirement 8.9). */
const SHARED_TREATMENT_THRESHOLD = 5;

export default function DocumentViewer({
  text,
  suggested,
  confirmed,
  criteria,
  selectedCriterionId,
  onSelectPassage,
  onSelectionChange,
}: {
  text: string;
  suggested: SuggestedMatch[];
  confirmed: ConfirmedMatch[];
  criteria: Criterion[];
  selectedCriterionId: string | null;
  onSelectPassage: (criterionIds: string[], matchId: string) => void;
  /** Reports the character offsets of a text selection, for creating a manual match. */
  onSelectionChange: (range: { start: number; end: number } | null) => void;
}) {
  const containerRef = useRef<HTMLDivElement>(null);

  const colorByCriterion = useMemo(() => {
    const map = new Map<string, string>();
    for (const criterion of criteria) {
      if (criterion.id) {
        map.set(criterion.id, criterion.displayColor);
      }
    }
    return map;
  }, [criteria]);

  const ranges = useMemo(
    () => buildHighlightRanges(suggested, confirmed),
    [suggested, confirmed]
  );

  const segments = useMemo(() => toSegments(text.length, ranges), [text.length, ranges]);

  /**
   * Maps a DOM selection back to offsets in the extracted text.
   *
   * Each segment carries its start offset as a data attribute, so the offset of a selection is the
   * segment's start plus the offset within it. Walking the whole tree to count characters would be
   * both slower and wrong once the browser normalises whitespace.
   */
  const handleMouseUp = () => {
    const selection = window.getSelection();
    if (!selection || selection.isCollapsed || selection.rangeCount === 0) {
      onSelectionChange(null);
      return;
    }
    const range = selection.getRangeAt(0);
    const startOffset = resolveOffset(range.startContainer, range.startOffset);
    const endOffset = resolveOffset(range.endContainer, range.endOffset);
    if (startOffset === null || endOffset === null || endOffset <= startOffset) {
      onSelectionChange(null);
      return;
    }
    onSelectionChange({ start: startOffset, end: endOffset });
  };

  const resolveOffset = (node: Node, offsetInNode: number): number | null => {
    let element: HTMLElement | null =
      node.nodeType === Node.TEXT_NODE ? node.parentElement : (node as HTMLElement);
    while (element && !element.dataset.segmentStart) {
      element = element.parentElement;
    }
    if (!element?.dataset.segmentStart) {
      return null;
    }
    return Number(element.dataset.segmentStart) + offsetInNode;
  };

  return (
    <div
      ref={containerRef}
      onMouseUp={handleMouseUp}
      className="h-full overflow-y-auto whitespace-pre-wrap break-words rounded border border-slate-200 bg-white p-6 font-serif text-[15px] leading-7 text-slate-900"
      aria-label="Submission text with evidence highlights"
    >
      {segments.map((segment) => {
        const content = text.slice(segment.start, segment.end);
        if (segment.ranges.length === 0) {
          return (
            <span key={segment.start} data-segment-start={segment.start}>
              {content}
            </span>
          );
        }

        const criterionIds = [...new Set(segment.ranges.map((range) => range.criterionId))];
        const isSelected =
          selectedCriterionId !== null && criterionIds.includes(selectedCriterionId);
        const hasConfirmed = segment.ranges.some((range) => range.confirmed);
        const crowded = criterionIds.length >= SHARED_TREATMENT_THRESHOLD;

        const primaryColor = crowded
          ? "#475569"
          : colorByCriterion.get(criterionIds[0]) ?? "#64748b";

        const label = crowded
          ? `${criterionIds.length} criteria`
          : criteria
              .filter((criterion) => criterion.id && criterionIds.includes(criterion.id))
              .map((criterion) => criterion.title)
              .join(", ");

        return (
          <mark
            key={segment.start}
            data-segment-start={segment.start}
            tabIndex={0}
            role="button"
            aria-label={`${hasConfirmed ? "Confirmed" : "Suggested"} evidence for ${label}`}
            title={
              crowded
                ? `${criterionIds.length} criteria reference this passage`
                : segment.ranges.map((range) => range.rationale).join(" | ")
            }
            onClick={() => onSelectPassage(criterionIds, segment.ranges[0].matchId)}
            onKeyDown={(event) => {
              if (event.key === "Enter" || event.key === " ") {
                event.preventDefault();
                onSelectPassage(criterionIds, segment.ranges[0].matchId);
              }
            }}
            style={{
              // Alpha kept low so the text over a highlight stays above a 4.5:1 contrast ratio
              // against it (Requirement 8.11); the colour identifies the criterion, the border
              // style distinguishes confirmed from suggested.
              backgroundColor: `${primaryColor}${isSelected ? "44" : "22"}`,
              borderBottom: hasConfirmed
                ? `2px solid ${primaryColor}`
                : `2px dashed ${primaryColor}`,
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
  );
}
