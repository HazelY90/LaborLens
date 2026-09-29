# LaborLens Data Sources and APIs

## Scope

LaborLens analyses Irish employment statistics and employment-policy evidence
for **2019–2025**. CSO CSV data is parsed deterministically and published observations are stored directly.
AI extracts policy records from complete official PDFs.

See [Schema Design](schema_design.md) for storage and category mappings, and
[Development Plan](development-plan.md) for the implemented scope.

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
Missing observations are omitted during import and returned as `null` by the
query API. Only the four datasets above are imported.

Reference links: [CSO portal](https://data.cso.ie/),
[PxStat guide](https://www.cso.ie/en/databases/userguides/pxstatuserguide/),
[discovery catalogue](https://data.gov.ie/).

CSV import rules are documented in
[Data Ingestion](data_ingestion.md#processing).

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

See [PDF Processing](data_ingestion.md#processing) for extraction,
validation, evidence retention and refresh behaviour.

## Enum Display Contract

The backend provides enum options as `{code, label}` objects for statistical
filters and policy types. `code` is the stable enum name used in stored records,
requests and responses; `label` is the English display name held by the enum.
The frontend uses backend-provided labels rather than maintaining its own map.
For example:

```json
{"code": "ECONOMIC_MIGRATION", "label": "Economic Migration"}
```

Only `label` is an additional enum field. Ordering, defaults, visibility,
aggregate relationships and dataset-specific allowed values stay in application
logic. Metadata uses fixed dataset-specific allowlists, not distinct values queried from stored rows.
Supported selections may have no observations.

## Source Verification

CSV endpoints and PDF URLs were checked on **23 September 2026**. A stable URL
can return revised data; version tracking is defined in
[Ingestion Workflow](data_ingestion.md).
