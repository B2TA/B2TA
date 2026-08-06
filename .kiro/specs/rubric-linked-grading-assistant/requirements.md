# Requirements Document

## Introduction

The Rubric-Linked Grading Assistant is a standalone web application that reduces the time a Teaching Assistant spends grading written assignments by connecting each rubric criterion directly to the passages of a student submission that provide evidence for that criterion.

A Teaching Assistant uploads a rubric file (or enters a rubric manually), uploads a batch of student submissions, and then works through the batch in a marking view. The marking view presents the rubric as a panel of criterion cards beside a document viewer. The application analyzes each submission and highlights candidate evidence passages in a color unique to each criterion, with a rationale explaining every match. The Teaching Assistant confirms or rejects matches, selects a performance level per criterion, writes feedback, and saves before advancing to the next submission. Before grades leave the application, the Teaching Assistant passes through a review stage that presents every student's scores and flags for confirmation. Final grades export as CSV and as a Canvas-gradebook-compatible CSV.

The Teaching Assistant performs all grading. The application does not assign scores. Every score in an export is a score a Teaching Assistant selected, derived from the fixed point value of the chosen performance level or from an explicit manual override.

The system is delivered as a React single-page application hosted on AWS Amplify, a Java Spring Boot backend on Amazon ECS Fargate behind an Application Load Balancer, Amazon S3 for file storage using pre-signed URLs for direct browser-to-S3 transfer, and Amazon Bedrock for the AI analysis.

## Confirmed Decisions

These points were decided before drafting and are reflected throughout the document.

- **D1 — No autosave or crash recovery.** Grading data is persisted only when a Teaching Assistant saves explicitly, advances with save, or exports. Background autosave, browser-local draft storage, and mid-session crash recovery are out of scope. See Requirement 14.
- **D2 — Missing evidence is stated, never hidden or guessed.** A criterion with no qualifying evidence passage displays an explicit "no evidence found" state. The application does not fabricate a passage and does not silently omit the criterion. See Requirement 6.
- **D3 — A review stage precedes export.** Grades cannot be exported without passing through a review screen that lists every submission, its per-criterion scores, its total, and any outstanding flags. See Requirement 15.
- **D4 — Rubric file formats are PDF, CSV, and XLSX.** Canvas rubric export is not a separately supported input format. See Requirement 1.
- **D5 — Fixed point values per performance level, and the Teaching Assistant's scores are preserved on export.** Each performance level carries a fixed point value. The exported file contains exactly the scores the Teaching Assistant selected. See Requirement 11 and Requirement 16.

## Assumptions

These assumptions were made to complete the draft. Each is stated so it can be confirmed or overridden during review. Changing any of them changes the corresponding requirements.

- **A1 — Authentication**: An Amazon Cognito user pool with email and password authentication is used, accounts are created by an administrator rather than open self-service signup, and there is a single TA role with no instructor oversight role. Institutional single sign-on federation is deferred. See Requirement 18.
- **A2 — Matching approach**: Candidate passages are shortlisted by embedding similarity, then Amazon Bedrock returns character offsets, a rationale, and a confidence value per match, so every highlight traces back to real submission text. See Requirement 6.
- **A3 — No optical character recognition**: A scanned or image-only PDF with no extractable text layer is flagged as unreadable and graded manually. See Requirement 4 and Requirement 8.
- **A4 — Unparseable rubric**: A rubric file that cannot be parsed is rejected with a specific reason, and the manual rubric editor opens prepopulated with any partially recovered data. See Requirement 2.
- **A5 — Partial batch failure proceeds**: A submission file that cannot be parsed does not fail the batch. The submission is retained and flagged for manual attention. See Requirement 4.
- **A6 — Long submissions**: Submissions are chunked and analyzed in full up to a hard cap of 100,000 extracted characters. A submission exceeding the cap is flagged rather than silently truncated. See Requirement 5.
- **A7 — Student identity**: Student identity is derived from the Canvas submission filename convention where present, falling back to the filename stem, with a confirmation step before grading. Duplicate resolved names are flagged as conflicts for the TA rather than merged or split automatically. See Requirement 5.
- **A8 — Plain text submissions**: `.txt` and `.md` submission files are accepted alongside `.pdf` and `.docx`. See Requirement 4.
- **A9 — Partial export permitted with confirmation**: Export is not blocked by incomplete grading. Incomplete submissions export with an empty score rather than a zero, after explicit TA confirmation. See Requirement 15 and Requirement 16.
- **A10 — Match feedback retained, no retraining**: TA confirmations and rejections are stored and included in the generic export, but no model tuning occurs in this specification. See Requirement 10.

## Deferred Design Decisions

The following are explicitly not decided in this document and are to be resolved in the design phase:

- Choice of datastore for structured data (parsed rubric criteria, per-student scores, comments, and match confirmations), including justification against the access patterns in Requirements 11, 12, 13, and 14.
- Amazon Bedrock model selection and prompt structure for Requirements 6 and 12.
- S3 bucket and key layout, lifecycle policy durations, and the IAM trust relationships between the Application Load Balancer, the ECS task role, and Amazon Bedrock.
- ECS service boundaries between synchronous request handling and long-running ingestion and analysis work.

## Out of Scope

The following are out of scope for this specification and are recorded as future considerations:

- Learning management system integration and grade sync.
- Multi-grader collaboration, grade moderation, and inter-rater reliability.
- Plagiarism and similarity detection.
- Autosave, background draft persistence, and mid-session crash recovery.
- Optical character recognition of scanned documents.
- Rubric authoring beyond the manual editor described in Requirement 2.
- Grading analytics dashboards.
- Layouts for viewport widths below 1024 pixels.

## Glossary

