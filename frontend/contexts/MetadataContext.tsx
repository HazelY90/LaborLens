"use client";

import { createContext, useContext, type ReactNode } from "react";
import { getMetadata } from "@/services/dataService";
import { useResource } from "@/hooks/useResource";
import { RequestState } from "@/components/RequestState";
import type { Metadata } from "@/utils/apiTypes";

const MetadataContext = createContext<Metadata | null>(null);

/** Mounted only inside the authenticated layout and shared by all five pages. */
export function MetadataProvider({ children }: { children: ReactNode }) {
  const { data, error, retry } = useResource(getMetadata);

  if (!data) return <main id="main-content" className="page-content">
    <RequestState error={error} retry={retry} />
  </main>;

  return <MetadataContext.Provider value={data}>{children}</MetadataContext.Provider>;
}

export function useMetadata() {
  const metadata = useContext(MetadataContext);

  if (!metadata) throw new Error("useMetadata requires MetadataProvider.");

  return metadata;
}
