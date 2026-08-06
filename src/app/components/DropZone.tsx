import { useCallback, useState, useRef } from "react";

interface DropZoneProps {
  /** Accepted file extensions (e.g. [".pdf", ".csv", ".xlsx"]) */
  accept: string[];
  /** Max file size in bytes */
  maxSize: number;
  /** Max number of files to accept */
  maxFiles?: number;
  /** Whether multiple files can be dropped */
  multiple?: boolean;
  /** Callback when valid files are dropped/selected */
  onFiles: (files: File[]) => void;
  /** Label shown in the drop zone */
  label?: string;
  /** Hint text below the label */
  hint?: string;
  /** Disable the zone */
  disabled?: boolean;
}

export default function DropZone({
  accept,
  maxSize,
  maxFiles = 1,
  multiple = false,
  onFiles,
  label = "Drop files here or click to browse",
  hint,
  disabled = false,
}: DropZoneProps) {
  const [isDragging, setIsDragging] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const validateFiles = useCallback(
    (files: File[]): { valid: File[]; errors: string[] } => {
      const errors: string[] = [];
      const valid: File[] = [];

      if (files.length > maxFiles) {
        errors.push(`Too many files. Maximum is ${maxFiles}.`);
        return { valid: [], errors };
      }

      for (const file of files) {
        const ext = "." + file.name.split(".").pop()?.toLowerCase();
        if (!accept.includes(ext)) {
          errors.push(`"${file.name}" has unsupported format. Accepted: ${accept.join(", ")}`);
          continue;
        }
        if (file.size > maxSize) {
          const sizeMB = (maxSize / (1024 * 1024)).toFixed(0);
          errors.push(`"${file.name}" exceeds ${sizeMB}MB limit.`);
          continue;
        }
        valid.push(file);
      }

      return { valid, errors };
    },
    [accept, maxSize, maxFiles]
  );

  const handleFiles = useCallback(
    (files: File[]) => {
      setError(null);
      const { valid, errors } = validateFiles(files);
      if (errors.length > 0) {
        setError(errors.join(" "));
      }
      if (valid.length > 0) {
        onFiles(valid);
      }
    },
    [validateFiles, onFiles]
  );

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      setIsDragging(false);
      if (disabled) return;
      const files = Array.from(e.dataTransfer.files);
      handleFiles(files);
    },
    [disabled, handleFiles]
  );

  const handleDragOver = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      if (!disabled) setIsDragging(true);
    },
    [disabled]
  );

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
  }, []);

  const handleInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const files = Array.from(e.target.files ?? []);
      handleFiles(files);
      if (inputRef.current) inputRef.current.value = "";
    },
    [handleFiles]
  );

  const acceptStr = accept.join(",");

  return (
    <div className="space-y-2">
      <div
        role="button"
        tabIndex={disabled ? -1 : 0}
        aria-label={label}
        onClick={() => !disabled && inputRef.current?.click()}
        onKeyDown={(e) => {
          if ((e.key === "Enter" || e.key === " ") && !disabled) {
            e.preventDefault();
            inputRef.current?.click();
          }
        }}
        onDrop={handleDrop}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        className={`cursor-pointer rounded-lg border-2 border-dashed p-8 text-center transition-colors ${
          disabled
            ? "cursor-not-allowed border-slate-200 bg-slate-50 opacity-60"
            : isDragging
              ? "border-blue-400 bg-blue-50"
              : "border-slate-300 bg-white hover:border-slate-400 hover:bg-slate-50"
        }`}
      >
        <input
          ref={inputRef}
          type="file"
          accept={acceptStr}
          multiple={multiple}
          onChange={handleInputChange}
          className="hidden"
          aria-hidden="true"
          tabIndex={-1}
        />
        <p className="text-sm font-medium text-slate-700">{label}</p>
        {hint && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
      </div>
      {error && (
        <p className="text-xs text-red-700" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
