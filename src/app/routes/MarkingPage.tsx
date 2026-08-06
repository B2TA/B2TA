import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { lazy, Suspense, useEffect, useMemo, useRef, useState } from "react"
import { Link, Navigate, useParams } from "react-router"

import api, { ApiError } from "../api"
import type {
  GradingRecord,
  Rubric,
  Session,
  Submission,
  SuggestedMatch,
} from "../types"

type DraftScore = {
  criterionId: string
  selectedLevelId: string | null
  overridePoints: number | null
  criterionFeedback: string
}

const PdfSubmissionViewer = lazy(
  () => import("../components/PdfSubmissionViewer"),
)

export default function MarkingPage() {
  const queryClient = useQueryClient()
  const { id = "", submissionId } = useParams<{
    id: string
    submissionId: string
  }>()
  const [scores, setScores] = useState<DraftScore[]>([])
  const [overallFeedback, setOverallFeedback] = useState("")
  const [loaded, setLoaded] = useState(false)
  const [dirty, setDirty] = useState(false)
  const requestedSuggestionsFor = useRef<string | null>(null)

  const sessionQuery = useQuery({
    queryKey: ["sessions", id],
    queryFn: () => api.get<Session>(`/sessions/${id}`),
  })
  const rubricQuery = useQuery({
    queryKey: ["sessions", id, "rubric"],
    queryFn: () => api.get<Rubric>(`/sessions/${id}/rubric`),
  })
  const submissionsQuery = useQuery({
    queryKey: ["sessions", id, "submissions"],
    queryFn: () => api.get<Submission[]>(`/sessions/${id}/submissions`),
  })
  const submission =
    submissionsQuery.data?.find((item) => item.id === submissionId) ??
    submissionsQuery.data?.find((item) => item.importStatus === "ready")
  const recordQuery = useQuery({
    queryKey: ["sessions", id, "submissions", submission?.id, "grading-record"],
    queryFn: () =>
      api.get<GradingRecord>(
        `/sessions/${id}/submissions/${submission!.id}/grading-record`,
      ),
    enabled: Boolean(submission),
    retry: false,
  })
  const suggestionsQuery = useQuery({
    queryKey: [
      "sessions",
      id,
      "submissions",
      submission?.id,
      "evidence-suggestions",
    ],
    queryFn: () =>
      api.get<SuggestedMatch[]>(
        `/sessions/${id}/submissions/${submission!.id}/evidence-suggestions`,
      ),
    enabled: Boolean(submission),
    retry: false,
  })

  useEffect(() => {
    if (loaded || !rubricQuery.data || !submission || recordQuery.isLoading)
      return
    const record = recordQuery.data
    setScores(
      rubricQuery.data.criteria.map((criterion) => {
        const saved = record?.criterionScores.find(
          (score) => score.criterionId === criterion.id,
        )
        return {
          criterionId: criterion.id,
          selectedLevelId: saved?.selectedLevelId ?? null,
          overridePoints: saved?.overridePoints ?? null,
          criterionFeedback: saved?.criterionFeedback ?? "",
        }
      }),
    )
    setOverallFeedback(record?.overallFeedback ?? "")
    setLoaded(true)
  }, [
    loaded,
    recordQuery.data,
    recordQuery.isLoading,
    rubricQuery.data,
    submission,
  ])

  const saveMutation = useMutation({
    mutationFn: () =>
      api.put<GradingRecord>(
        `/sessions/${id}/submissions/${submission!.id}/grading-record`,
        { overallFeedback, criterionScores: scores },
      ),
    onSuccess: () => setDirty(false),
  })
  const suggestionsMutation = useMutation({
    mutationFn: () =>
      api.post<SuggestedMatch[]>(
        `/sessions/${id}/submissions/${submission!.id}/evidence-suggestions`,
      ),
    onSuccess: (suggestions) => {
      queryClient.setQueryData(
        ["sessions", id, "submissions", submission!.id, "evidence-suggestions"],
        suggestions,
      )
    },
    retry: 1,
  })

  useEffect(() => {
    if (
      !submission?.extractedText ||
      !suggestionsQuery.isSuccess ||
      suggestionsQuery.data.length > 0 ||
      suggestionsMutation.isPending ||
      requestedSuggestionsFor.current === submission.id
    )
      return
    requestedSuggestionsFor.current = submission.id
    suggestionsMutation.mutate()
  }, [
    submission,
    suggestionsMutation,
    suggestionsQuery.data,
    suggestionsQuery.isSuccess,
  ])

  const completed = scores.filter(
    (score) => score.selectedLevelId !== null || score.overridePoints !== null,
  ).length
  const total = useMemo(
    () =>
      scores.reduce((sum, score) => {
        const criterion = rubricQuery.data?.criteria.find(
          (item) => item.id === score.criterionId,
        )
        const level = criterion?.performanceLevels.find(
          (item) => item.id === score.selectedLevelId,
        )
        return sum + (score.overridePoints ?? level?.points ?? 0)
      }, 0),
    [rubricQuery.data, scores],
  )
  const maxPoints =
    rubricQuery.data?.criteria.reduce(
      (sum, item) => sum + (item.maxPoints ?? 0),
      0,
    ) ?? 0
  const suggestions = suggestionsQuery.data ?? []

  function changeScore(criterionId: string, changes: Partial<DraftScore>) {
    setScores((current) =>
      current.map((score) =>
        score.criterionId === criterionId ? { ...score, ...changes } : score,
      ),
    )
    setDirty(true)
  }

  if (!submissionId && submissionsQuery.data) {
    const first = submissionsQuery.data.find(
      (item) => item.importStatus === "ready",
    )
    return first ? (
      <Navigate replace to={`/sessions/${id}/mark/${first.id}`} />
    ) : (
      <Navigate replace to={`/sessions/${id}/setup`} />
    )
  }
  if (
    sessionQuery.isLoading ||
    rubricQuery.isLoading ||
    submissionsQuery.isLoading ||
    (submission && recordQuery.isLoading)
  ) {
    return (
      <main className="grid min-h-screen place-items-center bg-slate-100 font-mono text-xs uppercase tracking-[0.18em] text-slate-500">
        Loading grading desk…
      </main>
    )
  }
  if (
    !submission ||
    !rubricQuery.data ||
    sessionQuery.isError ||
    rubricQuery.isError
  ) {
    return (
      <main className="grid min-h-screen place-items-center bg-slate-100">
        <div className="border border-slate-300 bg-white p-8">
          <h1 className="text-xl font-bold">Grading desk unavailable</h1>
          <Link
            className="mt-4 inline-block text-sm font-semibold text-amber-700"
            to={`/sessions/${id}/setup`}
          >
            Return to session setup
          </Link>
        </div>
      </main>
    )
  }

  const saveError =
    saveMutation.error instanceof ApiError
      ? "Score every criterion with a level or valid point override."
      : "The grading record could not be saved."

  return (
    <main className="min-h-screen bg-[#eef1f4] text-slate-950">
      <header className="sticky top-0 z-20 border-b border-slate-700 bg-[#263643] text-white shadow-sm">
        <div className="flex min-h-16 items-center justify-between gap-4 px-4 sm:px-6">
          <div className="min-w-0">
            <Link
              className="font-mono text-[10px] uppercase tracking-[0.18em] text-slate-300 hover:text-white"
              to={`/sessions/${id}/setup`}
            >
              ← Session setup
            </Link>
            <p className="truncate text-sm font-semibold sm:text-base">
              {sessionQuery.data?.name}
            </p>
          </div>
          <div className="text-right">
            <h1 className="text-sm font-bold">
              {submission.studentDisplayName}
            </h1>
            <p className="font-mono text-[10px] uppercase tracking-[0.12em] text-slate-300">
              Submission {submission.position + 1} · Attempt{" "}
              {submission.attemptCount}
            </p>
          </div>
        </div>
      </header>

      <div className="grid min-h-[calc(100vh-4rem)] lg:grid-cols-[minmax(0,1fr)_28rem]">
        <section
          className="min-h-[65vh] border-r border-slate-300 bg-slate-200 p-3 sm:p-5"
          aria-label="Submitted document"
        >
          <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                Student submission
              </p>
              <p className="mt-1 truncate text-sm font-semibold">
                {submission.originalFilename}
              </p>
            </div>
            <div className="flex items-center gap-2">
              <a
                className="border border-slate-400 bg-white px-3 py-2 text-xs font-semibold hover:border-amber-600 hover:text-amber-700"
                href={submission.artifactUrl ?? "#"}
                target="_blank"
                rel="noreferrer"
              >
                Open PDF
              </a>
            </div>
          </div>
          <Suspense
            fallback={
              <div className="grid min-h-[34rem] place-items-center border border-slate-300 bg-white font-mono text-[10px] uppercase tracking-[0.14em] text-slate-500">
                Opening PDF viewer…
              </div>
            }
          >
            <PdfSubmissionViewer
              artifactUrl={submission.artifactUrl ?? ""}
              expectedText={submission.extractedText ?? ""}
              rubric={rubricQuery.data}
              studentDisplayName={submission.studentDisplayName}
              suggestions={suggestions}
            />
          </Suspense>
        </section>

        <aside className="bg-[#f8fafc] lg:h-[calc(100vh-4rem)] lg:overflow-y-auto">
          <div className="border-b border-slate-200 bg-white px-5 py-5">
            <div className="flex items-end justify-between gap-4">
              <div>
                <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-amber-700">
                  Assessment ledger
                </p>
                <h2 className="mt-1 text-xl font-bold">Rubric</h2>
              </div>
              <p className="text-sm font-bold">
                {total}/{maxPoints} pts
              </p>
            </div>
            <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-slate-200">
              <div
                className="h-full bg-amber-600 transition-all"
                style={{
                  width: `${
                    rubricQuery.data.criteria.length
                      ? (completed / rubricQuery.data.criteria.length) * 100
                      : 0
                  }%`,
                }}
              />
            </div>
            <p className="mt-2 font-mono text-[10px] uppercase tracking-[0.12em] text-slate-500">
              {completed} of {rubricQuery.data.criteria.length} criteria scored
            </p>
            <p className="mt-2 text-xs leading-5 text-slate-500">
              {suggestionsMutation.isPending
                ? "Finding rubric evidence…"
                : suggestions.length > 0
                  ? `${suggestions.length} AI highlight${
                      suggestions.length === 1 ? "" : "s"
                    } on the PDF. AI never selects or recommends a mark.`
                  : "AI highlights will appear on the PDF without selecting or recommending a mark."}
            </p>
            {suggestionsMutation.isError ? (
              <p
                className="mt-2 text-xs font-semibold text-red-700"
                role="alert"
              >
                Evidence suggestions are unavailable. Check the Bedrock
                configuration and try again.
              </p>
            ) : null}
          </div>

          <div className="space-y-4 p-4">
            {rubricQuery.data.criteria.map((criterion, index) => {
              const score = scores.find(
                (item) => item.criterionId === criterion.id,
              )
              if (!score) return null
              const criterionSuggestionCount = suggestions.filter(
                (suggestion) => suggestion.criterionId === criterion.id,
              ).length
              return (
                <fieldset
                  className="border border-slate-200 bg-white p-4 shadow-sm"
                  key={criterion.id}
                >
                  <legend className="w-full px-0">
                    <span className="flex items-center gap-2 text-sm font-bold">
                      <span
                        className="h-2.5 w-2.5 rounded-full"
                        style={{ backgroundColor: criterion.displayColor }}
                      />
                      {criterion.title}
                      {criterionSuggestionCount > 0 ? (
                        <span className="font-mono text-[9px] font-semibold uppercase tracking-[0.1em] text-sky-700">
                          · {criterionSuggestionCount} on PDF
                        </span>
                      ) : null}
                      <span className="ml-auto font-mono text-xs text-slate-400">
                        /{criterion.maxPoints ?? "—"}
                      </span>
                    </span>
                  </legend>
                  {criterion.description ? (
                    <p className="mt-2 text-xs leading-5 text-slate-600">
                      {criterion.description}
                    </p>
                  ) : null}
                  <div className="mt-4 space-y-2">
                    {criterion.performanceLevels.map((level) => (
                      <label
                        className={`block cursor-pointer border p-3 transition ${
                          score.selectedLevelId === level.id
                            ? "border-amber-600 bg-amber-50"
                            : "border-slate-200 hover:border-slate-400"
                        }`}
                        key={level.id}
                      >
                        <span className="flex gap-3">
                          <input
                            aria-label={`${level.label} — ${level.points ?? 0} points`}
                            checked={score.selectedLevelId === level.id}
                            className="mt-0.5 accent-amber-700"
                            name={`criterion-${index}`}
                            onChange={() =>
                              changeScore(criterion.id, {
                                selectedLevelId: level.id,
                                overridePoints: null,
                              })
                            }
                            type="radio"
                          />
                          <span>
                            <span className="flex items-baseline gap-2 text-sm font-semibold">
                              <span>{level.label}</span>
                              <span className="font-mono text-[10px] text-slate-500">
                                {level.points ?? "—"} pts
                              </span>
                            </span>
                            {level.description ? (
                              <span className="mt-1 block text-xs leading-5 text-slate-500">
                                {level.description}
                              </span>
                            ) : null}
                          </span>
                        </span>
                      </label>
                    ))}
                  </div>
                  <label className="mt-3 block font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-slate-500">
                    Point override
                    <input
                      aria-label={`Point override for ${criterion.title}`}
                      className="mt-1 block w-full border border-slate-300 bg-white px-3 py-2 font-sans text-sm normal-case tracking-normal focus:border-amber-600 focus:outline-none"
                      max={criterion.maxPoints ?? undefined}
                      min="0"
                      onChange={(event) =>
                        changeScore(criterion.id, {
                          overridePoints:
                            event.target.value === ""
                              ? null
                              : Number(event.target.value),
                          selectedLevelId: null,
                        })
                      }
                      placeholder="Use instead of a level"
                      step="0.5"
                      type="number"
                      value={score.overridePoints ?? ""}
                    />
                  </label>
                  <label className="mt-3 block font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-slate-500">
                    Criterion feedback
                    <textarea
                      aria-label={`Feedback for ${criterion.title}`}
                      className="mt-1 block min-h-20 w-full resize-y border border-slate-300 p-3 font-sans text-sm leading-5 normal-case tracking-normal focus:border-amber-600 focus:outline-none"
                      onChange={(event) =>
                        changeScore(criterion.id, {
                          criterionFeedback: event.target.value,
                        })
                      }
                      placeholder="Feedback specific to this criterion"
                      value={score.criterionFeedback}
                    />
                  </label>
                </fieldset>
              )
            })}

            <section className="border border-slate-200 bg-white p-4 shadow-sm">
              <label className="block text-sm font-bold">
                Overall feedback
                <textarea
                  aria-label="Overall feedback"
                  className="mt-3 block min-h-28 w-full resize-y border border-slate-300 p-3 text-sm font-normal leading-6 focus:border-amber-600 focus:outline-none"
                  onChange={(event) => {
                    setOverallFeedback(event.target.value)
                    setDirty(true)
                  }}
                  placeholder="Summarize strengths and next steps"
                  value={overallFeedback}
                />
              </label>
            </section>
          </div>

          <div className="sticky bottom-0 border-t border-slate-300 bg-white/95 p-4 backdrop-blur">
            {saveMutation.isError ? (
              <p
                className="mb-2 text-xs font-semibold text-red-700"
                role="alert"
              >
                {saveError}
              </p>
            ) : null}
            <div className="flex items-center justify-between gap-4">
              <p className="font-mono text-[10px] uppercase tracking-[0.12em] text-slate-500">
                {saveMutation.isSuccess && !dirty
                  ? "Saved just now"
                  : dirty
                    ? "Unsaved changes"
                    : recordQuery.data
                      ? "Saved record loaded"
                      : "Not saved yet"}
              </p>
              <button
                className="min-h-11 bg-amber-600 px-5 text-sm font-bold text-white hover:bg-amber-700 disabled:cursor-not-allowed disabled:bg-slate-300"
                disabled={
                  completed !== rubricQuery.data.criteria.length ||
                  saveMutation.isPending
                }
                onClick={() => saveMutation.mutate()}
                type="button"
              >
                {saveMutation.isPending ? "Saving…" : "Save grading"}
              </button>
            </div>
          </div>
        </aside>
      </div>
    </main>
  )
}
