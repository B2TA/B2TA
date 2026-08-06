/**
 * Mock API layer for demo mode.
 *
 * When VITE_DEMO_MODE=true (or no backend URL is configured), all fetch calls are intercepted
 * and served from realistic in-memory data. This lets the full SPA run without a backend.
 */

export const DEMO_MODE =
  import.meta.env.VITE_DEMO_MODE === "true" ||
  (!import.meta.env.VITE_API_BASE_URL && !import.meta.env.VITE_COGNITO_USER_POOL_ID);

// ── Fake IDs ─────────────────────────────────────────────────────────────────

const SESSION_ID = "sess-demo-001";
const RUBRIC_ID = "rubric-demo-001";
const SUB1_ID = "sub-demo-001";
const SUB2_ID = "sub-demo-002";
const SUB3_ID = "sub-demo-003";
const JOB_ID = "job-demo-001";

// ── Fake user ────────────────────────────────────────────────────────────────

const FAKE_ME = { taId: "ta-demo-001", email: "demo@b2ta.dev" };

// ── Rubric criteria (matches prototype CRITERIA array) ───────────────────────

const CRITERIA = [
  {
    id: "crit-thesis",
    title: "Thesis Clarity",
    description: "Central argument is clearly stated, arguable, and appears early in the essay.",
    maxPoints: 5,
    displayColor: "#D97706",
    position: 0,
    requiresCompletion: true,
    performanceLevels: [
      { id: "lv-thesis-5", label: "Exemplary", description: "Thesis is precise, arguable, and elegantly positioned.", points: 5, position: 0 },
      { id: "lv-thesis-4", label: "Proficient", description: "Thesis is clear and arguable with minor ambiguity.", points: 4, position: 1 },
      { id: "lv-thesis-3", label: "Developing", description: "Thesis present but broad or partially unclear.", points: 3, position: 2 },
      { id: "lv-thesis-2", label: "Beginning", description: "Thesis implied but not directly stated.", points: 2, position: 3 },
      { id: "lv-thesis-1", label: "Insufficient", description: "No identifiable thesis.", points: 1, position: 4 },
    ],
  },
  {
    id: "crit-evidence",
    title: "Use of Evidence",
    description: "Integrates at least 3 cited sources; quotations and paraphrases directly support claims.",
    maxPoints: 5,
    displayColor: "#0D9488",
    position: 1,
    requiresCompletion: true,
    performanceLevels: [
      { id: "lv-evidence-5", label: "Exemplary", description: "Evidence is varied, well-integrated, and thoroughly analyzed.", points: 5, position: 0 },
      { id: "lv-evidence-4", label: "Proficient", description: "Evidence supports claims; minor integration issues.", points: 4, position: 1 },
      { id: "lv-evidence-3", label: "Developing", description: "Evidence present but thin or under-analyzed.", points: 3, position: 2 },
      { id: "lv-evidence-2", label: "Beginning", description: "Minimal sources; evidence often dropped in without context.", points: 2, position: 3 },
      { id: "lv-evidence-1", label: "Insufficient", description: "Little to no evidence cited.", points: 1, position: 4 },
    ],
  },
  {
    id: "crit-organization",
    title: "Organization",
    description: "Logical paragraph structure with clear transitions linking ideas across sections.",
    maxPoints: 5,
    displayColor: "#7C3AED",
    position: 2,
    requiresCompletion: true,
    performanceLevels: [
      { id: "lv-org-5", label: "Exemplary", description: "Seamless flow; every transition serves the argument.", points: 5, position: 0 },
      { id: "lv-org-4", label: "Proficient", description: "Well-organized; occasional abrupt transitions.", points: 4, position: 1 },
      { id: "lv-org-3", label: "Developing", description: "Basic structure present but transitions weak.", points: 3, position: 2 },
      { id: "lv-org-2", label: "Beginning", description: "Sections feel disjointed; logic hard to follow.", points: 2, position: 3 },
      { id: "lv-org-1", label: "Insufficient", description: "No discernible organizational logic.", points: 1, position: 4 },
    ],
  },
  {
    id: "crit-grammar",
    title: "Grammar & Mechanics",
    description: "Minimal errors in grammar, punctuation, and sentence structure throughout.",
    maxPoints: 3,
    displayColor: "#DC2626",
    position: 3,
    requiresCompletion: true,
    performanceLevels: [
      { id: "lv-gram-3", label: "Proficient", description: "Virtually error-free; prose is polished.", points: 3, position: 0 },
      { id: "lv-gram-2", label: "Developing", description: "A few noticeable errors that don't impede reading.", points: 2, position: 1 },
      { id: "lv-gram-1", label: "Beginning", description: "Frequent errors that impede comprehension.", points: 1, position: 2 },
    ],
  },
  {
    id: "crit-citations",
    title: "Citation Format",
    description: "All in-text citations and bibliography entries follow MLA or APA format consistently.",
    maxPoints: 2,
    displayColor: "#0374B5",
    position: 4,
    requiresCompletion: true,
    performanceLevels: [
      { id: "lv-cite-2", label: "Proficient", description: "Consistent, correct citation format throughout.", points: 2, position: 0 },
      { id: "lv-cite-1", label: "Developing", description: "Minor citation errors or inconsistencies.", points: 1, position: 1 },
      { id: "lv-cite-0", label: "Insufficient", description: "Missing citations or wrong format.", points: 0, position: 2 },
    ],
  },
];

