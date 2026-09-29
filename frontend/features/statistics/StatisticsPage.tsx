"use client";
import { useEffect, useState } from "react";
import { useMetadata } from "@/contexts/MetadataContext";
import { TrendView } from "./TrendView";
import { ComparisonView } from "./ComparisonView";

export function StatisticsPage({ id }: { id: string }) {
  const { datasets } = useMetadata();
  const dataset = datasets.find((item) => item.id === id);
  const [view, setView] = useState("trend");
  // Keep the floating navigation aligned with the section being read.
  useEffect(() => {
    const update = () => {
      const comparison = document.getElementById("comparison");
      setView(comparison && comparison.getBoundingClientRect().top <= window.innerHeight * 0.4 ? "comparison" : "trend");
    };
    update();
    window.addEventListener("scroll", update, { passive: true });
    window.addEventListener("resize", update);
    return () => {
      window.removeEventListener("scroll", update);
      window.removeEventListener("resize", update);
    };
  }, [id]);
  if (!dataset) return <p className="alert error">This dataset is unavailable.</p>;
  return <div className="statistics-layout">
    <nav className="section-nav" aria-label="Chart sections">{dataset.views.map((item) => <a key={item}
      href={`#${item}`} aria-current={view === item ? "location" : undefined}>
      {item === "trend" ? "Trend" : "Comparison"}</a>)}</nav>
    <div className="statistics-content">
      <section id="trend" className="statistics-section" aria-label="Trend">
        <TrendView dataset={dataset} />
      </section>
      {dataset.views.includes("comparison") && <section id="comparison" className="statistics-section comparison-section" aria-labelledby="comparison-title">
        <h2 id="comparison-title" className="comparison-title">Period comparison</h2>
        <ComparisonView dataset={dataset} />
      </section>}
    </div>
  </div>;
}
