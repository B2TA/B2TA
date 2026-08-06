import { Navigate, useLocation } from "react-router";
import type { ReactNode } from "react";
import { useAuth } from "./AuthProvider";

/**
 * Gates a route on a confirmed identity (Requirement 18.1).
 *
 * "Confirmed" means the API answered `/api/me`, not merely that a token exists locally: a token the
 * server rejects must not get as far as rendering a page that then fails every request.
 */
export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const location = useLocation();

  if (status === "loading") {
    return (
      <div className="flex min-h-screen items-center justify-center" role="status" aria-live="polite">
        <span className="text-sm text-slate-500">Checking your sign-in...</span>
      </div>
    );
  }

  if (status === "signed-out") {
    // Carries the attempted location so sign-in returns the TA to where they were headed.
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return <>{children}</>;
}
