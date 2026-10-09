# Modernization pinch points

What makes moving the legacy **Harvest Billing System** (Struts 1.1 / EJB 2 /
WebLogic 10.3.6, SVN `hba-archive/hbs`) onto the nr-fsp-new stack (React +
Spring Boot on OpenShift, shared Oracle `THE`) hard, ranked by impact. Each
item gives the evidence, what this repo already does about it, and what is
still open.

Evidence comes from a full read of the legacy source (2,543 Java files, 386
JSPs, 214 Struts actions, ~30 batch jobs, 80 Crystal + 79 Jasper reports) and
the **508 production data-fix folders** (`hba-archive/hbs/data-fix`, 2017–2026,
about one hand-written SQL fix a week).

Status key: ✅ handled in this repo · 🟡 partly handled / scaffolded ·
🔴 open, needs a decision or substantial work.

---

## 1. The business logic is in Java, not PL/SQL 🔴

- **Evidence.** The ~460 `HBS_*` procedures in nr-mof-db are generated
  single-table CRUD wrappers (`HBS_STORE_HBS_SCALE_RETURN` is a bare UPDATE).
  The real rules live in EJB session beans: `SummaryScaleReturnManagerBean`
  (~13.4K LOC), `DetailManagerBean` (~13.3K), the invoicing `WorkflowManager`,
  and a 9.2K-LOC web-tier `HBSValidator`. That's roughly **60–70K LOC of real
  logic**, against FSP, where the procs own the rules and the API stays thin.
- **Consequence.** nr-fsp-new's "thin service over procs" pattern only covers
  reads and CRUD writes here. Pricing (volume × rate split into 4 components),
  cancels (negated reversals), weight-scale YTD billing deltas, sample
  ratio/variance, spreading summary amounts back to details, interest reversal,
  versioning and the two state machines all have to be **ported as Java services**.
- **In this repo.** 🟡 Every screen, read and CRUD write is in place (screen
  framework, `docs/screen-framework.md`). Workflow actions that are more than one
  proc call are **deliberately not faked**. Each is listed with file paths in
  `docs/legacy-logic-to-port.md` and the per-area docs.

## 2. Invoicing / summarization batches are not atomic or restartable 🔴

- **Evidence.** ~194 data fixes (38%) recover from failed batch runs, and the
  number is growing: invoicing rollbacks went 2 (2022) → 8 → 18 → **35 (2025)**.
  The causes are CORBA `COMM_FAILURE` on the batch's remote-EJB (T3) link,
  "silent" B2031 failures, and half-written `WEIGH_SLIP_TXN` /
  `YTD_CYCLIC_BILLING` rows. The legacy app has **no real transactions**: 164 of
  165 EJB methods are `Supports`, so every proc call autocommits.
- **Plan.** Re-host jobs in-process (no T3 hop). Make each return one local
  transaction, write files after commit, use checkpoint/restart (Spring Batch
  job repository) and an admin re-run UI.
- **In this repo.** 🟡 The batch framework is in place: ShedLock coordination,
  failure email, the purge job (B9000), and an intake watchdog. The jobs are off
  by default (`HBS_BATCH_ENABLED=false`) so they can't double-process alongside
  the legacy batch. Invoicing (B2031/B2041), sample compile (B2022), ratios
  (B2024) and summarize (B1031) are **not yet ported**; see `docs/batch-jobs.md`.
  Wrapping the existing proc sequences in `@Transactional` *changes failure
  behaviour* versus legacy, so that has to be decided job by job.

## 3. The batch schedule is not in source control 🔴

- **Evidence.** The AutoMate 5 `.aml` files only say "run BAT"; the triggers
  live on the AutoMate server. Job order (intake → unpack → edits → summarize →
  audits → invoicing → delivery → purges) is implicit.
- **Ask.** Export the real AutoMate schedule and business calendar (month-end
  invoicing, ratio cycles, annual purge and copy-forward) before cut-over.

## 4. Windows SMB file shares are the integration bus 🟡

- **Evidence.** 12 shares on the HBS file server carry
  inbound XML, statement drops, print queues, the Microfiche legal archive,
  per-client FTP folders, and **web→batch control files** (process parameters,
  printer names, alert banner, edited from screen P038).
- **In this repo.**
  - ✅ Uploaded XML lands on a ReadWriteMany PVC (`SubmissionStorage`,
    `backend/openshift.deploy.yml`), with path-traversal-safe keys.
  - ✅ The alert banner and processing parameters become DB settings
    (`processing-parameters` screen). This needs the proposed table in
    `docs/db/proposed-ddl.sql`.
  - 🔴 Per-client FTP inbound/outbound, Microfiche, and print queues are open
    (items 5, 10, 11).

