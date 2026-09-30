# Frontend Design

Current implementation: Next.js, React, TypeScript and Recharts. All interface text is English.

## Pages and account access

- A public homepage and five authenticated data pages share one header.
- Page introductions are left-aligned. The theme and chart palette come from `utils/colors.json`.
- Login opens Annual Employment Rate. The header then shows five data links and an account menu.
- Registration, login, username changes, password changes and account deletion use dialogs. The account menu also provides logout.
- Registration requires a subsequent login. Password changes sign the user out and reopen login.
- Protected URLs show a login prompt without loading data when signed out. Backend data APIs also enforce authentication.

| Page | Charts | Dimensions |
| --- | --- | --- |
| Annual Employment Rate | Trend and annual comparison | Age, sex, education, region |
| Monthly Unemployment Rate | Trend only | Age, sex |
| Quarterly Employment Rate | Trend and quarterly comparison | Age, sex, education |
| Quarterly Employment Count | Trend and quarterly comparison | Citizenship, economic sector |
| Policies | Category tabs and timeline | Policy type, strategy period |

## Statistical layout

- Trend and comparison appear together: trend above, comparison below. A sticky left navigation jumps between sections.
- Series filters sit to the right of the trend chart in a height-limited, internally scrolling panel.
- The comparison area matches the trend chart width, leaving the space below the series panel empty.
- Comparison cards appear two per row on wide screens. Narrow screens stack the navigation, chart, filters and comparison cards.

## Trends

- Each single-select filter card produces one line. Start with overall values and allow up to five cards; at least one remains.
- Each card uses a distinct colour matching its line and legend. Labels identify all selected dimensions.
- Display all 2019–2025 period slots: annual, monthly or quarterly. Wide charts scroll horizontally.
- Recharts `LineChart` and linear `Line` series connect available observations across missing periods using `connectNulls`. Circular markers include zero and exclude missing observations.
- The time axis displays every period label at a -50-degree angle; the value axis remains visible.
- Hovering over the chart or focusing it and using arrow keys exposes a compact tooltip containing only the period and values with units. Values use their series colours; filter descriptions remain in the external legend.
- An expandable data table exposes each series by period, including missing observations.
- Empty selections show a no-data message. Do not aggregate series or replace missing values with zero.

## Comparisons

- Select a year; quarterly pages have a separate quarter selector. The initial selection is the latest supported year and Q4 where applicable.
- Vary one dimension per chart and fix every other dimension to its overall value. Users cannot change these fixed filters.
- Show four charts for annual employment, three for quarterly employment rate and two for quarterly employment count.
- Recharts `BarChart` renders separate vertical bars, not stacked bars, because categories can overlap. The value axis and its ticks are hidden; numeric labels appear above the bars.
- Every comparison chart fits its card without horizontal scrolling. `ResponsiveContainer` fills the available width, and bars shrink with category spacing, up to a maximum width of 65 pixels.
- Category labels use equal-width CSS Grid columns aligned with the bars below the plot. Labels wrap horizontally, including long words, and grow vertically without a fixed label height or rotation.
- Tooltips show only the selected period and the active bar's value with its unit; they omit category and filter descriptions.
- Missing observations have no bar and are listed below the chart as `Data unavailable`. True zero retains a minimal visible bar and a `0` label.
- Each chart provides an expandable category/value table that preserves missing observations.

Overall defaults are `IRELAND` for annual region, `AGE_15_74` for monthly age,
and `ALL` for other dimensions. Rates use `%`; counts use `thousand persons`.

## Policies

- Horizontal tabs select one of the five policy categories.
- A left timeline and right policy cards occupy 80% of the content width, centered; narrow screens use the full width.
- Group by strategy start/end years and source, ordered chronologically. Keep records from distinct sources separate.
- Cards display policy text without filenames or page citations. List distinct source files for the selected category, with links, at the bottom.
- Dates identify strategy periods, not publication or implementation dates. Categories without policies show an empty state.

## Data and code structure

- `app`: routes and layouts; `contexts`: authentication and shared metadata;
  `hooks`: session access and cancellable data loading; `components`: shared UI;
  `features`: home, account, statistics and policy components; `services`: API calls;
  `utils`: contracts, chart helpers, routes and colours.
- Chart data is adapted in the frontend without changing API contracts: trends align series by period, and comparisons attach category labels from metadata. `ChartTooltip` shares the compact period/value/unit presentation across both chart types.
- Metadata supplies dataset-specific `{code,label}` options, defaults, years and units. These are fixed allowlists, not database-derived availability.
- The browser API client keeps Access JWTs in memory, includes cookies, and authenticates by default. `auth: false` disables bearer authentication and automatic refresh.
- Concurrent refreshes share one request; protected requests retry at most once after a 401. Loading failures offer retry, and obsolete data requests are cancelled.
- The API still returns optional policy page citations, but the UI does not display them.

Contracts: [Backend Data API](backend_api.md) and [Authentication](authentication.md).
Configuration and commands: [Frontend README](../frontend/README.md).
