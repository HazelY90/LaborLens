# LaborLens Implementation Status

The current application is implemented as a Spring Boot backend, MySQL database
and Next.js frontend. It covers Irish employment statistics for 2019–2025 and
policies extracted from five strategy PDFs.

## Implemented scope

- **Database:** V1 creates four statistical tables, `policy`, `source_file` and
  `job_run`; V2 creates `users`.
- **Ingestion:** one manual runner prepares all nine files, imports the four CSVs,
  then sends each complete PDF to AI. Jobs retain checksums, run history and
  extraction evidence, with transactional replacement and repeat-run skipping.
- **Data API:** authenticated metadata, trend, comparison and policy queries
  under `/api/data`. Metadata uses fixed dataset-specific enum allowlists.
- **Authentication:** email/password registration and login, display-name updates,
  password changes, logout and account deletion. JWTs use database token versions
  for revocation; refresh credentials use an HttpOnly cookie.
- **Frontend:** public homepage, shared header, account dialogs and five data pages.
  Statistics use Recharts trend lines and responsive vertical comparison bars.
  Comparison labels wrap without horizontal chart scrolling; compact tooltips
  show periods and values with units. Policies use category tabs and a
  strategy-period timeline with source links at the bottom.

Combined AI analysis, saved analyses, email verification and password recovery
are not implemented. Deployment infrastructure is not part of the current code.

## Local workflow

1. Configure the backend and run Flyway migrations.
2. Run all ingestion jobs.
3. Start the backend application.
4. Configure the frontend API address if needed, then start the frontend.

Commands: [Backend README](../backend/README.md) and
[Frontend README](../frontend/README.md).

## Documentation

- [Data Sources](data_api.md)
- [Data Ingestion](data_ingestion.md)
- [Schema Design](schema_design.md)
- [Backend Data API](backend_api.md)
- [Authentication](authentication.md)
- [Frontend Design](frontend_design.md)
