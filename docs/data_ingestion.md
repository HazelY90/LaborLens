# Data Ingestion Jobs

This document defines job execution, source registration, validation and refresh
rules. Approved sources are listed in [Data Sources and APIs](data_api.md);
tables, allowed values and source mappings are defined in
[Schema Design](schema_design.md).

## Job boundaries

Three independently triggered jobs process one file per run:

- `FILE_PREPARATION`: reuse/download, validate and register the file.
- `CSV_IMPORT`: validate local CSV observations and write statistical data.
- `POLICY_EXTRACTION`: extract local PDF text, call the AI API, validate evidence
  and write policies.

File preparation precedes either processing job. CSV import and policy
extraction are independent and do not need to download again for each run.

## File acquisition and registration

1. Read approved source URLs, filenames and destination tables from application
   configuration. Source registration is not seeded by Flyway.
2. Resolve the source path under `app.data.root`. Reuse an existing valid file
   unless refresh is explicitly requested. Otherwise download a replacement to
   a temporary file in the same directory.
3. Validate content before publishing: CSV headers, expected statistic and
   parseability; PDF signature and readable document structure. HTTP success or
   a matching extension alone is insufficient. Record the SHA-256 checksum and
   provenance; compare checksums on refresh because a stable URL may return
   revised content.
4. Publish validated downloads with an atomic file replacement where supported.
   Upsert `source_file` by filename, preserving its ID and updating `checksum`
   to the validated file SHA-256. Existing local files also
   pass validation and registration. Download/validation failures leave the
   previous valid file and registry untouched. Log registration failures and retry
   idempotently against the validated file; filesystem and database writes do not
   form one transaction.
5. Complete file preparation. Separate jobs import CSV observations or extract
   PDF policies using the rules below. A source row records an available file,
   not import success; processing failures preserve the previous database snapshot.

Log source identity, retrieval time when downloaded, source modification time
when available, checksum, run status, counts and failures.

Begin with manually triggered runs. Do not download every file on application
startup or add scheduled refreshes until needed. Keep source versions/checksums
and extraction evidence in run artifacts so refreshed files do not erase the
provenance of previous analyses.

## CSV Ingestion Rules

1. Handle the optional UTF-8 BOM and trim surrounding whitespace. Validate
   headers and complete period syntax, derive year/month/quarter, and filter to
   2019–2025. Keep source codes as strings during parsing.
2. Select the fixed statistic and validate its unit. Apply QLF59's category
   allowlists. For ALF01 and QLF50, exclude `Less than primary (Level 0)` and
   `Level of education - not stated` regardless of whether `VALUE` is present.
   Map the retained labels to backend enum constants; reject unknown mappings
   or invalid required dimensions. Log deliberately excluded categories
   separately from missing values and errors.
3. **Store only valid, non-missing `VALUE` observations.** Skip blank values after
   validation and log their count; retain numeric zero. A nonblank invalid value
   is an error, not a missing value. Never estimate or fill values using AI.
4. Validate finite numeric values and measure bounds. Check natural-key
   uniqueness after category mapping; reject duplicate keys before writing.
5. Log retained, excluded, missing, and rejected counts separately. Validate
   expected coverage; a failed validation or unexpectedly empty import leaves
   the current database snapshot unchanged.
6. Resolve `source_file.table_name` through the fixed file/table allowlist.
   Replace that table's snapshot using `DELETE` and batch insert inside one
   InnoDB transaction; roll back on failure. Do not use `TRUNCATE`.

Repeated imports must yield the same semantic observations without duplicates.
Source codes and original labels remain in raw files and parsing mappings;
observations store semantic enum values. An absent observation means unavailable
data. APIs return `null` for missing periods rather than zero.

Local import reference after statistic/category selection:

| File | Selected source rows | Missing rows skipped | Rows stored |
| --- | ---: | ---: | ---: |
| ALF01.csv | 3,360 | 522 | 2,838 |
| MUM01.csv | 756 | 0 | 756 |
| QLF50.csv | 3,696 | 273 | 3,423 |
| QLF59.csv | 560 | 201 | 359 |

