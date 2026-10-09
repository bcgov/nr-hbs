# Scale Returns: Detail (`scale-returns`, query prefix `detailReturns.`)

The legacy pages are under `hbs-web/src/main/webapp/dac/detail` (JSP), `hbs-view/.../presentation/dac/detail` (Struts) and `hbs-ejb/.../hbs/dac/detail` (EJB). In this document `$E` is `hba-archive/hbs/trunk/source/ear/hbs-ejb/src/main/java/ca/bc/gov/mof/hbs`, `$V` is `.../ear/hbs-view/src/main/java/ca/bc/gov/mof/hbs/presentation` and `$DDL` is `nr-mof-db/scripts/THE`.

## Files

| Side | File |
|---|---|
| Backend | `backend/src/main/java/ca/bc/gov/nrs/hbs/api/catalog/DetailReturnsSql.java`: the shared SQL (a UNION ALL of the 6 version tables, the document view, the client fence and the union of all edit-error tables) |
| Backend | `backend/.../catalog/DetailReturnsCatalog.java`: `detailReturns.search` and the `codes.detailReturns.*` lists |
| Backend | `backend/.../catalog/DetailReturnsViewCatalog.java`: `detailReturns.version`, `.versions`, `.errors`, `.logs`, `.log`, `.logDefects`, `.segregations`, `.transactions` |
| Frontend | `frontend/src/screens/areas/scale-returns-detail.ts` |

The area adds no commands. See "Writes" below for the reason.

## Design notes

- **One search query for every return type.** The legacy app used a separate REF-CURSOR proc for each type:
  - `HBS2R551`: P (log tally) and F (SFP)
  - `HBS2R601`: W (weigh slip)
  - `HBS2R651`: S (sample tally)
  - `HBS2R701`: A and D (arrival and departure ledgers)

  Each proc concatenated the same dynamic WHERE clause. `DetailReturnsSql.VERSIONS` instead normalises LOG_TALLY, WEIGH_SLIP, SAMPLE_LOG_TALLY, SPECIAL_FOREST_PRODUCT_TALLY, SCL_SITE_ARRIVAL_LEDGER and SCL_SITE_DEPARTURE_LEDGER into common aliases. Every proc branch becomes a bound filter. Type X, the combined log and sample search from BT12638, becomes `returnType = 'X'`, which matches P or S.
- **One view for every type.** `detail-return` (`detailReturns.version`) replaces P552, P602, P652, P752 and P702. The reason is that `rowLink` and `reports` in the screen framework are static: a search over mixed types cannot route each row to a different screen.
  - Fields that don't apply to the record's type render as an em dash (—).
  - The five print buttons (HBS2R552, HBS2R602, HBS2R652, HBS3R755, HBS2R702) are all shown. Only the one that matches the return type produces content.
  - **Framework gap:** a `when`/`visibleIf` on `DetailSection`, `ReportLinkDef` and `LinkDef` would let one view show only the type-relevant sections and the matching print button.
- **Client fence** (`DetailReturnsSql.CLIENT_FENCE`). An industry user sees a version only when their client is one of the following. This matches the legacy view checks `P046Action.isIndustryUserInterestedDocument` and `$V/dac/detail/DetailScaleDocumentHelper.isDetailDocViewableByClient`, which threw `SecurityException`.
  - the scale-site owner (`SCALE_SITE.OWNER_CLI_NUMBER`)
  - the mark holder (`MARK_BILLED_CLI.CLIENT_NUMBER`)
  - the stratum owner (`STRATUM.CLIENT_NUMBER`)
  - the client of the primary scaler licence (`QUANT_LICENSE.CLIENT_NUMBER`, for an independent scaler)

  Every child list (errors, logs, defects, segregations, transactions) is fenced through its DDN. The legacy search did not apply this fence; it only forced "Site Owner" on Home searches.
- **Retention.** The search applies the legacy `PSR_LimitYearInd` rule (BT6020): nothing whose event date falls before 1 January of the year seven years back is returned. The event date is the scale date, or the arrival date for B4/TD ledgers, or the departure date for departures. The P674 "Detail Document Unavailable" page is therefore never reached from a search.

