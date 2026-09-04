import { parse } from "csv-parse/sync"

import type { RubricInput } from "./store.js"

const FIXED_HEADERS = [
  "Rubric Name",
  "Criteria Name",
  "Criteria Description",
  "Criteria Enable Range",
]
const RATING_HEADERS = ["Rating Name", "Rating Description", "Rating Points"]

export type RubricCsvPreview = {
  rubricName: string
  sourceFormat: "csv"
  criteria: RubricInput["criteria"]
  warnings: string[]
}

export class RubricCsvError extends Error {}

function validateHeaders(headers: string[]): number {
  if (headers.length < 7 || headers.length > 64) {
    throw new RubricCsvError(
      "Canvas rubric CSV must contain at least one rating column group",
    )
  }
  if (FIXED_HEADERS.some((header, index) => headers[index] !== header)) {
    throw new RubricCsvError("Canvas rubric CSV headers are invalid")
  }
  const ratingColumnCount = headers.length - FIXED_HEADERS.length
  if (ratingColumnCount % RATING_HEADERS.length !== 0) {
    throw new RubricCsvError("Canvas rubric CSV rating columns are incomplete")
  }
  for (let index = 4; index < headers.length; index += 1) {
    if (headers[index] !== RATING_HEADERS[(index - 4) % 3]) {
      throw new RubricCsvError("Canvas rubric CSV rating headers are invalid")
    }
  }
  return ratingColumnCount / 3
}

export function parseCanvasRubricCsv(csv: string): RubricCsvPreview {
  if (csv.length === 0 || csv.length > 1_000_000) {
    throw new RubricCsvError(
      "Canvas rubric CSV must be between 1 byte and 1 MB",
    )
  }

  let records: string[][]
  try {
    records = (parse(csv, {
      bom: true,
      columns: false,
      relax_column_count: false,
      skip_empty_lines: true,
      trim: true,
    }) as string[][])
  } catch {
    throw new RubricCsvError("Canvas rubric CSV could not be parsed")
  }

  const [headers, ...rows] = records
  if (!headers || rows.length === 0) {
    throw new RubricCsvError("Canvas rubric CSV has no criteria")
  }
  const ratingGroups = validateHeaders(headers)
  const rubricNames = new Set(rows.map((row) => row[0]).filter(Boolean))
  if (rubricNames.size !== 1) {
    throw new RubricCsvError("Import exactly one named rubric at a time")
  }

  const warnings: string[] = []
  const criterionNames = new Set<string>()
  const criteria = rows.map((row, rowIndex) => {
    const title = row[1]?.trim()
    if (!title) {
      throw new RubricCsvError(`Criterion ${rowIndex + 1} has no name`)
    }
    const normalizedTitle = title.toLocaleLowerCase()
    if (criterionNames.has(normalizedTitle)) {
      throw new RubricCsvError(`Criterion names must be unique: ${title}`)
    }
    criterionNames.add(normalizedTitle)
    if (["true", "1"].includes(row[3]?.toLowerCase())) {
      warnings.push(
        `${title}: range scoring will be imported as discrete rating levels`,
      )
    }

    const performanceLevels = []
    for (let group = 0; group < ratingGroups; group += 1) {
      const offset = 4 + group * 3
      const label = row[offset]?.trim()
      const description = row[offset + 1]?.trim() ?? ""
      const rawPoints = row[offset + 2]?.trim()
      if (!label && !rawPoints) continue
      const parsedPoints = Number(rawPoints)
      if (!label || rawPoints === "" || !Number.isFinite(parsedPoints)) {
        throw new RubricCsvError(
          `${title}: rating ${group + 1} needs a name and numeric points`,
        )
      }
      const points = Math.round(parsedPoints * 100) / 100
      performanceLevels.push({ label, description, points })
    }
    if (performanceLevels.length === 0) {
      throw new RubricCsvError(`${title}: add at least one rating`)
    }
    return {
      title,
      description: row[2]?.trim() ?? "",
      maxPoints: Math.max(...performanceLevels.map((level) => level.points)),
      performanceLevels,
    }
  })

  return {
    rubricName: [...rubricNames][0],
    sourceFormat: "csv",
    criteria,
    warnings,
  }
}
