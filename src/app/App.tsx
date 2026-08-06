import { BrowserRouter, Routes, Route, Navigate } from "react-router"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { useState } from "react"
import {
  SessionListPage,
  SessionSetupPage,
  MarkingPage,
  ReviewPage,
} from "./routes"

export default function App() {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            retry: 1,
          },
        },
      }),
  )

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/sessions" element={<SessionListPage />} />
          <Route path="/sessions/:id/setup" element={<SessionSetupPage />} />
          <Route path="/sessions/:id/mark" element={<MarkingPage />} />
          <Route
            path="/sessions/:id/mark/:submissionId"
            element={<MarkingPage />}
          />
          <Route path="/sessions/:id/review" element={<ReviewPage />} />
          <Route path="*" element={<Navigate to="/sessions" replace />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
