import { useState, useRef } from 'react'

// ── Criterion config ───────────────────────────────────────────────────────

const CRITERIA = [
  {
    id: 'thesis',
    label: 'Thesis Clarity',
    color: '#D97706',
    bg: 'rgba(245,158,11,0.14)',
    border: '#D97706',
    maxPts: 5,
    description: 'Central argument is clearly stated, arguable, and appears early in the essay.',
    levels: [
      { pts: 5, label: 'Exemplary', desc: 'Thesis is precise, arguable, and elegantly positioned.' },
      { pts: 4, label: 'Proficient', desc: 'Thesis is clear and arguable with minor ambiguity.' },
      { pts: 3, label: 'Developing', desc: 'Thesis present but broad or partially unclear.' },
      { pts: 2, label: 'Beginning', desc: 'Thesis implied but not directly stated.' },
      { pts: 1, label: 'Insufficient', desc: 'No identifiable thesis.' },
    ],
  },
  {
    id: 'evidence',
    label: 'Use of Evidence',
    color: '#0D9488',
    bg: 'rgba(13,148,136,0.12)',
    border: '#0D9488',
    maxPts: 5,
    description: 'Integrates at least 3 cited sources; quotations and paraphrases directly support claims.',
    levels: [
      { pts: 5, label: 'Exemplary', desc: 'Evidence is varied, well-integrated, and thoroughly analyzed.' },
      { pts: 4, label: 'Proficient', desc: 'Evidence supports claims; minor integration issues.' },
      { pts: 3, label: 'Developing', desc: 'Evidence present but thin or under-analyzed.' },
      { pts: 2, label: 'Beginning', desc: 'Minimal sources; evidence often dropped in without context.' },
      { pts: 1, label: 'Insufficient', desc: 'Little to no evidence cited.' },
    ],
  },
  {
    id: 'organization',
    label: 'Organization',
    color: '#7C3AED',
    bg: 'rgba(124,58,237,0.11)',
    border: '#7C3AED',
    maxPts: 5,
    description: 'Logical paragraph structure with clear transitions linking ideas across sections.',
    levels: [
      { pts: 5, label: 'Exemplary', desc: 'Seamless flow; every transition serves the argument.' },
      { pts: 4, label: 'Proficient', desc: 'Well-organized; occasional abrupt transitions.' },
      { pts: 3, label: 'Developing', desc: 'Basic structure present but transitions weak.' },
      { pts: 2, label: 'Beginning', desc: 'Sections feel disjointed; logic hard to follow.' },
      { pts: 1, label: 'Insufficient', desc: 'No discernible organizational logic.' },
    ],
  },
  {
    id: 'grammar',
    label: 'Grammar & Mechanics',
    color: '#DC2626',
    bg: 'rgba(220,38,38,0.10)',
    border: '#DC2626',
    maxPts: 3,
    description: 'Minimal errors in grammar, punctuation, and sentence structure throughout.',
    levels: [
      { pts: 3, label: 'Proficient', desc: 'Virtually error-free; prose is polished.' },
      { pts: 2, label: 'Developing', desc: 'A few noticeable errors that don\'t impede reading.' },
      { pts: 1, label: 'Beginning', desc: 'Frequent errors that impede comprehension.' },
    ],
  },
  {
    id: 'citations',
    label: 'Citation Format',
    color: '#0374B5',
    bg: 'rgba(3,116,181,0.11)',
    border: '#0374B5',
    maxPts: 2,
    description: 'All in-text citations and bibliography entries follow MLA or APA format consistently.',
    levels: [
      { pts: 2, label: 'Proficient', desc: 'Consistent, correct citation format throughout.' },
      { pts: 1, label: 'Developing', desc: 'Minor citation errors or inconsistencies.' },
      { pts: 0, label: 'Insufficient', desc: 'Missing citations or wrong format.' },
    ],
  },
]

// ── Highlighted spans in the essay ────────────────────────────────────────

interface HSpan {
  id: string
  criterionId: string
  text: string
  confirmed: boolean // true = TA confirmed, false = AI-suggested
  tooltip: string
  paragraphIdx: number
  offsetInParagraph: number // character offset (used to order margin flags)
}

const ESSAY_PARAGRAPHS = [
  {
    idx: 0,
    label: null,
    text: 'Essay 3: Argumentative Analysis',
    isTitle: true,
  },
  {
    idx: 1,
    label: '¶1',
    text: 'The question of whether social media platforms bear moral responsibility for political polarization has moved from academic seminars into courtrooms and congressional hearings. I will argue that platforms are not neutral conduits but active architects of epistemic bubbles, and that this architectural complicity carries genuine moral weight. This essay examines the amplification algorithm, the advertising incentive structure, and the suppression of cross-cutting exposure to substantiate that claim.',
    isTitle: false,
  },
  {
    idx: 2,
    label: '¶2',
    text: 'To understand how platforms shape belief, one must first acknowledge the design intent embedded in recommendation systems. According to Pariser (2011), the "filter bubble" emerges not from user choice alone but from algorithmic curation that optimizes for engagement over exposure diversity. Studies show that users who receive algorithmically curated feeds are 40% more likely to share partisan content than users on reverse-chronological feeds (Bail et al., 2018, p. 9024). This evidence suggests that the architecture, not merely the audience, is causally implicated.',
    isTitle: false,
  },
  {
    idx: 3,
    label: '¶3',
    text: 'Critics of this view argue that users bear primary responsibility for their own information diet. However, this critique underestimates the asymmetry of expertise between platform engineers and ordinary users. Furthermore, internal documents released in the 2021 Facebook Papers reveal that company researchers were aware polarization metrics rose with engagement optimization, yet the feature remained live. The limitation of the "user choice" framework is that it treats an unequal relationship as symmetrical.',
    isTitle: false,
  },
  {
    idx: 4,
    label: '¶4',
    text: 'The advertising model compounds the problem. Platforms generate revenue proportional to time-on-site, and outrage reliably extends sessions. Therefore, any financial incentive for platforms runs counter to the depolarization interventions researchers propose. This structural conflict of interest distinguishes platform complicity from the more passive negligence of, say, a telephone company whose infrastructure happens to carry inflammatory speech.',
    isTitle: false,
  },
  {
    idx: 5,
    label: '¶5',
    text: 'In conclusion, the architecture of engagement-maximizing platforms is not value-neutral. First, it systematically narrows the epistemic range of users. Finally, it sustains itself through financial incentives that are structurally opposed to reform. Assigning moral responsibility to platforms is therefore not a category error but an accurate description of causal agency. The path forward requires treating platform design as a site of democratic accountability, not merely a market matter.',
    isTitle: false,
  },
]

