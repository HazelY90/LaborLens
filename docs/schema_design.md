# LaborLens Schema Design

## 1. Purpose

This document defines which CSO statistics and classification labels LaborLens
will store and expose in the first frontend release. It also defines a compact
database schema for the selected observations, reusable source labels, and file
provenance.

The database retains the selected observations, their classification labels,
their units, and their missing values. Labels are normalised once and referenced
from fact rows so that the same source category is not stored with inconsistent
text.

The first release will present five statistical areas:

| Source table | Frontend subject | Main measure |
| --- | --- | --- |
| `ALF01` | Annual employment rate | Annual employment rate (%) |
| `MUM01` | Monthly unemployment rate | Seasonally adjusted monthly unemployment rate (%) |
| `QLF50` | Quarterly employment rate | Quarterly employment rate (%) |
| `QLF59` | Employment by citizenship and industry | Persons in employment (thousand) |
| `EHQ03` | Earnings, hours, and labour costs | Selected quarterly earnings, hours, and cost measures |

## 2. General Presentation Rules

### 2.1 Normalise source labels, curate the interface

All distinct CSO statistic, category, and unit labels found in the five CSV
files remain in the `label` table. Fact tables reference the labels required by
the selected observations. The API applies display wording, ordering, defaults,
and visibility rules from version-controlled backend configuration.

### 2.2 Aggregate and detail members must remain distinct

Several dimensions contain both aggregate and detailed members, for example:

- `All ages` and individual age bands.
- `Both sexes` and individual sex categories.
- `Ireland` and the three NUTS 2 regions.
- All education levels and individual education levels.
- All countries and individual citizenship groups.
- All industries, broad industry groups, and detailed industries.
- `All employees` and individual employee categories.

Aggregate members must be marked explicitly. The frontend must not place an
aggregate and its components in the same stacked total or add them together.

### 2.3 Missing values are not zero

A blank CSO `VALUE` means that no value was published for that dimension
combination. It must be stored as `NULL`, displayed as unavailable, and never
converted to zero.

When the selected filters have no published observations, the frontend should
show `No published value`. A line chart should display a gap rather than a
zero-valued point.

### 2.4 Filters must respond to data availability

The API should return whether a classification member has at least one
published value under the currently selected filters. The frontend can then
disable unavailable choices instead of allowing a selection that produces an
empty chart.

### 2.5 Source codes and units are not ordinary filters

CSO codes must remain available in provenance and download views, but the
standard dashboard does not need to expose them. A unit selector is unnecessary
when the selected statistic already determines the unit.

## 3. ALF01 — Annual Employment Rate

### 3.1 Frontend scope

`ALF01` provides annual employment rates for 2019–2025. The initial frontend
will expose year, age group, sex, education level, and NUTS 2 region.

The statistic is fixed to:

| Statistic code | Display name | Unit |
| --- | --- | --- |
| `ALF01C01` | Annual employment rate | `%` |

The frontend does not need statistic or unit selectors for this dataset.

### 3.2 Age members

Expose all available age labels:

- All ages
- 20–24
- 25–29
- 30–34
- 35–39
- 40–44
- 45–49
- 50–54
- 55–59
- 60–64

Use a single-select control with `All ages` as the default. `All ages` means the
aggregate age scope defined by this CSO table. The local CSV does not establish
the exact age boundaries of this aggregate, so the interface must not replace
it with an inferred range.

### 3.3 Sex members

Expose the source aggregate and the two source detail categories. Use the
aggregate as the default. Disable a detail category when the other active
filters leave no published values.

### 3.4 Education members

Expose these members in the standard interface:

| Source label | Frontend label | Member type |
| --- | --- | --- |
| `Levels of Education (Levels  0-8)` | All education levels | Aggregate |
| `Primary and lower secondary education (Levels 1-2)` | Primary and lower secondary | Detail |
| `Upper secondary and post-secondary non-tertiary education (Levels 3 and 4)` | Upper secondary and post-secondary non-tertiary | Detail |
| `Tertiary education (Levels 5-8)` | Tertiary education | Detail |

Do not expose these members in the standard interface:

| Source label | Visibility | Reason |
| --- | --- | --- |
| `Less than primary (Level 0)` | Hidden | All 2019–2025 values are missing. |
| `Level of education - not stated` | Advanced | Useful for data-quality inspection, but not for ordinary comparison. |

### 3.5 Region members

