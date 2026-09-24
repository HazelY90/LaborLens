# LaborLens Development Plan

## Planning Principle

LaborLens is limited to employment data and employment-policy publications from
Irish government departments. The final statistical dimensions, policy fields,
analytical methods, and visualisations must reflect the sources that can
actually be retrieved and validated.

The implementation must distinguish deterministic data processing from
generative AI work:

- CSV statistics are parsed, validated, transformed, and stored without AI
  extraction.
- PDF reports are converted to usable text, then AI extracts employment-policy
  information into a defined schema.
- Numeric analysis is calculated in Java or SQL.
- AI receives selected, verified statistics and extracted policy evidence to
  produce grounded analysis.

Each stage must leave documented decisions and a verifiable result for the
following stage.

## 1. Confirm the Data Sources

Identify official Irish government sources that provide employment statistics
as CSV files and employment-policy reports as PDFs.

### Tasks

- Identify the publishing department, dataset or report owner, canonical URL,
  licence, release frequency, and update history for each source.
- Confirm a repeatable download method for CSV datasets and PDF reports.
- Inspect representative files rather than relying only on catalogue
  descriptions.
- Determine whether CSV links are stable and whether historical releases remain
  available.
- Determine whether PDFs contain selectable text, tables, or scanned pages that
  require OCR.
- Select a deliberately small initial set of related datasets and reports.
- Record source versions, publication dates, retrieval timestamps, and file
  checksums.

### Deliverables

- Source decision record with rationale.
- Links to selected datasets, reports, and documentation.
- Representative CSV and PDF samples.
- Update-frequency and access notes.
- Known limitations and open questions.

### Completion Criteria

- Every initial source is an official Irish government publication.
- Every selected file can be retrieved through a documented, repeatable method.
- The selected statistics and reports have enough subject overlap to support a
  meaningful combined analysis.

## 2. Analyse the Available Content and Define the Schemas

Profile the CSV datasets and PDF reports before committing to the persistence
model or AI extraction contract.

### CSV Data Tasks

- Catalogue columns, data types, code lists, optional values, units, periods,
  geographic coverage, and demographic or employment dimensions.
- Examine missing values, suppression markers, provisional values, revisions,
  duplicates, and inconsistent records.
- Define the natural identity of each observation and its update behaviour.
- Define deterministic field mappings, transformations, validation rules, and
  query indexes.

### PDF and Policy Tasks

- Catalogue report metadata, document structure, page layout, text quality, and
  recurring policy sections.
- Define a policy extraction schema only after examining representative
  reports. Candidate fields may include policy title, objective, target group,
  intervention, responsible body, timeframe, funding, expected outcome, status,
  evidence excerpt, and page reference.
- Decide which fields are required, optional, directly quoted, inferred, or not
  reliably extractable.
- Define rules for rejecting, retrying, or flagging incomplete AI output.
- Define how prompt versions, model details, and extraction runs are stored.

### Combined Analysis Tasks

- Identify statistical measures that can be calculated reliably from the CSV
  data.
- Define the evidence package sent to AI: calculated values, applicable policy
  records, time range, source identifiers, and analytical question.
- Define an analysis-result schema including narrative findings, cited evidence,
  limitations, and generation metadata.
- Define checks that prevent unsupported statistics or policies from appearing
  in generated analysis.

### Deliverables

- CSV profiling report and statistical schema.
- Source-document and policy-extraction schemas.
- AI analysis-result schema.
- Field mappings and validation rules.
- Database schema and migration plan.
- Initial prompt and evaluation specifications.

### Completion Criteria

- The schemas preserve provenance from stored data and policies back to the
  original file and publication.
- Observation identity and CSV update behaviour are explicit and testable.
- Policy and analysis outputs have machine-validated structures.
- Proposed analysis is supported by overlapping statistical and policy content.

## 3. Build the Data Acquisition and Analysis Pipelines

Implement three connected pipelines: CSV ingestion, PDF policy extraction, and
combined AI analysis.

### CSV Ingestion Pipeline

- Download CSV files and record source metadata and checksums.
- Parse records without model involvement.
- Validate fields, types, codes, units, values, and relationships.
- Transform and normalise accepted records according to the mapping
  specification.
- Persist metadata and observations with deterministic duplicate handling and
  idempotent upserts.
- Store rejected records with actionable reasons.

### PDF Policy Extraction Pipeline

- Download and preserve the original PDF with publication metadata and a
  checksum.
- Extract page-aware text with a deterministic parser; introduce OCR only for
  scanned pages that need it.
- Split content into sections or page ranges suitable for model input.
- Ask the model to extract employment policies into the agreed structured
  schema.
- Validate each response against the schema and business rules.
- Preserve page references, supporting text, model name, prompt version,
  extraction time, and token usage.
