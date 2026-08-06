import { useState } from "react";
import { Link, useNavigate } from "react-router";
import AppShell from "../components/AppShell";
import { EmptyState, ErrorBanner, LoadingPanel } from "../components/Feedback";
import { useCreateSession, useDeleteSession, useSessions } from "../api/queries";

/** Session list: create, resume, and delete (Requirements 14.8, 14.9, 19.6). */
export default function SessionListPage() {
  const navigate = useNavigate();
  const sessions = useSessions();
  const createSession = useCreateSession();
  const deleteSession = useDeleteSession();

  const [name, setName] = useState("");
  const [pendingDelete, setPendingDelete] = useState<string | null>(null);

  const handleCreate = async (event: React.FormEvent) => {
    event.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) {
      return;
    }
    const created = await createSession.mutateAsync(trimmed);
    setName("");
    navigate(`/sessions/${created.id}/setup`);
  };

  /** Navigate based on session state: sessions with confirmed review go to mark, others to setup */
  const getSessionLink = (session: { id: string; reviewConfirmedAt: string | null; submissionCount: number }) => {
    if (session.reviewConfirmedAt) {
      return `/sessions/${session.id}/review`;
    }
    if (session.submissionCount > 0) {
      return `/sessions/${session.id}/mark`;
    }
    return `/sessions/${session.id}/setup`;
  };

  return (
    <AppShell title="Grading sessions">
      <form onSubmit={handleCreate} className="mb-6 flex flex-wrap items-end gap-3">
        <div className="min-w-64 flex-1">
          <label htmlFor="session-name" className="block text-sm font-medium text-slate-700">
            New session name
          </label>
          <input
            id="session-name"
            value={name}
            onChange={(event) => setName(event.target.value)}
            maxLength={200}
            placeholder="Essay 2 — Section A"
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <button
          type="submit"
          disabled={createSession.isPending || name.trim().length === 0}
          className="rounded bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {createSession.isPending ? "Creating..." : "New Session"}
        </button>
      </form>

      <ErrorBanner error={createSession.error} className="mb-4" />
      <ErrorBanner
        error={sessions.error}
        onRetry={() => void sessions.refetch()}
        className="mb-4"
      />

      {sessions.isLoading && <LoadingPanel label="Loading your sessions..." />}

      {sessions.data?.length === 0 && (
        <EmptyState title="No grading sessions yet">
          Create a session above, then upload a rubric and the submissions you want to grade.
        </EmptyState>
      )}

      {sessions.data && sessions.data.length > 0 && (
        <div className="overflow-hidden rounded border border-slate-200 bg-white">
          <table className="w-full text-left text-sm">
            <caption className="sr-only">Your grading sessions</caption>
            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th scope="col" className="px-4 py-2">Name</th>
                <th scope="col" className="px-4 py-2">Created</th>
                <th scope="col" className="px-4 py-2">Submissions</th>
                <th scope="col" className="px-4 py-2">Status</th>
                <th scope="col" className="px-4 py-2">Last updated</th>
                <th scope="col" className="px-4 py-2 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {sessions.data.map((session) => (
                <tr
                  key={session.id}
                  className="cursor-pointer hover:bg-slate-50"
                  onClick={() => navigate(getSessionLink(session))}
                >
                  <td className="px-4 py-3 font-medium text-slate-900">{session.name}</td>
                  <td className="px-4 py-3 text-slate-600">
                    {new Date(session.createdAt).toLocaleDateString()}
                  </td>
                  <td className="px-4 py-3 text-slate-600">{session.submissionCount}</td>
                  <td className="px-4 py-3 text-slate-600">
                    {session.reviewConfirmedAt ? (
                      <span className="inline-flex items-center rounded border border-emerald-300 bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-800">
                        Review confirmed
                      </span>
                    ) : session.submissionCount > 0 ? (
                      <span className="inline-flex items-center rounded border border-blue-300 bg-blue-50 px-2 py-0.5 text-xs font-medium text-blue-800">
                        Marking
                      </span>
                    ) : (
                      <span className="inline-flex items-center rounded border border-slate-300 bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-700">
                        Setup
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-slate-600">
                    {new Date(session.updatedAt).toLocaleDateString()}
                  </td>
                  <td className="px-4 py-3" onClick={(e) => e.stopPropagation()}>
                    <div className="flex flex-wrap items-center justify-end gap-2">
                      <Link
                        to={`/sessions/${session.id}/setup`}
                        className="rounded border border-slate-300 px-2 py-1 text-xs text-slate-700"
                      >
                        Setup
                      </Link>
                      <Link
                        to={`/sessions/${session.id}/mark`}
                        className="rounded border border-slate-300 px-2 py-1 text-xs text-slate-700"
                      >
                        Mark
                      </Link>
                      <Link
                        to={`/sessions/${session.id}/review`}
                        className="rounded border border-slate-300 px-2 py-1 text-xs text-slate-700"
                      >
                        Review
                      </Link>
                      {pendingDelete === session.id ? (
                        <>
                          <button
                            type="button"
                            onClick={() => {
                              deleteSession.mutate(session.id);
                              setPendingDelete(null);
                            }}
                            className="rounded bg-red-700 px-2 py-1 text-xs font-medium text-white"
                          >
                            Confirm delete
                          </button>
                          <button
                            type="button"
                            onClick={() => setPendingDelete(null)}
                            className="rounded border border-slate-300 px-2 py-1 text-xs"
                          >
                            Cancel
                          </button>
                        </>
                      ) : (
                        <button
                          type="button"
                          onClick={() => setPendingDelete(session.id)}
                          className="rounded border border-red-300 px-2 py-1 text-xs text-red-700"
                        >
                          Delete
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {pendingDelete && (
        <p className="mt-3 text-xs text-red-700" role="alert">
          Deleting a session removes its rubric, submissions, and every grading record. This cannot be
          undone.
        </p>
      )}
      <ErrorBanner error={deleteSession.error} className="mt-4" />
    </AppShell>
  );
}
