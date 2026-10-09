# Reports

## Legacy

Three engines:

1. **Crystal Reports** (`source/crystal/*.rpt`, 80 files) — superseded in
   Feb 2016 but the Java still refers to reports by their `.rpt` names
   (`ReportingFactory2.java`, 9.6K lines of positional prompts).
2. **Shared NRS JasperReports Server "JCRS"** (`source/jasper-server`, 79
   report units, datasource `JCRS_HBS` / `HBS$JASPER1`) — the production
   engine for every on-screen report, invoked through WebADE reporting with
   HTTP, e-mail or drop-folder delivery (P410 "Recent Queries").
3. **XSL-FO + Apache FOP 0.20.5** — the customer-facing invoices and
   statements, generated in batch (see [batch-jobs.md](batch-jobs.md)).

## New — in-process JasperReports (same approach as nr-fsp-new)

All 79 JCRS units are vendored into
`backend/src/main/resources/reports/hbs/<REPORT_ID>/` together with their
subreports, plus `_images/` (logos) and a generated `catalog.json`
(id, title, procedure, prompt names, subreports). Re-run the vendoring with
`python3 backend/scripts/vendor-jcrs-reports.py`; the source is
`hba-archive/hbs/trunk/source/jasper-server/src/main/resources/resources/JCRS/HBS`.

Changes made while vendoring:

- `LoggedInUser` was typed to a JasperServer-only class
  (`com.jaspersoft.jasperserver.api.metadata.user.domain.User`) — retyped to
  `java.lang.Object` so the JRXML compiles outside JRS.
- `org.apache.commons.lang.StringUtils` (commons-lang 2, bundled with JRS) →
  `org.apache.commons.lang3.StringUtils`.
- Subreports/logos resolved via `repo:` + `SUBREPORT_DIR`/`SUBREPORT_EXT`
  defaults — `HbsReportService` compiles every subreport into a per-report
  scratch dir (`hbs.reports.work-dir`, an emptyDir in OpenShift) and passes
  that dir as `SUBREPORT_DIR`, `.jasper` as `SUBREPORT_EXT`.

`HbsReportService`:

- compiles on first use and caches; fills against the live datasource (the
  JRXMLs are `language="plsql"` calls to the `HBS2R*`/`HBS3R*` procs);
  exports PDF or CSV.
- **overwrites identity prompts** from the JWT — `*_USERTYPE`/`*_USERTYPECD`
  (MOF/CLI/SPC), `*_USERCLILOC` (industry client), `*_JOBNO`; caps
  `*_SELECTCOUNT` (default 5,000).

`HbsReportController`:

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/hbs/reports` | catalogue filtered to what the caller may run |
| `POST /api/v1/hbs/reports/{id}` `{parameters:{PROMPT:value}, format:'PDF'|'CSV'}` | run and download |

Industry users may run the return, invoice-copy and billing-history reports
(scoped to their client by the procs); every other report is ministry-only —
the legacy audience rules (`HbsReportController.INDUSTRY_REPORTS`).

In the SPA, reports are run from **Reports** (any report, humanized prompt
names) or from screen buttons that pre-map criteria to prompts
(`ReportLinkDef`), replacing the legacy per-screen "View PDF"/"Send" buttons
and the e-mailed drop folders.

## Prompt rules

Many legacy report procs build their SQL by pasting prompt values in, sometimes
**unquoted** (`' WHERE hsr.DOCUMENT_CONTROL_NUMBER = ' || prompt`, `' IN (' || list || ')'`,
`' ORDER BY ' || prompt`), including through the shared `HBS_BILLINGSMRYRPT_WHERECLAUSE` /
`HBS_REGISTRYRPT_WHERECLAUSE` functions. Two layers protect them:

1. `HbsReportService.SAFE_PROMPT` — every prompt: no quotes, semicolons or `--`. Enough
   where the proc quotes the value (`''' || p || '''`, `qt || p || qt`).
2. `ReportPromptRules` — per report, for each prompt pasted in unquoted: digits only
   (org units, document / version numbers, ids, row ranges, years), a digit list (invoice
   numbers), a code list (file types) or an ORDER BY column list. Without it, a value
   like `1 OR 1 IN (1)` passes layer 1 and turns the WHERE clause into "every row".
   It also names the prompts a proc can't run without — a blank one leaves `x = `
   dangling (ORA-00936) — and answers 400 naming the prompt instead.

File-type lists (`RB_FILE_TYPE1..3`, `INVSMRY_FILETYPE1..3`) are entered bare
(`A01,B07`) and quoted by the backend (`'A01','B07'`), as legacy screens sent them;
`ALL` is passed through. The rules were derived from the nr-mof-db source of every
catalogued report proc; `ReportPromptRulesTest` checks each rule names a real prompt of
its report. A new report whose proc concatenates a prompt unquoted needs a rule there.

## Open items

- **HBS3R415** (piece-scale stumpage invoice copy) has **no Jasper
  conversion** — probably broken in production today; needs a JRXML.
- **HBS3R904, HBS3R906, HBS4R457, HBS4R467** are referenced in legacy
  `Constants.java` but exist in neither engine — confirm they are dead.
- Fonts: the JRXMLs use Arial/Times; the backend bundles Liberation (metric
  compatible) via `fonts/` like nr-fsp-new — compare output against JCRS.
- Very large reports may exceed the 300 s route timeout → make them async if
  needed.
- Each report should be smoke-tested against the TEST database (compile + fill with
  empty prompts); `HbsReportService` logs compile failures per report.
- Customer-facing invoices/statements (FOP) are **not** in this engine — see
  [pinch-points.md](pinch-points.md) §8.