- Mark low-confidence or incomplete results for review rather than silently
  treating them as verified facts.

### Combined Analysis Pipeline

- Calculate trends, changes, summaries, or other supported measures in Java or
  SQL.
- Select policy records that match the analysis subject and time context.
- Build a bounded evidence package containing only verified statistics and
  traceable policy records.
- Ask the model to generate structured findings grounded in that evidence.
- Validate that cited data points and policy references exist in the supplied
  evidence.
- Store the generated result with all input references, model settings, prompt
  version, timestamps, and limitations.

### Shared Pipeline Requirements

- Track every run, including source version, status, timestamps, counts, and
  errors.
- Make repeated processing idempotent where appropriate.
- Add automated tests using representative fixtures and failure cases.
- Start with manually triggered runs and introduce scheduling only after each
  workflow is reliable.

### Deliverables

- Executable CSV ingestion pipeline.
- Executable PDF policy extraction pipeline.
- Executable combined analysis pipeline.
- Run history, source provenance, model metadata, and error reporting.
- Repeatable local dataset setup and automated tests.
- Operational documentation for running and troubleshooting each pipeline.

### Completion Criteria

- Reprocessing the same CSV does not create duplicate observations.
- CSV values are never inferred or rewritten by a language model.
- Every extracted policy can be traced to a report and document location.
- Every generated analysis can be traced to its exact statistics, policies,
  prompt version, and model configuration.
- Invalid model output is rejected or flagged instead of being silently stored
  as trusted analysis.

## 4. Develop the Backend

Expose sources, statistics, policies, and generated analyses through a
maintainable Spring Boot application.

### Tasks

- Organise the application as a modular monolith with clear source-ingestion,
  statistics, policy-extraction, AI-analysis, and account boundaries.
- Implement persistence repositories and query services.
- Provide endpoints for available indicators, periods, dimensions, reports,
  policies, and source metadata.
- Provide endpoints for supported statistical calculations and saved AI
  analyses.
- Add an endpoint or controlled workflow for requesting a new analysis from a
  bounded set of data and policy inputs.
- Add filtering, pagination, stable response models, and consistent errors.
- Add registration, login, profile updates, password changes, secure password
  hashing, and JWT authentication.
- Protect model credentials and keep them out of client responses and version
  control.
- Add rate, timeout, retry, and cost controls around external model calls.
- Add unit and integration tests, including database-backed query tests and
  model-client test doubles.
- Document the REST API and local configuration.

### Deliverables

- Versioned REST API.
- Statistical, policy, and AI-analysis services.
- Basic authentication and profile functionality.
- Automated backend test suite.
- API and model-configuration documentation.

### Completion Criteria

- The API supports the agreed dashboard interactions without requiring the
  frontend to reproduce statistical calculations or prompts.
- Responses expose source attribution, units, data freshness, document
  references, and AI-generation metadata where relevant.
- Core endpoints and model failure paths are covered by automated tests.

## 5. Develop the Frontend

Build a focused Next.js dashboard for Irish employment statistics, policies,
and evidence-grounded AI analysis.

### Tasks

- Create the application shell, navigation, and responsive layout.
- Load indicators, periods, dimensions, reports, and policy categories from
  backend metadata.
- Implement filters that prevent unavailable combinations where practical.
- Add historical trend charts, tables, and summary cards supported by the data.
- Add policy browsing with report and page references.
- Add a combined analysis view that clearly separates calculated facts,
  extracted policy evidence, and AI-generated interpretation.
- Display units, source attribution, publication dates, data status, missing
  values, model metadata, limitations, loading states, and errors.
- Add registration, login, profile, and password-management screens.
- Add accessibility checks and frontend tests for critical interactions.
- Configure production builds and deployment integration.

### Deliverables

- Interactive Irish employment dashboard.
- Statistical and policy exploration views.
- Evidence-grounded AI analysis view.
- Account interface.
- Tested production build and deployment notes.

### Completion Criteria

- A user can inspect an employment trend, view related policies, and understand
  an AI-generated analysis without losing the connection to original sources.
- The interface visually distinguishes source facts from generated
  interpretation.
- The interface remains clear when data is incomplete, policy evidence is
  unavailable, or the model cannot produce a validated result.

## Cross-Cutting Requirements

- Document important assumptions and decisions as they are made.
- Keep database credentials, model API keys, and environment-specific settings
  out of version control.
- Preserve source attribution and transformation traceability.
- Version prompts and structured-output schemas.
- Treat model output as untrusted until schema and evidence checks pass.
- Log enough metadata to reproduce or audit a run without logging secrets or
  unnecessary sensitive content.
- Prefer simple, testable components over speculative infrastructure.
- Add monitoring, caching, queues, vector stores, or additional services only
  when observed requirements provide a concrete reason.
- Update this plan when source findings change the feasible product scope.
