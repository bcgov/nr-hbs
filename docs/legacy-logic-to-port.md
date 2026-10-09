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
| Summary returns | [areas/summary-returns.md](areas/summary-returns.md#legacy-logic-not-yet-ported) | Add/update/hold/release/discard/cancel/replace/rate-correction workflow: `SummaryScaleReturnManagerBean` (~13.4K LOC), state machine, release edits, pricing via `HBS_RATES`, confirmation preview. 16 reserved command ids. A **DDL gap** (no procs for WEIGHT/SAMPLE_SCALE_SUMMARY etc.). |
| Detail returns | [areas/detail-returns.md](areas/detail-returns.md) | Every write: add, save, release, hold, re-edit, discard, cancel/replace, approve/reject, clear digital-signature failure, event change, bulk — `DetailManagerBean` (~13.3K LOC), `WorkflowManager`, `StateManager`. |
| Submissions | [areas/submissions.md](areas/submissions.md#legacy-logic-not-yet-ported) | Role-based default criteria (now possible with `:hbsUserId`), P283 batch-slip state rules, P280/P041 validations. |
| Scale control | [areas/scale-control.md](areas/scale-control.md#legacy-logic-not-yet-ported) | Clear anomaly (P954) service, anomaly-window save validations + `HBS_UPDATE_STALE_ANOMALIES`, input validations. |
| Profiles | [areas/profiles.md](areas/profiles.md#legacy-logic-not-yet-ported) | Scaler auth keys (DBMS_CRYPTO — security), population add/expiry with 23:59:59 ratio dates, date-overlap / domain validations. |
| Rating & sampling | [areas/rating-and-sampling.md](areas/rating-and-sampling.md#legacy-logic-not-yet-ported) | Override-rule overlap/back-dating validations, status gating, stumpage-rate lookup (OUT-param `HBS_RATES`), all sampling-plan writes (`SamplingPlanService`). |
| Billing & admin | [areas/billing-and-admin.md](areas/billing-and-admin.md) | Force-date validation windows, `HBS3R830` generate (OUT param), P835/P836 checks, field-scale bulk actions. |
| Queries | [areas/queries.md](areas/queries.md#legacy-logic-not-yet-ported) | Statement PDF rendering (FOP 0.20.5 + 9 XSL versions); merged XML/PDF generation; screen validations. |
| Batch | [batch-jobs.md](batch-jobs.md) | Unpack (B1012), edits (B1021), summarize (B1031), audits (B19xx), invoicing (B2031/B2041), samples/ratios (B2022/B2024), delivery (B2065). |

## Cross-cutting framework follow-ups

| Item | Status |
|---|---|
| Viewer user-id bind for "associated sites/districts" filters | ✅ `:hbsUserId` added. Areas still need to add the filters (each doc has the SQL). Confirm the legacy `HBS_USER.USER_ID` format (`IDIR\NAME`?) in the TEST database. |
| Immutable columns re-sent by update commands (STORE procs overwrite every column) | ✅ `CommandDefinition.existingRow(...)` + `.existing(...)` read `ENTRY_USERID`/`ENTRY_TIMESTAMP` from the current row. Other immutable columns (keys, matched batch ids) should move to `existing()` as each command is reviewed. |
| Report prompt injection (procs concatenate prompts) | ✅ Character whitelist in `HbsReportService.safePrompt`. Long term, fix the procs to bind. |
| Report procs only fence by client when a user id is passed | ✅ `*_USERID` prompts filled from the JWT. HBS2R027/032/033 removed from industry reports (no client prompt). |
| Authenticated document downloads | ✅ `format: 'download'` column. |
| Narrower capabilities (`SUMMARY_RETURN_CONTROL`, `SUMMARY_RETURN_CORRECT`, invoice-correction approver, ministry-only sampling admin) | 🔴 Add with the corresponding services. |
| Per-record visibility (`visibleIf` on sections / reports / actions) | 🔴 Framework enhancement; detail screens currently show every type's field groups. |
| Optimistic locking + unique active-version index | 🔴 Needs the DBA ([pinch-points.md](pinch-points.md) §12). |
| OUT-parameter procs (`HBS3R830`, `HBS_RATES.*`) | 🔴 Needs bespoke DAOs (`AbstractStoredProcedureDao` already supports them). |

## Recommended order

1. **Read-only cut-over first.** Every query screen, report and the XML upload
   work today against the shared DB, while the legacy app and batch keep doing
   workflows ([questions.md](questions.md) Q15).
2. Summary-return workflow service: it unblocks the data-control staff, and
   most data fixes (stuck ERR/HLD, duplicate versions) live here.
3. Detail-return workflow + B1012/B1021/B1031 intake pipeline.
4. Invoicing (B2031/B2041) + statement rendering + delivery, with the AR
   consumer in the regression suite.
5. Sampling plans and ratios (B2022/B2024).
