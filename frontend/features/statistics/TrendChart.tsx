"use client";
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { ChartTooltip } from "./ChartTooltip";
import { observed } from "@/utils/charts";
import type { Trend } from "@/utils/apiTypes";
export interface ChartSeries { id: number; color: string; label: string; data: Trend }

/** Align series on a shared axis without dropping missing period slots. */
export function TrendChart({ series, unit }: { series: ChartSeries[]; unit: string }) {
  const periods = [...new Set(series.flatMap((line) => line.data.points.map((point) => point.period)))].sort();
  const values = series.flatMap((line) => observed(line.data.points).map((point) => point.value));
  if (!values.length) return <p className="empty-state">No observations are available for these selections.</p>;
  const width = Math.max(760, periods.length * 30 + 90);
  const max = Math.max(...values, 1) * 1.1;
  const rows = periods.map((period) => ({ period,
    ...Object.fromEntries(series.map((line) => {
      const value = line.data.points.find((point) => point.period === period)?.value;
      return [`series${line.id}`, value != null && Number.isFinite(value) ? value : null];
    })),
  }));
  return <>
    <div className="chart-scroll" tabIndex={0} role="region" aria-label={`Trend in ${unit}. Scroll horizontally to view all periods.`}>
      <div style={{ minWidth: width }}>
        <ResponsiveContainer width="100%" height={370}>
          <LineChart data={rows} margin={{ top: 25, right: 35, bottom: 10, left: 10 }} accessibilityLayer>
            <CartesianGrid stroke="#e4eaf0" vertical={false} />
            <XAxis dataKey="period" interval={0} angle={-50} textAnchor="end" height={80} tick={{ fontSize: 11 }} />
            <YAxis domain={[0, max]} tickFormatter={(value: number) => value.toFixed(1)} tick={{ fontSize: 11 }} />
            <Tooltip filterNull={false} content={(props) => <ChartTooltip {...props} unit={unit} />} />
            {series.map((line) => <Line key={line.id} dataKey={`series${line.id}`} name={line.label}
              type="linear" stroke={line.color} strokeWidth={2.5} connectNulls isAnimationActive={false}
              dot={{ r: 4, fill: line.color, stroke: "white", strokeWidth: 1.3 }} activeDot={{ r: 6 }} />)}
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
    <p className="chart-tip">Hover over the chart or focus it and use the arrow keys to explore values.</p>
    <ul className="chart-legend">{series.map((line) => <li key={line.id}><span className="color-dot" style={{ background: line.color }} />{line.label}</li>)}</ul>
    <details className="data-table"><summary>View data table</summary><div className="table-scroll"><table><caption>Trend observations ({unit})</caption>
      <thead><tr><th scope="col">Period</th>{series.map((line) => <th scope="col" key={line.id}>{line.label}</th>)}</tr></thead>
      <tbody>{periods.map((period) => <tr key={period}><th scope="row">{period}</th>{series.map((line) => <td key={line.id}>{line.data.points.find((point) => point.period === period)?.value ?? "Data unavailable"}</td>)}</tr>)}</tbody>
    </table></div></details>
  </>;
}
