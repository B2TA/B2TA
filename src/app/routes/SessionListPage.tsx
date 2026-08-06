import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useState, type FormEvent } from "react"
import { Link, useNavigate } from "react-router"

import api from "../api"
import type { Session } from "../types"

const dateFormatter = new Intl.DateTimeFormat("en-CA", {
  day: "numeric",
  month: "short",
  year: "numeric",
})

function SessionLedger({
  onDelete,
  sessions,
}: {
  onDelete: (session: Session) => void
  sessions: Session[]
}) {
  if (sessions.length === 0) {
    return (
      <div className="border-y border-dashed border-slate-300 py-16 text-center">
        <p className="text-base font-semibold text-slate-800">
          No grading sessions yet
        </p>
        <p className="mt-2 text-sm text-slate-500">
          Create a session to prepare your first rubric.
        </p>
      </div>
    )
  }

  return (
    <ol className="divide-y divide-slate-200 border-y border-slate-300">
      {sessions.map((session, index) => (
        <li
          key={session.id}
          className="group grid gap-4 py-6 sm:grid-cols-[3.5rem_1fr_auto] sm:items-center"
        >
          <span className="font-mono text-xs font-semibold tracking-[0.18em] text-amber-700">
            {String(index + 1).padStart(2, "0")}
          </span>
          <div>
            <h2 className="text-lg font-semibold tracking-tight text-slate-950">
              {session.name}
            </h2>
            <p className="mt-1 font-mono text-xs text-slate-500">
              Opened {dateFormatter.format(new Date(session.createdAt))}
            </p>
          </div>
          <div className="flex items-stretch gap-2">
            <Link
              aria-label={`Resume grading ${session.name}`}
              className="inline-flex min-h-11 flex-1 items-center justify-center border border-slate-900 bg-slate-950 px-5 text-sm font-semibold text-white transition hover:bg-amber-600 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-amber-600"
              to={`/sessions/${session.id}/setup`}
            >
              Resume grading
              <span
                aria-hidden="true"
                className="ml-3 transition-transform group-hover:translate-x-1"
              >
                →
              </span>
            </Link>
            <button
              aria-label={`Delete ${session.name}`}
              className="min-h-11 border border-slate-300 px-3 text-slate-500 transition hover:border-red-300 hover:bg-red-50 hover:text-red-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600"
              onClick={() => onDelete(session)}
              title="Delete session"
              type="button"
            >
              <span aria-hidden="true">×</span>
            </button>
          </div>
        </li>
      ))}
    </ol>
  )
}

