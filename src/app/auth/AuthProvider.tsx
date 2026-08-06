/**
 * Authentication state for the SPA (task 7.1, Requirements 18.1, 18.3, 18.8, 18.10).
 *
 * Wraps `@aws-amplify/auth` when a Cognito user pool is configured at build time, and otherwise runs
 * in dev mode where the API authenticates from an email header. Which mode is active is decided by
 * the presence of the pool configuration, not by a runtime toggle, so a production build cannot fall
 * back to the unauthenticated path.
 */

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useQueryClient } from "@tanstack/react-query";
import { setTokenProvider, setUnauthorizedHandler } from "./authSession";
import { getMe } from "../api/endpoints";
import { ApiError } from "../api/client";
import type { Me } from "../types";

/** Cognito settings, injected at build time. Absent means dev mode. */
const COGNITO_USER_POOL_ID = import.meta.env.VITE_COGNITO_USER_POOL_ID ?? "";
const COGNITO_CLIENT_ID = import.meta.env.VITE_COGNITO_CLIENT_ID ?? "";

export const cognitoConfigured =
  COGNITO_USER_POOL_ID.length > 0 && COGNITO_CLIENT_ID.length > 0;

interface AuthContextValue {
  /** Null until the API confirms the credentials by answering `/api/me`. */
  me: Me | null;
  status: "loading" | "signed-out" | "signed-in";
  /** Set when the API rejected the token while the app was open (Requirement 18.10). */
  reauthRequired: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
  /** Re-checks credentials after the user resolves a re-auth prompt. */
  refresh: () => Promise<void>;
  usesCognito: boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside an AuthProvider");
  }
  return context;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [me, setMe] = useState<Me | null>(null);
  const [status, setStatus] = useState<AuthContextValue["status"]>("loading");
  const [reauthRequired, setReauthRequired] = useState(false);

  // Held in a ref so the token provider installed once below always reads the current value.
  const amplifyRef = useRef<typeof import("aws-amplify/auth") | null>(null);

  /** Loads Amplify lazily: a dev-mode build should not pay for the SDK at all. */
  const loadAmplify = useCallback(async () => {
    if (!cognitoConfigured) {
      return null;
    }
    if (!amplifyRef.current) {
      const { Amplify } = await import("aws-amplify");
      Amplify.configure({
        Auth: {
          Cognito: {
            userPoolId: COGNITO_USER_POOL_ID,
            userPoolClientId: COGNITO_CLIENT_ID,
          },
        },
      });
      amplifyRef.current = await import("aws-amplify/auth");
    }
    return amplifyRef.current;
  }, []);

  // Publish the token accessor to the API client. Amplify refreshes an expiring token inside
  // fetchAuthSession, so the client gets a valid token without any refresh logic of its own.
  useEffect(() => {
    setTokenProvider(async () => {
      const auth = await loadAmplify();
      if (!auth) {
        return null;
      }
      const session = await auth.fetchAuthSession();
      return session.tokens?.accessToken?.toString() ?? null;
    });
  }, [loadAmplify]);

  // A 401 raises a prompt instead of navigating away, so an open marking view keeps its edits.
  useEffect(() => {
    setUnauthorizedHandler(() => setReauthRequired(true));
    return () => setUnauthorizedHandler(null);
  }, []);

  const verify = useCallback(async () => {
    try {
      const identity = await getMe();
      setMe(identity);
      setStatus("signed-in");
      setReauthRequired(false);
    } catch (error) {
      if (error instanceof ApiError && error.isUnauthorized) {
        setMe(null);
        setStatus("signed-out");
        return;
      }
      // A network or server failure is not a sign-out. Staying in "loading" would hide the app
      // behind a spinner forever, so the login screen is shown with the failure visible there.
      setMe(null);
      setStatus("signed-out");
    }
  }, []);

  useEffect(() => {
    void verify();
  }, [verify]);

  const signIn = useCallback(
    async (email: string, password: string) => {
      const auth = await loadAmplify();
      if (!auth) {
        // Dev mode: the API accepts the email header, so there is nothing to authenticate against.
        await verify();
        return;
      }
      const result = await auth.signIn({ username: email, password });
      if (!result.isSignedIn) {
        throw new Error(
          `Additional sign-in steps are required: ${result.nextStep.signInStep}. ` +
            "Complete them in the Cognito hosted UI."
        );
      }
      await verify();
    },
    [loadAmplify, verify]
  );

  const signOut = useCallback(async () => {
    const auth = await loadAmplify();
    // Requirement 18.8: the token and every cached grading record leave memory. Clearing the query
    // cache is what removes the student names, feedback, and submission text the app had loaded.
    queryClient.clear();
    setMe(null);
    setStatus("signed-out");
    setReauthRequired(false);
    if (auth) {
      await auth.signOut();
    }
  }, [loadAmplify, queryClient]);

  const value = useMemo<AuthContextValue>(
    () => ({
      me,
      status,
      reauthRequired,
      signIn,
      signOut,
      refresh: verify,
      usesCognito: cognitoConfigured,
    }),
    [me, status, reauthRequired, signIn, signOut, verify]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