// Precomputed highlight spans — offsets reference character position in the paragraph text
const HIGHLIGHT_SPANS: HSpan[] = [
  {
    id: 'h1',
    criterionId: 'thesis',
    text: 'I will argue that platforms are not neutral conduits but active architects of epistemic bubbles, and that this architectural complicity carries genuine moral weight.',
    confirmed: true,
    tooltip: 'Thesis Clarity — explicit arguable claim with clear scope.',
    paragraphIdx: 1,
    offsetInParagraph: 120,
  },
  {
    id: 'h2',
    criterionId: 'evidence',
    text: 'According to Pariser (2011), the "filter bubble" emerges not from user choice alone but from algorithmic curation that optimizes for engagement over exposure diversity.',
    confirmed: true,
    tooltip: 'Use of Evidence — named source with integrated analysis.',
    paragraphIdx: 2,
    offsetInParagraph: 96,
  },
  {
    id: 'h3',
    criterionId: 'evidence',
    text: 'Studies show that users who receive algorithmically curated feeds are 40% more likely to share partisan content than users on reverse-chronological feeds (Bail et al., 2018, p. 9024).',
    confirmed: true,
    tooltip: 'Use of Evidence — quantitative citation, full page reference.',
    paragraphIdx: 2,
    offsetInParagraph: 267,
  },
  {
    id: 'h4',
    criterionId: 'organization',
    text: 'However, this critique underestimates the asymmetry of expertise between platform engineers and ordinary users. Furthermore, internal documents released in the 2021 Facebook Papers',
    confirmed: false,
    tooltip: 'Organization — transition from counter-argument back to main claim. Confirm if intentional.',
    paragraphIdx: 3,
    offsetInParagraph: 85,
  },
  {
    id: 'h5',
    criterionId: 'citations',
    text: '(Bail et al., 2018, p. 9024)',
    confirmed: false,
    tooltip: 'Citation Format — APA in-text. Check bibliography entry.',
    paragraphIdx: 2,
    offsetInParagraph: 426,
  },
  {
    id: 'h6',
    criterionId: 'organization',
    text: 'Therefore, any financial incentive for platforms runs counter to the depolarization interventions researchers propose.',
    confirmed: true,
    tooltip: 'Organization — "Therefore" signals logical consequence; bridges ¶3 premise to ¶4 conclusion.',
    paragraphIdx: 4,
    offsetInParagraph: 122,
  },
  {
    id: 'h7',
    criterionId: 'thesis',
    text: 'Assigning moral responsibility to platforms is therefore not a category error but an accurate description of causal agency.',
    confirmed: false,
    tooltip: 'Thesis Clarity — restatement in conclusion. AI-flagged as possible thesis echo.',
    paragraphIdx: 5,
    offsetInParagraph: 220,
  },
]

// ── AI comment suggestions per criterion ──────────────────────────────────

const AI_COMMENTS: Record<string, Record<string, string>> = {
  thesis: {
    Exemplary: 'Your thesis is precise and elegantly positioned — the framing of platforms as "architects" rather than mere hosts is particularly effective.',
    Proficient: 'The thesis is clear and arguable. Consider sharpening the scope in the final sentence of ¶1 to avoid overpromising.',
    Developing: 'Your thesis emerges but remains somewhat broad. Try distilling it to a single claim before your first body paragraph.',
    Beginning: 'A thesis is implied but not explicitly stated. Readers need a direct, arguable claim early in the introduction.',
    Insufficient: 'No clear thesis was identifiable. Please revise to include a direct, arguable claim.',
  },
  evidence: {
    Exemplary: 'Evidence is varied and thoroughly integrated — the Bail et al. statistic is especially well deployed.',
    Proficient: 'Sources support your claims well. Make sure every quotation is followed by your own analysis (the "so what").',
    Developing: 'Evidence is present but needs more analysis. Don\'t let sources speak for themselves — explain what they prove.',
    Beginning: 'More sources are needed, and those you use should be more directly tied to your argument.',
    Insufficient: 'This essay lacks evidence. All claims should be supported with cited sources.',
  },
  organization: {
    Exemplary: 'The essay flows seamlessly; transitions like "Furthermore" and "Therefore" carry real logical weight here.',
    Proficient: 'Structure is clear. One or two transitions feel mechanical — aim for transitions that also advance the argument.',
    Developing: 'Paragraphs have a clear topic but transitions between them are abrupt. Try ending each paragraph with a bridge sentence.',
    Beginning: 'The sections feel disconnected. Consider outlining the logical relationship between each paragraph before revising.',
    Insufficient: 'No clear organizational logic is evident. A full structural revision is recommended.',
  },
  grammar: {
    Proficient: 'Prose is clean and polished throughout.',
    Developing: 'A few mechanical errors are present but don\'t impede reading. Proofread carefully before final submission.',
    Beginning: 'Frequent errors impede comprehension. Visit the Writing Center before resubmission.',
  },
  citations: {
    Proficient: 'Citations are consistent and correctly formatted throughout.',
    Developing: 'Minor citation errors noted. Check that all in-text citations have matching bibliography entries.',
    Insufficient: 'Citations are missing or incorrectly formatted. Follow APA guidelines throughout.',
  },
}

