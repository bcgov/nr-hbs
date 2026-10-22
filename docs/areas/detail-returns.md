# Scale Returns: Detail (`scale-returns`, query prefix `detailReturns.`)

The legacy pages are under `hbs-web/src/main/webapp/dac/detail` (JSP), `hbs-view/.../presentation/dac/detail` (Struts) and `hbs-ejb/.../hbs/dac/detail` (EJB). In this document `$E` is `hba-archive/hbs/trunk/source/ear/hbs-ejb/src/main/java/ca/bc/gov/mof/hbs`, `$V` is `.../ear/hbs-view/src/main/java/ca/bc/gov/mof/hbs/presentation` and `$DDL` is `nr-mof-db/scripts/THE`.

## Files

| Side | File |
|---|---|
| Backend | `backend/src/main/java/ca/bc/gov/nrs/hbs/api/catalog/DetailReturnsSql.java`: the shared SQL (a UNION ALL of the 6 version tables, the document view, the client fence and the union of all edit-error tables) |
| Backend | `backend/.../catalog/DetailReturnsCatalog.java`: `detailReturns.search` and the `codes.detailReturns.*` lists |
| Backend | `backend/.../catalog/DetailReturnsViewCatalog.java`: `detailReturns.version`, `.versions`, `.errors`, `.logs`, `.log`, `.logDefects`, `.segregations`, `.transactions` |
| Backend | `backend/.../detailreturns/` — workflows, state machine, store, validators, commands (`command/DetailCommands`, `BulkCommand`, `DetailEntryController`) |
| Frontend | `frontend/src/screens/areas/scale-returns-detail.ts` (workflow buttons on `detail-return`) |
| Frontend | `frontend/src/pages/detail/DetailEntryPage.tsx` (P004, P550–P760 entry + log/defect grid), `DetailBulkPage.tsx` (P560A), `detailEntryFields.ts` |

Writes are ported workflow commands (`ca.bc.gov.nrs.hbs.api.detailreturns`, see "Ported" below); the entry page reads `GET /api/v1/hbs/detail-returns/{ddn}/versions/{version}`.

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
| P004 Add Detail Scale Data (menu) | `/scale-returns/detail-entry` (no `ddn`: choose return type and event), "Add Detail Scale Data" on `detail-search` | `detailReturns.add` | `$V/dac/detail/LogTallyActionHandler` ADD, `P600Action.addDetailDocument`, `P650Action`, `P700Action`, `P750Action` |
| P550 / P553 Add / Update Piece Scale | `/scale-returns/detail-entry` (header + log lines) | `detailReturns.add`, `.save`, `.release` | `workflow/Add`, `Save`, `Release`; `P552Form` |
| P558 / P559 Update Log Details / Log Defects | `/scale-returns/detail-entry` "Log Details" grid; defects open under each log (max 9) | `detailReturns.save` (`logs`) | `P558Action`, `P559Action`, `LogDefectHelper.calculateVolume`, `LogUtil.netVolume` |
| P600 / P603 Add / Update Weight Scale | `/scale-returns/detail-entry` | `detailReturns.add`, `.save`, `.release` | `P600Action`, `P600Form` |
| P650 / P653, P658 / P659 Sample tally add / update / logs / defects | `/scale-returns/detail-entry` | `detailReturns.add`, `.save` | `P650Action`, `P658Action`, `P659Action` |
| P700 / P703 Add / Update Arrival or Departure Ledger Entry | `/scale-returns/detail-entry` | `detailReturns.add` (DDN ARL/DEP + SCL_SITE_LEDGER_SEQ), `.save`, `.release`, `.discard` | `P700Action.composeARL/DPL` |
| P750 / P753 / P758 SFP add / update / log details | `/scale-returns/detail-entry` (return type "Special Forest Product Tally"). Legacy showed these only in DEV/TEST; the new app offers them to DETAIL_RETURN_EDIT (business question below) | `detailReturns.add`, `.save` | `P750Action`, `SFPDetailParser` |
| P562 / P554A, P612 / P604A, P662 / P654A Event Change Selection / Confirm | "Change Event Type" on the entry page | `detailReturns.changeEvent` (clears the fields, then Save) | `P554AAction`, `P604AAction.revampScaleReturn`, `P654AAction` |
| P554 / P604 / P654 / P754 Confirm <action> | confirmation modals on `detail-return` and the entry page | `detailReturns.cancel`, `.cancelReplaceChanges`, `.replaceChanges`, `.replaceNoChanges`, `.discard`, `.approve`, `.reject`, `.clearSignatureFailure`, `.release` | `workflow/*`, `WorkflowManagerBean` |
| P560 Confirm Approval of Digital Signature | **not carried over**. It was unreachable (P554 is shown first) and its function is legacy logic 4 (`clearSignatureFailure`) | — | — |
| P560A / P554B / P561 Bulk Request Selection / Confirm / Selection List | `/scale-returns/detail-bulk` | `detailReturns.bulk` | `P570Action.performBulkAction`, `applyAuthorizationCheck`, `doUpdateAction` |
| Correction approval flow (HBS_INV_CORR_APP) | queue `detail-workbench?status=AWP`; Approve/Reject Change buttons on `detail-return` | `detailReturns.search`, `detailReturns.approve`, `.reject` | `workflow/Approve`, `Reject` |
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

