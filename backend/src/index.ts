import { createApp } from "./app.js"

const host = process.env.API_HOST ?? "0.0.0.0"
const port = Number.parseInt(process.env.API_PORT ?? "3001", 10)

createApp().listen(port, host, () => {
  console.log(`B2TA API listening on http://${host}:${port}`)
})
