/**
 * HTTP client for the Grading API.
 *
 * Everything the app sends goes through here so three things are guaranteed in one place: the
 * access token is attached (Requirement 18.3), a 401 raises the re-authentication prompt without
 * discarding unsaved work (Requirement 18.10), and a structured error body becomes a typed
 * `ApiError` the UI can branch on.
 */

import type { ApiErrorBody } from "../types";
import { devModeEmail, getAccessToken, notifyUnauthorized } from "../auth/authSession";

/**
 * Relative by default so requests are same-origin: in development the Vite proxy forwards `/api`,
 * and in deployment the load balancer routes it. Set VITE_API_BASE_URL only to point at an API on
 * another origin.
 */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

/** Client-side ceiling on a request. Above the API's own 30s budget so the server wins. */
const REQUEST_TIMEOUT_MS = 45_000;

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: Record<string, unknown>;

  constructor(status: number, code: string, message: string, details: Record<string, unknown> = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.details = details;
  }

  /** True when the request failed because the token is absent, expired, or invalid. */
  get isUnauthorized(): boolean {
    return this.status === 401;
  }

  /** True when retrying could plausibly succeed: a timeout, a 5xx, or a network failure. */
  get isRetryable(): boolean {
    return this.status === 0 || this.status === 504 || this.status >= 500;
  }
}

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  body?: unknown;
  /** Overrides the default timeout for a call with a longer server-side budget. */
  timeoutMs?: number;
  signal?: AbortSignal;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, timeoutMs = REQUEST_TIMEOUT_MS, signal } = options;

  const headers: Record<string, string> = { Accept: "application/json" };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  const token = await getAccessToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  } else {
    const email = devModeEmail();
    if (email) {
      headers["X-Dev-Ta-Email"] = email;
    }
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  // Honour a caller's own cancellation (React Query unmounts) alongside the timeout.
  signal?.addEventListener("abort", () => controller.abort(), { once: true });

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}/api${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    });
  } catch (cause) {
    clearTimeout(timeout);
    if (controller.signal.aborted && !signal?.aborted) {
      throw new ApiError(504, "REQUEST_TIMEOUT", "The request timed out. Nothing was changed.");
    }
    throw new ApiError(0, "NETWORK_ERROR", "The server could not be reached. Nothing was changed.");
  }
  clearTimeout(timeout);

  if (response.status === 401) {
    // Surfaces a re-auth prompt rather than a redirect, so an open marking view keeps its edits.
    notifyUnauthorized();
    const parsed = await readErrorBody(response);
    throw new ApiError(401, parsed.code, parsed.message, parsed.details);
  }

  if (!response.ok) {
    const parsed = await readErrorBody(response);
    throw new ApiError(response.status, parsed.code, parsed.message, parsed.details);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  if (text.length === 0) {
    return undefined as T;
  }
  return JSON.parse(text) as T;
}

/** Reads the structured error envelope, falling back to the status when the body is not one. */
async function readErrorBody(response: Response): Promise<{
  code: string;
  message: string;
  details: Record<string, unknown>;
}> {
  try {
    const parsed = (await response.json()) as Partial<ApiErrorBody>;
    if (parsed?.error?.message) {
      return {
        code: parsed.error.code ?? `HTTP_${response.status}`,
        message: parsed.error.message,
        details: (parsed.error.details as Record<string, unknown>) ?? {},
      };
    }
  } catch {
    // Not JSON: a proxy error page or an empty body. Fall through to the generic message.
  }
  return {
    code: `HTTP_${response.status}`,
    message: `The request failed with status ${response.status}.`,
    details: {},
  };
}

export const api = {
  get: <T>(path: string, options?: RequestOptions) =>
    request<T>(path, { ...options, method: "GET" }),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, method: "POST", body }),
  put: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, method: "PUT", body }),
  delete: <T>(path: string, options?: RequestOptions) =>
    request<T>(path, { ...options, method: "DELETE" }),
};

export default api;