## Screen mapping

| Legacy screen | New screen id | Query / command | Ported from |
|---|---|---|---|
| P046 Search for Detail Scale Data (`searchGeneral`, `searchGeneralForFS`) | `detail-search` | `detailReturns.search` | `$DDL/PROCEDURES/V7.01532__HBS2R551.sql`, `V7.01536__HBS2R601.sql`, `V7.01540__HBS2R651.sql`, `V7.01544__HBS2R701.sql`; criteria from `$V/dac/detail/P046Action.java`, P046Form |
| Home "Detail Scale Return Status": In Error, Digital Signature Failure, Corrections Requiring Approval (`searchWorkbenchFilter.do?...&chcStatus=`) | `detail-workbench` (`?returnType=P\|W\|S\|A\|D&status=ERR\|DSF\|AWP`) | `detailReturns.search` (`status`, `days`=30 from `DacHelper.setDetailDateRange`) | `P046Action` ACTION_FILTER_SEARCH |
| Home "Replaced By Check Scale" / "Check Scale Replacements" (`actionType=ByCheckScale` / `CSReplacement`) | `detail-workbench` (`?returnType=P\|S&mode=BY_CHECK_SCALE\|CS_REPLACEMENT`) | `detailReturns.search` filter `mode`, which reproduces `PSR_CROInd`=Y plus the event-type split | `P046Action` ACTION_REPLACE_BY_CHECKSCALE / ACTION_CHECKSCALE_REPLACEMENT; HBS2R551 `strAndVersionStatus`, CRO block |
| Home "Error Categories": Industry / Ministry Responsibility (`searchErrorCategory.do`) | `detail-error-categories` (`?returnType=&status=ERR&responsibility=I\|M`) | `detailReturns.search` filter `responsibility`: the edit-error responsibility is the given value or `J` (joint) | HBS2R551 `strAndWithScl` / `strAndOtherThanScl` (BT13643) |
| P047 Search for Scale Returns in Submitted Detail Batches (`searchBatch`) | `detail-batch-search` | `detailReturns.search` (`batchReceivedFrom/To`, `submitterClient`, `inputUserId`, `batchId`) | HBS2R551 `strAndReceivedDate`, `strAndTradingPartnerClientNum`, `strAndUserId` |
| `searchBatchError` / `searchBatchOther` (drill-down from P027) | not a screen of its own. Submissions can deep-link `detail-batch-search?batchId=`, or `detail-workbench?status=ERR` | `detailReturns.search` `batchId` | HBS2R551 `strWhereBatchID` |
| P048 Search for a Single Detail Scale Return (`searchSingle`, `detailDocumentSearch`, `detailSearchLink`) | `detail-single-search` | `detailReturns.search` (`ddn` / `scaleSite`+`weighSlipNumber` / `scalerLicence`+`returnNumber`+`returnType`) | `P046Action` single-search, `findDetailScaleDocumentAndAnyActiveVersionByCriteria` |
| P551 / P581 / P601 / P651 / P751 Detail Search Results; P701 Arrival/Departure Ledger Search Results | the result table of the four search screens above | `detailReturns.search` columns: union of the P551 and P701 columns | HBS2R551/601/651/701 SELECT lists |
| P551/P701 "Print" (list report) | `reports` on the search screens | Jasper HBS2R551, HBS2R601, HBS2R651, HBS2R701 | `ReportingFactory2.createDetailScaleListReport` |
| P551 "Show History" (version link) | "Version History" table on `detail-return` | `detailReturns.versions` | `hbs2:versionExplorer` |
| P551 "Generate XML" / "Generate PDF" (document delivery) | **not carried over**. `DocumentDeliveryManager.generateScaleDataReport` is Java XML generation. HBS2R552docd/602docd/652docd remain in the report catalogue | — | — |
| P583 Combined Piece and Sample Scale Listing | replaced: `detail-search` with Return Type "Log and Sample Tallies", plus Export CSV | `detailReturns.search` | — |
| ViewOriginal / ViewReplacement check-scale navigation | partly: "Check Scale / Red Tag" fields on `detail-return` (Original DDN, Original Scaler/Return) | `detailReturns.version` | `P046Action` BT6131 |
| P552 View a Piece Scale Detail Return | `detail-return` | `detailReturns.version`, `.errors`, `.logs`, `.segregations`, `.transactions`, `.versions`; print HBS2R552 | LOG_TALLY, LOG_TALLY_ERROR, LOG_TALLY_DETAIL_ERROR, LOG_TALLY_TXN(+_SEG), `DetailScaleTransactionsTag` |
| P556 Log Details (view) | "Log Details" table on `detail-return` | `detailReturns.logs` | LOG_TALLY_DETAIL + SCALE_SPECIES_CODE / SCALE_GRADE_CODE (P556.jsp) |
| P557 Log Defects (view) | `detail-log-defects` (`?logType=P&logDtlId=`) | `detailReturns.log`, `detailReturns.logDefects` | LOG_TALLY_DETAIL_DEFECT |
| P555 Piece Scale Detail Return Segregations | **not carried over**: a static mock-up. The real detail segregations are shown in the "Segregations" table | `detailReturns.segregations` | LOG_TALLY_DTL_SEGREGATION / SAMPLE_TALLY_DTL_SEGREGATION / SFP_TALLY_SEGREGATION |
| P602 View a Weight Scale Detail Return | `detail-return`; print HBS2R602 | as P552 | WEIGH_SLIP, WEIGH_SLIP_ERROR, WEIGH_SLIP_TXN(+_SEG) |
| P652 View Sample Scale Detail Return | `detail-return`; print HBS2R652 | as P552 | SAMPLE_LOG_TALLY, SAMPLE_LOG_TALLY_ERROR, SAMPLE_TALLY_TXN(+_SEG) |
| P656 / P657 Sample Log Details / Log Defects (view) | `detail-return` "Log Details" / `detail-log-defects?logType=S` | `detailReturns.logs`, `.log`, `.logDefects` | SAMPLE_TALLY_DETAIL, SAMPLE_TALLY_DETAIL_DEFECT |
| P752 View a Special Forest Product Detail Return | `detail-return`; print HBS3R755 | as P552 | SPECIAL_FOREST_PRODUCT_TALLY, SFP_TALLY_ERROR, SFP_TALLY_TXN(+_SEG) |
| P756 SFP Log Details (view) | `detail-return` "Log Details" (stack/bundle columns: Piece Count; the stack dimensions are in the query) | `detailReturns.logs` | SFP_LOG_DETAIL |
| P702 View Arrival / Departure Ledger Entry | `detail-return` ("Ledger" section); print HBS2R702 | `detailReturns.version` | SCL_SITE_ARRIVAL_LEDGER / SCL_SITE_DEPARTURE_LEDGER |
| P674 Detail Document Unavailable | **replaced** by the 7-year retention filter in the search | — | `DacValidator.compareForYearLimit` |
| P004 Add Detail Scale Data (menu) | **not carried over yet**. Every target (P550, P600, P650, P700, P750) is Java data entry, not a single proc (see below) | — | — |
| P550 / P553 Add / Update Piece Scale | **not carried over** (legacy logic 1, 2) | — | — |
| P558 / P559 Update Log Details / Log Defects | **not carried over** (legacy logic 3) | — | — |
| P600 / P603 Add / Update Weight Scale | **not carried over** (legacy logic 1, 2) | — | — |
| P650 / P653, P658 / P659 Sample tally add / update / logs / defects | **not carried over** (legacy logic 1, 2, 3) | — | — |
| P700 / P703 Add / Update Arrival or Departure Ledger Entry | **not carried over** (legacy logic 1, 2) | — | — |
| P750 / P753 / P758 SFP add / update / log details | **not carried over**: DEV/TEST-only in production (`hbs3:showInEnvironment`). P758 Save was broken (ClassCastException in P558Action) | — | — |
| P562 / P554A, P612 / P604A, P662 / P654A Event Change Selection / Confirm | **not carried over** (legacy logic 5). The change mutates the in-session form only and is persisted by Save | — | — |
| P554 / P604 / P654 / P754 Confirm <action> (Cancel, Cancel and Replace with Changes, Replace with/without Changes, Discard, Approve Change, Reject Change, Clear Digital Signature Failure, Release of RCP/CWC) | **not carried over** (legacy logic 4). P754 buttons were DEV/TEST-only | — | — |
| P560 Confirm Approval of Digital Signature | **not carried over**. It was unreachable (P554 is shown first) and its function is legacy logic 4 (`clearSignatureFailure`) | — | — |
| P560A / P554B / P561 Bulk Request Selection / Confirm / Selection List | **not carried over** (legacy logic 6) | — | — |
| Correction approval flow (HBS_INV_CORR_APP) | the queue is ported: `detail-workbench?status=AWP`. Approve/Reject is **not carried over** (legacy logic 4) | `detailReturns.search` | — |
| P554S001–P554S012 confirmation prototypes | **not carried over**: static mock-ups with no actions | — | — |
| P290 / P291 / P292 Pre-Registered LDS Numbers | **not carried over**: dead 2002 mock-up with no table or action. The real LDS registry (HBS3R976/982) belongs to Scale Control | — | — |
| P745 / P746 / P747 / P748 Search for Ledger Entries | **not carried over**: dead mock-ups, superseded by P046 → P701, which is now `detail-search` with Return Type Arrival/Departure Ledgers | — | — |
| P505 / P506 Submit File of Detail Returns | owned by the Submissions area (section 05) | — | — |
| `web:/dac/detail/admin/*` grant | dead grant: there are no JSPs | — | — |