// ── Essay text (from prototype ESSAY_PARAGRAPHS) ─────────────────────────────

const ESSAY_TEXT = [
  "The question of whether social media platforms bear moral responsibility for political polarization has moved from academic seminars into courtrooms and congressional hearings. I will argue that platforms are not neutral conduits but active architects of epistemic bubbles, and that this architectural complicity carries genuine moral weight. This essay examines the amplification algorithm, the advertising incentive structure, and the suppression of cross-cutting exposure to substantiate that claim.",
  "To understand how platforms shape belief, one must first acknowledge the design intent embedded in recommendation systems. According to Pariser (2011), the \"filter bubble\" emerges not from user choice alone but from algorithmic curation that optimizes for engagement over exposure diversity. Studies show that users who receive algorithmically curated feeds are 40% more likely to share partisan content than users on reverse-chronological feeds (Bail et al., 2018, p. 9024). This evidence suggests that the architecture, not merely the audience, is causally implicated.",
  "Critics of this view argue that users bear primary responsibility for their own information diet. However, this critique underestimates the asymmetry of expertise between platform engineers and ordinary users. Furthermore, internal documents released in the 2021 Facebook Papers reveal that company researchers were aware polarization metrics rose with engagement optimization, yet the feature remained live. The limitation of the \"user choice\" framework is that it treats an unequal relationship as symmetrical.",
  "The advertising model compounds the problem. Platforms generate revenue proportional to time-on-site, and outrage reliably extends sessions. Therefore, any financial incentive for platforms runs counter to the depolarization interventions researchers propose. This structural conflict of interest distinguishes platform complicity from the more passive negligence of, say, a telephone company whose infrastructure happens to carry inflammatory speech.",
  "In conclusion, the architecture of engagement-maximizing platforms is not value-neutral. First, it systematically narrows the epistemic range of users. Finally, it sustains itself through financial incentives that are structurally opposed to reform. Assigning moral responsibility to platforms is therefore not a category error but an accurate description of causal agency. The path forward requires treating platform design as a site of democratic accountability, not merely a market matter.",
].join("\n\n");

const ESSAY_TEXT_2 = "Climate change adaptation strategies for coastal cities represent one of the most pressing policy challenges of the twenty-first century. This essay argues that reactive, disaster-driven adaptation is both more expensive and less effective than proactive managed retreat combined with green infrastructure investment.\n\nRising sea levels threaten approximately 800 million people worldwide (Hallegatte et al., 2013). Traditional seawall construction provides short-term protection but creates a false sense of security that encourages further development in vulnerable zones. The Dutch Room for the River program demonstrates that controlled flooding zones reduce overall damage costs by 60% compared to levee-only approaches (Rijkswaterstaat, 2019).\n\nOpponents argue that managed retreat displaces communities and destroys property values. While these concerns are valid, the alternative—repeated rebuilding after each storm—imposes greater cumulative costs and psychological trauma on residents. New Orleans post-Katrina studies show that neighborhoods rebuilt in flood zones experienced three subsequent displacement events within fifteen years (Gotham & Campanella, 2020).\n\nIn conclusion, proactive adaptation through managed retreat and green infrastructure is the fiscally responsible and ethically sound path forward for coastal cities facing sea level rise.";

