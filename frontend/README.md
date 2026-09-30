# Frontend

Requires Node.js 20.9+, npm and the running backend.

## Charts

Statistical pages use Recharts for trend lines and vertical comparison bars.
Trend charts preserve all period slots and scroll horizontally when needed.
Comparison bars fit the card width without horizontal scrolling; category labels
wrap in aligned CSS Grid columns below the chart, and the value axis is hidden.
Tooltips show only the period and values with units. Both chart types provide
expandable data tables and distinguish missing observations from zero.

See [Frontend Design](../docs/frontend_design.md) for layout and interaction details.

## Configuration

Create `frontend/.env.local` (shown value is the default):

```properties
NEXT_PUBLIC_API_URL=http://localhost:8080/api
```

Include the frontend origin `http://localhost:3000` in the backend's `AUTH_ALLOWED_ORIGINS`.

## Commands

Run from `frontend/`.

Install dependencies:

```sh
npm ci
```

Start the development server at `http://localhost:3000`:

```sh
npm run dev
```

Build and start the production server:

```sh
npm run build
npm start
```