Expose all four members:

- Ireland
- Northern and Western
- Southern
- Eastern and Midland

Use `Ireland` as the default. Mark it as an aggregate member. A trend chart may
compare the national rate with regional rates, but the frontend must not treat
the national rate as another region in a stacked total.

## 4. MUM01 — Monthly Unemployment Rate

### 4.1 Frontend scope

The database and first frontend release use only the unemployment rate:

| Statistic code | Display name | Unit |
| --- | --- | --- |
| `MUM01C02` | Monthly unemployment rate | `%` |

The unemployment-count statistic is outside the selected storage scope.

The standard frontend does not need a statistic or unit selector.

### 4.2 Age members

Expose all three source members:

- 15–74, the default aggregate.
- 15–24.
- 25–74.

Use a single-select control. The 15–74 member overlaps the two detail members,
so the three series must never be added together.

### 4.3 Sex members

Expose the aggregate and the two source detail categories. Use the aggregate as
the default. All age and sex members contain published values throughout the
selected 2019–2025 period.

## 5. QLF50 — Quarterly Employment Rate

### 5.1 Frontend scope

`QLF50` provides quarterly employment rates from 2019Q1 through 2025Q4. The
frontend will expose quarter, age group, sex, and education level.

The statistic is fixed to:

| Statistic code | Display name | Unit |
| --- | --- | --- |
| `QLF50C01` | Quarterly employment rate | `%` |

### 5.2 Age members

All source age members contain some published values and may remain available,
but the control should separate summary and detailed members.

Summary members:

- All ages
- 25–54

Detailed age bands:

- 20–24
- 25–29
- 30–34
- 35–39
- 40–44
- 45–49
- 50–54
- 55–59
- 60–64

Use `All ages` as the default. The 25–54 summary overlaps several detailed age
bands, so it must not appear with those bands in an additive or stacked view.

### 5.3 Sex members

Expose the aggregate and the two source detail categories, with the aggregate
selected by default.

### 5.4 Education members

Apply the same education visibility rules as `ALF01`:

- Default: all education levels, Levels 1–2, Levels 3–4, and Levels 5–8.
- Advanced: education not stated.
- Hidden: Level 0, because all values are missing.

The API must calculate availability after applying the active age, sex, and
education filters because many detailed combinations have no published value.

## 6. QLF59 — Employment by Citizenship and Industry

### 6.1 Frontend scope

`QLF59` provides quarterly employment counts by citizenship group and NACE Rev.
2.1 industry. The value unit is `Thousand`.

Although the CSV contains rows beginning in 2019Q1, all values for 2019 and
2020 are missing. The frontend must present the usable period as 2021Q1 through
2025Q4.

The statistic is fixed to:

| Statistic code | Display name | Unit |
| --- | --- | --- |
| `QLF59C01` | Persons aged 15 years and over in employment | Thousand |

### 6.2 Citizenship members

The citizenship dimension contains overlapping aggregate and detailed members.
The frontend should provide two modes rather than one flat list.

Overview mode:

- All countries.
- Ireland.
- Countries excluding Ireland.

Detailed mode:

- Ireland.
- United Kingdom.
- EU14 excluding Ireland and the United Kingdom.
- EU accession countries represented by the source.
- Other countries outside Ireland, the United Kingdom, and EU27.

The database must preserve the exact source labels. The shorter labels above
are frontend display labels.

`Countries excluding Ireland` is an aggregate. It must not be included in a
stacked chart with the detailed non-Irish groups. All citizenship members have
complete 2021Q1–2025Q4 values when the industry is `All sectors`, but many
citizenship-and-industry combinations are sparse. The API must therefore return
member availability for the selected industry.

### 6.3 Industry members

Expose these members in the standard industry selector:

- All sectors.
- Agriculture, forestry, and fishing.
- Construction.
- Wholesale and retail trade.
- Transportation and storage.
- Accommodation and food services.
- Information and communication.
- Financial, insurance, and real estate activities.
- Professional, scientific, and technical activities.
- Administrative and support services.
- Public administration and defence.
- Education.
- Human health and social work.
- Other activities.

Keep these source members outside the standard industry selector:

| Source member | Visibility | Reason |
| --- | --- | --- |
| `Industry (B-E)` | Advanced | Broad aggregate overlapping detailed industries. |
| `Industry and Construction (B-F)` | Advanced | Broad aggregate overlapping detailed industries. |
| `Services (G-V)` | Advanced | Broad aggregate overlapping detailed industries. |
| `NACE Unknown` | Advanced | Data-quality category rather than an analytical industry. |