const ESSAY_TEXT_3 = "The ethics of artificial intelligence in criminal sentencing has become a contentious legal and philosophical debate. This essay contends that algorithmic risk assessment tools, while promising efficiency, embed historical biases that violate the principle of equal protection under law.\n\nCOMPAS and similar tools have been deployed in courtrooms across the United States since 2012. ProPublica's 2016 investigation revealed that Black defendants were nearly twice as likely as white defendants to be incorrectly flagged as high risk for recidivism (Angwin et al., 2016). The tool's designers argue these disparities reflect base-rate differences, not algorithmic bias.\n\nHowever, this defense confuses prediction with fairness. If an algorithm learned from data shaped by discriminatory policing practices, its predictions perpetuate those patterns. Harcourt (2007) calls this \"actuarial injustice\"—using group statistics to determine individual fates.\n\nIn conclusion, criminal sentencing algorithms must be held to higher standards than predictive accuracy alone. Unless bias auditing becomes mandatory and transparent, these tools risk automating the very inequities the justice system claims to oppose.";

// ── Session ──────────────────────────────────────────────────────────────────

const FAKE_SESSION = {
  id: SESSION_ID,
  name: "Essay 3: Argumentative Analysis",
  reviewConfirmedAt: null,
  submissionCount: 3,
  createdAt: "2025-01-10T09:00:00Z",
  updatedAt: "2025-01-12T14:30:00Z",
};

// ── Rubric ───────────────────────────────────────────────────────────────────

const FAKE_RUBRIC = {
  id: RUBRIC_ID,
  sessionId: SESSION_ID,
  sourceFormat: "manual" as const,
  createdAt: "2025-01-10T09:05:00Z",
  updatedAt: "2025-01-10T09:05:00Z",
  criteria: CRITERIA,
};

// ── Submissions ──────────────────────────────────────────────────────────────

const FAKE_SUBMISSIONS = [
  {
    id: SUB1_ID,
    originalFilename: "martinez_essay3.pdf",
    studentDisplayName: "Sofia Martinez",
    canvasSubmissionId: null,
    identityStatus: "verified" as const,
    extractionStatus: "success" as const,
    extractionFailureReason: null,
    extractedCharCount: ESSAY_TEXT.length,
    isOversized: false,
    position: 1,
    createdAt: "2025-01-11T10:00:00Z",
  },
  {
    id: SUB2_ID,
    originalFilename: "chen_essay3.pdf",
    studentDisplayName: "Wei Chen",
    canvasSubmissionId: null,
    identityStatus: "verified" as const,
    extractionStatus: "success" as const,
    extractionFailureReason: null,
    extractedCharCount: ESSAY_TEXT_2.length,
    isOversized: false,
    position: 2,
    createdAt: "2025-01-11T10:01:00Z",
  },
  {
    id: SUB3_ID,
    originalFilename: "johnson_essay3.pdf",
    studentDisplayName: "Marcus Johnson",
    canvasSubmissionId: null,
    identityStatus: "verified" as const,
    extractionStatus: "success" as const,
    extractionFailureReason: null,
    extractedCharCount: ESSAY_TEXT_3.length,
    isOversized: false,
    position: 3,
    createdAt: "2025-01-11T10:02:00Z",
  },
];

// ── Suggested matches for submission 1 ───────────────────────────────────────

const thesisStart = ESSAY_TEXT.indexOf("I will argue that platforms");
const thesisEnd = thesisStart + "I will argue that platforms are not neutral conduits but active architects of epistemic bubbles, and that this architectural complicity carries genuine moral weight.".length;

const evidenceStart = ESSAY_TEXT.indexOf("According to Pariser (2011)");
const evidenceEnd = evidenceStart + "According to Pariser (2011), the \"filter bubble\" emerges not from user choice alone but from algorithmic curation that optimizes for engagement over exposure diversity.".length;

const evidence2Start = ESSAY_TEXT.indexOf("Studies show that users");
const evidence2End = evidence2Start + "Studies show that users who receive algorithmically curated feeds are 40% more likely to share partisan content than users on reverse-chronological feeds (Bail et al., 2018, p. 9024).".length;

const orgStart = ESSAY_TEXT.indexOf("Therefore, any financial incentive");
const orgEnd = orgStart + "Therefore, any financial incentive for platforms runs counter to the depolarization interventions researchers propose.".length;