## 5. Client file-drop channel and scale-software vendors 🔴

- **Evidence.** Scale-site software (third-party vendors) drops XML into
  `FTPfiles\<client>\HBS\inbound`, and job B1011 picks it up. The contract is the
  folder convention + namespace `gov.bc.ca/forests/hbs/v3` +
  `HBS_Schema_V6_1b.xsd`.
- **In this repo.** ✅ The web upload (P505) keeps the same XSD and
  transmission-record contract, adds virus scanning, and hardens against XXE.
  🔴 The machine-to-machine channel is a stakeholder-managed change. Options are
  an authenticated upload API for vendors (preferred) or a managed SFTP service.
  **Question for the business.**

## 6. Scaler digital signatures and DBMS_CRYPTO keys 🔴

- **Evidence.** Scaler auth keys are encrypted *in Oracle* with
  `DBMS_CRYPTO.encrypt(…, AES128+CBC+PKCS5, '<key>')`. The key is a **WebADE
  preference** inlined into SQL (`HbsScalerAuthKeyQuery`). RSA private keys sit
  in a keystore whose key password equals the alias
  (`DigitalSignatureService.java`). B1012 verifies signatures with CRC32 or
  SHA-256 and RSA padding from `HBS_RSA_PADDING_SCHEME`.
- **Consequence.** Turning off WebADE loses the place the key is stored. The
  crypto must be reproduced byte-for-byte or every scaler's registered key breaks.
- **In this repo.** 🔴 Not ported, deliberately; key generation/display is not
  exposed on the scaler-profile screens. Needs the key moved to an OpenShift
  Secret/Vault, a rotation plan, and a security review.

## 7. Revenue / AR handoff is a database contract 🔴

- **Evidence.** HBS writes `FOREST_INVOICE`, `PCE_SCL_INVOICE`,
  `WGT_SCL_INVOICE` and `INVC_NOTATION` and calls `HBS_CREATE_AR_POST_TXN_KEY`.
  Downstream AR reads those tables; there is no API. A known defect takes the
  cancel invoice's paid-by client from the first transaction instead of the last.
- **Ask.** Which system consumes the AR rows, on what cadence? That consumer
  must be in the invoicing regression suite.

## 8. Customer-facing invoices/statements are legal documents (XSL-FO / FOP 0.20.5) 🔴

- **Evidence.** Piece/weight invoices, volume, sample and ratio statements are
  rendered in batch by FOP 0.20.5 from 9 versioned XSL template sets. They
  carry Forest Act s.130/131 notice text and are payable to the Minister of
  Finance. Clients also parse the XML (`HBS_Transmission_Schema_V1_0h`).
- **In this repo.** 🟡 Issued documents are **served** from what's already
  stored (queries area, `/api/v1/hbs/statements/...`). *Generating* new ones
  waits on the invoicing port. Plan: FOP 2.x with the same XSL, plus
  content/pixel comparison against archived PDFs.

## 9. Three report engines → one ✅

- **Evidence.** Crystal (dead since 2016, but its names are still used in
  code), the shared NRS JasperReports Server "JCRS" (79 units, the production
  engine), and FOP for customer documents.
- **In this repo.** ✅ All 79 JCRS units are vendored into
  `backend/src/main/resources/reports/hbs/` and run **in-process**, like
  nr-fsp-new. The vendoring scrubs the JRS-only `LoggedInUser` parameter and
  resolves `repo:` subreports, and identity prompts come from the JWT, never
  the request. That removes the JCRS dependency and the email/drop-folder
  report delivery (P410).
- 🔴 Open: HBS3R415 (piece-scale invoice copy) has no Jasper version, so it
  likely doesn't work in production today. Four reports referenced in code don't
  exist (HBS3R904/906, HBS4R457/467). Long reports may need async generation
  (route timeout is 300 s).

## 10. Physical printing 🔴

- **Evidence.** The print job B2075 has been **disabled** since BT3399, so
  invoices are printed by hand from the `InvoicePrint` share.
- **Ask.** Who prints today, how much, and on which printers? Options: BC Mail
  Plus / print vendor, or a print-queue screen.

## 11. Microfiche legal archive and retention 🔴

- **Evidence.** Every issued invoice/statement is also written to
  the Microfiche share. A missing directory has caused invoicing failures.
- **Ask.** Retention schedule (ARCS/ORCS)? Proposed replacement: immutable
  object storage.

## 12. Concurrency, versioning and stuck states ✅/🔴

- **Evidence.** 101 fixes for **duplicate active versions** caused by
  concurrent GUI edits (no optimistic locking, no DB unique rule). 132 fixes for
  returns **stuck in ERR/AWP/HLD with no UI way out**. Every fix hand-writes
  `HBS_AUDIT` / `HBS_SCALE_RETURN_AUDIT` rows, generated by an Excel/VBA tool
  (`_datafixTool/HBS_DatafixTool_V3.xlsm`). In effect that tool is a missing
  admin console.
