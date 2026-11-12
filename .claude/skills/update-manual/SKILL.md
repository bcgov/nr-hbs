---
name: update-manual
description: Update the HBS end-user manual in docs/manual/ after a change to user-visible behaviour (screens, menu items, fields, buttons, workflows, validation, roles/capabilities, reports, public area). Use whenever frontend/src or backend/src/main changed in a way a user would notice, or when the check-manual Stop hook asks for it. Ends with a secrets and personal-data sweep of the changed text, screenshots and PDF.
---

# Updating the HBS user manual

The manual in `docs/manual/` is for **end users** (ministry IDIR staff and
industry BCeID users), not developers. Keep it in step with the code in the
same change.

## 1. Decide whether the manual needs to change

List what changed: `git diff --name-only HEAD` plus untracked files.

**Update the manual** when a change alters anything a user sees or does:
- a screen added, removed, renamed or moved in the menu
- criteria, columns, form fields, required fields, defaults or validation
- a button or action added, removed or relabelled, or its availability
  (role, record status)
- a workflow or status change (e.g. a new return state)
- a role or capability grant change
- a report added, removed or renamed
- public-area content, session time-out, login or organization selection

**Don't update the manual** for refactors, tests, performance work, logging,
build/CI/deploy changes, backend SQL changes with the same results, or
developer docs. In that case say in one sentence why no user-visible
behaviour changed, and stop.

## 2. Find the chapter(s) to edit

| Source | Chapter |
|---|---|
| `frontend/src/screens/areas/queries.ts`, `backend/.../catalog/Queries*Catalog.java`, `backend/.../queries/**` | `02-queries.md` |
| `frontend/src/screens/areas/scale-returns-*.ts`, `pages/SubmitScaleDataPage.tsx`, `pages/PublicKeyPage.tsx`, `pages/SummaryReturnGridPage.tsx`, `pages/detail/**`, `backend/.../catalog/{SummaryReturns,DetailReturns*,Submissions,SubmissionsBatchSlip}Catalog.java`, `backend/.../{summaryreturns,detailreturns,submission,submissions,intake}/**` | `03-scale-returns.md` |
| `frontend/src/screens/areas/stratum-planner.ts`, `backend/.../catalog/SamplingCatalog.java`, `backend/.../sampling/**` | `04-stratum-planner.md` |
| `frontend/src/screens/areas/rating.ts`, `pages/StumpageRatePage.tsx`, `backend/.../catalog/RatingCatalog.java`, `backend/.../rating/**` | `05-rating.md` |
| `frontend/src/screens/areas/billing.ts`, `backend/.../catalog/BillingCatalog.java`, `backend/.../billing/**` | `06-billing.md` |
| `frontend/src/screens/areas/scale-control*.ts`, `backend/.../catalog/ScaleControl*Catalog.java`, `backend/.../scalecontrol/**` | `07-scale-control.md` |
| `frontend/src/screens/areas/profiles.ts`, `pages/ScalerKeyRequestPage.tsx`, `pages/StatementDeliveryPage.tsx`, `backend/.../catalog/Profiles*Catalog.java`, `backend/.../profiles/**` | `08-profiles.md` |
| `frontend/src/screens/areas/admin*.ts`, `backend/.../catalog/{Admin,SubmissionsProcessingParams}Catalog.java`, `backend/.../{admin,processparams}/**` | `09-administration.md` |
| `frontend/src/pages/public/**`, `backend/.../publicaccess/**` | `10-public-area.md` |
| `frontend/src/routes/access.ts`, `frontend/src/context/auth/types.ts` (role labels), `backend/.../security/HbsRoles.java`, `HbsAuthorities.java`, `query/Capability.java` | `11-who-can-do-what.md` (and the "Who" column of the affected chapter) |
| `pages/HomePage.tsx`, `pages/OrgSelectionPage.tsx`, `components/SessionTimeout/**`, `components/Layout/**`, `components/screens/**` (shared search/detail/form behaviour), `routes/routePaths.ts` (menu) | `01-getting-started.md` |
| A new area file in `frontend/src/screens/areas/` | New chapter: add it to `docs/manual/README.md` and this table |

`backend/...` = `backend/src/main/java/ca/bc/gov/nrs/hbs/api/`.

The screen definitions are the source of truth for what users see: `title`,
`description`, `criteria`/`fields` labels, `actions` labels and `capability`,
`bulkActions`, `reports`, and `nav` (whether it is in the menu). Read the
definition rather than guessing. Capability → role mapping is in
`frontend/src/routes/access.ts`; role display names are `ROLE_LABELS` in
`frontend/src/context/auth/types.ts`.

