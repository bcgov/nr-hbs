# Scale Control (legacy tab P900)

Area key `scale-control`. Registry ids are prefixed `scaleControl.`.

| Side | Files |
|---|---|
| Backend | `backend/src/main/java/ca/bc/gov/nrs/hbs/api/catalog/ScaleControlCatalog.java` (late submissions, anomalies, assessment windows)<br>`ScaleControlTransportCatalog.java` (timber transport, LDS registry)<br>`ScaleControlAuditCatalog.java` (change log, software use) |
| Frontend | `frontend/src/screens/areas/scale-control.ts`, `scale-control-transport.ts`, `scale-control-audit.ts` |

Legacy paths below are relative to `hba-archive/hbs/trunk/source/ear/`. DDL paths are relative to `nr-mof-db/scripts/THE/`.

## Screen map

| Legacy screen | New screen id(s) | Query / command ids | Ported from |
|---|---|---|---|
| P900 Scale Control Menu | not carried over: the SideNav "Scale Control" group replaces it | — | `hbs-web/.../opq/audit/P900.jsp` |
| Home "Recent Anomalies" links | `scale-anomalies?anomalyType=SCALING\|WEIGHING\|ARRIVAL\|ARR_DEP\|DEP_ARR\|WEIGH_SAMPLE\|WEIGH_RED\|RED_WEIGH` (links live in HomePage.tsx) | `scaleControl.anomalies.search` | `include/mainmenu_ministry.jsp`, `mainmenu_industry.jsp`; `P911Action` (actionType=homepage) |
| Home "Production Control" → Anomaly Assessment Windows | `anomaly-windows` | see P908 | `include/mainmenu_ministry.jsp` |
| P901 Search for Late Submissions | `late-submissions` | `scaleControl.lateSubmissions.search`; report HBS3R902 | `PROCEDURES/V7.01600__HBS3R902.sql`; `hbs-view/.../opq/audit/P901Action.java`; `DetailManager.findLateSubmissions*` |
| P902 List of Late Submissions | `late-submissions` (results) | same | same |
| P901 "Send XML" + `lateSubmissionConfirmation.jsp` (P494) | not carried over: replaced by Export CSV (no XML drop file or e-mail) | — | `P901Action` (HBSLateRequest, `LateSubmissions.mapping`) |
| P911 Search for Scale Event Anomalies | `scale-anomalies` | `scaleControl.anomalies.search` | `P911Action.java`, `AnomalyTypesTag.java`, `ScaleAnomalyHelper.java`; `hbs-ejb/.../dac/anomaly/AnomalyManagerBean.findScaleAnomalyCountByCriteria` |
| P912 List of Scaling Event Gaps and Duplicates | `scale-anomalies` (`anomalyType=SCALING`, `SCALING_GAP`, `SCALING_DUP`) | `scaleControl.anomalies.search`; report HBS2R912 | `PROCEDURES/V7.01546__HBS2R912.sql`; `P912.jsp` (missing-range arithmetic) |
| P914 List of Weighing Event Gaps and Duplicates | `scale-anomalies` (`WEIGHING*`) | same; report HBS2R914 | `V7.01547__HBS2R914.sql`; `P914.jsp` |
| P922 List of Arrival Event Gaps and Duplicates | `scale-anomalies` (`ARRIVAL*`) | same; report HBS2R922 | `V7.01548__HBS2R922.sql`; `P922.jsp` |
| P932 List of Arrivals without matching Departures | `scale-anomalies` (`ARR_DEP`) | same; report HBS2R932 | `V7.01549__HBS2R932.sql` |
| P933 List of Departures without matching Arrivals | `scale-anomalies` (`DEP_ARR`) | same; report HBS2R933 | `V7.01550__HBS2R933.sql` |
| P942 List of Weigh Slips without matching Samples | `scale-anomalies` (`WEIGH_SAMPLE`) | same; report HBS2R942 | `V7.01551__HBS2R942.sql` |
| P952 List of Weigh Slips without matching Red Tag Scales | `scale-anomalies` (`WEIGH_RED`) | same; report HBS2R952 | `V7.01552__HBS2R952.sql` |
| P953 List of Red Tag Scales without matching Weigh Slips | `scale-anomalies` (`RED_WEIGH`) | same; report HBS2R953 | `V7.01553__HBS2R953.sql` |
| P955 User Cleared Anomaly Details | `scale-anomaly-detail` ("User Cleared" section) | `scaleControl.anomalies.detail` | `AnomalyManagerBean.findAnomalyDetails` |
| P954 Confirm Anomaly Clear | `scale-anomaly-detail` shows the record. **The Clear action is not ported** (see below) | — | `P911Action.performActionClearAnomalies`, `AnomalyManagerBean.updateScaleAnomalyLog` |
| clearConfirm.jsp (old bulk clear) | not carried over: dead, superseded by P954 | — | `opq/audit/clearConfirm.jsp` |
| P908 Anomaly Assessment Windows (view mode, from P911 "View Assessment Windows") | `anomaly-windows-view` (header button "View Assessment Windows" on `scale-anomalies`) | `scaleControl.anomalyWindows.list` | `AnomalyManagerBean.findAllAnomalyAssessmentParameter`; `AnomalyAssessmentControlQuery.selectByAll`; `AnomalyBatchEventQuery.selectAllByLastEvent` |
| P908 / P909 Anomaly Assessment Windows (edit) / Confirm New Assessment Windows | `anomaly-windows` (form, PRODUCTION_CONTROL; one control type per save) | `scaleControl.anomalyWindows.detail`, command `scaleControl.anomalyWindows.update` → `HBS_STORE_ANMLY_ASSESS_CONTROL` | `P908Action.java`, `ScaleAnomalyHelper.populateAssessmentControlParamList`; `AnomalyManagerBean.processAssessmentWindow`; `PROCEDURES/V7.01853__HBS_STORE_ANMLY_ASSESS_CONTROL.sql` |
| P961 Search Scale Return Change Log | `change-log` (SCALE_CONTROL_MINISTRY_VIEW) | `scaleControl.changeLog.search`; report HBS2R962 | `hbs-ejb/.../infrastructure/audit/AuditDetailQuery.java`, `AuditManager2Impl.findAuditDetails`; `ScaleReturnAuditsTag.java`, `P962Action.java`; `V7.01554__HBS2R962.sql` |
| P962 Scale Return Change Log (one document, linked from summary/detail return views) | `change-log-document?returnFormat=SSR\|DSR&documentNumber=…&versionNumber=…` | `scaleControl.changeLog.document`; report HBS2R962 | same |
| P971 Search for Timber Transport Events | `transport-events` | `scaleControl.transport.search`; reports HBS3R972/973/974/979 | `V7.01601__HBS3R972.sql`, `V7.01602__HBS3R973.sql`, `V7.01603__HBS3R974.sql`, `V7.01605__HBS3R979.sql`; `P971Action.java`, `LdsHelper.java`; `ReportingFactory2.getTimberTransportEventParameters` |
| P972 List of Cut Block Departures / Arrivals From / Departures To Other Sites / Intra-Site Events | `transport-events` (results) | same | same |
| P971 "Send XML" + `timberTransportConfirmation.jsp` (P494) | not carried over: replaced by Export CSV | — | `P971Action` (HBSTransportRequest, `CutBlockDeparture.mapping`) |
| P975 Search for LDS Registry Entries | `lds-registry` | `scaleControl.lds.search`; report HBS3R976 | `V7.01604__HBS3R976.sql`; `P975Action.java`, `RetrieveLdsRegistryTimbermarkTag.java`, `LoadDescSlipRegistryManagerBean.java` |
| P976 List of Pre-Registered LDS Numbers | `lds-registry` (results), row link to `lds-registry-detail` | same | same |
| admin/P977 Add A LDS Registry Entry | `lds-registry-add` (SCALE_CONTROL_ADMIN) | `scaleControl.lds.create` → `HBS_CREATE_LOAD_DESC_SLIP_REG` (seq LOAD_DESC_SLIP_REGISTRY_SEQ) | `P977Action.java`; `V7.01643__HBS_CREATE_LOAD_DESC_SLIP_REG.sql` |
| admin/P978 Update A LDS Registry Entry | `lds-registry-detail` + `lds-registry-edit` (SCALE_CONTROL_ADMIN) | `scaleControl.lds.entry`, `scaleControl.lds.update` → `HBS_STORE_LOAD_DESC_SLIP_REG` | `P977Action.java`; `V7.01878__HBS_STORE_LOAD_DESC_SLIP_REG.sql` |
| P979 Confirm Delete LDS Registry Entry | Delete action (confirm modal) on `lds-registry-detail` | `scaleControl.lds.delete` → `HBS_REMOVE_LOAD_DESC_SLIP_REG` | `V7.01767__HBS_REMOVE_LOAD_DESC_SLIP_REG.sql` |
| P981 Search for LDS Registry Violations | `lds-violations` | `scaleControl.lds.violations`; report HBS3R982 | `V7.01606__HBS3R982.sql`, `FUNCTIONS/*HBS_LDS_REG_VIOLATION_TYPE.sql`; `P981Action.java`, `RetrieveLdsRegViolationsTag.java` |
| P982 List of LDS Registry Violations | `lds-violations` (results). The "Assigned LDS Range" box is now a per-row column | same | same |
| P991 Search for Scale Site Software Use | `software-use` and `software-anomalies` (same criteria) | `codes.scaleControl.softwareProducts` | `P991Action.java`, `SoftwareProductsTag.java`; `dac/srt/detail/ManagerBean.getSoftwareProductByClient` |
| P993 Software Use List | `software-use` (SOFTWARE_USE_VIEW) | `scaleControl.software.use`; report HBS3R993 | `V7.01608__HBS3R993.sql` |
| P992 List of Software Anomalies | `software-anomalies` (SCALE_CONTROL_VIEW, not for vendors) | `scaleControl.software.anomalies`; report HBS3R992 | `V7.01607__HBS3R992.sql` |