### Reports exposed

| Report | Where |
|---|---|
| HBS2R551, HBS2R601, HBS2R651, HBS2R701 (list reports) | Search screens. Prompts mapped: type, site, mark, licence, scale dates, status, event type, error code, `PSR_ACTIVEVERSIONINDICATOR=Y`, `PSR_LIMITYEARIND=Y`, `PSR_ORDERBY=3`. The procs parse dates with `yyyy-mm-dd-hh24-mi-ss`, so an ISO date gives 00:00 and a "To" date excludes that day. |
| HBS2R552, HBS2R602, HBS2R652, HBS3R755, HBS2R702 | `detail-return` (DDN + version, plus ledger type `docType` for HBS2R702) |

### Code lists

- `codes.detailReturns.eventTypes` (SCALE_EVENT_TYPE_CODE)
- `codes.detailReturns.docTypes` (DETAIL_SCALE_DOC_TYPE_CODE)
- `codes.detailReturns.sfpScaleTypes` (HBS_SFP_SCALE_TYPE_CODE)
- `codes.detailReturns.editErrors` (HBS_EDIT_ERR_MESSAGE_CODE)

## Writes: why there are no commands

The brief allowed a command only where the legacy action was a single table-API proc call. None of the detail screens qualify. Every write goes through `DetailScaleDocumentHelper` (`$V/dac/detail/DetailScaleDocumentHelper.java`), then the EJB `WorkflowManagerBean` (`$E/dac/detail/control/workflow/*`), then `StateManagerBean` (`$E/dac/detail/control/state/*`) and `DetailManagerBean` (`$E/dac/detail/DetailManagerBean.java`, about 13K LOC). Each step validates, recomputes and touches 2 to 6 tables through several `HBS_CREATE_/STORE_/REMOVE_*` procs. Exposing any one of those procs alone would write a half-transition: a version state without its document state, its errors or its sibling version. The UI therefore offers none of them, and `detail-return` explains this in its notes banner.

