# Endpoint Comparison: Flutter Client (Dart) vs Java Backend

## Auth Endpoints (`/api/auth/`)

| Dart Client Path | Backend Path | Status |
|---|---|---|
| `POST /auth/register` | `POST /auth/register` | ✅ Match |
| `POST /auth/login` | `POST /auth/login` | ✅ Match |
| `POST /auth/refresh` | `POST /auth/refresh` | ✅ Match |
| `POST /auth/logout` | `POST /auth/logout` | ✅ Match |
| `GET /auth/verify` | `GET /auth/verify?token=` | ✅ Match |
| `POST /auth/resend-verification` | ❌ Not implemented | 🔴 Missing |
| `POST /auth/forgot-password` | ❌ Not implemented | 🔴 Missing |
| `POST /auth/reset-password` | ❌ Not implemented | 🔴 Missing |

## User Endpoints

| Dart Client Path | Backend Path | Status |
|---|---|---|
| `GET /api/users/profile` | ❌ No UserController exists | 🔴 Missing |
| `PUT /api/users/profile` | ❌ No UserController exists | 🔴 Missing |
| `GET /api/users/{id}` | ❌ No UserController exists | 🔴 Missing |

## Jobs Endpoints (`/api/jobs/`)

| Dart Client Path | Backend Path | Status |
|---|---|---|
| `GET /jobs` (list) | ❌ Not implemented in JobController | 🔴 Missing |
| `POST /jobs/request` | `POST /jobs/request` | ✅ Match |
| `GET /jobs/nearby` | ❌ Not implemented | 🔴 Missing |
| `GET /jobs/history` | ❌ Not implemented | 🔴 Missing |
| `POST /jobs/accept` | `POST /jobs/{jobId}/accept` | ⚠️ Path mismatch (Dart has no `{jobId}` path variable) |
| `POST /jobs/decline` | ❌ Not implemented | 🔴 Missing |
| `PATCH /jobs/status` | ❌ Not implemented | 🔴 Missing |
| `POST /jobs/complete` | ❌ Not implemented | 🔴 Missing |

## Payment Endpoints

| Dart Client Path | Backend Path | Status |
|---|---|---|
| `POST /payments/mpesa/initiate` | ❌ No PaymentController | 🔴 Missing |
| `GET /payments/status` | ❌ Not implemented | 🔴 Missing |
| `GET /payments/history` | ❌ Not implemented | 🔴 Missing |

## Providers Endpoints (`/api/providers/`)

| Dart Client Path | Backend Path | Status |
|---|---|---|
| `GET /providers/nearby?lat=&lng=&radius=` | `GET /providers/nearby` | ✅ Match |
| `GET /providers/{id}` | `GET /providers/{providerId}` | ✅ Match |
| `GET /providers/earnings` | ❌ Not implemented | 🔴 Missing |
| `PATCH /providers/availability` | `PATCH /providers/status?online=` | ⚠️ Path mismatch (Dart: `/availability`, Backend: `/status`) |
| ❌ No Dart equivalent | `POST /providers/register` | 🔵 Backend-only |
| ❌ No Dart equivalent | `POST /providers/location` | 🔵 Backend-only |
| ❌ No Dart equivalent | `GET /providers/me` | 🔵 Backend-only |

## Chat Endpoints

| Dart Client Path | Backend Path | Status |
|---|---|---|
| `GET /chat/history` | ❌ No ChatController | 🔴 Missing |
| `POST /chat/send` | ❌ Not implemented | 🔴 Missing |
| `POST /chat/read` | ❌ Not implemented | 🔴 Missing |

## Categories Endpoints

| Dart Client Path | Backend Path | Status |
|---|---|---|
| `GET /categories` | ❌ No CategoryController | 🔴 Missing |
| `GET /categories/sub` | ❌ Not implemented | 🔴 Missing |

## Review Endpoints (`/api/reviews/`)

| Dart Client Path | Backend Path | Status |
|---|---|---|
| ❌ No Dart equivalent | `POST /reviews` | 🔵 Backend-only |
| ❌ No Dart equivalent | `GET /reviews/provider/{id}` | 🔵 Backend-only |
| ❌ No Dart equivalent | `GET /reviews/provider/{id}/average` | 🔵 Backend-only |
| ❌ No Dart equivalent | `PUT /reviews/{id}/helpful` | 🔵 Backend-only |

## Summary

### 🔴 Endpoints in Dart but MISSING from backend (16):
1. Auth: `resend-verification`, `forgot-password`, `reset-password`
2. User: `profile`, `updateProfile`, `getUserById`
3. Jobs: `jobs` (list), `nearby`, `history`, `decline`, `status`, `complete`
4. Payments: all 3 endpoints
5. Providers: `earnings`
6. Chat: all 3 endpoints
7. Categories: both endpoints

### ⚠️ Path Mismatches (2):
1. `/jobs/accept` vs `POST /jobs/{jobId}/accept` — Dart expects POST to `/jobs/accept`, backend expects jobId in the URL path
2. `/providers/availability` vs `PATCH /providers/status?online=` — Different path and different parameter style

### 🔵 Endpoints in Backend but not in Dart (5):
1. `POST /providers/register`
2. `POST /providers/location`
3. `GET /providers/me`
4. All 4 Review endpoints (`POST /reviews`, `GET /reviews/provider/{id}`, `GET /reviews/provider/{id}/average`, `PUT /reviews/{id}/helpful`)