Area code lists: `codes.scaleControl.controlTypes` (SCALE_CONTROL_TYPE_CODE), `codes.scaleControl.assessmentStatuses` (ANOMALY_ASSESSMENT_STATUS_CODE), `codes.scaleControl.docTypes` (DETAIL_SCALE_DOC_TYPE_CODE), `codes.scaleControl.softwareProducts`.

### `anomalyType` URL values

| URL value | Legacy radAnomalyType | SCALE_CONTROL_TYPE_CODE | SCALE_ANOMALY_TYPE_CODE |
|---|---|---|---|
| SCALING / SCALING_GAP / SCALING_DUP | scalinganomaly | S | any / G / D |
| WEIGHING / WEIGHING_GAP / WEIGHING_DUP | weighinganomaly | W | any / G / D |
| ARRIVAL / ARRIVAL_GAP / ARRIVAL_DUP | arrivalanomaly | A | any / G / D |
| ARR_DEP | arrdepanomaly | X | M |
| DEP_ARR | deparranomaly | D | M |
| WEIGH_SAMPLE | weighsampleanomaly | Y | M |
| WEIGH_RED | weighredanomaly | Z | M |
| RED_WEIGH | redweighanomaly | R | M |

### Design notes

- **One anomaly list instead of eight.** The eight HBS2R9xx procs are rebuilt as one `UNION ALL` in `ScaleControlCatalog.ANOMALY_SQL`. Each branch joins SCALE_ANOMALY_LOG to one document table (LOG_TALLY, SAMPLE_LOG_TALLY, WEIGH_SLIP, SCL_SITE_ARRIVAL_LEDGER, SCL_SITE_DEPARTURE_LEDGER) through the same FK pair the procs used. The list shows a common set of columns; the type-specific ones (LDS, transport ID, timber brand, pop/strat/year, sample weight) are on `scale-anomaly-detail`. HBS2R922's event-type restrictions (log tally PS/RR/4R/FD, sample tally RS) are kept for control type A.
- **Client fence.** Industry users are fenced to scale sites owned by their client (`scale_site.owner_cli_number`) on late submissions and anomalies. Legacy fenced CLI "All Sites" anomaly searches the same way, through HBS_USER.CLIENT_NUMBER. For control type X the legacy procs joined the site by timber brand, so they fenced on the *originating* site. The port fences on the receiving site.
- **Dates on `scale-anomalies` are optional**, so the Home deep links (which carry only `anomalyType`) run straight away. Legacy required the Document Event Date range.

