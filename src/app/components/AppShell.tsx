import type { ReactNode } from "react";
import { Link } from "react-router";
import { useAuth } from "../auth/AuthProvider";

/** Page frame: title bar, the signed-in TA, and a sign-out control. */
export default function AppShell({
  title,
  subtitle,
  actions,
  children,
}: {
  title: string;
  subtitle?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
}) {
  const { me, signOut, usesCognito } = useAuth();

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-4 px-6 py-3">
          <Link to="/sessions" className="text-sm font-semibold text-slate-900">
            Grading Assistant
          </Link>
          <div className="min-w-0 flex-1">
            <h1 className="truncate text-base font-semibold text-slate-900">{title}</h1>
            {subtitle && <div className="truncate text-xs text-slate-500">{subtitle}</div>}
          </div>
          {actions}
          <div className="flex items-center gap-3 text-xs text-slate-500">
            {me && <span className="hidden sm:inline">{me.email}</span>}
            {!usesCognito && (
              <span className="rounded border border-amber-300 bg-amber-50 px-2 py-0.5 text-amber-800">
                dev auth
              </span>
            )}
            <button
              type="button"
              onClick={() => void signOut()}
              className="rounded border border-slate-300 px-2 py-1 text-slate-700"
            >
              Sign out
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-7xl px-6 py-6">{children}</main>
    </div>
  );
}