- **TA**: A Teaching Assistant, the sole human role in this system, authenticated as described in Requirement 18.
- **Grading_App**: The React single-page application delivered through AWS Amplify Hosting.
- **Grading_API**: The Java Spring Boot service running on Amazon ECS Fargate behind an Application Load Balancer.
- **Rubric**: A structured collection of one or more Criterion records associated with a single assignment.
- **Criterion**: A single rubric row, consisting of a title, a description, a maximum point value, a unique display color, and an ordered list of Performance_Level records.
- **Performance_Level**: A named achievement tier within a Criterion, consisting of a label, a description, and a fixed point value.
- **Rubric_Parser**: The Grading_API component that converts an uploaded rubric file into a Rubric.
- **Rubric_Printer**: The Grading_API component that serializes a Rubric back into the CSV rubric interchange format.
- **Rubric_Editor**: The Grading_App component through which a TA creates or modifies a Rubric by direct entry.
- **Submission**: The extracted text of one student's assignment file, together with the identity of the associated student and a reference to the original file in Amazon S3.
- **Submission_Batch**: The ordered set of Submission records that a TA grades in one Grading_Session.
- **Upload_Service**: The Grading_API component that issues pre-signed Amazon S3 URLs and records upload completion.
- **Text_Extractor**: The Grading_API component that extracts plain text and character offsets from a submission file.
- **Submission_Ingestor**: The Grading_API component that expands uploaded archives, invokes the Text_Extractor per file, and creates Submission records.
- **Roster_Resolver**: The Grading_API component that derives a student identity from a submission filename.
- **Match_Engine**: The Grading_API component that produces Suggested_Match records by combining embedding similarity with an Amazon Bedrock analysis call.
- **Passage**: A contiguous span of Submission text identified by a start character offset and an end character offset.
- **Suggested_Match**: An unconfirmed association produced by the Match_Engine between one Criterion and one Passage, carrying a Match_Rationale and a Match_Confidence.
- **Confirmed_Match**: A Suggested_Match that a TA has accepted, or an association a TA has created by selecting text directly.
- **Match_Rationale**: A short natural-language explanation of why a Passage was associated with a Criterion.
- **Match_Confidence**: A numeric value between 0 and 1 inclusive expressing the Match_Engine's assessed strength of a Suggested_Match.
- **Marking_View**: The Grading_App screen on which a TA grades one Submission.
- **Rubric_Panel**: The region of the Marking_View that lists Criterion cards.
- **Document_Viewer**: The region of the Marking_View that renders Submission text with highlights.
- **Score_Calculator**: The Grading_App component that derives a total score from selected Performance_Level records and manual overrides.
- **Comment_Assistant**: The Grading_API component that produces candidate feedback text from selected Performance_Level records using Amazon Bedrock.
- **Grading_Record**: The per-student set of selected Performance_Level records, point overrides, feedback text, and Confirmed_Match records.
- **Unsaved_Changes**: Grading_Record edits a TA has made in the Grading_App that the Grading_API has not yet stored in the Grading_Store.
- **Grading_Session**: A persistent association between one Rubric, one Submission_Batch, and the Grading_Record set produced by one TA.
- **Grading_Store**: The persistence layer holding Grading_Session, Rubric, Submission metadata, and Grading_Record data.
- **Review_Screen**: The Grading_App screen that presents every Grading_Record in a Grading_Session for TA confirmation before export.
- **Export_Service**: The Grading_API component that serializes confirmed Grading_Record data into an export file in Amazon S3.
- **Auth_Service**: The Amazon Cognito user pool and the Grading_API token validation layer.

## Requirements

### Requirement 1: Rubric File Ingestion and Parsing

**User Story:** As a TA, I want to drop my existing rubric file into the application, so that I do not retype criteria that already exist.

#### Acceptance Criteria

1. THE Grading_App SHALL present a rubric upload zone that accepts a file dropped by pointer and a file chosen through a file picker.
2. WHEN a TA supplies one rubric file whose extension compared without regard to letter case is `.pdf`, `.csv`, or `.xlsx` and whose size is between 1 byte and 5,242,880 bytes inclusive, THE Upload_Service SHALL issue a pre-signed Amazon S3 URL scoped to a single object key and valid for 15 minutes from the time the URL is issued.
3. IF a TA supplies a rubric file whose extension compared without regard to letter case is not `.pdf`, `.csv`, or `.xlsx`, THEN THE Grading_App SHALL reject the file before requesting a pre-signed Amazon S3 URL, create no Amazon S3 object, and display the list of accepted rubric formats.
4. WHEN an Amazon S3 upload of a rubric file completes, THE Rubric_Parser SHALL produce a Rubric holding between 1 and 30 Criterion records in which every Criterion carries a title of 1 to 200 characters, a description of 0 to 2,000 characters, a maximum point value between 0 and 1,000 inclusive, and between 1 and 10 Performance_Level records each carrying a point value between 0 and 1,000 inclusive.
5. WHEN the Rubric_Parser processes a rubric laid out as a table with one Criterion per row and one Performance_Level per column, THE Rubric_Parser SHALL read the first row as a header row supplying Performance_Level labels, map each row after the header row to one Criterion, and map each cell of that row holding at least one non-whitespace character to one Performance_Level of that Criterion.
6. THE Rubric_Parser SHALL assign each Criterion a display color drawn from a fixed palette of at least 30 colors, each palette color having a contrast ratio of at least 3 to 1 against the Rubric_Panel background, such that no two Criterion records in the same Rubric carry the same palette color.
7. WHEN the Rubric_Parser completes, THE Grading_App SHALL display the parsed Criterion list with title, maximum point value, display color, and Performance_Level labels for TA review and correction before grading begins.
8. WHEN a rubric file of 5,242,880 bytes or smaller is uploaded, THE Rubric_Parser SHALL return either a Rubric or a parse failure within 10 seconds of upload completion.
9. IF a rubric file exceeds 5,242,880 bytes or is 0 bytes, THEN THE Grading_App SHALL reject the file before requesting a pre-signed Amazon S3 URL, create no Amazon S3 object, and display the observed file size in bytes together with the permitted range of 1 byte to 5,242,880 bytes.
10. IF the Rubric_Parser cannot identify at least one Criterion in an uploaded rubric file, THEN THE Grading_API SHALL return a failure that names the file, the attempted format, and the reason for the failure.
11. IF the Rubric_Parser identifies a Criterion whose maximum point value or a Performance_Level whose point value is absent or non-numeric, THEN THE Rubric_Parser SHALL retain that Criterion and every Performance_Level of that Criterion, set each such point value to unresolved, mark the Criterion as requiring TA completion, and complete the parse as successful.
12. IF an uploaded rubric PDF yields fewer than 50 extractable characters, THEN THE Grading_API SHALL return a failure identifying the file as containing no extractable text.
13. IF a TA supplies two or more files in the rubric upload zone in one drop action or one file picker action, THEN THE Grading_App SHALL reject the action, create no Amazon S3 object, and display that exactly one rubric file is accepted per upload.
14. IF the Rubric_Parser has returned neither a Rubric nor a parse failure within 10 seconds of upload completion, THEN THE Grading_API SHALL end the parse, return a parse-timeout failure naming the file and the 10 second bound, and store no partial Rubric.
15. IF an upload to a pre-signed Amazon S3 URL fails because the 15 minute validity period of that URL has elapsed, THEN THE Grading_App SHALL display that the upload window expired and provide a control that requests a new pre-signed Amazon S3 URL and retries the upload.

### Requirement 2: Manual Rubric Entry and Correction

**User Story:** As a TA, I want to enter or correct rubric criteria by hand, so that I can grade with a rubric that has no machine-readable file or that parsed imperfectly.

#### Acceptance Criteria

