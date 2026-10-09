# nr-hbs — Harvest Billing System (HBS)

A web application for **billing timber harvested on Crown land** in British
Columbia. Scale sites and their software submit **scale returns** (piece,
weight and sample scale data); ministry staff and industry clients review them.
HBS rates and summarizes them, issues **stumpage invoices and volume/ratio
statements**, and posts the amounts to revenue. It replaces the legacy HBS
(Struts 1 / EJB 2 / WebLogic, `a100.gov.bc.ca/ext/hbs`). Its architecture,
page layouts, styling, CI/CD and monitoring are the same as the sister app
**nr-fsp-new** (FSPTS).

Like FSPTS, this is a modern UI + API over the **existing shared Oracle `THE`
schema** (DDL in **nr-mof-db**). It does not own its data model. See
[docs/architecture.md](docs/architecture.md) for the one thing that differs:
HBS's business rules lived in Java, not PL/SQL.

## Architecture at a glance

```
┌─────────────────┐   HTTPS / JWT    ┌──────────────────────────┐  SQL + table-API procs  ┌───────────────┐
│  React SPA      │ ───────────────▶ │  Spring Boot API         │ ──────────────────────▶ │ Oracle (THE)  │
│  Vite · Carbon  │  Keycloak (SSO)  │  registry screens,       │       JDBC              │ HBS + FTA +   │
│  (frontend/)    │  IDIR / BCeID    │  Jasper reports, batch   │                         │ CLIENT tables │
└─────────────────┘                  │  (backend/)              │                         │ = nr-mof-db   │
                                     └──────────────────────────┘                         └───────────────┘
```

- **Frontend:** React 19 + TypeScript + Vite, BC Gov Carbon, oidc-client-ts (BC Gov SSO / Keycloak, as nr-fta) — the
  nr-fsp-new shell. About 200 legacy screens are declared in a screen
  registry. See [frontend/README.md](frontend/README.md).
- **Backend:** Java 21 + Spring Boot 3.5 OAuth2 resource server. It provides
  registry-driven queries and commands, in-process JasperReports, the
  scale-data XML upload, and ShedLock batch jobs. See
  [backend/README.md](backend/README.md).
- **Ops:** OpenShift templates, GitHub Actions (PR previews → TEST → PROD),
  Caddy + Coraza WAF, Sysdig alerts (`monitoring/`). Same as nr-fsp-new.

## Status

| | |
|---|---|
| ✅ Ported from nr-fsp-new | Project structure, CI/CD workflows, OpenShift templates, Docker images, compose, monitoring, Carbon UI shell, auth/org/theme/session/notification contexts, error handling, logging, ClamAV, user lookup, client search |
| ✅ Built | Every legacy screen across the 8 legacy tabs: 109 screens covering 198 legacy screen ids, 159 queries and 45 CRUD commands, each mapped to its legacy P-number in `docs/areas/`. Home work-queue dashboard. All 79 legacy reports in-process. Scale-data XML submission. 28-role security model with client fencing |
| 🟡 Scaffolded | Batch framework (purge + intake monitor); workflow screens render, with saves gated |
| 🔴 To port | Business workflows (summary/detail return state machines, pricing, invoicing, sampling plans) and most batch jobs — see [docs/legacy-logic-to-port.md](docs/legacy-logic-to-port.md) |

Read [docs/pinch-points.md](docs/pinch-points.md) and
[docs/questions.md](docs/questions.md) first.

## Quick start

Prerequisites: BC Gov VPN, Docker, `backend/src/main/resources/application-local.properties`
+ the Oracle truststore, and `frontend/.env` (same as nr-fsp-new; see
[compose.yml](compose.yml)).

```bash
docker compose up          # backend :8080 + frontend :3000
```

## Documentation

| Doc | What it covers |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Layers, request flow, legacy → new mapping, deployment differences from FSP |
| [docs/pinch-points.md](docs/pinch-points.md) | **Ranked modernization risks**, with evidence and status |
| [docs/questions.md](docs/questions.md) | Decisions needed from the business / FAM / DBAs / operations |
| [docs/screen-framework.md](docs/screen-framework.md) | How a legacy screen is declared (frontend registry + backend catalog) |
| [docs/roles-and-security.md](docs/roles-and-security.md) | 28 roles → FAM groups, capability matrix, client fence, FOI severing |
| [docs/database.md](docs/database.md) | Shared `THE` schema, the SELECT-only grant, table-API procs, state machines |
| [docs/legacy-logic-to-port.md](docs/legacy-logic-to-port.md) | The Java business logic still to port, by area, and the recommended order |
| [docs/batch-jobs.md](docs/batch-jobs.md) | Legacy AutoMate/EJB batch → ShedLock jobs, status per job |
| [docs/reports.md](docs/reports.md) | Crystal/JCRS → in-process JasperReports |
| [docs/file-shares-and-storage.md](docs/file-shares-and-storage.md) | The 12 SMB shares and their replacements |
| [docs/virus-scanning.md](docs/virus-scanning.md), [docs/user-lookup-integration.md](docs/user-lookup-integration.md) | Shared integrations (as in nr-fsp-new) |
| [docs/areas/](docs/areas/) | Per-area screen maps: legacy screen → new screen/query/command, SQL sources, gaps |

## Repository layout

```
backend/    Spring Boot API (Java 21)
frontend/   React SPA (Vite + TypeScript), Caddy image
docs/       Architecture, security, database, pinch points, per-area screen maps
monitoring/ Sysdig alert templates (applied after PROD deploys)
compose.yml Local dev (backend + frontend; shared Oracle, no local DB)
.github/    CI/CD workflows (PR deploy, merge → TEST → PROD, analysis, route-tls)
```

## License

Apache 2.0 — see [LICENSE](LICENSE).
