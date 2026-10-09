# AGENTS.md

Repository facts for automated coding assistants. Teams may edit or remove this file.

## Layout
- Same architecture as the sister app nr-fsp-new: `backend/` (Spring Boot 3.5, Java 21, plain JDBC over the shared Oracle `THE` schema — no ORM, no owned schema, no Flyway), `frontend/` (Vite, React 19, Carbon, Caddy + Coraza)
- Legacy screens are declared, not hand-built: backend `backend/src/main/java/ca/bc/gov/nrs/hbs/api/catalog/*Catalog.java` + frontend `frontend/src/screens/areas/*.ts` (see `docs/screen-framework.md`)
- OpenShift templates (not Helm): `backend/openshift.deploy.yml`, `frontend/openshift.deploy.yml`, `frontend/openshift.route.yml`
- Workflows: `.github/workflows/` (PR deploys via `reusable-deploy.yml`, tests via `reusable-tests.yml`, PROD vanity TLS via `route-tls.yml`)
- Docs: `docs/` (start with `docs/README.md`); DDL this app needs in nr-mof-db: `docs/db/`

## Build, test, run
- Full stack locally: `docker compose up` (needs BC Gov VPN + the gitignored `application-local.properties`; optional profile `caddy`)
- Backend (`cd backend`): `mvn -B verify` (unit tests incl. `CatalogIntegrityTest` and `ReportTemplatesCompileTest`; no DB needed)
- Frontend (`cd frontend`): `npm ci`, `npm run typecheck`, `npm run build`; e2e: `npx playwright install --with-deps chromium`, then `npx playwright test --project="chromium"`
- Deploys to OpenShift run from GitHub Actions (PR open, merge), not from a workstation

## Data rules
- Never write HBS tables with DML: the app's DB role is SELECT-only; writes go through the legacy `HBS_CREATE_*` / `HBS_STORE_*` / `HBS_REMOVE_*` procs (`CommandDefinition`)
- Every query value is a bound parameter; client-owned data declares `clientScope`

## Shared actions
- Uses bcgov shared actions and workflows (`bcgov/action-*`, `bcgov/actions/*`, `bcgov/actions-openshift/*`, `bcgov/quickstart-openshift-helpers`). Use them as provided; don't copy or fork them.
- Never pin `@main`. Pin bcgov shared actions to a published release SHA with a `# vX.Y.Z` comment.
- Vanity Route TLS (`bcgov/actions-openshift/route-tls`) is strictly managed via the standalone on-demand workflow (`.github/workflows/route-tls.yml` with `workflow_dispatch`). Never embed `route-tls` into `merge.yml`, `release.yml`, or continuous deployment pipelines.

## Settings
- Automated agents must not change repository or organization settings. Settings changes are made by a person on the team (team-run setup scripts: see #2858).
