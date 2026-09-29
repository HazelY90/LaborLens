# Backend Data API

Implemented contract for [Frontend Design](frontend_design.md).
Data endpoints require an Access JWT. See [Authentication](authentication.md).

## Structure

Use one `DataController` under `/api/data`. Keep queries in services/repositories
with fixed table mappings and typed filter/response DTOs. All endpoints, including metadata, require authentication. JSON fields and query parameters use camelCase;
dimension and policy values use existing enum codes.

| GET endpoint | Table / purpose | Views | Trend filters |
| --- | --- | --- | --- |
| `/api/data/annual-employment-rate` | `annual_employment_rate` | `trend`, `comparison` | `ageGroup`, `sex`, `education`, `region` |
| `/api/data/monthly-unemployment-rate` | `monthly_unemployment_rate` | `trend` | `ageGroup`, `sex` |
| `/api/data/quarterly-employment-rate` | `quarterly_employment_rate` | `trend`, `comparison` | `ageGroup`, `sex`, `education` |
| `/api/data/quarterly-employment-count` | `quarterly_employment_count` | `trend`, `comparison` | `citizenship`, `economicSector` |
| `/api/data/policies` | `policy` joined to `source_file` | — | `type` |
| `/api/data/metadata` | Controls for all five pages | — | — |

## Statistical queries

`view` defaults to `trend`. Each dimension accepts exactly one value; omitted
dimensions use the overall defaults below. Reject repeated parameters,
comma-separated values, unknown parameters and values unsupported by that dataset.

| Dataset | Overall defaults | Unit |
| --- | --- | --- |
| Annual employment rate | `ageGroup=ALL`, `sex=ALL`, `education=ALL`, `region=IRELAND` | `%` |
| Monthly unemployment rate | `ageGroup=AGE_15_74`, `sex=ALL` | `%` |
| Quarterly employment rate | `ageGroup=ALL`, `sex=ALL`, `education=ALL` | `%` |
| Quarterly employment count | `citizenship=ALL`, `economicSector=ALL` | `thousand persons` |

Allowed dimension values are dataset-specific subsets of the existing enums,
matching [Schema Design](schema_design.md). Never expose every shared enum value
as valid for every dataset. Map API `education` to `education_attainment_level`
and `region` to `nuts_2_region`.

### Trend

One filter card sends one request and receives one series. Multiple cards use
separate requests; no aggregation or automatic combination expansion is performed.
The response covers the full current analysis range, 2019–2025. Year/month/quarter
parameters are not accepted in this view.

```http
GET /api/data/annual-employment-rate?view=trend&ageGroup=AGE_30_34&sex=FEMALE&education=ALL&region=IRELAND
```

Response shape (values are illustrative; the example omits other periods):

```json
{
  "dataset": "annual-employment-rate",
  "view": "trend",
  "unit": "%",
  "filters": {
    "ageGroup": "AGE_30_34",
    "sex": "FEMALE",
    "education": "ALL",
    "region": "IRELAND"
  },
  "points": [
    {"period": "2023", "value": 75.2},
    {"period": "2024", "value": null},
    {"period": "2025", "value": 76.1}
  ]
}
```

Return all period slots in chronological order: 7 annual, 84 monthly or 28 quarterly.
Period formats are `YYYY`, `YYYY-MM` and `YYYY-Qn`. Values are JSON numbers or `null`;
zero remains zero. A valid filter with no observations returns all-null points.
The frontend connects across nulls and draws markers only for available observations.

### Comparison

Require `year` for annual comparisons and both `year` and `quarter` for quarterly
comparisons. Valid years are 2019–2025; quarters are integers 1–4. Reject dimension
filters: the server fixes all non-compared dimensions to their overall defaults.
Monthly unemployment does not support this view.

```http
GET /api/data/annual-employment-rate?view=comparison&year=2024
GET /api/data/quarterly-employment-rate?view=comparison&year=2024&quarter=2
```

