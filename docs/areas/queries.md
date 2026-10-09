# Queries area (legacy Queries tab, P400, `/opq/*`)

Legacy source root: `hba-archive/hbs/trunk/source/` (abbreviated below as `ear/…` = `ear/hbs-view/src/main/java/ca/bc/gov/mof/hbs/presentation/` for actions,
`ear/hbs-ejb/src/main/java/ca/bc/gov/mof/hbs/` for EJBs). DB DDL: `nr-mof-db/scripts/THE/` (`PROCEDURES/`, `FUNCTIONS/`).

Files:

- Backend: `backend/src/main/java/ca/bc/gov/nrs/hbs/api/catalog/QueriesCatalog.java` (issued documents,
  transmissions, billing summary, `codes.queries.*`), `backend/src/main/java/ca/bc/gov/nrs/hbs/api/catalog/QueriesReportsCatalog.java`
  (harvest history, timber marks, cut to cruise, scale site summary, aged unbilled),
  `backend/src/main/java/ca/bc/gov/nrs/hbs/api/queries/StatementDocumentController.java` (bespoke, replaces the
  `/StatementDelivery` servlet)
- Frontend: `frontend/src/screens/areas/queries.ts`

All Queries screens use capability `QUERIES_VIEW` (legacy grant `web:/opq/P400.jsp` = MOF_USER, CLI_USER) except
Aged Unbilled Scale (`MINISTRY`). The legacy sub-pages had no page security at all (anonymous users could open them
by URL); the new app requires a logged-in MOF/CLI user for every screen. There is no anonymous/public site.

## Screen mapping

