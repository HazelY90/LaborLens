import type { Dataset, Policies, Trend } from "./apiTypes";

export function filterLabel(dataset: Dataset, filters: Record<string, string>) {
  return dataset.dimensions.map((dimension) => `${dimension.label}: ${dimension.values.find((value) =>
    value.code === filters[dimension.key])?.label ?? filters[dimension.key] ?? "Overall"}`).join(" · ");
}

/** Null slots stay on the axis; only finite observations generate marks. */
export function observed(points: Trend["points"]) {
  return points.flatMap((point, index) => point.value !== null && Number.isFinite(point.value)
    ? [{ ...point, value: point.value, index }] : []);
}
export function groupPolicies(items: Policies["items"]) {
  const groups = new Map<string, { start: number; end: number; source: Policies["items"][number]["source"]; items: Policies["items"] }>();
  for (const item of items) {
    const key = `${item.periodStart}:${item.periodEnd}:${item.source.id}`;
    if (!groups.has(key)) groups.set(key, { start: item.periodStart, end: item.periodEnd, source: item.source, items: [] });
    groups.get(key)!.items.push(item);
  }
  return [...groups.values()].sort((a, b) => a.start - b.start || a.end - b.end || a.source.id - b.source.id);
}
export function sourceUrl(url: string) {
  try { const parsed = new URL(url); return ["https:", "http:"].includes(parsed.protocol) ? parsed.href : undefined; }
  catch { return undefined; }
}
