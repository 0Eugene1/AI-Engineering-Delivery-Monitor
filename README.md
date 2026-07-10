# AI Engineering Delivery Monitor

Internal engineering dashboard that automatically visualizes software delivery status using Jira, GitLab and Jenkins.

## Vision

The system does not replace Jira.

It provides a real-time delivery view by collecting information from existing development tools and automatically detecting:

- active workstreams
- blockers
- release risks
- development progress
- dependencies

## Current status

🚧 Architecture Phase

## Documentation

Source of truth: [`docs/`](./docs/README.md)

| Document | Description |
|---|---|
| [vision.md](./docs/vision.md) | Problem, goals, principles |
| [architecture.md](./docs/architecture.md) | Stack, data flow, packages |
| [architecture-overview.md](./docs/architecture-overview.md) | Shareable overview with Mermaid diagrams |
| [roadmap.md](./docs/roadmap.md) | Implementation phases and MVP scope |
| [database.md](./docs/database.md) | Schema, Workstream Type, events |
| [api.md](./docs/api.md) | REST contract for MVP |
| [integrations.md](./docs/integrations.md) | Jira, GitLab, Jenkins |
| [ux.md](./docs/ux.md) | Screens, Timeline, Release Health |
| [decisions.md](./docs/decisions.md) | ADRs and trade-offs |
| [glossary.md](./docs/glossary.md) | Terms |

## Tech Stack

Backend:
- Java 21
- Spring Boot
- PostgreSQL

Frontend:
- React
- TypeScript

Infrastructure:
- Docker