## 3. Style

- Write for the user. Use the on-screen labels exactly, in **bold**:
  **Search**, **Hold Selected**, **Scale Returns › Search for Summary Scale
  Returns**.
- Use role display names ("Billing Administrator"), never `HBS_BILL_ADMIN`.
  Use capability names nowhere.
- Never mention legacy P-numbers, registry ids, SQL, procs, class names or
  file paths.
- Each chapter: a short intro (what the area is for, who sees it) → a
  **Screens** table (menu item → what it's for, plus a "Who" column where
  access differs) → **Common tasks** as numbered steps.
- Document only menu screens in the Screens table. Mention screens reached
  from results (detail pages, edit forms) inside the task that uses them.
- Short sentences, active voice, present tense. Canadian spelling.
- Don't describe what is planned or not yet built. Describe the app as it is
  in this change.

## 4. Screenshots and the PDF

Screenshots live in `docs/manual/images/` and are referenced as
`![Caption](images/NN-name.png)`. The caption doubles as the figure caption
in the PDF. They are produced by `frontend/e2e/manual.screenshots.spec.ts`
(read-only; replaces the signed-in user's name with "Jordan Sample").

- If a change alters a screen that has a screenshot, or adds a screen worth
  showing, add or update its entry in that spec. Don't hand-edit images.
- Recapturing needs a signed-in e2e session, so ask the user before running:
  `cd frontend && E2E_BASE_URL=http://localhost:3000 npm run manual:screenshots`
  (after `npm run e2e:login` if the session has expired). If you can't
  recapture, say which images are now out of date.
- A screen the e2e account has no role for is skipped, never saved. Never
  commit a "You don't have access" image or one showing a real person's name.
- Rebuild the PDF after any manual change: `cd frontend && npm run manual:pdf`
  (writes `docs/manual/HBS-User-Manual.pdf`).

## 5. Secrets and personal-data sweep

The manual is published to the public website, so run this sweep on every
manual change before you report done: every new or changed chapter, image and
the rebuilt PDF. Fix anything found; never just mention it.

1. **Text.** Grep the changed manual files (and any spec/script you touched)
   for credential values and identities:
   `git diff --name-only HEAD -- docs/manual frontend/e2e/manual.screenshots.spec.ts`
   plus untracked files, against
   `password|secret|token|api[_-]?key|bearer|BEGIN .*PRIVATE|eyJ[A-Za-z0-9_-]{10,}|AKIA|ghp_|sk-|jdbc:|@gov\.bc\.ca|IDIR\\[A-Z]|BCEID\\[A-Z0-9]|E2E_[0-9]`.
   Matches that only name a concept ("tokens", `IDIR\NAME`) or the sample
   identity `jordan.sample@gov.bc.ca` are fine; any real value is not.
   Never print a credential value you find, only the file and line.
2. **Screenshots: look at every new or changed image** (Read the PNG). Reject
   and recapture or fix the spec if it shows any of:
   - a real person's name, IDIR/BCeID user id, email or initials (only
     "Jordan Sample" / `JSAMPLE` / "JS" are allowed), including in a header,
     profile panel, "Welcome," line, "Last Updated By" or "Input User ID"
     field;
   - the e2e account name (`E2E_…`) or a "You don't have access" page;
   - a token, cookie, URL with query secrets, devtools, an error stack trace
     or an internal hostname other than the app itself;
   - an individual's name or address in result rows (FOI: those must read
     "Not Releasable"). Client numbers, timber marks and site codes from
     TEST are acceptable but call them out to the user.
3. **Image metadata.** PNGs must carry no `tEXt`/`iTXt`/`zTXt`/`eXIf`
   chunks (Playwright writes none; anything else added them).
4. **PDF.** After `npm run manual:pdf`, check the PDF's decompressed streams
   hold no `/Users/`, `file://`, local usernames or `E2E_` strings, and that
   its Info has only Title/Creator/Producer.
5. Confirm `frontend/e2e/.auth/` is still gitignored
   (`git check-ignore frontend/e2e/.auth/user.json`); the saved session must
   never be committed.

Report the sweep result in your summary (clean, or what you fixed).

## 6. Finish

- Edit only the chapters affected. Keep the tables in step: a removed screen
  leaves no row behind.
- If you added or renamed a chapter, update `docs/manual/README.md`.
- In your summary to the user, list which manual sections you changed.
