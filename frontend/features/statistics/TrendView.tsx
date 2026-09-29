"use client";
import { useCallback, useRef, useState } from "react";
import { useResource } from "@/hooks/useResource";
import { RequestState } from "@/components/RequestState";
import { getTrend } from "@/services/dataService";
import { filterLabel, observed } from "@/utils/charts";
import { TrendChart } from "./TrendChart";
import type { Dataset } from "@/utils/apiTypes";
import colors from "@/utils/colors.json";
interface Card { id: number; color: string; filters: Record<string, string> }

export function TrendView({ dataset }: { dataset: Dataset }) {
  const nextId = useRef(1);
  const [cards, setCards] = useState<Card[]>([{ id: 0, color: colors.charts.lines[0], filters: dataset.defaults }]);
  const load = useCallback((signal: AbortSignal) => Promise.all(cards.map(async (card) => {
    try {
      const data = await getTrend(dataset.id, card.filters, signal);
      return { ...card, data, error: undefined };
    } catch (error) { return { ...card, data: undefined, error: error instanceof Error ? error.message : "Unable to load this series." }; }
  })), [cards, dataset.id]);
  const { data, error, retry } = useResource(load);
  function add() {
    if (cards.length >= 5) return;
    const color = colors.charts.lines.find((value) => !cards.some((card) => card.color === value))!;
    setCards([...cards, { id: nextId.current++, color, filters: { ...dataset.defaults } }]);
  }
  return <div className="explorer-grid"><section className="content-card chart-card"><div className="section-heading"><h2>Trends over time</h2><span className="unit">{dataset.unit}</span></div>
    <p className="muted small">Dots mark observations. Lines connect across missing periods.</p>
    {!data ? <RequestState error={error} retry={retry} /> : <>
      {data.some((line) => line.error) && <p className="alert error">Some series could not be loaded. <button className="text-button" onClick={retry}>Try again</button></p>}
      <TrendChart unit={dataset.unit} series={data.flatMap((line) => line.data ? [{ id: line.id, color: line.color, label: filterLabel(dataset, line.filters), data: line.data }] : [])} />
    </>}
  </section><aside className="filter-panel" aria-label="Series filters">
    <div className="section-heading"><h2>Compare series</h2><span>{cards.length} / 5</span></div>
    <p className="muted small">One selection per dimension. One line per card.</p>
    {/* Keep card scrolling independent of the chart and the add button. */}
    <div className="filter-scroll" role="region" aria-label="Series filter cards" tabIndex={0}>
    {cards.map((card, index) => <section className="filter-card" key={card.id} style={{ borderTopColor: card.color }}>
      <div className="section-heading"><h3><span className="color-dot" style={{ background: card.color }} />Series {index + 1}</h3>
        {cards.length > 1 && <button className="text-button small" aria-label={`Remove series ${index + 1}`} onClick={() => setCards(cards.filter((item) => item.id !== card.id))}>Remove</button>}</div>
      {dataset.dimensions.map((dimension) => <label className="filter-field" key={dimension.key}>{dimension.label}
        <select value={card.filters[dimension.key]} onChange={(event) => setCards(cards.map((item) => item.id === card.id
          ? { ...item, filters: { ...item.filters, [dimension.key]: event.target.value } } : item))}>
          {dimension.values.map((value) => <option key={value.code} value={value.code}>{value.label}</option>)}
        </select></label>)}
      {data?.find((item) => item.id === card.id)?.data && !observed(data.find((item) => item.id === card.id)!.data!.points).length && <p className="small muted">No observations for this selection.</p>}
      {data?.find((item) => item.id === card.id)?.error && <p className="alert error" role="alert">{data.find((item) => item.id === card.id)!.error}</p>}
    </section>)}
    </div>
    <button className="button add-series" disabled={cards.length >= 5} onClick={add}>+ Add series</button>
  </aside></div>;
}
