# Harvest Billing System — user manual

This manual is for the people who use HBS day to day: ministry staff (IDIR)
and industry users such as licensees, scale sites, submission agents and
scale-software vendors (Business BCeID). It describes what you see on screen
and how to get a job done.

<!-- Developer note (not in the PDF): how the system is built is in [docs/](../README.md);
the PDF is built with `npm run manual:pdf` in frontend/. -->

## Contents

| Chapter | Covers |
|---|---|
| [1. Getting started](01-getting-started.md) | Logging in, choosing an organization, the home page, menus, searching, printing, session time-out |
| [2. Queries](02-queries.md) | Invoices and statements, transmissions, billing and scaling history, timber mark information |
| [3. Scale Returns](03-scale-returns.md) | Summary and detail scale returns, submitting XML files, transmissions, batches, batch slips |
| [4. Stratum Planner](04-stratum-planner.md) | Sampling plans, populations, strata and default ratios |
| [5. Rating](05-rating.md) | Override rules and rates, district default rates, waste and residue rates, stumpage rates |
| [6. Billing](06-billing.md) | Final bills, non-renewable tenures, field-scaled decks, forced summarization |
| [7. Scale Control](07-scale-control.md) | Late submissions, anomalies, change log, transport events, Load Description Slip registry, software use |
| [8. Profiles](08-profiles.md) | Client, scale site, population, mark holder, scaler, delivery and cruise-based billing profiles |
| [9. Administration](09-administration.md) | User data domains, processing parameters |
| [10. Public area](10-public-area.md) | What anyone can see without logging in |
| [11. Who can do what](11-who-can-do-what.md) | Roles and what each one lets you do |

## How to read this manual

- **Menu paths** are written as **Area › Menu item**, for example
  **Scale Returns › Summary Returns**. The areas are the
  groups in the left-hand menu.
- **You only see what your roles allow.** If a menu item, button or area in
  this manual is missing for you, you don't hold a role that grants it. See
  [Who can do what](11-who-can-do-what.md), then ask your HBS administrator.
- **Industry users only ever see their own organization's data.** Where a
  search has a client number field, the system applies your active
  organization whatever you type.

## Keeping this manual current

The manual lives in the repository next to the code and is updated in the
same change as the feature it describes. Developers: see the
`update-manual` skill in `.claude/skills/update-manual/SKILL.md` for the
conventions and the source → chapter mapping.
