# Submissions & batch tracking

Inventory: legacy section 05 (Data Submission, File Upload/Download, Reports & Printing).
Legacy paths are relative to `hba-archive/hbs/trunk/source/ear`.

| Area | Files |
|---|---|
| Backend | `backend/.../api/catalog/SubmissionsCatalog.java` (transmissions, batches), `SubmissionsBatchSlipCatalog.java` (paper batch slips), `SubmissionsProcessingParamsCatalog.java` (P038 settings) |
| Frontend | `frontend/src/screens/areas/scale-returns-submissions.ts` (area `scale-returns`), `frontend/src/screens/areas/admin-processing.ts` (area `admin`) |
| DB (proposed) | `docs/db/proposed-ddl.sql` — `THE.HBS_APP_SETTING` + `THE.HBS_STORE_APP_SETTING` (**requires a DBA change**) |

## Screen map

| Legacy | Title | New screen id | Queries / commands | Ported from |
|---|---|---|---|---|
| P041 | Search for Detail Scale Transmissions | `xml-transmissions` (search, criteria) | `submissions.transmissions` | proc `HBS2R041` (`nr-mof-db/scripts/THE/PROCEDURES/V7.01515__HBS2R041.sql`) via `hbs-ejb/.../dac/srt/detail/ManagerBean.findTransmissionsByCriteria`; criteria from `hbs-web/.../dac/srt/P041.jsp`, `hbs-view/.../dac/srt/detail/P032Action.java` |
| P032 | List of Received Detail Scale Transmissions (XML) | `xml-transmissions` (results), `xml-transmission-detail` (row) | `submissions.transmissions`, `submissions.transmission`, `submissions.transmissionBatches`, `submissions.transmissionErrors`; reports **HBS2R032** (Xmit Id link), **HBS2R033** (File Name link) | as above; "Reason" = first `TRANSMISSION_FORMAT_ERROR` (DetailHbsEditError type 8, `hbs-ejb/.../dac/DetailHbsEditErrorQuery.java`) |
| P024 | Search for Submitted Detail Batches | `detail-batches` (search; URL param `returnType` = P/W/S/A/D/F/ALL) | `submissions.batches`, `codes.submissions.detailDocTypes` | proc `HBS2R027` (`.../PROCEDURES/V7.01503__HBS2R027.sql`) via `ManagerBean.findBatchCountByCriteria` / `BatchEntityBean.ejbFindByCriteria`; criteria `dac/srt/P024.jsp` + `dac/include/detailBatchReceivedCriteria.jsp`, `hbs-view/.../dac/srt/P024Action.java` |
| P027 | List of Submitted Detail Scale Data Batches (XML) | `detail-batches` (results, "Print" = **HBS2R027**), `detail-batch` (row; "Rejected Documents" = **HBS2R032** with batch id; "View Documents" → `detail-workbench?batchId&returnType`) | `submissions.batches`, `submissions.batch`, `submissions.batchErrors` | as above; batch "Reason" = first `DTL_BAT_FORMAT_ERROR` (type 9) |
| P032 "Batch Count" link (`detailBatchReceivedSearchFromHome1`) | — | `xml-transmission-detail` "Batches" table | `submissions.transmissionBatches` | `P024Action.findDetailTransId` |
| P280 | Generate a Batch Slip | `batch-slip-add` (form) | `batchSlip.create` → `HBS_CREATE_HBS_PAPER_BATCH`, key `HBS_PAPER_BATCH_SEQ` | `hbs-view/.../dac/srt/detail/P280Action.performAddBatchSlip`, `hbs-ejb/.../dac/srt/detail/PaperBatchQuery.java` |
| P281 | Search for Batch Slip | `batch-slips` (search, `nav: false`) | `submissions.batchSlips` | proc `HBS3R281` (`V7.01562__HBS3R281.sql`) via `PaperBatchEntityBean.ejbFindByCriteria`; `P281Action` |
| P282 | List of Batch Slips | `batch-slips` (results) | `submissions.batchSlips` | as above |
| P283 | Update a Batch Slip | `batch-slip` (view + Cancel / Confirm actions), `batch-slip-update` (form) | `submissions.batchSlip`; `batchSlip.update`, `batchSlip.cancel` (status CLR), `batchSlip.confirm` (status MAT) → `HBS_STORE_HBS_PAPER_BATCH`; `codes.submissions.regions`, `codes.submissions.paperBatchStatuses` | `P280Action.performUpdateBatchSlip`, `P281Action.performViewBatchSlip`; "Documents Received" = `SummaryScaleReturnQuery.selectCountByVolumeEstBatchId` (count of `HBS_SCALE_RETURN.VOLEST_BATCH_ID`) |
| P038 | HBS Alert and Processing Parameters | `processing-parameters` (area `admin`, capability `PRODUCTION_CONTROL`) | `admin.processingParameters`, `admin.appSettings`; `admin.processParameter.save`, `admin.printer.save`, `admin.alertText.save`, `admin.alertText.delete` → **proposed** `THE.HBS_STORE_APP_SETTING` | `hbs-view/.../dac/srt/detail/P038Action.java`, `hbs-ejb/.../dac/srt/detail/HBSProcessingParamHelper.java` (flat files) |
| P039 | Confirm HBS Processing Parameters / Confirm Alert Text / Delete Alert Text | confirmation modal of each `processing-parameters` action | — | `dac/srt/P039.jsp` |
| `hbs3:retrieveAlertText` | Alert banner (home, about, P009, P400, P900, P199, P850, P800) | Home page banner | `admin.alertText` (ANY_USER, one row, `alertText`) | `HBSProcessingParamHelper.readAlertTextFromFile` |
| P505/P506 | Submit File of Detail Returns | not in this area | — | Implemented separately (file upload). |
| P410 | Recent Queries (report pickup) | not carried over | — | Reports now stream directly from the in-process Jasper engine; the shared per-client drop folder (which leaked reports between users of a client) is gone. Belongs to Queries. |
| `ReportDelivery` / `StatementDelivery` / `DownloadPublicKeyServlet` servlets, XML "request" outputs, schemas | — | not in this area | — | Documented in the inventory; owned by Queries / Billing / public-key work. |
| B1011/B1012/B1021/... batch jobs | — | not carried over here | — | Batch pipeline; see `docs/legacy-logic-to-port.md` / legacy-batch-integrations. |