These counts describe the local sample, not immutable requirements for future
source revisions.

## PDF Ingestion Rules

- Apply the common file acquisition and registration workflow. Preserve original
  PDFs with title, publisher, strategy period, URL, retrieval time, file size,
  media type and SHA-256 checksum.
- Extract page-aware text deterministically; use OCR only for pages without
  usable embedded text.
- Store one concrete action or target per `policy` row using `period_start`,
  `period_end`, `policy`, `type` and `source_file_id`, plus the generated ID. Use
  the five [PolicyType categories](schema_design.md#policytype). Periods retain
  the source strategy years; explicit deadlines remain in the policy text.
- Require structured AI output with a source file, supporting excerpt and page
  reference for every policy. Preserve evidence in extraction artifacts linked
  to stored records. Validate column reading order, quote matches, periods and
  type labels; reject or flag unsupported or invalid output.
- Preserve model settings, prompt/schema versions, token usage, and extraction
  timestamp. Keep overlapping report editions distinct and use only evidence
  applicable to 2019–2025. Replace policies only for the current source file
  after validation, within one transaction; repeated imports must not duplicate
  records. Unknown budgets or effective dates do not invalidate supported actions.

## Run history and retries

- Create and commit a `job_run` in `RUNNING` state before work begins. Use UTC
  timestamps and retain one row for every attempt, including retries and skips.
  Initial preparation can fail before a source row or checksum exists. The file
  name still identifies the target. Import/extraction require a registered file.
- Read and hash the actual input; preserve that checksum in the run. Process an
  unchanged snapshot and coordinate preparation/import/extraction for the same
  file so refreshes cannot replace it during processing.
- `source_file.checksum` describes the current validated file. `job_run.checksum`
  describes the input to that particular attempt; never update old runs when a
  file changes. A failed preparation must not assign its candidate checksum to
  the current source row.
- Before CSV import or policy extraction, check successful runs for the same
  source, job type, checksum and process version. A skip is valid only if that
  combination still represents the latest successfully committed snapshot for
  this source/job, not merely any historical success. Otherwise process again.
  A force option bypasses the skip. Mark skips `SKIPPED` with a finish time.
- `process_version` identifies parser/mapping rules for CSV and the model,
  settings, prompt and schema combination for policy extraction. Keep the full
  configuration in run artifacts keyed by run ID. Changing that configuration
  requires a new version even if file bytes are unchanged.
- Perform downloads, parsing and API calls outside database write transactions.
  For CSV/policy jobs, replace data and set `SUCCESS`, committed `row_count` and
  finish time in the same transaction. On failure, roll back the replacement,
  then mark the existing run `FAILED` in a separate transaction with a sanitised
  error. File preparation commits source registration and success together after
  file validation/publication; its `row_count` is null.
- Record a finish time for every terminal status. A crashed process may leave a
  `RUNNING` record; confirm it is no longer active before marking it failed and
  starting a new attempt. Serialize runs for the same source/job; a success
  lookup alone does not prevent simultaneous duplicate processing.

## Data directory and permissions

`app.data.root` reads `DATA_DIR` and defaults to `../data`. This is an external
filesystem directory, not a classpath resource. Launching from `backend/` resolves
it to the repository's `data/`. Store `cso/<filename>` or `policies/<filename>` in
`source_file.download_path`, relative to the configured root.

The job uses the operating-system permissions of the backend process. Java does
not restrict access to the backend project directory. The runtime account needs
read access to reused files, directory traversal access, and write access for
new downloads, temporary files and replacements. Check access before processing
and report permission errors without changing permissions automatically.

For deployment, set an absolute `DATA_DIR` appropriate to the runtime account.
For containers, mount a writable data volume at that path. A host repository
folder is not automatically visible inside a container. Keep downloaded files
outside packaged application resources.

The directory configuration is implemented. The job and its source configuration
binding, validation and import/extraction services remain implementation work.
