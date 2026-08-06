import { createServer } from "node:http"

function createTextPdf(text) {
  const escapedText = text.replace(/([\\()])/g, "\\$1")
  const stream = `BT\n/F1 14 Tf\n72 720 Td\n(${escapedText}) Tj\nET`
  const objects = [
    "<< /Type /Catalog /Pages 2 0 R >>",
    "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    `<< /Length ${Buffer.byteLength(stream, "latin1")} >>\nstream\n${stream}\nendstream`,
  ]
  let content = "%PDF-1.4\n"
  const offsets = [0]
  objects.forEach((object, index) => {
    offsets.push(Buffer.byteLength(content, "latin1"))
    content += `${index + 1} 0 obj\n${object}\nendobj\n`
  })
  const xrefOffset = Buffer.byteLength(content, "latin1")
  content += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`
  content += offsets
    .slice(1)
    .map((offset) => `${String(offset).padStart(10, "0")} 00000 n \n`)
    .join("")
  content += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF\n`
  return Buffer.from(content, "latin1")
}

const submissionPdf = createTextPdf("HW1 machine-readable submission.")

const assignment = {
  id: 99,
  course_id: 42,
  name: "Argumentative essay",
  points_possible: 10,
  rubric: [
    {
      id: "criterion-1",
      description: "Thesis clarity",
      long_description: "States a focused, arguable claim.",
      points: 5,
      ratings: [
        {
          id: "level-1",
          description: "Strong",
          long_description: "Clear and specific.",
          points: 5,
        },
        {
          id: "level-2",
          description: "Developing",
          long_description: "Present but broad.",
          points: 3,
        },
      ],
    },
  ],
}

const server = createServer((request, response) => {
  if (request.headers.authorization !== "Bearer canvas-pat") {
    response.setHeader("Content-Type", "application/json")
    response
      .writeHead(401)
      .end(JSON.stringify({ errors: [{ message: "Invalid token" }] }))
    return
  }

  const path = new URL(request.url ?? "/", "http://canvas.test").pathname
  if (path === "/files/501/download") {
    response.setHeader("Content-Type", "application/pdf")
    response.end(submissionPdf)
    return
  }

  response.setHeader("Content-Type", "application/json")
  const responses = new Map([
    ["/api/v1/users/self/profile", { id: 7, name: "Ada TA" }],
    [
      "/api/v1/courses",
      [{ id: 42, name: "CPSC 310", course_code: "CPSC 310" }],
    ],
    ["/api/v1/courses/42/assignments", [assignment]],
    ["/api/v1/courses/42/assignments/99", assignment],
    [
      "/api/v1/courses/42/users",
      [
        { id: 12, name: "Alex Able", sortable_name: "Able, Alex" },
        { id: 13, name: "Blair Baker", sortable_name: "Baker, Blair" },
      ],
    ],
    [
      "/api/v1/courses/42/assignments/99/submissions",
      [
        {
          id: 812,
          user_id: 12,
          workflow_state: "submitted",
          submission_type: "online_upload",
          submitted_at: "2026-08-06T18:00:00Z",
          attempt: 1,
          attachments: [
            {
              id: 501,
              filename: "alex-hw1.pdf",
              content_type: "application/pdf",
              size: submissionPdf.length,
              url: "http://127.0.0.1:3002/files/501/download",
            },
          ],
        },
        {
          id: 813,
          user_id: 13,
          workflow_state: "unsubmitted",
          attempt: null,
        },
      ],
    ],
  ])
  const body = responses.get(path)
  if (!body) {
    response.writeHead(404).end(JSON.stringify({ error: "Not found" }))
    return
  }
  response.end(JSON.stringify(body))
})

server.listen(3002, "127.0.0.1", () => {
  console.log("Fake Canvas listening on http://127.0.0.1:3002")
})
