import {
  bigint,
  boolean,
  doublePrecision,
  integer,
  jsonb,
  pgTable,
  primaryKey,
  text,
  timestamp,
  uniqueIndex,
  uuid,
} from "drizzle-orm/pg-core"

const timestamps = {
  createdAt: timestamp("created_at", { withTimezone: true, mode: "string" })
    .notNull()
    .defaultNow(),
  updatedAt: timestamp("updated_at", { withTimezone: true, mode: "string" })
    .notNull()
    .defaultNow(),
}

export const gradingSessions = pgTable("grading_sessions", {
  id: uuid("id").primaryKey(),
  taId: text("ta_id").notNull(),
  name: text("name").notNull(),
  reviewConfirmedAt: timestamp("review_confirmed_at", {
    withTimezone: true,
    mode: "string",
  }),
  ...timestamps,
})

export const rubrics = pgTable(
  "rubrics",
  {
    id: uuid("id").primaryKey(),
    sessionId: uuid("session_id")
      .notNull()
      .references(() => gradingSessions.id, { onDelete: "cascade" }),
    storageKey: text("storage_key"),
    sourceFormat: text("source_format").notNull(),
    ...timestamps,
  },
  (table) => [uniqueIndex("rubrics_session_id_unique").on(table.sessionId)],
)

export const criteria = pgTable(
  "criteria",
  {
    id: uuid("id").primaryKey(),
    rubricId: uuid("rubric_id")
      .notNull()
      .references(() => rubrics.id, { onDelete: "cascade" }),
    title: text("title").notNull(),
    description: text("description").notNull(),
    maxPoints: doublePrecision("max_points"),
    displayColor: text("display_color").notNull(),
    position: integer("position").notNull(),
    requiresCompletion: boolean("requires_completion").notNull(),
    createdAt: timestamp("created_at", { withTimezone: true, mode: "string" })
      .notNull()
      .defaultNow(),
  },
  (table) => [
    uniqueIndex("criteria_rubric_position_unique").on(
      table.rubricId,
      table.position,
    ),
  ],
)

export const performanceLevels = pgTable(
  "performance_levels",
  {
    id: uuid("id").primaryKey(),
    criterionId: uuid("criterion_id")
      .notNull()
      .references(() => criteria.id, { onDelete: "cascade" }),
    label: text("label").notNull(),
    description: text("description").notNull(),
    points: doublePrecision("points"),
    position: integer("position").notNull(),
  },
  (table) => [
    uniqueIndex("performance_levels_criterion_position_unique").on(
      table.criterionId,
      table.position,
    ),
  ],
)

export const submissions = pgTable(
  "submissions",
  {
    id: uuid("id").primaryKey(),
    sessionId: uuid("session_id")
      .notNull()
      .references(() => gradingSessions.id, { onDelete: "cascade" }),
    storageKey: text("storage_key"),
    originalFilename: text("original_filename").notNull(),
    studentDisplayName: text("student_display_name").notNull(),
    externalStudentId: text("external_student_id"),
    externalSubmissionId: text("external_submission_id"),
    identityStatus: text("identity_status").notNull(),
    importStatus: text("import_status").notNull(),
    submissionType: text("submission_type").notNull(),
    attemptCount: integer("attempt_count").notNull(),
    submittedAt: timestamp("submitted_at", {
      withTimezone: true,
      mode: "string",
    }),
    extractionStatus: text("extraction_status").notNull(),
    extractionFailureReason: text("extraction_failure_reason"),
    extractedText: text("extracted_text"),
    extractedCharCount: integer("extracted_char_count"),
    isOversized: boolean("is_oversized").notNull(),
    position: integer("position").notNull(),
    createdAt: timestamp("created_at", { withTimezone: true, mode: "string" })
      .notNull()
      .defaultNow(),
  },
  (table) => [
    uniqueIndex("submissions_session_position_unique").on(
      table.sessionId,
      table.position,
    ),
  ],
)

