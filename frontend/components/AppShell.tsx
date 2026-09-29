"use client";
import type { ReactNode } from "react";
import { AuthProvider } from "@/contexts/AuthContext";
import { useAuth } from "@/hooks/useAuth";
import { Header } from "./Header";
import { AuthDialog } from "@/features/auth/AuthDialog";

function Content({ children }: { children: ReactNode }) {
  const { error, notice, view, restore } = useAuth();
  return <><a className="skip-link" href="#main-content">Skip to content</a><Header />
    {error && <div className="site-message error" role="alert">{error} <button className="text-button" onClick={() => void restore()}>Try again</button></div>}
    {notice && !view && <div className="site-message" role="status">{notice}</div>}
    {children}<AuthDialog /></>;
}
export function AppShell({ children }: { children: ReactNode }) {
  return <AuthProvider><Content>{children}</Content></AuthProvider>;
}
