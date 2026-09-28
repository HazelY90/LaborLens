# LaborLens Data Sources and APIs

## Scope

LaborLens analyses Irish employment statistics and employment-policy evidence
for **2019–2025**. CSO CSV data is parsed and calculated deterministically. AI
extracts policy meaning from official PDFs and interprets validated evidence.

See [Schema Design](schema_design.md) for storage and category mappings, and
[Development Plan](development-plan.md) for implementation milestones.

## CSO Statistics

Download full CSV tables from the CSO PxStat API without an API key:

```text
https://ws.cso.ie/public/api.restful/PxStat.Data.Cube_API.ReadDataset/{TABLE_ID}/CSV/1.0/en
```

| Table ID | Selected statistic | Unit | Dimensions beyond time |
| --- | --- | --- | --- |
| ALF01 | `ALF01C01`: annual employment rate | `%` | Age, sex, education, NUTS 2 region |
| MUM01 | `MUM01C02`: seasonally adjusted monthly unemployment rate | `%` | Age, sex |
| QLF50 | `QLF50C01`: quarterly employment rate | `%` | Age, sex, education |
| QLF59 | `QLF59C01`: employed persons aged 15 and over | `Thousand` | Selected citizenship and sector groups |

QLF59 uses only the four citizenship groups and five sectors listed in
[Enum Labels and Source Mappings](schema_design.md#4-enum-labels-and-source-mappings).
Its local 2019–2020 values are all missing, so stored observations begin at
2021Q1. EHQ03 is excluded because 87.30% of its local 2019–2025 values are missing.

Reference links: [CSO portal](https://data.cso.ie/),
[PxStat guide](https://www.cso.ie/en/databases/userguides/pxstatuserguide/),
[discovery catalogue](https://data.gov.ie/).

CSV import rules and local reference counts are documented in
[CSV Ingestion Rules](data_ingestion.md#csv-ingestion-rules).

## Policy Reports

Use the Department of Enterprise, Tourism and Employment
[Statement of Strategy archive](https://www.gov.ie/en/department-of-enterprise-tourism-and-employment/publications/statement-of-strategy/).
The approved direct PDF URLs are:

| Strategy period | PDF URL | Strategy overlap with 2019–2025 |
| --- | --- | --- |
| 2018–2021 | <https://enterprise.gov.ie/en/publications/publication-files/statement-of-strategy-2018-2021.pdf> | 2019–2021 |
| 2021–2023 | <https://enterprise.gov.ie/en/publications/publication-files/statement-of-strategy-2021-2023.pdf> | 2021–2023 |
| 2023–2025 | <https://enterprise.gov.ie/en/publications/publication-files/statement-of-strategy-2023-2025.pdf> | 2023–2025 |
| 2024–2025 | <https://enterprise.gov.ie/en/publications/publication-files/statement-of-strategy-2024-2025.pdf> | 2024–2025 |
| 2025–2028 | <https://enterprise.gov.ie/en/publications/publication-files/statement-of-strategy-2025-2028.pdf> | 2025 only |

Strategy overlap identifies relevant reports, not verified implementation dates.

See [PDF Ingestion Rules](data_ingestion.md#pdf-ingestion-rules) for extraction,
validation, evidence retention and refresh behaviour.

## Enum Display Contract

The backend provides enum options as `{value, label}` objects for statistical
filters and policy types. `value` is the stable enum name used in stored records,
requests and responses; `label` is the English display name held by the enum.
The frontend uses backend-provided labels rather than maintaining its own map.
For example:

```json
{"value": "ECONOMIC_MIGRATION", "label": "Economic Migration"}
```

Only `label` is an additional enum field. Ordering, defaults, visibility,
aggregate relationships and dataset-specific allowed values stay in application
logic. Option availability must still reflect the dataset and stored data.

## Combined Analysis Contract

Calculate numeric results in Java or SQL. Give AI only those verified results,
validated policy evidence, an explicit period, and a bounded analytical question.
Validate all citations against the supplied evidence; generated findings must
not invent measurements or claim policy causation from statistical association.

Each saved analysis must retain:

- Findings, limitations, evidence references, and validation status.
- Actual statistical input values, semantic dimension keys, calculation
  definition, source checksums, and mapping version.
- Policy and document references, model/settings, prompt/schema versions, and
  generation timestamp.

Preserving input values is necessary because snapshot refreshes can replace
fact-row IDs. The detailed analysis schema remains to be finalised.

## Source Verification

CSV endpoints and PDF URLs were checked on **23 September 2026**. A stable URL
can return revised data; version tracking is defined in
[Ingestion Workflow](data_ingestion.md).
