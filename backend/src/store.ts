import { randomUUID } from "node:crypto"

import { and, asc, desc, eq, inArray } from "drizzle-orm"

import type { Database } from "./db/connect.js"
import {
  asyncJobs,
  canvasAssignmentLinks,
  canvasPublicationOutcomes,
  criteria,
  criterionScores,
  gradingRecords,
  gradingSessions,
  performanceLevels,
  rubrics,
  submissions,
  suggestedMatches,
} from "./db/schema.js"
import type {
  AsyncJob,
  CanvasPublicationOutcome,
  Criterion,
  GradingRecord,
  Rubric,
  Session,
  Submission,
  SuggestedMatch,
} from "./types.js"

export type CanvasAssignmentLink = {
  courseId: number
  assignmentId: number
  criterionIds: Record<string, string>
}

export type SubmissionInput = Omit<Submission, "id" | "sessionId" | "createdAt" | "artifactUrl" | "position" | "storageKey"> & {
  storageKey?: string | null
}

export type SubmissionArtifact = {
  data: Buffer
  contentType: "application/pdf"
  filename: string
}

export type RubricInput = {
  sourceFormat?: "manual" | "canvas" | "csv"
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

export type GradingRecordInput = {
  overallFeedback: string
  criterionScores: Array<{
    criterionId: string
    selectedLevelId: string | null
    overridePoints: number | null
    criterionFeedback: string
  }>
}

export type SubmissionUpdate = Partial<Pick<Submission, "storageKey" | "importStatus" | "extractionStatus" | "extractionFailureReason" | "extractedText" | "extractedCharCount" | "isOversized">>

export type JobUpdate = Partial<Pick<AsyncJob, "status" | "completedItems" | "failedItems" | "error">>

const CRITERION_COLORS = ["#B45309", "#0F766E", "#7E22CE", "#B91C1C", "#0369A1"]

function iso(value: string): string {
  return new Date(value).toISOString()
}

function mapSession(row: typeof gradingSessions.$inferSelect): Session {
  return {
    ...row,
    reviewConfirmedAt: row.reviewConfirmedAt
      ? iso(row.reviewConfirmedAt)
      : null,
    createdAt: iso(row.createdAt),
    updatedAt: iso(row.updatedAt),
  }
}

function mapSubmission(row: typeof submissions.$inferSelect): Submission {
  return {
    ...row,
    identityStatus: row.identityStatus as Submission["identityStatus"],
    importStatus: row.importStatus as Submission["importStatus"],
    submissionType: row.submissionType as Submission["submissionType"],
    extractionStatus: row.extractionStatus as Submission["extractionStatus"],
    submittedAt: row.submittedAt ? iso(row.submittedAt) : null,
    artifactUrl: row.storageKey
      ? `/api/sessions/${row.sessionId}/submissions/${row.id}/artifact`
      : null,
    createdAt: iso(row.createdAt),
  }
}

function mapJob(row: typeof asyncJobs.$inferSelect): AsyncJob {
  return {
    ...row,
    type: row.type as AsyncJob["type"],
    status: row.status as AsyncJob["status"],
    createdAt: iso(row.createdAt),
    updatedAt: iso(row.updatedAt),
  }
}

export class PostgresStore {
  constructor(readonly database: Database) {}

  async listSessions(): Promise<Session[]> {
    return (
      await this.database
        .select()
        .from(gradingSessions)
        .orderBy(desc(gradingSessions.createdAt))
    ).map(mapSession)
  }

  async createSession(name: string): Promise<Session> {
    const [row] = await this.database
      .insert(gradingSessions)
      .values({ id: randomUUID(), taId: "local-ta", name })
      .returning()
    return mapSession(row)
  }

  async getSession(id: string): Promise<Session | undefined> {
    const [row] = await this.database
      .select()
      .from(gradingSessions)
      .where(eq(gradingSessions.id, id))
      .limit(1)
    return row ? mapSession(row) : undefined
  }

  async deleteSession(id: string): Promise<boolean> {
    return (
      (
        await this.database
          .delete(gradingSessions)
          .where(eq(gradingSessions.id, id))
          .returning({ id: gradingSessions.id })
      ).length > 0
    )
  }

  async getCanvasLink(
    sessionId: string,
  ): Promise<CanvasAssignmentLink | undefined> {
    const [row] = await this.database
      .select()
      .from(canvasAssignmentLinks)
      .where(eq(canvasAssignmentLinks.sessionId, sessionId))
      .limit(1)
    return row
  }

  async saveCanvasLink(
    sessionId: string,
    link: CanvasAssignmentLink,
  ): Promise<void> {
    await this.database.transaction(async (tx) => {
      await tx
        .insert(canvasAssignmentLinks)
        .values({ sessionId, ...link })
        .onConflictDoUpdate({
          target: canvasAssignmentLinks.sessionId,
          set: link,
        })
      await tx
        .delete(canvasPublicationOutcomes)
        .where(eq(canvasPublicationOutcomes.sessionId, sessionId))
    })
  }

  async confirmReview(sessionId: string): Promise<Session | undefined> {
    const now = new Date().toISOString()
    const [row] = await this.database
      .update(gradingSessions)
      .set({ reviewConfirmedAt: now, updatedAt: now })
      .where(eq(gradingSessions.id, sessionId))
      .returning()
    return row ? mapSession(row) : undefined
  }

  async invalidateReview(sessionId: string): Promise<void> {
    await this.database
      .update(gradingSessions)
      .set({ reviewConfirmedAt: null, updatedAt: new Date().toISOString() })
      .where(eq(gradingSessions.id, sessionId))
  }

  async getRubric(sessionId: string): Promise<Rubric | undefined> {
    const [rubricRow] = await this.database
      .select()
      .from(rubrics)
      .where(eq(rubrics.sessionId, sessionId))
      .limit(1)
    if (!rubricRow) return undefined
    const criterionRows = await this.database
      .select()
      .from(criteria)
      .where(eq(criteria.rubricId, rubricRow.id))
      .orderBy(asc(criteria.position))
    const criterionIds = criterionRows.map((row) => row.id)
    const levelRows = criterionIds.length
      ? await this.database
          .select()
          .from(performanceLevels)
          .where(inArray(performanceLevels.criterionId, criterionIds))
          .orderBy(asc(performanceLevels.position))
      : []
    return {
      ...rubricRow,
      sourceFormat: rubricRow.sourceFormat as Rubric["sourceFormat"],
      createdAt: iso(rubricRow.createdAt),
      updatedAt: iso(rubricRow.updatedAt),
      criteria: criterionRows.map(
        (criterion): Criterion => ({
          ...criterion,
          createdAt: iso(criterion.createdAt),
          performanceLevels: levelRows.filter(
            (level) => level.criterionId === criterion.id,
          ),
        }),
      ),
    }
  }

  async saveRubric(sessionId: string, input: RubricInput): Promise<Rubric> {
    await this.database.transaction(async (tx) => {
      const [existing] = await tx
        .select()
        .from(rubrics)
        .where(eq(rubrics.sessionId, sessionId))
        .limit(1)
      const rubricId = existing?.id ?? randomUUID()
      const now = new Date().toISOString()
      if (existing) {
        await tx
          .delete(gradingRecords)
          .where(
            inArray(
              gradingRecords.submissionId,
              tx
                .select({ id: submissions.id })
                .from(submissions)
                .where(eq(submissions.sessionId, sessionId)),
            ),
          )
        await tx.delete(criteria).where(eq(criteria.rubricId, rubricId))
        await tx
          .update(rubrics)
          .set({ sourceFormat: input.sourceFormat ?? "manual", updatedAt: now })
          .where(eq(rubrics.id, rubricId))
      } else {
        await tx.insert(rubrics).values({
          id: rubricId,
          sessionId,
          storageKey: null,
          sourceFormat: input.sourceFormat ?? "manual",
          createdAt: now,
          updatedAt: now,
        })
      }
      for (const [criterionIndex, criterion] of input.criteria.entries()) {
        const criterionId = randomUUID()
        await tx.insert(criteria).values({
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
        })
        if (criterion.performanceLevels?.length) {
          await tx.insert(performanceLevels).values(
            criterion.performanceLevels.map((level, position) => ({
              id: randomUUID(),
              criterionId,
              label: level.label.trim(),
              description: level.description?.trim() ?? "",
              points: level.points ?? null,
              position,
            })),
          )
        }
      }
      await tx
        .update(gradingSessions)
        .set({ reviewConfirmedAt: null, updatedAt: now })
        .where(eq(gradingSessions.id, sessionId))
      await tx
        .delete(canvasPublicationOutcomes)
        .where(eq(canvasPublicationOutcomes.sessionId, sessionId))
    })
    return (await this.getRubric(sessionId))!
  }

  async listSubmissions(sessionId: string): Promise<Submission[]> {
    return (
      await this.database
        .select()
        .from(submissions)
        .where(eq(submissions.sessionId, sessionId))
        .orderBy(asc(submissions.position))
    ).map(mapSubmission)
  }

  async getSubmission(
    sessionId: string,
    submissionId: string,
  ): Promise<Submission | undefined> {
    const [row] = await this.database
      .select()
      .from(submissions)
      .where(
        and(
          eq(submissions.sessionId, sessionId),
          eq(submissions.id, submissionId),
        ),
      )
      .limit(1)
    return row ? mapSubmission(row) : undefined
  }

  async saveSubmissionBatch(
    sessionId: string,
    input: SubmissionInput[],
  ): Promise<Submission[]> {
    await this.database.transaction(async (tx) => {
      await tx.delete(submissions).where(eq(submissions.sessionId, sessionId))
      if (input.length) await tx.insert(submissions).values(
          input.map((item, position) => ({
            id: randomUUID(),
            sessionId,
            position,
            ...item,
          })),
        )
      const now = new Date().toISOString()
      await tx
        .update(gradingSessions)
        .set({ reviewConfirmedAt: null, updatedAt: now })
        .where(eq(gradingSessions.id, sessionId))
      await tx
        .delete(canvasPublicationOutcomes)
        .where(eq(canvasPublicationOutcomes.sessionId, sessionId))
    })
    return this.listSubmissions(sessionId)
  }

  async reserveSubmissionBatch(
    sessionId: string,
    input: SubmissionInput[],
  ): Promise<Submission[]> {
    return this.saveSubmissionBatch(sessionId, input)
  }

  async updateSubmission(
    sessionId: string,
    submissionId: string,
    update: SubmissionUpdate,
  ): Promise<Submission | undefined> {
    const [row] = await this.database
      .update(submissions)
      .set(update)
      .where(
        and(
          eq(submissions.sessionId, sessionId),
          eq(submissions.id, submissionId),
        ),
      )
      .returning()
    return row ? mapSubmission(row) : undefined
  }

  async getGradingRecord(
    submissionId: string,
  ): Promise<GradingRecord | undefined> {
    const [record] = await this.database
      .select()
      .from(gradingRecords)
      .where(eq(gradingRecords.submissionId, submissionId))
      .limit(1)
    if (!record) return undefined
    const scores = await this.database
      .select()
      .from(criterionScores)
      .where(eq(criterionScores.gradingRecordId, record.id))
    return {
      ...record,
      savedAt: iso(record.savedAt),
      createdAt: iso(record.createdAt),
      criterionScores: scores,
    }
  }

  async saveGradingRecord(
    sessionId: string,
    submissionId: string,
    input: GradingRecordInput,
  ): Promise<GradingRecord> {
    await this.database.transaction(async (tx) => {
      const [existing] = await tx
        .select()
        .from(gradingRecords)
        .where(eq(gradingRecords.submissionId, submissionId))
        .limit(1)
      const id = existing?.id ?? randomUUID()
      const now = new Date().toISOString()
      if (existing) {
        await tx
          .update(gradingRecords)
          .set({ overallFeedback: input.overallFeedback, savedAt: now })
          .where(eq(gradingRecords.id, id))
        await tx
          .delete(criterionScores)
          .where(eq(criterionScores.gradingRecordId, id))
      } else {
        await tx.insert(gradingRecords).values({
          id,
          submissionId,
          overallFeedback: input.overallFeedback,
          savedAt: now,
          createdAt: now,
        })
      }
      await tx.insert(criterionScores).values(
        input.criterionScores.map((score) => ({
          id: randomUUID(),
          gradingRecordId: id,
          ...score,
        })),
      )
      await tx
        .update(gradingSessions)
        .set({ reviewConfirmedAt: null, updatedAt: now })
        .where(eq(gradingSessions.id, sessionId))
      await tx
        .delete(canvasPublicationOutcomes)
        .where(
          and(
            eq(canvasPublicationOutcomes.sessionId, sessionId),
            eq(canvasPublicationOutcomes.submissionId, submissionId),
          ),
        )
    })
    return (await this.getGradingRecord(submissionId))!
  }

  async listPublicationOutcomes(
    sessionId: string,
  ): Promise<CanvasPublicationOutcome[]> {
    const rows = await this.database
      .select()
      .from(canvasPublicationOutcomes)
      .where(eq(canvasPublicationOutcomes.sessionId, sessionId))
    const orderedIds = (await this.listSubmissions(sessionId)).map(
      (submission) => submission.id,
    )
    return rows
      .sort(
        (a, b) =>
          orderedIds.indexOf(a.submissionId) -
          orderedIds.indexOf(b.submissionId),
      )
      .map((row) => ({
        ...row,
        status: row.status as CanvasPublicationOutcome["status"],
        publishedAt: row.publishedAt ? iso(row.publishedAt) : null,
        gradingRecordSavedAt: iso(row.gradingRecordSavedAt),
      }))
  }

  async getPublicationOutcome(
    sessionId: string,
    submissionId: string,
  ): Promise<CanvasPublicationOutcome | undefined> {
    const [row] = await this.database
      .select()
      .from(canvasPublicationOutcomes)
      .where(
        and(
          eq(canvasPublicationOutcomes.sessionId, sessionId),
          eq(canvasPublicationOutcomes.submissionId, submissionId),
        ),
      )
      .limit(1)
    return row
      ? {
          ...row,
          status: row.status as CanvasPublicationOutcome["status"],
          publishedAt: row.publishedAt ? iso(row.publishedAt) : null,
          gradingRecordSavedAt: iso(row.gradingRecordSavedAt),
        }
      : undefined
  }

  async savePublicationOutcome(
    sessionId: string,
    outcome: CanvasPublicationOutcome,
  ): Promise<CanvasPublicationOutcome> {
    await this.database
      .insert(canvasPublicationOutcomes)
      .values({ sessionId, ...outcome })
      .onConflictDoUpdate({
        target: [
          canvasPublicationOutcomes.sessionId,
          canvasPublicationOutcomes.submissionId,
        ],
        set: outcome,
      })
    return outcome
  }

  async listSuggestions(submissionId: string): Promise<SuggestedMatch[]> {
    return (
      await this.database
        .select()
        .from(suggestedMatches)
        .where(eq(suggestedMatches.submissionId, submissionId))
        .orderBy(asc(suggestedMatches.createdAt))
    ).map((row) => ({ ...row, createdAt: iso(row.createdAt) }))
  }

  async saveSuggestions(
    submissionId: string,
    input: Array<Omit<SuggestedMatch, "id" | "submissionId" | "createdAt">>,
  ): Promise<SuggestedMatch[]> {
    const createdAt = new Date().toISOString()
    return this.database.transaction(async (tx) => {
      await tx
        .delete(suggestedMatches)
        .where(eq(suggestedMatches.submissionId, submissionId))
      if (!input.length) return []
      return (
        await tx
          .insert(suggestedMatches)
          .values(
            input.map((suggestion) => ({
              ...suggestion,
              id: randomUUID(),
              submissionId,
              createdAt,
            })),
          )
          .returning()
      ).map((row) => ({ ...row, createdAt: iso(row.createdAt) }))
    })
  }

  async createJob(
    sessionId: string,
    type: AsyncJob["type"],
    totalItems: number,
  ): Promise<AsyncJob> {
    const [row] = await this.database
      .insert(asyncJobs)
      .values({
        id: randomUUID(),
        sessionId,
        type,
        status: "pending",
        totalItems,
      })
      .returning()
    return mapJob(row)
  }

  async getJob(id: string): Promise<AsyncJob | undefined> {
    const [row] = await this.database
      .select()
      .from(asyncJobs)
      .where(eq(asyncJobs.id, id))
      .limit(1)
    return row ? mapJob(row) : undefined
  }

  async listOpenJobs(): Promise<AsyncJob[]> {
    return (
      await this.database
        .select()
        .from(asyncJobs)
        .where(inArray(asyncJobs.status, ["pending", "running"]))
        .orderBy(asc(asyncJobs.createdAt))
    ).map(mapJob)
  }

  async updateJob(
    id: string,
    update: JobUpdate,
  ): Promise<AsyncJob | undefined> {
    const [row] = await this.database
      .update(asyncJobs)
      .set({ ...update, updatedAt: new Date().toISOString() })
      .where(eq(asyncJobs.id, id))
      .returning()
    return row ? mapJob(row) : undefined
  }
}