1. THE Rubric_Editor SHALL allow a TA to create a Criterion by entering a title of 1 to 200 characters, a description of 0 to 2,000 characters, a maximum point value between 0.01 and 1,000 inclusive expressed with at most 2 decimal places, and between 1 and 10 Performance_Level records each carrying a label of 1 to 100 characters and a point value between 0 and the maximum point value of that Criterion inclusive, and SHALL assign the created Criterion a display color that differs from the display color of every other Criterion in the same Rubric.
2. THE Rubric_Editor SHALL allow a TA to modify the title, description, maximum point value, display color, Performance_Level labels, and Performance_Level point values of an existing Criterion within the ranges stated in criterion 1.
3. THE Rubric_Editor SHALL allow a TA to delete any Criterion from the Rubric.
4. THE Rubric_Editor SHALL allow a TA to move any Criterion to any position in the Criterion list and SHALL store the resulting order as the Criterion order of the Rubric.
5. IF a rubric file parse fails as described in Requirement 1, THEN THE Grading_App SHALL open the Rubric_Editor prepopulated with every Criterion the Rubric_Parser recovered, and SHALL open the Rubric_Editor with an empty Criterion list when the Rubric_Parser recovered zero Criterion records.
6. IF a TA attempts to start grading while the Rubric holds zero Criterion records, or while any Criterion has an unresolved maximum point value, zero Performance_Level records, or a title holding no non-whitespace character, THEN THE Grading_App SHALL block the transition to the Marking_View and identify each blocking Criterion by title, or by its position in the Criterion list when that title holds no non-whitespace character.
7. IF a TA sets the point value of a Performance_Level above the maximum point value of the containing Criterion, THEN THE Rubric_Editor SHALL reject the entry, retain the previously stored point value of that Performance_Level, and display the Criterion title together with the permitted range of 0 to the maximum point value of that Criterion.
8. IF a TA enters a maximum point value that is non-numeric, below 0.01, above 1,000, or expressed with more than 2 decimal places, THEN THE Rubric_Editor SHALL reject the entry, retain the previously stored maximum point value, and display the permitted range of 0.01 to 1,000 with at most 2 decimal places.
9. WHEN a TA confirms the Rubric, THE Grading_API SHALL store the Rubric in the Grading_Store as the Rubric of the Grading_Session within 3 seconds.
10. IF the Grading_API cannot store the Rubric in the Grading_Store when a TA confirms the Rubric, THEN THE Grading_App SHALL retain every entered Criterion in the Rubric_Editor, block the transition to the Marking_View, display the failure reason, and provide a retry control.

### Requirement 3: Rubric Serialization and Round-Trip Fidelity

**User Story:** As a TA, I want to save a corrected rubric back out as a file, so that I can reuse the corrected rubric for the next assignment without repeating the corrections.

#### Acceptance Criteria

1. THE Rubric_Printer SHALL serialize a Rubric into a UTF-8 encoded CSV rubric interchange format that uses the one Criterion per row and one Performance_Level per column table layout described in Requirement 1 and that the Rubric_Parser accepts through the `.csv` input path.
2. WHEN a TA requests a rubric export, THE Export_Service SHALL write the serialized Rubric to a single Amazon S3 object key and return a pre-signed download URL for that object key valid for 15 minutes from the time the URL is issued.
3. THE Grading_API SHALL yield, by applying the Rubric_Printer and then the Rubric_Parser to a Rubric, a Rubric whose Criterion count, Criterion order, Criterion titles, Criterion descriptions, Criterion maximum point values, Criterion display colors, Performance_Level count per Criterion, Performance_Level order, Performance_Level labels, Performance_Level descriptions, and Performance_Level point values equal those of the original Rubric field by field, with text values equal character for character, numeric values equal exactly, and a maximum point value stored as unresolved preserved as unresolved.
4. THE Grading_API SHALL yield, by applying the Rubric_Parser, then the Rubric_Printer, then the Rubric_Parser to a rubric file that the Rubric_Parser accepts, a Rubric equal field by field to the result of the first Rubric_Parser application.
5. IF a Criterion field value or a Performance_Level field value contains a comma, a double quote, a line break, or a leading or trailing whitespace character, THEN THE Rubric_Printer SHALL emit the value with RFC 4180 quoting so that the Rubric_Parser recovers the original value character for character, including every leading and trailing whitespace character.
6. IF a TA requests a rubric export while the Rubric holds zero Criterion records, THEN THE Grading_App SHALL block the export, create no Amazon S3 object, and display that a Rubric requires at least one Criterion to export.
7. IF writing the serialized Rubric to Amazon S3 fails or issuing the pre-signed download URL fails, THEN THE Grading_App SHALL leave the Rubric in the Grading_Store unchanged, display the failure reason, and provide a control that retries the export.
8. WHEN a TA requests an export of a Rubric holding up to 50 Criterion records each holding up to 10 Performance_Level records, THE Export_Service SHALL return a pre-signed download URL or a failure within 10 seconds.

### Requirement 4: Submission Batch Ingestion

**User Story:** As a TA, I want to drop a folder of downloaded submissions into the application, so that I can grade a whole assignment in one sitting.

#### Acceptance Criteria

1. THE Grading_App SHALL present a submission upload zone that is visually and functionally separate from the rubric upload zone described in Requirement 1.
2. THE Grading_App SHALL accept between 1 and 300 files whose extensions compared without regard to letter case are `.pdf`, `.docx`, `.txt`, `.md`, or `.zip` in one submission upload action in the submission upload zone.
3. WHEN a TA supplies submission files, THE Upload_Service SHALL issue one pre-signed Amazon S3 URL per file, each scoped to a single object key and valid for 15 minutes from the time the URL is issued as described in Requirement 1, so that file bytes travel from the browser to Amazon S3 without passing through the Grading_API.
4. WHEN a `.zip` archive upload completes, THE Submission_Ingestor SHALL expand the archive at every directory depth and create one Submission per contained `.pdf`, `.docx`, `.txt`, or `.md` entry.
5. WHEN a submission file upload completes, THE Text_Extractor SHALL extract plain text together with a zero-based start character offset and a zero-based end character offset for every extracted text run, where each run has a start offset strictly less than its end offset and the runs appear in ascending start offset order without overlapping one another.
6. WHILE submission ingestion is in progress, THE Grading_App SHALL display the count of files ingested, the count of files remaining, and the total count of files in the upload, and SHALL update those counts within 2 seconds of each file completing ingestion.
7. IF the Submission_Ingestor cannot extract text from one submission file, THEN THE Submission_Ingestor SHALL create the Submission, mark the Submission as extraction-failed with a reason drawn from the set of unreadable file, password-protected file, no extractable text, and extraction timeout, and continue processing the remaining files in the batch.
8. IF a submission PDF yields zero extractable characters, THEN THE Submission_Ingestor SHALL mark the Submission as extraction-failed with the reason recorded as no extractable text.
9. WHEN submission ingestion completes, THE Grading_App SHALL display an ingestion report naming every extraction-failed file, every skipped `.zip` entry, and every rejected file together with the reason recorded for each, and the count of Submission records ingested successfully, and SHALL keep the ingestion report displayed until a TA dismisses it.
10. IF a `.zip` archive contains an entry whose extension compared without regard to letter case is not `.pdf`, `.docx`, `.txt`, or `.md`, including an entry with a `.zip` extension, THEN THE Submission_Ingestor SHALL skip the entry without expanding the entry, create no Submission for the entry, and record the skipped entry name in the ingestion report.
11. IF a `.zip` archive expands to an entry path that resolves outside the archive root, THEN THE Submission_Ingestor SHALL reject the archive and record the offending entry path.
12. WHERE every submission file in the Submission_Batch is 52,428,800 bytes or smaller, WHEN a Submission_Batch of 150 submissions averaging 10 pages each is uploaded, THE Submission_Ingestor SHALL complete text extraction for the full batch within 10 minutes of the last file upload completing.
13. IF a single submission file is 0 bytes or exceeds 52,428,800 bytes, THEN THE Grading_App SHALL reject the file before requesting a pre-signed Amazon S3 URL, create no Amazon S3 object, and display the observed file size in bytes together with the permitted range of 1 byte to 52,428,800 bytes.
14. IF a TA supplies a submission file whose extension compared without regard to letter case is not `.pdf`, `.docx`, `.txt`, `.md`, or `.zip`, THEN THE Grading_App SHALL reject the file before requesting a pre-signed Amazon S3 URL, create no Amazon S3 object, and display the list of accepted submission formats.
15. IF the Text_Extractor has not completed extraction of one submission file within 120 seconds of starting that extraction, THEN THE Submission_Ingestor SHALL end extraction for that file, mark the Submission as extraction-failed with the reason recorded as extraction timeout, and continue processing the remaining files in the batch.
16. IF a `.zip` archive holds more than 300 entries or expands to more than 1,073,741,824 uncompressed bytes, THEN THE Submission_Ingestor SHALL reject the archive, create no Submission from the archive, and record in the ingestion report the observed entry count and uncompressed byte count together with the 300 entry and 1,073,741,824 byte limits.