## Legacy logic not yet ported

1. **Clear an anomaly (P954).** Source: `hbs-view/.../opq/audit/P911Action.java` (`performActionClearAnomalies`, actionType=Clear / clearConfirmation) and `hbs-ejb/.../dac/anomaly/AnomalyManagerBean.updateScaleAnomalyLog`. This is not a safe single command. `HBS_STORE_SCALE_ANOMALY_LOG` (`PROCEDURES/V7.01915__HBS_STORE_SCALE_ANOMALY_LOG.sql`) rewrites all 23 columns, so a body-driven command would let the browser rewrite the document links and batch ids. A dedicated service should:
   - require HBS_SCALE_ADMIN (legacy role check in P911Action);
   - re-read the SCALE_ANOMALY_LOG row by `anomalyId` and require status `O`;
   - require ANOMALY_ASSESSMENT_CONTROL status `RCM` or `WCM` for its control type (`clearFlag`);
   - require the scale site's ORG_UNIT_NO to be in the admin's district data domain (`hbs3:isScaleSiteAssociatedWithOrgUnit`, HBS_USER_DIST_DATA_DOMAIN);
   - call HBS_STORE_SCALE_ANOMALY_LOG with the existing values, except status `U`, `i_user_id` = the audit user, `i_cleared_datetime` = now, and `i_user_cleared_comment` = the comment (optional, max 240). Note `i_org_batch_id` is the last argument.

   It then becomes an ActionDef on `scale-anomaly-detail` with a Comment field (capability SCALE_CONTROL_ADMIN, or ANOMALY_EDIT if the business wants industry site admins to clear).