The broad aggregates may later support a separate broad-sector overview. They
must not be mixed with their components in a stacked total.

When a citizenship group is selected, the frontend should disable industries
with no published values for that group. Missing quarters must remain gaps.

## 7. EHQ03 — Earnings, Hours, and Labour Costs

### 7.1 Frontend role

The other four tables primarily describe employment and unemployment levels or
rates. `EHQ03` adds information about earnings, paid hours, and employer labour
costs. It therefore supports analysis of job conditions and labour costs rather
than only the quantity of employment.

The first release should include `EHQ03`, but only through a curated set of four
measures.

### 7.2 Measures exposed in the first release

| Statistic code | Frontend label | Unit | Reason |
| --- | --- | --- | --- |
| `EHQ03C02` | Average weekly earnings | Euro | Direct measure of weekly earnings. |
| `EHQ03C03` | Average hourly earnings | Euro | Supports industry comparison with less influence from differences in paid hours. |
| `EHQ03C05` | Average weekly paid hours | Hours | Helps explain whether weekly earnings change with hours. |
| `EHQ03C08` | Average hourly total labour costs | Euro | Represents the broader hourly cost to employers. |

### 7.3 Measures retained but not exposed initially

| Measure | Initial visibility | Reason |
| --- | --- | --- |
| Employment | Hidden | Overlaps conceptually with `QLF59`, but uses a different source population and coverage. |
| Earnings excluding irregular earnings | Advanced | Useful for later detailed earnings analysis. |
| Earnings excluding irregular and overtime earnings | Advanced | Useful for later detailed earnings analysis. |
| Irregular earnings | Advanced | Too detailed for the initial dashboard. |
| Other labour costs | Advanced | Total labour costs cover the initial use case. |
| Benefit in kind | Advanced | Sparse and less central to the initial dashboard. |
| All seasonally adjusted measures | Hidden | All corresponding values in the local 2019–2025 sample are missing. |

The database must retain all 21 source statistics even when the frontend hides
them.

### 7.4 Dimensions and defaults

The frontend will expose:

- Quarter, covering 2019Q1 through 2025Q4.
- NACE Rev. 2 industry.
- One of the four selected measures.

Use `All NACE economic sectors` as the default industry and average weekly
earnings as the default measure.

The employee type must be fixed to `All employees` in the first release. The
other three employee categories exist in the CSV but have no published values
for 2019–2025.

The standard frontend does not need a unit selector. The selected statistic
determines whether the value represents Euro or Hours.

### 7.5 Separation from QLF59

`QLF59` and `EHQ03` both contain industry classifications, but they must not be
joined directly by the displayed industry letter:

- `QLF59` uses NACE Rev. 2.1 and comes from labour-force statistics.
- `EHQ03` uses NACE Rev. 2 and comes from an enterprise survey.
- Their source populations and industry codes are not identical.

The first release must not plot `QLF59` employment counts and `EHQ03`
employment counts as if they were the same measure. Any later cross-dataset
industry analysis requires an explicit, versioned classification mapping.

## 8. Persistence Structure

The database contains three shared tables and five fact tables:

- `data_source`
- `label`
- `dataset_label`
- `alf01`
- `mum01`
- `qlf50`
- `qlf59`
- `ehq03`

`data_source` stores the file name, official download URL, and configured local
path for each CSV. `label` stores each distinct source category once.
`dataset_label` records which logical dataset uses each label. Each fact table
has fixed columns for the dimensions and measure represented by its CSV.

The logical dataset-to-table mapping is fixed in backend code:

| Dataset code | Fact table |
| --- | --- |
| `ALF01` | `alf01` |
| `MUM01` | `mum01` |
| `QLF50` | `qlf50` |
| `QLF59` | `qlf59` |
| `EHQ03` | `ehq03` |

Physical table names are not stored in `data_source` or accepted from request
parameters. This fixed mapping makes the relationship explicit without using
dynamic SQL.

The following concerns remain outside the database:

| Concern | Location |
| --- | --- |
| Frontend display wording and ordering | Backend code or version-controlled configuration |
| Visible, advanced, and hidden categories | Backend code or version-controlled configuration |
| Default frontend selections | Backend code |
| Download timestamps and checksums | Application logs |
| Parsed, rejected, and missing row counts | Application logs |
| Import failures | Application logs and monitoring |
| Previous CSV versions | Raw-file archive if later required |

