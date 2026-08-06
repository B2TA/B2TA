import { useEffect, useState } from "react";
import { Link, useParams } from "react-router";
import AppShell from "../components/AppShell";
import { Badge, EmptyState, ErrorBanner, LoadingPanel } from "../components/Feedback";
import {
  useRubric,
  useSaveRubric,
  useSession,
  useSubmissions,
  useUpdateIdentity,
} from "../api/queries";
import type { Criterion } from "../types";

/**
 * Session setup: rubric entry and the submission roster.
 *
 * The rubric editor here is the manual entry path, which is what the grading, review, and export
 * endpoints need in order to have criteria to work against. File upload and parsing (tasks 3.3-3.6)
 * add a second way to populate the same structure and are not wired yet; the note in the upload panel
 * says so rather than showing a control that would fail.
 */
export default function SessionSetupPage() {
  const { id: sessionId = "" } = useParams<{ id: string }>();
  const session = useSession(sessionId);
  const rubric = useRubric(sessionId);
  const submissions = useSubmissions(sessionId);
  const saveRubric = useSaveRubric(sessionId);
  const updateIdentity = useUpdateIdentity(sessionId);

  const [criteria, setCriteria] = useState<Criterion[]>([]);
  const [loadedRubricId, setLoadedRubricId] = useState<string | null>(null);
  const [editingName, setEditingName] = useState<Record<string, string>>({});

  useEffect(() => {
    const loaded = rubric.data;
    const key = loaded?.id ?? "none";
    if (loadedRubricId === key) {
      return;
    }
    setLoadedRubricId(key);
    setCriteria(loaded?.criteria ?? []);
  }, [rubric.data, loadedRubricId]);

  const addCriterion = () => {
    setCriteria((current) => [
      ...current,
      {
        id: null,
        title: "",
        description: "",
        maxPoints: 10,
        // Left blank so the server assigns a distinct colour from its palette.
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
      current.map((criterion, i) => (i === index ? { ...criterion, ...patch } : criterion))
    );
  };

  const removeCriterion = (index: number) => {
    setCriteria((current) =>
      current.filter((_, i) => i !== index).map((criterion, i) => ({ ...criterion, position: i }))
    );
  };

  const handleSave = () => {
    saveRubric.mutate({
      criteria: criteria.map((criterion, index) => ({
        ...criterion,
        position: index,
        performanceLevels: criterion.performanceLevels.map((level, levelIndex) => ({
          ...level,
          position: levelIndex,
        })),
      })),
    });
  };

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

      <section className="mb-6 rounded border border-slate-200 bg-white p-4">
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="flex-1 text-sm font-semibold text-slate-900">Rubric</h2>
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

        <p className="mt-1 text-xs text-slate-500">
          Rubric file upload and parsing are not wired up yet, so criteria are entered here. A rubric
          needs at least one criterion before marking can start.
        </p>

        <ErrorBanner error={saveRubric.error} className="mt-3" />
        {saveRubric.isSuccess && (
          <p className="mt-3 text-xs text-emerald-700">
            Saved {criteria.length} criteria.
          </p>
        )}

        {criteria.length === 0 && (
          <div className="mt-4">
            <EmptyState title="No criteria yet">
              Add a criterion to describe what you are grading against.
            </EmptyState>
          </div>
        )}

        <div className="mt-4 space-y-4">
          {criteria.map((criterion, index) => (
            <fieldset key={index} className="rounded border border-slate-200 p-3">
              <legend className="px-1 text-xs font-medium text-slate-500">
                Criterion {index + 1}
              </legend>

              <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_140px_auto]">
                <div>
                  <label
                    htmlFor={`criterion-title-${index}`}
                    className="block text-xs font-medium text-slate-600"
                  >
                    Title
                  </label>
                  <input
                    id={`criterion-title-${index}`}
                    value={criterion.title}
                    maxLength={200}
                    onChange={(event) => updateCriterion(index, { title: event.target.value })}
                    className="mt-1 w-full rounded border border-slate-300 px-2 py-1 text-sm"
                  />
                </div>
                <div>
                  <label
                    htmlFor={`criterion-max-${index}`}
                    className="block text-xs font-medium text-slate-600"
                  >
                    Max points
                  </label>
                  <input
                    id={`criterion-max-${index}`}
                    inputMode="decimal"
                    value={criterion.maxPoints ?? ""}
                    onChange={(event) => {
                      const parsed = Number(event.target.value);
                      updateCriterion(index, {
                        maxPoints: event.target.value === "" || !Number.isFinite(parsed)
                          ? null
                          : parsed,
                      });
                    }}
                    className="mt-1 w-full rounded border border-slate-300 px-2 py-1 text-sm"
                  />
                </div>
                <div className="flex items-end">
                  <button
                    type="button"
                    onClick={() => removeCriterion(index)}
                    className="rounded border border-red-300 px-2 py-1 text-xs text-red-700"
                  >
                    Remove
                  </button>
                </div>
              </div>

              <div className="mt-3">
                <label
                  htmlFor={`criterion-desc-${index}`}
                  className="block text-xs font-medium text-slate-600"
                >
                  Description
                </label>
                <textarea
                  id={`criterion-desc-${index}`}
                  rows={2}
                  maxLength={2000}
                  value={criterion.description ?? ""}
                  onChange={(event) => updateCriterion(index, { description: event.target.value })}
                  className="mt-1 w-full rounded border border-slate-300 px-2 py-1 text-sm"
                />
              </div>

              <div className="mt-3 space-y-2">
                <span className="block text-xs font-medium text-slate-600">
                  Performance levels
                </span>
                {criterion.performanceLevels.map((level, levelIndex) => (
                  <div key={levelIndex} className="flex flex-wrap items-center gap-2">
                    <input
                      aria-label={`Level ${levelIndex + 1} label`}
                      value={level.label}
                      maxLength={200}
                      onChange={(event) => {
                        const levels = [...criterion.performanceLevels];
                        levels[levelIndex] = { ...level, label: event.target.value };
                        updateCriterion(index, { performanceLevels: levels });
                      }}
                      className="min-w-40 flex-1 rounded border border-slate-300 px-2 py-1 text-sm"
                    />
                    <input
                      aria-label={`Level ${levelIndex + 1} points`}
                      inputMode="decimal"
                      value={level.points ?? ""}
                      onChange={(event) => {
                        const parsed = Number(event.target.value);
                        const levels = [...criterion.performanceLevels];
                        levels[levelIndex] = {
                          ...level,
                          points:
                            event.target.value === "" || !Number.isFinite(parsed) ? null : parsed,
                        };
                        updateCriterion(index, { performanceLevels: levels });
                      }}
                      className="w-24 rounded border border-slate-300 px-2 py-1 text-sm"
                    />
                    <button
                      type="button"
                      onClick={() => {
                        const levels = criterion.performanceLevels.filter(
                          (_, i) => i !== levelIndex
                        );
                        updateCriterion(index, { performanceLevels: levels });
                      }}
                      disabled={criterion.performanceLevels.length <= 1}
                      className="rounded border border-slate-300 px-2 py-1 text-xs text-slate-600 disabled:opacity-40"
                    >
                      Remove level
                    </button>
                  </div>
                ))}
                <button
                  type="button"
                  onClick={() =>
                    updateCriterion(index, {
                      performanceLevels: [
                        ...criterion.performanceLevels,
                        {
                          id: null,
                          label: "",
                          description: "",
                          points: null,
                          position: criterion.performanceLevels.length,
                        },
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
          ))}
        </div>
      </section>

      <section className="rounded border border-slate-200 bg-white p-4">
        <h2 className="text-sm font-semibold text-slate-900">Submissions</h2>
        <p className="mt-1 text-xs text-slate-500">
          Submission upload and text extraction are not wired up yet. Submissions already ingested for
          this session appear below, where you can correct a resolved student name.
        </p>

        <ErrorBanner
          error={submissions.error}
          onRetry={() => void submissions.refetch()}
          className="mt-3"
        />

        {submissions.data?.length === 0 && (
          <div className="mt-4">
            <EmptyState title="No submissions in this session yet" />
          </div>
        )}

        {submissions.data && submissions.data.length > 0 && (
          <ul className="mt-3 divide-y divide-slate-100">
            {submissions.data.map((submission) => (
              <li key={submission.id} className="flex flex-wrap items-center gap-2 py-2">
                <span className="w-8 text-xs text-slate-400">{submission.position + 1}</span>
                <input
                  aria-label={`Student name for ${submission.originalFilename}`}
                  value={editingName[submission.id] ?? submission.studentDisplayName}
                  onChange={(event) =>
                    setEditingName((current) => ({
                      ...current,
                      [submission.id]: event.target.value,
                    }))
                  }
                  className="min-w-48 flex-1 rounded border border-slate-300 px-2 py-1 text-sm"
                />
                <span className="text-xs text-slate-400">{submission.originalFilename}</span>
                {submission.identityStatus !== "verified" && (
                  <Badge tone="warn">
                    {submission.identityStatus === "disambiguation_required"
                      ? "Needs disambiguation"
                      : "Unverified"}
                  </Badge>
                )}
                {submission.extractionStatus === "failed" && (
                  <Badge tone="danger">Extraction failed</Badge>
                )}
                <button
                  type="button"
                  onClick={() =>
                    updateIdentity.mutate({
                      submissionId: submission.id,
                      studentDisplayName:
                        editingName[submission.id] ?? submission.studentDisplayName,
                    })
                  }
                  disabled={updateIdentity.isPending}
                  className="rounded border border-slate-300 px-2 py-1 text-xs text-slate-700 disabled:opacity-50"
                >
                  Save name
                </button>
              </li>
            ))}
          </ul>
        )}
        <ErrorBanner error={updateIdentity.error} className="mt-3" />
      </section>
    </AppShell>
  );
}
