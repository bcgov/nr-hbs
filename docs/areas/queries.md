# Queries area (legacy Queries tab, P400, `/opq/*`)

Legacy source root: `hba-archive/hbs/trunk/source/` (abbreviated below as `ear/…` = `ear/hbs-view/src/main/java/ca/bc/gov/mof/hbs/presentation/` for actions,
`ear/hbs-ejb/src/main/java/ca/bc/gov/mof/hbs/` for EJBs). DB DDL: `nr-mof-db/scripts/THE/` (`PROCEDURES/`, `FUNCTIONS/`).

Files:

- Backend: `backend/src/main/java/ca/bc/gov/nrs/hbs/api/catalog/QueriesCatalog.java` (issued documents,
  transmissions, billing summary, `codes.queries.*`), `backend/src/main/java/ca/bc/gov/nrs/hbs/api/catalog/QueriesReportsCatalog.java`
  (harvest history, timber marks, cut to cruise, scale site summary, aged unbilled),
  `backend/src/main/java/ca/bc/gov/nrs/hbs/api/queries/StatementDocumentController.java` and
  `StatementTransmissionController.java` (bespoke, replace the `/StatementDelivery` servlet),
  `StatementPdfRenderer.java` (XSL-FO → PDF, stylesheets in `backend/src/main/resources/xsl/<version>/`)
- Frontend: `frontend/src/screens/areas/queries.ts`, `frontend/src/pages/StatementDeliveryPage.tsx`
  (`/StatementDelivery/...` links), `frontend/src/pages/InvoiceInformationPage.tsx` (`/public/invoice-information`)

All Queries screens use capability `QUERIES_VIEW` (legacy grant `web:/opq/P400.jsp` = MOF_USER, CLI_USER) except
Aged Unbilled Scale (`MINISTRY`). The legacy sub-pages had no page security at all (anonymous users could open them
by URL); the new app requires a logged-in MOF/CLI user for every screen of the Queries area. The legacy public menu is
carried over separately as the public read-only area (`/public`, [public-access.md](../public-access.md)): harvest
history, issued documents and statement copies, and timber marks, always FOI-severed for the anonymous viewer.

## Screen mapping

