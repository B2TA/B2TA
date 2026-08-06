import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useState, type FormEvent } from "react"
import { Link, useParams } from "react-router"

import api, { ApiError } from "../api"
import type { Rubric, Session } from "../types"

type CanvasConnection = {
  connected: true
  baseUrl: string
  user: { id: number name: string }
}

type CanvasCourse = {
  id: number
  name: string
  courseCode: string
}

type CanvasAssignment = {
  id: number
  name: string
  pointsPossible: number | null
  hasRubric: boolean
}

function RubricSummary({ rubric }: { rubric: Rubric }) {
  const totalPoints = rubric.criteria.reduce(
    (sum, criterion) => sum + (criterion.maxPoints ?? 0),
    0,
  )

  return (
    <section
      className="mt-12 border-t border-slate-300 pt-8"
      aria-labelledby="rubric-heading"
    >
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.2em] text-emerald-700">
            Imported from Canvas
          </p>
          <h2
            className="mt-2 text-2xl font-bold tracking-tight"
            id="rubric-heading"
          >
            Assignment rubric
          </h2>
        </div>
        <p className="font-mono text-xs text-slate-500">
          {rubric.criteria.length} criteria · {totalPoints} points
        </p>
      </div>

      <ol className="divide-y divide-slate-200 border-y border-slate-300">
        {rubric.criteria.map((criterion, index) => (
          <li
            className="grid gap-5 py-6 md:grid-cols-[3rem_1fr_auto]"
            key={criterion.id}
          >
            <span className="font-mono text-xs font-semibold tracking-[0.18em] text-amber-700">
              {String(index + 1).padStart(2, "0")}
            </span>
            <div>
              <h3 className="text-lg font-semibold tracking-tight">
                {criterion.title}
              </h3>
              {criterion.description ? (
                <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600">
                  {criterion.description}
                </p>
              ) : null}
              {criterion.performanceLevels.length > 0 ? (
                <div className="mt-4 flex flex-wrap gap-2">
                  {criterion.performanceLevels.map((level) => (
                    <span
                      className="border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-700"
                      key={level.id}
                    >
                      {level.label}
                      {level.points !== null ? ` · ${level.points}` : ""}
                    </span>
                  ))}
                </div>
              ) : null}
            </div>
            <span className="font-mono text-sm font-semibold text-slate-700">
              {criterion.maxPoints ?? "—"} pts
            </span>
          </li>
        ))}
      </ol>
    </section>
  )
}

