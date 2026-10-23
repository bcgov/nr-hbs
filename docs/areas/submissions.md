# Submissions & batch tracking

Inventory: legacy section 05 (Data Submission, File Upload/Download, Reports & Printing).
Legacy paths are relative to `hba-archive/hbs/trunk/source/ear`.

| Area | Files |
|---|---|
| Backend | `backend/.../api/catalog/SubmissionsCatalog.java` (transmissions, batches), `SubmissionsBatchSlipCatalog.java` (paper batch slips), `SubmissionsProcessingParamsCatalog.java` (P038 settings) |
| Frontend | `frontend/src/screens/areas/scale-returns-submissions.ts` (area `scale-returns`), `frontend/src/screens/areas/admin-processing.ts` (area `admin`) |
| Settings | the backend's settings ConfigMap (`processparams.AppSettings`; [coexistence.md](../coexistence.md#settings-configmap)) |

## Screen map

| Legacy | Title | New screen id | Queries / commands | Ported from |
|---|---|---|---|---|
| P041 | Search for Detail Scale Transmissions | `xml-transmissions` (search, criteria) | `submissions.transmissions` | proc `HBS2R041` (`nr-mof-db/scripts/THE/PROCEDURES/V7.01515__HBS2R041.sql`) via `hbs-ejb/.../dac/srt/detail/ManagerBean.findTransmissionsByCriteria`; criteria from `hbs-web/.../dac/srt/P041.jsp`, `hbs-view/.../dac/srt/detail/P032Action.java` |
| P032 | List of Received Detail Scale Transmissions (XML) | `xml-transmissions` (results), `xml-transmission-detail` (row) | `submissions.transmissions`, `submissions.transmission`, `submissions.transmissionBatches`, `submissions.transmissionErrors`; reports **HBS2R032** (Xmit Id link), **HBS2R033** (File Name link) | as above; "Reason" = first `TRANSMISSION_FORMAT_ERROR` (DetailHbsEditError type 8, `hbs-ejb/.../dac/DetailHbsEditErrorQuery.java`) |
| P024 | Search for Submitted Detail Batches | `detail-batches` (search; URL param `returnType` = P/W/S/A/D/F/ALL) | `submissions.batches`, `codes.submissions.detailDocTypes` | proc `HBS2R027` (`.../PROCEDURES/V7.01503__HBS2R027.sql`) via `ManagerBean.findBatchCountByCriteria` / `BatchEntityBean.ejbFindByCriteria`; criteria `dac/srt/P024.jsp` + `dac/include/detailBatchReceivedCriteria.jsp`, `hbs-view/.../dac/srt/P024Action.java` |
| P027 | List of Submitted Detail Scale Data Batches (XML) | `detail-batches` (results, "Print" = **HBS2R027**), `detail-batch` (row; "Rejected Documents" = **HBS2R032** with batch id; "View Documents" → `detail-workbench?batchId&returnType`) | `submissions.batches`, `submissions.batch`, `submissions.batchErrors` | as above; batch "Reason" = first `DTL_BAT_FORMAT_ERROR` (type 9) |
| P032 "Batch Count" link (`detailBatchReceivedSearchFromHome1`) | — | `xml-transmission-detail` "Batches" table | `submissions.transmissionBatches` | `P024Action.findDetailTransId` |
| P280 | Generate a Batch Slip | `batch-slip-add` (form) | workflow `batchSlip.create` → `HBS_CREATE_HBS_PAPER_BATCH`, key `HBS_PAPER_BATCH_SEQ` | `hbs-view/.../dac/srt/detail/P280Action.performAddBatchSlip`, `hbs-ejb/.../dac/srt/detail/PaperBatchQuery.java` |
| P281 | Search for Batch Slip | `batch-slips` (search, `nav: false`) | `submissions.batchSlips` | proc `HBS3R281` (`V7.01562__HBS3R281.sql`) via `PaperBatchEntityBean.ejbFindByCriteria`; `P281Action` |
| P282 | List of Batch Slips | `batch-slips` (results) | `submissions.batchSlips` | as above |
| P283 | Update a Batch Slip | `batch-slip` (view + Cancel / Confirm actions), `batch-slip-update` (form) | `submissions.batchSlip`; workflows `batchSlip.update`, `batchSlip.cancel` (status CLR), `batchSlip.confirm` (status MAT) → `HBS_STORE_HBS_PAPER_BATCH`; `codes.submissions.regions`, `codes.submissions.paperBatchStatuses` | `P280Action.performUpdateBatchSlip`, `P281Action.performViewBatchSlip`; "Documents Received" = `SummaryScaleReturnQuery.selectCountByVolumeEstBatchId` (count of `HBS_SCALE_RETURN.VOLEST_BATCH_ID`) |
| P038 | HBS Alert and Processing Parameters | `processing-parameters` (area `admin`, capability `PRODUCTION_CONTROL`), read-only | `admin.processingParameters` (settings ConfigMap); share bridge `GET /integrations/legacy/process-parameters` | `hbs-view/.../dac/srt/detail/P038Action.java`, `hbs-ejb/.../dac/srt/detail/HBSProcessingParamHelper.java` (flat files) |
| P039 | Confirm HBS Processing Parameters / Confirm Alert Text / Delete Alert Text | not carried over (no edits in the app) | — | `dac/srt/P039.jsp` |
| `hbs3:retrieveAlertText` | Alert banner (home, about, P009, P400, P900, P199, P850, P800) | Home page banner | `admin.alertText` (ANY_USER, one row, `alertText`) | `HBSProcessingParamHelper.readAlertTextFromFile` |
| P505/P506 | Submit File of Detail Returns | `/scale-returns/submit` (bespoke `SubmitScaleDataPage`); success links to `xml-transmission-detail` | `POST /api/v1/hbs/submissions` | `SubmitDocumentAction`; unpack = B1012 port below |
| P410 | Recent Queries (report pickup) | not carried over | — | Reports now stream directly from the in-process Jasper engine; the shared per-client drop folder (which leaked reports between users of a client) is gone. Belongs to Queries. |
| `ReportDelivery` / `StatementDelivery` / `DownloadPublicKeyServlet` servlets, XML "request" outputs, schemas | — | not in this area | — | Documented in the inventory; owned by Queries / Billing / public-key work. |
| B1012 unpack (UI uploads only) | — | outcome on `xml-transmissions` / `xml-transmission-detail` (step, status, P032 "Reason" = transmission format error; batches and their errors) | — | ported: `intake.UnpackJob` (see "Ported: B1012 unpack" below) |
| B1011/B1012 (FTP), B1021/... batch jobs | — | stay in legacy batch | — | see `docs/batch-jobs.md`, `docs/coexistence.md` |

