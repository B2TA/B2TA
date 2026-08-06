/**
 * Confidence threshold slider (Task 8.6).
 *
 * - Range slider 0.00-1.00
 * - Hides matches below threshold (keeps in store)
 * - Shows hidden count per criterion
 * - Stale match indicator with "Re-analyze" button
 */

export default function ConfidenceFilter({
  threshold,
  onThresholdChange,
  hiddenCount,
}: {
  threshold: number;
  onThresholdChange: (value: number) => void;
  hiddenCount: number;
}) {
  return (
    <div className="flex items-center gap-2">
      <label htmlFor="confidence-slider" className="text-xs font-medium text-slate-600">
        Min confidence
      </label>
      <input
        id="confidence-slider"
        type="range"
        min={0}
        max={1}
        step={0.01}
        value={threshold}
        onChange={(e) => onThresholdChange(Number(e.target.value))}
        className="h-1.5 w-24 cursor-pointer appearance-none rounded-full bg-slate-200 accent-slate-700"
        aria-valuemin={0}
        aria-valuemax={1}
        aria-valuenow={threshold}
        aria-label="Confidence threshold"
      />
      <span className="font-mono text-xs text-slate-700">{threshold.toFixed(2)}</span>
      {hiddenCount > 0 && (
        <span className="rounded bg-amber-100 px-1.5 py-0.5 text-xs text-amber-800">
          {hiddenCount} hidden
        </span>
      )}
    </div>
  );
}