export default function SessionSetupPage() {
  const { id } = useParams<{ id: string }>()
  const queryClient = useQueryClient()
  const [canvasUrl, setCanvasUrl] = useState("")
  const [accessToken, setAccessToken] = useState("")
  const [connection, setConnection] = useState<CanvasConnection | null>(null)
  const [courseId, setCourseId] = useState("")
  const [assignmentId, setAssignmentId] = useState("")

  const sessionQuery = useQuery({
    queryKey: ["sessions", id],
    queryFn: () => api.get<Session>(`/sessions/${id}`),
    enabled: Boolean(id),
  })
  const rubricQuery = useQuery({
    queryKey: ["sessions", id, "rubric"],
    queryFn: async () => {
      try {
        return await api.get<Rubric>(`/sessions/${id}/rubric`)
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) return null
        throw error
      }
    },
    enabled: Boolean(id),
  })
  const connectCanvas = useMutation({
    mutationFn: () =>
      api.post<CanvasConnection>("/canvas/connection", {
        baseUrl: canvasUrl,
        accessToken,
      }),
    onSuccess: (nextConnection) => {
      setConnection(nextConnection)
      setAccessToken("")
    },
  })
  const coursesQuery = useQuery({
    queryKey: ["canvas", "courses"],
    queryFn: () => api.get<CanvasCourse[]>("/canvas/courses"),
    enabled: Boolean(connection),
  })
  const assignmentsQuery = useQuery({
    queryKey: ["canvas", "courses", courseId, "assignments"],
    queryFn: () =>
      api.get<CanvasAssignment[]>(`/canvas/courses/${courseId}/assignments`),
    enabled: Boolean(connection && courseId),
  })
  const importRubric = useMutation({
    mutationFn: () =>
      api.post<Rubric>(`/sessions/${id}/canvas/import`, {
        courseId: Number(courseId),
        assignmentId: Number(assignmentId),
      }),
    onSuccess: (rubric) =>
      queryClient.setQueryData(["sessions", id, "rubric"], rubric),
  })

  function handleConnect(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    connectCanvas.mutate()
  }

  const assignments = assignmentsQuery.data ?? []
  const selectedAssignment = assignments.find(
    (assignment) => String(assignment.id) === assignmentId,
  )

  return (
    <main className="min-h-screen bg-[#f7f8fa] text-slate-950">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-5 py-4 sm:px-8">
          <Link className="flex items-baseline gap-2" to="/sessions">
            <span className="text-xl font-black tracking-[-0.08em]">B2TA</span>
            <span className="font-mono text-[10px] uppercase tracking-[0.2em] text-slate-400">
              ← Sessions
            </span>
          </Link>
          <span className="font-mono text-[10px] uppercase tracking-[0.16em] text-slate-500">
            Session setup
          </span>
        </div>
      </header>

      <div className="mx-auto max-w-6xl px-5 py-12 sm:px-8 sm:py-16">
        <div className="mb-10 border-b border-slate-300 pb-8">
          <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.2em] text-amber-700">
            Source assignment
          </p>
          <h1 className="mt-3 text-3xl font-bold tracking-[-0.04em] sm:text-5xl">
            {sessionQuery.data?.name ?? "Prepare grading session"}
          </h1>
          <p className="mt-4 max-w-2xl text-sm leading-6 text-slate-600">
            Connect the Canvas assignment that owns the rubric and submissions.
            B2TA keeps the grading work here.
          </p>
        </div>

        <div className="grid gap-10 lg:grid-cols-[minmax(0,1fr)_18rem]">
          <section aria-labelledby="canvas-connect-heading">
            <p className="font-mono text-[10px] uppercase tracking-[0.18em] text-slate-400">
              Step 01
            </p>
            <h2
              className="mt-2 text-2xl font-bold tracking-tight"
              id="canvas-connect-heading"
            >
              Connect this session to Canvas
            </h2>

            {!connection ? (
              <form className="mt-7 grid gap-5" onSubmit={handleConnect}>
                <label
                  className="grid gap-2 text-sm font-semibold"
                  htmlFor="canvas-url"
                >
                  Canvas URL
                  <input
                    className="min-h-12 border border-slate-300 bg-white px-4 font-normal outline-none focus:border-amber-600 focus:ring-2 focus:ring-amber-600/20"
                    id="canvas-url"
                    onChange={(event) => setCanvasUrl(event.target.value)}
                    placeholder="https://canvas.example.edu"
                    required
                    type="url"
                    value={canvasUrl}
                  />
                </label>
                <label
                  className="grid gap-2 text-sm font-semibold"
                  htmlFor="canvas-token"
                >
                  Personal access token
                  <input
                    autoComplete="off"
                    className="min-h-12 border border-slate-300 bg-white px-4 font-normal outline-none focus:border-amber-600 focus:ring-2 focus:ring-amber-600/20"
                    id="canvas-token"
                    onChange={(event) => setAccessToken(event.target.value)}
                    required
                    type="password"
                    value={accessToken}
                  />
                </label>
                <p className="text-xs leading-5 text-slate-500">
                  The token is sent directly to the B2TA backend, held only in
                  memory, and cleared when the backend stops.
                </p>
                {connectCanvas.isError ? (
                  <p
                    className="border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800"
                    role="alert"
                  >
                    Canvas could not be connected. Check the site URL and token,
                    then try again.
                  </p>
                ) : null}
                <button
                  className="min-h-12 w-fit bg-slate-950 px-6 text-sm font-semibold text-white hover:bg-amber-600 disabled:opacity-50"
                  disabled={
                    connectCanvas.isPending || !canvasUrl || !accessToken
                  }
                  type="submit"
                >
                  {connectCanvas.isPending ? "Connecting…" : "Connect Canvas"}
                </button>
              </form>
            ) : (
              <div className="mt-7 border-l-4 border-emerald-600 bg-emerald-50 px-5 py-4">
                <p className="font-semibold text-emerald-950">
                  Connected as {connection.user.name}
                </p>
                <p className="mt-1 font-mono text-xs text-emerald-800">
                  {connection.baseUrl}
                </p>
              </div>
            )}

            {connection ? (
              <div className="mt-10 border-t border-slate-300 pt-8">
                <p className="font-mono text-[10px] uppercase tracking-[0.18em] text-slate-400">
                  Step 02
                </p>
                <h2 className="mt-2 text-2xl font-bold tracking-tight">
                  Choose the source assignment
                </h2>
                <div className="mt-7 grid gap-5 sm:grid-cols-2">
                  <label
                    className="grid gap-2 text-sm font-semibold"
                    htmlFor="canvas-course"
                  >
                    Course
                    <select
                      className="min-h-12 border border-slate-300 bg-white px-4 font-normal"
                      id="canvas-course"
                      onChange={(event) => {
                        setCourseId(event.target.value)
                        setAssignmentId("")
                      }}
                      value={courseId}
                    >
                      <option value="">Choose a course</option>
                      {(coursesQuery.data ?? []).map((course) => (
                        <option key={course.id} value={course.id}>
                          {course.courseCode ? `${course.courseCode} · ` : ""}
                          {course.name}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label
                    className="grid gap-2 text-sm font-semibold"
                    htmlFor="canvas-assignment"
                  >
                    Assignment
                    <select
                      className="min-h-12 border border-slate-300 bg-white px-4 font-normal disabled:bg-slate-100"
                      disabled={!courseId || assignmentsQuery.isPending}
                      id="canvas-assignment"
                      onChange={(event) => setAssignmentId(event.target.value)}
                      value={assignmentId}
                    >
                      <option value="">Choose an assignment</option>
                      {assignments.map((assignment) => (
                        <option
                          disabled={!assignment.hasRubric}
                          key={assignment.id}
                          value={assignment.id}
                        >
                          {assignment.name}
                          {assignment.hasRubric ? "" : " · No rubric"}
                        </option>
                      ))}
                    </select>
                  </label>
                </div>
                {importRubric.isError ? (
                  <p
                    className="mt-5 border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800"
                    role="alert"
                  >
                    The Canvas rubric could not be imported. Choose an
                    assignment with a rubric and try again.
                  </p>
                ) : null}
                <button
                  className="mt-6 min-h-12 bg-amber-600 px-6 text-sm font-semibold text-white hover:bg-amber-700 disabled:opacity-50"
                  disabled={
                    !selectedAssignment?.hasRubric || importRubric.isPending
                  }
                  onClick={() => importRubric.mutate()}
                  type="button"
                >
                  {importRubric.isPending ? "Importing…" : "Import rubric"}
                </button>
              </div>
            ) : null}
          </section>

          <aside className="h-fit border border-slate-200 bg-white p-5 text-sm leading-6 text-slate-600">
            <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-slate-400">
              Token safety
            </p>
            <p className="mt-3">
              Use a token created by the same Canvas account that can access and
              grade this assignment.
            </p>
            <p className="mt-3">
              B2TA never places the token in a URL, browser storage, or API
              response.
            </p>
          </aside>
        </div>

        {rubricQuery.data ? <RubricSummary rubric={rubricQuery.data} /> : null}
      </div>
    </main>
  )
}
