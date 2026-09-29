# Data Ingestion

Configuration: [Backend README](../backend/README.md). Sources: [Data APIs](data_api.md).
Tables and enums: [Schema Design](schema_design.md).

## Workflow

One manual launch runs `JobRunner` through three sequential stages:

1. `filePrepare`: prepare all four CSVs and five PDFs, then upsert `source_file`.
   Reuse valid local files; download missing, invalid or explicitly refreshed files.
   Validate CSV contents and PDF signatures/size (under 50 MB) before publishing.
2. `metricsExtraction`: import each CSV into its corresponding table.
3. `policyExtraction`: process each complete PDF in one AI request and write its policies.

| CSV (in execution order) | Table |
| --- | --- |
| `ALF01.csv` | `annual_employment_rate` |
| `MUM01.csv` | `monthly_unemployment_rate` |
| `QLF50.csv` | `quarterly_employment_rate` |
| `QLF59.csv` | `quarterly_employment_count` |

PDF strategy periods run in order: 2018–2021, 2021–2023, 2023–2025, 2024–2025,
2025–2028. All write to `policy`, linked by `source_file_id`.
Source filenames, URLs and table mappings are defined in `CsvSource` and `PdfSource`.

## Processing

- **CSV:** select configured statistics/categories for 2019–2025, map labels to
  enums, skip blank values and retain zero. Reject invalid values, unknown mappings,
  duplicate keys, incomplete source period coverage and empty results.
  Period coverage includes rows with missing values; individual observations may be absent. Replace each table in one transaction.
- **PDF:** `PolicyAi` sends the original PDF as a Base64 attachment, source filename,
  strategy period, and both JSON configurations from `policyExtraction/promts/`:
  `prompt.json` and `outputStructure.json`. It validates the returned fields,
  source, period, type, positive page number and quote length. Truncated, malformed
  or empty results fail. Quotes and pages are retained for review, not verified locally.
  Replace only that source's policies in one transaction after validation.
- **Timeout:** `app.jobs.pdf.timeout-seconds=600` controls the Job deadline and
  explicit OpenAI request timeout; the ingestion profile also aligns the client timeout.

## Runs and files

- `JobStore` manages MySQL locks, transactions and `job_run` statuses:
  `RUNNING`, `SUCCESS`, `FAILED`, `SKIPPED`. A failure stops the pipeline;
  earlier committed files remain intact.
- CSV/PDF processing skips inputs matching the latest successful run's checksum
  and processing version, unless forced. Rerun the same command to retry failures.
- `DATA_DIR` defaults to `../data`: CSVs use `cso/`, PDFs use `policies/`, and
  immutable inputs/results use `runs/<job_run.id>/`. Policy artifacts include
  prompts, raw responses, model metadata and `evidence.json` linking quotes to policy IDs.
- When resetting the database and run IDs, also clear or move the corresponding
  `runs/` directory to avoid archive collisions.

## Run

Run Flyway migrations first as shown in the [Backend README](../backend/README.md),
then run from `backend/`:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=ingestion
```

The process exits after completion. Normal application startup does not run jobs.
To download fresh files and force reprocessing:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=ingestion -Dspring-boot.run.arguments="--app.jobs.refresh=true --app.jobs.force=true"
```
