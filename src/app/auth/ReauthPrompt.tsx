import { useState } from "react";
import { useAuth } from "./AuthProvider";

/**
 * Re-authentication prompt shown when the API rejects a token while the app is open
 * (Requirement 18.10).
 *
 * Rendered as an overlay rather than a redirect, and it never unmounts the page beneath it, so every
 * unsaved edit in the marking view survives the interruption and the TA can retry the save
 * immediately afterwards.
 */
export default function ReauthPrompt() {
  const { reauthRequired, usesCognito, signIn, refresh, signOut } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (!reauthRequired) {
    return null;
  }

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (usesCognito) {
        await signIn(email, password);
      } else {
        await refresh();
      }
      setPassword("");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Sign-in failed.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="reauth-title"
    >
      <div className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
        <h2 id="reauth-title" className="text-lg font-semibold text-slate-900">
          Your session expired
        </h2>
        <p className="mt-2 text-sm text-slate-600">
          Sign in again to continue. Your unsaved changes on this page are still here and nothing has
          been discarded.
        </p>

        <form className="mt-4 space-y-3" onSubmit={handleSubmit}>
          {usesCognito && (
            <>
              <div>
                <label htmlFor="reauth-email" className="block text-sm font-medium text-slate-700">
                  Email
                </label>
                <input
                  id="reauth-email"
                  type="email"
                  autoComplete="username"
                  required
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm"
                />
              </div>
              <div>
                <label
                  htmlFor="reauth-password"
                  className="block text-sm font-medium text-slate-700"
                >
                  Password
                </label>
                <input
                  id="reauth-password"
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

          {error && (
            <p className="text-sm text-red-700" role="alert">
              {error}
            </p>
          )}

          <div className="flex items-center gap-2 pt-1">
            <button
              type="submit"
              disabled={busy}
              className="rounded bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
            >
              {busy ? "Signing in..." : "Sign in and continue"}
            </button>
            <button
              type="button"
              onClick={() => void signOut()}
              className="rounded border border-slate-300 px-4 py-2 text-sm text-slate-700"
            >
              Discard and sign out
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