### Settings table (requires DBA change)

`THE.HBS_APP_SETTING` does **not** exist in nr-mof-db. `APPLICATION_PREFERENCE` (shared, no write proc),
`SCALE_CTL_SYSTEM_SETTING` (Scale Control System, 4-char key) and `PREFERENCE` (per-user) were rejected.
The proposed DDL + proc are in `docs/db/proposed-ddl.sql`. Until they are deployed, `processing-parameters`
fails to load and the Home banner silently shows nothing (HomePage swallows the error).

Keys: `B2031.START_LOC`, `B2031.TIME_LIMIT`, `B2041.START_LOC`, `B2041.TIME_LIMIT`, `PRINTER.SEND_TO`,
`PRINTER.COPY_TO`, `ALERT_TEXT`. A CHECK constraint and the proc reject any other key; the proc applies the
legacy P038 validations (whole numbers; `\\server\printer`; alert ≤ 296 chars). Delete alert = value NULL.

## Behaviour differences / decisions

- **Default date range**: `xml-transmissions` and `detail-batches` default to the last 30 days
  (`WORKBENCH_DATE_RANGE=-30`); `xml-transmissions` auto-searches on open (legacy
  `detailScaleTransmissionListDefault`). Legacy P041's own default (yesterday–today) is not reproduced.
- **Client fence** (new; legacy relied on menu defaults): industry/SPC users see a transmission/batch only if
  `CLIENT_NUMBER` or `RECD_CLIENT_NUMBER` is their client, or a batch is at a scale site their client owns
  (legacy `L<userId>` "all sites" domain of HBS2R027).
- **Transmission ID vs general criteria**: legacy ignored the dates when a Transmission ID was entered; here
  the defaulted dates still apply (screen note tells the user to clear them).
- **Exclusion checkbox** defaults to on for everyone (legacy: on for MOF, off for CLI).
- **Scale site / submitter radios** on P024 are kept for the HBS2R027 report prompts; the on-screen query
  applies Region/District, Single Site, Creator and Input User ID whenever they are filled.
- P027 "Batch Step" uses `HBS_XML_TRANS_STEP_CODE` (as HBS2R027 does); the legacy `DetailTransmissionTag` showed
  the `EDI_TRANS_STEP_CODE` description.