The five fact tables contain the latest validated 2019–2025 snapshot only.

## 9. Common Storage Rules

### 9.1 Measure columns

The first four datasets each store one fixed measure. Their numeric columns use
the measure and unit in the column name:

| Table | Measure column | Type |
| --- | --- | --- |
| `alf01` | `employment_rate_percent` | `DOUBLE NULL` |
| `mum01` | `unemployment_rate_percent` | `DOUBLE NULL` |
| `qlf50` | `employment_rate_percent` | `DOUBLE NULL` |
| `qlf59` | `employed_persons_thousands` | `DOUBLE NULL` |

These tables do not need `statistic_label_id` or `unit_label_id` columns because
the table and measure-column definition fix both meanings.

`EHQ03` stores multiple statistics and units. It therefore uses:

```sql
statistic_label_id BIGINT UNSIGNED NOT NULL,
unit_label_id BIGINT UNSIGNED NOT NULL,
measure_value DECIMAL(20, 6) NULL
```

A blank source `VALUE` becomes SQL `NULL`. Zero remains numeric zero. The
natural unique key never includes a measure value.

### 9.2 Labels

Codes and names are stored once in `label`. Fact tables reference dimension
members through purpose-specific foreign keys such as `age_label_id`,
`citizenship_label_id`, and `industry_label_id`.

Every column ending in `_label_id` declares its own foreign key:

```sql
FOREIGN KEY (<label_column>) REFERENCES label(id)
```

`label_type` identifies the role of a label. Supported values are:

- `STATISTIC`
- `AGE`
- `SEX`
- `EDUCATION_LEVEL`
- `REGION`
- `CITIZENSHIP`
- `INDUSTRY`
- `EMPLOYEE_TYPE`
- `UNIT`

`classification_scheme` distinguishes labels that reuse the same code under
different standards. In particular, `QLF59` industries use `NACE_REV_2_1` and
`EHQ03` industries use `NACE_REV_2`.

The ingestion service must validate that every purpose-specific foreign key has
the expected `label_type`, classification scheme, and `dataset_label`
association. A standard foreign key confirms that a label exists but cannot
confirm those semantic rules by itself.

### 9.3 Time attributes

Time is stored directly as integers and is not represented in `label`:

| Table | Time attributes |
| --- | --- |
| `alf01` | `year INT` |
| `mum01` | `year INT`, `month INT` |
| `qlf50` | `year INT`, `quarter INT` |
| `qlf59` | `year INT`, `quarter INT` |
| `ehq03` | `year INT`, `quarter INT` |

The database does not store the source period code, period label, month-start
date, or quarter-start date. The API can derive display values such as `2025-03`
and `2025 Q2` from the integer attributes.

All fact tables check `year BETWEEN 2019 AND 2025`. Monthly data also checks
`month BETWEEN 1 AND 12`, and quarterly data checks
`quarter BETWEEN 1 AND 4`.

### 9.4 Current data only

The database does not store ingestion history. A refresh follows this process:

1. Read the filename, download URL, and local path from `data_source`.
2. Download the CSV and write retrieval metadata to the application log.
3. Parse and validate the complete file before modifying the database.
4. Upsert the distinct source vocabulary into `label` and `dataset_label`.
5. Select the required statistics and filter observations to 2019–2025.
6. Reject the refresh if a fact-table natural key appears more than once.
7. Begin a database transaction.
8. Delete the current rows from the matching fact table.
9. Batch-insert the complete validated snapshot.
10. Commit the transaction, or roll it back if a database operation fails.

Using `DELETE` and batch insert inside one InnoDB transaction avoids a partial
snapshot. `TRUNCATE` must not be used because MySQL treats it as DDL and it
cannot participate safely in the same rollback strategy.

## 10. Data Source Table

### 10.1 `data_source`

One row represents one of the five current CSV files used by LaborLens.

| Column | MySQL type | Purpose |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED AUTO_INCREMENT` | Primary key |
| `dataset_code` | `VARCHAR(16) NOT NULL` | Stable logical code such as `ALF01` |
| `file_name` | `VARCHAR(255) NOT NULL` | Downloaded filename such as `ALF01.csv` |
| `download_url` | `VARCHAR(1024) NOT NULL` | Official CSO download link |
| `download_path` | `VARCHAR(1024) NOT NULL` | Local or configured storage path |

Constraints:

```sql
PRIMARY KEY (id)

