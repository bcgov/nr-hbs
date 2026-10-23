# Database changes for the HBS backend

Everything here goes to the shared Oracle `THE` schema through the DBA
(nr-mof-db). Each change has a backout. Run in **TEST first**, then PROD.

## Run order

| # | Script | Run as | What | Backout |
|---|---|---|---|---|
| 0 | [00-precheck.sql](00-precheck.sql) | DBA | Read-only: `HBS_SHEDLOCK` doesn't exist yet, `PROXY_FSA_HBS_READ_WRITE_USER` holds `FSA_HBS_READ_WRITE_ROLE`, and duplicate active versions | — |
| 1 | [hbs-shedlock.sql](hbs-shedlock.sql) | THE / DBA | `HBS_SHEDLOCK` table, PK, grant to `FSA_HBS_READ_WRITE_ROLE`, public synonym | [hbs-shedlock-backout.sql](hbs-shedlock-backout.sql) |
| 2 | [99-verify.sql](99-verify.sql) | `PROXY_FSA_HBS_READ_WRITE_USER` | Read-only: role active, `HBS_SHEDLOCK` resolves | — |
| — | [active-version-duplicates.sql](active-version-duplicates.sql) | any | Read-only monitoring: detail documents with two active versions. Before go-live, then periodically | — |
| — | [datafix-departure-active-versions.sql](datafix-departure-active-versions.sql) | THE / DBA | One-off PROD fix for the three departure ledgers that check found (Oct 2026): leaves each document's newest version as its only active one, with audit rows. Set `c_user` first | [datafix-departure-active-versions-backout.sql](datafix-departure-active-versions-backout.sql) |

Before backing out 1, set the backend's `HBS_BATCH_ENABLED=false`.

## What is not here, and why

- **No settings table.** The P038 processing parameters and alert text live in
  the backend's settings ConfigMap ([../coexistence.md](../coexistence.md)),
  not in the database.
- **No unique "one active version" indexes.** They were proposed for the
  detail-return port and withdrawn: legacy and the port briefly hold two active
  versions inside one workflow, and legacy autocommits each proc call, so the
  index would break legacy batch. `active-version-duplicates.sql` detects the
  problem instead; the new app's row locks prevent it on its side.
- **No other grants.** The backend's role `FSA_HBS_READ_WRITE_ROLE` and user
  `PROXY_FSA_HBS_READ_WRITE_USER` already exist in nr-mof-db (PR #582,
  migration 202610212122). A scan of every table, view, sequence, proc and
  function the backend references (175 tables/views, 33 sequences, and every
  proc and function, including the report procs) against that role's grants
  found everything granted except `HBS_SHEDLOCK`. Objects it doesn't
  grant (e.g. `FOREST_CLIENT`, `TIMBER_STATUS_CODE`) are read the legacy way
  instead ([../database.md](../database.md)).
- **No new procs**: every proc the ported workflows call already exists in
  nr-mof-db.

## nr-mof-db placement

nr-mof-db pairs every `V…` script with a `U…` undo script (e.g.
`TABLES/V202607021600.1__FSPTS_SHEDLOCK.sql` / `U…`). The apply files here map
to `TABLES/`, `CONSTRAINTS/` and `GRANTS/` `V…` scripts, and the
backout files to the matching `U…` scripts.
