import { BrowserRouter, Navigate, Route, Routes } from "react-router";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "./auth/AuthProvider";
import ProtectedRoute from "./auth/ProtectedRoute";
import ReauthPrompt from "./auth/ReauthPrompt";
import { ApiError } from "./api/client";
import {
  LoginPage,
  MarkingPage,
  ReviewPage,
  SessionListPage,
  SessionSetupPage,
} from "./routes";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      // A 401 or 404 will not change on a second attempt, and retrying only delays the message the
      // user needs. Transient failures get one retry.
      retry: (failureCount, error) => {
        if (error instanceof ApiError && !error.isRetryable) {
          return false;
        }
        return failureCount < 1;
      },
      refetchOnWindowFocus: false,
    },
  },
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          {/* Rendered above the routes so a 401 mid-session prompts for credentials without
              unmounting the page, which is what preserves unsaved edits (Requirement 18.10). */}
          <ReauthPrompt />
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              path="/sessions"
              element={
                <ProtectedRoute>
                  <SessionListPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/sessions/:id/setup"
              element={
                <ProtectedRoute>
                  <SessionSetupPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/sessions/:id/mark"
              element={
                <ProtectedRoute>
                  <MarkingPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/sessions/:id/review"
              element={
                <ProtectedRoute>
                  <ReviewPage />
                </ProtectedRoute>
              }
            />
            <Route path="*" element={<Navigate to="/sessions" replace />} />
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
