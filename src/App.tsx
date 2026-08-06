import { useEffect, useMemo, useRef, useState } from 'react'
import { ApiError, syncToCanvas } from './canvas/api'
import { useCanvasData } from './canvas/useCanvasData'
import type { Criterion, HSpan, Paragraph } from './canvas/types'

/** The single assignment this deployment grades. */
const ASSIGNMENT_ID = import.meta.env.VITE_CANVAS_ASSIGNMENT_ID ?? '1'

// ── Canvas-backed data ────────────────────────────────────────────────────
//
// The rubric, submission text, evidence spans, and suggested comments all arrive
// from Canvas via the backend. Nothing here is hardcoded: when a load fails the UI
// says so rather than falling back to sample data.

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
  para: Paragraph,
  criteria: Criterion[],
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

  const criterion = (id: string) => criteria.find((c) => c.id === spans.find((s) => s.id === id)?.criterionId)

  return (
    <>
      {segs.map((seg, i) => {
        if (!seg.spanId) return <span key={i}>{seg.text}</span>
        const hs = spans.find((s) => s.id === seg.spanId)!
        const crit = criterion(seg.spanId)
        // A span whose criterion is missing means the rubric changed under us; skip the
        // highlight rather than crashing the whole document view.
        if (!crit) return <span key={i}>{seg.text}</span>
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
  criteria,
  spans,
  activeSpanId,
  openTooltipId,
  onFlagClick,
  dark,
}: {
  paraIdx: number
  criteria: Criterion[]
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
        const crit = criteria.find((c) => c.id === hs.criterionId)
        if (!crit) return null
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
  aiComments,
  spans,
  score,
  isExpanded,
  isActive,
  onToggle,
  onScore,
  onJump,
  dark,
}: {
  crit: Criterion
  aiComments: Record<string, string> | undefined
  spans: HSpan[]
  score: number | null
  isExpanded: boolean
  isActive: boolean
  onToggle: () => void
  onScore: (pts: number) => void
  onJump: () => void
  dark: boolean
}) {
  const selectedLevel = score !== null ? crit.levels.find((l) => l.pts === score) : null
  const aiComment = selectedLevel ? aiComments?.[selectedLevel.label] : null

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
      {isExpanded && !spans.some((s) => s.criterionId === crit.id) && (
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

// ── Full-screen load / empty states ────────────────────────────────────────

function FullScreenNotice({
  bg,
  color,
  title,
  detail,
  action,
}: {
  bg: string
  color: string
  title: string
  detail?: string
  action?: { label: string; onClick: () => void }
}) {
  return (
    <div
      style={{
        height: '100vh',
        background: bg,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 12,
        padding: 24,
        textAlign: 'center',
      }}
    >
      <p style={{ color, fontSize: 15, fontWeight: 600 }}>{title}</p>
      {detail && (
        <p style={{ color, fontSize: 12, maxWidth: 460, lineHeight: 1.5, opacity: 0.85 }}>
          {detail}
        </p>
      )}
      {action && (
        <button
          onClick={action.onClick}
          style={{
            marginTop: 4,
            padding: '7px 18px',
            borderRadius: 7,
            border: 'none',
            background: 'linear-gradient(135deg, #0374B5, #025F96)',
            color: '#FFFFFF',
            fontSize: 12,
            fontWeight: 600,
            cursor: 'pointer',
          }}
        >
          {action.label}
        </button>
      )}
    </div>
  )
}

// ── Main App ───────────────────────────────────────────────────────────────

export default function App() {
  const canvas = useCanvasData(ASSIGNMENT_ID)

  const criteria = useMemo(() => canvas.rubric?.criteria ?? [], [canvas.rubric])
  const paragraphs = canvas.submission?.paragraphs ?? []
  const spans = canvas.submission?.spans ?? []
  const aiComments = canvas.submission?.comments ?? {}

  const [scores, setScores] = useState<Record<string, number | null>>({})
  const [expandedId, setExpandedId] = useState<string>('')
  const [activeCriterionId, setActiveCriterionId] = useState<string | null>(null)
  const [activeSpanId, setActiveSpanId] = useState<string | null>(null)
  const [openTooltipId, setOpenTooltipId] = useState<string | null>(null)
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [showHighlights, setShowHighlights] = useState(true)
  const [dark, setDark] = useState(false)
  const [comment, setComment] = useState('')
  const [synced, setSynced] = useState(false)
  const [syncing, setSyncing] = useState(false)
  const [syncError, setSyncError] = useState<string | null>(null)
  const [canvasTotal, setCanvasTotal] = useState<number | null>(null)

  const submissionRef = useRef<HTMLDivElement>(null)

  // Reset per-submission state when the TA moves to another student, seeding from any
  // assessment Canvas already holds so re-opening graded work shows the real scores.
  useEffect(() => {
    if (!canvas.submission) return

    const seeded: Record<string, number | null> = {}
    for (const c of criteria) {
      seeded[c.id] = canvas.submission.existingScores?.[c.id] ?? null
    }
    setScores(seeded)
    setExpandedId(criteria[0]?.id ?? '')
    setActiveCriterionId(criteria[0]?.id ?? null)
    setActiveSpanId(null)
    setOpenTooltipId(null)
    setComment('')
    setSynced(false)
    setSyncError(null)
    setCanvasTotal(null)
  }, [canvas.submission, criteria])

  const totalEarned = criteria.reduce((s, c) => s + (scores[c.id] ?? 0), 0)
  const totalPossible = criteria.reduce((s, c) => s + c.maxPts, 0)
  const scoredCount = criteria.filter((c) => scores[c.id] != null).length
  const allScored = criteria.length > 0 && scoredCount === criteria.length

  async function handleSync() {
    if (!canvas.currentStudent || !allScored || syncing) return

    setSyncing(true)
    setSyncError(null)
    try {
      const result = await syncToCanvas(
        ASSIGNMENT_ID,
        canvas.currentStudent.userId,
        criteria.map((c) => ({
          criterionId: c.id,
          points: scores[c.id] ?? null,
          // Name the rating the TA actually picked, so Canvas records the level and
          // not just a bare point value.
          ratingId: c.levels.find((l) => l.pts === scores[c.id])?.id ?? null,
        })),
        comment,
      )
      setSynced(true)
      setCanvasTotal(result.canvasTotal)
    } catch (cause) {
      // Preserve every TA input and show Canvas's own message so the sync can be retried.
      setSyncError(cause instanceof ApiError ? cause.message : String(cause))
    } finally {
      setSyncing(false)
    }
  }

  function handleSpanClick(id: string) {
    const span = spans.find((s) => s.id === id)
    if (!span) return
    setActiveSpanId(id === activeSpanId ? null : id)
    setActiveCriterionId(span.criterionId)
    setExpandedId(span.criterionId)
    setOpenTooltipId(id === openTooltipId ? null : id)
  }

  function handleJump(criterionId: string) {
    setActiveCriterionId(criterionId)
    setActiveSpanId(null)
    const firstSpan = spans.find((s) => s.criterionId === criterionId)
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

  // Load states are full-screen because there is nothing meaningful to render around a
  // missing rubric. None of them substitute sample data — a screen that looks like it
  // is working while disconnected is worse than one that says it is not.
  if (canvas.loading) {
    return <FullScreenNotice bg={bg} color={textSecondary} title="Loading from Canvas…" />
  }

  if (canvas.error) {
    return (
      <FullScreenNotice
        bg={bg}
        color={textSecondary}
        title="Could not load from Canvas"
        detail={canvas.error}
        action={{ label: 'Retry', onClick: canvas.reload }}
      />
    )
  }

  if (!canvas.rubric?.hasRubric) {
    return (
      <FullScreenNotice
        bg={bg}
        color={textSecondary}
        title="No rubric attached to this assignment"
        detail="Attach a rubric in Canvas, then reload. Grading cannot proceed without one."
        action={{ label: 'Reload', onClick: canvas.reload }}
      />
    )
  }

  if (canvas.queue.length === 0) {
    return (
      <FullScreenNotice
        bg={bg}
        color={textSecondary}
        title="No submissions to grade"
        detail="Every student is still unsubmitted, so the grading queue is empty."
        action={{ label: 'Reload', onClick: canvas.reload }}
      />
    )
  }

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
            {canvas.rubric?.assignmentName ?? 'Loading…'}
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
          <button
            onClick={canvas.previous}
            disabled={canvas.currentIndex === 0}
            title="Previous student"
            style={{
              background: 'none',
              border: 'none',
              color: canvas.currentIndex === 0 ? 'rgba(255,255,255,0.25)' : 'rgba(255,255,255,0.7)',
              cursor: canvas.currentIndex === 0 ? 'default' : 'pointer',
              display: 'flex',
            }}
          >
            <IcoArrowLeft />
          </button>
          <div style={{ textAlign: 'center', minWidth: 110 }}>
            <p style={{ color: '#FFFFFF', fontSize: 12, fontWeight: 600, lineHeight: 1.1 }}>
              {canvas.currentStudent?.name ?? '—'}
            </p>
            <p style={{ color: 'rgba(255,255,255,0.5)', fontSize: 10 }}>
              {canvas.queue.length > 0
                ? `Student ${canvas.currentIndex + 1} of ${canvas.queue.length}`
                : 'No submissions'}
            </p>
          </div>
          <button
            onClick={canvas.next}
            disabled={canvas.currentIndex >= canvas.queue.length - 1}
            title="Next student"
            style={{
              background: 'none',
              border: 'none',
              color:
                canvas.currentIndex >= canvas.queue.length - 1
                  ? 'rgba(255,255,255,0.25)'
                  : 'rgba(255,255,255,0.7)',
              cursor: canvas.currentIndex >= canvas.queue.length - 1 ? 'default' : 'pointer',
              display: 'flex',
            }}
          >
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
              {criteria.map((c) => (
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
              {canvas.loadingSubmission && (
                <p style={{ color: textSecondary, fontSize: 12, marginBottom: 16 }}>
                  Loading submission…
                </p>
              )}

              {/* Extraction can fail without blocking grading — the TA scores manually. */}
              {canvas.submission?.extractionError && (
                <div
                  role="alert"
                  style={{
                    marginBottom: 20,
                    padding: '10px 12px',
                    borderRadius: 7,
                    background: 'rgba(217,119,6,0.1)',
                    border: '1px solid rgba(217,119,6,0.35)',
                    color: dark ? '#FCD34D' : '#92400E',
                    fontSize: 12,
                    lineHeight: 1.5,
                  }}
                >
                  <strong>Could not read this submission.</strong> {canvas.submission.extractionError}
                  {' '}You can still score it manually against the rubric.
                </div>
              )}

              {!canvas.loadingSubmission
                && paragraphs.length === 0
                && !canvas.submission?.extractionError && (
                <p style={{ color: textSecondary, fontSize: 12 }}>
                  This submission has no readable text.
                </p>
              )}

              {paragraphs.map((para) => (
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
                        criteria={criteria}
                        spans={spans}
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
                          criteria,
                          spans,
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
                {scoredCount}/{criteria.length} criteria
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
                      Criteria addressed: <strong style={{ color: textPrimary }}>{scoredCount}/{criteria.length}</strong>
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
                        width: `${criteria.length ? (scoredCount / criteria.length) * 100 : 0}%`,
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
                {criteria.map((crit) => (
                  <CriterionCard
                    key={crit.id}
                    crit={crit}
                    aiComments={aiComments[crit.id]}
                    spans={spans}
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
                {syncError && (
                  <div
                    role="alert"
                    style={{
                      marginBottom: 8,
                      padding: '8px 10px',
                      borderRadius: 7,
                      background: 'rgba(220,38,38,0.1)',
                      border: '1px solid rgba(220,38,38,0.35)',
                      color: dark ? '#FCA5A5' : '#B91C1C',
                      fontSize: 11,
                      lineHeight: 1.4,
                    }}
                  >
                    {syncError}
                  </div>
                )}
                {synced && canvasTotal != null && (
                  <p style={{ marginBottom: 8, fontSize: 11, color: textSecondary }}>
                    Canvas recorded <strong style={{ color: textPrimary }}>{canvasTotal}</strong> points.
                  </p>
                )}
                <button
                  onClick={handleSync}
                  disabled={!allScored || syncing || synced}
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
                  {synced ? 'Synced to Canvas ✓' : syncing ? 'Syncing…' : allScored ? 'Sync to Canvas Gradebook' : `Score ${criteria.length - scoredCount} more criteria`}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
