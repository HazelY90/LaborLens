"use client";

import { useCallback, useState } from "react";
import { Bar, BarChart, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { ChartTooltip } from "./ChartTooltip";
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
  const load = useCallback(
    (signal: AbortSignal) => {
      if (dataset.id === "monthly-unemployment-rate") return Promise.reject(new Error("Monthly data supports trends only."));

      return getComparison(dataset.id, year, isQuarterly ? quarter : undefined, signal);
    },
    [dataset.id, year, quarter, isQuarterly]
  );
  const { data, error, retry } = useResource(load);

  return <>
    <div className="period-controls content-card">
      <label className="filter-field">Year<select value={year} onChange={(event) => setYear(Number(event.target.value))}>{dataset.years.map((year) => <option key={year}>{year}</option>)}</select>
      </label>
      {isQuarterly
        && <label className="filter-field">Quarter<select
          value={quarter}
          onChange={(event) => setQuarter(Number(event.target.value) as Quarter)}
        >{dataset.quarters.map((quarter) => <option key={quarter} value={quarter}>Q{quarter}</option>)}</select>
        </label>}
      <p className="small muted">Each chart varies one dimension. All other dimensions use overall values.</p>
    </div>
    {!data
      ? <RequestState error={error} retry={retry} />
      : <div className="comparison-grid">{data.charts.map((chart) => {
        const dimension = dataset.dimensions.find((item) => item.key === chart.dimension)!;
        const rows = chart.bars.map((bar) => ({
          ...bar,
          label: dimension.values.find((value) => value.code === bar.code)?.label ?? bar.code,
        }));
        const max = Math.max(1, ...chart.bars.map((bar) => bar.value ?? 0)) * 1.1;

        return <section className="content-card" key={chart.dimension}>
          <div className="section-heading">
            <h2>{dimension.label}</h2>
            <span className="unit">{data.period} · {data.unit}</span>
          </div>
          <p className="small muted">{filterLabel(
            {
              ...dataset,
              dimensions: dataset.dimensions.filter((item) => item.key !== chart.dimension)
            },
            chart.fixedFilters
          )}</p>
          <div
            className="comparison-chart"
            role="region"
            aria-label={`${dimension.label} comparison`}
          >
            <ResponsiveContainer width="100%" height={280} minWidth={0}>
              <BarChart
                data={rows}
                margin={{ top: 30, right: 16, bottom: 0, left: 16 }}
                accessibilityLayer
              >
                {/* Hide the value axis while retaining a consistent scale. */}
                <YAxis domain={[0, max]} hide />
                <XAxis dataKey="label" tick={false} tickLine={false} height={8} />
                <Tooltip
                  shared={false}
                  filterNull={false}
                  content={(props) => <ChartTooltip {...props} period={data.period} unit={data.unit} />}
                />
                <Bar
                  dataKey="value"
                  fill={colors.charts.bar}
                  maxBarSize={65}
                  radius={[5, 5, 0, 0]}
                  minPointSize={2}
                  isAnimationActive={false}
                >
                  <LabelList
                    dataKey="value"
                    position="top"
                    fontSize={11}
                    formatter={(value) => value == null ? "" : Number(value).toLocaleString("en-IE")}
                  />
                </Bar>
              </BarChart>
            </ResponsiveContainer>
            {/* Match the chart's category bands and let labels grow vertically. */}
            <div
              className="comparison-labels"
              style={{ gridTemplateColumns: `repeat(${Math.max(rows.length, 1)}, minmax(0, 1fr))` }}
            >
              {rows.map((row) => <div className="chart-category" key={row.code}>{row.label}</div>)}
            </div>
          </div>
          {rows.some((row) => row.value === null)
            && <p className="small muted">Data unavailable: {rows.filter((row) => row.value === null).map((row) => row.label).join(", ")}</p>}
          <details className="data-table">
            <summary>View data table</summary>
            <div className="table-scroll">
              <table>
                <caption>{dimension.label} ({data.unit})</caption>
                <thead>
                  <tr>
                    <th scope="col">Category</th>
                    <th scope="col">Value</th>
                  </tr>
                </thead>
                <tbody>{rows.map((row) => <tr key={row.code}>
                  <th scope="row">{row.label}</th>
                  <td>{row.value?.toLocaleString("en-IE") ?? "Data unavailable"}</td>
                </tr>)}</tbody>
              </table>
            </div>
          </details>
        </section>;
      })}</div>}
  </>;
}
