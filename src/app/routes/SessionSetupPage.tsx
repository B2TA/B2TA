import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core";
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import AppShell from "../components/AppShell";
import { Badge, EmptyState, ErrorBanner, LoadingPanel } from "../components/Feedback";
import DropZone from "../components/DropZone";
import ProgressBar from "../components/ProgressBar";
import {
  useConfirmIdentities,
  useExportRubric,
  useRubric,
  useSaveRubric,
  useSession,
  useSubmissions,
  useUpdateIdentity,
} from "../api/queries";
import * as endpoints from "../api/endpoints";
import { uploadFileToS3, uploadFilesParallel } from "../api/upload";
import { useJobPolling } from "../hooks/useJobPolling";
import type { Criterion } from "../types";

// --- Validation helpers ---

function validateCriterion(c: Criterion): string[] {
  const errors: string[] = [];
  if (!c.title || c.title.length < 1 || c.title.length > 200) {
    errors.push("Title must be 1-200 characters");
  }
  if (c.description && c.description.length > 2000) {
    errors.push("Description must be at most 2000 characters");
  }
  if (c.maxPoints === null || c.maxPoints < 0.01 || c.maxPoints > 1000) {
    errors.push("Max points must be between 0.01 and 1000");
  }
  if (c.performanceLevels.length < 1 || c.performanceLevels.length > 10) {
    errors.push("Must have 1-10 performance levels");
  }
  return errors;
}

function isRubricValid(criteria: Criterion[]): boolean {
  if (criteria.length === 0) return false;
  return criteria.every((c) => validateCriterion(c).length === 0);
}

// --- Sortable Criterion Item ---

function SortableCriterionItem({
  criterion,
  index,
  onUpdate,
  onRemove,
}: {
  criterion: Criterion;
  index: number;
  onUpdate: (index: number, patch: Partial<Criterion>) => void;
  onRemove: (index: number) => void;
}) {
  const id = `criterion-${index}`;
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id,
  });

  const style = {
    transform: transform ? `translate3d(${transform.x}px, ${transform.y}px, 0)` : undefined,
    transition,
    opacity: isDragging ? 0.5 : 1,
  };

  const errors = validateCriterion(criterion);

  return (
    <fieldset ref={setNodeRef} style={style} className="rounded border border-slate-200 p-3">
      <legend className="flex items-center gap-2 px-1 text-xs font-medium text-slate-500">
        <button
          type="button"
          className="cursor-grab touch-none text-slate-400 hover:text-slate-600"
          {...attributes}
          {...listeners}
          aria-label={`Drag to reorder criterion ${index + 1}`}
        >
          ⠿
        </button>
        Criterion {index + 1}
        {errors.length > 0 && (
          <span className="text-red-500" title={errors.join(", ")}>⚠</span>
        )}
      </legend>

      <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_140px_auto]">
        <div>
          <label htmlFor={`${id}-title`} className="block text-xs font-medium text-slate-600">
            Title
          </label>
          <input
            id={`${id}-title`}
            value={criterion.title}
            maxLength={200}
            onChange={(e) => onUpdate(index, { title: e.target.value })}
            className="mt-1 w-full rounded border border-slate-300 px-2 py-1 text-sm"
          />
        </div>
        <div>
          <label htmlFor={`${id}-max`} className="block text-xs font-medium text-slate-600">
            Max points
          </label>
          <input
            id={`${id}-max`}
            inputMode="decimal"
            value={criterion.maxPoints ?? ""}
            onChange={(e) => {
              const parsed = Number(e.target.value);
              onUpdate(index, {
                maxPoints: e.target.value === "" || !Number.isFinite(parsed) ? null : parsed,
              });
            }}
            className="mt-1 w-full rounded border border-slate-300 px-2 py-1 text-sm"
          />
        </div>
        <div className="flex items-end">
          <button
            type="button"
            onClick={() => onRemove(index)}
            className="rounded border border-red-300 px-2 py-1 text-xs text-red-700"
          >
            Remove
          </button>
        </div>
      </div>

      <div className="mt-3">
        <label htmlFor={`${id}-desc`} className="block text-xs font-medium text-slate-600">
          Description
        </label>
        <textarea
          id={`${id}-desc`}
          rows={2}
          maxLength={2000}
          value={criterion.description ?? ""}
          onChange={(e) => onUpdate(index, { description: e.target.value })}
          className="mt-1 w-full rounded border border-slate-300 px-2 py-1 text-sm"
        />
      </div>

      <div className="mt-3 space-y-2">
        <span className="block text-xs font-medium text-slate-600">Performance levels</span>
        {criterion.performanceLevels.map((level, levelIndex) => (
          <div key={levelIndex} className="flex flex-wrap items-center gap-2">
            <input
              aria-label={`Level ${levelIndex + 1} label`}
              value={level.label}
              maxLength={200}
              onChange={(e) => {
                const levels = [...criterion.performanceLevels];
                levels[levelIndex] = { ...level, label: e.target.value };
                onUpdate(index, { performanceLevels: levels });
              }}
              className="min-w-40 flex-1 rounded border border-slate-300 px-2 py-1 text-sm"
            />
            <input
              aria-label={`Level ${levelIndex + 1} points`}
              inputMode="decimal"
              value={level.points ?? ""}
              onChange={(e) => {
                const parsed = Number(e.target.value);
                const levels = [...criterion.performanceLevels];
                levels[levelIndex] = {
                  ...level,
                  points: e.target.value === "" || !Number.isFinite(parsed) ? null : parsed,
                };
                onUpdate(index, { performanceLevels: levels });
              }}
              className="w-24 rounded border border-slate-300 px-2 py-1 text-sm"
            />
            <button
              type="button"
              onClick={() => {
                const levels = criterion.performanceLevels.filter((_, i) => i !== levelIndex);
                onUpdate(index, { performanceLevels: levels });
              }}
              disabled={criterion.performanceLevels.length <= 1}
              className="rounded border border-slate-300 px-2 py-1 text-xs text-slate-600 disabled:opacity-40"
            >
              ×
            </button>
          </div>
        ))}
        <button
          type="button"
          onClick={() =>
            onUpdate(index, {
              performanceLevels: [
                ...criterion.performanceLevels,
                { id: null, label: "", description: "", points: null, position: criterion.performanceLevels.length },
              ],
            })
          }
          disabled={criterion.performanceLevels.length >= 10}
          className="rounded border border-slate-300 px-2 py-1 text-xs text-slate-700 disabled:opacity-40"
        >
          Add level
        </button>
      </div>
    </fieldset>
  );
}

