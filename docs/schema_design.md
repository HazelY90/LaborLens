# LaborLens Schema Design

## 1. Storage Model

The database stores the latest validated 2019–2025 statistical snapshot in four
fact tables, extracted policies in `policy`, validated source files in
`source_file`, and execution history in `job_run`. Each statistical fact table
has a fixed measure and unit.

| Source file | Fact table | Measure column | Unit |
| --- | --- | --- | --- |
| ALF01.csv | `annual_employment_rate` | `employment_rate_percent` | Percent |
| MUM01.csv | `monthly_unemployment_rate` | `unemployment_rate_percent` | Percent |
| QLF50.csv | `quarterly_employment_rate` | `employment_rate_percent` | Percent |
| QLF59.csv | `quarterly_employment_count` | `employed_persons_thousands` | Thousand persons |

Only valid, non-missing observations are stored. This keeps fact tables focused
on queryable measurements; original CSV files and import logs preserve missing
source records. See [CSV Ingestion Rules](data_ingestion.md#csv-ingestion-rules).

Classification values are Java enum names persisted as strings. Each enum
constant has one additional field, `label`, for its English display name. The
backend supplies `{value, label}` options to the frontend; the frontend displays
`label` and submits `value`. Display labels are not stored in fact or policy rows.
Ordering, defaults, visibility, aggregate relationships and dataset-specific
allowed values remain in application code, not enum fields. Raw CSO codes and
labels remain in source files and ingestion mappings.

## 2. Source Table

`source_file` registers both CSV and PDF files and maps each file to its destination
table. Several PDF files map to `policy`, so `table_name` is not unique.
Flyway creates an empty registry. Source registration and file handling follow
[Data Ingestion Jobs](data_ingestion.md). `download_path` stores a path relative
to the configured external data root. The mappings below are job inputs, not
migration seed data.

| Column | MySQL type | Constraint or purpose |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED AUTO_INCREMENT` | Primary key |
| `file_name` | `VARCHAR(255) NOT NULL` | Unique source filename |
| `table_name` | `VARCHAR(64) NOT NULL` | Destination from the mapping below; not unique |
| `download_url` | `VARCHAR(1024) NOT NULL` | Approved official CSV or PDF URL |
| `download_path` | `VARCHAR(1024) NOT NULL` | Configured local path |
| `checksum` | `CHAR(64) NOT NULL` | Lowercase SHA-256 of the current validated file; not unique |

The checksum identifies file bytes, not successful CSV import or policy
extraction. Successful processing is recorded separately in `job_run`.

| Source file | Destination table | Local path |
| --- | --- | --- |
| ALF01.csv | `annual_employment_rate` | `cso/ALF01.csv` |
| MUM01.csv | `monthly_unemployment_rate` | `cso/MUM01.csv` |
| QLF50.csv | `quarterly_employment_rate` | `cso/QLF50.csv` |
| QLF59.csv | `quarterly_employment_count` | `cso/QLF59.csv` |
| statement-of-strategy-2018-2021.pdf | `policy` | `policies/statement-of-strategy-2018-2021.pdf` |
| statement-of-strategy-2021-2023.pdf | `policy` | `policies/statement-of-strategy-2021-2023.pdf` |
| statement-of-strategy-2023-2025.pdf | `policy` | `policies/statement-of-strategy-2023-2025.pdf` |
| statement-of-strategy-2024-2025.pdf | `policy` | `policies/statement-of-strategy-2024-2025.pdf` |
| statement-of-strategy-2025-2028.pdf | `policy` | `policies/statement-of-strategy-2025-2028.pdf` |

Use the CSV endpoint and approved PDF URLs in [Data Sources and APIs](data_api.md).
Enforce the five permitted destination names with a check constraint. Each
statistical table still has one source file; each policy row references its PDF.

## 3. Fact Tables

### Common types and constraints

- `id`: `BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY`.
- Time columns: `INT NOT NULL`; year 2019–2025, month 1–12, quarter 1–4.
- Dimension columns: `VARCHAR(32) NOT NULL`, mapped to enum names using string
  persistence. Validate table-specific values in ingestion and SQL checks.
- Measure columns: `DOUBLE NOT NULL`. Rates must be finite and between 0 and
  100; counts must be finite and nonnegative. Numeric zero is valid.
- Each natural unique key consists of the time columns and every dimension.
  This prevents duplicate observations regardless of generated row IDs.

### `annual_employment_rate`

Columns, in order:

```text
id, year, age_group, sex, education_attainment_level, nuts_2_region,
employment_rate_percent
```

```sql
UNIQUE (year, age_group, sex, education_attainment_level, nuts_2_region)
INDEX idx_annual_trend (
    age_group, sex, education_attainment_level, nuts_2_region, year
)
INDEX idx_annual_region (nuts_2_region, year)
```

Defaults: `ALL` age, sex, and education; `IRELAND` region.

### `monthly_unemployment_rate`

Columns, in order:

```text
id, year, month, age_group, sex, unemployment_rate_percent
```

```sql
UNIQUE (year, month, age_group, sex)
INDEX idx_monthly_trend (age_group, sex, year, month)
```

Defaults: `AGE_15_74` and `ALL` sex. The measure is seasonally adjusted.

### `quarterly_employment_rate`

Columns, in order:

```text
id, year, quarter, age_group, sex, education_attainment_level,
employment_rate_percent
```

```sql
UNIQUE (year, quarter, age_group, sex, education_attainment_level)
INDEX idx_quarterly_trend (
    age_group, sex, education_attainment_level, year, quarter
)
INDEX idx_quarterly_education (education_attainment_level, year, quarter)
```

Defaults: `ALL` age, sex, and education.

### `quarterly_employment_count`

Columns, in order:

```text
id, year, quarter, citizenship, economic_sector, employed_persons_thousands
```

```sql
UNIQUE (year, quarter, citizenship, economic_sector)
INDEX idx_count_trend (citizenship, economic_sector, year, quarter)
INDEX idx_count_sector (economic_sector, year, quarter)
```

Defaults: `ALL` citizenship and sector. The measure covers employed persons
aged 15 and over. The local usable time series starts at 2021Q1.

## 4. Enum Labels and Source Mappings

The following mappings define stable enum names. Use the source label as the
display `label` for each statistical enum constant, normalising surrounding and
repeated whitespace. The backend is the single source of display labels; the
frontend must not maintain a separate label mapping. Changing a display label
does not change the persisted enum name. Dataset restrictions and defaults are
application rules, not fields attached to enum constants.

### `AgeGroup`

| Source label | Enum value |
| --- | --- |
| All ages | `ALL` |
| 15 - 24 years | `AGE_15_24` |
| 15 - 74 years | `AGE_15_74` |
| 20 - 24 years | `AGE_20_24` |
| 25 - 29 years | `AGE_25_29` |
| 25 - 54 years | `AGE_25_54` |
| 25 - 74 years | `AGE_25_74` |
| 30 - 34 years | `AGE_30_34` |
| 35 - 39 years | `AGE_35_39` |
| 40 - 44 years | `AGE_40_44` |
| 45 - 49 years | `AGE_45_49` |
| 50 - 54 years | `AGE_50_54` |
| 55 - 59 years | `AGE_55_59` |
| 60 - 64 years | `AGE_60_64` |

Allowed members are dataset-specific:

- `annual_employment_rate`: `ALL` and the nine five-year bands from
  `AGE_20_24` through `AGE_60_64`.
- `monthly_unemployment_rate`: `AGE_15_24`, `AGE_25_74`, `AGE_15_74`.
- `quarterly_employment_rate`: the annual table's members plus `AGE_25_54`.

`ALL` retains the source table's own age scope. It must not be interpreted as
all possible ages or used to infer unpublished age bands.

### `Sex`

| Source label | Enum value |
| --- | --- |
| Both sexes | `ALL` |
| Female | `FEMALE` |
| Male | `MALE` |

All three rate tables use these values. The default is `ALL`.

### `EducationLevel`

| Source label | Enum value |
| --- | --- |
| Levels of Education (Levels  0-8) | `ALL` |
| Primary and lower secondary education (Levels 1-2) | `PRIMARY_LOWER_SECONDARY` |
| Upper secondary and post-secondary non-tertiary education (Levels 3 and 4) | `UPPER_POST_SECONDARY` |
| Tertiary education (Levels 5-8) | `TERTIARY` |

Both employment-rate tables use only these four education groups. Exclude
`Less than primary (Level 0)` and `Level of education - not stated` during
ingestion because of their high missing-value rates, even when an individual
row has a published value. Preserve the source's published `ALL` rate.

### `Region`

| Source label | Enum value |
| --- | --- |
| Ireland | `IRELAND` |
| Northern and Western | `NORTHERN_WESTERN` |
| Southern | `SOUTHERN` |
| Eastern and Midland | `EASTERN_MIDLAND` |

Only the annual employment-rate table has a region dimension. `IRELAND` is the
national aggregate and the default selection.

### `Citizenship`

| Source label | Enum value |
| --- | --- |
| All Countries | `ALL` |
| Ireland | `IRELAND` |
| All countries excluding Ireland | `EXCLUDING_IRELAND` |
| All countries excluding Ireland,United Kingdom and EU272020 | `OUTSIDE_EU_UK` |

These are citizenship groups of people employed in Ireland. `EU272020` means
EU27 using its 2020 membership composition, which includes Ireland and excludes
the UK. Use `ALL` as the default.

### `EconomicSector`

Only these five QLF59 members are stored, using NACE Rev. 2.1:

| Source label | Enum value |
| --- | --- |
| All NACE Economic Sectors (A-V) | `ALL` |
| Agriculture, Forestry and Fishing (A) | `AGRICULTURE` |
| Industry and Construction (B-F) | `INDUSTRY_CONSTRUCTION` |
| Services (G-V) | `SERVICES` |
| Information and Communication (J,K) | `INFORMATION_COMMUNICATION` |

`Information and Communication (J,K)` is the available IT-related group. NACE
Rev. 2.1 places computer programming in K, while QLF59 publishes J and K together;
therefore the group is broader than IT alone. See the
[official classification](https://ec.europa.eu/eurostat/documents/3859598/24236479/KS-01-26-047-EN-N.pdf).
Use `ALL` as the default and import the published aggregate values directly.

## 5. Interpretation and Query Rules

- Keep published age groups. The employment-rate files lack population weights
  for regrouping, and MUM01 cannot split 25–74 into smaller requested groups.
  `ALL` retains each source table's own age scope.
- Employment rates use the relevant population as the denominator; unemployment
  rates use the relevant labour force. Rates cannot be summed or simply averaged
  across groups, and unemployment is not `100 - employment rate`. See
  [CSO definitions](https://www.cso.ie/en/releasesandpublications/ep/p-lfs/labourforcesurveyquarter42025/backgroundnotes/).
- Do not average quarterly employment rates to replace official annual rates.
- Aggregate relationships are fixed in business code: national and total groups
  overlap their components; `EXCLUDING_IRELAND` includes `OUTSIDE_EU_UK`, and
  `SERVICES` includes `INFORMATION_COMMUNICATION`. Do not stack overlapping
  groups or reconstruct totals from the reduced selection.
- Determine filter availability by checking for matching stored rows. An absent
  observation means unavailable data, not zero. APIs fill missing periods with
  `null` on the requested time axis so charts show gaps.
- Return the fixed unit and source attribution alongside each query result.

## 6. Policy Table

`policy` stores one concrete action or target from one report. Columns, in order:

```text
id, period_start, period_end, policy, type, source_file_id
```

| Column | MySQL type | Constraint or purpose |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED AUTO_INCREMENT` | Primary key |
| `period_start` | `INT NOT NULL` | Inclusive start year of the source strategy |
| `period_end` | `INT NOT NULL` | Inclusive end year of the source strategy |
| `policy` | `TEXT NOT NULL` | Nonblank English description of one action or target |
| `type` | `VARCHAR(32) NOT NULL` | One `PolicyType` enum value |
| `source_file_id` | `BIGINT UNSIGNED NOT NULL` | Foreign key to `source_file.id`; restrict deletion |

Require `period_start <= period_end`. Preserve the original strategy periods:
2018–2021, 2021–2023, 2023–2025, 2024–2025 and 2025–2028. These years describe
source coverage, not legal commencement or expiry. Filter reports by overlap
with the analysis period; do not claim every action was implemented during that
period. Keep explicit deadlines, such as January 2026, in the policy text.

```sql
INDEX idx_policy_type_period (type, period_start, period_end)
INDEX idx_policy_source (source_file_id)
```

Validate that `source_file_id` identifies an approved PDF mapped to `policy`.
Keep similar actions from different reports as separate source-specific rows.
Refresh transactions and source evidence retention follow
[PDF Ingestion Rules](data_ingestion.md#pdf-ingestion-rules); evidence artifacts
remain outside this table.

### `PolicyType`

Use five broad categories. Each enum constant contains only its display `label`
in addition to its stable name. Assignment rules belong to extraction and
application logic.

| Type | Display label | Scope and examples |
| --- | --- | --- |
| `ECONOMIC_MIGRATION` | Economic Migration | Economic migration, employment permits, critical skills eligibility and overseas talent access |
| `EMPLOYMENT_DEVELOPMENT` | Employment Development | Job creation, regional enterprise plans, enterprise growth, tourism jobs and enterprise digital transition |
| `SKILLS_DEVELOPMENT` | Skills Development | Domestic skills supply, training, upskilling and higher-education collaboration |
| `WORKING_CONDITIONS` | Working Conditions | Pay, sick pay, remote/flexible work, collective bargaining, employment rights, retirement rules and occupational safety |
| `EMPLOYMENT_INCLUSION` | Employment Inclusion | Access to employment for underrepresented groups, including female participation, disability and minority employment supports |

Assign one type according to the action's main purpose. Skills-based immigration
belongs to `ECONOMIC_MIGRATION`; domestic training belongs to
`SKILLS_DEVELOPMENT`. Enterprise digitalisation belongs to
`EMPLOYMENT_DEVELOPMENT`, while an explicit training action belongs to
`SKILLS_DEVELOPMENT`. Split a passage into separate records when it contains
independently supported actions, such as flexible work and female participation.
Exclude internal departmental HR and unsupported generic aspirations.

The AI-analysis schema remains to be designed. Saved analyses must preserve
actual statistical inputs, policy evidence and source versions, because
refreshes replace rows and may change their IDs.


## 7. Job Run Table

`job_run` records one execution attempt for one file and one job. Retries create
new rows; multiple successful executions of the same version are allowed.

| Column | MySQL type | Constraint or purpose |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED AUTO_INCREMENT` | Primary key |
| `job_type` | `VARCHAR(32) NOT NULL` | `JobType` name |
| `source_file_id` | `BIGINT UNSIGNED NULL` | Foreign key to `source_file.id`; restrict deletion |
| `file_name` | `VARCHAR(255) NOT NULL` | Target filename, available before source registration |
| `checksum` | `CHAR(64) NULL` | Lowercase SHA-256 of the input bytes for this attempt |
| `status` | `VARCHAR(16) NOT NULL` | `JobStatus` name |
| `started_at` | `DATETIME(6) NOT NULL` | UTC start time supplied by the job |
| `finished_at` | `DATETIME(6) NULL` | UTC completion time; null while running |
| `row_count` | `BIGINT UNSIGNED NULL` | Committed observation/policy count; zero is valid, null if not applicable |
| `error_message` | `TEXT NULL` | Sanitised failure reason; never credentials or tokens |
| `process_version` | `VARCHAR(128) NOT NULL` | Version of parsing rules or model/prompt/schema configuration |

Require nonblank filenames and process versions. `FILE_PREPARATION` may have a
null source ID or checksum before a file is available. CSV import and policy
extraction require both. Every successful run requires both. Terminal statuses
require `finished_at >= started_at`; `RUNNING` requires a null finish time.
Checksums must contain exactly 64 lowercase hexadecimal characters when present.

```sql
INDEX idx_job_success (source_file_id, job_type, status, checksum, process_version)
INDEX idx_job_file_time (file_name, started_at)
```

Indexes are not unique: a retry or forced run retains its own history. The
recorded checksum is a snapshot and must not change when the source is refreshed.
The application verifies filename/source consistency. Run transitions, duplicate
processing checks and transaction boundaries are defined in
[Data Ingestion Jobs](data_ingestion.md#run-history-and-retries).

### `JobType`

| Enum value | Display label |
| --- | --- |
| `FILE_PREPARATION` | File Preparation |
| `CSV_IMPORT` | CSV Import |
| `POLICY_EXTRACTION` | Policy Extraction |

### `JobStatus`

| Enum value | Display label |
| --- | --- |
| `RUNNING` | Running |
| `SUCCESS` | Success |
| `FAILED` | Failed |
| `SKIPPED` | Skipped |

These enums follow the same name-and-label-only design as the other enums.