## Legacy logic not yet ported (to implement as a `ca.bc.gov.nrs.hbs.api.detailreturns` service)

1. **Add (P550, P600, P650, P700, P750)**: `$E/dac/detail/control/workflow/Add.java`.
   - Generate the DDN:
     - `KeyGeneratorLogTally` / `KeyGeneratorSampleLogTally` / `KeyGeneratorSpecialForestProduct`: licence(4) + return number(4) + a 5-character base-36 count of minutes since the base date, then scrambled.
     - `KeyGeneratorWeighSlip`: site(4) + the last 6 characters of the weigh slip number + base-36 days.
     - Ledgers: `ARL` / `DEP` + a 10-digit `UniqueId('SCL_SITE_LEDGER')`.
   - If the DDN already exists, reject with `error.duplicate.ddn`.
   - Run `DetailScaleDocumentValidator.getValidator(doc).copyInputFieldsAndValidate()`.
   - Call `DetailManagerBean.createAndStoreDetailScaleDocumentAndVersion`, which runs:
     - `HBS_CREATE_DTL_SCL_DOCUMENT` (state OIP)
     - then `HBS_CREATE_LOG_TALLY` / `_WEIGH_SLIP` / `_SAMPLE_LOG_TALLY` / `_SFP_TALLY` / `_SCL_SITE_ARR_LEDGER` / `_DEPARTURE_LEDGER` (version 1, INC, active)
     - then the `*_ERROR` rows.
   - Struts-level checks, which also need porting:
     - validation.xml forms P552Form, P600Form, P650Form, P700Form, P752Form
     - the `HBSValidator` cross-references: scaleSiteAndUser, licenceAndUser, scaleSiteProfile, timbermarkCrossRef, popStratYear
