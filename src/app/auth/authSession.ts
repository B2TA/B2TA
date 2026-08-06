/**
 * Access token source for the API client.
 *
 * The client cannot import the React auth context without creating a cycle (the context calls the
 * client to load `/api/me`), so the token is published here as a module-level accessor that the
 * provider installs on mount. Nothing else in the app reads tokens directly.
 */

type TokenProvider = () => Promise<string | null>;

/** Returns null until a provider is installed, so pre-auth calls simply go out unauthenticated. */
let tokenProvider: TokenProvider = async () => null;

/** Invoked when the API rejects a token, so the provider can prompt for re-authentication. */
let unauthorizedHandler: (() => void) | null = null;

export function setTokenProvider(provider: TokenProvider): void {
  tokenProvider = provider;
}

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler;
}

export async function getAccessToken(): Promise<string | null> {
  try {
    return await tokenProvider();
  } catch {
    // A failed refresh is indistinguishable from being signed out as far as the request is
    // concerned: send it unauthenticated and let the 401 drive the re-auth prompt.
    return null;
  }
}

export function notifyUnauthorized(): void {
  unauthorizedHandler?.();
}

/**
 * True when the API is running with `auth.dev-mode` enabled and expects an email header instead of
 * a Cognito token.
 *
 * Compiled out of a production build: `import.meta.env.DEV` is statically false there, so the
 * header is never sent by a deployed bundle even if the variable were set.
 */
export function devModeEmail(): string | null {
  if (!import.meta.env.DEV) {
    return null;
  }
  const email = import.meta.env.VITE_DEV_TA_EMAIL;
  return email && email.length > 0 ? email : null;
}
