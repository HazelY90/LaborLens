"use client";

import type { ReactNode } from "react";
import { AuthProvider } from "@/contexts/AuthContext";
import { useAuth } from "@/hooks/useAuth";
import { Header } from "./Header";
import { AuthDialog } from "@/features/auth/AuthDialog";

function Content({ children }: { children: ReactNode }) {
  // Session restoration runs silently; explicit account actions retain their feedback.
  const { notice, view } = useAuth();

  return <>
    <a className="skip-link" href="#main-content">Skip to content</a>
    <Header />
    {notice && !view && <div className="site-message" role="status">{notice}</div>}
    {children}
    <AuthDialog />
  </>;
}

export function AppShell({ children }: { children: ReactNode }) {
  return <AuthProvider>
    <Content>{children}</Content>
  </AuthProvider>;
}
