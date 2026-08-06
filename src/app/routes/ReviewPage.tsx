import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Link, useParams } from "react-router"

import api from "../api"
import type { CanvasPublication, ReviewData } from "../types"

const reviewKey = (sessionId: string) => ["sessions", sessionId, "review"]
const publicationKey = (sessionId: string) => [
  "sessions",
  sessionId,
  "canvas-publication",
]

function flagLabel(flag: string): string {
  switch (flag) {
    case "incomplete_grading":
      return "Grading incomplete"
    case "extraction_failed":
      return "Submission needs attention"
    case "missing_submission":
      return "Missing submission"
    default:
      return flag.replaceAll("_", " ")
  }
}

export default function ReviewPage() {
  const { id = "" } = useParams<{ id: string }>()
  const queryClient = useQueryClient()
  const reviewQuery = useQuery({
    queryKey: reviewKey(id),
    queryFn: () => api.get<ReviewData>(`/sessions/${id}/review`),
  })
  const publicationQuery = useQuery({
    queryKey: publicationKey(id),
    queryFn: () =>
      api.get<CanvasPublication>(`/sessions/${id}/canvas/publication`),
  })
  const confirmReview = useMutation({
    mutationFn: () => api.post<ReviewData>(`/sessions/${id}/review/confirm`),
    onSuccess: (review) => queryClient.setQueryData(reviewKey(id), review),
  })
  const publishGrades = useMutation({
    mutationFn: () =>
      api.post<CanvasPublication>(`/sessions/${id}/canvas/publication`),
    onSuccess: (publication) =>
      queryClient.setQueryData(publicationKey(id), publication),
  })

  if (reviewQuery.isPending || publicationQuery.isPending) {
    return (
      <main className="grid min-h-screen place-items-center bg-slate-100 font-mono text-xs uppercase tracking-[0.18em] text-slate-500">
        Loading batch review…
      </main>
    )
  }
  if (reviewQuery.isError || publicationQuery.isError) {
    return (
      <main className="grid min-h-screen place-items-center bg-slate-100">
        <div className="border border-red-200 bg-white p-8">
          <h1 className="text-xl font-bold">Batch review unavailable</h1>
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

  const review = reviewQuery.data
  const publication = publicationQuery.data
  const incomplete = review.submissions.some((submission) =>
    submission.flags.includes("incomplete_grading"),
  )
  const confirmed = Boolean(review.reviewConfirmedAt)
  const allPublished =
    publication.summary.total > 0 &&
    publication.summary.published === publication.summary.total

  return (
    <main className="min-h-screen bg-[#f7f8fa] text-slate-950">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-5 py-4 sm:px-8">
          <Link
            className="font-mono text-[10px] uppercase tracking-[0.18em] text-slate-500 hover:text-slate-950"
            to={`/sessions/${id}/mark`}
          >
            ← Grading desk
          </Link>
          <span className="font-mono text-[10px] uppercase tracking-[0.16em] text-slate-500">
            Final review
          </span>
        </div>
      </header>

      <div className="mx-auto max-w-6xl px-5 py-12 sm:px-8 sm:py-16">
        <div className="border-b border-slate-300 pb-8">
          <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.2em] text-amber-700">
            Publish gate
          </p>
          <h1 className="mt-3 text-3xl font-bold tracking-[-0.04em] sm:text-5xl">
            Review grading batch
          </h1>
          <p className="mt-4 max-w-2xl text-sm leading-6 text-slate-600">
            Check every recorded score before publishing. Saving a later grade
            change clears this confirmation, and Canvas is only updated from
            this page.
          </p>
        </div>

        <section className="mt-10" aria-labelledby="student-results-heading">
          <div className="flex flex-wrap items-end justify-between gap-3">
            <div>
              <p className="font-mono text-[10px] uppercase tracking-[0.18em] text-slate-400">
                Student results
              </p>
              <h2
                className="mt-1 text-2xl font-bold"
                id="student-results-heading"
              >
                {review.submissions.length} students in batch
              </h2>
            </div>
            <p className="font-mono text-xs text-slate-500">
              {review.flaggedCount} need attention
            </p>
          </div>

          <ol className="mt-6 divide-y divide-slate-200 border-y border-slate-300">
            {review.submissions.map((submission, index) => {
              const outcome = publication.outcomes.find(
                (item) => item.submissionId === submission.submissionId,
              )
              return (
                <li
                  className="grid gap-3 py-5 sm:grid-cols-[3rem_1fr_auto] sm:items-center"
                  key={submission.submissionId}
                >
                  <span className="font-mono text-xs text-slate-400">
                    {String(index + 1).padStart(2, "0")}
                  </span>
                  <div>
                    <p className="font-semibold">
                      {submission.studentDisplayName}
                    </p>
                    <div className="mt-1 flex flex-wrap gap-2">
                      {submission.flags.map((flag) => (
                        <span
                          className="font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-amber-700"
                          key={flag}
                        >
                          {flagLabel(flag)}
                        </span>
                      ))}
                      {outcome?.status === "published" ? (
                        <span className="font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-emerald-700">
                          Published to Canvas
                        </span>
                      ) : outcome?.status === "failed" ? (
                        <span className="font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-red-700">
                          Canvas publish failed · retry available
                        </span>
                      ) : null}
                    </div>
                  </div>
                  <p className="text-lg font-bold">
                    {submission.total === null
                      ? "—"
                      : `${submission.total}/${submission.maxPossible ?? "—"} pts`}
                  </p>
                </li>
              )
            })}
          </ol>
        </section>

        <section className="mt-10 border border-slate-300 bg-white p-6 sm:p-8">
          <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-slate-400">
            Canvas publication
          </p>
          {confirmed ? (
            <p className="mt-3 font-semibold text-emerald-800">
              Review confirmed
            </p>
          ) : (
            <p className="mt-3 max-w-xl text-sm leading-6 text-slate-600">
              Confirmation records that you reviewed the current saved grades.
              It does not publish anything by itself.
            </p>
          )}

          {confirmReview.isError ? (
            <p className="mt-4 text-sm font-semibold text-red-700" role="alert">
              Every ready submission must have a saved grade before review can
              be confirmed.
            </p>
          ) : null}
          {publishGrades.isError ? (
            <p className="mt-4 text-sm font-semibold text-red-700" role="alert">
              Canvas publication could not start. Check the connection and try
              again.
            </p>
          ) : null}

          <div className="mt-6 flex flex-wrap items-center gap-4">
            {!confirmed ? (
              <button
                className="min-h-12 bg-slate-950 px-6 text-sm font-semibold text-white hover:bg-amber-600 disabled:cursor-not-allowed disabled:opacity-40"
                disabled={incomplete || confirmReview.isPending}
                onClick={() => confirmReview.mutate()}
                type="button"
              >
                {confirmReview.isPending ? "Confirming…" : "Confirm review"}
              </button>
            ) : !allPublished ? (
              <button
                className="min-h-12 bg-amber-600 px-6 text-sm font-semibold text-white hover:bg-amber-700 disabled:cursor-not-allowed disabled:opacity-40"
                disabled={publishGrades.isPending}
                onClick={() => publishGrades.mutate()}
                type="button"
              >
                {publishGrades.isPending
                  ? "Publishing…"
                  : publication.summary.failed > 0
                    ? "Retry failed grades"
                    : "Publish grades to Canvas"}
              </button>
            ) : (
              <p className="font-semibold text-emerald-800">
                All gradeable submissions are published.
              </p>
            )}
            {publication.outcomes.length > 0 ? (
              <p className="font-mono text-xs text-slate-600">
                {publication.summary.published} published ·{" "}
                {publication.summary.failed} failed
              </p>
            ) : null}
          </div>
        </section>
      </div>
    </main>
  )
}
