# Database

## Where the data lives

- **Oracle `THE` schema** on the shared NRS Oracle instances (test and prod),
  reached over TCPS 1543 exactly like nr-fsp-new (init container builds the
  truststore; `DATABASE_*` secrets).
- DDL, procedures and functions are versioned in **nr-mof-db**
  (`scripts/THE/{TABLES,VIEWS,PROCEDURES,FUNCTIONS,PACKAGES,…}`). This repo
  owns no schema and runs no Flyway (the quickstart's Postgres/Flyway/Prisma
  layer is not used — see the cleanup note in the README).
- The legacy app connected through its own application account and
  used **public synonyms** — no schema prefixes in SQL. The new app does the
  same, so its proxy user needs the same synonyms/grants.

## The grant constraint that shapes the code

The legacy application role has **SELECT only** on the HBS tables (INSERT/UPDATE/
DELETE on just `HBS_FILE` and three crypto tables). Every legacy write went
through the generated table-API procedures:

| Proc family | Count in nr-mof-db | Purpose |
|---|---|---|
| `HBS_CREATE_<TABLE>` | 129 | INSERT one row (caller supplies the id from `<TABLE>_SEQ`) |
| `HBS_STORE_<TABLE>` | 97 | UPDATE one row by key |
| `HBS_REMOVE_<TABLE>` | 112 | DELETE one row by key |
| `HBS2R*` / `HBS3R*` / `HBS0000*` | ~110 | report/search procs returning a REF CURSOR (dynamic SQL) |
| `HBS_*` functions | ~50 | numbering, reconciliation cursors, table-name lookups, LDS helpers |
| `HBS_RATES`, `HBS_CONSTANTS`, `HBS_GET_CLIENT`, … packages | 6 | rate lookup, client lookup, status updates |

So in the new app:

- **Reads** are SQL (ported from the legacy `*Query` classes) in registry
  `QueryDefinition`s, or the report procs via Jasper.
- **Writes** are `CommandDefinition`s calling the same `HBS_CREATE/STORE/REMOVE`
  procs with **positional args in the proc's declared order** — never direct
  DML (it would fail on the grants anyway).
- Ids: `SELECT <TABLE>_SEQ.NEXTVAL FROM DUAL` before the create call, as the
  legacy `UniqueIdQuery` did.
- Audit columns (`ENTRY_USERID/_TIMESTAMP`, `UPDATE_USERID/_TIMESTAMP`) are
  proc parameters (no triggers) — filled server-side from the JWT
  (`IDIR\NAME` / `BCEID\name`, truncated to 30).

### What the proxy user can actually read (checked 2026-10-09)

The app connects as the HBS proxy user (`DATABASE_USER`). Every registry
query, command look-up, sequence, statement-download query and report proc
was described against the TEST database with that user (Oracle parse +
privilege check, nothing executed).
The role has **no grant** on several shared FTA/CLIENT objects the first port
assumed, so the SQL reads them the way the legacy app did:

| Not granted | Read instead |
|---|---|
| `FOREST_CLIENT` | `V_CLIENT_PUBLIC` (client number, name, legal first/middle name, status, type) |
| `FILE_TYPE_CODE`, `CLIENT_TYPE_CODE`, `CLIENT_STATUS_CODE`, `TIMBER_STATUS_CODE`, `QUOTA_TYPE_CODE`, `SALE_METHOD_CODE`, `PAYMENT_METHOD_CODE`, `TENURE_FILE_STATUS_CODE`, `STAND_RATE_ELIGIBILITY_CODE`, `GRADE_SCHEDULE_CODE`, `APPRAISAL_METHOD_CODE` | `CODE_LIST_TABLE` (`column_name` = `FILE_TYPE_CODE`, `CLIENT_TYPE_CODE`, … `PAYMENT_METHOD_CD`, `TENURE_FILE_STS_CD`, `STAND_RATE_ELGB_CD`, `GRADE_SCHEDULE_CD`, `APPRAISAL_MTHD_COD`); dropdowns use `CodesCatalog.codeList` |
| `HBS_EDIT_ERR_RESPONSIBLTY_CODE` | `CODE_LIST_TABLE` `HBS_ERR_USER_CD` (Industry / Ministry / Ministry-Industry) |
| `MGMT_UNIT_TYPE_CODE` | legacy's fixed list (`OpqConstants.MGMT_TYPE_CODE`) |
| `HBS_GET_CRUISE_VOLUME`, `HBS_GET_AGED_UNBILLED_REGION` (functions) | inlined as SQL in `QueriesReportsCatalog` |
| `APP_WEIGHT_VOL` | not replaceable: Cut to Cruise uses the worksheet's net cruise volume for Coastal marks (the function's own fallback) |
| `PKG_SIL21_CLIENT_SEARCH` | nr-forest-client-api ([forest-client-integration.md](forest-client-integration.md)) |

Worth asking the DBA for: `SELECT` on `APP_WEIGHT_VOL` (exact Coastal cruise
volumes). The only other objects the user can't see are the new ones below
(`HBS_SHEDLOCK`, `HBS_APP_SETTING`), which don't exist yet — until they do,
the Processing Parameters / App Settings / home alert banner queries fail and
the batch jobs can't take a lock.

## Transactions

The legacy app ran **without real transactions**: 164 of 165 EJB methods were
`Supports` and the web tier never began one, so every proc call autocommitted.
The new `CommandService` wraps each command in one transaction (equivalent for
a single call). Multi-call workflows must decide explicitly — wrapping the
legacy sequences in `@Transactional` changes failure behaviour (half-written
rows that data fixes used to clean up would instead roll back). That is the
intent, but each ported workflow needs a regression test.

## Locking

There is **no optimistic locking** in the schema (no `REVISION_COUNT`); the
legacy Java compared state codes before updating, racily — the cause of ~100
"duplicate active version" data fixes. Planned: `UPDATE_TIMESTAMP`-based ETag
checks in the services + a DBA-added unique index on the active version
(`docs/pinch-points.md` §12).

## The two state machines

Documents (detail: log tally / weigh slip / sample tally / SFP tally / ledgers;
summary: piece / weight / sample returns) are versioned on every change.

- Document state (`SCALE_RETURN_STATE_CODE`): OIP, OCM, CIP, CCM, CWR, CWC,
  RNP, RNC, RCP, RCC, DIS — original/cancel/replace in progress/complete…
- Version state (`SCL_RTN_VERSION_STATE_CODE`): INC, ACC, RDY, ERR, HLD, DSF,
  LCK, ISS, FAL, AWP, PND, PNV, SPR, CAN, DEL, NTI, INV.

Full transition tables: the legacy data-layer analysis summarised in
[legacy-logic-to-port.md](legacy-logic-to-port.md).

## Objects this app needs added to nr-mof-db

| Object | Why | DDL |
|---|---|---|
| `THE.HBS_SHEDLOCK` | ShedLock for the batch jobs | [db/hbs-shedlock.sql](db/hbs-shedlock.sql) |
| App-setting table + store proc | Replaces the ProcessParam share files (alert banner, processing parameters, printer names) | [db/proposed-ddl.sql](db/proposed-ddl.sql) |
| Grants/synonyms for the new proxy user | Mirror the legacy application role | DBA |

## Known gaps between the legacy code and the DB

Some procs the legacy Java calls are **absent** from nr-mof-db (e.g.
`HBS2R026`, `HBS2R331`, the `*_XU_FOREST_TALLY` family), and some legacy inline
DML would fail under the current grants. Treat those code paths as dead until
verified against the TEST database; the area docs flag each one.