### Requirement 5: Student Identity Association

**User Story:** As a TA, I want each submission tied to the correct student before I start grading, so that the grades I export land on the right rows.

#### Acceptance Criteria

1. WHEN the Submission_Ingestor creates a Submission, THE Roster_Resolver SHALL derive a student display name from the submission filename with leading and trailing whitespace removed and each run of consecutive whitespace collapsed to a single space, and SHALL truncate a derived name longer than 200 characters to its first 200 characters.
2. WHEN the Roster_Resolver processes a submission filename that matches the Canvas submission filename convention of a leading student name segment followed by underscore-separated optional late marker and numeric identifier segments, THE Roster_Resolver SHALL set the student display name from the student name segment, record the Canvas submission identifier, and mark the identity as verified.
3. IF a submission filename does not match the Canvas submission filename convention, THEN THE Roster_Resolver SHALL set the student display name to the filename with the extension removed and mark the identity as unverified.
4. IF two or more Submission records in one Submission_Batch resolve to student display names that are equal under case-insensitive comparison, THEN THE Grading_App SHALL mark every Submission record in that group as requiring TA disambiguation, SHALL retain every Submission record in that group, and SHALL display the source filename of each Submission record in that group.
5. WHEN submission ingestion completes, THE Grading_App SHALL display a confirmation step listing every Submission record created in the Submission_Batch, including extraction-failed Submission records, with the resolved student display name, the source filename, and exactly one identity verification state of verified, unverified, or requiring disambiguation.
6. THE Grading_App SHALL allow a TA to edit the student display name of any Submission in the confirmation step and SHALL mark an identity edited by the TA as verified.
7. IF one or more Submission records carry an unverified identity or a disambiguation flag when a TA requests grading to start, THEN THE Grading_App SHALL block entry to the Marking_View, list every affected Submission by student display name and source filename, and require the TA to activate an acknowledgement control before entering the Marking_View.
8. IF a Submission has extracted text longer than 100,000 characters, THEN THE Grading_App SHALL mark the Submission as oversized, analyze the first 100,000 characters, and display in the Marking_View the oversized state together with the extracted character count and the 100,000 character analysis cap.
9. IF a TA submits a student display name that is empty, that contains only whitespace, or that exceeds 200 characters, THEN THE Grading_App SHALL reject the edit, retain the previous student display name, and display the permitted length range of 1 to 200 characters.
10. WHEN a TA edits a student display name in the confirmation step, THE Grading_App SHALL re-evaluate the duplicate name condition across every Submission in the Submission_Batch and SHALL clear the disambiguation flag from every Submission whose student display name is no longer duplicated.
11. WHEN a TA completes the confirmation step, THE Grading_API SHALL store the confirmed student display name and the identity verification state of every Submission in the Submission_Batch in the Grading_Store.

### Requirement 6: Explainable Rubric-to-Passage Matching

**User Story:** As a TA, I want the application to show me which parts of the submission relate to each criterion and why, so that I can judge the suggestion instead of trusting it blindly.

#### Acceptance Criteria

1. WHEN a Grading_Session starts, THE Match_Engine SHALL produce between 0 and 5 Suggested_Match records for each Criterion in the Rubric against each Submission with successfully extracted text, retaining the 5 records with the highest Match_Confidence when more than 5 candidate records qualify.
2. THE Match_Engine SHALL populate every Suggested_Match with a Criterion identifier, a Passage start character offset, a Passage end character offset, a Match_Rationale of 1 to 300 characters, and a Match_Confidence between 0.00 and 1.00 inclusive expressed with 2 decimal places.
3. THE Match_Engine SHALL restrict every Suggested_Match Passage to a start character offset and an end character offset satisfying 0 <= start offset < end offset <= the analyzed character length of the Submission, with the end offset minus the start offset between 20 and 1,500 inclusive, so that every highlight corresponds to text present in the Submission.
4. THE Match_Engine SHALL discard a candidate Suggested_Match whose Passage range overlaps the Passage range of a retained Suggested_Match of the same Criterion by 50 percent or more of the shorter of the two ranges.
5. WHERE the extracted Submission text exceeds 4,000 characters, THE Match_Engine SHALL analyze the text in 4,000-character chunks with 400 characters of overlap between consecutive chunks and map every returned Passage offset back to the offset space of the whole Submission.
6. IF the Match_Engine produces no Suggested_Match for a Criterion with a Match_Confidence of 0.50 or greater, THEN THE Grading_App SHALL display an explicit "no evidence found" state on that Criterion card, render no highlight for that Criterion in the Document_Viewer, and allow the TA to score that Criterion.
7. IF an Amazon Bedrock analysis call returns a failure or returns no response within 30 seconds, THEN THE Match_Engine SHALL retry the call up to 3 times after delays of 1 second, 2 seconds, and 4 seconds respectively and, after a fourth failed attempt, mark the affected Criterion and Submission pair as analysis-unavailable.
8. IF a Criterion and Submission pair is marked analysis-unavailable, THEN THE Grading_App SHALL display the analysis-unavailable state on the Criterion card and SHALL allow the TA to score that Criterion without Suggested_Match records.
9. IF an Amazon Bedrock analysis response returns a Passage offset outside the Submission character range or an unparseable structure, THEN THE Match_Engine SHALL discard that Suggested_Match and record the discard reason in the Grading_Store.
10. WHEN a Submission of 5,000 words is analyzed against a Rubric of 8 Criterion records, THE Match_Engine SHALL persist every resulting Suggested_Match record and every analysis-unavailable marking in the Grading_Store within 60 seconds of the analysis starting.
11. THE Match_Engine SHALL persist Suggested_Match records in the Grading_Store so that reopening a Submission reuses the stored records produced from the current Rubric instead of issuing a new Amazon Bedrock call.
12. THE Grading_App SHALL allow a TA to hide Suggested_Match records whose Match_Confidence falls below a TA-selected threshold, SHALL retain every hidden Suggested_Match record in the Grading_Store, and SHALL display on each Criterion card the count of Suggested_Match records hidden by that threshold.
13. WHILE a Match_Engine analysis of the current Submission is in progress, THE Grading_App SHALL display an analysis-in-progress state on each Criterion card of that Submission and SHALL allow the TA to select a Performance_Level for every Criterion.
14. WHEN a TA changes the title, the description, or the Performance_Level records of a Criterion for which Suggested_Match records exist, THE Grading_App SHALL mark those Suggested_Match records as stale, display the stale state on that Criterion card, and provide a control that requests a new analysis for that Criterion.

### Requirement 7: Rubric Panel in the Marking View

**User Story:** As a TA, I want the rubric beside the submission as a native panel, so that I can read criteria and the student's work at the same time without moving windows.

#### Acceptance Criteria

