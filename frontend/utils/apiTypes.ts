/** Shared contracts for the existing Spring API. */
export interface UserProfile {
  id: number;
  email: string;
  username: string;
}
export interface AccessResponse {
  accessToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  user: UserProfile;
}
export interface LoginInput { email: string; password: string }
export interface RegisterInput extends LoginInput { username: string }
export interface PasswordInput { currentPassword: string; newPassword: string }

export type DatasetId = "annual-employment-rate" | "monthly-unemployment-rate"
  | "quarterly-employment-rate" | "quarterly-employment-count";
export type PolicyType = "ECONOMIC_MIGRATION" | "EMPLOYMENT_DEVELOPMENT"
  | "SKILLS_DEVELOPMENT" | "WORKING_CONDITIONS" | "EMPLOYMENT_INCLUSION";
export type Sex = "ALL" | "FEMALE" | "MALE";
export type AnnualAge = "ALL" | "AGE_20_24" | "AGE_25_29" | "AGE_30_34" | "AGE_35_39"
  | "AGE_40_44" | "AGE_45_49" | "AGE_50_54" | "AGE_55_59" | "AGE_60_64";
export type Education = "ALL" | "PRIMARY_LOWER_SECONDARY" | "UPPER_POST_SECONDARY" | "TERTIARY";
export type Region = "IRELAND" | "NORTHERN_WESTERN" | "SOUTHERN" | "EASTERN_MIDLAND";
export type Citizenship = "ALL" | "IRELAND" | "EXCLUDING_IRELAND" | "OUTSIDE_EU_UK";
export type EconomicSector = "ALL" | "AGRICULTURE" | "INDUSTRY_CONSTRUCTION" | "SERVICES" | "INFORMATION_COMMUNICATION";
export type Quarter = 1 | 2 | 3 | 4;
export interface AnnualFilters { ageGroup?: AnnualAge; sex?: Sex; education?: Education; region?: Region }
export interface MonthlyFilters { ageGroup?: "AGE_15_74" | "AGE_15_24" | "AGE_25_74"; sex?: Sex }
export interface QuarterlyFilters { ageGroup?: AnnualAge | "AGE_25_54"; sex?: Sex; education?: Education }
export interface CountFilters { citizenship?: Citizenship; economicSector?: EconomicSector }

export interface Option { code: string; label: string }
export interface Dimension { key: string; label: string; values: Option[] }
export interface Dataset {
  id: DatasetId;
  label: string;
  unit: "%" | "thousand persons";
  granularity: "annual" | "monthly" | "quarterly";
  views: ("trend" | "comparison")[];
  years: number[];
  quarters: Quarter[];
  defaults: Record<string, string>;
  dimensions: Dimension[];
}
export interface Metadata { datasets: Dataset[]; policyTypes: { code: PolicyType; label: string }[] }
export interface Trend {
  dataset: DatasetId;
  view: "trend";
  unit: string;
  filters: Record<string, string>;
  points: { period: string; value: number | null }[];
}
export interface Comparison {
  dataset: DatasetId;
  view: "comparison";
  unit: string;
  period: string;
  charts: {
    dimension: string;
    fixedFilters: Record<string, string>;
    bars: { code: string; value: number | null }[];
  }[];
}
export interface Policies {
  type: PolicyType;
  items: {
    id: number;
    periodStart: number;
    periodEnd: number;
    policy: string;
    source: { id: number; fileName: string; url: string };
    page: number | null;
  }[];
}