| Legacy screen | New screen id | Query / command ids | Ported from |
|---|---|---|---|
| P400 Queries Menu | Queries area SideNav (no page) | — | `opq/P400.jsp` |
| PUBLIC anonymous menu / tab bar (`/include/mainmenu_public.jsp`, `tab_public.jsp`) | not carried over | — | There is no anonymous site in the new app (FAM/BCeID login required). Public FAQ / glossary / manual links are static MOF web pages. |
| P431 / P432 Mark Monthly Billing History (selection + report configuration) | `billing-history` (+ reports `HBS3R431` PDF and CSV) | `harvestHistory.billing` | Billing branch of `PROCEDURES/V7.01739__HBS_GET_HISTORY.sql` (same SELECT/filters as `V7.01498__HBS00001R.sql` behind `HBS3R431`); `ear/…/opq/ftas/MarkMonthlyBillingAction.java`; group/SPG/detail-line prompt codes from `ear/…/opq/ReportingFactory.java` (`getGroupTypeParam`, `getDisplaySPGParam`, `getDisplayDetailsParam`) and `opq/OpqConstants.java` |
| P441 / P442 Mark Monthly Scaling History | `scaling-history` (+ report `HBS3R441`) | `harvestHistory.scaling` | Scaling branch of `HBS_GET_HISTORY` (same as `V7.01570__HBS3R441R.sql`) |
| P493 / P494 Harvest Report XML Delivery Address + Confirmation | not carried over | — | Replaced: the history list is exportable (search export / CSV) and the PDF downloads directly; no e-mail drop-directory delivery. See "Legacy logic not yet ported" for the XML history file. |
| P493 / P494 generic Report Delivery Address / Confirmation (`opq/emailReport.jsp`, `confirmed.jsp`) | not carried over | — | Every report now streams directly from the in-process Jasper engine (`HbsReportController`); no e-mailed/queued reports. |
| P449 Search for A Single Issued Document | `issued-document` | `issuedDocuments.search` (`statementNumber`) | `HBS3R450_*` procs (below); 7-year/2003-11-01 rule from `ear/…/opq/P450Action.java` |
| P450 Search for Issued Documents | `issued-documents` (document-type radio) | `issuedDocuments.search` | `PROCEDURES/V7.01572__HBS3R450_PS.sql`, `V7.01574__HBS3R450_WS.sql`, `V7.01571__HBS3R450_CS.sql`, `V7.01573__HBS3R450_RS.sql` (UNION ALL of the four), called by `ear-ejb/…/ivs/TransactionStatementManagerBean.getSearchIssuedDocumentStatement` |
| P451 / P461 List of Issued Piece / Weight Scale Invoices | `issued-documents` results (`documentType` PSI / WSI); Print = `HBS4R452` / `HBS4R462`, Print Client Register = `HBS3R451` / `HBS3R461` | `issuedDocuments.search` | `opq/P451.jsp` columns |
| P456 / P466 / P471 / P476 List of Issued Volume / Compiled Sample / Ratio Statements | `issued-documents` results (PSV / WSV / CSS / RS); Print `HBS4R452`/`HBS4R462`/`HBS4R472`/`HBS4R477`, Register `HBS3R451`/`HBS3R461`/`HBS3R471`/`HBS3R476` | `issuedDocuments.search` | `opq/P456.jsp` columns (Total Weight = `SAMPLE_SCALE_SUMMARY.NET_WEIGHT` for CSS) |
| P474 Statement/Invoice Does Not Exist / Unavailable | not carried over as a page | — | Replaced by the empty-result state of `issued-document` and by the 404 / 410 responses of `StatementDocumentController` ("Statement Does Not Exist" / "Statement cannot be retrieved as it is older than seven years"). |
| StatementDelivery servlet `/StatementDelivery/statements/<type>/<number>.pdf\|xml` | bespoke `GET /api/v1/hbs/statements/{type}/{number}.{pdf\|xml}`; rows carry `documentUrl` / `documentXmlUrl` | — | `ear/…/dad/StatementServlet.java`, `ear-ejb/…/dad/HBSTransmission.java`, `ear-ejb/…/dad/HbsFileManagerBean.findHbsFileByTransactionStatement` + `HbsFileQuery.pkByStatementNumber` (statement table → `HBS_FILE.FILE_OBJECT`), FOI from `TransactionStatementManagerBean.freedomOfInformationCheck` and `hbs-core/src/main/resources/xsl/V1_0h/freedom-of-info-{send,copy}-to.xsl` |
| StatementDelivery `/transmissions/<file>` (stored transmission files in the client's statement drop directory) | not carried over | — | Files lived on the WebLogic file system (`DocumentDeliveryTransmissionHelper.getStatementDropDirectory`); see "Legacy logic not yet ported". The P453 XML/PDF File columns show the file names only. |
| P452 / P453 Search for Transmissions of Issued Statements / Transmissions of Issued Statements (+ Home "SearchFromHome") | `statement-transmissions` (`?recent=Y` = last 30 days for the user's client; Print = `HBS3R453`) | `statementTransmissions.search` | `PROCEDURES/V7.01576__HBS3R452.sql`; `ear/…/opq/P452Action.java` (`ACTION_HOME_SEARCH`, `setWorkbenchDateRange`) |
| P453 per-type document count links | row link → `issued-documents?transmissionId=` | `issuedDocuments.search` (`transmissionId`) | `HBS3R450_*` `RB_TRANSMISSION_ID` branch (`DOCUMENT_DELIVERY_BATCH_ITEM`) |
| P421 Billing Summary Selection ("List of Invoices and Volume Statements") | `billing-summary` (View PDF Report `HBS3R421` piece / `HBS3R422` weight) | `billingSummary.search` | `PROCEDURES/V7.01567__HBS3R421.sql`, `HBS3R422`, `FUNCTIONS/…HBS_BILLINGSMRYRPT_WHERECLAUSE.sql`; `ear/…/opq/P421Action.java` |
| P422 Billing Summary Report Configuration | not carried over | — | Non-functional orphan mock-up (no action, nothing links to it). |
| P441.jsp / P442.jsp (old mock-ups with the same ids) | not carried over | — | Obsolete JavaScript-only mock-ups, superseded by the invoiceSummary "scaling" flow (now `scaling-history`). |
| P401 Configure Scale Site Summary Report | `scale-site-summary` (8 report variants `HBS2R401/402/403Group{DistScale,ScaleTM,ScalePop}`) | `scaleSiteSummary.search` | Summary-return branch of `PROCEDURES/V7.01527__HBS2R401.sql`, `V7.01528__HBS2R402.sql`, `V7.01529__HBS2R403.sql`; `ear/…/opq/dac/P401Action.java` |
| P403 Configure Aged Unbilled Scale Report | `aged-unbilled-scale` (`HBS2R411` piece / `HBS2R412` weight) | `agedUnbilled.search` | `PROCEDURES/V7.01530__HBS2R411.sql`, `V7.01531__HBS2R412.sql`; `ear/…/opq/dac/P403Action.java` |
| P490 Cut to Cruise Comparison Report | `cut-to-cruise` (Print Report `HBS3R490`) | `cutToCruise.search` | `PROCEDURES/V7.01581__HBS3R490.sql`; `ear/…/opq/P490Action.java` |
| P480 Search for Timber Mark | `timber-mark-search` | `timberMarks.search` | `PROCEDURES/HBS3R481` + `FUNCTIONS/V10.00802__HBS_3R481_CONDITIONAL.sql` |
| P481 Timber Mark Query | `timber-mark` (detail) | `timberMarks.detail`, `timberMarks.species`, `timberMarks.stumpageRates`, `timberMarks.spgRates`, `timberMarks.appraisals` | `HBS3R481`/`HBS_3R481_CONDITIONAL` (mark, tenure, client, latest CNF appraisal); `ear-ejb/…/legacy/ftas/TimberMarkReaderBean.java` (`fillInStumpageRateInfo` → `plu/StumpageRateQuery.getStumpageRateInfoStatement1`, `fillInAppSpeciesInfo` → `plu/ApprisalSpecieQuery` / `ApprisalSpecieCoastQuery`, `fillInAppSPGInfo` → `plu/ApprisalSPGQuery`, `isSevered`); Reserve Rate = `StumpageRate.getReserveRate`, Fixed Rate = `ApprisalSPG.getTotalStumpageRate` |
| P410 View Recent Queries | not carried over | — | Listed report files in the shared drop directory (`ReportDisplayTag`); reports are now generated on demand and downloaded directly, nothing is stored. The CLI "Recently Issued Transmissions" section is covered by `statement-transmissions?recent=Y`. |
| P457 Invoice Report Request (CIS, `/opq/cis/invoiceSearch`) | not carried over | — | Dead: no menu link, and the "≤ 2003-10-31" + "≤ 7 years" rules mean no invoice can be returned. |
| P416 / P417 Multiple Invoice Selection / Invoice Selection List (CIS) | not carried over | — | Dead/orphaned (no menu link; pre-Nov-2003 HDBS invoices only). |
| `opq/confirmation.jsp` | not carried over | — | Dead (posts to an undefined action). |
| `opq/P494.jsp` audit wrapper | — | — | Belongs to Scale Control / audit (P902/P972). |
| Client Profile link (P300) / Stumpage Rate Search (P230) / Stratum Advisor (P850) | other areas | — | Profiles, Rating and Stratum Planner areas. |

## Deliberate differences from the legacy app

- **Client fence.** `issuedDocuments.search`, `billingSummary.search` and `statementTransmissions.search` apply
  `clientScope`: industry users see only documents where their client is the send-to or copy-to party, and only
  their own client's transmissions. Legacy let anyone (even anonymous users) list any client's documents and only
  severed the PDF content. Harvest history, timber marks, cut-to-cruise and scale-site summary stay unfenced (public
  data in legacy) with FOI severing of individual client names/addresses.
- **FOI severing** in SQL via the viewer binds (`:hbsIsMinistry`, `:hbsViewerClient`): `harvestHistory.*`
  (mark holder + site owner name/location name), `timberMarks.search/detail` (name, location name, address). The
  text is "Not Releasable" (legacy history procs said "CLIENT NAME NOT RELEASABLE").
- **Client Association** on P450/P421 (Both / Original / Copy To radio + one client field) is three client fields
  (`clientNumber` = Both, `sendToClientNumber` = Original, `copyToClientNumber` = Copy To) because a filter may
  only bind its own parameter. `clientLocation` matches either role's location.
- **Sort-by radio** on P450 is replaced by sortable columns. P432/P442 "Group output by / Detail lines / SPG
  groups" are report-only criteria on the history screens.
- **Region/District** criteria accept a region or district org unit (`codes.orgUnits`); the legacy "Interior /
  Coast / North / South" pseudo-areas (`HBS_CONSTANTS.*_ORG_NUMBER`) are not in the code list.
- The P431/P441 Species/Product/Grade special groups (CNFRS, DCDS, NOT_LOG, NOT_XM, LUM_REJ, UTL_BTR, firmwood
  rejects) are not offered; single codes only. Billing Type is single-select (legacy: checkboxes).
- `StatementDocumentController` does not honour the legacy `?foi` debug parameter.

## Frontend follow-up (shared components — not edited by this area)

> **Update (integration):** done — `documentUrl` / `documentXmlUrl` use the new `format: 'download'` column, which fetches with the bearer token.

- Issued-document rows (`issued-document`, `issued-documents`) include `documentUrl` (`…/statements/{type}/{n}.pdf`)
  and `documentXmlUrl` (`….xml`), `null` when the document is older than seven years / before 2003-11-01 or has no
  stored file. These endpoints require the bearer token, so the shared `SearchScreen` needs a "download" column
  format/action that fetches with `getBlob`/apiFetch and calls `triggerBrowserDownload` (they currently render as
  plain text). The row link goes to the summary return (`summary-return`, legacy SDN link).
- P450's results heading per type (P451 "List of Issued Piece Scale Invoices" …) and the per-type column hiding
  (no SDN/Site/Mark for RS, Pop/Str/Yr not for PSV, Total Weight CSS only) would need conditional columns; all
  columns are shown and are simply empty where not applicable.

## Legacy logic not yet ported

1. **Statement PDF rendering.** `GET …/statements/{type}/{n}.pdf` returns 501. Legacy rendered the stored XML via
   XSL-FO: `ear-ejb/…/dad/HbsStatementPdfFactory.java` (Apache FOP 0.20 `Driver`, fop config, `xsldir`) with the
   stylesheets in `hbs-core/src/main/resources/xsl/<TRANS_VERSION>/` (`hbs-transmission.xsl`, `piece-scale-invoice.xsl`,
   `weight-scale-invoice.xsl`, `piece-scale-volume-statement.xsl`, `weight-scale-volume-statement.xsl`,
   `compiled-sample-statement.xsl`, `ratio-statement.xsl`, `page-header.xsl`, `pagelayout.xsl`, …) chosen by
   `HBS_FILE.HBS_TS_VERSION_ID → HBS_TS_VERSION.TRANS_VERSION`. Port = add Apache FOP 2.x + copy the XSL tree into
   `backend/src/main/resources/xsl/`, then transform the (FOI-severed) XML in the controller. Multi-statement merge
   (`combine.xsl`, `HBSTransmission.mergeStatement`) is needed for "Generate XML / Generate PDF" below.
2. **P451/P456 "Generate XML" / "Generate PDF"** (≤ 200 documents) — `ear/…/dad/docd/DataDeliveryAction.java` →
   `DocumentDeliveryManager.generateStatementReport` / `HbsStatementPdfFactory`: one merged XML/PDF of all listed
   statements.
3. **FOI gate on Print / Print Client Register** — `ear/…/opq/P450Action.canShowInPublic` / `canShowIndustry`
   (`TransactionStatementManagerBean.findNotReleasableInfoCount` on `V_CLIENT_PUBLIC`, `findNotReleasableIndustryInfoCount`
   on PIECE/WEIGHT/SAMPLE_SCL_SMRY_TXN): blocks the register reports for non-ministry users when any listed document
   involves another Individual client ("The report contains personal information, and is temporarily unavailable for
   public viewing"). The `HBS3R451/461/471/476` and `HBS4R4xx` reports are offered on the screen without this check;
   with the new client fence a CLI user only lists documents of their own client, but the report procs themselves
   are not fenced — a dedicated report guard should apply the fence/FOI before running them.
4. **Harvest history validation** (`MarkMonthlyBillingAction.validateForm`): ≤ 12-month range, from ≥ max(today − 15 y,
   1995-01-01), no future month, ≤ 10 marks/licences each existing (MBC/TM lookups), species/product/grade combination
   valid for the range, "criteria selection insufficient" rule; XML history file (`DocdHelper.generateHistoryReport`,
   < 5,000 records, e-mailed); CSV `HBS3C431` (Oracle report program, layout in `hbs/opq/ftas/invoiceSummaryFileLayout.html`).
   The report CSV export of `HBS3R431` is offered instead.
5. **History report prompts** — `INVSMRY_LICMARK01..10` / `INVSMRY_LICMARKTYPE` (1 = licence, 2 = mark, 3 = file type,
   4 = mgmt unit) and `INVSMRY_FILETYPE1..3` were built from the radio choice in `ReportingFactory2.setParameters`.
   The screen maps only `timberMarks` → `LICMARK01` with type 2; a multi-mark or licence/file-type report needs a
   small mapping step (split the comma list, choose the type). Quoting the file types is done: the backend quotes
   `RB_FILE_TYPE1..3` / `INVSMRY_FILETYPE1..3` itself ([reports.md](../reports.md) "Prompt rules").
6. **P450 / P421 validations** — `validation.xml` `P450Form` / `P421Form` + actions: date ranges within the limiting
   year and ≤ 12 months, not future; population/stratum/year only for non-piece types with valid P, P/Y, P/S/Y combos;
   scale-site / mark / licence cross-reference checks; client/location validity.
7. **P452 "Associated Locations"** (CLI_DOC_RCVR) — `HBS3R452` `rb_cli_loc_option = 'A'` restricted to
   `HBS_USER_CLI_LOC_DATA_DOMAIN` rows of the user id. The registry has no user-id viewer bind, so the option is not
   offered; leaving Client Location blank = all locations of the (fenced) client.
8. **Stored transmission file download** (`StatementServlet.handleTransmissionCommand`) — needs a storage decision
   (files are not in the database).
9. **P481 MGU description** — `TimberMarkReaderBean.findMgmtUnitDesc` (CodeReader lookup through `MGMT_UNIT_XREF`
   to a per-type code table). Only the MGU id is shown.
   The Mgmt Unit Type choices are legacy's fixed list (`OpqConstants.MGMT_TYPE_CODE`: F, T, U, V, "U,V",
   "F,T,U,V"), filtered with `IN`, and the type description is a `DECODE` — the app does not read
   `MGMT_UNIT_TYPE_CODE` (the HBS proxy user has no grant on it).
10. **P401 validations** (`P401Action`): scale-from ≥ Jan 1 of (current year − 7), "This report grouping is not available
    for Piece Scale", association/client consistency, scaler licence not valid for weight. `scaleSiteSummary.search`
    lists the active summary versions only (the reports also union the detail tallies — LOG_TALLY / WEIGH_SLIP /
    sample tallies — and aggregate volumes); its client filter matches mark holder **or** site owner (the report
    honours the association radio exactly; Stratum Owner is report-only).
11. **P403 validations** — rates required, numeric, ≤ 2 decimals; date ≥ Jan 1 of (current year − 7).
12. **P490 validation** — expiry date must be in the future (`futureExpirationDate`); mark/licence cross-reference.

## Missing capabilities

None — `QUERIES_VIEW` and `MINISTRY` cover the area.

## Open questions for the business

- Should industry users still be able to retrieve (FOI-severed) statements of other clients by number, as the public
  legacy site allowed? `StatementDocumentController` currently does (legacy behaviour, with severing), while the lists
  are fenced to the user's own client.
- Is a PDF rendering of statements still required (FOP port), or is the XML + the register reports enough?
- Report prompts are concatenated into dynamic SQL inside the legacy `HBS2R*` / `HBS3R*` procedures (e.g.
  `HBS3R450_*`, `HBS_BILLINGSMRYRPT_WHERECLAUSE`, `HBS2R401`). The screens only pass criteria values, but the report
  endpoint should validate prompt formats (codes, numbers, dates) before calling them — a cross-area concern.
- Report date prompts are sent as ISO `yyyy-mm-dd`; the procs use `TO_DATE(…, 'yyyy-mm-dd-hh24-mi-ss')`, which Oracle
  accepts for a shorter input, but this should be verified per report in TEST.