const SUGGESTED_MATCHES = [
  {
    id: "match-001",
    submissionId: SUB1_ID,
    criterionId: "crit-thesis",
    passageStart: thesisStart,
    passageEnd: thesisEnd,
    rationale: "Explicit arguable claim with clear scope positioned in the introduction.",
    confidence: 0.94,
    matchState: "suggested" as const,
    isStale: false,
    createdAt: "2025-01-11T11:00:00Z",
  },
  {
    id: "match-002",
    submissionId: SUB1_ID,
    criterionId: "crit-evidence",
    passageStart: evidenceStart,
    passageEnd: evidenceEnd,
    rationale: "Named source with integrated analysis of filter bubble concept.",
    confidence: 0.91,
    matchState: "suggested" as const,
    isStale: false,
    createdAt: "2025-01-11T11:00:01Z",
  },
  {
    id: "match-003",
    submissionId: SUB1_ID,
    criterionId: "crit-evidence",
    passageStart: evidence2Start,
    passageEnd: evidence2End,
    rationale: "Quantitative citation with full page reference supporting the claim.",
    confidence: 0.89,
    matchState: "suggested" as const,
    isStale: false,
    createdAt: "2025-01-11T11:00:02Z",
  },
  {
    id: "match-004",
    submissionId: SUB1_ID,
    criterionId: "crit-organization",
    passageStart: orgStart,
    passageEnd: orgEnd,
    rationale: "\"Therefore\" signals logical consequence; bridges premise to conclusion.",
    confidence: 0.82,
    matchState: "suggested" as const,
    isStale: false,
    createdAt: "2025-01-11T11:00:03Z",
  },
];

// ── Grading record for submission 1 (partially completed) ────────────────────

const FAKE_GRADING_RECORD = {
  id: "grade-demo-001",
  submissionId: SUB1_ID,
  studentDisplayName: "Sofia Martinez",
  overallFeedback: "",
  savedAt: "2025-01-12T14:00:00Z",
  criterionScores: [
    { id: "cs-1", criterionId: "crit-thesis", selectedLevelId: "lv-thesis-4", overridePoints: null, criterionFeedback: "Clear thesis, minor ambiguity in scope." },
    { id: "cs-2", criterionId: "crit-evidence", selectedLevelId: "lv-evidence-5", overridePoints: null, criterionFeedback: "" },
    { id: "cs-3", criterionId: "crit-organization", selectedLevelId: null, overridePoints: null, criterionFeedback: "" },
    { id: "cs-4", criterionId: "crit-grammar", selectedLevelId: null, overridePoints: null, criterionFeedback: "" },
    { id: "cs-5", criterionId: "crit-citations", selectedLevelId: null, overridePoints: null, criterionFeedback: "" },
  ],
  suggestedMatches: SUGGESTED_MATCHES,
  confirmedMatches: [],
  criterionAnalysis: [
    { criterionId: "crit-thesis", state: "complete" as const, failureReason: null, analyzedCharCount: ESSAY_TEXT.length },
    { criterionId: "crit-evidence", state: "complete" as const, failureReason: null, analyzedCharCount: ESSAY_TEXT.length },
    { criterionId: "crit-organization", state: "complete" as const, failureReason: null, analyzedCharCount: ESSAY_TEXT.length },
    { criterionId: "crit-grammar", state: "complete" as const, failureReason: null, analyzedCharCount: ESSAY_TEXT.length },
    { criterionId: "crit-citations", state: "complete" as const, failureReason: null, analyzedCharCount: ESSAY_TEXT.length },
  ],
  extractedText: ESSAY_TEXT,
  extractionStatus: "success" as const,
  extractionFailureReason: null,
  isOversized: false,
  position: 1,
  batchSize: 3,
  totalScore: 9,
  maxScore: 20,
  unscoredCriterionCount: 3,
};

// ── Empty grading records for submissions 2 and 3 ────────────────────────────

function emptyGradingRecord(subId: string, name: string, text: string, position: number) {
  return {
    id: `grade-${subId}`,
    submissionId: subId,
    studentDisplayName: name,
    overallFeedback: "",
    savedAt: null,
    criterionScores: CRITERIA.map((c, i) => ({
      id: `cs-${subId}-${i}`,
      criterionId: c.id,
      selectedLevelId: null,
      overridePoints: null,
      criterionFeedback: "",
    })),
    suggestedMatches: [],
    confirmedMatches: [],
    criterionAnalysis: CRITERIA.map((c) => ({
      criterionId: c.id,
      state: "complete" as const,
      failureReason: null,
      analyzedCharCount: text.length,
    })),
    extractedText: text,
    extractionStatus: "success" as const,
    extractionFailureReason: null,
    isOversized: false,
    position,
    batchSize: 3,
    totalScore: 0,
    maxScore: 20,
    unscoredCriterionCount: 5,
  };
}

