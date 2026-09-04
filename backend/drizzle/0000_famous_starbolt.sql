CREATE TABLE "async_jobs" (
	"id" uuid PRIMARY KEY NOT NULL,
	"session_id" uuid NOT NULL,
	"type" text NOT NULL,
	"status" text NOT NULL,
	"total_items" integer NOT NULL,
	"completed_items" integer DEFAULT 0 NOT NULL,
	"failed_items" integer DEFAULT 0 NOT NULL,
	"error" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "canvas_assignment_links" (
	"session_id" uuid PRIMARY KEY NOT NULL,
	"course_id" bigint NOT NULL,
	"assignment_id" bigint NOT NULL,
	"criterion_ids" jsonb NOT NULL
);
--> statement-breakpoint
CREATE TABLE "canvas_publication_outcomes" (
	"session_id" uuid NOT NULL,
	"submission_id" uuid NOT NULL,
	"student_display_name" text NOT NULL,
	"status" text NOT NULL,
	"error" text,
	"published_at" timestamp with time zone,
	"grading_record_saved_at" timestamp with time zone NOT NULL,
	CONSTRAINT "canvas_publication_outcomes_session_id_submission_id_pk" PRIMARY KEY("session_id","submission_id")
);
--> statement-breakpoint
CREATE TABLE "criteria" (
	"id" uuid PRIMARY KEY NOT NULL,
	"rubric_id" uuid NOT NULL,
	"title" text NOT NULL,
	"description" text NOT NULL,
	"max_points" double precision,
	"display_color" text NOT NULL,
	"position" integer NOT NULL,
	"requires_completion" boolean NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "criterion_scores" (
	"id" uuid PRIMARY KEY NOT NULL,
	"grading_record_id" uuid NOT NULL,
	"criterion_id" uuid NOT NULL,
	"selected_level_id" uuid,
	"override_points" double precision,
	"criterion_feedback" text NOT NULL
);
--> statement-breakpoint
CREATE TABLE "grading_records" (
	"id" uuid PRIMARY KEY NOT NULL,
	"submission_id" uuid NOT NULL,
	"overall_feedback" text NOT NULL,
	"saved_at" timestamp with time zone NOT NULL,
	"created_at" timestamp with time zone NOT NULL
);
--> statement-breakpoint
CREATE TABLE "grading_sessions" (
	"id" uuid PRIMARY KEY NOT NULL,
	"ta_id" text NOT NULL,
	"name" text NOT NULL,
	"review_confirmed_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "performance_levels" (
	"id" uuid PRIMARY KEY NOT NULL,
	"criterion_id" uuid NOT NULL,
	"label" text NOT NULL,
	"description" text NOT NULL,
	"points" double precision,
	"position" integer NOT NULL
);
--> statement-breakpoint
CREATE TABLE "rubrics" (
	"id" uuid PRIMARY KEY NOT NULL,
	"session_id" uuid NOT NULL,
	"storage_key" text,
	"source_format" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "submissions" (
	"id" uuid PRIMARY KEY NOT NULL,
	"session_id" uuid NOT NULL,
	"storage_key" text,
	"original_filename" text NOT NULL,
	"student_display_name" text NOT NULL,
	"external_student_id" text NOT NULL,
	"external_submission_id" text,
	"identity_status" text NOT NULL,
	"import_status" text NOT NULL,
	"submission_type" text NOT NULL,
	"attempt_count" integer NOT NULL,
	"submitted_at" timestamp with time zone,
	"extraction_status" text NOT NULL,
	"extraction_failure_reason" text,
	"extracted_text" text,
	"extracted_char_count" integer,
	"is_oversized" boolean NOT NULL,
	"position" integer NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "suggested_matches" (
	"id" uuid PRIMARY KEY NOT NULL,
	"submission_id" uuid NOT NULL,
	"criterion_id" uuid NOT NULL,
	"passage_start" integer NOT NULL,
	"passage_end" integer NOT NULL,
	"rationale" text NOT NULL,
	"confidence" double precision NOT NULL,
	"created_at" timestamp with time zone NOT NULL
);
--> statement-breakpoint
ALTER TABLE "async_jobs" ADD CONSTRAINT "async_jobs_session_id_grading_sessions_id_fk" FOREIGN KEY ("session_id") REFERENCES "public"."grading_sessions"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "canvas_assignment_links" ADD CONSTRAINT "canvas_assignment_links_session_id_grading_sessions_id_fk" FOREIGN KEY ("session_id") REFERENCES "public"."grading_sessions"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "canvas_publication_outcomes" ADD CONSTRAINT "canvas_publication_outcomes_session_id_grading_sessions_id_fk" FOREIGN KEY ("session_id") REFERENCES "public"."grading_sessions"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "canvas_publication_outcomes" ADD CONSTRAINT "canvas_publication_outcomes_submission_id_submissions_id_fk" FOREIGN KEY ("submission_id") REFERENCES "public"."submissions"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "criteria" ADD CONSTRAINT "criteria_rubric_id_rubrics_id_fk" FOREIGN KEY ("rubric_id") REFERENCES "public"."rubrics"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "criterion_scores" ADD CONSTRAINT "criterion_scores_grading_record_id_grading_records_id_fk" FOREIGN KEY ("grading_record_id") REFERENCES "public"."grading_records"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "criterion_scores" ADD CONSTRAINT "criterion_scores_criterion_id_criteria_id_fk" FOREIGN KEY ("criterion_id") REFERENCES "public"."criteria"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "criterion_scores" ADD CONSTRAINT "criterion_scores_selected_level_id_performance_levels_id_fk" FOREIGN KEY ("selected_level_id") REFERENCES "public"."performance_levels"("id") ON DELETE set null ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "grading_records" ADD CONSTRAINT "grading_records_submission_id_submissions_id_fk" FOREIGN KEY ("submission_id") REFERENCES "public"."submissions"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "performance_levels" ADD CONSTRAINT "performance_levels_criterion_id_criteria_id_fk" FOREIGN KEY ("criterion_id") REFERENCES "public"."criteria"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "rubrics" ADD CONSTRAINT "rubrics_session_id_grading_sessions_id_fk" FOREIGN KEY ("session_id") REFERENCES "public"."grading_sessions"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "submissions" ADD CONSTRAINT "submissions_session_id_grading_sessions_id_fk" FOREIGN KEY ("session_id") REFERENCES "public"."grading_sessions"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "suggested_matches" ADD CONSTRAINT "suggested_matches_submission_id_submissions_id_fk" FOREIGN KEY ("submission_id") REFERENCES "public"."submissions"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "suggested_matches" ADD CONSTRAINT "suggested_matches_criterion_id_criteria_id_fk" FOREIGN KEY ("criterion_id") REFERENCES "public"."criteria"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE UNIQUE INDEX "criteria_rubric_position_unique" ON "criteria" USING btree ("rubric_id","position");--> statement-breakpoint
CREATE UNIQUE INDEX "criterion_scores_record_criterion_unique" ON "criterion_scores" USING btree ("grading_record_id","criterion_id");--> statement-breakpoint
CREATE UNIQUE INDEX "grading_records_submission_id_unique" ON "grading_records" USING btree ("submission_id");--> statement-breakpoint
CREATE UNIQUE INDEX "performance_levels_criterion_position_unique" ON "performance_levels" USING btree ("criterion_id","position");--> statement-breakpoint
CREATE UNIQUE INDEX "rubrics_session_id_unique" ON "rubrics" USING btree ("session_id");--> statement-breakpoint
CREATE UNIQUE INDEX "submissions_session_position_unique" ON "submissions" USING btree ("session_id","position");