1. WHILE the Marking_View is displayed at a viewport width of 1024 pixels or greater, THE Marking_View SHALL render the Rubric_Panel and the Document_Viewer side by side within one page layout, with neither region overlapping the other and without page-level horizontal scrolling.
2. THE Rubric_Panel SHALL render one card per Criterion in the Rubric, in the stored Criterion order, each card showing the Criterion title, the Criterion description, the maximum point value, the Criterion display color, and every selectable Performance_Level with its label and its fixed point value.
3. THE Rubric_Panel SHALL display on each Criterion card the count of Suggested_Match records and the count of Confirmed_Match records for the current Submission as numerals, displaying 0 rather than omitting the count when a count is zero.
4. THE Rubric_Panel SHALL display on each Criterion card either the awarded points and the maximum point value when a Performance_Level is selected for the current Submission, or a text label identifying the Criterion as unscored when no Performance_Level is selected, and SHALL display the count of unscored Criterion records for the current Submission.
5. WHILE a Criterion card holds keyboard focus, THE Rubric_Panel SHALL render a focus indicator on that card with a contrast ratio of at least 3 to 1 against the adjacent background and SHALL keep the focused card fully within the visible bounds of the Rubric_Panel.
6. THE Rubric_Panel SHALL convey each of the unscored state, the scored state, the "no evidence found" state described in Requirement 6, and the analysis-unavailable state described in Requirement 6 through a text label on the Criterion card in addition to color.
7. WHEN the Criterion cards exceed the visible height of the Rubric_Panel, THE Rubric_Panel SHALL scroll vertically within its own region and SHALL leave the Document_Viewer scroll position unchanged.
8. IF the current Submission is marked extraction-failed or oversized as described in Requirement 4 and Requirement 5, THEN THE Rubric_Panel SHALL display a text label naming that state and SHALL keep every Performance_Level selection control on every Criterion card operable.
9. WHEN a TA opens the Marking_View for a Rubric of up to 20 Criterion records, THE Rubric_Panel SHALL complete the initial render of all Criterion cards within 2 seconds.

### Requirement 8: Submission Rendering and Highlighting

**User Story:** As a TA, I want the relevant passages highlighted in the student's text in each criterion's color, so that I can find evidence without reading every paragraph closely.

#### Acceptance Criteria

1. THE Document_Viewer SHALL render the extracted text of the current Submission with paragraph breaks preserved.
2. THE Document_Viewer SHALL highlight every Passage referenced by a Confirmed_Match and every Passage referenced by a Suggested_Match that is not hidden by the Match_Confidence threshold described in Requirement 6, using the display color of the associated Criterion and spanning exactly the Passage start character offset to the Passage end character offset.
3. THE Document_Viewer SHALL render Suggested_Match highlights and Confirmed_Match highlights with visual treatments that differ in at least one attribute other than color, and SHALL label each treatment in text in a legend.
4. WHERE 2 to 4 Passage ranges overlap, THE Document_Viewer SHALL render the overlapping region so that the display color of every associated Criterion remains visible within that region.
5. WHEN a TA hovers a highlighted Passage with a pointer, THE Document_Viewer SHALL display within 300 milliseconds one entry per associated match showing the Criterion title, the Match_Rationale, and the Match_Confidence as a value between 0.00 and 1.00 with 2 decimal places, and SHALL dismiss that display within 300 milliseconds of the pointer leaving the highlighted Passage.
6. WHEN a TA activates a highlighted Passage by pointer or by pressing Enter or Space while that Passage holds keyboard focus, THE Document_Viewer SHALL display one entry per associated match showing the Criterion title, the Match_Rationale, the Match_Confidence, a control that confirms that match, and a control that rejects that match, and SHALL dismiss that display when the TA presses Escape.
7. IF a Submission is marked extraction-failed, THEN THE Document_Viewer SHALL display the extraction failure reason and a pre-signed download URL for the original file valid for 15 minutes from the time the URL is issued, and SHALL render no extracted text and no highlights.
8. WHILE the Marking_View is displayed at a viewport width of 1024 pixels or greater, WHEN a TA opens a Submission of 10,000 words carrying 40 highlights, THE Document_Viewer SHALL complete the render of the first viewport of text and of every highlight within that viewport within 2 seconds of the open action.
9. WHERE 5 or more Passage ranges overlap, THE Document_Viewer SHALL render the overlapping region with one shared visual treatment together with a text label stating the count of Criterion records associated with that region.
10. THE Document_Viewer SHALL make every rendered highlight reachable by keyboard focus in ascending order of Passage start character offset and SHALL render a focus indicator on the focused highlight with a contrast ratio of at least 3 to 1 against the adjacent background.
11. THE Document_Viewer SHALL render Submission text over every highlight treatment with a contrast ratio of at least 4.5 to 1 between the text and that highlight treatment.

### Requirement 9: Navigation Between Criteria and Passages

**User Story:** As a TA, I want to jump from a criterion to its evidence and back, so that I can score one criterion at a time across a long document.

#### Acceptance Criteria

1. WHEN a TA activates a Criterion card by pointer or by keyboard, THE Document_Viewer SHALL scroll the associated Passage with the lowest start character offset fully into the visible region of the Document_Viewer, aligning the start character offset of that Passage with the top of the visible region when that Passage is taller than the visible region, and SHALL render every Passage associated with that Criterion with a treatment visually distinct from the treatment of Passage records of unselected Criterion records together with a text label naming the selected Criterion.
2. WHILE a Criterion is selected, THE Marking_View SHALL provide a control that advances to the next associated Passage in ascending start character offset order and a control that returns to the previous associated Passage, SHALL display the ordinal position of the current associated Passage and the total count of associated Passage records, and SHALL leave the current associated Passage unchanged when a TA activates the advance control on the last associated Passage or the return control on the first associated Passage.
3. WHEN a TA activates by pointer or by keyboard a highlighted Passage associated with exactly one Criterion, THE Rubric_Panel SHALL scroll the associated Criterion card fully into the visible region of the Rubric_Panel and SHALL render that card with a treatment visually distinct from the treatment of unselected Criterion cards together with a text label naming the selected Criterion.
4. WHEN a TA selects a different Criterion, THE Marking_View SHALL remove the selected-Criterion treatment and the selected-Criterion text label from the previously selected Criterion card and from every Passage associated with the previously selected Criterion.
5. WHEN a TA activates a Criterion card that has zero associated Passage records, THE Marking_View SHALL display the "no evidence found" state described in Requirement 6, leave the Document_Viewer scroll position unchanged, and render the next-Passage control and the previous-Passage control as disabled with a text label stating that the Criterion has zero associated passages.
6. WHEN a TA activates a Criterion card marked analysis-unavailable as described in Requirement 6, THE Marking_View SHALL display the analysis-unavailable state, leave the Document_Viewer scroll position unchanged, and render the next-Passage control and the previous-Passage control as disabled with a text label stating that no analysis is available for that Criterion.
7. IF a TA activates a highlighted Passage associated with two or more Criterion records, THEN THE Marking_View SHALL display the title of every Criterion associated with that Passage for the TA to choose from and SHALL leave the Rubric_Panel scroll position unchanged until the TA chooses one Criterion.
8. WHEN a TA activates a Criterion card of a Submission of 10,000 words carrying 40 highlights, THE Document_Viewer SHALL complete the scroll and the treatment change within 200 milliseconds of the activation.

