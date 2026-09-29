"use client";
import { useState } from "react";
import { observed } from "@/utils/charts";
import type { Trend } from "@/utils/apiTypes";
export interface ChartSeries { id: number; color: string; label: string; data: Trend }

/** SVG keeps every period slot while connecting only observed points. */
export function TrendChart({ series, unit }: { series: ChartSeries[]; unit: string }) {
  const [tip, setTip] = useState("");
  const periods = [...new Set(series.flatMap((line) => line.data.points.map((point) => point.period)))].sort();
  const values = series.flatMap((line) => observed(line.data.points).map((point) => point.value));
  if (!values.length) return <p className="empty-state">No observations are available for these selections.</p>;
  const width = Math.max(760, periods.length * 30 + 90);
  const height = 370;
  const max = Math.max(...values, 1) * 1.1;
  const x = (period: string) => 65 + periods.indexOf(period) / Math.max(periods.length - 1, 1) * (width - 100);
  const y = (value: number) => 285 - value / max * 250;
  return <>
    <div className="chart-scroll" tabIndex={0} role="region" aria-label="Trend chart. Scroll horizontally to view all periods.">
      <svg width={width} height={height} role="group" aria-label={`Trend in ${unit}`}>
        {Array.from({ length: 6 }, (_, index) => max * index / 5).map((value) => <g key={value}>
          <line x1="65" x2={width - 35} y1={y(value)} y2={y(value)} stroke="#e4eaf0" />
          <text x="55" y={y(value) + 4} textAnchor="end" className="axis-label">{value.toFixed(1)}</text>
        </g>)}
        {periods.map((period) => <text key={period} transform={`translate(${x(period)},305) rotate(-50)`} textAnchor="end" className="axis-label">{period}</text>)}
        {series.map((line) => {
          const points = observed(line.data.points);
          return <g key={line.id}>
            <polyline points={points.map((point) => `${x(point.period)},${y(point.value)}`).join(" ")} fill="none" stroke={line.color} strokeWidth="2.5" />
            {points.map((point) => {
              const label = `${point.period} — ${line.label} — ${point.value.toLocaleString("en-IE")} ${unit}`;
              return <circle key={point.period} cx={x(point.period)} cy={y(point.value)} r="4" fill={line.color} stroke="white" strokeWidth="1.3"
                tabIndex={0} role="img" aria-label={label} onFocus={() => setTip(label)} onBlur={() => setTip("")}
                onMouseEnter={() => setTip(label)} onMouseLeave={() => setTip("")}><title>{label}</title></circle>;
            })}
          </g>;
        })}
      </svg>
    </div>
    <p className="chart-tip" role="status">{tip || "Hover over or focus a point to see its period and value."}</p>
    <ul className="chart-legend">{series.map((line) => <li key={line.id}><span className="color-dot" style={{ background: line.color }} />{line.label}</li>)}</ul>
    <details className="data-table"><summary>View data table</summary><div className="table-scroll"><table><caption>Trend observations ({unit})</caption>
      <thead><tr><th scope="col">Period</th>{series.map((line) => <th scope="col" key={line.id}>{line.label}</th>)}</tr></thead>
      <tbody>{periods.map((period) => <tr key={period}><th scope="row">{period}</th>{series.map((line) => <td key={line.id}>{line.data.points.find((point) => point.period === period)?.value ?? "Data unavailable"}</td>)}</tr>)}</tbody>
    </table></div></details>
  </>;
}