- **Plan.** Optimistic locking (ETag/revision), a DB unique index on the
  active version, an explicit state machine, and an admin "correction console"
  with automatic audit. Together these remove roughly 80% of the data-fix workload.
- **Status.** 🔴 Needs the logic port (item 1) and a DBA index change.

## 13. Hard-coded limits and legacy defects that must not be ported ✅

- `EVENT_SEQUENCE ≤ 9`: only 4 cancel/replace cycles are possible (13 fixes).
- The MM11 duplicate edit scans 13 years of history.
- The B2022 replacement match ignores scale date.
- The cancel invoice takes paid-by from the wrong transaction.
- Security defects found in the legacy web tier:
  - Only 89 of 386 JSPs check page security, so most maintenance screens can be
    opened by URL with no grant.
  - A path traversal in the report servlet.
  - A `?foi` debug parameter that changes privacy handling.
  - A self-registration key that can be computed from the client number.
  - XSS, and SQL built by string concatenation.
- **In this repo.** ✅ Every endpoint is capability-checked server-side. Every
  query is whitelisted with bound parameters, enforced by
  `CatalogIntegrityTest`. Industry users are client-fenced server-side.
  Storage keys are traversal-safe. Self-registration is replaced by FAM.

## 14. Identity: WebADE → FAM / BC Gov SSO (Keycloak) 🟡

- **Evidence.** 28 WebADE roles that **stack** (e.g. CLI_USER + CLI_SITE_ADMIN),
  three user types (MOF/CLI/SPC), anonymous public access, and per-user data
  domains (district / scale site / client location in `HBS_USER_*_DATA_DOMAIN`).
- **In this repo.** ✅ Roles map 1:1 to `HBS_*` FAM client roles (Keycloak
  `client_roles`), and stacking is preserved (unlike FSP's single-role model).
  Client-tied roles carry FAM's `_FOREST_CLIENT-<8 digits>` scope, and FSP's
  org picker is reused. The data-domain
  maintenance screen is kept (`user-data-domains`). 🔴 FAM must create the 28
  roles. **Public anonymous access** (invoice/statement copies, timber mark,
  stumpage rates, sampling plans) has no equivalent in the FSP model; see
  `docs/questions.md`.

## 15. Shared `THE` schema with FTA, CLIENT, SCS, WASTE, MPR 🟡

- **Evidence.** HBS reads FTA (`TIMBER_MARK`, `HAULING_AUTHORITY`), CLIENT,
  SCS (`QUANT_LICENSE` is now a view over `SCALER`), appraisal materialized
  views and WASTE, and writes into CIS/AR. ~600 public synonyms, no schema
  prefixes. The app role has **SELECT only** on HBS tables;
  writes must go through the procs.
- **In this repo.** ✅ Same approach as nr-fsp-new: no Flyway/Postgres, no
  owned schema. Reads are ported SQL and writes go through the legacy procs
  (`CommandDefinition`). 🔴 Some procs the legacy Java calls **don't exist** in
  nr-mof-db (`HBS2R026`, `HBS2R331`, `*_XU_FOREST_TALLY` …). Validate against
  the TEST database before relying on those paths. New objects (`HBS_SHEDLOCK`, the
  app-setting table) need nr-mof-db migrations: `docs/db/`.

## 16. EDI channel 🔴

- **Evidence.** B012/B015/B021 code (fixed-width COBOL records, X12 acks
  through an ITSD gateway) has no scheduler entry, and its search proc is
  missing from the DB.
- **Ask.** Confirm EDI is retired; archive the `HBS_EDI_*` data.

## 17. Volumes and long-running work 🟡

- **Evidence.** Invoicing runs are capped at 180/210 minutes. Batch output caps
  are 600/1000/400 returns. Batch jobs use 512 MB heaps. Annual copy-forward
  touches 2–8K rows.
- **In this repo.** ✅ All searches are server-paged with a row cap.
  🔴 Batch pods need sizing once jobs are ported.

## 18. Key-person and undocumented procedures 🔴

- **Evidence.** One analyst authored most post-2023 data fixes. Annual anomaly
  purge, annual cruise-profile copy-forward, and population-window tweaks are
  manual scripts.
- **Plan.** Interview them; turn each procedure into a scheduled job or admin
  screen.

---

See also: `docs/questions.md` (decisions needed),
`docs/legacy-logic-to-port.md` (the Java logic still to port),
`docs/batch-jobs.md`, `docs/reports.md`, `docs/roles-and-security.md`.
