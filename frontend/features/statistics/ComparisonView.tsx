"use client";
import { useCallback, useState } from "react";
import { getComparison } from "@/services/dataService";
import { useResource } from "@/hooks/useResource";
import { RequestState } from "@/components/RequestState";
import { filterLabel } from "@/utils/charts";
import type { Dataset, Quarter } from "@/utils/apiTypes";
import colors from "@/utils/colors.json";

export function ComparisonView({ dataset }: { dataset: Dataset }) {
  const [year, setYear] = useState(dataset.years.at(-1)!);
  const [quarter, setQuarter] = useState<Quarter>(4);
  const isQuarterly = dataset.granularity === "quarterly";
  const load = useCallback((signal: AbortSignal) => {
    if (dataset.id === "monthly-unemployment-rate") return Promise.reject(new Error("Monthly data supports trends only."));
    return getComparison(dataset.id, year, isQuarterly ? quarter : undefined, signal);
  }, [dataset.id, year, quarter, isQuarterly]);
  const { data, error, retry } = useResource(load);
  return <><div className="period-controls content-card">
    <label className="filter-field">Year<select value={year} onChange={(event) => setYear(Number(event.target.value))}>{dataset.years.map((year) => <option key={year}>{year}</option>)}</select></label>
    {isQuarterly && <label className="filter-field">Quarter<select value={quarter} onChange={(event) => setQuarter(Number(event.target.value) as Quarter)}>{dataset.quarters.map((quarter) => <option key={quarter} value={quarter}>Q{quarter}</option>)}</select></label>}
    <p className="small muted">Each chart varies one dimension. All other dimensions use overall values.</p>
  </div>
  {!data ? <RequestState error={error} retry={retry} /> : <div className="comparison-grid">{data.charts.map((chart) => {
    const dimension = dataset.dimensions.find((item) => item.key === chart.dimension)!;
    const isCompact = chart.dimension === "ageGroup" || chart.dimension === "economicSector";
    const max = Math.max(1, ...chart.bars.map((bar) => bar.value ?? 0)) * 1.1;
    return <section className="content-card" key={chart.dimension}><div className="section-heading"><h2>{dimension.label}</h2><span className="unit">{data.period} · {data.unit}</span></div>
      <p className="small muted">{filterLabel({ ...dataset, dimensions: dataset.dimensions.filter((item) => item.key !== chart.dimension) }, chart.fixedFilters)}</p>
      <div className={isCompact ? `compact-chart${chart.dimension === "economicSector" ? " sector-chart" : ""}` : "chart-scroll"} tabIndex={0} role="region" aria-label={`${dimension.label} comparison`}><div className="bar-chart" style={isCompact ? undefined : { minWidth: Math.max(380, chart.bars.length * 100) }}>
        {chart.bars.map((bar) => <div className="bar-column" key={bar.code}>
          <div className="bar-space">{bar.value === null ? <div className="missing-bar"><span>Data unavailable</span></div> :
            <div className={`bar ${bar.value === 0 ? "zero-bar" : ""}`} style={{ height: `${bar.value / max * 100}%`, background: colors.charts.bar }}>
              <span>{bar.value.toLocaleString("en-IE")}</span></div>}</div>
          <p>{dimension.values.find((value) => value.code === bar.code)?.label ?? bar.code}</p>
        </div>)}
      </div></div>
    </section>;
  })}</div>}
  </>;
}
