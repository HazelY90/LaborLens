"use client";

import { createContext, useCallback, useEffect, useState, useSyncExternalStore, type ReactNode } from "react";
import { apiClient, ApiError } from "@/services/apiClient";
import * as auth from "@/services/authService";
import type { UserProfile } from "@/utils/apiTypes";

export type AuthView = "login" | "register" | "username" | "password" | "delete";

interface AuthState {
  user: UserProfile | null;
  isLoading: boolean;
  error: string;
  notice: string;
  view: AuthView | null;
  open: (view: AuthView) => void;
  close: () => void;
  notify: (message: string) => void;
  restore: () => Promise<void>;
}

export const AuthContext = createContext<AuthState | null>(null);

const serverSession = () => null;

/** The API client owns credentials; React only subscribes to session changes. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const session = useSyncExternalStore(apiClient.subscribe, apiClient.getSession, serverSession);
  const [isLoading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, notify] = useState("");
  const [view, setView] = useState<AuthView | null>(null);
  const restore = useCallback(
    () => auth.refresh().then(() => {
      setError("");
    }).catch((error: unknown) => {
      if (!(error instanceof ApiError && error.status === 401)) {
        setError("We could not restore your session. Please try again.");
      }
    }).finally(() => setLoading(false)),
    []
  );
  useEffect(() => {
    void restore();
  }, [restore]);
  const open = (next: AuthView) => {
    notify("");
    setView(next);
  };

  return <AuthContext.Provider value={{
    user: session?.user ?? null,
    isLoading,
    error,
    notice,
    view,
    open,
    close: () => setView(null),
    notify,
    restore: async () => {
      setLoading(true);
      setError("");
      await restore();
    }
  }}>{children}</AuthContext.Provider>;
}