### Requirement 10: Match Confirmation and Manual Association

**User Story:** As a TA, I want to confirm or reject suggested matches and add my own, so that the evidence record reflects my judgement.

#### Acceptance Criteria

1. WHEN a TA confirms a Suggested_Match, THE Grading_App SHALL create a Confirmed_Match carrying the Criterion identifier, the Passage start character offset, the Passage end character offset, the Match_Rationale, and the Match_Confidence of that Suggested_Match together with an origin recorded as TA-confirmed, and SHALL update the Document_Viewer highlight treatment of that Passage within 200 milliseconds.
2. WHEN a TA rejects a Suggested_Match, THE Grading_App SHALL remove the associated highlight from the Document_Viewer, record the match state as TA-rejected together with the Criterion identifier and the Passage start and end character offsets in the Grading_Record, and present no Suggested_Match for that Criterion and Passage range when the TA reopens the Submission.
3. WHEN a TA selects a text range in the Document_Viewer of 1 to 5,000 characters holding at least one non-whitespace character and assigns a Criterion, THE Grading_App SHALL create a Confirmed_Match whose Passage start and end character offsets are expressed in the offset space of the extracted Submission text, whose Match_Rationale is recorded as TA-authored, and whose Match_Confidence is recorded as not applicable.
4. THE Marking_View SHALL allow a TA to remove a Confirmed_Match, SHALL record a removed Confirmed_Match derived from a Suggested_Match as TA-rejected, and SHALL delete from the Grading_Record a removed Confirmed_Match whose Match_Rationale is recorded as TA-authored.
5. THE Grading_App SHALL allow one Passage to be associated with two or more Criterion records, SHALL hold at most one Confirmed_Match per Criterion per Passage, and SHALL keep the match state of a Passage for one Criterion independent of the match state of that Passage for every other Criterion.
6. WHEN a TA changes any match state, THE Grading_App SHALL mark the Grading_Record of the current Submission as holding Unsaved_Changes.
7. THE Grading_App SHALL set the match state of one Suggested_Match after a sequence of confirm and reject actions on that Suggested_Match to the state produced by the last action in that sequence.
8. IF a TA assigns a Criterion to a selected text range that is empty, that holds no non-whitespace character, or that exceeds 5,000 characters, THEN THE Grading_App SHALL reject the assignment, create no Confirmed_Match, and display the permitted selection range of 1 to 5,000 characters.
9. IF a TA assigns a Criterion to a selected text range whose start and end character offsets equal those of an existing Confirmed_Match of that same Criterion, THEN THE Grading_App SHALL reject the assignment, retain the existing Confirmed_Match, and display that the passage is already associated with that Criterion.
10. WHILE the current Submission is marked extraction-failed, THE Marking_View SHALL render the match confirmation control, the match rejection control, and the text selection association control as disabled with a text label stating that no extracted text is available for association.

### Requirement 11: Scoring and Total Calculation

**User Story:** As a TA, I want the total score computed as I select performance levels, so that I do not add points by hand for every student.

#### Acceptance Criteria

1. THE Rubric_Panel SHALL allow a TA to select exactly one Performance_Level per Criterion for the current Submission.
2. WHEN a TA selects a Performance_Level, THE Score_Calculator SHALL set the awarded points for that Criterion to the fixed point value of the selected Performance_Level.
3. THE Score_Calculator SHALL display a total score equal to the sum of the awarded points across every Criterion record and a maximum score equal to the sum of the maximum point values across every Criterion record, each rounded to 2 decimal places.
4. THE Rubric_Panel SHALL allow a TA to override the awarded points of a Criterion with a manually entered value between 0 and the maximum point value of that Criterion inclusive, expressed with at most 2 decimal places.
5. IF a TA enters an override value that is non-numeric, below 0, above the maximum point value of the Criterion, or expressed with more than 2 decimal places, THEN THE Rubric_Panel SHALL reject the entry, retain the awarded points held for that Criterion before the entry, and display the permitted range of 0 to the maximum point value of that Criterion with at most 2 decimal places.
6. WHEN a TA changes a Performance_Level selection after entering an override for the same Criterion, THE Score_Calculator SHALL replace the override with the fixed point value of the newly selected Performance_Level and SHALL display a text label on that Criterion card stating that the override was replaced.
7. WHEN a TA changes any Performance_Level selection or override, THE Score_Calculator SHALL update the displayed total score within 100 milliseconds.
8. THE Score_Calculator SHALL derive every awarded point value from a TA-selected Performance_Level or a TA-entered override.
9. THE Score_Calculator SHALL set the displayed total score of a Grading_Record to the sum of the awarded points of the individual Criterion records of that Grading_Record.
10. WHILE one or more Criterion records of the current Submission have neither a selected Performance_Level nor an override, THE Score_Calculator SHALL display the count of those Criterion records beside the total score and SHALL exclude those Criterion records from the total score.
11. WHEN a TA removes an override from a Criterion that has no selected Performance_Level, THE Score_Calculator SHALL mark that Criterion as unscored and SHALL reduce the displayed total score by the removed override value.

### Requirement 12: Feedback Comments and AI Comment Suggestions

**User Story:** As a TA, I want help drafting feedback that matches the levels I selected, so that each student receives specific comments without me writing every sentence from scratch.

#### Acceptance Criteria

1. THE Marking_View SHALL allow a TA to enter free-text feedback of 0 to 10,000 characters for the current Submission.
2. THE Marking_View SHALL allow a TA to enter free-text feedback of 0 to 2,000 characters for an individual Criterion.
3. WHEN a TA requests comment suggestions, THE Comment_Assistant SHALL return between 1 and 5 candidate feedback snippets, each of 1 to 1,000 characters, derived from the selected Performance_Level records and the Confirmed_Match records of the current Submission.
4. WHEN a TA selects a candidate feedback snippet, THE Grading_App SHALL insert the snippet text into the feedback field at the cursor position and SHALL leave the inserted text editable.
5. THE Grading_App SHALL label every inserted candidate feedback snippet as AI-generated until a TA edits or accepts the containing feedback field.
6. IF the Comment_Assistant returns a failure or returns no response within 15 seconds, THEN THE Grading_App SHALL display the failure reason, provide a retry control, and retain every character of feedback text already entered by the TA.
7. WHEN a TA requests comment suggestions for a Submission graded against a Rubric of up to 20 Criterion records, THE Comment_Assistant SHALL return candidate feedback snippets or a failure within 15 seconds.
8. IF a TA requests comment suggestions while zero Performance_Level records are selected for the current Submission, THEN THE Grading_App SHALL block the request and display that at least one Performance_Level selection is required to request comment suggestions.
9. IF a TA enters feedback text longer than the permitted length of the feedback field, THEN THE Marking_View SHALL reject the characters beyond that length and display the entered character count together with the permitted maximum.
10. WHEN a TA changes any feedback text, THE Grading_App SHALL mark the Grading_Record of the current Submission as holding Unsaved_Changes.

### Requirement 13: Batch Progress and Navigation

**User Story:** As a TA, I want to see where I am in the batch and move between students, so that I can pace a long grading session and revisit earlier work.

#### Acceptance Criteria

