import { apiClient } from "./apiClient";
import type {
  AnnualFilters, MonthlyFilters, QuarterlyFilters, CountFilters, Quarter,
  Metadata, Trend, Comparison, Policies, PolicyType, DatasetId,
} from "../utils/apiTypes";

/** Metadata-driven controls use the same endpoints as the typed page helpers. */
export function getTrend(
  id: DatasetId,
  filters: Record<string, string>,
  signal?: AbortSignal
) {
  return apiClient.request<Trend>(`/data/${id}`, { query: { ...filters, view: "trend" }, signal });
}

export function getComparison(
  id: Exclude<DatasetId, "monthly-unemployment-rate">,
  year: number,
  quarter?: Quarter,
  signal?: AbortSignal
) {
  return apiClient.request<Comparison>(`/data/${id}`, { query: { view: "comparison", year, quarter }, signal });
}

/** Fetch the catalog used to populate dataset and policy controls. */
export function getMetadata(signal?: AbortSignal) {
  return apiClient.request<Metadata>("/data/metadata", { signal });
}

export function getAnnualTrend(
  filters: AnnualFilters = {},
  signal?: AbortSignal
) {
  return apiClient.request<Trend>("/data/annual-employment-rate", { query: { ...filters, view: "trend" }, signal });
}

export function getAnnualComparison(
  year: number,
  signal?: AbortSignal
) {
  return apiClient.request<Comparison>("/data/annual-employment-rate", { query: { view: "comparison", year }, signal });
}

export function getMonthlyTrend(
  filters: MonthlyFilters = {},
  signal?: AbortSignal
) {
  return apiClient.request<Trend>("/data/monthly-unemployment-rate", { query: { ...filters, view: "trend" }, signal });
}

export function getQuarterlyTrend(
  filters: QuarterlyFilters = {},
  signal?: AbortSignal
) {
  return apiClient.request<Trend>("/data/quarterly-employment-rate", { query: { ...filters, view: "trend" }, signal });
}

export function getQuarterlyComparison(
  year: number,
  quarter: Quarter,
  signal?: AbortSignal
) {
  return apiClient.request<Comparison>(
    "/data/quarterly-employment-rate",
    { query: { view: "comparison", year, quarter }, signal }
  );
}

export function getCountTrend(
  filters: CountFilters = {},
  signal?: AbortSignal
) {
  return apiClient.request<Trend>("/data/quarterly-employment-count", { query: { ...filters, view: "trend" }, signal });
}

export function getCountComparison(
  year: number,
  quarter: Quarter,
  signal?: AbortSignal
) {
  return apiClient.request<Comparison>(
    "/data/quarterly-employment-count",
    { query: { view: "comparison", year, quarter }, signal }
  );
}

export function getPolicies(
  type: PolicyType,
  signal?: AbortSignal
) {
  return apiClient.request<Policies>("/data/policies", { query: { type }, signal });
}