// ── Small SVG icons ────────────────────────────────────────────────────────

const IcoChevDown = () => (
  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="6 9 12 15 18 9" /></svg>
)
const IcoChevRight = () => (
  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="9 18 15 12 9 6" /></svg>
)
const IcoArrowLeft = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="15 18 9 12 15 6" /></svg>
)
const IcoArrowRight = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="9 18 15 12 9 6" /></svg>
)
const IcoEye = ({ off }: { off?: boolean }) => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    {off ? (
      <>
        <path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94" />
        <path d="M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19m-6.72-1.07a3 3 0 11-4.24-4.24" />
        <line x1="1" y1="1" x2="23" y2="23" />
      </>
    ) : (
      <>
        <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
        <circle cx="12" cy="12" r="3" />
      </>
    )}
  </svg>
)
const IcoGear = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="3" />
    <path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z" />
  </svg>
)
const IcoTarget = () => (
  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="10" /><circle cx="12" cy="12" r="6" /><circle cx="12" cy="12" r="2" />
  </svg>
)
const IcoSync = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="1 4 1 10 7 10" /><polyline points="23 20 23 14 17 14" />
    <path d="M20.49 9A9 9 0 005.64 5.64L1 10m22 4l-4.64 4.36A9 9 0 013.51 15" />
  </svg>
)
const IcoSparkle = () => (
  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 2l2.4 7.4H22l-6.2 4.5 2.4 7.4L12 17l-6.2 4.3 2.4-7.4L2 9.4h7.6L12 2z" />
  </svg>
)
const IcoMoon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M21 12.79A9 9 0 1111.21 3 7 7 0 0021 12.79z" />
  </svg>
)
const IcoSun = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="5" />
    <line x1="12" y1="1" x2="12" y2="3" /><line x1="12" y1="21" x2="12" y2="23" />
    <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" /><line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
    <line x1="1" y1="12" x2="3" y2="12" /><line x1="21" y1="12" x2="23" y2="12" />
    <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" /><line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
  </svg>
)

// ── Render essay paragraph with highlights ─────────────────────────────────

function renderParagraph(
  para: typeof ESSAY_PARAGRAPHS[0],
  spans: HSpan[],
  activeSpanId: string | null,
  activeCriterionId: string | null,
  showHighlights: boolean,
  onSpanClick: (id: string) => void,
  dark: boolean,
) {
  const paraSpans = spans.filter((s) => s.paragraphIdx === para.idx)
  if (paraSpans.length === 0 || !showHighlights) {
    return <span>{para.text}</span>
  }

  // Build segments
  type Seg = { text: string; spanId?: string }
  const segs: Seg[] = [{ text: para.text }]

  for (const hs of paraSpans) {
    const phrase = hs.text
    const next: Seg[] = []
    for (const seg of segs) {
      if (seg.spanId) { next.push(seg); continue }
      const idx = seg.text.indexOf(phrase)
      if (idx === -1) { next.push(seg); continue }
      if (idx > 0) next.push({ text: seg.text.slice(0, idx) })
      next.push({ text: phrase, spanId: hs.id })
      const after = seg.text.slice(idx + phrase.length)
      if (after) next.push({ text: after })
    }
    segs.splice(0, segs.length, ...next)
  }

  const criterion = (id: string) => CRITERIA.find((c) => c.id === spans.find((s) => s.id === id)?.criterionId)

  return (
    <>
      {segs.map((seg, i) => {
        if (!seg.spanId) return <span key={i}>{seg.text}</span>
        const hs = spans.find((s) => s.id === seg.spanId)!
        const crit = criterion(seg.spanId)!
        const isActive = activeSpanId === seg.spanId || activeCriterionId === hs.criterionId
        const dimmed = activeCriterionId && activeCriterionId !== hs.criterionId
        return (
          <mark
            key={i}
            className={`hl-span ${hs.confirmed ? 'hl-confirmed' : 'hl-suggested'}`}
            onClick={() => onSpanClick(seg.spanId!)}
            style={{
              '--hl-color': crit.color,
              backgroundColor: crit.bg,
              color: 'inherit',
              opacity: dimmed ? 0.3 : 1,
              boxShadow: isActive ? `0 0 0 2px ${crit.color}` : undefined,
              transition: 'opacity 0.2s, box-shadow 0.15s',
            } as React.CSSProperties}
          >
            {seg.text}
          </mark>
        )
      })}
    </>
  )
}

// ── Margin flag column ─────────────────────────────────────────────────────