2. **Assessment window update (P908 → P909 Confirm).** Source: `P908Action.java`, `HBSValidator.validateAnomalyAssessmentWindow`, `AnomalyManagerBean.processAssessmentWindow` / `processAssessmentWindowExpand`. The `anomaly-windows` form calls only `HBS_STORE_ANMLY_ASSESS_CONTROL`, and the read-only batch-id, status and sequence values come back from the browser. A dedicated service should:
   - reject the change unless the current DB status is RCM or WCM (`validateStatus`: "Your request was denied, because the status of a scale control type has changed.");
   - enforce the rules: From Days > To Days; new From > old To; new To < old From; at least one value changed;
   - when To Days is unchanged and From Days has increased (window expanded): set status `WIP`, call `HBS_UPDATE_STALE_ANOMALIES(TO_CHAR(SYSDATE - newFromDays,'yyyy-mm-dd'), type, current_anomaly_batch_id)`, then set status `WCM`. Without this step, stale-dated (`S`) anomalies inside the widened window are not reopened.
   - show the P909 preview first (proposed next run = tomorrow, From Date = run − From Days, To Date = run − To Days).
3. **Home-link default window (P911 actionType=homepage).** Legacy set Scale Date From/To to the last HBS_ANOMALY_BATCH_EVENT `to_scale_date` − 30 days … `to_scale_date` (`AnomalyManager.findLastAnomalyBatchEvent`, `WORKBENCH_DATE_RANGE`). The deep link now searches without a date window.
4. **"Associated Sites" / "Sites in Associated Districts" scopes** (P901 and P911, `radSearchBy`/`radSearchBySites`). These need HBS_USER_SITE_DATA_DOMAIN / HBS_USER_DIST_DATA_DOMAIN filtered by the caller's user id. QueryService has no user-id viewer bind (`:hbsUserId`), so they are not offered. Single site, region/district and (for industry) the client fence are.
5. **Scaler-role restrictions on P911** (CLI_SCALER without CLI_SITE_ADMIN). Legacy forced the user's own primary scaler licence (`hbs3:retrieveScalerLicense`) and hid the arrival and mismatch types. Not ported; this needs the user-id bind or a scaler-licence claim.
6. **P911 / P901 validations** (`P911Action.validateForm`, validation.xml P901Form):
   - limiting year (from date ≥ 1 Jan of current year − 7), dates not in the future, From ≤ To;
   - the scale site must exist and be in the user's site data domain (`errors.scaleanomaly-user-access`);
   - the licence must exist in QUANT_LICENSE;
   - the licence option cannot be combined with arrival or arrival/departure types;
   - days late must be 1–731.

   Bad input now returns no rows instead of an error.