2. **Save (P553, P603, P653, P700/P703 update)**: `workflow/Save.java`.
   - Run the validator: `copyInputFieldsAndValidate`, `fieldDeckIdValidate`, `validateEventOrScaleDate`.
   - For tallies, run `LogTallyCommon.calculateTotalNetVolume()`.
   - Then `removeErrors`, `updateDetailScaleDocumentVersion` (`HBS_STORE_<version table>` + `HBS_STORE_DTL_SCL_DOCUMENT`) and `saveDetailScaleDocumentErrors`.
   - Optimistic locking is done through `UPDATE_TIMESTAMP` (`StaleStateException` → "Your changes were not saved because the scale return has been updated by another process.").
   - Edit eligibility comes from `DetailScaleDocumentHelper.isDetailDocumentEditable`:
     - INC with HBS_DTL_DATA_ENT or SPC_SUBM_AGNT; or
     - INC/HLD with site authority (CLI_SITE_ADMIN site domain / HBS_SCALE_ADMIN district domain) or with a scaler-licence match (primary / secondary / signing / original check scale).
3. **Log details / defects (P558/P559, P658/P659, P758)**:
   - Parsers: `P558Action`, `P659Action`, `LogDetailParser` / `SampleDetailParser`.
   - Volume: `LogUtil.netVolume` per log, which must be in 0..999.999. `LogDefectHelper.calculateVolume` handles at most 9 defects with unique numbers.
   - Persistence:
     - `HBS_CREATE/STORE/REMOVE_LOG_TALLY_DETAIL` and `_LOG_TALLY_DTL_DEF`
     - `_SAMPLE_TALLY_DETAIL` and `_SMPL_TALLY_DTL_DEF`
     - `_SFP_LOG_DETAIL`
   - Then the whole-document Save in item 2. Audit diffs come from `diffLogs`.