// ── Review data ──────────────────────────────────────────────────────────────

const FAKE_REVIEW = {
  sessionId: SESSION_ID,
  reviewConfirmedAt: null,
  totalSubmissions: 3,
  flaggedCount: 0,
  unflaggedCount: 3,
  criteria: CRITERIA.map((c) => ({
    criterionId: c.id,
    title: c.title,
    maxPoints: c.maxPoints,
    position: c.position,
  })),
  submissions: [
    {
      submissionId: SUB1_ID,
      studentDisplayName: "Sofia Martinez",
      position: 1,
      totalPoints: 9,
      maxPoints: 20,
      unscoredCriterionCount: 3,
      overrideCount: 0,
      criterionScores: [
        { criterionId: "crit-thesis", criterionTitle: "Thesis Clarity", points: 4, selectedLevelLabel: "Proficient", overridden: false },
        { criterionId: "crit-evidence", criterionTitle: "Use of Evidence", points: 5, selectedLevelLabel: "Exemplary", overridden: false },
        { criterionId: "crit-organization", criterionTitle: "Organization", points: null, selectedLevelLabel: null, overridden: false },
        { criterionId: "crit-grammar", criterionTitle: "Grammar & Mechanics", points: null, selectedLevelLabel: null, overridden: false },
        { criterionId: "crit-citations", criterionTitle: "Citation Format", points: null, selectedLevelLabel: null, overridden: false },
      ],
      flags: ["incomplete_grading" as const],
    },
    {
      submissionId: SUB2_ID,
      studentDisplayName: "Wei Chen",
      position: 2,
      totalPoints: 0,
      maxPoints: 20,
      unscoredCriterionCount: 5,
      overrideCount: 0,
      criterionScores: CRITERIA.map((c) => ({
        criterionId: c.id, criterionTitle: c.title, points: null, selectedLevelLabel: null, overridden: false,
      })),
      flags: ["incomplete_grading" as const],
    },
    {
      submissionId: SUB3_ID,
      studentDisplayName: "Marcus Johnson",
      position: 3,
      totalPoints: 0,
      maxPoints: 20,
      unscoredCriterionCount: 5,
      overrideCount: 0,
      criterionScores: CRITERIA.map((c) => ({
        criterionId: c.id, criterionTitle: c.title, points: null, selectedLevelLabel: null, overridden: false,
      })),
      flags: ["incomplete_grading" as const],
    },
  ],
};

// ── Route handler ────────────────────────────────────────────────────────────

// Mutable store for grading records so saves persist in-session
const gradingStore: Record<string, typeof FAKE_GRADING_RECORD> = {
  [SUB1_ID]: FAKE_GRADING_RECORD,
  [SUB2_ID]: emptyGradingRecord(SUB2_ID, "Wei Chen", ESSAY_TEXT_2, 2) as typeof FAKE_GRADING_RECORD,
  [SUB3_ID]: emptyGradingRecord(SUB3_ID, "Marcus Johnson", ESSAY_TEXT_3, 3) as typeof FAKE_GRADING_RECORD,
};

function delay(ms = 150): Promise<void> {
  return new Promise((r) => setTimeout(r, ms));
}

/**
 * Handles a mock API request. Returns the response body (JSON-serializable).
 * Throws an object with {status, body} to signal an error status.
 */
