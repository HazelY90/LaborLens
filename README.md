# LaborLens

LaborLens is a full-stack dashboard for exploring Ireland’s labour market.
It brings together official employment statistics for 2019–2025 and policies
extracted from five government strategy reports, helping users explore trends,
compare groups and browse employment policies in one place.

## Main Features

- **Employment statistics:** four pages covering annual employment rates,
  monthly unemployment rates, quarterly employment rates and employment counts.
- **Interactive comparisons:** compare up to five trend lines and explore
  demographic or sector differences through period-based bar charts.
- **Policy browsing:** explore five policy categories by strategy period,
  with links to the original reports.
- **Account management:** register, log in, change a display name or password,
  log out and delete an account. Data pages require login.
- **Data ingestion:** prepare official source files, import CSV statistics and
  use AI to extract structured policies from complete PDF reports.

## Technology Stack

| Area | Technologies |
| --- | --- |
| Frontend | TypeScript, React, Next.js, Tailwind CSS, Recharts |
| Backend | Java, Spring Boot, Spring Data JPA, Spring Security |
| Database | MySQL, Flyway |
| Policy extraction | Spring AI, OpenAI |
| Build tools | Maven, npm |

## Development Workflow

1. **Select and review sources:** identify four CSO statistical datasets and
   five official policy reports.
2. **Design storage and ingestion:** define the database structure and build
   file preparation, CSV import and PDF policy extraction jobs.
3. **Design the user experience:** agree on page layouts, filters and charts,
   then define the data each page needs.
4. **Develop the backend:** implement data queries, APIs and account authentication.
5. **Develop the frontend:** connect API services, build account flows and data
   pages, and refine layouts and missing-data presentation.
6. **Validate and document:** check application behaviour and keep the setup
   instructions and design documents aligned with the implementation.

## Project Structure

- `backend/`: application APIs, authentication, database migrations and ingestion jobs.
- `frontend/`: homepage, account dialogs, statistical charts and policy pages.
- `docs/`: data sources, workflows and design documentation.

## Getting Started

Configure the backend, migrate the database and run the ingestion jobs before
starting the backend and frontend applications.

- [Backend configuration and commands](backend/README.md)
- [Frontend configuration and commands](frontend/README.md)
- [Implementation overview and design documents](docs/development-plan.md)
