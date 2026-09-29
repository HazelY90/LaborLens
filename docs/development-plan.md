# LaborLens Development Plan

## Direction and Current Position

Build an Irish employment dashboard combining verified statistics and traceable
policy evidence for 2019–2025. Use a Spring Boot modular monolith, MySQL, and a
Next.js frontend. CSV processing and numeric calculations are deterministic;
AI is reserved for policy extraction and evidence-grounded interpretation.

Source selection, CSV profiling and an initial review of all five policy PDFs
are documented. Statistical tables, the six-column policy table, five policy
types, the shared source-file registry with checksums, and job execution history
are designed. A manual ingestion runner now executes file preparation for all
nine sources, CSV processing for the four statistical tables, then AI policy
extraction for the five reports. The jobs include run history, validation,
transactional replacement and evidence artifacts. Live download/model verification,
database integration verification, business APIs and dashboard features remain
work. The V1 database
migration is complete, and an isolated migration test is implemented. The analysis schema and
exhaustive policy extraction remain to be completed.

- [Data Sources and APIs](data_api.md): approved sources, ingestion rules, and
  evidence contracts.
- [Data Ingestion Jobs](data_ingestion.md): file handling, validation, refreshes
  and runtime directory permissions.
- [Schema Design](schema_design.md): tables, enum mappings, constraints, and
  statistical interpretation rules.

## 1. Complete Source Validation

The selected sources are four CSO datasets and five Statement of Strategy PDFs.
Confirm licences, release frequency, revision behaviour, repeatable retrieval,
and PDF text quality. Record file checksums and retrieval metadata.

**Done when:** every source can be retrieved reproducibly and the selected
statistics and policies have sufficient topic and time overlap for analysis.

## 2. Complete the Data Contracts

Implement the documented statistical and policy schemas, source-file mappings
and enum labels. Formalise extraction artifacts with page-level evidence and
rejection/review rules, and define a machine-validatable analysis result. Version
schemas and prompts.

**Done when:** observation identity, source attribution, validation rules, and
policy/analysis evidence requirements are explicit and testable.

## 3. Build the Pipelines

Use one manual entry point to execute three stages in order. First validate,
reuse or download all four CSV files and all five PDFs, and register them in
`source_file`. After all files are prepared, process ALF01, MUM01, QLF50 and
QLF59 sequentially, writing each to its corresponding statistical table using
the mappings in [Schema Design](schema_design.md).

Next, process all five policy PDFs for 2018–2021, 2021–2023, 2023–2025,
2024–2025 and 2025–2028. Extract page-aware text, call the AI API to extract
policies, validate the output and evidence, and write the results to `policy`
with the corresponding `source_file_id`.

Complete ingestion of all four CSV files and all five PDFs before developing
trend-query APIs and combined analysis.

- Implement acquisition, registration, CSV import and PDF extraction according
  to [Data Ingestion Jobs](data_ingestion.md).

**Done when:** repeat imports do not duplicate observations, failed refreshes
preserve existing data, all four statistical tables contain validated observations,
and policies from all five PDFs retain their source evidence. Invalid model output
must be rejected or flagged. Test representative success and failure
cases, including missing/invalid values, duplicate keys, and rollback.

## 4. Implement Backend Services

Build repositories and services for sources, statistics, policies, analyses, and
accounts. Expose versioned APIs for filters, trends, supported calculations,
policy browsing, and saved or requested analyses. Return units, source references,
data availability, pagination, and consistent errors.

For combined analysis, calculate statistics in Java/SQL, select relevant
validated policies, generate findings, and verify their evidence references.
Each saved analysis must retain its exact statistical inputs and policy evidence.

Implement registration, login, profile updates, password changes, secure password
hashing, and JWT authentication. Keep credentials outside version control and
client responses. Apply model-call timeouts, retries, rate limits, and cost
controls. Document local configuration and API contracts.

**Done when:** database-backed API tests and model-client test doubles cover core
queries and failures; the frontend does not need to recreate calculations or
prompts.

## 5. Build and Deploy the Dashboard

Create responsive trend charts, tables, filters, policy browsing, analysis views,
and account screens. Use backend data availability to handle unsupported filter
combinations. Display missing periods as gaps and distinguish calculated facts,
policy evidence, and generated interpretation, with source links and limitations.

Test accessibility, loading/error states, and critical interactions. Verify
production builds and document deployment using EC2, RDS, and Nginx where needed.

**Done when:** a user can inspect a trend, find related policy evidence, and read
an analysis while retaining access to the original sources.

## Engineering Priorities

- Enums hold stable names and display labels only; the backend supplies labels
  to the frontend. Keep ordering, defaults, visibility, aggregate relationships
  and dataset restrictions in application code.
- Keep provenance and validation central to ingestion and AI workflows.
- Add infrastructure only for demonstrated needs; avoid speculative queues,
  caches, vector stores, or separate services.
- Update these documents when source findings or implementation decisions change.
