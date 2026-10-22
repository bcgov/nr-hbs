# Scale Returns – Summary (area key `scale-returns`, ids `summaryReturns.*`)

- Backend: `backend/src/main/java/ca/bc/gov/nrs/hbs/api/catalog/SummaryReturnsCatalog.java`
- Frontend: `frontend/src/screens/areas/scale-returns-summary.ts`
- Legacy roots (relative to `hba-archive/hbs/trunk/source/ear`): JSPs `hbs-web/src/main/webapp/dac/summary/**`; Struts `hbs-view/.../presentation/dac/summary/*`; EJB `hbs-ejb/.../hbs/dac/summary/**` (abbreviated `ejb/summary/`).
- DDL: `nr-mof-db/scripts/THE/PROCEDURES/V7.01516__HBS2R051.sql`, `V7.01519__HBS2R101.sql`, `V7.01522__HBS2R151.sql`; `FUNCTIONS/V10.00837__HBS_PIECE_SCALE_PAYBYCOPYTO.sql`, `V10.00847__HBS_WEIGHT_SCALE_PAYBYCOPYTO.sql`.

## Screen map

| Legacy | Legacy title | New screen id | Query / command ids | Ported from |
|---|---|---|---|---|
| Home (`mainmenu_ministry.jsp`) | Submitted / Generated Summary Scale Return Status – In Error / Held | `summary-returns?returnType=P\|W\|S&status=ERR\|HLD&generated=Y\|N` | `summaryReturns.search` | `P045Action` FilterSearch → HBS2R051/101/151 |
| P002 | Scale Return Menu | replaced by this area's SideNav entries | – | `dac/P002.jsp` |
| P042 | Search for Summary Scale Return | `summary-return-find` | `summaryReturns.find` | `P042Action`, `validateP042Page`; HBS2R0x1 by DCN / invoice / `PSR_StatementNumber` |
| P045 | Search for Summary Scale Returns | `summary-returns` | `summaryReturns.search` | `P045Action`; `SummaryScaleReturnManagerBean.findPieceVersionCountByCriteria` (~l.11335 HBS2R051, ~11623 HBS2R101, ~11866 HBS2R151) |
| P051/P101/P151 | Piece/Weight/Sample Scale Summary Returns | `summary-returns` results; Print → HBS2R051/101/151 | `summaryReturns.search` | `P051.jsp`, `ReportingFactory2.getSummaryPieceScaleReturnListReportParameters` |
| P051 Show History / VersionPager | versions | `summary-return` "Versions" | `summaryReturns.versions` | `MaxVersionTag`/`findVersions` |
| P052 / P102 / P152 | View Piece / Weight / Sample Scale Summary Return | `summary-return` (Print → HBS2R052/102/152) | `summaryReturns.detail` | `Summary{Piece,Weight,Sample}ScaleReturnVersionQuery`, `scaleReturnHeader.jsp`, `getSampleVolume` |
| P052.. errors | hbsEditErrors "Ver" | "Edit Errors" | `summaryReturns.versionErrors` | `HBSEditErrorsTag`, `*_SCL_VERSION_ERROR` |
| P052.. transactions | transactionsList.jsp | "Transactions" | `summaryReturns.transactions` | `SummaryScaleTransactionsTag`, `SummaryScaleReturnHelper.createDisplayTransaction` (issue date FOREST_INVOICE cancellation_ind='N'; sent date HBS_OUTPUT_BATCH.sent_date_time; volume/value = sum txn segregations; sample statement/issue date HBS_COMPILED_SAMPLE_STMNT, blank when CRT/FAL) |
| P052 Show Details | contributing detail txns | "Details (Generated Summary)" | `summaryReturns.detailTransactions` | `DetailsLinkTag`/`FetchDetailTxnsTag` (LOG_TALLY_TXN, WEIGH_SLIP_TXN, SAMPLE_TALLY_TXN, SFP_TALLY_TXN by DCN + summary_document_version) |
| P052.. Change Log | → P962 | "Change Log" row link → `change-log-document?returnFormat=SSR` | scale-control | – |
| P055/P105/P155 | Segregations | "Segregations" (Txn Seq column replaces Prev/Next Txn) | `summaryReturns.segregations` | `PieceScaleSegregationQuery`, `SummaryScaleTxnSegregationsTag` |
| P056/P106/P156 | Notations | "Notations" | `summaryReturns.notations` | `*ScaleNotationQuery` |
| P107 | Daily Load Summaries | "Daily Load Summaries" | `summaryReturns.dailyLoads` | `WeightScaleDailyLoadSummaryQuery` |
| P050 | Add Piece / Volume Estimate / Area Based Estimate Return | `summary-return-add-piece` (`?pssmryType=AREAEST` etc.) | `summaryReturns.addPiece` | `P050Action.addScaleReturn` |
| P100 | Weight Scale Summary Return (add) | `summary-return-add-weight` | `summaryReturns.addWeight` | `P100Action` |
| P150 | Add Sample Scale Submitted Summary Return | `summary-return-add-sample` | `summaryReturns.addSample` | `P150Action` |
| P053/P103/P153 | Update Piece/Weight/Sample Scale Return | `summary-return-update` (loads `summaryReturns.detail`) | `summaryReturns.update` | `P050/P100/P150Action` Save |
| P052.. buttons | Hold, Release, Discard, Cancel, C&R With/Without Changes, Replace With/Without Changes, Request/Approve/Reject Rate Correction | `summary-return` actions (shown per record from `summaryReturns.preview`) | `summaryReturns.hold/.release/.discard/.cancel/.cancelReplaceWithChanges/.cancelReplaceWithoutChanges/.replaceWithChanges/.replaceWithoutChanges/.requestRateCorrection/.approveRateCorrection/.rejectRateCorrection` (body documentControlNumber, version, updateTimestamp) | see Ported |
| P102/P152 Re-queue Txn | Re-queue | not carried over: button and action commented out in legacy (P102/P152.jsp, P100/P150Action, `SummaryScaleReturnStateHelper.reQueue`) | – | – |
| P054/P104/P154 | Confirmation Screen | confirm modal with the transaction preview | `summaryReturns.preview` (`action`) | `FetchConfirmationDataTag` |
| P054A | Confirm Custody Transfer | update form "Confirm Custody Transfer" (body `confirmCustodyTransfer`) | `summaryReturns.update` refuses without it | P050Action `authorizationFailure` |
| P057/P157, P058/P109/P158, P110 | Update segregations / notations / daily loads | bespoke page `/scale-returns/summary-return-children?grid=segregations\|notations\|dailyLoads` | `summaryReturns.saveSegregations/.saveNotations/.saveDailyLoads` (reads `summaryReturns.segregationRows`, `.notations`, `.dailyLoads`) | P057/P157/P058/P109/P158/P110Action |
| P174 | Summary Document Unavailable | replaced: `within_limit` filter; "Available" column on P042 | – | `DacValidator.compareForYearLimit`, `strWhereLimitYear` |
| /dac/summary/admin/* | grant only | not carried over: no JSPs/actions exist | – | action_lnk.tsv |
| P054S001-7, P104S001-7, P154S001-7, P101S002, P151S002, P108 | mock-ups | not carried over: unreachable static prototypes | – | – |
| HBS3R415/416 (CSV 3C415/416) | View PDF Report | belongs to the invoice screens (Queries area) | – | – |

All ids are WorkflowCommand beans in `backend/.../summaryreturns/` (see Ported).

### Deviations from legacy

- One `UNION ALL` search over P/W/S (PIECE/WEIGHT/SAMPLE_SCALE_SUMMARY + HBS_SCALE_RETURN, SCALE_SITE, ORG_UNIT, MARK_BILLED_CLI, STRATUM). All values are bound; no string concatenation.
- The legacy client-association select became one criterion per role: Mark Holder, Site Owner, Stratum Owner, Bill To, Copy To.
- Bill To / Copy To follow PAYBYCOPYTO: the stored client, otherwise the mark holder or site owner (piece) or stratum owner (weight). BCOMB2/3 bill the site owner.
- Client location is not filtered. Region/District matches the district or `rollup_region_no` / `forest_region`.
- Not ported:
  - associated sites/districts (the `:hbsUserId` bind now exists — follow-up);
  - the BILL_ADMIN Home default and the workbench date range;
  - the `OVRRULE` / `OTH` status pseudo-codes (use `overrideRatingRuleId`);
  - the date-window, category-vs-type and pop/strat/year validations, replaced by `requireOneOf` plus the 5,000-row cap.
- Industry fence on every query (Bill To / Copy To / mark holder / site owner / stratum owner). Legacy lists were unfenced.
- The detail screen shows the Piece and Weight/Sample field groups and all three Print buttons on every return.
- HBS2R0x1 report prompts are validated by `HbsReportService` (character whitelist); dates are sent as yyyy-mm-dd.

## Ported (work package 3 — built, needs TEST regression)

Package `backend/src/main/java/ca/bc/gov/nrs/hbs/api/summaryreturns/` (one transaction per command,
writes only through the table-API procs, `RowLock` on the version's UPDATE_TIMESTAMP, industry fence).

| Legacy | New | Command id |
|---|---|---|
| `ScaleReturnStateFactory` + `ScaleReturn*State` | `ScaleReturnState` (state table + every UI transition) | – |
| `SummaryReturnFieldsTag`, `ButtonsForCBATag`, P052/P102/P152 role blocks, P053 Release/Discard | `SummaryButtons`; commands refuse a button the page would not have shown | – |
| `SummaryScaleReturnHelper.isEditable[Piece]ScaleReturn`, `DacHelper.has{ScaleSite,District,Scaler}Authorization` | `SummaryReturnAccess` | – |
| `SummaryScaleReturnStateHelper.hold` (BT16641, BT15212) | `SummaryReturnWorkflows.hold` | `summaryReturns.hold` |
| `SummaryWorkflowManager.release` → `Release.execute` (BT17364 NTI, BT15212 generated segregations, CWC previous-version release), `P100Action.updateYTDCyclicBillingInd` (BT7327) | `SummaryReturnWorkflows.release` | `summaryReturns.release`, `summaryReturns.approveRateCorrection` |
| `SummaryScaleReturnStateHelper.discard / discardGeneratedFailed` | `SummaryReturnWorkflows.discard` | `summaryReturns.discard` |
| `…cancel / replaceWithoutChanges / cancelAndReplaceWithoutChanges` (+ area-based NTI) | same names | `summaryReturns.cancel/.replaceWithoutChanges/.cancelReplaceWithoutChanges` |
| `…replaceWithChanges / cancelAndReplaceWithChanges / createNewVersion` | same names | `summaryReturns.replaceWithChanges/.cancelReplaceWithChanges` |
| `…requestRateCorrection / rejectRateCorrection` | same names | `summaryReturns.requestRateCorrection/.rejectRateCorrection` |
| `FetchConfirmationDataTag` (P054) | `SummaryPreviewCommand` (read only) | `summaryReturns.preview` |
| `updateScaleReturnVersion`, `updateSummaryScaleReturnAndChildrenWithAudit`, `createAndStore…`, `save/removeScaleReturnErrors`, `AuditManager2Impl.createAudit` | `SummaryReturnWriter`, `AuditTrail` (HBS_AUDIT + HBS_SCALE_RETURN_AUDIT with the legacy field names) | – |
| `PieceScaleValidator`, `WeightScaleValidator`, `SampleScaleValidator`, `Summary[Billable]ScaleReturnValidator`, `RateManager` rate checks (HBS_RATES procs), BT15212 area-based generation | `edits/SummaryReleaseEdits` (+ `PieceEdits`, `WeightEdits`, `SampleEdits`, `BillableEdits`, `Rates`, `Lookups`, `AreaBasedSegregations`) | – |
| P050/P100/P150 add, P053/P103/P153 update, P057/P157, P058/P109/P158, P110 | `entry/` | `summaryReturns.addPiece/.addWeight/.addSample/.update/.saveSegregations/.saveNotations/.saveDailyLoads` |
| `DetailManagerBean.isEligibleForRateCorrection`, generated discard detail reset, `extendRateCorrection` | `generated/LegacyGeneratedSummaryDetails` (+ `detailreturns.DetailWorkflow` for the replacement-pending release after a reset) | – |
| P832 Hold / Release Selected (billing) | `SummaryReturnWorkflows.hold` / `.release` via `billing.UnissuedFinalBillCommands` | `billing.unissuedFinalBills.hold/.release` |

States left for the legacy batch: release ends RDY/ERR/NTI/PND exactly as `Release.execute`
(B2031/B2041 extension selects RDY/ERR of OIP/CIP/RNP/RCP/CWR/CWC and re-runs the release edits
for OIP/RCP before extending); a CWC release also puts the previous invoiced version back to
RDY and active so the cancel is extended first; one active version per document at the end of
every command. Pricing stays in the batch extension; at release the rates are only checked to
exist (SD08/SD10/SD11/SD12/SD17 via `HBS_RATES`).

**DDL gap — corrected.** The table-API procs for WEIGHT/SAMPLE_SCALE_SUMMARY,
WEIGHT_SCL_DAILY_LOAD_SUMMARY, the version/segregation/load-summary error tables,
WEIGHT_SCALE_NOTATION, HBS_AUDIT / HBS_SCALE_RETURN_AUDIT and YTD_CYCLIC_BILLING all exist in
nr-mof-db and are what the legacy entity beans called; no proposed DDL is needed for these
workflows.

### Deviations / legacy defects kept

- Every legacy step committed separately (and re-read with `verifyScaleReturnIsInState`); here the
  whole action is one transaction.
- Button rules were only UI hiding in legacy; the commands now enforce them (`SummaryButtons`).
- Release from the update page: save first, then Release on the view page (legacy did both in
  one click; the RCP/CWC confirmation is the Release confirmation with the preview).
- Hold of an area-based return deletes notation 98, or the last notation when there is none
  (legacy loop bug; the generated notation is 50).
- The generated-segregation edits of an area-based release are computed but not saved (legacy
  discarded the result of `validateGeneratedSegregations`).
- `removeScaleReturnErrors` removes the version errors of every version of the document and
  leaves weight load-summary errors, as legacy did.
- Audit field "Number_Sections" for the sample check-scale indicator is the legacy constant.
- Generated summaries (`generated/`): discarding a failed generated summary resets each detail
  document (lock check, latest txn FAL, fail → reset, PND / CAN companions) and then, as legacy
  `WorkflowManagerBean.reset` did, processes a replacement-pending (PNV) version on the real
  detail-return workflow (`DetailWorkflow.processReplacementVersionPendingAfterReset`: the reset
  version is superseded when OIP/RWC and active RDY/AWP, the PNV goes to AWP or through the full
  detail release). A CWC detail fails the reset as it did in legacy; generated SFP piece summaries
  cannot be discarded (legacy only looked at log tallies). Needs a business decision.
- Add / update / grids (`entry/`): category drop-downs are enforced on the server; weight and
  sample notation edits are saved (legacy P109/P158 only saved additions).

## Capabilities

- `SUMMARY_RETURN_EDIT` (broad): add piece, update, hold, release, discard, the grids; the
  per-role / per-state / data-domain rules are checked in the commands.
- `SUMMARY_RETURN_CONTROL` (HBS_SUMM_DATA_CTL, HBS_SUMM_DATA_CORR): add weight / sample
  (legacy grant add/* is HBS_SUMM_DATA_CTL; checked in the command).
- `SUMMARY_RETURN_CORRECT` (HBS_SUMM_DATA_CORR, HBS_BILL_ADMIN): cancel / replace.
- `SUMMARY_RETURN_ADMIN` (HBS_BILL_ADMIN): request rate correction.
- `SUMMARY_RATE_CORRECTION_APPROVE` (HBS_INV_CORR_APP): approve / reject rate correction.