- Batch slip screens are not in the nav (P002 links removed by ticket 8835, 2010) and are ministry-only
  (`MINISTRY` to view, `BATCH_SLIP_EDIT` to write).
- HBS2R027 date prompts receive `yyyy-mm-dd`; the proc's `TO_DATE(..., 'yyyy-mm-dd-hh24-mi-ss')` treats the
  To date as midnight, so the report excludes the last day (legacy passed 23:59:59).

## Legacy logic not yet ported

1. **Role-based default criteria for P041/P024** (`hbs-view/.../dac/srt/detail/P032Action.setDefaultSearchCriteria`,
   `hbs-view/.../dac/srt/P024Action.java` lines ~1450–1570): HBS_SCALE_ADMIN → "Sites in Associated Districts";
   MOF_SCALER / HBS_DTL_DATA_ENT / independent CLI_SCALER → Input User ID = self (fixed); CLI_SITE_ADMIN →
   "Associated Sites" + own client; company CLI_SCALER → own client + self; SPC_SUBM_AGNT → own client.
   `ScreenDef` defaults are static and `QueryService` exposes no `:hbsUserId` bind, so neither the
   per-role defaults nor the **"Associated Sites" (`HBS_USER_SITE_DATA_DOMAIN`) / "Sites in Associated
   Districts" (`HBS_USER_DIST_DATA_DOMAIN`)** options can be expressed. Needs a framework addition: a viewer
   bind `:hbsUserId` (legacy user id) and/or role-aware `defaultValue`s.
2. **Report prompt fencing**: HBS2R027/HBS2R032/HBS2R033 have no `*_USERCLILOC` prompt, so the report
   endpoint (ANY_USER) does not fence them by client. Legacy passed `L<userId>` for non-MOF "All Sites"
   (HBS2R027) and only offered 032/033 for rows the user could see. The report service should either
   restrict these reports to ministry users or verify the transmission/batch against the client fence.
3. **P283 Confirm rule** (`P280Action.perform`, ACTION_CONFIRM): status → MAT only when
   Documents Received == Document Count, else error `error.doc-count-received.invalid`; Cancel/Confirm only
   while status = PND; Save hidden when CLR; site/dates/region/sender editable only while PND. The generic
   command cannot check these — `batchSlip.confirm` / `cancel` / `update` should get a small service that
   re-reads `HBS_PAPER_BATCH`, enforces the state rules and the count match, then calls
   `HBS_STORE_HBS_PAPER_BATCH`.
4. **`HBS_STORE_HBS_PAPER_BATCH` overwrites every column**: the update/cancel/confirm commands re-send
   `ENTRY_USERID`, `ENTRY_TIMESTAMP`, `DTL_DOC_BATCH_ID`, `HBS_RETURN_TYPE_CODE` and status from the request
   body (time of day of `ENTRY_TIMESTAMP` is truncated to the date). The service in item 3 should take these
   from the DB row instead of the client.
5. **P280 validations** (validation.xml `P280Form`): `scaleSiteNoCrossRef` (site exists),
   `regionDistrictCrossRef`, `fromBeforeTo`, `beforeCurrent`, `userDomainRequired`/`userId` on sender,
   document count integer ≥ 1. Not enforced (the proc has no checks).
6. **P041/P024 form validations** (validation.xml `P032Form` / `P024Form`): `fromBeforeTo`, `beforeCurrent`,
   `limitingYear`, `scaleSiteNoCrossRef`, `clientNumberValid`, client+location pairing.
7. **P038 "Set to Defaults"** (start 0, limit 9999999999) is two writes; the user saves each value.
8. **Batch jobs reading the settings**: B2031/B2041 (`HBSProcessingParamHelper.canProcess`) and B2075 printing
   (`loadPrinterValues`) read the flat files; whatever replaces them must read `HBS_APP_SETTING`.
9. **Report logging**: legacy logged P032/P027 report runs via `SummaryScaleReturnHelper.logReport` (REPORT_LOG).

## Capabilities

All existing: `SCALE_RETURNS_VIEW` (transmissions/batches; legacy `web:/dac/srt/*` MOF/CLI plus SPC menus),
`MINISTRY` (batch slip views), `BATCH_SLIP_EDIT` (P280/P283 writes), `PRODUCTION_CONTROL` (P038),
`ANY_USER` (`admin.alertText`). None missing.
