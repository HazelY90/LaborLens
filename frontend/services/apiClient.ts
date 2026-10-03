import type { AccessResponse } from "../utils/apiTypes";

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly field: string | null = null,

  ) {
    super(message);
    this.name = "ApiError";
  }
}

export interface RequestOptions {
  method?: "GET" | "POST" | "PATCH" | "DELETE" | "PUT";
  auth?: boolean;
  query?: Record<string, string | number | boolean | null | undefined>;
  body?: unknown;
  headers?: HeadersInit;
  signal?: AbortSignal;
}

interface ClientOptions {
  baseUrl: string;
  fetcher?: typeof fetch;
  isBrowserOnly?: boolean;
}

/** Factory supports isolated tests; the exported application client is browser-only. */
export function createApiClient({ baseUrl, fetcher = fetch, isBrowserOnly = false }: ClientOptions) {
  const base = new URL(baseUrl.replace(/\/+$/, "") + "/");
  let session: AccessResponse | null = null;
  let expiresAt = 0;
  // In-flight requests must belong to the session that started them.
  let revision = 0;
  let isSignedOut = false;
  let refreshing: Promise<AccessResponse> | null = null;
  const listeners = new Set<() => void>();

  function browser() {
    if (isBrowserOnly && typeof window === "undefined") {
      throw new Error("The shared API client is browser-only. Do not use it in Server Components or Route Handlers.");
    }
  }

  function emit() {
    listeners.forEach((listener) => listener());
  }

  function clearSession() {
    browser();
    revision += 1;
    session = null;
    expiresAt = 0;
    isSignedOut = true;
    emit();
  }

  function changed() {
    return new ApiError(0, "SESSION_CHANGED", "The active session changed.");
  }

  function unauthorized() {
    return new ApiError(401, "UNAUTHENTICATED", "Please sign in to continue.");
  }

  function store(
    value: AccessResponse,
    expected: number
  ) {
    if (revision !== expected) throw changed();

    if (!value || typeof value.accessToken !== "string" || !value.accessToken
      || value.tokenType !== "Bearer"
      || !Number.isFinite(value.expiresIn)
      || value.expiresIn <= 0
      || !value.user) {
      throw new ApiError(502, "INVALID_RESPONSE", "Invalid authentication response.");
    }
    session = value;
    expiresAt = Date.now() + value.expiresIn * 1000;
    isSignedOut = false;
    emit();

    return value;
  }

  function url(
    path: string,
    query: RequestOptions["query"]
  ) {
    // Do not send credentials to arbitrary hosts or paths outside the configured API.
    if (!path.startsWith("/") || path.startsWith("//") || path.includes("\\")) throw new Error("Use an API-relative path.");
    const target = new URL(path.slice(1), base);

    if (target.origin !== base.origin || !target.pathname.startsWith(base.pathname)) throw new Error("Invalid API path.");
    for (const [key, value] of Object.entries(query ?? {})) {
      if (value !== undefined && value !== null) target.searchParams.set(key, String(value));
    }

    return target;
  }

  async function send(
    path: string,
    options: RequestOptions,
    token: string | null
  ) {
    browser();
    const headers = new Headers(options.headers);
    headers.set("Accept", "application/json");
    headers.delete("Authorization");

    if (token) headers.set("Authorization", `Bearer ${token}`);

    if (options.body !== undefined) headers.set("Content-Type", "application/json");
    const target = url(path, options.query);
    try {
      return await fetcher(
        target,
        {
          method: options.method ?? "GET",
          headers,
          body: options.body === undefined ? undefined : JSON.stringify(options.body),
          credentials: "include",
          cache: "no-store",
          signal: options.signal,
        }
      );
    } catch (error) {
      if (options.signal?.aborted || (error instanceof Error && error.name === "AbortError")) throw error;
      throw new ApiError(0, "NETWORK_ERROR", "Unable to reach the API.");
    }
  }

  async function decode<T>(response: Response): Promise<T> {
    if (response.status === 204) return undefined as T;
    const raw = await response.text();
    let body: unknown;
    try {
      body = raw ? JSON.parse(raw) : undefined;
    } catch {
      body = undefined;
    }

    if (!response.ok) {
      const data = body && typeof body === "object" ? body as Record<string, unknown> : {};
      throw new ApiError(
        response.status,
        typeof data.code === "string" ? data.code : "HTTP_ERROR",
        typeof data.message === "string" ? data.message : `Request failed (${response.status}).`,
        typeof data.field === "string" ? data.field : null
      );
    }

    if (raw && body === undefined) throw new ApiError(502, "INVALID_RESPONSE", "Expected a JSON response.");

    return body as T;
  }

  function refresh(): Promise<AccessResponse> {
    browser();

    if (isSignedOut) return Promise.reject(unauthorized());

    // Share one refresh request across callers that need a new access token.
    if (refreshing) return refreshing;
    const expected = revision;
    const task = (async () => {
      try {
        const value = await decode<AccessResponse>(await send("/auth/refresh", { method: "POST", auth: false }, null));

        return store(value, expected);
      } catch (error) {
        if (revision === expected && error instanceof ApiError && error.status === 401) clearSession();
        throw error;
      }
    })();
    refreshing = task;
    // A completed old refresh must not erase a newer pending refresh.
    void task.finally(() => {
      if (refreshing === task) refreshing = null;
    }).catch(() => { });

    return task;
  }

  async function request<T>(
    path: string,
    options: RequestOptions = {}
  ): Promise<T> {
    browser();
    options.signal?.throwIfAborted();

    if (options.auth === false) return decode<T>(await send(path, options, null));
    const expected = revision;

    if (!session || expiresAt <= Date.now()) await refresh();
    options.signal?.throwIfAborted();

    if (revision !== expected) throw changed();
    const token = session?.accessToken ?? null;
    let response = await send(path, options, token);

    if (revision !== expected) throw changed();

    if (response.status === 401) {
      // A concurrent request may already have refreshed this token.
      if (session?.accessToken === token) await refresh();
      options.signal?.throwIfAborted();

      if (revision !== expected) throw changed();
      response = await send(path, options, session?.accessToken ?? null);

      if (revision !== expected) throw changed();

      if (response.status === 401) clearSession();
    }

    return decode<T>(response);
  }

  async function startSession(action: () => Promise<AccessResponse>) {
    browser();
    const pending = refreshing;
    clearSession();
    const expected = revision;

    // Serialize cookie-changing login behind an already-running refresh.
    if (pending) await pending.catch(() => { });

    if (revision !== expected) throw changed();

    return store(await action(), expected);
  }

  async function endSession(action: () => Promise<void>) {
    browser();
    const pending = refreshing;
    clearSession();
    const expected = revision;

    if (pending) await pending.catch(() => { });

    if (revision !== expected) throw changed();
    await action();
  }

  return {
    request,
    refresh,
    clearSession,
    startSession,
    endSession,
    getSession: () => {
      browser();

      return session;
    },
    subscribe(listener: () => void) {
      browser();
      listeners.add(listener);

      return () => {
        listeners.delete(listener);
      };
    },
    updateUser(user: AccessResponse["user"]) {
      browser();

      if (session && session.user.id === user.id) {
        session = { ...session, user };
        emit();
      }
    },
  };
}

export const apiClient = createApiClient({
  baseUrl: process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080/api",
  isBrowserOnly: true,
});