export default function SessionListPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [isCreating, setIsCreating] = useState(false)
  const [sessionName, setSessionName] = useState("")
  const [deletingSession, setDeletingSession] = useState<Session | null>(null)
  const sessionsQuery = useQuery({
    queryKey: ["sessions"],
    queryFn: () => api.get<Session[]>("/sessions"),
  })
  const createSession = useMutation({
    mutationFn: (name: string) => api.post<Session>("/sessions", { name }),
    onSuccess: (session) => navigate(`/sessions/${session.id}/setup`),
  })
  const deleteSession = useMutation({
    mutationFn: (sessionId: string) =>
      api.delete<void>(`/sessions/${sessionId}`),
    onSuccess: async () => {
      setDeletingSession(null)
      await queryClient.invalidateQueries({ queryKey: ["sessions"] })
    },
  })

  function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const name = sessionName.trim()
    if (name) createSession.mutate(name)
  }

  return (
    <main className="min-h-screen bg-[#f7f8fa] text-slate-950">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-5 py-4 sm:px-8">
          <Link className="flex items-baseline gap-2" to="/sessions">
            <span className="text-xl font-black tracking-[-0.08em]">B2TA</span>
            <span className="hidden font-mono text-[10px] uppercase tracking-[0.2em] text-slate-400 sm:inline">
              Back to TA
            </span>
          </Link>
          <span className="border border-slate-200 px-3 py-1.5 font-mono text-[10px] uppercase tracking-[0.16em] text-slate-500">
            Local workspace
          </span>
        </div>
      </header>

      <section className="mx-auto max-w-6xl px-5 py-14 sm:px-8 sm:py-20">
        <div className="mb-14 max-w-3xl">
          <p className="mb-4 font-mono text-xs font-semibold uppercase tracking-[0.2em] text-amber-700">
            Grading ledger
          </p>
          <h1 className="text-4xl font-bold tracking-[-0.045em] text-slate-950 sm:text-6xl">
            Pick up where you left off.
          </h1>
          <p className="mt-5 max-w-xl text-base leading-7 text-slate-600">
            Each session keeps one rubric, one submission batch, and the grading
            records that connect them.
          </p>
        </div>

        <div className="mb-5 flex items-end justify-between gap-4">
          <div>
            <p className="font-mono text-[10px] uppercase tracking-[0.18em] text-slate-400">
              Active work
            </p>
            <h2 className="mt-1 text-xl font-semibold tracking-tight">
              Grading sessions
            </h2>
          </div>
          <button
            className="min-h-11 border border-amber-600 bg-amber-600 px-5 text-sm font-semibold text-white transition hover:border-amber-700 hover:bg-amber-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-amber-600"
            onClick={() => setIsCreating(true)}
            type="button"
          >
            New grading session
          </button>
        </div>

        {sessionsQuery.isPending ? (
          <p className="border-y border-slate-300 py-12 font-mono text-xs uppercase tracking-[0.16em] text-slate-500">
            Loading sessions…
          </p>
        ) : sessionsQuery.isError ? (
          <div
            className="border-y border-red-300 bg-red-50 px-4 py-6 text-sm text-red-900"
            role="alert"
          >
            Sessions could not be loaded. Check that the B2TA API is running,
            then reload.
          </div>
        ) : (
          <SessionLedger
            onDelete={setDeletingSession}
            sessions={sessionsQuery.data}
          />
        )}
      </section>

      {isCreating ? (
        <div
          aria-labelledby="new-session-title"
          aria-modal="true"
          className="fixed inset-0 z-20 grid place-items-center bg-slate-950/45 px-5 backdrop-blur-[2px]"
          role="dialog"
        >
          <form
            className="w-full max-w-lg bg-white p-7 shadow-2xl sm:p-9"
            onSubmit={handleCreate}
          >
            <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.2em] text-amber-700">
              New ledger
            </p>
            <h2
              className="mt-3 text-2xl font-bold tracking-tight"
              id="new-session-title"
            >
              Name this grading session
            </h2>
            <p className="mt-2 text-sm leading-6 text-slate-600">
              Use the course and assignment name your teaching team will
              recognize.
            </p>
            <label
              className="mt-7 block text-sm font-semibold"
              htmlFor="session-name"
            >
              Session name
            </label>
            <input
              autoFocus
              className="mt-2 min-h-12 w-full border border-slate-300 px-4 text-base outline-none transition placeholder:text-slate-400 focus:border-amber-600 focus:ring-2 focus:ring-amber-600/20"
              id="session-name"
              maxLength={200}
              onChange={(event) => setSessionName(event.target.value)}
              placeholder="CPSC 310 · Assignment 1"
              required
              value={sessionName}
            />
            {createSession.isError ? (
              <p className="mt-3 text-sm text-red-700" role="alert">
                The session could not be created. Check the API and try again.
              </p>
            ) : null}
            <div className="mt-8 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
              <button
                className="min-h-11 border border-slate-300 px-5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
                onClick={() => setIsCreating(false)}
                type="button"
              >
                Cancel
              </button>
              <button
                className="min-h-11 bg-slate-950 px-5 text-sm font-semibold text-white hover:bg-amber-600 disabled:cursor-not-allowed disabled:opacity-50"
                disabled={!sessionName.trim() || createSession.isPending}
                type="submit"
              >
                {createSession.isPending ? "Creating…" : "Create session"}
              </button>
            </div>
          </form>
        </div>
      ) : null}

      {deletingSession ? (
        <div
          aria-labelledby="delete-session-title"
          aria-modal="true"
          className="fixed inset-0 z-20 grid place-items-center bg-slate-950/45 px-5 backdrop-blur-[2px]"
          role="dialog"
        >
          <div className="w-full max-w-lg bg-white p-7 shadow-2xl sm:p-9">
            <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.2em] text-red-700">
              Destructive action
            </p>
            <h2
              className="mt-3 text-2xl font-bold tracking-tight"
              id="delete-session-title"
            >
              Delete this grading session?
            </h2>
            <p className="mt-3 text-sm leading-6 text-slate-600">
              <strong className="font-semibold text-slate-900">
                {deletingSession.name}
              </strong>{" "}
              and its grading work will be removed. This cannot be undone.
            </p>
            {deleteSession.isError ? (
              <p className="mt-4 text-sm text-red-700" role="alert">
                The session could not be deleted. Check the API and try again.
              </p>
            ) : null}
            <div className="mt-8 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
              <button
                className="min-h-11 border border-slate-300 px-5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
                disabled={deleteSession.isPending}
                onClick={() => setDeletingSession(null)}
                type="button"
              >
                Keep session
              </button>
              <button
                className="min-h-11 bg-red-700 px-5 text-sm font-semibold text-white hover:bg-red-800 disabled:cursor-not-allowed disabled:opacity-50"
                disabled={deleteSession.isPending}
                onClick={() => deleteSession.mutate(deletingSession.id)}
                type="button"
              >
                {deleteSession.isPending ? "Deleting…" : "Delete session"}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </main>
  )
}