4. **State changes on one document (the P554, P604, P654 and P754 confirmations)**: `$E/dac/detail/control/workflow/`. The rules live in `$E/dac/detail/control/VersionState.java` and `DocumentState.java`.
   - `Release.java` (283 LOC): validate and change state (RDY/ERR/NTI/DSF). CWC/INC goes to AWP. Sets the DTL_DOC_SEQ on corrections.
   - `Hold.java`: on RNP with ERR/RDY/NTI, `cancel`, otherwise `supercede`. Then `WorkflowDocumentFactory.hold` copies the version to a new HLD version, re-validates it and stores it.
   - `Discard.java` (169 LOC):
     - always: `discard`, then `removeErrors`
     - OIP with a PNV replacement: inactivate the discarded version, `process` and `release` the replacement
     - CWC: reject the invoiced version
     - RCP: release the PNV, or reject the latest cancelled version
   - `Cancel.java`: `StateManager.cancel`. ISS goes to AWP and the document to CIP.
   - `ReplaceWithChanges.java`: `replaceWithChanges`, then `WorkflowDocumentFactory.replace`, which creates a new INC version.
   - `ReplaceWithNoChanges.java`: `replaceWithNoChanges`, which sets AWP and the document to CWR/RNP.
   - `Approve.java`:
     - always: `approve`
     - CWC: also approve the invoiced version
     - RCP / CWR / RNP: then run `Release`
     - the legacy UI also saved afterwards (BT10068)
     - Authority: HBS_INV_CORR_APP + district authority over the timber mark, or over the previous invoiced version's mark for CWC/RCP.
   - `Reject.java`:
     - always: `reject`
     - CWC: re-activate the invoiced version
     - RCP: re-activate the latest superseded or cancelled version, unless a PNV exists
   - `clearSignatureFailure`: in `WorkflowManagerBean` / `StateManagerBean` (`process`). Needs HBS_SCALE_ADMIN + scale-site authority.
   - Button availability (which button shows for which document state and version status, and who may use it): `$V/dac/detail/FunctionsComparator` + `DetailDocumentFunctionsTag` + `hbs3:isScaleSiteAssociatedWithOrgUnit`. The matrix is summarised in section 04, "Buttons.jsp".
   - The "proposed transactions" shown on the confirmation pages: `FetchDetailConfirmationDataTag` (faux cancel/replace transactions).
5. **Event type change (P562/P554A, P612/P604A, P662/P654A)**: `$V/dac/detail/P554AAction.java`, `P604AAction.java`, `P654AAction.java`. These clear fields per target event type (matrices in section 04), then Save as in item 2.
6. **Bulk requests (P560A/P554B/P561)**: `$V/dac/detail/P570Action.java`.
   - The search is re-filtered by status per action (also hard-coded in HBS2R551 `PSR_VersionStatusCode` = 'RELEASE', 'HOLD', 'CANCEL', and so on).
   - `applyAuthorizationCheck` runs per row.
   - `doUpdateAction` applies the optional field update (scale site, scale date, timber mark or PSY from→to) with re-validation, then calls the item 4 commands per selected row, then Save or Release.
7. **Data-domain search scopes.** The legacy "Associated Sites", "Sites in Associated Districts", "Marks in Associated Districts" and "own scaler licence" scopes (Home work queues `mofscalerscaledata`, `mofdualrolesscaledata`, `clientdualrolesscaledata`) join the following on the legacy user id (`PSR_UserId`):
   - HBS_USER_SITE_DATA_DOMAIN
   - HBS_USER_DIST_DATA_DOMAIN (mark district)
   - HBS_SCALER_AUTH_KEY + QUANT_LICENSE

   `QueryService` binds no user id, so these scopes are not applied. Industry users are still fenced by client, and ministry work queues show all districts unless the user narrows them. Fix: add a `:hbsUserId` viewer bind to `QueryService`, then add the filters `associatedSites` / `associatedDistricts` / `ownLicence`.
8. **Document delivery** ("Generate XML" / "Generate PDF" on P551): `DocumentDeliveryManager.generateScaleDataReport`.
9. **Transaction History "Date Issued"**: an invoice lookup through the summary return's latest transaction (`InvoiceHelper.findInvoice` or `ForestTally.processedHdbsDt`). It is not in `detailReturns.transactions`.

## Open questions for the business

- Should industry users keep seeing returns for which they are only the mark holder or stratum owner (the legacy view rule), or only their sites and licences (the legacy Home rule)?
- Must SFP online entry ever be enabled? It was DEV/TEST-only.
- Are the P290–P292 pre-registered LDS ranges wanted? There is no table for them today.
