# LaborLens

LaborLens is a full-stack employment intelligence platform focused on Ireland.
It combines official employment statistics with employment-policy reports
published by Irish government departments, then uses generative AI to produce
evidence-based analysis grounded in both quantitative data and policy context.

## Project Direction

The project has two source-processing paths and one combined analysis path:

```text
Official CSV datasets
        -> deterministic parsing and validation
        -> normalisation and statistical storage
        -> verified statistical results -------------------\
                                                            -> AI analysis
Official PDF reports
        -> text extraction
        -> AI policy extraction
        -> schema validation and policy storage
        -> validated policy records ------------------------/

AI analysis -> validated analysis results -> REST APIs -> dashboard
```

CSV data is not sent to a model for extraction. Structured files are processed
with deterministic code so that ingestion remains reproducible, testable, and
cost-efficient. Generative AI is used where semantic interpretation is needed:
extracting employment policies from PDF reports and analysing those policies
alongside verified statistical results.

The initial statistical scope is ALF01, MUM01, QLF50, and selected QLF59
categories for 2019–2025. EHQ03 is excluded following the missing-value review.
The [Schema Design](docs/schema_design.md) defines four semantic fact tables,
backend enum dimensions, a policy table, and a shared source-file registry.
Combined analytical operations still require further design.

## Project Plan

Development is divided into five stages:

1. Confirm official Irish government CSV datasets and PDF policy reports.
2. Profile the available content and design schemas for statistical
   observations, source documents, extracted policies, and AI analyses.
3. Build repeatable CSV ingestion, PDF policy extraction, and combined analysis
   pipelines.
4. Develop backend APIs, analytical services, AI orchestration, and basic
   account features.
5. Develop the frontend dashboard for employment trends, policy exploration,
   and AI-generated analysis.

See [Development Plan](docs/development-plan.md) for detailed tasks,
deliverables, and completion criteria.

## Technical Priorities

- Use official Irish government publications as the source of both statistical
  data and policy evidence.
- Process CSV files deterministically without using AI for field extraction.
- Preserve original PDF reports and use AI only to extract policy meaning into
  a defined, validated structure.
- Retain provenance from every statistic and extracted policy back to its
  source, including document location or page references where available.
- Calculate numeric results in Java or SQL before asking AI to interpret them.
- Store model, prompt, schema, and source versions for reproducible AI runs.
- Validate AI output and make uncertainty or unsupported conclusions visible.
- Keep ingestion idempotent, with duplicate detection and clear failure
  reporting.
- Prefer a modular monolith and add infrastructure only for demonstrated needs.

## Technology Stack

### Backend

- Java
- Spring Boot
- Spring Data JPA
- Spring AI with OpenAI
- RESTful APIs
- JWT authentication
- Maven

### Data and AI Processing

- MySQL
- CSV parsing selected according to the confirmed source format
- PDF text extraction, with OCR only when required by scanned reports
- Structured AI output for employment-policy extraction
- Schema and business-rule validation
- Deterministic statistical calculations in Java and SQL
- AI synthesis of validated employment data and extracted policy evidence
- Scheduled refresh support after the pipelines are stable

### Frontend

- TypeScript
- React
- Next.js
- Tailwind CSS
- A charting library selected after the required visualisations are known

### Deployment

- AWS EC2
- AWS RDS
- Nginx when required for routing or static asset delivery

## Initial Scope

The first usable release is expected to include:

- Four official Irish employment datasets available as CSV, with selected
  indicators and categories.
- A small collection of official PDF reports containing employment policies.
- Repeatable CSV ingestion with validation, enum mapping, and transactional
  snapshot replacement.
- AI-assisted policy extraction with source references and validation.
- Deterministic statistical calculations over the employment data.
- AI-generated analysis grounded in selected statistics and policy records.
- REST APIs and an interactive dashboard for trends, policies, and analyses.
- Basic account management and a deployed application.

Authentication is supporting functionality rather than the main focus. The
engineering depth of LaborLens should come primarily from reliable source
processing, policy extraction, traceable AI analysis, statistical modelling,
and interactive visualisation.

## Database Migrations

Flyway migrations are in `backend/src/main/resources/db/migration`:

- `V1__create_schema.sql`: source registry with file checksums, four statistical
  tables, policies and per-file job execution history,
  including indexes, category checks, measure bounds and the policy source key.

Flyway leaves `source_file` empty. Job workflows, source registration, external
data directory configuration and access requirements are documented in
[Data Ingestion Jobs](docs/data_ingestion.md). Job implementation remains pending.

Use MySQL 8.0.16 or later. Start the backend with `backend/` as the working
directory so Spring reads its untracked `.env` as a properties file. Variable
names are listed in `backend/env.txt`. Flyway uses `flyway.url`, `flyway.user`
and `flyway.password`, falling back to `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`
when those properties are absent. Omit unused overrides rather than leaving
URL/user overrides blank. Keep the migration and application accounts on the
same database, with schema-change privileges granted to the migration account.

Application startup applies pending migrations before Hibernate validation.
Automatic baseline and database cleaning are disabled. Do not edit a migration
that has already been applied; add a new version instead. MySQL DDL is not fully
transactional, so inspect any failed migration before attempting recovery.

With Docker running and the MySQL 8.4 image available, run the isolated migration
test from `backend/`:

```bash
./mvnw -Dtest=FlywayMigrationTests test
```

The test migrates a disposable database, checks repeat execution, an empty source registry, job-style source fixtures,
valid observations and rejected invalid values, and verifies the policy foreign
key. It is skipped when Docker is unavailable; a skipped test does not verify
SQL execution. The test does not connect to the database configured in `.env`.
