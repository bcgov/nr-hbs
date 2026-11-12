# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

@AGENTS.md

HBS (Harvest Billing System) is a modern UI + API over the legacy HBS data in
the shared Oracle `THE` schema (DDL lives in the separate nr-mof-db repo). It
replaces a Struts/EJB app screen by screen and mirrors the sister app
nr-fsp-new's structure. Read `docs/pinch-points.md` and `docs/questions.md`
before larger changes.

## Commands

Backend (`cd backend`, Java 21, Maven):
- `mvn -B verify`: full unit suite. Nothing needs Oracle.
- `mvn test -Dtest=CatalogIntegrityTest`: one test class (`-Dtest=Class#method` for one method)
- `mvn spring-boot:run -Dspring-boot.run.profiles=local`: run natively (needs VPN, the gitignored `src/main/resources/application-local.properties` and the `cert/jssecacerts` truststore)

Frontend (`cd frontend`):
- `npm ci`, `npm run typecheck`, `npm run build`, `npm run dev`
- `npm run lint` and `npm test` are placeholders: typecheck and the e2e suite are the real checks.
- E2E (Playwright, read-only against a real environment):
  - `E2E_BASE_URL=http://localhost:3000 npm run e2e:login`: interactive IDIR login. The session is saved in `e2e/.auth/` and lasts about 30 min.
  - `E2E_BASE_URL=http://localhost:3000 npm run e2e`: the suite
  - `npm run e2e -- screens.crawl`: a single spec (filter by file name)
- `npm run access:matrix`: regenerates `docs/access-matrix*.{xlsx,csv}` (role × page READ/WRITE/UPDATE/DELETE) from the screen defs, `Capability.java` and `docs/role-rationalization.md`
- `npm run manual:screenshots` / `npm run manual:pdf`: user-manual images and PDF (see the update-manual skill)

Full stack: `docker compose up` from the root (backend :8080, frontend :3000, shared TEST Oracle; no local DB).

## Architecture

Most of the roughly 200 legacy screens are declared, not hand-built (`docs/screen-framework.md`):

- **Frontend** `frontend/src/screens/areas/<area>.ts` exports `ScreenDef[]`:
  - each one is `kind: 'search' | 'detail' | 'form'`, with criteria, columns, rowLink, actions/rowActions/bulkActions, reports and capability;
  - `screens/registry.ts` auto-discovers them, and the router and SideNav are generated from them, so a new area touches no other file.
- **Backend** `backend/.../api/catalog/<Area>Catalog.java` (a `QueryCatalog` bean) declares the matching items:
  - `QueryDefinition`s: ported legacy SELECT, filters as bound named params, `clientScope` for the industry client fence, and FOI severing via the `:hbsIsMinistry`/`:hbsViewerClient` binds;
  - `CommandDefinition`s: a call to a legacy `HBS_CREATE_*/STORE_*/REMOVE_*` proc with **positional args in the proc's declared order**.
- **Generic endpoints.** The SPA calls `/api/v1/hbs/queries/{id}` and `/commands/{id}`. Ids must match on both sides: `criteria[].name` equals the filter param, and `columns[].key` equals the camelCased SQL alias.
- **Multi-step writes** (release, cancel/replace, approvals, invoicing) are `workflow/WorkflowCommand` beans, served through the same `/commands/{id}` endpoint. They write only through `TableApi`, take optimistic locks with `RowLock`, and some inherit `capability()` from a superclass. What is still to port is listed in `docs/legacy-logic-to-port.md`.
- **Bespoke pages** live in `frontend/src/pages/` (scale-data submission, detail-return entry, stumpage rate, Home work queues, public area).

Security (`docs/roles-and-security.md`):
- **Roles.** Keycloak JWT `client_roles` become HBS roles (`security/HbsRoles`). Users stack a base role (MOF_USER / CLI_USER / SPC_USER) with functional roles. Industry roles are granted per client, and the active client is fenced server-side.
- **Capabilities.** A capability is a set of roles. It is defined in `query/Capability.java` and **mirrored** in `frontend/src/routes/access.ts`: keep the two in step.
  - The backend is authoritative.
  - SPA links and buttons render only when `can(user, capability)`.
  - Per-record rules live in `DetailAuthority` / `SummaryButtons` (backend), with the backend-decided `actionsAvailable` for summary returns.
- `docs/role-rationalization.md` holds the proposed 29 → 16 role consolidation. The access-matrix generator parses its tables, so keep the table format when you edit it.

Reports: 79 legacy Jasper (JCRS) reports compiled in-process (`reports/hbs/catalog.json` lists their prompts); screens link to them through `reports`.

## Guard rails specific to this repo

- `CatalogIntegrityTest` enforces SELECT-only SQL, per-filter binds, `:scopeClientNumber` in client scopes and safe proc names. Run it after editing any catalog.
- Catalog SQL is checked only statically. To exercise it, run the e2e suite against TEST, which is read-only.
- Verify table and column names against nr-mof-db `scripts/THE/TABLES/*.sql`. Don't invent SQL; port it from the legacy DAO or query class.
- A user-visible change also updates `docs/manual/` in the same change, followed by the secrets and personal-data sweep (`.claude/skills/update-manual/SKILL.md`). A Stop hook enforces this.
- Generated docs and the HBS role spreadsheet involve staff data: docs carry counts only, never names. Office lock files (`~$*`) are gitignored because they contain the editor's name.