## Ported (work package 4, `ca.bc.gov.nrs.hbs.api.detailreturns`)

Status: **built — needs TEST regression** (docs/coexistence.md). Every workflow
runs in the command's single transaction, so none of the intermediate states the
legacy app autocommitted between proc calls is visible to B1021/B1031/B19xx.

| Legacy | New class | Command id / endpoint |
|---|---|---|
| `$E/dac/detail/control/VersionState`, `DocumentState` | `state/VersionState`, `state/DocumentState` (guards + legacy `IllegalStateException` texts) | — |
| `$E/dac/detail/control/state/*` (`StateManagerBean`) | `state/StateManager` | — |
| `workflow/Add` + `DetailManagerBean.createAndStoreDetailScaleDocumentAndVersion` | `DetailWorkflow.add`, `store/DetailStore.createDocumentAndVersion` | `detailReturns.add` |
| `workflow/Save` | `DetailWorkflow.save` | `detailReturns.save` |
| `workflow/Release` + `WorkflowManagerBean.release/processHeld/processReplacementVersionPending/process` | `DetailWorkflow.release`, `releaseCommand`, `processReplacementVersionPending`, `process` | `detailReturns.release`, `detailReturns.reEdit` (BT-5790) |
| `WorkflowManagerBean.clearSignatureFailure` | `DetailWorkflow.clearSignatureFailure` | `detailReturns.clearSignatureFailure` |
| `workflow/Hold` + `WorkflowDocumentFactory.hold/newVersion` | `DetailWorkflow.hold`, `newVersion` (next version, new DTL_DOC_SEQ BT 5298, fresh log/defect ids, `updateDetailScaleDocumentAndInsertVersion` original-flag swap) | `detailReturns.hold` |
| `workflow/Discard` | `DetailWorkflow.discard` | `detailReturns.discard` |
| `workflow/Cancel` | `DetailWorkflow.cancel` | `detailReturns.cancel` |
| `workflow/ReplaceWithChanges` + `WorkflowDocumentFactory.replace` | `DetailWorkflow.replaceWithChanges` | `detailReturns.cancelReplaceChanges`, `detailReturns.replaceChanges` |
| `workflow/ReplaceWithNoChanges` | `DetailWorkflow.replaceWithNoChanges` | `detailReturns.replaceNoChanges` |
| `workflow/Approve` / `Reject` | `DetailWorkflow.approve` / `reject` | `detailReturns.approve` / `detailReturns.reject` |
| `P554AAction`, `P604AAction`, `P654AAction` | `EventChange` | `detailReturns.changeEvent` |
| `P570Action` (bulk) | `command/BulkCommand` (one savepoint per row: PROCESSED / NOTPROCESSED / ERROR) | `detailReturns.bulk` |
| `P840Action.bulkRequestCommonAction` (P842–P844, billing screen) | `command/FieldScaledDeckBulkCommand` + `FieldScaledDeckReturns` (cancel / hold → FS flag N → release on this workflow) | `billing.fieldScaledDecks.bulk` (see [billing-and-admin.md](billing-and-admin.md)) |
| `WorkflowManagerBean.reset` → `processReplacementVersionPending(ddn, document, user)` | `DetailWorkflow.processReplacementVersionPendingAfterReset` (called by the summary-return discard of a failed generated summary, `summaryreturns.generated.DetailDocumentReset`) | — |
| `KeyGenerator*`, `P700Action` ledger DDN | `KeyGenerator` (epoch-ms arithmetic in Pacific time, as legacy) | — |
| `LogUtil.netVolume`, `calculateTotalNetVolume`, `LogDefectHelper.calculateVolume` | `LogVolume`, `DetailPayload.applyLogs` | — |
| `DetailManagerBean` persistence (finders, `updateDetailScaleDocumentAndChildren`, `removeErrors`, `saveDetailScaleDocumentErrors`, `LogTallyDAO.updateLogs` sync, `createAudit`) | `store/DetailStore`, `store/JdbcDetailRows`, `store/DetailAuditTrail` (HBS_AUDIT + HBS_SCALE_RETURN_AUDIT) | — |
| `DetailScaleDocumentValidator` + `LogTallyCommonValidator`, `LogTallyValidator`, `SFPTallyValidator`, `WeighSlipValidator`, `SampleLogTallyValidator`, `ArrivalLedgerValidator`, `DepartureLedgerValidator`, `ScaledLogXrefValidator`, `PopStratYearParser`, `RateManagerBean.hasRate` | `validation/*` (edit errors stored in the type's error tables, RDY/ERR/NTI decided from them) | — |
| `CrossReferenceEdit`, `HbsEditError` codes | `validation/CrossReferenceEdit`, `validation/HbsEditError` (generated) | — |
| Struts validation.xml / form `validate()` / log-detail parsers / `HBSValidator` | `validation/form/DetailFormValidator` (400 with the legacy messages) | all entry commands |
| `FunctionsComparator`, `Buttons.jsp` + `IsScaleSiteAssociatedWithOrgUnitTag`, `isDetailDocumentEditable` | `DetailAuthority` (+ `UserDomains`: site/district data domains, scaler keys) | every command; `GET /detail-returns/{ddn}/versions/{v}` lists the caller's actions |
| `PSDetailDocumentFields`, `WSDetailDocumentFields`, `SSDetailDocumentFields`, `SPSFields`, `LedgerFields` | `FieldAvailability` (generated) + `getCheckboxValue` defaults in `DetailPayload` | `GET /detail-returns/fields` |

`StringUtil.empty` semantics: legacy `StringUtil.empty(s)` does **not** trim
(`keepWhitespace = true`), so a one-space value (the log product / ungraded
grade, the `" "` the change-event actions put into an input field) is not empty;
only `StringUtil.empty(s, false)` trims. `Row.empty` and `DetailValidator.empty`
follow the first, `Row.blank` / `DetailValidator.blank` the second, used only
where legacy trimmed (`WeighSlipValidator` input PSY and the two population
checks, `DetailScaleDocumentValidator.findStratum` BT 11263 →
`WeighSampleSupport.findStratum`). `DetailAuthority` (tags, `DacHelper`) does
not trim. `DetailPayload` takes text as posted, like the Struts forms and
parsers: only "" becomes null, whitespace is kept, and only the fields a legacy
form setter trimmed are trimmed (P552 original DDN / red-tag site, P600 cut
block / mark / site / PSY, P650 red-tag site / mark / site, every ledger field
(P700, BT 14035), the field scale deck); log and sample tally lines get product
" " and an empty grade " " (`LogDetailParser` / `SampleDetailParser` /
`getScaleGradeCode`), SFP lines an empty product " " (`getScaleProductCode`).
Number and date fields still treat blank as null.

Locking: the SPA sends the version's `updateTimestamp`; `DetailStore` also
re-reads the document state (legacy `StaleStateException`) and the version's
state/active/UPDATE_TIMESTAMP before every store. Refusals are 409 with the
legacy `error.unable.tosave.doc` text. Industry callers are fenced to returns of
their client (site owner, mark holder, stratum owner, scaler licence client).

Known legacy behaviour kept (each commented in code): the RCP/PND release branch
is unreachable; `FunctionsComparator` never used the signing licence; `Reject`
under RCP with a pending replacement failed (now refused, whole transaction
rolled back); `Release` of a locked check scale finds its original by DDN only
(by licence + return number stays in B1021).

Not ported (out of scope): signature validation of submitted versions
(`validateSignature`, work package 2), the batch-only transitions
(`Process`/`Reset`/`Reconcile` commands of B1021/B1031/B2031).

DDL: none (no procs were missing). A unique "one active version" index was
considered and withdrawn — legacy and this port briefly hold two active
versions inside one workflow (`process`: AWP stored before the previous
invoiced version is deactivated) and legacy autocommits each call, so it would
break legacy batch; `docs/db/active-version-duplicates.sql` monitors instead.

## Ported (leftovers package)

1. **Data-domain search scopes** — `detailReturns.search` filters `scope` and `markScope` (HBS2R551
   `PSR_SearchBySites` / `PSR_SearchByMarks` on `:hbsUserId`): `ASSOCIATED_SITES` (HBS_USER_SITE_DATA_DOMAIN),
   `ASSOCIATED_DISTRICTS` (HBS_USER_DIST_DATA_DOMAIN on the site's district), `OWN_LICENCE` (the primary
   licence is one of the user's QUANT_LICENSEs, scale date inside the HBS_SCALER_AUTH_KEY period —
   `mofscalerscaledata`), `MOF_DUAL` / `CLIENT_DUAL` (district / site domain UNION own licence —
   `mofdualrolesscaledata` / `clientdualrolesscaledata`, BT 3268), `markScope=ASSOCIATED_DISTRICTS` (mark
   district). P046 offers "Data Domain" (associated sites / districts) and "Marks in Associated Districts"; the
   Home work queues (`detail-workbench`, `detail-error-categories`) default the scope by role as
   `P046Action.setSearchCriteriaForHome` / `setCriteriaForErrorcategory` did (scale admin → districts, MOF
   scaler → own licence, both → union; site admin → sites, CLI scaler → own licence, both → union).
2. **Document delivery "Generate PDF"** (P551/P601/P651, `P046Action` GEN_PDF →
   `ReportingFactory2.createDetailScaleReportDocd`): report buttons `HBS2R552docd` / `HBS2R602docd` /
   `HBS2R652docd` on `detail-search` (per return type, criteria mapped as `getDetail*ScaleReportParametersDocd`).
3. **Transaction History "Date Issued"** — `detailReturns.transactions` column `dateIssued`
   (`DetailScaleTransactionsTag.createDisplayTransaction`): the latest transaction of the summary version —
   piece / weight: its FOREST_INVOICE date (`InvoiceHelper.findInvoice(number, "N")`); sample: the compiled
   sample statement's issue date (BT13916). The SRTS `ForestTally.processedHdbsDt` fallback is not carried
   over: neither SRTS_TALLY_NUMBER nor FOREST_TALLY exists in nr-mof-db.
4. **P650 `replacementInd`** (`HBSValidator.validateReplacementIndicator`) — `DetailFormValidator` now takes a
   `Context` (document state + previous version's CHECK_REPLACES_ORIGINAL_IND and state, built by
   `DetailCommandSupport.formContext` as `findPreviousDocumentAndVersion` did): on a sample-tally Save,
   un-ticking "replaces original" is refused ("Replacement indicator cannot be unchecked.") when the previous
   version replaced the original, unless the document is OIP or that version is RDY / ERR.

## Legacy logic not yet ported

1. **Document delivery "Generate XML"** (P551, `DocumentDeliveryManager.generateScaleDataReport` →
   `dac/detail/xml/XMLScaleDataFactory`, `LogTallyXMLCreator` / `WeighSlipXMLCreator` / `SampleTallyXMLCreator`,
   ~1,600 lines of Castor mapping): the listed returns re-serialised as a scale-data submission file. Not built in
   this package (size); the PDF delivery copy and the CSV export cover the read use. Needs the submission XML
   model of `intake/xml` turned into a writer.
2. The list reports (`HBS2R551/601/651/701`) do not pass the new scope to `PSR_SEARCHBYSITES` (the report link
   cannot translate `ASSOCIATED_SITES` → `associatedsites`); the on-screen list is scoped.

## Open questions for the business

- Should industry users keep seeing returns for which they are only the mark holder or stratum owner (the legacy view rule), or only their sites and licences (the legacy Home rule)?
- Must SFP online entry ever be enabled? It was DEV/TEST-only in legacy; the new entry page offers it (hide the type in `detailEntryFields.ts` DOC_TYPES if not).
- Are the P290–P292 pre-registered LDS ranges wanted? There is no table for them today.
