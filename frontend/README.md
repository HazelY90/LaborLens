# Frontend

Requires Node.js 20.9+, npm and the running backend.

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
