"use client";

import type { ReactNode } from "react";
import { useAuth } from "@/hooks/useAuth";

/** Render no protected content until the refresh cookie has been checked. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, isLoading, open } = useAuth();

  if (isLoading) return <main id="main-content" className="page-content">
    <p role="status">Loading your account…</p>
  </main>;

  if (!user) return <main id="main-content" className="access-gate">
    <p className="eyebrow">Your LaborLens account</p>
    <h1>Log in to explore the data.</h1>
    <p>Access employment trends and policy insights in one place.</p>
    <button className="button primary" onClick={() => open("login")}>Log in</button>
  </main>;

  return children;
}
