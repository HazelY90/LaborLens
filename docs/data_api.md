# LaborLens Data Sources and APIs

## 1. Scope

LaborLens analyses Irish employment data and employment policy over the fixed
period from **2019 through 2025**, inclusive.

The project uses two official source groups:

1. Statistical data from the Central Statistics Office (CSO) PxStat API.
2. Policy documents from the Department of Enterprise, Tourism and Employment
   Statement of Strategy archive.

The Action Plan for Jobs series is excluded because it ends before the selected
analysis period. Records dated before 2019 or after 2025 must not be included in
the initial analytical dataset, even when a live API response contains them.

## 2. Processing Boundary

The source formats follow different processing paths:

```text
CSO PxStat CSV
    -> deterministic download and parsing
    -> validation and normalisation
    -> statistical calculations

Statement of Strategy PDF
    -> deterministic file and text extraction
    -> AI-assisted employment-policy extraction
    -> schema and evidence validation

Verified statistics + validated policy records
    -> AI-assisted combined analysis
    -> evidence validation and persistence
```

CSV values must not be extracted, rewritten, or estimated by a language model.
AI is used only for semantic policy extraction from PDFs and for interpreting
already validated statistics together with policy evidence.

## 3. Statistical Data: CSO PxStat

### 3.1 Publisher

The Central Statistics Office is Ireland's official statistics body. Its
PxStat portal provides machine-readable datasets in formats including CSV,
JSON-stat, PX, and XLSX.

- Portal: <https://data.cso.ie/>
- User guide: <https://www.cso.ie/en/databases/userguides/pxstatuserguide/>
- Discovery catalogue: <https://data.gov.ie/>

### 3.2 CSV API Pattern

Complete PxStat tables can be downloaded as CSV using the following endpoint
pattern:

```text
https://ws.cso.ie/public/api.restful/
PxStat.Data.Cube_API.ReadDataset/{TABLE_ID}/CSV/1.0/en
```

The URL is split above for readability. Runtime code must use it as one
continuous URL.

Example:

```http
GET https://ws.cso.ie/public/api.restful/PxStat.Data.Cube_API.ReadDataset/MUM01/CSV/1.0/en
```

The endpoints do not require an API key. Each response is a complete CSV table,
so LaborLens must filter the returned records to the 2019–2025 period during
ingestion.

### 3.3 Selected Tables

#### MUM01 — Seasonally Adjusted Monthly Unemployment

```text
https://ws.cso.ie/public/api.restful/PxStat.Data.Cube_API.ReadDataset/MUM01/CSV/1.0/en
```

Purpose:

- Monthly unemployment count.
- Monthly unemployment rate.
- Analysis by age group and sex.

Observed columns:

```text
STATISTIC
Statistic Label
TLIST(M1)
Month
C02076V02508
Age Group
C02199V02655
Sex
UNIT
VALUE
```

The endpoint currently contains records from January 1998 onward. LaborLens
will retain January 2019 through December 2025 only.

#### QLF50 — Quarterly Employment Rate

```text
https://ws.cso.ie/public/api.restful/PxStat.Data.Cube_API.ReadDataset/QLF50/CSV/1.0/en
```

Purpose:

- Quarterly employment-rate trends.
- Analysis by sex, age group, and education attainment level.

Observed columns:

```text
STATISTIC
Statistic Label
TLIST(Q1)
Quarter
C02199V02655
Sex
C04283V05060
Education Attainment Level
C02076V02508
Age Group
UNIT
VALUE
```

The endpoint currently begins at 2019Q1. LaborLens will retain 2019Q1 through
2025Q4.

#### ALF01 — Annual Employment Rate

```text
https://ws.cso.ie/public/api.restful/PxStat.Data.Cube_API.ReadDataset/ALF01/CSV/1.0/en
```

Purpose:

- Annual employment-rate summaries.
- Regional comparison within Ireland.
- Analysis by age, sex, education attainment level, and NUTS 2 region.

Observed columns:

```text
STATISTIC
Statistic Label
TLIST(A1)
Year
C02076V02508
Age Group
C02199V02655
Sex
C04283V05060
Education Attainment Level
C03788V04538
NUTS 2 Region
UNIT
VALUE
```

The endpoint contains the complete required annual period, 2019–2025.

#### QLF59 — Persons Aged 15 Years and Over in Employment

```text
https://ws.cso.ie/public/api.restful/PxStat.Data.Cube_API.ReadDataset/QLF59/CSV/1.0/en
```

Purpose:

- Quarterly employment counts.
- Analysis by citizenship and NACE Rev. 2.1 economic sector.

Observed columns:

```text
STATISTIC
Statistic Label
TLIST(Q1)
Quarter
C04004V04762
Citizenship
C04498V05282
NACE Rev 2.1 Economic Sector
UNIT
VALUE
```

The endpoint currently begins at 2019Q1. LaborLens will retain 2019Q1 through
2025Q4.

#### EHQ03 — Earnings, Hours, Employment, and Labour Costs

```text
https://ws.cso.ie/public/api.restful/PxStat.Data.Cube_API.ReadDataset/EHQ03/CSV/1.0/en
```

Purpose:

- Quarterly earnings and working-hours measures.
- Employment and labour-cost measures.
- Analysis by NACE Rev. 2 economic sector and employee type.

Observed columns:

```text
STATISTIC
Statistic Label
TLIST(Q1)
Quarter
C02665V03225
Economic Sector NACE Rev 2
C02397V02888
Type of Employee
UNIT
VALUE
```

This is a comparatively large table. The verified response contains more than
130,000 records and begins at 2008Q1. LaborLens will retain 2019Q1 through
2025Q4 and should stream the download rather than loading the entire response
into memory as a single string.

### 3.4 Data Catalogue API

Ireland's national open-data portal uses CKAN and can be used to discover CSO
tables and their resource URLs.

```http
GET https://data.gov.ie/api/3/action/package_search?q=employment
GET https://data.gov.ie/api/3/action/package_show?id={dataset-name}
GET https://data.gov.ie/api/3/action/resource_search?query=name:{keyword}
```

The CKAN API is a discovery and metadata source. Once a table ID has been
approved for LaborLens, the ingestion pipeline should use the direct PxStat URL
as the stable content endpoint while retaining the related CKAN metadata.

### 3.5 CSV Ingestion Rules

- Accept only observations from 2019 through 2025, inclusive.
- Retain source dimension codes as well as readable labels.
- Preserve the original statistic code, unit, and period value.
- Treat an empty `VALUE` as missing data, not zero.
- Remove the optional UTF-8 byte-order mark from the first header name.
- Validate period syntax before converting it to an internal date or period.
- Do not combine counts, percentages, hours, and euro values as if they shared a
  unit.
- Preserve the source table ID on every observation.
- Record retrieval time, response checksum, source URL, and source metadata
  modification time.
- Use deterministic duplicate keys derived from table ID, statistic code,
  period, dimension codes, and unit.

## 4. Policy Reports: Statement of Strategy

### 4.1 Publisher and Index

The policy corpus comes from the Department of Enterprise, Tourism and
Employment Statement of Strategy archive.

- Official archive page:
  <https://www.gov.ie/en/department-of-enterprise-tourism-and-employment/publications/statement-of-strategy/>
- Department publication sitemap:
  <https://enterprise.gov.ie/sitemap.xml>

There is no documented public API dedicated to these reports. The files are
official static PDFs. The initial source registry should therefore store an
explicit allowlist of approved PDF URLs. The archive page and sitemap can be
checked periodically to discover later versions, but a newly discovered file
must not enter the analytical corpus until it passes source validation.

### 4.2 Selected Reports

The following English-language reports overlap the 2019–2025 analysis period:

| Strategy period | PDF URL | In-scope policy years |
| --- | --- | --- |
| 2018–2021 | <https://enterprise.gov.ie/en/publications/publication-files/statement-of-strategy-2018-2021.pdf> | 2019–2021 |
| 2021–2023 | <https://enterprise.gov.ie/en/publications/publication-files/statement-of-strategy-2021-2023.pdf> | 2021–2023 |
| 2023–2025 | <https://enterprise.gov.ie/en/publications/publication-files/statement-of-strategy-2023-2025.pdf> | 2023–2025 |
| 2024–2025 | <https://enterprise.gov.ie/en/publications/publication-files/statement-of-strategy-2024-2025.pdf> | 2024–2025 |
| 2025–2028 | <https://enterprise.gov.ie/en/publications/publication-files/statement-of-strategy-2025-2028.pdf> | 2025 only |

All five URLs were verified as direct `application/pdf` responses. Although the
first and last documents extend beyond the project window, only policy evidence
applicable to 2019–2025 may be used in the initial analysis.

### 4.3 Policy Content to Extract

The policy-extraction schema will be finalised after representative PDFs are
profiled. The initial extraction target is:

```text
policyTitle
strategyPeriod
applicableFrom
applicableTo
strategicGoal
objective
policyAction
targetGroup
responsibleBody
expectedOutcome
performanceIndicator
employmentTheme
evidenceText
pageNumber
sourceDocumentId
```

Employment themes may include:

- Employment growth and job quality.
- Regional employment and enterprise development.
- Skills, workforce capability, and productivity.
- Workplace relations, employment rights, and safe working conditions.
- Labour-market participation and economic migration.
- Digital and green transitions affecting employment.
- Support for domestic enterprise and foreign investment.

These themes are classification candidates, not assumed source fields. They
must be adjusted after the documents are profiled.

### 4.4 PDF Ingestion Rules

- Download only from the approved official domain and URL registry.
- Preserve the original PDF unchanged.
- Record the document title, strategy period, publisher, URL, retrieval time,
  file size, media type, and SHA-256 checksum.
- Extract text with page boundaries before sending content to AI.
- Use OCR only when a page has no usable embedded text.
- Require structured model output that matches the policy schema.
- Preserve a short supporting passage and page number for every extracted
  policy record.
- Treat model output as untrusted until schema and evidence checks pass.
- Store model name, model parameters, prompt version, schema version, token
  usage, and extraction timestamp.
- Keep overlapping strategy documents as separate sources. Do not silently
  merge similar policies across editions.
- Do not use policy statements applying only after 2025 in the initial analysis.

## 5. Combined Analysis

The combined-analysis service receives only:

1. Statistics calculated deterministically from validated CSO observations.
2. Policy records that passed schema and source-evidence validation.
3. A bounded analytical question and an explicit 2019–2025 time range.

The model may explain relationships, changes, alignment, or gaps between the
statistics and policies. It must not invent missing measurements, claim that a
policy caused a statistical change, or present correlation as causation.

Every stored analysis must retain:

```text
analysisPeriod
statisticalObservationIds
calculationDefinition
policyRecordIds
sourceDocumentIds
promptVersion
modelName
modelParameters
generatedAt
findings
limitations
evidenceReferences
validationStatus
```

## 6. Initial Source Priority

The recommended implementation order is:

1. `ALF01` for a compact annual 2019–2025 employment-rate dataset.
2. `MUM01` for monthly unemployment trends.
3. Statement of Strategy 2018–2021, 2021–2023, and 2023–2025 PDFs.
4. `QLF50` for detailed quarterly employment-rate analysis.
5. Statement of Strategy 2024–2025 and the 2025 portion of 2025–2028.
6. `QLF59` for citizenship and sector analysis.
7. `EHQ03` for earnings, hours, and labour-cost analysis after the ingestion
   pipeline has been tested with smaller tables.

This order provides an end-to-end vertical slice before introducing the largest
table or the full document corpus.

## 7. Verification Date

The API endpoints, direct PDF URLs, response formats, and observed coverage in
this document were checked on **23 September 2026**. External sources may be
revised, so ingestion must detect content changes rather than assume that a URL
always returns identical bytes.
