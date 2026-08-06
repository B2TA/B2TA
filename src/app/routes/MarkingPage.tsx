import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router";
import AppShell from "../components/AppShell";
import { Badge, EmptyState, ErrorBanner, LoadingPanel } from "../components/Feedback";
import DocumentViewer from "../components/DocumentViewer";
import RubricPanel, { awardedPoints, type CriterionDraft } from "../components/RubricPanel";
import MatchList from "../components/MatchList";
import {
  useAnalyzeSubmission,
  useConfirmMatch,
  useCreateManualMatch,
  useDeleteConfirmedMatch,
  useGradingRecord,
  useJobStatus,
  useReanalyzeCriterion,
  useRejectMatch,
  useRubric,
  useSaveGrading,
  useSubmissions,
  useSuggestComments,
} from "../api/queries";
import type { Criterion, GradingRecord, SaveGradingRequest } from "../types";

/** Longest text range a TA may associate with a criterion (Requirement 10.3, 10.8). */
const MAX_MANUAL_PASSAGE_LENGTH = 5000;

/**
 * Marking view (tasks 8.x wired against the 5.x endpoints).
 *
 * All server state comes from `GET .../grading`, which returns the record, the matches, the analysis
 * state, and the extracted text in one response. Local edits live in a draft map and are only written
 * on an explicit save (Requirement 14.1), so navigating away with unsaved work is detectable and
 * requires confirmation (Requirement 13.4).
 */