UNIQUE (dataset_code)

UNIQUE (file_name)
```

Each fact table stores a `data_source_id` foreign key:

```sql
FOREIGN KEY (data_source_id) REFERENCES data_source(id)
```

## 11. Label Tables

### 11.1 `label`

`label` is the shared vocabulary for all five CSV files. The ingestion process
loads every distinct statistic, dimension member, and unit found in those files,
including labels that are not exposed in the first frontend release.

| Column | MySQL type | Purpose |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED AUTO_INCREMENT` | Primary key |
| `label_type` | `VARCHAR(32) NOT NULL` | Semantic type such as `AGE`, `INDUSTRY`, or `UNIT` |
| `classification_scheme` | `VARCHAR(64) NOT NULL` | Classification namespace, such as `CSO`, `NACE_REV_2`, or `NACE_REV_2_1` |
| `label_code` | `VARCHAR(128) NOT NULL` | Original source code, or a stable application code for a unit |
| `label_name` | `VARCHAR(255) NOT NULL` | Original source name |
| `member_type` | `VARCHAR(16) NULL` | `AGGREGATE`, `DETAIL`, or `UNKNOWN` for dimension members |

Constraints:

```sql
PRIMARY KEY (id)

UNIQUE (
    label_type,
    classification_scheme,
    label_code
)

CHECK (
    label_type IN (
        'STATISTIC',
        'AGE',
        'SEX',
        'EDUCATION_LEVEL',
        'REGION',
        'CITIZENSHIP',
        'INDUSTRY',
        'EMPLOYEE_TYPE',
        'UNIT'
    )
)

CHECK (
    member_type IS NULL
    OR member_type IN ('AGGREGATE', 'DETAIL', 'UNKNOWN')
)
```

`member_type` is `NULL` for `STATISTIC` and `UNIT`. CSV files do not provide a
separate unit code, so ingestion assigns stable codes such as `PERCENT`,
`THOUSAND_PERSONS`, `PERSONS`, `EURO`, and `HOURS` while retaining the exact CSV
text in `label_name`.

### 11.2 `dataset_label`

`dataset_label` associates labels with a logical dataset code rather than a
numeric file ID. The dataset code immediately identifies the corresponding fact
table through the fixed mapping in Section 8.

| Column | MySQL type | Purpose |
| --- | --- | --- |
| `dataset_code` | `VARCHAR(16) NOT NULL` | Logical dataset code |
| `label_id` | `BIGINT UNSIGNED NOT NULL` | Label used by that dataset |

Constraints and index:

```sql
PRIMARY KEY (dataset_code, label_id)

FOREIGN KEY (dataset_code)
    REFERENCES data_source(dataset_code)

FOREIGN KEY (label_id)
    REFERENCES label(id)

INDEX idx_dataset_label_label (label_id)
```

The table contains no comma-separated table list and no physical table name.
A label shared by multiple datasets has one `label` row and one
`dataset_label` row for each dataset.

## 12. ALF01 Table

### 12.1 Purpose and grain

`alf01` stores annual employment rates. One row represents a year, age group,
sex category, education category, and NUTS 2 region.

### 12.2 Columns

| Column | MySQL type | Purpose |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED AUTO_INCREMENT` | Primary key |
| `data_source_id` | `BIGINT UNSIGNED NOT NULL` | Source file |
| `year` | `INT NOT NULL` | Calendar year parsed from `Year` |
| `age_label_id` | `BIGINT UNSIGNED NOT NULL` | `AGE` label |
| `sex_label_id` | `BIGINT UNSIGNED NOT NULL` | `SEX` label |
| `education_label_id` | `BIGINT UNSIGNED NOT NULL` | `EDUCATION_LEVEL` label |
| `region_label_id` | `BIGINT UNSIGNED NOT NULL` | `REGION` label |
| `employment_rate_percent` | `DOUBLE NULL` | Annual employment rate in percent |

Constraints and indexes:

```sql
PRIMARY KEY (id)

UNIQUE (
    data_source_id,
    year,
    age_label_id,
    sex_label_id,
    education_label_id,
    region_label_id
)

CHECK (year BETWEEN 2019 AND 2025)

