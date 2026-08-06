import { randomUUID } from "node:crypto"

import type { Criterion, Rubric, Session, Submission } from "./types.js"

export type SubmissionInput = Omit<Submission, "id" | "sessionId" | "createdAt" | "artifactUrl" | "storageKey" | "position"> & {
  artifact: {
    data: Buffer
    contentType: "application/pdf"
    filename: string
  } | null
}

export type SubmissionArtifact = {
  data: Buffer
  contentType: "application/pdf"
  filename: string
}

const CRITERION_COLORS = ["#B45309", "#0F766E", "#7E22CE", "#B91C1C", "#0369A1"]

export type RubricInput = {
  sourceFormat?: "manual" | "canvas"
  criteria: Array<{
    title: string
    description?: string
    maxPoints?: number | null
    performanceLevels?: Array<{
      label: string
      description?: string
      points?: number | null
    }>
  }>
}

export class MemoryStore {
  readonly #sessions = new Map<string, Session>()
  readonly #rubrics = new Map<string, Rubric>()
  readonly #submissions = new Map<string, Submission[]>()
  readonly #artifacts = new Map<string, SubmissionArtifact>()

  listSessions(): Session[] {
    return [...this.#sessions.values()].sort((a, b) =>
      b.createdAt.localeCompare(a.createdAt),
    )
  }

  createSession(name: string): Session {
    const now = new Date().toISOString()
    const session: Session = {
      id: randomUUID(),
      taId: "local-ta",
      name,
      reviewConfirmedAt: null,
      createdAt: now,
      updatedAt: now,
    }

    this.#sessions.set(session.id, session)
    this.#submissions.set(session.id, [])
    return session
  }

  getSession(id: string): Session | undefined {
    return this.#sessions.get(id)
  }

  deleteSession(id: string): boolean {
    for (const submission of this.#submissions.get(id) ?? []) {
      this.#artifacts.delete(submission.id)
    }
    this.#rubrics.delete(id)
    this.#submissions.delete(id)
    return this.#sessions.delete(id)
  }

  getRubric(sessionId: string): Rubric | undefined {
    return this.#rubrics.get(sessionId)
  }

  saveRubric(sessionId: string, input: RubricInput): Rubric {
    const existing = this.#rubrics.get(sessionId)
    const now = new Date().toISOString()
    const rubricId = existing?.id ?? randomUUID()

    const criteria: Criterion[] = input.criteria.map(
      (criterion, criterionIndex) => {
        const criterionId = randomUUID()
        return {
          id: criterionId,
          rubricId,
          title: criterion.title.trim(),
          description: criterion.description?.trim() ?? "",
          maxPoints: criterion.maxPoints ?? null,
          displayColor:
            CRITERION_COLORS[criterionIndex % CRITERION_COLORS.length],
          position: criterionIndex,
          requiresCompletion: criterion.maxPoints == null,
          createdAt: now,
          performanceLevels: (criterion.performanceLevels ?? []).map(
            (level, levelIndex) => ({
              id: randomUUID(),
              criterionId,
              label: level.label.trim(),
              description: level.description?.trim() ?? "",
              points: level.points ?? null,
              position: levelIndex,
            }),
          ),
        }
      },
    )

    const rubric: Rubric = {
      id: rubricId,
      sessionId,
      storageKey: null,
      sourceFormat: input.sourceFormat ?? "manual",
      criteria,
      createdAt: existing?.createdAt ?? now,
      updatedAt: now,
    }

    this.#rubrics.set(sessionId, rubric)
    return rubric
  }

  listSubmissions(sessionId: string): Submission[] {
    return this.#submissions.get(sessionId) ?? []
  }

  saveSubmissionBatch(
    sessionId: string,
    input: SubmissionInput[],
  ): Submission[] {
    for (const submission of this.#submissions.get(sessionId) ?? []) {
      this.#artifacts.delete(submission.id)
    }
    const now = new Date().toISOString()
    const submissions = input.map(({ artifact, ...item }, position) => {
      const id = randomUUID()
      if (artifact) this.#artifacts.set(id, artifact)
      return {
        ...item,
        id,
        sessionId,
        storageKey: artifact ? `memory://${id}` : null,
        artifactUrl: artifact
          ? `/api/sessions/${sessionId}/submissions/${id}/artifact`
          : null,
        position,
        createdAt: now,
      }
    })
    this.#submissions.set(sessionId, submissions)
    return submissions
  }

  getSubmissionArtifact(
    sessionId: string,
    submissionId: string,
  ): SubmissionArtifact | undefined {
    const belongsToSession = (this.#submissions.get(sessionId) ?? []).some(
      (submission) => submission.id === submissionId,
    )
    return belongsToSession ? this.#artifacts.get(submissionId) : undefined
  }
}
