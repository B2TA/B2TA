import { useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router";
import { useAuth } from "../auth/AuthProvider";
import { ErrorBanner } from "../components/Feedback";

/**
 * Sign-in screen (Requirement 18.1, 18.2).
 *
 * There is no "create account" path: Cognito accounts are created by an administrator, so offering
 * sign-up here would advertise something the user pool refuses.
 */
export default function LoginPage() {
  const { status, signIn, usesCognito, isDemoMode } = useAuth();
  const navigate = useNavigate();
  const location = useLocation() as { state?: { from?: string } };

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  if (status === "signed-in") {
    return <Navigate to={location.state?.from ?? "/sessions"} replace />;
  }

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await signIn(email, password);
      navigate(location.state?.from ?? "/sessions", { replace: true });
    } catch (cause) {
      setError(cause);
    } finally {
      setBusy(false);
    }
  };

  const handleDemoEnter = async () => {
    setBusy(true);
    setError(null);
    try {
      await signIn("demo@b2ta.dev", "");
      navigate(location.state?.from ?? "/sessions", { replace: true });
    } catch (cause) {
      setError(cause);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 p-4">
      <div className="w-full max-w-sm rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-xl font-semibold text-slate-900">Sign in</h1>
        <p className="mt-1 text-sm text-slate-500">
          Grading sessions are visible only to the teaching assistant who owns them.
        </p>

        {isDemoMode && (
          <div className="mt-4">
            <div className="rounded border border-blue-200 bg-blue-50 px-3 py-2 text-xs text-blue-900 mb-3">
              Demo Mode — No backend required. All data is simulated locally.
            </div>
            <button
              onClick={handleDemoEnter}
              disabled={busy}
              className="w-full rounded bg-blue-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50 transition-colors"
            >
              {busy ? "Entering..." : "Enter Demo"}
            </button>
          </div>
        )}

        {!isDemoMode && !usesCognito && (
          <div className="mt-4 rounded border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-900">
            No Cognito user pool is configured in this build, so the API is being asked to
            authenticate from a development header. Continue to use the local dev identity.
          </div>
        )}

        {!isDemoMode && (
          <form className="mt-4 space-y-3" onSubmit={handleSubmit}>
            {usesCognito && (
              <>
                <div>
                  <label htmlFor="email" className="block text-sm font-medium text-slate-700">
                    Email
                  </label>
                  <input
                    id="email"
                    type="email"
                    autoComplete="username"
                    required
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm"
                  />
                </div>
                <div>
                  <label htmlFor="password" className="block text-sm font-medium text-slate-700">
                    Password
                  </label>
                  <input
                    id="password"
                    type="password"
                    autoComplete="current-password"
                    required
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm"
                  />
                </div>
              </>
            )}

            <ErrorBanner error={error} />

            <button
              type="submit"
              disabled={busy}
              className="w-full rounded bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
            >
              {busy ? "Signing in..." : usesCognito ? "Sign in" : "Continue"}
            </button>
          </form>
        )}

        <ErrorBanner error={error} />

        {!isDemoMode && (
          <p className="mt-4 text-xs text-slate-500">
            Accounts are created by an administrator. Contact your course coordinator if you cannot sign
            in.
          </p>
        )}
      </div>
    </div>
  );
}
