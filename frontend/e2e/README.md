# HBS end-to-end tests (Playwright)

Same harness as nr-fsp-new: an IDIR login is captured once by the `setup`
project (`auth.setup.ts`, programmatic in CI via `E2E_IDIR_USER` /
`E2E_IDIR_PASSWORD`) and reused through `storageState`.

| Spec | Kind | What it checks |
|---|---|---|
| `smoke.spec.ts` | unauthenticated | the deployed frontend serves `/` |
| `pages.smoke.spec.ts` | real | Home, Reports, every legacy-tab landing page and key registry screens render; two deep-link searches round-trip against the environment's Oracle (read-only) |

Run locally:

```bash
npx playwright install chromium
E2E_BASE_URL=http://localhost:3000 npm run e2e:login   # one-time interactive IDIR login
E2E_BASE_URL=http://localhost:3000 npm run e2e
```

The E2E IDIR account needs `HBS_MOF_USER` in FAM. The suite is read-only — it
never runs commands or uploads.

