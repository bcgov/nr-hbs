# HBS Frontend (SPA)

The Harvest Billing System single-page app — the nr-fta frontend: Carbon with
the BC Gov theme, nr-fta's page layouts, side nav and styles, BC Gov SSO
(Keycloak) auth, session timeout, toasts, Caddy + Coraza WAF image and runtime
`config.js` — plus nr-fsp-new's BCeID org picker, with the HBS screens on top.

- **React 19** + **TypeScript**, **Vite 6**, `@carbon/react`, `react-router-dom`
- **oidc-client-ts** against BC Gov SSO (Keycloak) — Authorization Code + PKCE, IDIR / Business BCeID

## Local development

```bash
npm ci
npm run dev          # http://localhost:3000, /api proxied to :8080
```

Copy `.env.example` to `.env.local` and set `VITE_KEYCLOAK_URL` / `VITE_KEYCLOAK_CLIENT_ID`. Or run the whole
stack from the repo root with `docker compose up`.

| Command | What it does |
|---|---|
| `npm run dev` | Vite dev server |
| `npm run build` | production build |
| `npm run typecheck` | `tsc --noEmit` — the type gate |
| `npm run e2e` | Playwright suite ([e2e/README.md](e2e/README.md)) |

## Structure

```
src/
├── App.tsx                 auth gates + routes (/home, /reports, /scale-returns/submit, /:area, /:area/:screenId)
├── screens/
│   ├── types.ts            ScreenDef (search / detail / form), FieldDef, ColumnDef, ActionDef …
│   ├── registry.ts         auto-loads areas/*.ts; AREAS = the legacy tab bar
│   └── areas/*.ts          one file per functional area — the legacy screens, declared (109 screens covering 198 legacy P-numbers)
├── components/screens/     SearchScreen, DetailScreen, FormScreen, ScreenLayout, FieldGrid, FieldInput,
│                           ActionButton, ReportButton, DownloadCell, ScreenPage (route element)
├── components/             nr-fta's PageTitle, SectionTile, Tombstone, DetailTile, AsyncBoundary,
│                           EmptyState, StatusTag, ExportCsvButton, ConfirmationModal, Layout (header + side nav)
├── pages/                  HomePage (legacy work-queue dashboard), ReportsPage, SubmitScaleDataPage,
│                           AreaLandingPage, PageLayout + NotFound (from FTA), Landing / Unauthorized /
│                           Forbidden / OrgSelection
├── routes/                 access.ts (capability matrix), routePaths.ts (SideNav from the registry)
├── services/               apiFetch (from FSP), common.ts, screens.ts (queries/commands/reports), clientSearch
├── context/                auth (HBS roles, stacking), org (active client + useScopedUser), theme, layout, notifications
└── styles/                 Carbon + BCGov theme, _search / _detail / _tables (from FTA, unchanged)
```

## Adding or changing a screen

Edit the area file in `src/screens/areas/` and the matching backend catalog —
see [../docs/screen-framework.md](../docs/screen-framework.md). Nav, routing and
access come for free; use `nav: false` for screens reached only by links.

## Access

`routes/access.ts` mirrors backend `Capability.java`. Roles **stack**; use
`useScopedUser()` (roles narrowed to the active client) for every check.
Buttons and form saves are only enabled for commands the backend has
registered (`GET /api/v1/hbs/commands`).
