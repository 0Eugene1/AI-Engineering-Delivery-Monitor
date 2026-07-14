# Docker

Local infrastructure for the Skeleton (Phase 1): PostgreSQL + the backend, via Docker Compose.

## Usage

```powershell
cd docker
copy .env.example .env   # first time only, then adjust if needed
docker compose up --build
```

Services:

| Service | Purpose | Port (host) |
|---|---|---|
| `postgres` | PostgreSQL 16 | `${DB_PORT:-5432}` |
| `backend` | Spring Boot app (see [../backend](../backend/README.md)) | `${APP_PORT:-8080}` |

Health check after startup: http://localhost:8080/actuator/health

`.env` is git-ignored; only `.env.example` (no secrets) is committed.