7. **P971, P975, P977, P981 cross-reference validations** (timber mark, scale site, region/district; cut block mask; number-range toggles; 12-month limit). Also the P971 defaults: To Date = From Date and To number = From number when left blank (`LdsHelper.setCriteria`). See `validation.xml` P971Form / P975Form / P977Form / P981Form and `HBSValidator`.
8. **Change-log industry fence.** `change-log-document` does not check who owns the return. Legacy didn't either. Fence it by joining the DDN to LOG_TALLY / WEIGH_SLIP / SAMPLE_LOG_TALLY and the SDN to the summary tables, reusing the scale-returns fence.
9. **P991 "Product required for SPC_SFTWR_VNDR"** (`requiredOnRole`). The SQL vendor fence (`:hbsUserType = 'SPC'` → own products only) covers it on the server.

## Report-path gaps (shared `HbsReportService`, not in these files)

> **Update (integration):** `HbsReportService` now fills `*_USERID` / `*_USER_ID` prompts from the token, whitelists prompt characters (no quotes/semicolons/comment markers) and reformats dates to `YYYY-Mon-DD` for HBS3R972–979. The anomaly, HBS3R902 and other non-industry reports are ministry-only in `HbsReportController`. The remaining items below (end-of-day "To" dates, `RB_SEARCH_BY`, HBS3R992 rules) are still open.

- **Report procs don't fence by client.** The anomaly procs (`RB_USER_ID` + `RB_SEARCH_BY`) and HBS3R902 (`PLLS_USERID`) restrict only when a user id is passed, and the report service doesn't inject one. An industry user's Print therefore returns rows from other clients. HbsReportService should overwrite `*_USER_ID` / `PLLS_USERID` from the token (and set `RB_SEARCH_BY=allsites` for CLI users), or hide these buttons from industry users.
- **Unquoted prompts are pasted into dynamic SQL** in HBS2R9xx, HBS3R902, HBS3R972–979, HBS3R976 and HBS3R982 (SQL injection through report prompts). HbsReportService needs prompt validation.
- **Date formats:** the HBS2R9xx and HBS3R902 procs parse dates with `yyyy-mm-dd-hh24-mi-ss`, so ISO dates work but "To" means midnight. HBS3R972–979 expect `YYYY-Mon-DD`, so their Print buttons fail on dates until a date-format hook exists. HBS3R982 and HBS2R962 also exclude the last day.
- **Search scope in the anomaly reports:** `RB_SEARCH_BY` is left blank. HBS2R912 then applies the licence filter but ignores `RB_DISTRICT_SCALED`, which it applies only with search-by `district`. `RB_ANOMALY_TYPE` (G/D) is not passed.
- **HBS3R992 differs from the screen.** The report still runs the legacy proc, while the screen uses corrected anomaly rules (see `ScaleControlAuditCatalog`).

## Open questions for the business

1. Who may clear anomalies: HBS_SCALE_ADMIN only (legacy), or also CLI_SITE_ADMIN / CLI_SCALER (ANOMALY_EDIT)?
2. Scale Event Anomalies and Late Submissions are open to every CLI user (SCALE_CONTROL_VIEW), fenced to their client's sites. Legacy granted P911 only to CLI_SCALER / CLI_SITE_ADMIN among industry roles. Is that acceptable?
3. Should HBS3R992 use the corrected anomaly rules, or match the legacy report?
4. Should CLI users' software-use results be limited to their own sites?
5. Should LDS registry update and delete stay HBS_SCALE_ADMIN-only? They are now enforced on the server; legacy only hid the links.