// --- Main Page Component ---

export default function SessionSetupPage() {
  const { id: sessionId = "" } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const session = useSession(sessionId);
  const rubric = useRubric(sessionId);
  const submissions = useSubmissions(sessionId);
  const saveRubric = useSaveRubric(sessionId);
  const updateIdentity = useUpdateIdentity(sessionId);
  const confirmIdentities = useConfirmIdentities(sessionId);
  const exportRubric = useExportRubric(sessionId);
  const rubricJob = useJobPolling();
  const ingestionJob = useJobPolling();

  const [criteria, setCriteria] = useState<Criterion[]>([]);
  const [loadedRubricId, setLoadedRubricId] = useState<string | null>(null);
  const [editingName, setEditingName] = useState<Record<string, string>>({});

  // Upload states
  const [rubricUploading, setRubricUploading] = useState(false);
  const [rubricUploadError, setRubricUploadError] = useState<string | null>(null);
  const [submissionUploading, setSubmissionUploading] = useState(false);
  const [submissionUploadProgress, setSubmissionUploadProgress] = useState({ completed: 0, total: 0 });
  const [submissionUploadError, setSubmissionUploadError] = useState<string | null>(null);
  const [ingestionFailures, setIngestionFailures] = useState<string[]>([]);

  // DnD sensors
  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  // Sync criteria from server
  useEffect(() => {
    const loaded = rubric.data;
    const key = loaded?.id ?? "none";
    if (loadedRubricId === key) return;
    setLoadedRubricId(key);
    setCriteria(loaded?.criteria ?? []);
  }, [rubric.data, loadedRubricId]);

  // Refresh rubric after parse job completes
  useEffect(() => {
    if (rubricJob.isComplete) {
      rubric.refetch();
      rubricJob.reset();
    }
  }, [rubricJob.isComplete]);

  // Refresh submissions after ingestion job completes
  useEffect(() => {
    if (ingestionJob.isComplete) {
      submissions.refetch();
      ingestionJob.reset();
    }
  }, [ingestionJob.isComplete]);

  const addCriterion = () => {
    setCriteria((current) => [
      ...current,
      {
        id: null,
        title: "",
        description: "",
        maxPoints: 10,
        displayColor: "",
        position: current.length,
        requiresCompletion: false,
        performanceLevels: [
          { id: null, label: "Excellent", description: "", points: 10, position: 0 },
          { id: null, label: "Adequate", description: "", points: 6, position: 1 },
          { id: null, label: "Needs work", description: "", points: 2, position: 2 },
        ],
      },
    ]);
  };

  const updateCriterion = (index: number, patch: Partial<Criterion>) => {
    setCriteria((current) =>
      current.map((c, i) => (i === index ? { ...c, ...patch } : c))
    );
  };

  const removeCriterion = (index: number) => {
    setCriteria((current) =>
      current.filter((_, i) => i !== index).map((c, i) => ({ ...c, position: i }))
    );
  };

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const oldIndex = criteria.findIndex((_, i) => `criterion-${i}` === active.id);
    const newIndex = criteria.findIndex((_, i) => `criterion-${i}` === over.id);
    if (oldIndex === -1 || newIndex === -1) return;
    setCriteria((current) =>
      arrayMove(current, oldIndex, newIndex).map((c, i) => ({ ...c, position: i }))
    );
  };

  const handleSave = () => {
    saveRubric.mutate({
      criteria: criteria.map((c, index) => ({
        ...c,
        position: index,
        performanceLevels: c.performanceLevels.map((level, li) => ({ ...level, position: li })),
      })),
    });
  };

  const handleRubricUpload = useCallback(async (files: File[]) => {
    const file = files[0];
    if (!file) return;
    setRubricUploading(true);
    setRubricUploadError(null);
    try {
      const { uploadUrl, objectKey } = await endpoints.getRubricUploadUrl(sessionId, file.name);
      await uploadFileToS3(file, uploadUrl);
      const { jobId } = await endpoints.parseRubric(sessionId, objectKey);
      rubricJob.startPolling(jobId);
    } catch (err) {
      setRubricUploadError(err instanceof Error ? err.message : "Upload failed");
    } finally {
      setRubricUploading(false);
    }
  }, [sessionId]);

  const handleSubmissionUpload = useCallback(async (files: File[]) => {
    setSubmissionUploading(true);
    setSubmissionUploadError(null);
    setSubmissionUploadProgress({ completed: 0, total: files.length });
    setIngestionFailures([]);
    try {
      const filenames = files.map((f) => f.name);
      const { uploads } = await endpoints.getSubmissionUploadUrls(sessionId, filenames);
      const { objectKeys, failed } = await uploadFilesParallel(files, uploads, {
        concurrency: 5,
        onProgress: (p) => setSubmissionUploadProgress({ completed: p.completed, total: p.total }),
      });
      if (failed.length > 0) {
        setIngestionFailures(failed);
      }
      if (objectKeys.length > 0) {
        const { jobId } = await endpoints.ingestSubmissions(sessionId, objectKeys);
        ingestionJob.startPolling(jobId);
      }
    } catch (err) {
      setSubmissionUploadError(err instanceof Error ? err.message : "Upload failed");
    } finally {
      setSubmissionUploading(false);
    }
  }, [sessionId]);

  const handleExportRubric = async () => {
    const result = await exportRubric.mutateAsync();
    if (result?.downloadUrl) {
      window.open(result.downloadUrl, "_blank");
    }
  };

  const handleStartGrading = async () => {
    await confirmIdentities.mutateAsync();
    navigate(`/sessions/${sessionId}/mark`);
  };

  const rubricIsValid = isRubricValid(criteria);

  if (session.isLoading || rubric.isLoading) {
    return (
      <AppShell title="Session setup">
        <LoadingPanel />
      </AppShell>
    );
  }

  return (
    <AppShell
      title={session.data?.name ?? "Session setup"}
      subtitle="Rubric and submissions"
      actions={
        <div className="flex gap-2">
          <Link
            to={`/sessions/${sessionId}/mark`}
            className="rounded border border-slate-300 px-3 py-1.5 text-xs text-slate-700"
          >
            Go to marking
          </Link>
        </div>
      }
    >
      <ErrorBanner error={session.error} className="mb-4" />

      {/* === RUBRIC SECTION === */}
      <section className="mb-6 rounded border border-slate-200 bg-white p-4">
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="flex-1 text-sm font-semibold text-slate-900">Rubric</h2>
          <button
            type="button"
            onClick={handleExportRubric}
            disabled={exportRubric.isPending || criteria.length === 0}
            className="rounded border border-slate-300 px-3 py-1.5 text-xs text-slate-700 disabled:opacity-50"
          >
            {exportRubric.isPending ? "Exporting..." : "Export CSV"}
          </button>
          <button
            type="button"
            onClick={addCriterion}
            disabled={criteria.length >= 30}
            className="rounded border border-slate-300 px-3 py-1.5 text-xs text-slate-700 disabled:opacity-50"
          >
            Add criterion
          </button>
          <button
            type="button"
            onClick={handleSave}
            disabled={saveRubric.isPending || criteria.length === 0}
            className="rounded bg-slate-900 px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50"
          >
            {saveRubric.isPending ? "Saving..." : "Save rubric"}
          </button>
        </div>

        {/* Rubric Upload Zone */}
        <div className="mt-4">
          <DropZone
            accept={[".pdf", ".csv", ".xlsx"]}
            maxSize={5 * 1024 * 1024}
            onFiles={handleRubricUpload}
            label="Drop rubric file here or click to browse"
            hint="Accepts .pdf, .csv, .xlsx (max 5MB). Will be parsed into criteria below."
            disabled={rubricUploading || rubricJob.isActive}
          />
          {rubricUploadError && (
            <p className="mt-2 text-xs text-red-700" role="alert">{rubricUploadError}</p>
          )}
          {rubricJob.isActive && rubricJob.job && (
            <div className="mt-2">
              <ProgressBar
                current={rubricJob.job.progressCurrent}
                total={rubricJob.job.progressTotal || 1}
                label="Parsing rubric..."
                tone="blue"
              />
            </div>
          )}
          {rubricJob.isFailed && (
            <p className="mt-2 text-xs text-red-700" role="alert">
              Rubric parsing failed: {rubricJob.job?.failureReason ?? "Unknown error"}
            </p>
          )}
        </div>

        <ErrorBanner error={saveRubric.error} className="mt-3" />
        <ErrorBanner error={exportRubric.error} className="mt-3" />
        {saveRubric.isSuccess && (
          <p className="mt-3 text-xs text-emerald-700">Saved {criteria.length} criteria.</p>
        )}

        {criteria.length === 0 && (
          <div className="mt-4">
            <EmptyState title="No criteria yet">
              Upload a rubric file or add criteria manually.
            </EmptyState>
          </div>
        )}

        {/* Criterion Editor with Drag-and-Drop */}
        {criteria.length > 0 && (
          <div className="mt-4 space-y-4">
            <DndContext
              sensors={sensors}
              collisionDetection={closestCenter}
              onDragEnd={handleDragEnd}
            >
              <SortableContext
                items={criteria.map((_, i) => `criterion-${i}`)}
                strategy={verticalListSortingStrategy}
              >
                {criteria.map((criterion, index) => (
                  <SortableCriterionItem
                    key={`criterion-${index}`}
                    criterion={criterion}
                    index={index}
                    onUpdate={updateCriterion}
                    onRemove={removeCriterion}
                  />
                ))}
              </SortableContext>
            </DndContext>
          </div>
        )}

        {/* Validation status */}
        {criteria.length > 0 && !rubricIsValid && (
          <p className="mt-3 text-xs text-amber-700">
            Fix validation errors above before starting grading.
          </p>
        )}
      </section>

      {/* === SUBMISSION SECTION === */}
      <section className="mb-6 rounded border border-slate-200 bg-white p-4">
        <h2 className="text-sm font-semibold text-slate-900">Submissions</h2>

        {/* Submission Upload Zone */}
        <div className="mt-3">
          <DropZone
            accept={[".pdf", ".docx", ".txt", ".md", ".zip"]}
            maxSize={50 * 1024 * 1024}
            maxFiles={300}
            multiple
            onFiles={handleSubmissionUpload}
            label="Drop submission files here or click to browse"
            hint="Accepts .pdf, .docx, .txt, .md, .zip (max 50MB each, up to 300 files)"
            disabled={submissionUploading || ingestionJob.isActive}
          />

          {submissionUploadError && (
            <p className="mt-2 text-xs text-red-700" role="alert">{submissionUploadError}</p>
          )}

          {/* Upload progress */}
          {submissionUploading && submissionUploadProgress.total > 0 && (
            <div className="mt-2">
              <ProgressBar
                current={submissionUploadProgress.completed}
                total={submissionUploadProgress.total}
                label={`Uploading ${submissionUploadProgress.completed} of ${submissionUploadProgress.total} files...`}
                tone="blue"
              />
            </div>
          )}

          {/* Ingestion progress */}
          {ingestionJob.isActive && ingestionJob.job && (
            <div className="mt-2">
              <ProgressBar
                current={ingestionJob.job.progressCurrent}
                total={ingestionJob.job.progressTotal || 1}
                label={`Processing ${ingestionJob.job.progressCurrent} of ${ingestionJob.job.progressTotal} files...`}
                tone="green"
              />
            </div>
          )}

          {ingestionJob.isFailed && (
            <p className="mt-2 text-xs text-red-700" role="alert">
              Ingestion failed: {ingestionJob.job?.failureReason ?? "Unknown error"}
            </p>
          )}
        </div>

        {/* Ingestion Report: upload failures */}
        {ingestionFailures.length > 0 && (
          <div className="mt-3 rounded border border-amber-200 bg-amber-50 p-3">
            <p className="text-xs font-medium text-amber-800">
              {ingestionFailures.length} file(s) failed to upload:
            </p>
            <ul className="mt-1 list-inside list-disc text-xs text-amber-700">
              {ingestionFailures.map((name) => (
                <li key={name}>{name}</li>
              ))}
            </ul>
          </div>
        )}

        <ErrorBanner
          error={submissions.error}
          onRetry={() => void submissions.refetch()}
          className="mt-3"
        />

        {/* Student Confirmation Table */}
        {submissions.data?.length === 0 && (
          <div className="mt-4">
            <EmptyState title="No submissions in this session yet" />
          </div>
        )}

        {submissions.data && submissions.data.length > 0 && (
          <>
            <div className="mt-4 overflow-hidden rounded border border-slate-200">
              <table className="w-full text-left text-sm">
                <caption className="sr-only">Submissions</caption>
                <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th scope="col" className="px-3 py-2 w-12">#</th>
                    <th scope="col" className="px-3 py-2">Student name</th>
                    <th scope="col" className="px-3 py-2">File</th>
                    <th scope="col" className="px-3 py-2">Status</th>
                    <th scope="col" className="px-3 py-2 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {submissions.data.map((sub) => (
                    <tr key={sub.id}>
                      <td className="px-3 py-2 text-slate-400">{sub.position + 1}</td>
                      <td className="px-3 py-2">
                        <input
                          aria-label={`Student name for ${sub.originalFilename}`}
                          value={editingName[sub.id] ?? sub.studentDisplayName}
                          onChange={(e) =>
                            setEditingName((cur) => ({ ...cur, [sub.id]: e.target.value }))
                          }
                          className="w-full min-w-32 rounded border border-slate-300 px-2 py-1 text-sm"
                        />
                      </td>
                      <td className="px-3 py-2 text-xs text-slate-500 max-w-48 truncate">
                        {sub.originalFilename}
                      </td>
                      <td className="px-3 py-2">
                        <div className="flex flex-wrap gap-1">
                          {sub.identityStatus === "verified" && <Badge tone="ok">Verified</Badge>}
                          {sub.identityStatus === "unverified" && <Badge tone="warn">Unverified</Badge>}
                          {sub.identityStatus === "disambiguation_required" && (
                            <Badge tone="warn">Needs disambiguation</Badge>
                          )}
                          {sub.extractionStatus === "failed" && (
                            <Badge tone="danger">Extraction failed</Badge>
                          )}
                          {sub.extractionStatus === "pending" && (
                            <Badge tone="neutral">Pending</Badge>
                          )}
                          {sub.isOversized && <Badge tone="warn">Oversized</Badge>}
                        </div>
                      </td>

                      <td className="px-3 py-2 text-right">
                        <button
                          type="button"
                          onClick={() =>
                            updateIdentity.mutate({
                              submissionId: sub.id,
                              studentDisplayName: editingName[sub.id] ?? sub.studentDisplayName,
                            })
                          }
                          disabled={updateIdentity.isPending}
                          className="rounded border border-slate-300 px-2 py-1 text-xs text-slate-700 disabled:opacity-50"
                        >
                          Save name
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <ErrorBanner error={updateIdentity.error} className="mt-3" />
          </>
        )}

        {/* Start Grading button */}
        {submissions.data && submissions.data.length > 0 && (
          <div className="mt-4 flex items-center gap-3">
            <button
              type="button"
              onClick={handleStartGrading}
              disabled={!rubricIsValid || confirmIdentities.isPending}
              className="rounded bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
            >
              {confirmIdentities.isPending ? "Confirming..." : "Start Grading"}
            </button>
            {!rubricIsValid && (
              <span className="text-xs text-amber-700">
                Save a valid rubric before starting grading.
              </span>
            )}
          </div>
        )}
        <ErrorBanner error={confirmIdentities.error} className="mt-3" />
      </section>
    </AppShell>
  );
}
