import type { TooltipContentProps } from "recharts";

/** Keep chart hints compact; include units while leaving series names in the external legend. */
export function ChartTooltip({ active, payload, label, period, unit }: Pick<TooltipContentProps<number, string>, "active" | "payload" | "label"> & { period?: string; unit: string }) {
  if (!active || !payload?.length) return null;

  return <div className="chart-tooltip">
    <strong>{period ?? label}</strong>
    {payload.map((
      item,
      index
    ) => <div key={index} style={{ color: item.color }}>
        {item.value == null ? "Data unavailable" : `${item.value.toLocaleString("en-IE")} ${unit}`}
      </div>)}
  </div>;
}
