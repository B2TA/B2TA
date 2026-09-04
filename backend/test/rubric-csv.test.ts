import assert from "node:assert/strict"
import { readFile } from "node:fs/promises"
import { test } from "node:test"

import { parseCanvasRubricCsv, RubricCsvError } from "../src/rubric-csv.js"

test("Canvas rubric CSV becomes a canonical preview", async () => {
  const csv = await readFile(
    new URL("../../public/templates/canvas-rubric-template.csv", import.meta.url),
    "utf8",
  )
  const preview = parseCanvasRubricCsv(csv)

  assert.equal(preview.rubricName, "Document Analysis")
  assert.equal(preview.sourceFormat, "csv")
  assert.equal(preview.criteria.length, 4)
  assert.equal(preview.criteria[0].title, "Argument and purpose")
  assert.equal(preview.criteria[0].maxPoints, 4)
  assert.deepEqual(
    preview.criteria[0].performanceLevels?.map((level) => level.label),
    ["Excellent", "Developing", "Insufficient"],
  )
})

test("Canvas rubric CSV rejects incomplete rating triplets", () => {
  assert.throws(
    () =>
      parseCanvasRubricCsv(
        "Rubric Name,Criteria Name,Criteria Description,Criteria Enable Range,Rating Name\nEssay,Thesis,,false,Strong\n",
      ),
    RubricCsvError,
  )
})
