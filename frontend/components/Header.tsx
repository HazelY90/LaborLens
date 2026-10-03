"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useAuth } from "@/hooks/useAuth";
import { logout } from "@/services/authService";
import { pages } from "@/utils/pages";

/** One header is rendered by the root layout across public and protected routes. */
export function Header() {
  const { user, isLoading, open, notify } = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const menu = useRef<HTMLDetailsElement>(null);
  const [isBusy, setBusy] = useState(false);
  useEffect(
    () => {
      function outside(event: PointerEvent) {
        if (menu.current && !menu.current.contains(event.target as Node)) menu.current.open = false;
      }

      function escape(event: KeyboardEvent) {
        if (event.key === "Escape" && menu.current?.open) {
          menu.current.open = false;
          menu.current.querySelector("summary")?.focus();
        }
      }
      document.addEventListener("pointerdown", outside);
      document.addEventListener("keydown", escape);

      return () => {
        document.removeEventListener("pointerdown", outside);
        document.removeEventListener("keydown", escape);
      };
    },
    []
  );

  async function signOut() {
    setBusy(true);

    if (menu.current) menu.current.open = false;
    try {
      await logout();
      notify("You have been logged out.");
    }
    catch {
      notify("Logged out locally, but the server could not confirm logout. Please log in and try again to end all sessions.");
    }
    finally {
      setBusy(false);
      router.replace("/");
    }
  }

  return <header className="site-header">
    <div className="header-inner">
      <Link href="/" className="brand" aria-label="LaborLens home">Labor<span>Lens</span>
      </Link>
      {user
        && <nav className="main-nav" aria-label="Main navigation">{pages.map((page) => <Link
          key={page.id}
          href={`/${page.id}`}
          aria-current={pathname === `/${page.id}` ? "page" : undefined}
        >
          {page.label}</Link>)}</nav>}
      <div className="header-account">{isLoading
        ? <span className="session-loading" role="status">Loading account…</span>
        : user
          ?
          <details className="account-menu" ref={menu}>
            <summary>
              <span className="avatar" aria-hidden="true">{user.username.slice(0, 1).toUpperCase()}</span>
              <span className="welcome">Welcome, {user.username}</span>
              <span aria-hidden="true">⌄</span>
            </summary>
            <div className="account-options">
              <p>{user.email}</p>
              <button onClick={() => {
                if (menu.current) menu.current.open = false;
                open("username");
              }}>Change username</button>
              <button onClick={() => {
                if (menu.current) menu.current.open = false;
                open("password");
              }}>Change password</button>
              <button disabled={isBusy} onClick={signOut}>Log out</button>
              <button
                className="delete-option"
                onClick={() => {
                  if (menu.current) menu.current.open = false;
                  open("delete");
                }}
              >Delete account</button>
            </div>
          </details>
          : <div className="header-actions">
            <button className="button outline" onClick={() => open("register")}>Sign up</button>
            <button className="button primary" onClick={() => open("login")}>Log in</button>
          </div>}</div>
    </div>
  </header>;
}