export async function handleMockRequest(
  method: string,
  path: string,
  body?: unknown,
): Promise<unknown> {
  await delay(); // simulate network latency

  // GET /api/me
  if (method === "GET" && path === "/me") {
    return FAKE_ME;
  }

  // GET /api/sessions
  if (method === "GET" && path === "/sessions") {
    return [FAKE_SESSION];
  }

  // POST /api/sessions
  if (method === "POST" && path === "/sessions") {
    const req = body as { name?: string };
    return { ...FAKE_SESSION, id: `sess-new-${Date.now()}`, name: req?.name ?? "New Session" };
  }

  // DELETE /api/sessions/:id
  if (method === "DELETE" && path.match(/^\/sessions\/[^/]+$/)) {
    return undefined;
  }

  // GET /api/sessions/:id
  if (method === "GET" && path.match(/^\/sessions\/[^/]+$/) && !path.includes("/rubric") && !path.includes("/submissions") && !path.includes("/review") && !path.includes("/export")) {
    return FAKE_SESSION;
  }

  // GET /api/sessions/:id/rubric
  if (method === "GET" && path.match(/^\/sessions\/[^/]+\/rubric$/)) {
    return FAKE_RUBRIC;
  }

  // PUT /api/sessions/:id/rubric
  if (method === "PUT" && path.match(/^\/sessions\/[^/]+\/rubric$/)) {
    return { ...FAKE_RUBRIC, criteria: (body as { criteria: unknown[] })?.criteria ?? CRITERIA };
  }

  // GET /api/sessions/:id/submissions
  if (method === "GET" && path.match(/^\/sessions\/[^/]+\/submissions$/)) {
    return FAKE_SUBMISSIONS;
  }

  // GET /api/sessions/:id/submissions/:id/grading
  const gradingGet = path.match(/^\/sessions\/[^/]+\/submissions\/([^/]+)\/grading$/);
  if (method === "GET" && gradingGet) {
    const subId = gradingGet[1];
    return gradingStore[subId] ?? emptyGradingRecord(subId, "Unknown", "", 0);
  }

  // PUT /api/sessions/:id/submissions/:id/grading
  const gradingPut = path.match(/^\/sessions\/[^/]+\/submissions\/([^/]+)\/grading$/);
  if (method === "PUT" && gradingPut) {
    const subId = gradingPut[1];
    const existing = gradingStore[subId];
    if (existing) {
      const req = body as { overallFeedback?: string; criterionScores?: unknown[] };
      const updated = { ...existing, overallFeedback: req?.overallFeedback ?? existing.overallFeedback, savedAt: new Date().toISOString() };
      if (req?.criterionScores) {
        updated.criterionScores = req.criterionScores as typeof existing.criterionScores;
      }
      gradingStore[subId] = updated;
      return updated;
    }
    return existing;
  }

  // POST /api/sessions/:id/submissions/:id/matches/:id/confirm
  if (method === "POST" && path.includes("/matches/") && path.endsWith("/confirm")) {
    const matchId = path.split("/matches/")[1].replace("/confirm", "");
    const match = SUGGESTED_MATCHES.find((m) => m.id === matchId);
    if (match) {
      return {
        id: `confirmed-${matchId}`,
        submissionId: match.submissionId,
        criterionId: match.criterionId,
        passageStart: match.passageStart,
        passageEnd: match.passageEnd,
        rationale: match.rationale,
        confidence: match.confidence,
        origin: "ta_confirmed",
        sourceMatchId: matchId,
        createdAt: new Date().toISOString(),
      };
    }
    return { id: `confirmed-${matchId}`, createdAt: new Date().toISOString() };
  }

  // POST /api/sessions/:id/submissions/:id/matches/:id/reject
  if (method === "POST" && path.includes("/matches/") && path.endsWith("/reject")) {
    return undefined;
  }

  // POST /api/sessions/:id/submissions/:id/comments/suggest
  if (method === "POST" && path.includes("/comments/suggest")) {
    return {
      snippets: [
        { text: "Your argument demonstrates strong analytical thinking. Consider expanding your discussion of counterarguments to strengthen the overall essay.", isAiGenerated: true },
        { text: "The evidence integration is effective but could benefit from more explicit connections between cited data and your central thesis.", isAiGenerated: true },
      ],
    };
  }

  // GET /api/sessions/:id/review
  if (method === "GET" && path.match(/^\/sessions\/[^/]+\/review$/)) {
    return FAKE_REVIEW;
  }

  // POST /api/sessions/:id/export/generic
  if (method === "POST" && path.includes("/export/")) {
    return { downloadUrl: "data:text/csv,student%2Cscore%0ASofia%20Martinez%2C9", filename: "grades_export.csv" };
  }

  // GET /api/jobs/:id
  if (method === "GET" && path.match(/^\/jobs\/[^/]+$/)) {
    return {
      id: JOB_ID,
      sessionId: SESSION_ID,
      jobType: "match_analysis",
      status: "complete",
      progressCurrent: 3,
      progressTotal: 3,
      failureReason: null,
      createdAt: "2025-01-11T11:00:00Z",
      updatedAt: "2025-01-11T11:00:05Z",
    };
  }

  // POST /api/sessions/:id/submissions/:id/analyze
  if (method === "POST" && path.includes("/analyze")) {
    return { jobId: JOB_ID };
  }

  // Fallback: 404
  console.warn(`[Mock API] Unhandled ${method} ${path}`);
  return undefined;
}