1. THE Marking_View SHALL display the ordinal position of the current Submission and the total count of Submission records in the Submission_Batch.
2. THE Marking_View SHALL display the count of Submission records in the Submission_Batch that hold a selected Performance_Level or an override for every Criterion and the count of Submission records that do not.
3. THE Marking_View SHALL provide a control that saves the current Grading_Record and advances to the next Submission, and a control that saves the current Grading_Record and returns to the previous Submission, in the Submission_Batch order.
4. IF a TA navigates away from a Submission that holds Unsaved_Changes without saving, THEN THE Grading_App SHALL display the count of Grading_Record fields holding Unsaved_Changes and SHALL require the TA to activate a confirmation control before discarding those changes.
5. IF a TA advances from a Submission on which one or more Criterion records have no selected Performance_Level, THEN THE Grading_App SHALL display the count of unscored Criterion records and SHALL require the TA to activate a confirmation control before advancing.
6. THE Marking_View SHALL provide a list from which a TA selects any Submission in the Submission_Batch by student display name and by ordinal position in the Submission_Batch.
7. WHEN a TA navigates to an already-graded Submission, THE Marking_View SHALL restore the stored Performance_Level selections, overrides, feedback text, and Confirmed_Match records for that Submission.
8. WHEN a TA navigates to another Submission in a Submission_Batch of 150 Submission records, THE Marking_View SHALL complete the render of the arriving Submission within 2 seconds of the navigation action.
9. IF a TA activates the advance control on the last Submission or the return control on the first Submission of the Submission_Batch, THEN THE Marking_View SHALL save the current Grading_Record, keep the current Submission displayed, and display a text label stating that the end or the start of the Submission_Batch is reached.
10. IF the Grading_API cannot load the arriving Submission when a TA navigates, THEN THE Grading_App SHALL keep the current Submission displayed, retain every Unsaved_Changes edit, display the failure reason, and provide a retry control.

### Requirement 14: Explicit Save and Session Resumption

**User Story:** As a TA, I want a clear save action and an accurate saved indicator, so that I know exactly what has been recorded and what has not.

#### Acceptance Criteria

1. THE Marking_View SHALL provide a save control that sends the Grading_Record of the current Submission to the Grading_API.
2. WHEN a TA activates the save control, THE Grading_API SHALL store the selected Performance_Level records, point overrides, feedback text, and Confirmed_Match records of that Submission in the Grading_Store.
3. WHEN a save of a Grading_Record of 20 Criterion records completes, THE Grading_API SHALL acknowledge the save within 2 seconds.
4. WHILE a Grading_Record holds Unsaved_Changes, THE Marking_View SHALL display an unsaved indicator naming the current Submission.
5. WHEN a save completes, THE Marking_View SHALL display a saved indicator together with the time of that save expressed to the second in the local time zone of the Grading_App.
6. IF a save request fails, THEN THE Grading_App SHALL retain the Grading_Record edits in the open Marking_View, display the failure reason, and provide a retry control.
7. IF a TA closes or reloads the browser tab while a Grading_Record holds Unsaved_Changes, THEN THE Grading_App SHALL display a browser confirmation prompt before the tab closes or reloads.
8. WHEN a TA opens a Grading_Session that was previously saved, THE Grading_App SHALL restore the Rubric, the Submission_Batch, and every Grading_Record stored by a completed save.
9. THE Grading_App SHALL list the Grading_Session records owned by the requesting TA so that a TA resumes any previously saved Grading_Session.
10. THE Grading_App SHALL yield, when a TA reopens a Grading_Session, a Grading_Record equal field by field to the Grading_Record stored by the most recent completed save of that Grading_Record.
11. WHEN a TA activates the save control while the Grading_Record of the current Submission holds zero Unsaved_Changes, THE Grading_App SHALL leave the Grading_Store unchanged and SHALL display the saved indicator with the time of the most recent completed save.
12. IF a save request returns no response within 30 seconds, THEN THE Grading_App SHALL end the request, retain the Grading_Record edits in the open Marking_View, display a save-timeout reason, and provide a retry control.

### Requirement 15: Pre-Export Review

**User Story:** As a TA, I want to review every grade in one place before anything is exported, so that I catch mistakes before the grades reach students.

#### Acceptance Criteria

1. THE Grading_App SHALL require a TA to open the Review_Screen before the Export_Service produces a grade export file.
2. THE Review_Screen SHALL display one row per Submission showing the student display name, the awarded points per Criterion, the total score, and the maximum score.
3. THE Review_Screen SHALL flag with a text label each Submission that has one or more Criterion records with no selected Performance_Level and SHALL display the count of those Criterion records on that Submission row.
4. THE Review_Screen SHALL flag with a text label naming the state each Submission that is marked extraction-failed, oversized, identity-unverified, or requiring disambiguation.
5. THE Review_Screen SHALL flag with a text label each Criterion of each Submission that carries a manual point override.
6. THE Review_Screen SHALL display the count of flagged Submission records and the count of unflagged Submission records as numerals, displaying 0 rather than omitting a count when a count is zero.
7. WHEN a TA selects a Submission row on the Review_Screen, THE Grading_App SHALL open the Marking_View for that Submission.
8. IF one or more Submission records are flagged when a TA requests an export, THEN THE Grading_App SHALL display the flag counts and SHALL require explicit TA confirmation before the Export_Service produces the file.
9. WHEN a TA confirms the Review_Screen, THE Grading_API SHALL record the confirmation against the Grading_Session together with the time of the confirmation expressed to the second.
10. WHEN a TA opens the Review_Screen for a Grading_Session of 150 Submission records, THE Review_Screen SHALL complete the initial render within 3 seconds of the open action.
11. WHEN a TA changes any Grading_Record after confirming the Review_Screen, THE Grading_API SHALL clear the recorded confirmation of that Grading_Session so that the TA opens and confirms the Review_Screen again before the Export_Service produces a grade export file.
12. IF a Grading_Session holds zero Submission records when a TA opens the Review_Screen, THEN THE Review_Screen SHALL display that zero Submission records are available for review and SHALL block the export request.

### Requirement 16: Grade Export

**User Story:** As a TA, I want to export the finished grades, so that I can upload them to the gradebook and hand the feedback to students.

#### Acceptance Criteria

1. WHEN a TA requests a generic export, THE Export_Service SHALL produce a CSV file containing one row per Submission with the student display name, the awarded points per Criterion, the selected Performance_Level label per Criterion, the total score, the maximum score, and the feedback text.
2. WHEN a TA requests a Canvas export, THE Export_Service SHALL produce a CSV file whose header row and column order match the Canvas gradebook import format and whose assignment column contains the total score per Submission.
3. THE Export_Service SHALL write into every export file the awarded points held in the Grading_Store for each Submission without recalculating or adjusting those values.
4. WHEN the Export_Service produces an export file, THE Export_Service SHALL store the file under a single Amazon S3 object key and return a pre-signed download URL for that object key valid for 15 minutes from the time the URL is issued.
5. WHERE a Submission has an incomplete Grading_Record and the TA has confirmed the export as described in Requirement 15, THE Export_Service SHALL emit an empty score value for that Submission rather than a zero score.
6. IF a field value in an export file contains a comma, a double quote, a line break, or a leading or trailing whitespace character, THEN THE Export_Service SHALL emit the value with RFC 4180 quoting so that a parser recovers the original value character for character.
7. THE Export_Service SHALL produce a generic export file that, parsed as an RFC 4180 CSV file, yields student display names, per-Criterion awarded points, selected Performance_Level labels, total scores, and feedback text equal to those held in the Grading_Store for that Grading_Session, with text values equal character for character and numeric values equal exactly.
8. WHEN a TA requests an export for a Submission_Batch of 150 Submission records, THE Export_Service SHALL return a pre-signed download URL or a failure within 30 seconds.
9. IF writing an export file to Amazon S3 fails or issuing the pre-signed download URL fails, THEN THE Grading_App SHALL leave the Grading_Store unchanged, display the failure reason, and provide a control that retries the export.
10. IF a Grading_Session holds zero Submission records when a TA requests an export, THEN THE Grading_App SHALL block the export, create no Amazon S3 object, and display that at least one Submission is required to export.
11. THE Export_Service SHALL encode every export file as UTF-8 and terminate every record of every export file with a carriage return and line feed pair as specified by RFC 4180.

