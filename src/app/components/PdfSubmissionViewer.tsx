import {
  getDocument,
  GlobalWorkerOptions,
  Util,
  type PDFDocumentProxy,
  type PDFPageProxy,
} from "pdfjs-dist"
import pdfWorkerUrl from "pdfjs-dist/build/pdf.worker.min.mjs?url"
import { useEffect, useRef, useState } from "react"

import type { Rubric, SuggestedMatch } from "../types"

GlobalWorkerOptions.workerSrc = pdfWorkerUrl

type TextItem = {
  str: string
  width: number
  transform: number[]
}

type PositionedTextItem = TextItem & {
  start: number
  end: number
}

type PageModel = {
  page: PDFPageProxy
  pageNumber: number
  viewport: ReturnType<PDFPageProxy["getViewport"]>
  items: PositionedTextItem[]
}

const PDF_SCALE = 1.2

function normalizeItemText(value: string) {
  return value.replace(/[ \t]+/g, " ").trim()
}

async function buildPageModels(document: PDFDocumentProxy) {
  const models: PageModel[] = []
  let normalizedText = ""
  let documentCursor = 0
  let hasTextPage = false

  for (let pageNumber = 1; pageNumber <= document.numPages; pageNumber += 1) {
    const page = await document.getPage(pageNumber)
    const content = await page.getTextContent()
    const sourceItems = content.items.filter(
      (item): item is typeof item & TextItem => "str" in item,
    )
    const visibleItems = sourceItems
      .map((item) => ({ item, text: normalizeItemText(item.str) }))
      .filter(({ text }) => text.length > 0)

    if (visibleItems.length > 0 && hasTextPage) {
      documentCursor += 2
      normalizedText += "\n\n"
    }
    const positionedItems: PositionedTextItem[] = []
    visibleItems.forEach(({ item, text }, index) => {
      if (index > 0) {
        documentCursor += 1
        normalizedText += " "
      }
      const start = documentCursor
      documentCursor += text.length
      positionedItems.push({
        str: text,
        width: item.width,
        transform: item.transform,
        start,
        end: documentCursor,
      })
      normalizedText += text
    })
    if (visibleItems.length > 0) hasTextPage = true

    models.push({
      page,
      pageNumber,
      viewport: page.getViewport({ scale: PDF_SCALE }),
      items: positionedItems,
    })
  }
  return { models, normalizedText }
}