One response contains every dimension chart for that page. Example showing only
one chart; the actual annual response also includes age, education and region:

```json
{
  "dataset": "annual-employment-rate",
  "view": "comparison",
  "unit": "%",
  "period": "2024",
  "charts": [
    {
      "dimension": "sex",
      "fixedFilters": {"ageGroup": "ALL", "education": "ALL", "region": "IRELAND"},
      "bars": [
        {"code": "ALL", "value": 76.0},
        {"code": "FEMALE", "value": 72.0},
        {"code": "MALE", "value": null}
      ]
    }
  ]
}
```

Include every allowed category in metadata order, including the overall category
as a baseline. Missing observations return `null` and retain their category slot.
Read stored observations directly; overlapping categories must not be summed.

## Policies

Require one `type`: `ECONOMIC_MIGRATION`, `EMPLOYMENT_DEVELOPMENT`,
`SKILLS_DEVELOPMENT`, `WORKING_CONDITIONS` or `EMPLOYMENT_INCLUSION`.
Return all policies for the selected category; no pagination is needed for the
current five-report scope.

```http
GET /api/data/policies?type=SKILLS_DEVELOPMENT
```

Response shape (illustrative):

```json
{
  "type": "SKILLS_DEVELOPMENT",
  "items": [
    {
      "id": 123,
      "periodStart": 2025,
      "periodEnd": 2028,
      "policy": "Expand domestic skills training.",
      "source": {
        "id": 9,
        "fileName": "statement-of-strategy-2025-2028.pdf",
        "url": "https://enterprise.gov.ie/en/publications/publication-files/statement-of-strategy-2025-2028.pdf"
      },
      "page": 3
    }
  ]
}
```

Order by `periodStart`, `periodEnd`, `source.id`, then `id`, ascending. The frontend
groups items by strategy period and retains source identity. These periods are
not publication or implementation dates. No matches returns `items: []`.

V1 stores source identity and strategy periods in the database; cited pages exist
in `runs/<run-id>/evidence.json`. Resolve pages by policy ID from the latest
successful `POLICY_EXTRACTION` run for that source. Never use failed or skipped
run artifacts. Return `page: null` if evidence is unavailable. The current UI does
not display page citations; it lists distinct source files below the selected category. Do not expose local filesystem paths or read arbitrary client paths.

## Metadata

`GET /api/data/metadata` supplies all page controls in one response:

- `datasets`: four entries with `id` (endpoint suffix), `label`, `unit`,
  `granularity` (`annual`, `monthly`, `quarterly`), `views`, `years`, `quarters`,
  `defaults` and `dimensions`.
- `years`: the supported analysis years 2019–2025, even when observations are missing.
  `quarters`: `[1, 2, 3, 4]` for quarterly datasets, otherwise `[]`.
- `dimensions`: an ordered array of `{key, label, values}`, where each value is
  `{code, label}`. Use dataset-specific enum allowlists and existing display labels.
  Overall comes first, age bands use numeric order, and other categories use enum order.
- `policyTypes`: an ordered array of `{code, label}` using the existing policy enum.

Metadata is generated by `DataCatalog` from fixed enum allowlists, not database
queries for existing values. The frontend uses metadata labels for controls, legends and bars; query responses
use the corresponding codes. Supported filter combinations may still have missing data.

## Errors

Use `400` for invalid parameters, missing required parameters or unsupported views;
`401` for missing or invalid authentication; `500` for unexpected server failures. Valid
queries without observations return `200` with null values or an empty policy list.

Error body: `{ "code": "INVALID_FILTER", "message": "Unsupported ageGroup for this dataset.", "field": "ageGroup" }`.
Use `INVALID_PARAMETER`, `INVALID_FILTER` or `INTERNAL_ERROR` as
appropriate; `field` is null for errors unrelated to a specific parameter. Do not
expose SQL, credentials or stack traces.
