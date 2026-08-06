import { createServer } from "node:http"

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
  response.setHeader("Content-Type", "application/json")
  if (request.headers.authorization !== "Bearer canvas-pat") {
    response
      .writeHead(401)
      .end(JSON.stringify({ errors: [{ message: "Invalid token" }] }))
    return
  }

  const path = new URL(request.url ?? "/", "http://canvas.test").pathname
  const responses = new Map([
    ["/api/v1/users/self/profile", { id: 7, name: "Ada TA" }],
    [
      "/api/v1/courses",
      [{ id: 42, name: "CPSC 310", course_code: "CPSC 310" }],
    ],
    ["/api/v1/courses/42/assignments", [assignment]],
    ["/api/v1/courses/42/assignments/99", assignment],
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
