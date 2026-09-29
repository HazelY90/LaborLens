# Authentication

One user type, without roles. Sign in with email and password; usernames are display names and may repeat. Email is unique and case-insensitive. Passwords use BCrypt hashes. Email verification, email changes and password recovery are outside the initial scope.

## User table

`users`: `id`, `email`, `username`, `password_hash`, `token_version` (integer, default `0`), `created_at`.

Do not store tokens or token hashes, and do not create a token table.

## Tokens

- Access JWT: 30-minute lifetime, returned in the response body and held in frontend memory. Send as `Authorization: Bearer <token>`.
- Refresh JWT: 7-day lifetime, sent in an `HttpOnly`, `SameSite=Lax` cookie with `Path=/api/auth`; use `Secure` in production.
- Both JWTs contain user ID, token type, token version, issued-at and expiration claims. Validate signature, issuer, expiration, expected type, user existence and current database version.
- Refresh returns a new Access JWT and replaces the Refresh Cookie. Expired or invalid refresh credentials require login.
- Without per-token storage, replaced Refresh JWTs remain valid until expiration or a version change; one-time use and replay detection are not supported.
- Validate request origins for cookie-authenticated refresh and logout. Configure allowed origins and credentialed CORS for the actual frontend deployment.

## Endpoints

| Method | Path | Input / behavior |
| --- | --- | --- |
| POST | `/api/auth/register` | Email, username, password; create user |
| POST | `/api/auth/login` | Email, password; return Access JWT and user details, set Refresh Cookie |
| POST | `/api/auth/refresh` | Refresh Cookie; issue new credentials |
| GET | `/api/auth/me` | Access JWT; return current user details |
| PATCH | `/api/auth/me/username` | Access JWT, new username |
| PATCH | `/api/auth/me/password` | Access JWT, current password, new password |
| DELETE | `/api/auth/me` | Access JWT, current password; delete account |
| POST | `/api/auth/logout` | Valid Refresh Cookie; revoke credentials and clear cookie |

Homepage, registration and login are public. Data queries, metadata and account operations require authentication. Refresh and logout authenticate through the Refresh Cookie.

## Invalidation

- Password change: update the password hash and increment `token_version` in one transaction; clear the Refresh Cookie and require login.
- Logout: increment `token_version`, clear the Refresh Cookie and discard the frontend Access JWT.
- Both operations invalidate all previously issued Access and Refresh JWTs on all devices.
- Username changes preserve tokens. Account deletion clears the cookie; subsequent authentication fails because the user no longer exists. Public statistical and policy data remain intact.

## Runtime and request contract

V2 creates `users` on the next normal Flyway migration. Configure `JWT_SECRET_KEY`
with at least 32 UTF-8 bytes of random secret material; it is used directly as the
HS256 signing key, not Base64-decoded. Never commit it.
`AUTH_COOKIE_SECURE` defaults to `true`; set `false` only for local HTTP development.
`AUTH_ALLOWED_ORIGINS` is an exact comma-separated origin allowlist, defaulting to
`http://localhost:5173,http://localhost:8080`. Refresh/logout require an allowed
`Origin` header, including in Postman. Browser calls use `credentials: "include"`.

JSON request fields:

- Register: `email`, `username`, `password`; returns `201` with `{id,email,username}`.
- Login: `email`, `password`; returns `200` with `{accessToken,tokenType,expiresIn,user}`.
- Refresh: no body; same response as login. Refresh JWT appears only in the `refresh_token` cookie.
- Rename: `username`; returns the updated user. GET `/me` returns the same user shape.
- Password change: `currentPassword`, `newPassword`; deletion: `currentPassword`.
- Password change, deletion and logout return `204` and clear the cookie.

Emails use ASCII and are normalized to lowercase. Display names are 1–50 characters
and cannot contain control characters. New passwords are at least 8 characters
and at most 72 UTF-8 bytes. Invalid input returns `400`, duplicate email `409`,
invalid credentials/JWT `401`, and rejected refresh/logout origins `403`.
Error JSON uses `{code,message,field}` without credential values.
