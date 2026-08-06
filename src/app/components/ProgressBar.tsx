interface ProgressBarProps {
  /** Current progress (0 to total) */
  current: number;
  /** Total items */
  total: number;
  /** Optional label like "Uploading 3 of 10 files..." */
  label?: string;
  /** Color tone */
  tone?: "blue" | "green" | "amber";
}

export default function ProgressBar({
  current,
  total,
  label,
  tone = "blue",
}: ProgressBarProps) {
  const pct = total > 0 ? Math.min(100, Math.round((current / total) * 100)) : 0;

  const barColors: Record<string, string> = {
    blue: "bg-blue-600",
    green: "bg-emerald-600",
    amber: "bg-amber-500",
  };

  return (
    <div className="space-y-1">
      {label && (
        <div className="flex items-center justify-between">
          <span className="text-xs text-slate-600">{label}</span>
          <span className="text-xs font-medium text-slate-700">{pct}%</span>
        </div>
      )}
      <div
        className="h-2 w-full overflow-hidden rounded-full bg-slate-200"
        role="progressbar"
        aria-valuenow={current}
        aria-valuemin={0}
        aria-valuemax={total}
        aria-label={label ?? `${pct}% complete`}
      >
        <div
          className={`h-full rounded-full transition-all duration-300 ${barColors[tone]}`}
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  );
}