### Settings ConfigMap

The P038 values live in the backend's settings ConfigMap, one key each: `B2031.START_LOC`,
`B2031.TIME_LIMIT`, `B2041.START_LOC`, `B2041.TIME_LIMIT`, `PRINTER.SEND_TO`, `PRINTER.COPY_TO`,
`ALERT_TEXT`. They are changed in OpenShift, not in the app; `processing-parameters` shows them read-only and
the Home banner shows `ALERT_TEXT`. The share bridge applies the legacy P038 validations (whole numbers;
`\\server\printer`) before writing the legacy files. Setup: [coexistence.md](../coexistence.md#settings-configmap).

## Ported: B1012 unpack of P505 uploads (work package 2)

Legacy `hbs-batch/.../detail/input/main/B1012.java` (`process` and everything it calls) for the files
uploaded in this app; FTP files stay with legacy B1011/B1012 (it only scans the XMLinput share).
Package `backend/.../api/intake`:

| Piece | Ports |
|---|---|
| `UnpackJob` (`@Scheduled` + ShedLock `HBS_B1012_UNPACK`; `hbs.batch.enabled` AND `hbs.batch.unpack.enabled`, cron `hbs.batch.unpack.cron`, default every 5 min) | `B1012.execute`: picks RCD/CSA transmissions whose RECD_DIRECTORY is a `SubmissionStorage` key `input/<client>/<id>.xml`; moves the file to `archive/` after commit; failures → "Batch Failure - B1012" e-mail, transmission rolled back to RCD/CSA and retried; `XmlIntakeMonitorJob` alerts when a file waits > 2 h |
| `UnpackService` | one `@Transactional` per transmission, so UNP/PRC is never committed (legacy `removeAbnormalTerminations` can't touch it) |
| `TransmissionUnpacker` | `process`, `processBatches`, `processLedgerBatches`, `processBatch`, `matchPaperBatch`, `processDocuments`, `processBatchDocument`, `processEntries`, `validationError` (3 variants); transmission state `unpacking/unpackComplete/unpackFailed` via `HBS_STORE_DTL_SCL_DOC_TRANS` |
| `VersionComposer` | `composeWS/WSB/WSR/WSO/LT/PPS/PSS/POT/SFP(T)/ARL/DPL`, `compose*Log*`, `setRSASignatureValues`, `WorkflowDocumentFactory.newBatch*` (ACC, inactive, signature not yet valid) |
| `DocumentWriter` | `DetailManagerBean.createAndStoreDetailScaleDocumentAndVersion` / `updateDetailScaleDocumentAndInsertVersion` (document create/store, original-version hand-over, `createAudit`, version + children create, the log/sample tally entity-bean store), `ValidationHelper.restrictScaleDate` (OLDSD1/OLDAD1/OLDDD1) |
| `intake.xml.*` | `dac.detail.xml.*` (all format edits FXA/FBA/FWAH/FTAH/FTAL/FLA, hash strings, `LogUtil`/`VolumeUtil` volumes, `HowellCRC32`), `SubmissionUnmarshaller` = Castor `Submission.mapping` (V1/V2) and `Submission_V3.mapping` (V3) with `Validator.getXsdVersion` |
| `JdbcUnpackRepository` | the finders the edits used (client location, active detail submitter, scale site, existing document type, version uniqueness, pending paper batch, RSA padding id) |

Procs: HBS_STORE_DTL_SCL_DOC_TRANS, HBS_CREATE/STORE_DTL_DOC_BATCH, HBS_CREATE/STORE_DTL_DOC_BATCH_DOC,
HBS_STORE_HBS_PAPER_BATCH, HBS_CREATE/STORE_DTL_SCL_DOCUMENT, HBS_CREATE_AUDIT, HBS_CREATE_SCL_RTN_AUDIT,
HBS_CREATE_WEIGH_SLIP, HBS_CREATE/STORE_LOG_TALLY (+ _DETAIL, _DTL_DEF), HBS_CREATE/STORE_SAMPLE_LOG_TALLY
(+ HBS_CREATE_SAMPLE_TALLY_DETAIL, HBS_CREATE_SMPL_TALLY_DTL_DEF), HBS_CREATE_SFP_TALLY, HBS_CREATE_SFP_LOG_DETAIL,
HBS_CREATE_SCL_SITE_ARR_LEDGER, HBS_CREATE_DEPARTURE_LEDGER, HBS_STORE_WEIGH_SLIP / _SFP_TALLY (original flag hand-over),
HBS_CREATE_TX_FMT_ERR, HBS_CREATE_BATCH_FMT_ERR, HBS_CREATE_BATCH_DOC_FMT_ERR_2. No proposed DDL needed.

Signatures: legacy B1012 did **not** verify scaler signatures (B1021 does, still in legacy WebLogic with
its keys). The port stores hash total, crypt method, encrypted signature (RAW), input key id and RSA padding id
exactly as legacy, and needs no keys or Secrets.

Deliberate differences (all on inputs P505's schema check already rejects, or crash paths):
- FXA04 (no production files from software vendors on the web): legacy read the input user's WebADE roles at
  unpack time; P505 now records the caller's `HBS_SPC_SFTWR_VNDR` role in a `<id>.submitter` sidecar next to
  the file. A file without a sidecar skips the edit (as legacy did for a deleted user).
- A v3-namespace file without `xsi:schemaLocation` is read with the V3 mapping (legacy used the default mapping and
  rejected every batch with FBA02).
- An unparseable scale date is the format edit (FWAH04 / FTAH14) instead of the legacy NullPointerException that
  stopped the whole run; missing `<Log>`s count as zero.
- A CREATE proc failure rolls the transmission back (legacy EJBs swallowed some and committed partial data).
- Kept legacy defects: FSD01 never fires (`Character.equals("Y")`), SFP log detail ids from
  DETAIL_SCALE_DOCUMENT_SEQ, SFP `PROD_LOG_LENGTH` truncated to an integer, ledger entries created for test files.

Tests: `TransmissionUnpackerTest` (21, RecordingTableApi, every document type, edits at each level, original-flag
hand-over, paper batch match), `SubmissionUnmarshallerTest` (11: mappings V2/V3, hash string, CRC32, volumes,
XXE), `UnpackJobTest` (3). Fixture `src/test/resources/intake/all-types-v3.xml` (validates against
HBS_Schema_V6_1b). Needs a TEST regression pass: upload the same file on legacy and new side, compare rows.

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

## Ported (work package 7)

| Legacy | Id | Class | Notes |
|---|---|---|---|
| P280 Save | `batchSlip.create` | `submissions.BatchSlipCommands.Create` | validation.xml P280Form: site exists, dates required / not after today / From ≤ To, region exists, `DOMAIN\USERID` sender, count integer ≥ 1, notes ≤ 255. New slip PND. |
| P283 Save | `batchSlip.update` | `BatchSlipCommands.Save` | RowLock; refused when CLR; while PND every field is editable, otherwise only Document Count and Instruction/notes. Keys, matched batch, return type and ENTRY_ columns always from the DB row. |
| P283 Cancel | `batchSlip.cancel` | `BatchSlipCommands.Cancel` | PND only → CLR. |
| P283 Confirm | `batchSlip.confirm` | `BatchSlipCommands.Confirm` | PND only, Documents Received not empty and equal to Document Count (error.doc-count-received.invalid) → MAT. |

On `batch-slip`, Cancel shows only for a Pending slip (`statusCode` PND) and Confirm only when `confirmAllowed`
(Pending with documents received, a column of `submissions.batchSlip`) — `ActionDef.showWhen`; the count mismatch
stays a server message.
| P041/P024 role defaults | criteria `siteScope`, `inputUserScope` | `SubmissionsCatalog` + `FieldDef.roleDefaults` (SearchScreen) | P032Action / P024Action: HBS_SCALE_ADMIN → Sites in Associated Districts; CLI_SITE_ADMIN → Associated Sites; MOF_SCALER / DTL_DATA_ENT / CLI_SCALER → own Input User ID (`:hbsUserId`). Own client for CLI/SPC is the `clientScope` fence. |
| P041/P024 validations | (query guard) | `submissions.SearchCriteriaGuard` | beforeCurrent, limitingYear, fromBeforeTo, scale site and region cross-references, with the legacy texts. |
| P038/P039 | — (edited in the settings ConfigMap; P039 confirmations not carried over) | — | The share bridge keeps the P038Action value checks (whole numbers, `\\server\printer`) before writing the legacy files. |
| Legacy ProcessParam files | `GET /api/v1/hbs/integrations/legacy/process-parameters` | `processparams.LegacyBridgeController`, `LegacyProcessParameterFiles` | Service account `HBS_LEGACY_BRIDGE`; format in [coexistence.md](../coexistence.md#share-bridge). |

The registry commands for these ids were removed from `SubmissionsBatchSlipCatalog` and `SubmissionsProcessingParamsCatalog`.

## Legacy logic not yet ported

1. ~~Report prompt fencing~~ — HBS2R032 / HBS2R033 ported: `submissions.SubmissionReportGuard` lets a
   non-ministry caller run them only for a transmission created or received by their client, or a batch of such
   a transmission / at a site their client owns (both now in `HbsReportController.INDUSTRY_REPORTS`).
   **HBS2R027 stays ministry-only**: its proc fences industry only through `RB_SEARCH_BY_SITES = 'L' || user id`,
   and the prompt whitelist (`HbsReportService.SAFE_PROMPT`) cannot carry the backslash of the user id.
2. **Batch jobs reading the settings in the new app**: once B2031/B2041/B2075 move here, they must read
   the settings ConfigMap (`processparams.AppSettings`); until then the share bridge feeds the legacy files (batch work, not a UI function).
3. ~~Report logging~~ — ported for every report: `service.v1.report.ReportLogWriter` writes the HBS_REPORT_LOG
   row (`PresentationHelper.logReport` → `ReportLogManager.createReportLog`: user, report code = report id,
   status SUB, format, client, "Job Number: n") through `HBS_CREATE_REPORT_LOG` / `HBS_REPORT_LOG_SEQ`, best effort
   (a failed write — e.g. a report code missing from HBS_REPORT_CODE — is logged and does not fail the download).
4. Legacy P041 "ignore the dates when a Transmission ID is entered" — kept as a deliberate difference (see above).

## Capabilities

All existing (plus the new `LEGACY_BRIDGE` service-account capability for the bridge endpoint): `SCALE_RETURNS_VIEW` (transmissions/batches; legacy `web:/dac/srt/*` MOF/CLI plus SPC menus),
`MINISTRY` (batch slip views), `BATCH_SLIP_EDIT` (P280/P283 writes), `PRODUCTION_CONTROL` (P038),
`ANY_USER` (`admin.alertText`). None missing.