| Legacy screen | New screen id | Query / command ids | Ported from |
|---|---|---|---|
| P400 Queries Menu | Queries area SideNav (no page) | — | `opq/P400.jsp` |
| PUBLIC anonymous menu / tab bar (`/include/mainmenu_public.jsp`, `tab_public.jsp`) | public read-only area `/public` (+ `/public/access` for "Register") | `/api/v1/hbs/public/**` (`publicaccess.PublicController`, whitelist `PublicQueries`) | Decided 2026-10-21 (Q1). See [public-access.md](../public-access.md). Public FAQ / glossary / manual links are static MOF web pages. |
| P431 / P432 Mark Monthly Billing History (selection + report configuration) | `billing-history` (+ reports `HBS3R431` PDF and CSV, + Download XML) | `harvestHistory.billing` | Billing branch of `PROCEDURES/V7.01739__HBS_GET_HISTORY.sql` (same SELECT/filters as `V7.01498__HBS00001R.sql` behind `HBS3R431`); `ear/…/opq/ftas/MarkMonthlyBillingAction.java`; group/SPG/detail-line prompt codes from `ear/…/opq/ReportingFactory.java` (`getGroupTypeParam`, `getDisplaySPGParam`, `getDisplayDetailsParam`) and `opq/OpqConstants.java` |
| P441 / P442 Mark Monthly Scaling History | `scaling-history` (+ report `HBS3R441`, + Download XML) | `harvestHistory.scaling` | Scaling branch of `HBS_GET_HISTORY` (same as `V7.01570__HBS3R441R.sql`) |
| P493 / P494 Harvest Report XML Delivery Address + Confirmation | not carried over | — | Replaced: the history list is exportable (search export / CSV) and the PDF downloads directly; no e-mail drop-directory delivery. See "Legacy logic not yet ported" for the XML history file. |
| P493 / P494 generic Report Delivery Address / Confirmation (`opq/emailReport.jsp`, `confirmed.jsp`) | not carried over | — | Every report now streams directly from the in-process Jasper engine (`HbsReportController`); no e-mailed/queued reports. |
| P449 Search for A Single Issued Document | `issued-document` | `issuedDocuments.search` (`statementNumber`) | `HBS3R450_*` procs (below); 7-year/2003-11-01 rule from `ear/…/opq/P450Action.java` |
| P450 Search for Issued Documents | `issued-documents` (document-type radio; Print / Print Client Register for that type; Download XML) | `issuedDocuments.search` | `PROCEDURES/V7.01572__HBS3R450_PS.sql`, `V7.01574__HBS3R450_WS.sql`, `V7.01571__HBS3R450_CS.sql`, `V7.01573__HBS3R450_RS.sql` (UNION ALL of the four), called by `ear-ejb/…/ivs/TransactionStatementManagerBean.getSearchIssuedDocumentStatement` |
| P451 / P461 List of Issued Piece / Weight Scale Invoices | `issued-documents` results (`documentType` PSI / WSI); Print = `HBS4R452` / `HBS4R462`, Print Client Register = `HBS3R451` / `HBS3R461` | `issuedDocuments.search` | `opq/P451.jsp` columns |
| P456 / P466 / P471 / P476 List of Issued Volume / Compiled Sample / Ratio Statements | `issued-documents` results (PSV / WSV / CSS / RS); Print `HBS4R452`/`HBS4R462`/`HBS4R472`/`HBS4R477`, Register `HBS3R451`/`HBS3R461`/`HBS3R471`/`HBS3R476` | `issuedDocuments.search` | `opq/P456.jsp` columns (Total Weight = `SAMPLE_SCALE_SUMMARY.NET_WEIGHT` for CSS) |
| P474 Statement/Invoice Does Not Exist / Unavailable | not carried over as a page | — | Replaced by the empty-result state of `issued-document` and by the 404 / 410 responses of `StatementDocumentController` ("Statement Does Not Exist" / "Statement cannot be retrieved as it is older than seven years"). |
| StatementDelivery servlet `/StatementDelivery/statements/<type>/<number>.pdf\|xml` | bespoke `GET /api/v1/hbs/statements/{type}/{number}.{pdf\|xml}`; rows carry `documentUrl` / `documentXmlUrl`; SPA route `/StatementDelivery/statements/:type/:file` (old links/bookmarks) | — | `ear/…/dad/StatementServlet.java`, `ear-ejb/…/dad/HBSTransmission.java`, `ear-ejb/…/dad/HbsFileManagerBean.findHbsFileByTransactionStatement` + `HbsFileQuery.pkByStatementNumber` (statement table → `HBS_FILE.FILE_OBJECT`), FOI from `TransactionStatementManagerBean.freedomOfInformationCheck` and `hbs-core/src/main/resources/xsl/V1_0h/freedom-of-info-{send,copy}-to.xsl`; PDF see "Ported" below |
| StatementDelivery `/transmissions/<id>.pdf\|xml` (the statement e-mail links) | bespoke `GET /api/v1/hbs/statements/transmissions/{id}.{pdf\|xml}`; SPA route `/StatementDelivery/transmissions/:file` | — | `StatementServlet.handleTransmissionCommand`; files rebuilt from Oracle as legacy batch B2065 wrote them — see "Ported" below. The P453 XML/PDF File columns still show the stored file names. |
| Invoice general information (`hbs/opq/cis/invoiceGeneralInformation.html`, linked from the statement e-mail) | public SPA page `/public/invoice-information` (old `/invoice-information` redirects) | — | Static content ported to `InvoiceInformationPage.tsx`; no sign-in, like the legacy page. |
| P452 / P453 Search for Transmissions of Issued Statements / Transmissions of Issued Statements (+ Home "SearchFromHome") | `statement-transmissions` (`?recent=Y` = last 30 days for the user's client; Print = `HBS3R453`) | `statementTransmissions.search` | `PROCEDURES/V7.01576__HBS3R452.sql`; `ear/…/opq/P452Action.java` (`ACTION_HOME_SEARCH`, `setWorkbenchDateRange`) |
| P453 per-type document count links | row link → `issued-documents?transmissionId=` | `issuedDocuments.search` (`transmissionId`) | `HBS3R450_*` `RB_TRANSMISSION_ID` branch (`DOCUMENT_DELIVERY_BATCH_ITEM`) |
| P421 Billing Summary Selection ("List of Invoices and Volume Statements") | `billing-summary` (View PDF Report `HBS3R421` piece / `HBS3R422` weight) | `billingSummary.search` | `PROCEDURES/V7.01567__HBS3R421.sql`, `HBS3R422`, `FUNCTIONS/…HBS_BILLINGSMRYRPT_WHERECLAUSE.sql`; `ear/…/opq/P421Action.java` |
| P422 Billing Summary Report Configuration | not carried over | — | Non-functional orphan mock-up (no action, nothing links to it). |
| P441.jsp / P442.jsp (old mock-ups with the same ids) | not carried over | — | Obsolete JavaScript-only mock-ups, superseded by the invoiceSummary "scaling" flow (now `scaling-history`). |
| P401 Configure Scale Site Summary Report | `scale-site-summary` (Group By radio + Return Type pick one of 8 units `HBS2R401/402/403Group{DistScale,ScaleTM,ScalePop}` behind a single Download PDF) | `scaleSiteSummary.search` | Summary-return branch of `PROCEDURES/V7.01527__HBS2R401.sql`, `V7.01528__HBS2R402.sql`, `V7.01529__HBS2R403.sql`; `ear/…/opq/dac/P401Action.java` |
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

**Issued Documents "Download XML" (legacy P451/P456 "Generate XML").** `GET /api/v1/hbs/statements/export.xml`
with the search criteria (`StatementTransmissionXml`):
- **Format:** every listed statement's stored XML, with private client details hidden (FOI-severed)
  as on the single-statement download, merged into one `hbs-transmission`
  (`HBS_Transmission_Schema_V1_0h`): one `hbs-batch` per document type, a request header and
  the FOIPP fine print.
- **Row limit:** at most 200 rows, legacy's `SUPPRESS_LIMIT`. Statements older than seven years are
  left out.
- **Validation:** the result is checked against V1_0h, and a mismatch is only logged, since older
  stored files follow the schema version current when they were issued.
- **Batch order:** one `hbs-batch` per type in legacy's `HashMap` order (RS, PSV/CSS, PSI, WSV, WSI).
- **Download PDF** (legacy "Generate PDF"): `GET /api/v1/hbs/statements/export.pdf`, the same document
  rendered with the current stylesheets (see "Ported").

**Download XML (legacy "Send XML").** `GET /api/v1/hbs/queries/{harvestHistory.billing|harvestHistory.scaling}/export/xml`
(`HarvestHistoryXmlService`):
- **Format:** the same document legacy e-mailed, `HBS_Billing_History_Request_Schema_v3` /
  `HBS_Scaling_History_Request_Schema_v3` (namespace `gov.bc.ca/forests/hbs/v2`, vendored in
  `backend/src/main/resources/xml/history/`). Every file is validated against the schema
  before download.
- **Header:** repeats the criteria in legacy wording ("All", "All Land", "Interior Forest
  Regions", …).
- **Privacy:** names hidden for privacy (FOI-severed) read "CLIENT NAME NOT RELEASABLE" /
  "CLIENT LOCATION NAME NOT RELEASABLE".
- **Row limit:** 5,000 or more rows are refused, as legacy did.
- **Deliberate differences from legacy:** a 24-hour `datetime-created` (legacy wrote 12-hour
  without AM/PM), rows in screen order (legacy had none), no `xsi:schemaLocation`, and a
  download instead of an e-mail.

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

## Ported

| Legacy | New | Notes |
|---|---|---|
| Statement PDF rendering — `ear-ejb/…/dad/HbsStatementPdfFactory.java` (FOP 0.20.5 `Driver`, `conf/hbsconfig.xml`, `support/logo.gif`) + `hbs-core/src/main/resources/xsl/<TRANS_VERSION>/` | `StatementPdfRenderer` (Apache FOP 2.12, JDK XSLT); `GET /api/v1/hbs/statements/{type}/{n}.pdf` | The XSL tree (V1_0 … V1_0h, V999_0) is copied **unchanged** to `backend/src/main/resources/xsl/` (minus `combine.xsl`, `hashtotal.xsl`, `ejb.xsl`, which need Xalan Java extensions and are replaced by Java merging). FO that FOP 0.20 accepted and 2.x rejects is repaired between XSLT and FOP by `Fop020CompatibilityFilter` (cell spans past the last column, empty `block-container`, borders on side regions). `xsl/support/hbs-fop.xconf`: Arial → base-14 Helvetica (same metrics; legacy embedded Arial from Windows font files), relaxed validation; the logo resolves from the classpath only. The single-statement PDF uses the file's own version (`HBS_FILE.HBS_TS_VERSION_ID → HBS_TS_VERSION.TRANS_VERSION`) and the FOI-severed XML, as `HBSTransmission.getPDFTransmission` did. |
| P451/P456 "Generate PDF" — `ear/…/dad/docd/DataDeliveryAction.java` (ACTION_PDF_REPORT) → `DocumentDeliveryManagerBean.generateStatementReport` → `HbsStatementPdfFactory.getPDFTransmission(out, reader)` | `GET /api/v1/hbs/statements/export.pdf`; "Download PDF" next to "Download XML" on `issued-documents` (`pdfExport`) | Same criteria, FOI and ≤ 200 limit (`Constants.SUPPRESS_LIMIT`) as `export.xml`; rendered with the latest version (`XmlConstants.CURRENT_XSL_VERSION` = `HBS_TS_VERSION` with the highest id, `StatementXslVersion`). |
| P451/P456 "Generate XML" — `generateStatementReport` | `GET /api/v1/hbs/statements/export.xml` (`StatementTransmissionXml`) | Batches now in legacy's `HashMap` order. |
| Statement-delivery transmission files — batch `B2065.createDeliveryFiles` / `mergeItemIntoProperBatch` (writes `<id>.xml` / `<id>.pdf` to the client's drop directory) served by `StatementServlet.handleTransmissionCommand` | `GET /api/v1/hbs/statements/transmissions/{id}.{xml\|pdf}` (`StatementTransmissionController`, `DeliveryTransmissionXml`); SPA `/StatementDelivery/transmissions/:file` | Rebuilt from `DOCUMENT_DELIVERY_TRANSMISSION` + `DOCUMENT_DELIVERY_BATCH_ITEM` (ordered by `BATCH_ID`) + each item's `HBS_FILE.FILE_OBJECT`, type from `HBS_DETERMINE_STATEMENT_TYPE` (`DocumentDeliveryTransmissionQuery.selectTransmissionItems`): root attributes `transmission-id`, `client-number`, `client-location-code`, `recipient-type`, `date-created`/`time-created` (from `CREATION_DATE`, legacy `hh:mm:ss`), the first statement of each file, one batch per type in B2065's `HashMap` order. PDF with the current stylesheets (cover sheet included). Available once B2065 set `FILE_NAME` / `PDF_FILE_NAME` (status COM/SUB). Access as legacy: signed-in user; ministry any client (an optional `?clientNumber=` must match), others only their own (active) client; otherwise 404. No FOI severing (legacy served the client's own files). |
| Statement e-mail reference page `invoiceGeneralInformation.html` (batch property `invoice_general_info`) | public SPA page `/public/invoice-information` (old `/invoice-information` redirects) | Configure the legacy batch `cfg_b_general_info` = `<new app URL>/invoice-information`. |

Differences from legacy: the transmission XML is rebuilt rather than read from the share, so statements
are copied as stored (legacy re-serialised them through Castor) and `xsi:schemaLocation` is not written;
the PDF text uses Helvetica instead of embedded Arial. These need the TEST regression (open the same
e-mail link in legacy and new and compare).

## Ported — screen validations, FOI gate, associated locations, MGU description

| Legacy | New | Notes |
|---|---|---|
| P450 FOI gate on Print / Print Client Register — `opq/P450Action.perform` (BT 2637 / BT17247), `canShowIndustry` → `TransactionStatementManagerBean.findNotReleasableIndustryInfoCount` (`TransactionStatementQuery.getNotReleasableIndustryInfoCount`) | `queries.QueriesReportGuard` (a `ReportRequestGuard` run by `HbsReportController` before every report) for `HBS4R452/462/472/477`, `HBS3R451/461/471/476`; these reports are now in `HbsReportController.INDUSTRY_REPORTS` | Every caller: `RB_DOC_NUMBER` is blanked (legacy `setDocumentNumber(null)`). Non-ministry caller: `RB_CLIENT_ASSOCIATION=BOTH`, `RB_CLIENT_NUMBER` = active client (the report procs filter only on the prompts, so this is the fence), then the legacy check over the listed documents (`issuedDocuments.search` with the prompts mapped back to criteria → their summary returns → `QueryCriteriaReads.industryNotReleasable`, bound, in chunks of 900): a piece / weight / compiled-sample transaction whose two parties are both other clients, one of them an Individual (`V_CLIENT_PUBLIC`), refuses the report with "The report contains personal information, and is temporarily unavailable for public viewing". Ratio statements are not checked (legacy). Legacy compared the parties with the user's `HBS_USER.CLIENT_NUMBER`; the new check uses the token's active client. `canShowInPublic` is not needed: the public area offers no reports. |
| Harvest history validation — `opq/ftas/MarkMonthlyBillingAction.validateForm` (+ `getLimitDate`, `isBillingDateValid`, `isValidDateRange`, `isValidCliLoc`, `isValidMark`, `isValidLicence`, `isValidSPGCombination`) | `queries.QueriesCriteriaGuard` (`@Before` on `QueryService`) for `harvestHistory.billing/scaling`; the month rules in `queries.HarvestHistoryRules`, shared with the public area (`PublicQueries.historyRange`) | From = first of its month, To = last of its month (`TimberMarkHelper.getBillingDateFrom/To`), so "up to 12 months" is Jan–Dec; From ≥ max(1 Jan (year − 15), 1995-01-01); not after the last day of this month; From ≤ To. Client / location exist; every mark is a billed mark; every licence has billed marks; with two or more of species / product / grade the combination is in `SPECIES_PRODUCT_GRADE_XREF` during the interval; Mgmt Unit No needs a type; "Billing/Scaling History criteria selection insufficient". New: at most 10 marks / licences (legacy had 10 boxes). Not ported: the special species/product/grade groups (not offered) and "at least one billing type" (single select now). The XML history file (< 5,000 rows) was already ported (`HarvestHistoryXmlService`). |
| History report prompts (`ReportingFactory2.setParameters`, item 5 of the old list) | `HarvestHistoryPrompts` (already in place) | Marks → `INVSMRY_LICMARK01..10` type 2, licences type 1, file types type 3, mgmt unit type 4; file types quoted by `ReportPromptRules`. |
| P450 / P449 — validation.xml `P450Form` + `P450Action.perform` | `QueriesCriteriaGuard` for `issuedDocuments.search` | Scale / issue From: valid date, not before max(1 Jan (year − 7), 2003-11-01) ("Statement cannot be retrieved as … is older than seven years", BT 16733), From ≤ To, not in the future; To not in the future; a complete issue or scale date range unless a statement number (P449) or transmission id (P453 link) is given (`validateOneOfManyRequired`); client number with location, client / location exist; Pop/Strat/Year not for piece documents, P, P/Y or P/S/Y with 4/2/4 digits, sampling year ≥ year − 7; Ratio Statements take no scale site / dates / mark / districts (`searchFieldAllowed`); scale site and mark cross-references. |
| P421 — validation.xml `P421Form` + `P421Action.perform` | `QueriesCriteriaGuard` for `billingSummary.search` | Billing / scale From: valid, ≥ 1 Jan (year − 7), not future, ≤ To, one month when nothing narrows harvest (district, file type, mark, licence, mgmt unit) and scale (district, site, population) (`billingSummaryDateRange`), at most 12 months (`dateRangeRestriction`); one date range required; licence, mark, scale site cross-references; sampling year limit; Pop/Strat/Year digits and combination (weight / sample only). |
| P452 — validation.xml `P452Form` | `QueriesCriteriaGuard` for `statementTransmissions.search` | Transmission From ≥ the 7-year / 2003-11-01 limit, not future, ≤ To; To not future; client number / location exist. The Home "recent" search has no dates. |
| P452 "Associated Locations" (CLI_DOC_RCVR) — `HBS3R452` `rb_cli_loc_option = 'A'` (`strAndAssLoc`) | `statementTransmissions.search` filter `clientLocationOption=A` (`HBS_USER_CLI_LOC_DATA_DOMAIN` of `:hbsUserId`); criterion "Client Locations" on `statement-transmissions`; `HBS3R453` gets `RB_CLI_LOC_OPTION` from it | |
| P481 MGU description — `TimberMarkReaderBean.findMgmtUnitDesc` | `timberMarks.detail` column `mgmtUnitDescription` | Both look-ups are external code lists in `CODE_LIST_TABLE` (`CodeLocator`): `MGMT_UNIT_XREF` names the unit type's list, whose entry for the unit id is the description; blank when not cross-referenced (legacy swallowed the miss). |
| P401 — `opq/dac/P401Action.validateForm` | `QueriesCriteriaGuard` for `scaleSiteSummary.search` | Scale From required / valid / ≥ 1 Jan (year − 7); To required / valid; return type required; Scale Site and Population grouping not for piece; mark and site exist; client association (All takes no client, the others need one: "Association Required." / "Client number Required." / "Client information Required."); Pop/Strat/Year (not for piece, digits, sampling-year limit, combination); scaler licence not for weight, must exist. |
| P403 — `opq/dac/P403Action.validateForm` | `QueriesCriteriaGuard` (`agedUnbilled.search`: Scaled Prior To required / valid / ≥ 1 Jan (year − 7)); `QueriesReportGuard` (`HBS2R411/412`: each Estimated Average Rate > 0, numeric, ≤ 2 decimals) | The rates are report prompts, so they are checked when the report runs. |
| P490 — validation.xml `P490Form` | `QueriesCriteriaGuard` for `cutToCruise.search` | Mark / licence cross-references; the expiry exclusion only with Forest File ID or client (BT11752) and not in the future ("Expiration Date may not be a future date." — the legacy rule refuses a future date); client exists, client / location exist. The screen's expiry date is no longer required (optional in the proc and legacy). |

Report-path note: the guards run on `POST /reports/{id}` through the new `service.v1.report.ReportRequestGuard`
extension point (`HbsReportController` applies every guard bean in turn).

## Not carried over (from the old list)

- **P450 sort radio / `populationStratumYearSortAllowed`**: the sort radio became sortable columns.

## Missing capabilities

None — `QUERIES_VIEW` and `MINISTRY` cover the area.

## Open questions for the business

- Should industry users still be able to retrieve (FOI-severed) statements of other clients by number, as the public
  legacy site allowed? `StatementDocumentController` currently does (legacy behaviour, with severing), while the lists
  are fenced to the user's own client.
- Report prompts are concatenated into dynamic SQL inside the legacy `HBS2R*` / `HBS3R*` procedures (e.g.
  `HBS3R450_*`, `HBS_BILLINGSMRYRPT_WHERECLAUSE`, `HBS2R401`). The screens only pass criteria values, but the report
  endpoint should validate prompt formats (codes, numbers, dates) before calling them — a cross-area concern.
- Report date prompts are sent as ISO `yyyy-mm-dd`; the procs use `TO_DATE(…, 'yyyy-mm-dd-hh24-mi-ss')`, which Oracle
  accepts for a shorter input, but this should be verified per report in TEST.
