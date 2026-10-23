# Porting a legacy workflow

How a legacy EJB workflow, servlet or batch job becomes Java in this app. Read
[coexistence.md](coexistence.md) first: the legacy batch keeps running against
the same rows while these ports go live, and that shapes several rules below.

## The pieces

| Piece | Where | Use it for |
|---|---|---|
| `TableApi` | `workflow/TableApi.java` | Every write. Calls `HBS_CREATE_* / HBS_STORE_* / HBS_REMOVE_*` (and the few business procs/functions) **by parameter name**, checked against nr-mof-db. `nextId(seq)` for new keys, `argsFromRow(proc, row)` to start a STORE from the current row. |
| `ProcSignatures` | `db/hbs-proc-signatures.json` | Generated from nr-mof-db by `backend/scripts/gen-proc-signatures.py`. Procs this app needs that don't exist yet go in `docs/db/*.sql` and are generated with `"proposed": true`. |
| `WorkflowCommand` | `workflow/WorkflowCommand.java` | A multi-step write the SPA triggers. A `@Component` implementing it is served by `POST /api/v1/hbs/commands/{id}` and listed by `GET /commands`, so the SPA's button appears once it exists. `CommandService` opens the transaction. |
| `WorkflowValidationException` | `workflow/` | Business-rule failures, with the **legacy message text**. 400 with one sub-error per message. |
| `RowLock` / `StaleUpdateException` | `workflow/` | Optimistic lock on `UPDATE_TIMESTAMP`: the SPA sends back the `updateTimestamp` it loaded; re-read the row and `RowLock.check(current, expected)`. 409 with the legacy wording. |
| `Audit` | `workflow/Audit.java` | `user()` (caller, or the batch user in a job) and `now()` (Pacific, to the second) for the audit parameters. |
| `RecordingTableApi` | `src/test/.../workflow/` | Unit-test double: validates each call against the real signatures and records it. Assert the sequence of proc calls and key arguments. |
| `BatchJobRunner` + ShedLock | `batch/` | Scheduled jobs. Each job has its own `hbs.batch.<job>.enabled` switch on top of `HBS_BATCH_ENABLED`. |

Reads stay plain SQL (`NamedParameterJdbcTemplate`, bound named parameters,
no schema prefix), exactly as the registry queries do.

## Rules

1. **No DML.** The DB role is SELECT-only on HBS tables. If the legacy code ran
   inline `INSERT/UPDATE/DELETE`, find the table-API proc that does the same; if
   none exists, write the proc into `docs/db/proposed-ddl-<area>.sql` (header
   comment: what, why, which legacy code) and regenerate the signatures. The
   workflow is still built; it fails cleanly until the DBA applies the proc.
2. **Every value is a bound parameter.** No string concatenation into SQL, ever.
3. **Port, don't invent.** Each rule cites the legacy file and method it came
   from (`$E` = `hba-archive/hbs/trunk/source/ear/hbs-ejb/src/main/java/ca/bc/gov/mof/hbs`,
   `$V` = `…/ear/hbs-view/…`, `$B` = `hba-archive/hbs/trunk/source/hbs-batch/…`).
   Messages use the legacy text (`ApplicationResources.properties`,
   `ErrorMessages`, validation.xml). Where legacy behaviour is a known defect
   (docs/questions.md Q12), port it as is and note it, unless the area doc says
   otherwise.
4. **One workflow = one transaction.** The legacy app autocommitted each proc
   call; here the whole workflow commits or rolls back. Never commit an
   intermediate state that a legacy batch job selects on (see the hazards in
   [coexistence.md](coexistence.md)).
5. **Lock.** Any workflow that updates an existing document/version re-reads it
   and checks `RowLock` (and the legacy state preconditions) before writing.
6. **Client fence.** Industry callers act only on their active client's
   records (`RequestUtil.getCurrentClientNumber()`); check ownership on the row
   you load, not on what the body says.
7. **Capabilities.** Reuse `Capability` values; add a new one only when the
   legacy grants differ (mirror it in `frontend/src/routes/access.ts`).
8. **Tests.** At least: happy path proc sequence (`RecordingTableApi`), each
   precondition/validation failure, the lock check. Pure calculations
   (volumes, keys, pricing) get table-driven tests with legacy examples.
9. **Frontend.** Use the screen framework (`ActionDef.command`, form screens)
   where it fits; build a bespoke page only for genuinely bespoke entry (e.g.
   tally grids). Command ids follow `<area>.<verb>` and match the ids already
   reserved in `frontend/src/screens/areas/*.ts`.
10. **Docs.** Move each item from the area doc's "Legacy logic not yet ported"
    list to a "Ported" note (class + command id), and keep
    [legacy-logic-to-port.md](legacy-logic-to-port.md) in step.

## Verifying without a database

There is no writable database for development. Checks available offline:

- `mvn -B verify` — includes `TableApiTest`, `CatalogIntegrityTest` (every
  registry command's argument count matches its proc) and each workflow's
  `RecordingTableApi` tests (every parameter name exists).
- Table/column names against `nr-mof-db/scripts/THE/TABLES/*.sql`.
- `npm run typecheck` and `npm run build` for the SPA.

Workflows still need a TEST-database regression pass before they replace the
legacy screens; [coexistence.md](coexistence.md) lists them.
