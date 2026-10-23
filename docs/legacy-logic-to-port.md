# Legacy logic still to port

Every legacy screen now has a new screen (or is listed as not carried over),
and every read and CRUD-shaped write is wired. What remains is the Java
**business logic** the legacy app ran between the screen and the table-API
procs. Each area doc has a precise list with legacy file paths; this page is
the index and the recommended order.

Screens whose writes depend on unported logic still render. Their action
buttons are **hidden** and their forms show "Saving isn't available yet",
because the SPA only enables commands the backend has registered
(`GET /api/v1/hbs/commands`). Nothing is faked.

## By area

| Area | Doc | What's left (summary) |
|---|---|---|
| Summary returns | [areas/summary-returns.md](areas/summary-returns.md#ported-work-package-3--built-needs-test-regression) | ✅ Built (work package 3): state workflows, release edits, data entry, grids, P054 preview, audits — `backend/.../summaryreturns/`. ✅ Discard of a failed generated summary now processes the details' replacement-pending versions through the detail-return workflow (`DetailWorkflow.processReplacementVersionPendingAfterReset`). Needs the TEST regression pass. Re-queue Txn not carried over (dead in legacy). |
| Detail returns | [areas/detail-returns.md](areas/detail-returns.md#ported-work-package-4-cabcgovnrshbsapidetailreturns) | ✅ Ported (built — needs TEST regression): add, save, release, re-edit, hold, discard, cancel/replace, approve/reject, clear digital-signature failure, event change, bulk; validators, DDN keys, volumes (`ca.bc.gov.nrs.hbs.api.detailreturns`). ✅ `StringUtil.empty` semantics (no trim; whitespace codes kept from the SPA, parser defaults for line product/grade). ✅ Leftovers: data-domain search scopes (`scope` / `markScope`), "Generate PDF" delivery copy (HBS2R552/602/652docd), "Date Issued", P650 replacement-indicator rule. Left: "Generate XML" delivery file. |
| Submissions | [areas/submissions.md](areas/submissions.md#legacy-logic-not-yet-ported) | ✅ Ported (WP7): role-based defaults + associated sites/districts, P280/P283 batch-slip workflows, P041/P024/P901/P911 criteria checks, P038 read-only from the settings ConfigMap + share-bridge endpoint. ✅ Leftovers: HBS2R032/033 fenced for industry (`SubmissionReportGuard`), report logging for every report (`ReportLogWriter`). Left: HBS2R027 stays ministry-only (proc fence needs a backslash prompt); new-app batch reading `HBS_APP_SETTING` (batch). |
| Scale control | [areas/scale-control.md](areas/scale-control.md#legacy-logic-not-yet-ported) | ✅ Ported (WP7): P954 clear, P908/P909 window workflow + `HBS_UPDATE_STALE_ANOMALIES`, P901/P911 validations, associated sites/districts scopes. ✅ Leftovers: home-link default window, CLI_SCALER own-licence fence + arrival types refused, P971/P975/P977/P981 validations (`ScaleControlCriteriaGuard`), change-log fence. Arrival types hidden for scalers in the SPA; the licence field isn't prefilled/locked (the fence covers it). |
| Profiles | [areas/profiles.md](areas/profiles.md#legacy-logic-not-yet-ported) | ✅ Ported: scaler auth keys (`profiles.scalers.requestKey`; needs the Q9 Secret + TEST crypto check). ✅ Ported (`api/profiles/`, needs TEST regression): population add/expiry with 23:59:59 ratio dates, scale-site / mark-holder / delivery date-overlap and data-domain validations, P383 row validations. ✅ Leftovers: P320 pre-checks (`ScaleSiteProfileSearchGuard`), P385 sale method, P364 "In my Org Unit". |
| Rating & sampling | [areas/rating-and-sampling.md](areas/rating-and-sampling.md#ported--rating-workflow-commands-backendapirating) | ✅ Built — needs TEST regression. Rating: all writes as workflows in `api/rating` (`rating.overrideRule.*`, `rating.overrideRate.*`, `rating.districtRate.*`, `rating.wasteRate.*`) with the overlap / 7-year / SPG / grade-schedule / status rules; P230 stumpage lookup `GET /rating/stumpage-rate` (`HBS_RATES` via TableApi). Stratum Planner: all plan writes in `api/sampling` (`sampling.plan.*`, `sampling.stratumPlan.*`, `sampling.stratumComposition.*`; feed B2022/B2024). Left: P202 return drill-down links, P865/P870/P871 sample lists, P875–P877 charts. |
| Billing & admin | [areas/billing-and-admin.md](areas/billing-and-admin.md) | ✅ Ported (WP7): force-date windows + force-all, `HBS3R830` generate, P835/P836 checks + key change. ✅ P832 Hold / Release Selected (`billing.unissuedFinalBills.hold/.release`, summary-return workflows), P842–P844 field-scale bulk actions (`billing.fieldScaledDecks.bulk`, detail-return cancel/hold/release). ✅ P828 associated-districts filter + role defaults, HBS3R829 print. |
| Queries | [areas/queries.md](areas/queries.md#ported--screen-validations-foi-gate-associated-locations-mgu-description) | ✅ Screen validations (`QueriesCriteriaGuard`), FOI gate + client fence on the Print / Print Client Register reports (`QueriesReportGuard`), P452 associated locations, P481 MGU description. (Statement PDFs, merged XML/PDF and the e-mailed transmission files were already ported.) |
| Batch | [batch-jobs.md](batch-jobs.md) | ~~Unpack (B1012)~~ ported for P505 uploads (`intake.UnpackJob`, FTP stays legacy), edits (B1021), summarize (B1031), audits (B19xx), invoicing (B2031/B2041), samples/ratios (B2022/B2024), delivery (B2065). |

## Cross-cutting framework follow-ups

| Item | Status |
|---|---|
| Viewer user-id bind for "associated sites/districts" filters | ✅ `:hbsUserId` added and used: submissions / scale control site scopes, sampling plans `districtScope`, final bills `districtScope`, detail returns `scope` / `markScope`, statement transmissions associated locations, P364 "In my Org Unit". Confirm the legacy `HBS_USER.USER_ID` format (`IDIR\NAME`?) in the TEST database. |
| Immutable columns re-sent by update commands (STORE procs overwrite every column) | ✅ `CommandDefinition.existingRow(...)` + `.existing(...)` read `ENTRY_USERID`/`ENTRY_TIMESTAMP` from the current row. Other immutable columns (keys, matched batch ids) should move to `existing()` as each command is reviewed. |
| Report prompt injection (procs concatenate prompts) | ✅ Character whitelist in `HbsReportService.safePrompt`. Long term, fix the procs to bind. |
| Report procs only fence by client when a user id is passed | ✅ `*_USERID` prompts filled from the JWT. Report guards (`ReportRequestGuard`) fence the issued-document reports (`QueriesReportGuard`) and HBS2R032/033 (`SubmissionReportGuard`); HBS2R027 stays ministry-only. |
| Authenticated document downloads | ✅ `format: 'download'` column. |
| Narrower capabilities (`SUMMARY_RETURN_CONTROL`, `SUMMARY_RETURN_CORRECT`, invoice-correction approver, ministry-only sampling admin) | 🟡 Summary-return ones added (`SUMMARY_RETURN_CONTROL`, `SUMMARY_RETURN_CORRECT`, `SUMMARY_RATE_CORRECTION_APPROVE`); sampling admin open. |
| Per-record visibility (`visibleIf` on sections / reports / actions) | 🟡 Actions and row actions: `ActionDef.showWhen` (used on billing generate, scale-control clear, batch-slip cancel/confirm; summary returns use the backend's `actionsAvailable`). Multi-row commands: `bulkActions` with per-row `showWhen` (P832, P842–P844). Sections: detail screens still show every type's field groups. |
| Optimistic locking + active-version integrity | ✅ `workflow/RowLock` on UPDATE_TIMESTAMP in every workflow; a unique index isn't possible while legacy batch runs, so [db/active-version-duplicates.sql](db/active-version-duplicates.sql) monitors ([pinch-points.md](pinch-points.md) §12). |
| OUT-parameter procs (`HBS3R830`, `HBS_RATES.*`) | ✅ via `TableApi` OUT parameters: `HBS_RATES.*` (`rating.StumpageRateService`), `HBS3R830` (`billing.GenerateFinalBillCommand`). |

## Plan

The legacy web UI is switched off while the legacy batch keeps running
([coexistence.md](coexistence.md), decided 2026-10-21, Q15). So every UI
workflow is ported now, plus the batch pieces the UI depends on:

1. All workflows behind the screens (summary returns, detail returns,
   profiles, rating and sampling, billing admin, scale control, submissions)
   as `WorkflowCommand`s ([workflow-porting.md](workflow-porting.md)).
2. Unpack (B1012) for files uploaded in the new app; legacy B1021 onward
   continues from the database. FTP intake stays in legacy.
3. Statement PDFs and the e-mailed statement links (rebuilt from Oracle).
4. Later, job by job once vendor change management allows: FTP intake,
   invoicing (B2031/B2041, with the AR consumer in the regression suite),
   statements and delivery, sampling ratios (B2022/B2024).