INDEX idx_alf01_trend (
    age_label_id,
    sex_label_id,
    education_label_id,
    region_label_id,
    year
)

INDEX idx_alf01_region (region_label_id, year)
```

All four label columns and `data_source_id` have foreign keys to their shared
tables.

## 13. MUM01 Table

### 13.1 Purpose and grain

`mum01` stores the seasonally adjusted monthly unemployment rate. One row
represents a year, month, age group, and sex category. Only source statistic
`MUM01C02` is inserted into this fact table.

### 13.2 Columns

| Column | MySQL type | Purpose |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED AUTO_INCREMENT` | Primary key |
| `data_source_id` | `BIGINT UNSIGNED NOT NULL` | Source file |
| `year` | `INT NOT NULL` | Calendar year parsed from `Month` |
| `month` | `INT NOT NULL` | Calendar month from 1 through 12 |
| `age_label_id` | `BIGINT UNSIGNED NOT NULL` | `AGE` label |
| `sex_label_id` | `BIGINT UNSIGNED NOT NULL` | `SEX` label |
| `unemployment_rate_percent` | `DOUBLE NULL` | Monthly unemployment rate in percent |

Constraints and index:

```sql
PRIMARY KEY (id)

UNIQUE (
    data_source_id,
    year,
    month,
    age_label_id,
    sex_label_id
)

CHECK (year BETWEEN 2019 AND 2025)

CHECK (month BETWEEN 1 AND 12)

INDEX idx_mum01_trend (
    age_label_id,
    sex_label_id,
    year,
    month
)
```

## 14. QLF50 Table

### 14.1 Purpose and grain

`qlf50` stores quarterly employment rates. One row represents a year, quarter,
sex category, education category, and age group.

### 14.2 Columns

| Column | MySQL type | Purpose |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED AUTO_INCREMENT` | Primary key |
| `data_source_id` | `BIGINT UNSIGNED NOT NULL` | Source file |
| `year` | `INT NOT NULL` | Calendar year parsed from `Quarter` |
| `quarter` | `INT NOT NULL` | Calendar quarter from 1 through 4 |
| `sex_label_id` | `BIGINT UNSIGNED NOT NULL` | `SEX` label |
| `education_label_id` | `BIGINT UNSIGNED NOT NULL` | `EDUCATION_LEVEL` label |
| `age_label_id` | `BIGINT UNSIGNED NOT NULL` | `AGE` label |
| `employment_rate_percent` | `DOUBLE NULL` | Quarterly employment rate in percent |

Constraints and indexes:

```sql
PRIMARY KEY (id)

UNIQUE (
    data_source_id,
    year,
    quarter,
    sex_label_id,
    education_label_id,
    age_label_id
)

CHECK (year BETWEEN 2019 AND 2025)

CHECK (quarter BETWEEN 1 AND 4)

INDEX idx_qlf50_trend (
    age_label_id,
    sex_label_id,
    education_label_id,
    year,
    quarter
)

INDEX idx_qlf50_education (
    education_label_id,
    year,
    quarter
)
```

## 15. QLF59 Table

### 15.1 Purpose and grain

`qlf59` stores quarterly employment counts by citizenship and NACE Rev. 2.1
industry. One row represents a year, quarter, citizenship group, and industry.

### 15.2 Columns

| Column | MySQL type | Purpose |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED AUTO_INCREMENT` | Primary key |
| `data_source_id` | `BIGINT UNSIGNED NOT NULL` | Source file |
| `year` | `INT NOT NULL` | Calendar year parsed from `Quarter` |
| `quarter` | `INT NOT NULL` | Calendar quarter from 1 through 4 |
| `citizenship_label_id` | `BIGINT UNSIGNED NOT NULL` | `CITIZENSHIP` label |
| `industry_label_id` | `BIGINT UNSIGNED NOT NULL` | `INDUSTRY` label under `NACE_REV_2_1` |
| `employed_persons_thousands` | `DOUBLE NULL` | Employed persons in thousands |

Constraints and indexes:

```sql
PRIMARY KEY (id)

UNIQUE (
    data_source_id,
    year,
    quarter,
    citizenship_label_id,
    industry_label_id
)

CHECK (year BETWEEN 2019 AND 2025)

CHECK (quarter BETWEEN 1 AND 4)

INDEX idx_qlf59_citizenship (
    citizenship_label_id,
    year,
    quarter
)

INDEX idx_qlf59_industry (
    industry_label_id,
    year,
    quarter
)

INDEX idx_qlf59_combination (
    citizenship_label_id,
    industry_label_id,
    year,
    quarter
)
```

