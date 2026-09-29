/** Shared routes keep the header and page titles in sync. */
export const pages = [
  { id: "annual-employment-rate", label: "Annual employment", title: "Annual employment rate", description: "Explore employment across age, sex, education and region." },
  { id: "monthly-unemployment-rate", label: "Monthly unemployment", title: "Monthly unemployment rate", description: "Follow unemployment trends over time." },
  { id: "quarterly-employment-rate", label: "Quarterly employment", title: "Quarterly employment rate", description: "Compare employment rates by quarter and demographic group." },
  { id: "quarterly-employment-count", label: "Employment count", title: "Quarterly employment count", description: "Explore employment by citizenship and economic sector." },
  { id: "policies", label: "Policies", title: "Employment policies", description: "Explore policy developments alongside Ireland’s labour market." },
] as const;
