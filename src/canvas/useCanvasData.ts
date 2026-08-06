/**
 * Loads Canvas-backed grading data for the marking view.
 *
 * Rubric and queue are fetched once per assignment; the submission is refetched as the
 * TA moves between students, without a full page reload.
 */

import { useCallback, useEffect, useState } from 'react'
import { ApiError, fetchQueue, fetchRubric, fetchSubmission } from './api'
import type { RubricView, Student, SubmissionView } from './types'

export interface CanvasData {
  /** True while the rubric and queue are loading for the first time. */
  loading: boolean
  /** True while switching to a different student; the shell stays rendered. */
  loadingSubmission: boolean
  /** Fatal load error. Never falls back to demo data. */
  error: string | null

  rubric: RubricView | null
  queue: Student[]
  submission: SubmissionView | null

  currentIndex: number
  currentStudent: Student | null

  goToIndex: (index: number) => void
  next: () => void
  previous: () => void
  reload: () => void
}

export function useCanvasData(assignmentId: string): CanvasData {
  const [loading, setLoading] = useState(true)
  const [loadingSubmission, setLoadingSubmission] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [rubric, setRubric] = useState<RubricView | null>(null)
  const [queue, setQueue] = useState<Student[]>([])
  const [submission, setSubmission] = useState<SubmissionView | null>(null)
  const [currentIndex, setCurrentIndex] = useState(0)
  const [reloadToken, setReloadToken] = useState(0)

  // Rubric and queue: once per assignment.
  useEffect(() => {
    let cancelled = false

    async function load() {
      setLoading(true)
      setError(null)
      try {
        const [rubricResult, queueResult] = await Promise.all([
          fetchRubric(assignmentId),
          fetchQueue(assignmentId),
        ])
        if (cancelled) return
        setRubric(rubricResult)
        setQueue(queueResult)
        setCurrentIndex(0)
      } catch (cause) {
        if (cancelled) return
        setError(cause instanceof ApiError ? cause.message : String(cause))
        // Deliberately leave rubric and queue null so the UI shows the failure rather
        // than a stale or fabricated grading session.
        setRubric(null)
        setQueue([])
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [assignmentId, reloadToken])

  // Submission: whenever the selected student changes.
  useEffect(() => {
    const student = queue[currentIndex]
    if (!student) {
      setSubmission(null)
      return
    }

    let cancelled = false

    async function load() {
      setLoadingSubmission(true)
      try {
        const result = await fetchSubmission(assignmentId, student.userId)
        if (!cancelled) setSubmission(result)
      } catch (cause) {
        if (cancelled) return
        // An extraction failure must not block grading — surface it and let the TA
        // score manually against an empty document.
        setSubmission({
          userId: student.userId,
          studentName: student.name,
          paragraphs: [],
          spans: [],
          comments: {},
          extractionError: cause instanceof ApiError ? cause.message : String(cause),
          alreadyGraded: student.alreadyGraded,
          existingScores: {},
        })
      } finally {
        if (!cancelled) setLoadingSubmission(false)
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [assignmentId, queue, currentIndex])

  const goToIndex = useCallback(
    (index: number) => {
      setCurrentIndex((current) => {
        if (index < 0 || index >= queue.length) return current
        return index
      })
    },
    [queue.length],
  )

  const next = useCallback(() => {
    setCurrentIndex((current) => (current + 1 < queue.length ? current + 1 : current))
  }, [queue.length])

  const previous = useCallback(() => {
    setCurrentIndex((current) => (current > 0 ? current - 1 : current))
  }, [])

  const reload = useCallback(() => setReloadToken((token) => token + 1), [])

  return {
    loading,
    loadingSubmission,
    error,
    rubric,
    queue,
    submission,
    currentIndex,
    currentStudent: queue[currentIndex] ?? null,
    goToIndex,
    next,
    previous,
    reload,
  }
}