### Requirement 17: Keyboard Operation

**User Story:** As a TA grading dozens of submissions in one sitting, I want to work entirely from the keyboard, so that I am not slowed down by moving a pointer between panels.

#### Acceptance Criteria

1. THE Grading_App SHALL allow a TA to reach and activate every control in the Marking_View using keyboard input alone.
2. THE Marking_View SHALL provide a keyboard shortcut that saves and advances to the next Submission and a keyboard shortcut that saves and returns to the previous Submission.
3. THE Marking_View SHALL provide a keyboard shortcut that saves the current Grading_Record without changing the current Submission.
4. THE Marking_View SHALL provide a keyboard shortcut that moves focus to the next Criterion card and a keyboard shortcut that moves focus to the previous Criterion card.
5. WHILE a Criterion card holding between 1 and 9 Performance_Level records holds focus, THE Rubric_Panel SHALL allow a TA to select a Performance_Level by pressing the number key from 1 to 9 corresponding to the ordinal position of that Performance_Level.
6. THE Marking_View SHALL provide a keyboard shortcut that moves focus to the next highlighted Passage of the focused Criterion and a keyboard shortcut that moves focus to the previous highlighted Passage of the focused Criterion.
7. WHILE a highlighted Passage holds focus, THE Marking_View SHALL provide a keyboard shortcut that confirms the associated match and a keyboard shortcut that rejects the associated match.
8. THE Marking_View SHALL provide a keyboard shortcut that moves focus to the feedback field for the current Submission.
9. THE Grading_App SHALL display a keyboard shortcut reference that a TA opens with a keyboard shortcut.
10. WHILE a text input holds focus, THE Grading_App SHALL pass character keys to the text input rather than interpreting the character keys as Marking_View shortcuts.
11. WHEN a TA moves focus with keyboard input, THE Grading_App SHALL render a focus indicator on the focused element with a contrast ratio of at least 3 to 1 against the adjacent background.
12. WHILE a Criterion card holding 10 Performance_Level records holds focus, THE Rubric_Panel SHALL allow a TA to select the tenth Performance_Level by pressing the 0 key.
13. THE Grading_App SHALL assign at most one Marking_View action to each keyboard shortcut so that no two Marking_View actions share the same key combination.
14. IF a TA presses a key combination that is assigned to no Marking_View action, THEN THE Grading_App SHALL leave the Grading_Record of the current Submission unchanged and SHALL leave the focused element unchanged.

### Requirement 18: Authentication and Access Control

**User Story:** As a TA, I want my grading data reachable only by me, so that student scores and comments stay confidential.

#### Acceptance Criteria

1. THE Auth_Service SHALL authenticate a TA against an Amazon Cognito user pool before the Grading_App displays any Grading_Session data.
2. THE Auth_Service SHALL restrict Amazon Cognito account creation to an administrator so that a visitor cannot create an account without administrator action.
3. WHEN the Grading_App calls the Grading_API, THE Grading_App SHALL present the Amazon Cognito access token of the authenticated TA.
4. IF a Grading_API request presents an absent, expired, or invalid access token, THEN THE Grading_API SHALL reject the request with HTTP status 401 and SHALL omit Grading_Session data from the response.
5. IF an authenticated TA requests a Grading_Session, Rubric, Submission, or Grading_Record that the requesting TA does not own, THEN THE Grading_API SHALL reject the request with HTTP status 404.
6. THE Upload_Service SHALL issue a pre-signed Amazon S3 URL only for an object key within the Amazon S3 prefix owned by the requesting TA.
7. THE Grading_API SHALL restrict the ECS task role to the Amazon S3 prefixes, Amazon Bedrock model invocations, and Grading_Store resources that the Grading_API operations in this document require.
8. WHEN a TA signs out, THE Grading_App SHALL discard the access token and every Grading_Record held in the open Grading_App within 1 second of the sign-out action.
9. THE Grading_App SHALL communicate with the Grading_API over HTTPS.
10. IF an Amazon Cognito access token expires while a TA holds an open Marking_View, THEN THE Grading_App SHALL retain every Unsaved_Changes edit in the open Marking_View, display a re-authentication prompt, and resume the interrupted Grading_API request after the TA re-authenticates.
11. THE Grading_API SHALL omit the access token, every student display name, and every feedback text value from every log record the Grading_API writes.

### Requirement 19: Scale, Storage Lifecycle, and Resilience

**User Story:** As a TA, I want the application to hold up under a full class batch, so that grading 150 submissions works the same way as grading 5.

#### Acceptance Criteria

1. THE Grading_API SHALL support a Submission_Batch containing between 1 and 150 Submission records.
2. IF a TA uploads submission files that would raise a Submission_Batch above 150 Submission records, THEN THE Grading_App SHALL reject the excess files and display the 150 submission limit.
3. THE Grading_API SHALL store uploaded rubric files, uploaded submission files, and generated export files in Amazon S3 under key prefixes that identify the owning TA and the Grading_Session.
4. THE Grading_API SHALL apply an Amazon S3 lifecycle configuration that deletes generated export files 30 days after creation.
5. THE Grading_API SHALL apply an Amazon S3 lifecycle configuration that deletes uploaded rubric files and uploaded submission files 180 days after the last modification of the owning Grading_Session.
6. WHEN a TA deletes a Grading_Session, THE Grading_API SHALL delete the associated Grading_Record data from the Grading_Store and the associated objects from Amazon S3 within 24 hours.
7. IF an ECS task processing submission ingestion terminates before completion, THEN THE Submission_Ingestor SHALL resume ingestion of the unprocessed submission files within 5 minutes of that termination and SHALL create no second Submission record for a submission file already ingested.
8. WHILE a long-running ingestion or analysis operation is in progress, THE Grading_API SHALL expose the operation status so that the Grading_App reports progress without holding an open request for longer than 30 seconds.
9. WHEN 10 TA users each grade a distinct Submission_Batch of 150 submissions concurrently, THE Grading_API SHALL serve Marking_View data requests within 2 seconds at the 95th percentile.
10. IF the Submission_Ingestor cannot resume a terminated ingestion operation after 3 resume attempts, THEN THE Grading_API SHALL mark the Submission_Batch ingestion as failed, retain every Submission record already created, and expose the failure reason to the Grading_App.
11. THE Grading_API SHALL retain every Grading_Record of a Grading_Session in the Grading_Store until a TA deletes that Grading_Session, independent of the Amazon S3 lifecycle deletions described in criteria 4 and 5.