function MarginFlags({
  paraIdx,
  spans,
  activeSpanId,
  openTooltipId,
  onFlagClick,
  dark,
}: {
  paraIdx: number
  spans: HSpan[]
  activeSpanId: string | null
  openTooltipId: string | null
  onFlagClick: (id: string) => void
  dark: boolean
}) {
  const paraSpans = spans.filter((s) => s.paragraphIdx === paraIdx)
  if (paraSpans.length === 0) return <div style={{ width: 28 }} />
  return (
    <div style={{ width: 28, display: 'flex', flexDirection: 'column', gap: 4, paddingTop: 2, position: 'relative' }}>
      {paraSpans.map((hs) => {
        const crit = CRITERIA.find((c) => c.id === hs.criterionId)!
        const isOpen = openTooltipId === hs.id
        return (
          <div key={hs.id} style={{ position: 'relative' }}>
            <button
              onClick={() => onFlagClick(hs.id)}
              title={crit.label}
              style={{
                width: 20,
                height: 20,
                borderRadius: 4,
                border: `1.5px solid ${crit.color}`,
                background: activeSpanId === hs.id ? crit.color : crit.bg,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: activeSpanId === hs.id ? '#fff' : crit.color,
                fontSize: 8,
                fontWeight: 700,
                transition: 'all 0.12s',
                outline: 'none',
              }}
            >
              {hs.confirmed ? '●' : '◌'}
            </button>
            {isOpen && (
              <div
                className="tooltip-open"
                style={{
                  position: 'absolute',
                  left: 26,
                  top: 0,
                  zIndex: 50,
                  background: dark ? '#1E2A35' : '#FFFFFF',
                  border: `1.5px solid ${crit.color}`,
                  borderRadius: 8,
                  padding: '10px 12px',
                  width: 240,
                  boxShadow: '0 4px 16px rgba(0,0,0,0.14)',
                  pointerEvents: 'none',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
                  <span
                    style={{
                      width: 8,
                      height: 8,
                      borderRadius: '50%',
                      background: crit.color,
                      flexShrink: 0,
                    }}
                  />
                  <span style={{ fontSize: 11, fontWeight: 700, color: crit.color }}>{crit.label}</span>
                  <span
                    style={{
                      marginLeft: 'auto',
                      fontSize: 9,
                      fontWeight: 600,
                      letterSpacing: '0.06em',
                      color: hs.confirmed ? '#16A34A' : '#D97706',
                      background: hs.confirmed ? 'rgba(22,163,74,0.1)' : 'rgba(217,119,6,0.1)',
                      padding: '1px 5px',
                      borderRadius: 3,
                    }}
                  >
                    {hs.confirmed ? 'CONFIRMED' : 'AI SUGGESTED'}
                  </span>
                </div>
                <p style={{ fontSize: 11.5, color: dark ? '#CBD5E1' : '#374151', lineHeight: 1.5 }}>
                  {hs.tooltip}
                </p>
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}

// ── Rubric criterion card ──────────────────────────────────────────────────

function CriterionCard({
  crit,
  score,
  isExpanded,
  isActive,
  onToggle,
  onScore,
  onJump,
  dark,
}: {
  crit: typeof CRITERIA[0]
  score: number | null
  isExpanded: boolean
  isActive: boolean
  onToggle: () => void
  onScore: (pts: number) => void
  onJump: () => void
  dark: boolean
}) {
  const selectedLevel = score !== null ? crit.levels.find((l) => l.pts === score) : null
  const aiComment = selectedLevel ? AI_COMMENTS[crit.id]?.[selectedLevel.label] : null

  return (
    <div
      className="sidebar-card"
      style={{
        background: dark ? (isActive ? '#1A2535' : '#152030') : (isActive ? '#F8FAFE' : '#FFFFFF'),
        border: `1.5px solid ${isActive ? crit.color : dark ? '#1E3448' : '#E0E6EB'}`,
        borderRadius: 10,
        overflow: 'hidden',
        marginBottom: 10,
      }}
    >
      {/* Card header */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          padding: '10px 12px',
          cursor: 'pointer',
          gap: 8,
        }}
        onClick={onToggle}
      >
        {/* Color swatch */}
        <span
          style={{
            width: 10,
            height: 10,
            borderRadius: '50%',
            background: crit.color,
            flexShrink: 0,
            boxShadow: isActive ? `0 0 0 3px ${crit.bg}` : 'none',
          }}
        />
        <span
          style={{
            fontSize: 12.5,
            fontWeight: 600,
            color: dark ? '#E2EAF4' : '#1E293B',
            flex: 1,
          }}
        >
          {crit.label}
        </span>

        {/* Score badge */}
        <span
          style={{
            fontFamily: 'JetBrains Mono, monospace',
            fontSize: 11,
            fontWeight: 600,
            color: score !== null ? crit.color : dark ? '#4A6280' : '#94A3B8',
            background: score !== null ? crit.bg : 'transparent',
            padding: '2px 6px',
            borderRadius: 4,
          }}
        >
          {score !== null ? `${score}/${crit.maxPts}` : `—/${crit.maxPts}`}
        </span>

        {/* Jump to */}
        <button
          onClick={(e) => { e.stopPropagation(); onJump() }}
          title="Jump to evidence"
          style={{
            width: 22,
            height: 22,
            borderRadius: 5,
            border: `1px solid ${isActive ? crit.color : dark ? '#1E3448' : '#E0E6EB'}`,
            background: isActive ? crit.bg : 'transparent',
            color: isActive ? crit.color : dark ? '#4A6280' : '#94A3B8',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <IcoTarget />
        </button>

        <span style={{ color: dark ? '#4A6280' : '#94A3B8', display: 'flex' }}>
          {isExpanded ? <IcoChevDown /> : <IcoChevRight />}
        </span>
      </div>

      {/* Criterion description (always visible) */}
      <div style={{ padding: '0 12px 10px', borderBottom: `1px solid ${dark ? '#1A2E42' : '#F0F4F8'}` }}>
        <p style={{ fontSize: 11, color: dark ? '#6B8CAE' : '#64748B', lineHeight: 1.5 }}>
          {crit.description}
        </p>
      </div>

      {/* Expanded: level selector */}
      {isExpanded && (
        <div style={{ padding: '10px 12px' }}>
          <p style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.08em', color: dark ? '#4A6280' : '#94A3B8', marginBottom: 8, textTransform: 'uppercase' }}>
            Rating Level
          </p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
            {crit.levels.map((level) => {
              const selected = score === level.pts
              return (
                <button
                  key={level.pts}
                  onClick={() => onScore(level.pts)}
                  style={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: 10,
                    padding: '8px 10px',
                    borderRadius: 7,
                    border: `1.5px solid ${selected ? crit.color : dark ? '#1A3044' : '#E8EEF3'}`,
                    background: selected ? crit.bg : 'transparent',
                    cursor: 'pointer',
                    textAlign: 'left',
                    transition: 'all 0.12s',
                  }}
                >
                  <span
                    style={{
                      width: 24,
                      height: 24,
                      borderRadius: 5,
                      background: selected ? crit.color : dark ? '#1A3044' : '#F1F5F9',
                      color: selected ? '#fff' : dark ? '#4A6280' : '#64748B',
                      fontFamily: 'JetBrains Mono, monospace',
                      fontSize: 11,
                      fontWeight: 700,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                    }}
                  >
                    {level.pts}
                  </span>
                  <div>
                    <p style={{ fontSize: 12, fontWeight: 600, color: selected ? crit.color : dark ? '#CBD5E1' : '#374151', marginBottom: 1 }}>
                      {level.label}
                    </p>
                    <p style={{ fontSize: 11, color: dark ? '#4A6280' : '#64748B', lineHeight: 1.4 }}>
                      {level.desc}
                    </p>
                  </div>
                </button>
              )
            })}
          </div>

          {/* AI comment suggestion */}
          {aiComment && (
            <div
              style={{
                marginTop: 12,
                padding: '8px 10px',
                background: dark ? 'rgba(3,116,181,0.1)' : 'rgba(3,116,181,0.06)',
                border: `1px solid ${dark ? 'rgba(3,116,181,0.25)' : 'rgba(3,116,181,0.2)'}`,
                borderRadius: 7,
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 5, marginBottom: 5 }}>
                <IcoSparkle />
                <span style={{ fontSize: 10, fontWeight: 700, color: '#0374B5', letterSpacing: '0.06em', textTransform: 'uppercase' }}>
                  AI Suggestion
                </span>
              </div>
              <p style={{ fontSize: 11, color: dark ? '#8BB4D0' : '#374151', lineHeight: 1.5, fontStyle: 'italic' }}>
                "{aiComment}"
              </p>
              <button
                style={{
                  marginTop: 6,
                  fontSize: 10,
                  fontWeight: 600,
                  color: '#0374B5',
                  background: 'none',
                  border: 'none',
                  cursor: 'pointer',
                  padding: 0,
                }}
              >
                Insert into comment ↓
              </button>
            </div>
          )}
        </div>
      )}

      {/* "No match" state */}
      {isExpanded && !HIGHLIGHT_SPANS.some((s) => s.criterionId === crit.id) && (
        <div
          style={{
            padding: '8px 12px 12px',
            textAlign: 'center',
            color: dark ? '#4A6280' : '#94A3B8',
            fontSize: 11,
            fontStyle: 'italic',
          }}
        >
          No matching passage found — flag manually.
        </div>
      )}
    </div>
  )
}

// ── Main App ───────────────────────────────────────────────────────────────

export default function App() {
  const [scores, setScores] = useState<Record<string, number | null>>(
    Object.fromEntries(CRITERIA.map((c) => [c.id, null]))
  )
  const [expandedId, setExpandedId] = useState<string>('thesis')
  const [activeCriterionId, setActiveCriterionId] = useState<string | null>('thesis')
  const [activeSpanId, setActiveSpanId] = useState<string | null>(null)
  const [openTooltipId, setOpenTooltipId] = useState<string | null>('h2')
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [showHighlights, setShowHighlights] = useState(true)
  const [dark, setDark] = useState(false)
  const [comment, setComment] = useState('')
  const [synced, setSynced] = useState(false)

  const submissionRef = useRef<HTMLDivElement>(null)

  const totalEarned = CRITERIA.reduce((s, c) => s + (scores[c.id] ?? 0), 0)
  const totalPossible = CRITERIA.reduce((s, c) => s + c.maxPts, 0)
  const scoredCount = CRITERIA.filter((c) => scores[c.id] !== null).length
  const allScored = scoredCount === CRITERIA.length

  function handleSpanClick(id: string) {
    const span = HIGHLIGHT_SPANS.find((s) => s.id === id)
    if (!span) return
    setActiveSpanId(id === activeSpanId ? null : id)
    setActiveCriterionId(span.criterionId)
    setExpandedId(span.criterionId)
    setOpenTooltipId(id === openTooltipId ? null : id)
  }

  function handleJump(criterionId: string) {
    setActiveCriterionId(criterionId)
    setActiveSpanId(null)
    const firstSpan = HIGHLIGHT_SPANS.find((s) => s.criterionId === criterionId)
    if (firstSpan) setOpenTooltipId(firstSpan.id)
  }

  function handleCriterionToggle(id: string) {
    setExpandedId(expandedId === id ? '' : id)
    setActiveCriterionId(id)
  }

  const bg = dark ? '#0D1B2A' : '#F5F5F5'
  const cardBg = dark ? '#152030' : '#FFFFFF'
  const borderColor = dark ? '#1A3347' : '#C7CDD1'
  const textPrimary = dark ? '#E2EAF4' : '#1A2332'
  const textSecondary = dark ? '#6B8CAE' : '#6B7E93'
  const headerBg = dark ? '#0A1520' : '#2D3B45'

  return (
    <div style={{ height: '100vh', display: 'flex', flexDirection: 'column', background: bg, overflow: 'hidden' }}>

      {/* ── Canvas top bar ── */}
      <header
        style={{
          background: headerBg,
          height: 52,
          display: 'flex',
          alignItems: 'center',
          padding: '0 20px',
          gap: 16,
          flexShrink: 0,
          boxShadow: '0 1px 4px rgba(0,0,0,0.25)',
          zIndex: 10,
        }}
      >
        {/* Panda logo placeholder */}
        <div
          style={{
            width: 32,
            height: 32,
            borderRadius: 7,
            background: 'rgba(255,255,255,0.12)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <span style={{ fontSize: 16 }}>🐼</span>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flex: 1 }}>
          <span style={{ color: 'rgba(255,255,255,0.55)', fontSize: 12 }}>SpeedGrader</span>
          <span style={{ color: 'rgba(255,255,255,0.3)', fontSize: 12 }}>›</span>
          <span style={{ color: '#FFFFFF', fontSize: 13, fontWeight: 600 }}>
            Essay 3: Argumentative Analysis
          </span>
        </div>

        {/* Student selector */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 6,
            background: 'rgba(255,255,255,0.1)',
            border: '1px solid rgba(255,255,255,0.15)',
            borderRadius: 7,
            padding: '5px 10px',
          }}
        >
          <button style={{ background: 'none', border: 'none', color: 'rgba(255,255,255,0.7)', cursor: 'pointer', display: 'flex' }}>
            <IcoArrowLeft />
          </button>
          <div style={{ textAlign: 'center' }}>
            <p style={{ color: '#FFFFFF', fontSize: 12, fontWeight: 600, lineHeight: 1.1 }}>Maya Chen</p>
            <p style={{ color: 'rgba(255,255,255,0.5)', fontSize: 10 }}>Student 7 of 24</p>
          </div>
          <button style={{ background: 'none', border: 'none', color: 'rgba(255,255,255,0.7)', cursor: 'pointer', display: 'flex' }}>
            <IcoArrowRight />
          </button>
        </div>

        <div
          style={{
            fontFamily: 'JetBrains Mono, monospace',
            fontSize: 11,
            color: 'rgba(255,255,255,0.45)',
            background: 'rgba(255,255,255,0.07)',
            padding: '3px 8px',
            borderRadius: 5,
          }}
        >
          ENGL 301
        </div>
      </header>

      {/* ── Main body ── */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>

        {/* ── Submission pane ── */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>

          {/* Floating mini-toolbar */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              padding: '8px 20px',
              borderBottom: `1px solid ${borderColor}`,
              background: cardBg,
              flexShrink: 0,
            }}
          >
            <span style={{ fontSize: 11, fontWeight: 600, color: textSecondary, letterSpacing: '0.05em', textTransform: 'uppercase', marginRight: 4 }}>
              Submission
            </span>

            {/* Highlights toggle */}
            <button
              onClick={() => setShowHighlights((v) => !v)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 5,
                padding: '4px 9px',
                borderRadius: 6,
                border: `1px solid ${showHighlights ? '#0374B5' : borderColor}`,
                background: showHighlights ? 'rgba(3,116,181,0.08)' : 'transparent',
                color: showHighlights ? '#0374B5' : textSecondary,
                fontSize: 11,
                fontWeight: 500,
                cursor: 'pointer',
                transition: 'all 0.12s',
              }}
            >
              <IcoEye off={!showHighlights} />
              {showHighlights ? 'Highlights on' : 'Highlights off'}
            </button>

            {/* Prev / Next flag */}
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                border: `1px solid ${borderColor}`,
                borderRadius: 6,
                overflow: 'hidden',
              }}
            >
              {['←', '→'].map((arrow, i) => (
                <button
                  key={arrow}
                  title={i === 0 ? 'Previous flagged passage' : 'Next flagged passage'}
                  style={{
                    width: 30,
                    height: 28,
                    background: 'transparent',
                    border: 'none',
                    borderRight: i === 0 ? `1px solid ${borderColor}` : 'none',
                    color: textSecondary,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 13,
                  }}
                >
                  {arrow}
                </button>
              ))}
            </div>

            {/* Settings */}
            <button
              style={{
                width: 28,
                height: 28,
                borderRadius: 6,
                border: `1px solid ${borderColor}`,
                background: 'transparent',
                color: textSecondary,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <IcoGear />
            </button>

            {/* Dark mode */}
            <button
              onClick={() => setDark((v) => !v)}
              style={{
                marginLeft: 'auto',
                width: 28,
                height: 28,
                borderRadius: 6,
                border: `1px solid ${borderColor}`,
                background: dark ? 'rgba(255,255,255,0.06)' : 'transparent',
                color: textSecondary,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              {dark ? <IcoSun /> : <IcoMoon />}
            </button>

            {/* Highlight key */}
            <div style={{ display: 'flex', gap: 8, paddingLeft: 8, borderLeft: `1px solid ${borderColor}` }}>
              {CRITERIA.map((c) => (
                <button
                  key={c.id}
                  onClick={() => {
                    setActiveCriterionId(activeCriterionId === c.id ? null : c.id)
                    setExpandedId(c.id)
                  }}
                  title={c.label}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 4,
                    fontSize: 10,
                    fontWeight: 600,
                    color: activeCriterionId === c.id ? c.color : textSecondary,
                    background: 'none',
                    border: 'none',
                    cursor: 'pointer',
                    padding: '2px 4px',
                    borderRadius: 4,
                    opacity: activeCriterionId && activeCriterionId !== c.id ? 0.4 : 1,
                    transition: 'opacity 0.15s',
                  }}
                >
                  <span style={{ width: 8, height: 8, borderRadius: '50%', background: c.color, flexShrink: 0 }} />
                  <span style={{ display: 'none' }}>{c.label}</span>
                </button>
              ))}
              <span style={{ fontSize: 10, color: textSecondary, alignSelf: 'center' }}>filter</span>
            </div>
          </div>

          {/* Essay */}
          <div
            ref={submissionRef}
            style={{
              flex: 1,
              overflowY: 'auto',
              padding: '32px 16px 32px 20px',
            }}
          >
            <div
              style={{
                maxWidth: 720,
                margin: '0 auto',
                background: cardBg,
                border: `1px solid ${borderColor}`,
                borderRadius: 10,
                padding: '40px 48px',
                boxShadow: dark ? '0 4px 24px rgba(0,0,0,0.35)' : '0 1px 8px rgba(0,0,0,0.06)',
              }}
            >
              {ESSAY_PARAGRAPHS.map((para) => (
                <div
                  key={para.idx}
                  style={{
                    display: 'flex',
                    gap: 10,
                    marginBottom: para.isTitle ? 20 : 0,
                  }}
                >
                  {/* Margin label + flags */}
                  <div style={{ width: 36, flexShrink: 0, display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4, paddingTop: para.isTitle ? 4 : 3 }}>
                    {para.label && (
                      <span
                        style={{
                          fontFamily: 'JetBrains Mono, monospace',
                          fontSize: 9,
                          color: textSecondary,
                          opacity: 0.6,
                          letterSpacing: '0.05em',
                        }}
                      >
                        {para.label}
                      </span>
                    )}
                    {showHighlights && (
                      <MarginFlags
                        paraIdx={para.idx}
                        spans={HIGHLIGHT_SPANS}
                        activeSpanId={activeSpanId}
                        openTooltipId={openTooltipId}
                        onFlagClick={handleSpanClick}
                        dark={dark}
                      />
                    )}
                  </div>

                  {/* Text */}
                  <div style={{ flex: 1 }}>
                    {para.isTitle ? (
                      <h1
                        style={{
                          fontSize: 18,
                          fontWeight: 700,
                          color: textPrimary,
                          marginBottom: 4,
                          lineHeight: 1.3,
                        }}
                      >
                        {para.text}
                      </h1>
                    ) : (
                      <p
                        style={{
                          fontSize: 14,
                          lineHeight: 1.8,
                          color: dark ? '#B8CAE0' : '#2D3D4E',
                          marginBottom: 20,
                        }}
                      >
                        {renderParagraph(
                          para,
                          HIGHLIGHT_SPANS,
                          activeSpanId,
                          activeCriterionId,
                          showHighlights,
                          handleSpanClick,
                          dark,
                        )}
                      </p>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Bottom grade entry (Canvas-native feel) */}
          <div
            style={{
              borderTop: `1px solid ${borderColor}`,
              background: cardBg,
              padding: '12px 20px',
              display: 'flex',
              gap: 16,
              alignItems: 'flex-end',
              flexShrink: 0,
            }}
          >
            <div>
              <p style={{ fontSize: 10, fontWeight: 700, color: textSecondary, letterSpacing: '0.07em', textTransform: 'uppercase', marginBottom: 5 }}>
                Grade
              </p>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  border: `1.5px solid ${allScored ? '#0374B5' : borderColor}`,
                  borderRadius: 7,
                  padding: '6px 10px',
                  background: allScored ? 'rgba(3,116,181,0.06)' : (dark ? '#0A1520' : '#F9FAFB'),
                }}
              >
                <span
                  style={{
                    fontFamily: 'JetBrains Mono, monospace',
                    fontSize: 20,
                    fontWeight: 700,
                    color: allScored ? '#0374B5' : textSecondary,
                  }}
                >
                  {totalEarned}
                </span>
                <span style={{ color: textSecondary, fontSize: 14 }}>/</span>
                <span style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 14, color: textSecondary }}>
                  {totalPossible}
                </span>
              </div>
            </div>
            <div style={{ flex: 1 }}>
              <p style={{ fontSize: 10, fontWeight: 700, color: textSecondary, letterSpacing: '0.07em', textTransform: 'uppercase', marginBottom: 5 }}>
                Comment
              </p>
              <textarea
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                placeholder="Add a comment to the student…"
                rows={2}
                style={{
                  width: '100%',
                  background: dark ? '#0A1520' : '#F9FAFB',
                  border: `1px solid ${borderColor}`,
                  borderRadius: 7,
                  padding: '7px 10px',
                  fontSize: 12,
                  color: textPrimary,
                  resize: 'none',
                  outline: 'none',
                  fontFamily: 'Inter, sans-serif',
                }}
                onFocus={(e) => (e.currentTarget.style.borderColor = '#0374B5')}
                onBlur={(e) => (e.currentTarget.style.borderColor = borderColor)}
              />
            </div>
            <div style={{ fontSize: 11, color: textSecondary, textAlign: 'center', lineHeight: 1.4 }}>
              <span style={{ display: 'block', color: dark ? '#4A6280' : '#94A3B8', fontSize: 10 }}>
                {scoredCount}/{CRITERIA.length} criteria
              </span>
            </div>
          </div>
        </div>

        {/* ── GradeLens sidebar ── */}
        <div
          style={{
            width: sidebarOpen ? 356 : 36,
            flexShrink: 0,
            display: 'flex',
            transition: 'width 0.22s ease',
            borderLeft: `1px solid ${borderColor}`,
            position: 'relative',
            overflow: 'hidden',
          }}
        >
          {/* Collapse tab */}
          <button
            onClick={() => setSidebarOpen((v) => !v)}
            title={sidebarOpen ? 'Collapse sidebar' : 'Expand GradeLens'}
            style={{
              position: 'absolute',
              left: 0,
              top: '50%',
              transform: 'translateY(-50%)',
              zIndex: 5,
              width: 18,
              height: 56,
              background: '#0374B5',
              border: 'none',
              borderRadius: '6px 0 0 6px',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#fff',
              boxShadow: '-2px 2px 8px rgba(0,0,0,0.18)',
            }}
          >
            <span style={{ fontSize: 9, transform: sidebarOpen ? 'rotate(0)' : 'rotate(180deg)', transition: 'transform 0.2s' }}>◀</span>
          </button>

          {sidebarOpen && (
            <div
              style={{
                flex: 1,
                display: 'flex',
                flexDirection: 'column',
                background: dark ? '#0F1D2B' : '#F8FAFE',
                overflow: 'hidden',
                marginLeft: 18,
              }}
            >
              {/* GradeLens header */}
              <div
                style={{
                  padding: '12px 14px 10px',
                  borderBottom: `1px solid ${dark ? '#1A3347' : '#DDE5EE'}`,
                  background: dark ? '#0A1822' : '#FFFFFF',
                  flexShrink: 0,
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                  {/* Extension badge */}
                  <div
                    style={{
                      width: 28,
                      height: 28,
                      borderRadius: 7,
                      background: 'linear-gradient(135deg, #0374B5, #0D9488)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                      boxShadow: '0 2px 6px rgba(3,116,181,0.35)',
                    }}
                  >
                    <span style={{ color: '#fff', fontSize: 13 }}>⬡</span>
                  </div>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <span style={{ fontSize: 13, fontWeight: 700, color: textPrimary }}>GradeLens</span>
                      <span
                        style={{
                          fontSize: 8,
                          fontWeight: 700,
                          letterSpacing: '0.08em',
                          color: '#0374B5',
                          background: 'rgba(3,116,181,0.1)',
                          padding: '1px 5px',
                          borderRadius: 3,
                          border: '1px solid rgba(3,116,181,0.2)',
                        }}
                      >
                        EXT
                      </span>
                    </div>
                    <p style={{ fontSize: 10, color: textSecondary }}>Essay 3 · ENGL 301</p>
                  </div>
                  <button
                    onClick={() => setDark((v) => !v)}
                    style={{ background: 'none', border: 'none', color: textSecondary, cursor: 'pointer', display: 'flex' }}
                  >
                    {dark ? <IcoSun /> : <IcoMoon />}
                  </button>
                </div>

                {/* Progress bar */}
                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 5 }}>
                    <span style={{ fontSize: 11, color: textSecondary }}>
                      Criteria addressed: <strong style={{ color: textPrimary }}>{scoredCount}/{CRITERIA.length}</strong>
                    </span>
                    <span
                      style={{
                        fontFamily: 'JetBrains Mono, monospace',
                        fontSize: 11,
                        fontWeight: 700,
                        color: allScored ? '#0374B5' : textSecondary,
                      }}
                    >
                      {totalEarned}/{totalPossible} pts
                    </span>
                  </div>
                  <div
                    style={{
                      height: 5,
                      background: dark ? '#1A3347' : '#E4EBF2',
                      borderRadius: 999,
                      overflow: 'hidden',
                    }}
                  >
                    <div
                      style={{
                        height: '100%',
                        width: `${(scoredCount / CRITERIA.length) * 100}%`,
                        background: 'linear-gradient(90deg, #0374B5, #0D9488)',
                        borderRadius: 999,
                        transition: 'width 0.4s ease',
                      }}
                    />
                  </div>
                </div>
              </div>

              {/* Criteria list */}
              <div style={{ flex: 1, overflowY: 'auto', padding: '12px 12px 0' }}>
                {CRITERIA.map((crit) => (
                  <CriterionCard
                    key={crit.id}
                    crit={crit}
                    score={scores[crit.id] ?? null}
                    isExpanded={expandedId === crit.id}
                    isActive={activeCriterionId === crit.id}
                    onToggle={() => handleCriterionToggle(crit.id)}
                    onScore={(pts) => {
                      setScores((prev) => ({ ...prev, [crit.id]: pts }))
                      setSynced(false)
                    }}
                    onJump={() => handleJump(crit.id)}
                    dark={dark}
                  />
                ))}
                <div style={{ height: 12 }} />
              </div>

              {/* Bottom: comment + sync */}
              <div
                style={{
                  padding: '12px',
                  borderTop: `1px solid ${dark ? '#1A3347' : '#DDE5EE'}`,
                  background: dark ? '#0A1822' : '#FFFFFF',
                  flexShrink: 0,
                }}
              >
                <div style={{ marginBottom: 8 }}>
                  <p style={{ fontSize: 10, fontWeight: 700, color: textSecondary, letterSpacing: '0.07em', textTransform: 'uppercase', marginBottom: 5 }}>
                    Feedback Comment
                  </p>
                  <textarea
                    value={comment}
                    onChange={(e) => setComment(e.target.value)}
                    placeholder="Add overall feedback…"
                    rows={3}
                    style={{
                      width: '100%',
                      background: dark ? '#0D1B2A' : '#F8FAFE',
                      border: `1px solid ${dark ? '#1A3347' : '#DDE5EE'}`,
                      borderRadius: 7,
                      padding: '8px 10px',
                      fontSize: 12,
                      color: textPrimary,
                      resize: 'none',
                      outline: 'none',
                      fontFamily: 'Inter, sans-serif',
                      lineHeight: 1.5,
                    }}
                    onFocus={(e) => (e.currentTarget.style.borderColor = '#0374B5')}
                    onBlur={(e) => (e.currentTarget.style.borderColor = dark ? '#1A3347' : '#DDE5EE')}
                  />
                </div>
                <button
                  onClick={() => { if (allScored) setSynced(true) }}
                  disabled={!allScored}
                  style={{
                    width: '100%',
                    padding: '9px 0',
                    borderRadius: 8,
                    border: 'none',
                    background: synced
                      ? 'rgba(3,116,181,0.12)'
                      : allScored
                        ? 'linear-gradient(135deg, #0374B5, #025F96)'
                        : dark ? '#1A3347' : '#E4EBF2',
                    color: synced
                      ? '#0374B5'
                      : allScored
                        ? '#FFFFFF'
                        : textSecondary,
                    fontSize: 12,
                    fontWeight: 700,
                    cursor: allScored ? 'pointer' : 'not-allowed',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: 7,
                    transition: 'all 0.15s',
                    boxShadow: allScored && !synced ? '0 2px 8px rgba(3,116,181,0.3)' : 'none',
                  }}
                >
                  <IcoSync />
                  {synced ? 'Synced to Canvas ✓' : allScored ? 'Sync to Canvas Gradebook' : `Score ${CRITERIA.length - scoredCount} more criteria`}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
