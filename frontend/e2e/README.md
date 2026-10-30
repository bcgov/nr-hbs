# HBS end-to-end tests (Playwright)

Same harness as nr-rept: an IDIR login is captured once by the `setup`
project (`auth.setup.ts`, programmatic in CI via `E2E_IDIR_USER` /
`E2E_IDIR_PASSWORD` / `E2E_IDIR_TOTP_SECRET`) and reused through
`storageState`.

| Spec | Kind | What it checks |
|---|---|---|
| `smoke.spec.ts` | unauthenticated | the deployed frontend serves `/` |
| `public.spec.ts` | anonymous (no session) | the public home and every public search screen render; a public issued-documents search runs and offers no reports; a protected page shows the login landing; the API answers 401 without a token |
| `pages.smoke.spec.ts` | real | Home, every legacy-tab landing page and key registry screens render; two deep-link searches round-trip |
| `screens.crawl.spec.ts` | real | every screen each area's landing page offers the account opens cleanly: heading shown, not Forbidden, no "Couldn't load data", no error toast. One test per area, soft assertions so a run lists every broken screen |
| `navigation.spec.ts` | real | Home is a root link (no "General" section); icons on section headings only; only the current page is bold; the current page's section opens on load; the collapsed rail; the support mail link; unknown routes land on Home |
| `search-behaviour.spec.ts` | real | Billing / Scaling History: no subtitle, "From *" / "To * (up to 12 months)", red asterisks, required-field messages; the "More criteria needed" warning stays on the page and closes on leaving it; Clear all; Scale Site Summary client fields disabled until an association is picked; Transmissions of Issued Statements has no "Recently Issued" checkbox and the Home "Recent" link fills From Date with 30 days ago |
| `queries.spec.ts` | real | issued documents of the last 30 days show PDF / XML under one "Actions" column; a statement PDF downloads (checks the `%PDF` header); a timber mark from those results opens its record without the summary strip; an XML transmission and a summary return in error open their records; scale site summary searches |

"real" specs run against the environment's Oracle and are read-only: they open
screens and run searches, and never submit a command or upload. Steps that need
a row (a download, a drill-down) skip with a reason when the environment has no
matching data, so a quiet TEST doesn't fail the run.

`screens.crawl.spec.ts` is the slow one (every screen, each loading its codes
and, for lists, its first search): a few minutes per area. Run it alone with
`npm run e2e -- screens.crawl`.

Run locally:

```bash
npx playwright install chromium
E2E_BASE_URL=http://localhost:3000 npm run e2e:login   # one-time interactive IDIR login
E2E_BASE_URL=http://localhost:3000 npm run e2e
```

## Unattended login (TOTP)

Set `E2E_IDIR_USER`, `E2E_IDIR_PASSWORD` and `E2E_IDIR_TOTP_SECRET` and the
setup project signs in on its own, answering the Entra MFA prompt with a
generated authenticator code (`e2e/totp.ts`). CI reads the same three values
from repository secrets of the same names.

This follows BC Gov SSO's
[Automating MFA with TOTP](https://bcgov.github.io/sso-docs/advanced/automating-mfa-totp)
guide, and should use a dedicated IDIR test account. To get the secret, sign in
as that account at <https://mysignins.microsoft.com/security-info>, then
**Add sign-in method → Microsoft Authenticator → Set up a different
authentication app → Can't scan QR code?** and copy the **Secret key**. Spaces
and case don't matter. Finish the registration by entering one generated code
so the method is actually enrolled.

The secret is the second factor itself: keep it in a masked secret, never in
the repo or a committed `.env`. If the account's default sign-in method is an
Authenticator push, the setup switches to "Use a verification code" itself, but
making the authenticator-app code the default method is more reliable.

```bash
E2E_BASE_URL=http://localhost:3000 E2E_IDIR_USER=... E2E_IDIR_PASSWORD=... \
  E2E_IDIR_TOTP_SECRET=... npm run e2e:login
```

Without the three variables, `npm run e2e:login` opens a browser and waits up
to 5 minutes for you to finish the IDIR sign-in by hand. Either way the session
is saved to `e2e/.auth/` (gitignored) and reused until it expires (~30 min);
delete that folder or re-run `e2e:login` to sign in again.

The setup project runs with trace and screenshots off, and fills the password
and code without them reaching the HTML report, because CI uploads that report
as an artifact.

## Account

The E2E IDIR account needs `HBS_MOF_USER` in FAM. The suite is read-only — it
never runs commands or uploads.