function PdfPage({
  model,
  suggestions,
  rubric,
}: {
  model: PageModel
  suggestions: SuggestedMatch[]
  rubric: Rubric
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const outputScale = window.devicePixelRatio || 1
    canvas.width = Math.floor(model.viewport.width * outputScale)
    canvas.height = Math.floor(model.viewport.height * outputScale)
    canvas.style.width = `${Math.floor(model.viewport.width)}px`
    canvas.style.height = `${Math.floor(model.viewport.height)}px`
    const task = model.page.render({
      canvas,
      viewport: model.viewport,
      transform:
        outputScale === 1 ? undefined : [outputScale, 0, 0, outputScale, 0, 0],
    })
    return () => task.cancel()
  }, [model])

  const overlays = suggestions.flatMap((suggestion) => {
    const criterion = rubric.criteria.find(
      (item) => item.id === suggestion.criterionId,
    )
    return model.items.flatMap((item) => {
      const overlapStart = Math.max(item.start, suggestion.passageStart)
      const overlapEnd = Math.min(item.end, suggestion.passageEnd)
      if (overlapStart >= overlapEnd) return []

      const transform = Util.transform(model.viewport.transform, item.transform)
      const height = Math.hypot(transform[2], transform[3])
      const fullWidth = item.width * model.viewport.scale
      const startRatio = (overlapStart - item.start) / (item.end - item.start)
      const widthRatio = (overlapEnd - overlapStart) / (item.end - item.start)
      return [
        {
          suggestion,
          criterion,
          left: transform[4] + fullWidth * startRatio,
          top: transform[5] - height,
          width: Math.max(fullWidth * widthRatio, 2),
          height: Math.max(height, 8),
        },
      ]
    })
  })

  return (
    <div
      className="relative mx-auto bg-white shadow-lg"
      style={{ height: model.viewport.height, width: model.viewport.width }}
    >
      <canvas data-pdf-page={model.pageNumber} ref={canvasRef} />
      <div
        className="pointer-events-none absolute inset-0"
        aria-label={`Evidence overlays on page ${model.pageNumber}`}
      >
        {overlays.map((overlay, index) => {
          const isTooltipAnchor =
            overlays.findIndex(
              (item) => item.suggestion.id === overlay.suggestion.id,
            ) === index
          const tooltipId = `pdf-tooltip-${overlay.suggestion.id}`
          return (
            <span
              aria-describedby={isTooltipAnchor ? tooltipId : undefined}
              aria-label={
                isTooltipAnchor
                  ? `Why this may match ${overlay.criterion?.title ?? "rubric criterion"}`
                  : undefined
              }
              className={`group absolute rounded-[2px] border-b-2 transition ${
                isTooltipAnchor
                  ? "pointer-events-auto cursor-help focus:outline-2 focus:outline-offset-2"
                  : ""
              }`}
              data-testid={
                isTooltipAnchor
                  ? `pdf-evidence-${overlay.suggestion.id}`
                  : undefined
              }
              id={
                isTooltipAnchor
                  ? `pdf-suggestion-${overlay.suggestion.id}`
                  : undefined
              }
              key={`${overlay.suggestion.id}-${index}`}
              style={{
                backgroundColor: `${overlay.criterion?.displayColor ?? "#B45309"}42`,
                borderColor: overlay.criterion?.displayColor ?? "#B45309",
                height: overlay.height,
                left: overlay.left,
                top: overlay.top,
                width: overlay.width,
              }}
              role={isTooltipAnchor ? "button" : undefined}
              tabIndex={isTooltipAnchor ? 0 : undefined}
            >
              {isTooltipAnchor ? (
                <span
                  className="pointer-events-none absolute top-full left-1/2 z-30 mt-2 hidden w-72 -translate-x-1/2 border border-slate-700 bg-slate-900 p-3 text-left text-white shadow-xl group-hover:block group-focus:block"
                  id={tooltipId}
                  role="tooltip"
                >
                  <span className="block font-mono text-[9px] font-semibold uppercase tracking-[0.14em] text-sky-300">
                    May match {overlay.criterion?.title ?? "rubric criterion"}
                  </span>
                  <span className="mt-1.5 block text-xs leading-5 normal-case tracking-normal">
                    {overlay.suggestion.rationale}
                  </span>
                </span>
              ) : null}
            </span>
          )
        })}
      </div>
      <span className="absolute right-2 bottom-2 bg-slate-900/75 px-2 py-1 font-mono text-[9px] text-white">
        {model.pageNumber}
      </span>
    </div>
  )
}

export default function PdfSubmissionViewer({
  artifactUrl,
  expectedText,
  rubric,
  studentDisplayName,
  suggestions,
}: {
  artifactUrl: string
  expectedText: string
  rubric: Rubric
  studentDisplayName: string
  suggestions: SuggestedMatch[]
}) {
  const [pages, setPages] = useState<PageModel[]>([])
  const [error, setError] = useState(false)
  const [textAligned, setTextAligned] = useState(true)

  useEffect(() => {
    const loadingTask = getDocument({ url: artifactUrl })
    let active = true
    void loadingTask.promise
      .then(buildPageModels)
      .then(({ models, normalizedText }) => {
        if (active) {
          setPages(models)
          setTextAligned(normalizedText === expectedText)
        }
      })
      .catch(() => {
        if (active) setError(true)
      })
    return () => {
      active = false
      void loadingTask.destroy()
    }
  }, [artifactUrl, expectedText])

  if (error)
    return (
      <div
        className="grid min-h-[34rem] place-items-center border border-slate-300 bg-white p-8 text-center text-sm text-slate-600"
        role="alert"
      >
        The PDF could not be rendered here. Use Open PDF to view the original.
      </div>
    )
  if (pages.length === 0)
    return (
      <div className="grid min-h-[34rem] place-items-center border border-slate-300 bg-white font-mono text-[10px] uppercase tracking-[0.14em] text-slate-500">
        Rendering {studentDisplayName}&apos;s PDF…
      </div>
    )

  return (
    <div
      className="h-[calc(100vh-9.5rem)] min-h-[34rem] overflow-auto"
      aria-label={`${studentDisplayName} submission PDF`}
    >
      {!textAligned && suggestions.length > 0 ? (
        <p
          className="sticky top-0 z-10 border border-amber-300 bg-amber-50 px-4 py-3 text-xs font-semibold text-amber-900"
          role="status"
        >
          Highlights are hidden because this PDF no longer matches its extracted
          text.
        </p>
      ) : null}
      <div className="space-y-4 py-1">
        {pages.map((model) => (
          <PdfPage
            key={model.pageNumber}
            model={model}
            rubric={rubric}
            suggestions={textAligned ? suggestions : []}
          />
        ))}
      </div>
    </div>
  )
}