export const gradingRecords = pgTable(
  "grading_records",
  {
    id: uuid("id").primaryKey(),
    submissionId: uuid("submission_id")
      .notNull()
      .references(() => submissions.id, { onDelete: "cascade" }),
    overallFeedback: text("overall_feedback").notNull(),
    savedAt: timestamp("saved_at", {
      withTimezone: true,
      mode: "string",
    }).notNull(),
    createdAt: timestamp("created_at", {
      withTimezone: true,
      mode: "string",
    }).notNull(),
  },
  (table) => [
    uniqueIndex("grading_records_submission_id_unique").on(table.submissionId),
  ],
)

export const criterionScores = pgTable(
  "criterion_scores",
  {
    id: uuid("id").primaryKey(),
    gradingRecordId: uuid("grading_record_id")
      .notNull()
      .references(() => gradingRecords.id, { onDelete: "cascade" }),
    criterionId: uuid("criterion_id")
      .notNull()
      .references(() => criteria.id, { onDelete: "cascade" }),
    selectedLevelId: uuid("selected_level_id").references(
      () => performanceLevels.id,
      { onDelete: "set null" },
    ),
    overridePoints: doublePrecision("override_points"),
    criterionFeedback: text("criterion_feedback").notNull(),
  },
  (table) => [
    uniqueIndex("criterion_scores_record_criterion_unique").on(
      table.gradingRecordId,
      table.criterionId,
    ),
  ],
)

export const suggestedMatches = pgTable("suggested_matches", {
  id: uuid("id").primaryKey(),
  submissionId: uuid("submission_id")
    .notNull()
    .references(() => submissions.id, { onDelete: "cascade" }),
  criterionId: uuid("criterion_id")
    .notNull()
    .references(() => criteria.id, { onDelete: "cascade" }),
  passageStart: integer("passage_start").notNull(),
  passageEnd: integer("passage_end").notNull(),
  rationale: text("rationale").notNull(),
  confidence: doublePrecision("confidence").notNull(),
  createdAt: timestamp("created_at", {
    withTimezone: true,
    mode: "string",
  }).notNull(),
})

export const canvasAssignmentLinks = pgTable("canvas_assignment_links", {
  sessionId: uuid("session_id")
    .primaryKey()
    .references(() => gradingSessions.id, { onDelete: "cascade" }),
  courseId: bigint("course_id", { mode: "number" }).notNull(),
  assignmentId: bigint("assignment_id", { mode: "number" }).notNull(),
  criterionIds: jsonb("criterion_ids")
    .$type<Record<string, string>>()
    .notNull(),
})

export const canvasPublicationOutcomes = pgTable(
  "canvas_publication_outcomes",
  {
    sessionId: uuid("session_id")
      .notNull()
      .references(() => gradingSessions.id, { onDelete: "cascade" }),
    submissionId: uuid("submission_id")
      .notNull()
      .references(() => submissions.id, { onDelete: "cascade" }),
    studentDisplayName: text("student_display_name").notNull(),
    status: text("status").notNull(),
    error: text("error"),
    publishedAt: timestamp("published_at", {
      withTimezone: true,
      mode: "string",
    }),
    gradingRecordSavedAt: timestamp("grading_record_saved_at", {
      withTimezone: true,
      mode: "string",
    }).notNull(),
  },
  (table) => [primaryKey({ columns: [table.sessionId, table.submissionId] })],
)

export const asyncJobs = pgTable("async_jobs", {
  id: uuid("id").primaryKey(),
  sessionId: uuid("session_id")
    .notNull()
    .references(() => gradingSessions.id, { onDelete: "cascade" }),
  type: text("type").notNull(),
  status: text("status").notNull(),
  totalItems: integer("total_items").notNull(),
  completedItems: integer("completed_items").notNull().default(0),
  failedItems: integer("failed_items").notNull().default(0),
  error: text("error"),
  ...timestamps,
})
