"use client";

import Link from "next/link";
import { useAuth } from "@/hooks/useAuth";

export function Home() {
  const { user, isLoading, open } = useAuth();

  return <main id="main-content" className="home">
    <section className="hero" aria-labelledby="hero-title">
      <p className="eyebrow">A clearer view of Ireland’s labour market</p>
      <h1 id="hero-title">Understand employment.<br />
        <span>See the bigger picture.</span>
      </h1>
      <p className="hero-description">Explore employment trends, compare communities, and discover the policies shaping work in Ireland.</p>
      <div className="hero-actions">{user
        ? <Link className="button primary" href="/annual-employment-rate">Explore employment <span aria-hidden="true">→</span>
        </Link>
        :
        <button className="button primary" disabled={isLoading} onClick={() => open("register")}>Get started <span aria-hidden="true">→</span>
        </button>}
        {!user
          && <button className="text-button" disabled={isLoading} onClick={() => open("login")}>Already a member? Log in</button>}</div>
      <p className="hero-caption">Employment · Unemployment · Policy</p>
    </section>
  </main>;
}
