# Backend

Spring Boot modular monolith. **Status:** Skeleton (Phase 1 of [roadmap.md](../docs/roadmap.md)) — no Jira/GitLab/Jenkins integration and no business entities yet. That starts next.

## Stack

- Java 21
- Spring Boot 3.5 (web, actuator, data-jpa)
- PostgreSQL (via JDBC/JPA)
- Liquibase (schema migrations)
- Maven (with wrapper — no local Maven install required)

## Run locally (with Docker Postgres)

From the repo root:

```powershell
cd docker
copy .env.example .env   # first time only
docker compose up --build
```

Then check:

- Health: http://localhost:8080/actuator/health
- Info: http://localhost:8080/actuator/info

## Run backend only (Postgres already running elsewhere)

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Configure `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `SERVER_PORT` via environment variables or `-D` system properties (see `src/main/resources/application.yml` for defaults).

## Tests

```powershell
cd backend
.\mvnw.cmd clean verify
```

The context test uses an embedded H2 database in PostgreSQL-compatibility mode, so it runs without Docker and still exercises the Liquibase wiring.

## Package layout

```text
src/main/java/ru/eltc/deliverymonitor/
├── DeliveryMonitorApplication.java
└── integration/jira/          # placeholder — next task connects Jira here
```

See [docs/architecture.md](../docs/architecture.md) (Backend packages table) for the full planned package layout; packages are added when their phase starts, not ahead of time.

## Migrations

Liquibase changesets live in `src/main/resources/db/changelog/changes/`, included from `db.changelog-master.yaml`. Add one changeset per logical change; never edit an already-applied changeset.

## Next task

Jira integration ([roadmap.md](../docs/roadmap.md) Phase 2), using confirmed config from [docs/discovery.md](../docs/discovery.md) §9.1 (`MPTPSUPP`, board 718, filter 30532).