Rows from 2019–2020 remain stored with
`employed_persons_thousands = NULL`. The frontend starts the usable timeline at
2021Q1.

## 16. EHQ03 Table

### 16.1 Purpose and grain

`ehq03` stores quarterly earnings, paid-hours, employment, and labour-cost
measures by NACE Rev. 2 industry and employee type. One row represents a
statistic, year, quarter, industry, employee type, and unit.

### 16.2 Columns

| Column | MySQL type | Purpose |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED AUTO_INCREMENT` | Primary key |
| `data_source_id` | `BIGINT UNSIGNED NOT NULL` | Source file |
| `statistic_label_id` | `BIGINT UNSIGNED NOT NULL` | `STATISTIC` label |
| `year` | `INT NOT NULL` | Calendar year parsed from `Quarter` |
| `quarter` | `INT NOT NULL` | Calendar quarter from 1 through 4 |
| `industry_label_id` | `BIGINT UNSIGNED NOT NULL` | `INDUSTRY` label under `NACE_REV_2` |
| `employee_type_label_id` | `BIGINT UNSIGNED NOT NULL` | `EMPLOYEE_TYPE` label |
| `unit_label_id` | `BIGINT UNSIGNED NOT NULL` | `UNIT` label |
| `measure_value` | `DECIMAL(20,6) NULL` | Published value |

Constraints and indexes:

```sql
PRIMARY KEY (id)

UNIQUE (
    data_source_id,
    statistic_label_id,
    year,
    quarter,
    industry_label_id,
    employee_type_label_id,
    unit_label_id
)

CHECK (year BETWEEN 2019 AND 2025)

CHECK (quarter BETWEEN 1 AND 4)

INDEX idx_ehq03_trend (
    statistic_label_id,
    industry_label_id,
    employee_type_label_id,
    year,
    quarter
)

INDEX idx_ehq03_industry (
    industry_label_id,
    year,
    quarter
)
```

All 21 source statistics and all employee-type rows are stored. Backend
configuration exposes only the selected four measures and `All employees` in
the first frontend release.

## 17. Backend Presentation Configuration

The frontend still requires display wording, grouping, visibility, ordering,
and defaults. These rules belong in version-controlled backend code or
configuration rather than additional database tables.

A conceptual configuration entry is:

```yaml
datasets:
  QLF50:
    dimensions:
      education:
        "-":
          displayLabel: All education levels
          visibility: default
          default: true
        "10":
          displayLabel: Less than primary
          visibility: hidden
```

The backend validates configuration codes against `label` and `dataset_label`.
An unknown source label is logged and excluded from the public filter list until
its presentation rule is reviewed.

Availability is calculated from the relevant fact table using that table's
measure column with `IS NOT NULL`.

## 18. API Query Mapping

Each API view queries exactly one fact table:

| Frontend view | Fact table | Measure column |
| --- | --- | --- |
| Annual employment | `alf01` | `employment_rate_percent` |
| Monthly unemployment | `mum01` | `unemployment_rate_percent` |
| Quarterly employment | `qlf50` | `employment_rate_percent` |
| Citizenship and industry | `qlf59` | `employed_persons_thousands` |
| Earnings and labour costs | `ehq03` | `measure_value` |

Example annual trend query:

```sql
SELECT year, employment_rate_percent
FROM alf01
WHERE age_label_id = :ageLabelId
  AND sex_label_id = :sexLabelId
  AND education_label_id = :educationLabelId
  AND region_label_id = :regionLabelId
ORDER BY year;
```

Example EHQ03 trend query:

```sql
SELECT year, quarter, measure_value
FROM ehq03
WHERE statistic_label_id = :statisticLabelId
  AND industry_label_id = :industryLabelId
  AND employee_type_label_id = :employeeTypeLabelId
ORDER BY year, quarter;
```

The API joins `label` when it needs source names, applies configured display
wording, and returns JSON for interactive charts. CSV export uses the same query
result.

## 19. Scope Boundary

These five fact tables store only the current CSO statistical snapshot. Policy
documents, AI-extracted policies, and generated analyses require separate
schemas because they have different fields and validation rules.

Ingestion logs, checksums, timing information, and rejected-row details remain
outside MySQL in application logs and monitoring.
