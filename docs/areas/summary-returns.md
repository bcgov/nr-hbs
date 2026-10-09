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
| P050 | Add Piece / Volume Estimate / Area Based Estimate Return | `summary-return-add-piece` (`?pssmryType=AREAEST` etc.) | reserved `summaryReturns.addPiece` | `P050Action.addScaleReturn` |
| P100 | Weight Scale Summary Return (add) | `summary-return-add-weight` | reserved `summaryReturns.addWeight` | `P100Action` |
| P150 | Add Sample Scale Submitted Summary Return | `summary-return-add-sample` | reserved `summaryReturns.addSample` | `P150Action` |
| P053/P103/P153 | Update Piece/Weight/Sample Scale Return | `summary-return-update` (loads `summaryReturns.detail`) | reserved `summaryReturns.update` | `P050/P100/P150Action` Save |
| P052.. buttons | Hold, Release, Discard, Cancel, C&R With/Without Changes, Replace With/Without Changes, Request/Approve/Reject Rate Correction, Re-queue Txn | `summary-return` actions | reserved `summaryReturns.hold/.release/.discard/.cancel/.cancelReplaceWithChanges/.cancelReplaceWithoutChanges/.replaceWithChanges/.replaceWithoutChanges/.requestRateCorrection/.approveRateCorrection/.rejectRateCorrection/.requeueTxn` (body documentControlNumber, version, returnType [+trxId]) | see below |
| P054/P104/P154 | Confirmation Screen | confirm modal; preview not yet ported | – | `FetchConfirmationDataTag` |
| P054A | Confirm Custody Transfer | not a screen; becomes a validation response of the service | – | P050Action `authorizationFailure` |
| P057/P157, P058/P109/P158, P110 | Update segregations / notations / daily loads | not yet ported (editable grids need the service) | – | P057/P157/P058/P109/P158/P110Action |
| P174 | Summary Document Unavailable | replaced: `within_limit` filter; "Available" column on P042 | – | `DacValidator.compareForYearLimit`, `strWhereLimitYear` |
| /dac/summary/admin/* | grant only | not carried over: no JSPs/actions exist | – | action_lnk.tsv |
| P054S001-7, P104S001-7, P154S001-7, P101S002, P151S002, P108 | mock-ups | not carried over: unreachable static prototypes | – | – |
| HBS3R415/416 (CSV 3C415/416) | View PDF Report | belongs to the invoice screens (Queries area) | – | – |

Reserved command ids are not registered on the backend, so the SPA hides
their buttons and disables the save on the add/update forms (it reads
`GET /api/v1/hbs/commands`) until `SummaryReturnWorkflowService` registers them.

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

## Legacy logic not yet ported

No CommandDefinitions are registered. A `SummaryReturnWorkflowService` must implement the reserved ids, one transaction per action, writing through the HBS_CREATE/STORE/REMOVE procs for HBS_SCALE_RETURN, PIECE_SCALE_SUMMARY, PIECE_SCL_SMRY_TXN, PIECE_SCL_SEG, *_NOTATION, SAMPLE_SEG, SAMPLE/WEIGHT_SCL_SMRY_TXN, HBS_UPDATE_SAMPLE_SCL_SMRY_TXN, *_SEG_ERROR.

**DDL gap:** there are no table-API procs for WEIGHT_SCALE_SUMMARY, SAMPLE_SCALE_SUMMARY, WEIGHT_SCL_DAILY_LOAD_SUMMARY, *_SCL_VERSION_ERROR or WEIGHT_SCL_TXN_SEGREGATION (the legacy code wrote them with inline SQL). Add procs, or grant a DML role (DBA decision).

1. **Add.** Code: `P050Action.addScaleReturn`, `P100Action`, `P150Action`, `SummaryScaleReturnManagerBean.createAndStoreSummaryScaleReturn` (~l.246).
   - Validation (`presentation/.../SummaryScaleReturnValidator` `validateP050Form` / `validateP050FormDates` / `validateP100Form` / `validateP150Form`):
     - required fields; scaler licence type S/R;
     - site/licence authorization, except for HBS_SUMM_DATA_CORR and SPC_SUBM_AGNT;
     - area-based returns need a cruise-based, client-owned mark;
     - BCOMB / private-mark rules;
     - Bill To / Copy To must exist, and are cleared when they can be derived;
     - sections < 100;
     - Field Deck Id required for FLDSCLEST;
     - a waste cut block must belong to the mark;
     - the batch slip must exist and not be cleared;
     - Area Cut ≤ 9999.9;
     - `validatePopStratYear` (default-ratio populations allow PR only);
     - 7-year limit.
   - DCN comes from `SummaryScaleReturnHelper.getId`. New returns start as OIP, generated N, version 1, state INC.
   - Estimates force category CR/FI/WA/OT; waste returns use site ZZZ.
   - AREAEST derives licence, return number and site (`findAreaBasedLicenseNo/ReturnNo/ScaleSiteNo`) and sets log count 0.
2. **Update and sub-grids.** Code: `updateSummaryScaleReturnAndChildren` (~l.615).
   - Optimistic lock ("…updated by another process.").
   - P054A custody transfer.
   - Segregation rules:
     - species/product/grade;
     - pieces ≥ 0;
     - SB4 xor NMV;
     - beachcomb marks.
   - Daily loads must be > 0 and feed the totals.
   - Editability (`isEditablePieceScaleReturn` / `isEditableScaleReturn`):
     - never for generated returns;
     - CTL may edit INC; CORR may edit INC or HLD;
     - estimate rules per CLI_CRUISE_ADMIN / SPC_SUBM_AGNT / site / licence / district.
   - The legacy sub-grids had no role or state checks. The port must add them.
3. **State machine.** Code: `ejb/summary/control/ScaleReturnStateFactory` + `ScaleReturn*State`, `SummaryScaleReturnStateHelper` (1,589 LOC), `control/workflow/SummaryWorkflowManagerBean` (Release/Compile/Extend, WorkflowStateHelper).
   - **Held** (OIP/CIP/CWC/CWR/RCP/RNP + INC; OIP/RCP + HLD):
     - Release → RDY. Becomes PND if a correction is pending, ERR on an edit failure, or NTI for area-based returns with Area Cut 0.
     - Discard:
       - OIP → DIS/DEL
       - CWC → OCM/DEL
       - CWR → OCM/ISS
       - RCP → CCM/DEL
       - RNP → CCM/CAN
   - **Ready** (RDY/ERR, NTI, SPR):
     - Hold, OIP/RCP only. The old version becomes SPR and inactive, and a new HLD version is created (`createAndStoreSummaryScaleReturnVersionForHold`, BT16641).
     - Discard.
   - **Locked** (LCK), batch-driven:
     - Invoice/reconcile → OCM/ISS, CCM/CAN, RCC/ISS or RNC/ISS.
     - Failed reconcile → FAL; failed release → ERR.
   - **Invoiced** (ISS/CAN):
     - Cancel → CIP/RDY (NTI for area-based).
     - Replace without changes → RNP/RDY.
     - Replace with changes → RCP/INC.
     - Cancel & replace with changes → CWC/INC.
     - Cancel & replace without changes → CWR/RDY.
     - Request rate correction → CWR/AWP.
   - **Awaiting approval** (CWR + AWP):
     - Approve = Release (`Release.extendRateCorrectionDetails` → `SummarizationManager.extendRateCorrection`).
     - Reject.
     - Discard → OCM/DEL.
   - **Sample FAL:** Re-queue (`helper.requeue`, `isValidRequeueTxnId`).
   - Button visibility: `SummaryReturnFieldsTag`, `ButtonsForCBATag`.
4. **Release edits.** Code: `control/SummaryScaleReturnValidator.applyEditsAndValidate` with `PieceScaleValidator` (1,823 LOC), `WeightScaleValidator`, `SampleScaleValidator`, `SummaryBillableScaleReturnValidator`.
   - Errors are saved and removed with `saveScaleReturnErrors` / `removeScaleReturnErrors`.
   - Generated returns get client edits only.
   - A final bill (FB) needs prior billing activity.
   - Area-based returns: `generateAreaBasedSegregationsAndNotation`.
5. **Transactions and pricing.** Code: `SummaryScaleReturnExtender.extend`, `BillableScaleExtender`, `Piece/Weight/SampleScaleExtender` (weight uses the STRATUM ratio), `Generated*Extender`.
   - DR/CR and ORG/CAN/RPL come from the state.
   - Rates: `RateHelper` → `plu/RateManagerBean` → `HBS_RATES` procs `Retrieve_Normal_Rate`, `Retrieve_Beachcomb_Rate`, `Retrieve_Waste_Rate`, `Get_Override_And_Appraisal`.
   - Writes `*_SCL_SMRY_TXN` and `*_SCL_TXN_SEGREGATION` (the rate components).
   - Invoicing and reconciliation are batch: `ReconciliationDelegate`, `TransactionStatementDelegate`.
6. **Confirmation preview.** `FetchConfirmationDataTag` builds:
   - the previous invoiced version and the next pending version;
   - the resulting transactions;
   - for rate corrections, the correctable detail transactions (`IsEligibleForRateCorrectionTag`).

   Proposed as a `summaryReturns.preview` read.
7. **Discard of generated returns.** `removeSummaryScaleReturnAndChildren` frees the detail transactions and deletes the compiled statements and files (~l.2590).

## Capabilities (open)

- Weight/sample add and update should use `SUMMARY_RETURN_CONTROL` (HBS_SUMM_DATA_CTL/CORR).
- Cancel, replace, hold, release, discard and re-queue should use `SUMMARY_RETURN_CORRECT` (HBS_SUMM_DATA_CORR).
- Approve/reject rate correction should be HBS_INV_CORR_APP only; it currently uses `DETAIL_RETURN_APPROVE`.

These are broader capabilities in the meantime. Add the narrower ones with the service, in both `Capability.java` and `access.ts`.
