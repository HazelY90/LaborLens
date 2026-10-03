"use client";

import { useCallback, useState } from "react";
import { useMetadata } from "@/contexts/MetadataContext";
import { useResource } from "@/hooks/useResource";
import { getPolicies } from "@/services/dataService";
import { RequestState } from "@/components/RequestState";
import { groupPolicies, sourceUrl } from "@/utils/charts";

export function PoliciesPage() {
  const { policyTypes } = useMetadata();
  const [type, setType] = useState(policyTypes[0]?.code);
  const load = useCallback((signal: AbortSignal) => getPolicies(type, signal), [type]);
  const { data, error, retry } = useResource(load);
  const groups = groupPolicies(data?.items ?? []);
  // List each source once below the timeline for the selected category.
  const sources = [...new Map(groups.map((group) => [group.source.id, group.source])).values()];

  return <>
    <div className="view-tabs policy-tabs" aria-label="Policy categories">{policyTypes.map((item) => <button
      key={item.code}
      className={type === item.code ? "is-active" : ""}
      aria-pressed={type === item.code}
      onClick={() => setType(item.code)}
    >{item.label}</button>)}</div>
    <p className="muted small policy-note">Dates indicate source strategy periods, not policy publication or implementation dates.</p>
    {!data
      ? <RequestState error={error} retry={retry} />
      : groups.length === 0
        ? <div className="content-card empty-state">No policies are available in this category.</div>
        :
        <div className="policy-timeline">{groups.map((group) => <section
          className="timeline-group"
          key={`${group.start}:${group.end}:${group.source.id}`}
        >
          <div className="timeline-period">
            <span className="timeline-dot" />
            <p className="small muted">Strategy period</p>
            <h2>{group.start}–{group.end}</h2>
          </div>
          <div className="timeline-policies">
            {group.items.map((item) => <article className="content-card policy-card" key={item.id}>
              <p>{item.policy}</p>
            </article>)}
          </div>
        </section>)}</div>}
    {sources.length > 0
      && <section className="content-card policy-sources" aria-labelledby="sources-title">
        <h2 id="sources-title">Source files</h2>
        <ul>{sources.map((source) => <li key={source.id}>{sourceUrl(source.url)
          ?
          <a href={sourceUrl(source.url)} target="_blank" rel="noreferrer">{source.fileName} ↗</a>
          : source.fileName}</li>)}</ul>
      </section>}
  </>;
}