export default function MarkingPage() {
  const { id: sessionId = "" } = useParams<{ id: string }>();
  const [searchParams, setSearchParams] = useSearchParams();

  const submissions = useSubmissions(sessionId);
  const rubric = useRubric(sessionId);

  // The submission in the URL, defaulting to the first in batch order so the page is usable from a
  // bare /mark link.
  const submissionId = searchParams.get("submission") ?? submissions.data?.[0]?.id ?? "";

  const grading = useGradingRecord(sessionId, submissionId || undefined);
  const saveGrading = useSaveGrading(sessionId, submissionId);
  const confirmMatch = useConfirmMatch(sessionId, submissionId);
  const rejectMatch = useRejectMatch(sessionId, submissionId);
  const createManualMatch = useCreateManualMatch(sessionId, submissionId);
  const deleteMatch = useDeleteConfirmedMatch(sessionId, submissionId);
  const analyze = useAnalyzeSubmission(sessionId, submissionId);
  const reanalyze = useReanalyzeCriterion(sessionId, submissionId);
  const suggestComments = useSuggestComments(sessionId, submissionId);

  const [drafts, setDrafts] = useState<Record<string, CriterionDraft>>({});
  const [feedback, setFeedback] = useState("");
  const [dirty, setDirty] = useState(false);
  const [selectedCriterionId, setSelectedCriterionId] = useState<string | null>(null);
  const [selection, setSelection] = useState<{ start: number; end: number } | null>(null);
  const [selectionError, setSelectionError] = useState<string | null>(null);
  const [activeJobId, setActiveJobId] = useState<string | null>(null);
  const [reanalyzingCriterionId, setReanalyzingCriterionId] = useState<string | null>(null);
  const [snippets, setSnippets] = useState<string[]>([]);

  const criteria = useMemo<Criterion[]>(() => rubric.data?.criteria ?? [], [rubric.data]);

  // Reset the drafts whenever a different submission's record arrives. Keyed on the record id and
  // submission id rather than on the object, so a background refetch does not discard live edits.
  const loadedKey = useRef<string | null>(null);
  useEffect(() => {
    const record = grading.data;
    if (!record || criteria.length === 0) {
      return;
    }
    const key = `${record.submissionId}:${record.savedAt ?? "unsaved"}`;
    if (loadedKey.current === key) {
      return;
    }
    loadedKey.current = key;
    setDrafts(buildDrafts(criteria, record));
    setFeedback(record.overallFeedback);
    setDirty(false);
    setSnippets([]);
    setSelection(null);
    setSelectionError(null);
    setSelectedCriterionId(criteria[0]?.id ?? null);
  }, [grading.data, criteria]);

  // Requirement 14.7: warn before the tab closes while a record holds unsaved changes.
  useEffect(() => {
    if (!dirty) {
      return;
    }
    const handler = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [dirty]);

  // Refresh the record once an analysis job finishes so new suggestions appear without a manual
  // reload. `refetch` is pulled out of the query object because that object is a new reference on
  // every render, and depending on it would re-run this effect continuously.
  const job = useJobStatus(activeJobId);
  const jobStatus = job.data?.status;
  const refetchGrading = grading.refetch;
  useEffect(() => {
    if (jobStatus !== "complete" && jobStatus !== "failed") {
      return;
    }
    setActiveJobId(null);
    setReanalyzingCriterionId(null);
    void refetchGrading();
  }, [jobStatus, refetchGrading]);

  const totals = useMemo(() => {
    let total = 0;
    let max = 0;
    let unscored = 0;
    for (const criterion of criteria) {
      max += criterion.maxPoints ?? 0;
      const points = awardedPoints(criterion, drafts[criterion.id!]);
      if (points === null) {
        unscored++;
      } else {
        total += points;
      }
    }
    return { total, max, unscored };
  }, [criteria, drafts]);

  const updateDraft = useCallback(
    (criterionId: string, patch: Partial<CriterionDraft>) => {
      setDrafts((current) => ({
        ...current,
        [criterionId]: {
          ...(current[criterionId] ?? emptyDraft()),
          ...patch,
        },
      }));
      setDirty(true);
    },
    []
  );

  const handleOverrideChange = (criterionId: string, raw: string) => {
    const criterion = criteria.find((item) => item.id === criterionId);
    if (!criterion) {
      return;
    }
    if (raw.trim() === "") {
      updateDraft(criterionId, { overridePoints: null, overrideError: null });
      return;
    }
    const parsed = Number(raw);
    const max = criterion.maxPoints;
    // Requirement 11.5: reject the entry and keep the previous value, stating the permitted range.
    if (!Number.isFinite(parsed)) {
      updateDraft(criterionId, { overrideError: "Enter a number." });
      return;
    }
    if (decimalPlaces(raw) > 2) {
      updateDraft(criterionId, { overrideError: "Use at most 2 decimal places." });
      return;
    }
    if (parsed < 0 || (max !== null && parsed > max)) {
      updateDraft(criterionId, {
        overrideError: `Enter a value between 0 and ${max ?? "the maximum"}.`,
      });
      return;
    }
    updateDraft(criterionId, { overridePoints: parsed, overrideError: null });
  };

  const handleSelectLevel = (criterionId: string, levelId: string | null) => {
    // Requirement 11.6: choosing a level replaces an override for the same criterion.
    updateDraft(criterionId, {
      selectedLevelId: levelId,
      overridePoints: null,
      overrideError: null,
    });
  };

  const buildSavePayload = (): SaveGradingRequest => ({
    overallFeedback: feedback,
    criterionScores: criteria
      .filter((criterion) => Boolean(criterion.id))
      .map((criterion) => {
        const draft = drafts[criterion.id!] ?? emptyDraft();
        return {
          criterionId: criterion.id!,
          selectedLevelId: draft.selectedLevelId,
          overridePoints: draft.overridePoints,
          criterionFeedback: draft.criterionFeedback,
        };
      }),
    // Omitted on purpose: confirmed matches are managed by the dedicated match endpoints, which
    // apply immediately. Sending them here would let a stale draft undo a confirmation.
  });

  const handleSave = async () => {
    const invalid = Object.values(drafts).some((draft) => draft.overrideError);
    if (invalid) {
      return;
    }
    await saveGrading.mutateAsync(buildSavePayload());
    setDirty(false);
  };

  const orderedSubmissions = submissions.data ?? [];
  const currentIndex = orderedSubmissions.findIndex((item) => item.id === submissionId);

  const navigateTo = async (targetId: string) => {
    if (dirty) {
      const proceed = window.confirm(
        "This submission has unsaved changes. Save before moving on?\n\n" +
          "OK saves and continues. Cancel stays here."
      );
      if (!proceed) {
        return;
      }
      await handleSave();
    }
    loadedKey.current = null;
    setSearchParams({ submission: targetId });
  };

  const handleAddManualMatch = async () => {
    if (!selection || !selectedCriterionId) {
      return;
    }
    const length = selection.end - selection.start;
    if (length < 1 || length > MAX_MANUAL_PASSAGE_LENGTH) {
      setSelectionError(
        `Select between 1 and ${MAX_MANUAL_PASSAGE_LENGTH} characters. You selected ${length}.`
      );
      return;
    }
    setSelectionError(null);
    try {
      await createManualMatch.mutateAsync({
        criterionId: selectedCriterionId,
        passageStart: selection.start,
        passageEnd: selection.end,
      });
      setSelection(null);
      window.getSelection()?.removeAllRanges();
    } catch {
      // Rendered by the ErrorBanner bound to the mutation below.
    }
  };

  const handleSuggestComments = async () => {
    const result = await suggestComments.mutateAsync({ currentDraft: feedback });
    setSnippets(result.snippets.map((snippet) => snippet.text));
  };

  const insertSnippet = (text: string) => {
    // Requirement 12.4: inserted text stays editable, and is appended rather than replacing what the
    // TA already wrote.
    setFeedback((current) => (current.length === 0 ? text : `${current}\n\n${text}`));
    setDirty(true);
  };

  if (submissions.isLoading || rubric.isLoading) {
    return (
      <AppShell title="Marking">
        <LoadingPanel label="Loading the rubric and submissions..." />
      </AppShell>
    );
  }

  if (rubric.data === null) {
    return (
      <AppShell title="Marking">
        <EmptyState title="This session has no rubric yet">
          <Link className="underline" to={`/sessions/${sessionId}/setup`}>
            Add a rubric in session setup
          </Link>{" "}
          before grading.
        </EmptyState>
      </AppShell>
    );
  }

  if (orderedSubmissions.length === 0) {
    return (
      <AppShell title="Marking">
        <EmptyState title="This session has no submissions yet">
          <Link className="underline" to={`/sessions/${sessionId}/setup`}>
            Add submissions in session setup
          </Link>
          .
        </EmptyState>
      </AppShell>
    );
  }

  const record = grading.data;
  const selectedCriterion = criteria.find((item) => item.id === selectedCriterionId);
  const extractedText = record?.extractedText ?? "";
  const matchBusy =
    confirmMatch.isPending ||
    rejectMatch.isPending ||
    createManualMatch.isPending ||
    deleteMatch.isPending;

  return (
    <AppShell
      title="Marking"
      subtitle={
        record
          ? `${record.studentDisplayName} · submission ${record.position} of ${record.batchSize}`
          : undefined
      }
      actions={
        <div className="flex items-center gap-2">
          {dirty ? (
            <Badge tone="warn">Unsaved changes</Badge>
          ) : record?.savedAt ? (
            <Badge tone="ok">Saved {new Date(record.savedAt).toLocaleTimeString()}</Badge>
          ) : null}
          <button
            type="button"
            onClick={() => void handleSave()}
            disabled={saveGrading.isPending || !record}
            className="rounded bg-slate-900 px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50"
          >
            {saveGrading.isPending ? "Saving..." : "Save"}
          </button>
          <Link
            to={`/sessions/${sessionId}/review`}
            className="rounded border border-slate-300 px-3 py-1.5 text-xs text-slate-700"
          >
            Review
          </Link>
        </div>
      }
    >
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <label htmlFor="submission-picker" className="text-xs font-medium text-slate-600">
          Submission
        </label>
        <select
          id="submission-picker"
          value={submissionId}
          onChange={(event) => void navigateTo(event.target.value)}
          className="rounded border border-slate-300 px-2 py-1 text-sm"
        >
          {orderedSubmissions.map((submission, index) => (
            <option key={submission.id} value={submission.id}>
              {index + 1}. {submission.studentDisplayName || submission.originalFilename}
            </option>
          ))}
        </select>
        <button
          type="button"
          disabled={currentIndex <= 0}
          onClick={() => void navigateTo(orderedSubmissions[currentIndex - 1].id)}
          className="rounded border border-slate-300 px-2 py-1 text-xs disabled:opacity-40"
        >
          Save and previous
        </button>
        <button
          type="button"
          disabled={currentIndex < 0 || currentIndex >= orderedSubmissions.length - 1}
          onClick={() => void navigateTo(orderedSubmissions[currentIndex + 1].id)}
          className="rounded border border-slate-300 px-2 py-1 text-xs disabled:opacity-40"
        >
          Save and next
        </button>

        <div className="ml-auto flex items-center gap-3 text-sm">
          <span className="font-semibold text-slate-900">
            {totals.total.toFixed(2)} / {totals.max.toFixed(2)}
          </span>
          {totals.unscored > 0 && (
            <Badge tone="warn">{totals.unscored} criteria unscored</Badge>
          )}
          <button
            type="button"
            onClick={async () => {
              const created = await analyze.mutateAsync(false);
              setActiveJobId(created.jobId);
            }}
            disabled={analyze.isPending || Boolean(activeJobId)}
            className="rounded border border-slate-300 px-2 py-1 text-xs disabled:opacity-50"
          >
            {activeJobId ? "Analysing..." : "Find evidence"}
          </button>
        </div>
      </div>

      <ErrorBanner
        error={grading.error}
        onRetry={() => void grading.refetch()}
        className="mb-4"
      />
      <ErrorBanner error={saveGrading.error} onRetry={() => void handleSave()} className="mb-4" />
      <ErrorBanner error={analyze.error} className="mb-4" />
      <ErrorBanner error={reanalyze.error} className="mb-4" />
      <ErrorBanner error={confirmMatch.error} className="mb-4" />
      <ErrorBanner error={rejectMatch.error} className="mb-4" />
      <ErrorBanner error={createManualMatch.error} className="mb-4" />
      <ErrorBanner error={deleteMatch.error} className="mb-4" />

      {job.data && job.data.status !== "complete" && (
        <div className="mb-4 rounded border border-sky-300 bg-sky-50 px-4 py-2 text-sm text-sky-900">
          Finding evidence: {job.data.progressCurrent} of {job.data.progressTotal} criteria.
        </div>
      )}
      {job.data?.status === "failed" && job.data.failureReason && (
        <ErrorBanner error={new Error(job.data.failureReason)} className="mb-4" />
      )}

      {grading.isLoading && <LoadingPanel label="Loading the submission..." />}

      {record && (
        <>
          {record.extractionStatus === "failed" && (
            <div className="mb-4 rounded border border-amber-300 bg-amber-50 px-4 py-2 text-sm text-amber-900">
              Text could not be extracted from this submission
              {record.extractionFailureReason ? ` (${record.extractionFailureReason})` : ""}. Evidence
              association is unavailable, but you can still score and write feedback.
            </div>
          )}
          {record.isOversized && (
            <div className="mb-4 rounded border border-amber-300 bg-amber-50 px-4 py-2 text-sm text-amber-900">
              This submission exceeds the analysis size limit, so only the earlier part of it was
              analysed for evidence.
            </div>
          )}

          <div className="grid gap-4 lg:grid-cols-[minmax(0,420px)_minmax(0,1fr)]">
            <div className="flex max-h-[70vh] flex-col">
              <RubricPanel
                criteria={criteria}
                drafts={drafts}
                suggested={record.suggestedMatches}
                confirmed={record.confirmedMatches}
                analysis={record.criterionAnalysis}
                selectedCriterionId={selectedCriterionId}
                onSelectCriterion={setSelectedCriterionId}
                onSelectLevel={handleSelectLevel}
                onOverrideChange={handleOverrideChange}
                onCriterionFeedbackChange={(criterionId, text) =>
                  updateDraft(criterionId, { criterionFeedback: text })
                }
                onReanalyze={async (criterionId) => {
                  setReanalyzingCriterionId(criterionId);
                  const created = await reanalyze.mutateAsync(criterionId);
                  setActiveJobId(created.jobId);
                }}
                reanalyzingCriterionId={reanalyzingCriterionId}
              />
            </div>

            <div className="flex max-h-[70vh] flex-col gap-3">
              {extractedText.length > 0 ? (
                <>
                  <div className="flex flex-wrap items-center gap-2 text-xs text-slate-600">
                    <span className="inline-flex items-center gap-1">
                      <span
                        aria-hidden="true"
                        className="inline-block h-3 w-6 border-b-2 border-solid border-slate-700 bg-slate-200"
                      />
                      Confirmed
                    </span>
                    <span className="inline-flex items-center gap-1">
                      <span
                        aria-hidden="true"
                        className="inline-block h-3 w-6 border-b-2 border-dashed border-slate-700 bg-slate-100"
                      />
                      Suggested
                    </span>
                    {selection && selectedCriterion && (
                      <button
                        type="button"
                        onClick={() => void handleAddManualMatch()}
                        disabled={matchBusy}
                        className="ml-auto rounded bg-slate-900 px-2 py-1 text-xs font-medium text-white disabled:opacity-50"
                      >
                        Associate selection with {selectedCriterion.title}
                      </button>
                    )}
                  </div>
                  {selectionError && (
                    <p className="text-xs text-red-700" role="alert">
                      {selectionError}
                    </p>
                  )}
                  <div className="min-h-0 flex-1">
                    <DocumentViewer
                      text={extractedText}
                      suggested={record.suggestedMatches}
                      confirmed={record.confirmedMatches}
                      criteria={criteria}
                      selectedCriterionId={selectedCriterionId}
                      onSelectPassage={(criterionIds) => {
                        // A passage tied to exactly one criterion selects it; an overlapping passage
                        // leaves the selection alone so the TA chooses (Requirement 9.7).
                        if (criterionIds.length === 1) {
                          setSelectedCriterionId(criterionIds[0]);
                        }
                      }}
                      onSelectionChange={setSelection}
                    />
                  </div>
                </>
              ) : (
                <EmptyState title="No extracted text is available for this submission" />
              )}
            </div>
          </div>

          <div className="mt-4 grid gap-4 lg:grid-cols-2">
            {selectedCriterion && (
              <div className="rounded border border-slate-200 bg-white p-4">
                <MatchList
                  criterionTitle={selectedCriterion.title}
                  text={extractedText}
                  suggested={record.suggestedMatches.filter(
                    (match) => match.criterionId === selectedCriterionId
                  )}
                  confirmed={record.confirmedMatches.filter(
                    (match) => match.criterionId === selectedCriterionId
                  )}
                  onConfirm={(matchId) => confirmMatch.mutate(matchId)}
                  onReject={(matchId) => rejectMatch.mutate(matchId)}
                  onDelete={(matchId) => deleteMatch.mutate(matchId)}
                  busy={matchBusy}
                />
              </div>
            )}

            <div className="rounded border border-slate-200 bg-white p-4">
              <div className="flex items-center justify-between gap-2">
                <label htmlFor="overall-feedback" className="text-sm font-semibold text-slate-900">
                  Overall feedback
                </label>
                <span className="text-xs text-slate-400">{feedback.length}/10000</span>
              </div>
              <textarea
                id="overall-feedback"
                rows={8}
                maxLength={10000}
                value={feedback}
                onChange={(event) => {
                  setFeedback(event.target.value);
                  setDirty(true);
                }}
                className="mt-2 w-full rounded border border-slate-300 px-3 py-2 text-sm"
              />

              <div className="mt-2 flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => void handleSuggestComments()}
                  disabled={suggestComments.isPending || totals.unscored === criteria.length}
                  className="rounded border border-slate-300 px-3 py-1.5 text-xs text-slate-700 disabled:opacity-50"
                >
                  {suggestComments.isPending ? "Generating..." : "Suggest comments"}
                </button>
                {totals.unscored === criteria.length && (
                  <span className="text-xs text-slate-500">
                    Select at least one performance level first.
                  </span>
                )}
              </div>

              <ErrorBanner
                error={suggestComments.error}
                onRetry={() => void handleSuggestComments()}
                className="mt-2"
              />

              {snippets.length > 0 && (
                <ul className="mt-3 space-y-2">
                  {snippets.map((snippet, index) => (
                    <li
                      key={index}
                      className="rounded border border-sky-300 bg-sky-50 p-2 text-xs text-slate-800"
                    >
                      <Badge tone="info">AI-generated</Badge>
                      <p className="mt-1">{snippet}</p>
                      <button
                        type="button"
                        onClick={() => insertSnippet(snippet)}
                        className="mt-2 rounded bg-slate-900 px-2 py-0.5 text-xs font-medium text-white"
                      >
                        Insert into feedback
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        </>
      )}
    </AppShell>
  );
}

function emptyDraft(): CriterionDraft {
  return {
    selectedLevelId: null,
    overridePoints: null,
    criterionFeedback: "",
    overrideError: null,
  };
}

/** Seeds the draft map from the stored record so a reopened submission restores exactly (Req 13.7). */
function buildDrafts(criteria: Criterion[], record: GradingRecord): Record<string, CriterionDraft> {
  const stored = new Map(record.criterionScores.map((score) => [score.criterionId, score]));
  const drafts: Record<string, CriterionDraft> = {};
  for (const criterion of criteria) {
    if (!criterion.id) {
      continue;
    }
    const score = stored.get(criterion.id);
    drafts[criterion.id] = {
      selectedLevelId: score?.selectedLevelId ?? null,
      overridePoints: score?.overridePoints ?? null,
      criterionFeedback: score?.criterionFeedback ?? "",
      overrideError: null,
    };
  }
  return drafts;
}

function decimalPlaces(raw: string): number {
  const dot = raw.indexOf(".");
  return dot < 0 ? 0 : raw.length - dot - 1;
}
