# Open questions

Decisions the business / operations / FAM / DBAs need to make. Each one
blocks or shapes part of the build; the related pinch point is in
[pinch-points.md](pinch-points.md).

| # | Question | Who | Blocks |
|---|---|---|---|
| Q1 | **Public (anonymous) access.** The legacy app let anyone, without logging in, view invoice/statement copies, timber mark information, stumpage rates, sampling plans and harvest-history reports, and self-register. nr-fsp-new requires login. Keep a public read-only surface (separate unauthenticated routes + rate limiting), require BCeID/IDIR, or drop it? | Business | Queries area, Landing page |
| Q2 | **FAM groups.** OK to create the 28 `HBS_*` FAM roles (1:1 with the WebADE roles, `_FOREST_CLIENT-<n>`-scoped for BCeID)? Who migrates current role holders? Role stacking is kept (unlike FSP) — confirm. | Business + FAM | Go-live |
| Q3 | **Machine-to-machine submission channel.** How do scale-software vendors submit XML once the FTP folders go away — an authenticated API (preferred) or managed SFTP? Which vendors/clients use it today, on which schema version? | Business + vendors | Intake cut-over |
| Q4 | **Batch schedule.** Export the AutoMate 5 schedule (triggers, order, month-end calendar, ratio cycles, annual jobs). | Operations | Batch port |
| Q5 | **AR / revenue consumer.** Which system reads `FOREST_INVOICE` / AR posting rows HBS writes, and on what cadence? | Revenue Branch | Invoicing port |
| Q6 | **Printing.** B2075 has been disabled since BT3399 — who prints invoices today, how many, where? Print vendor / BC Mail Plus / print-queue UI? | Business | Delivery |
| Q7 | **Retention.** Retention schedule (ARCS/ORCS) for Microfiche copies, submitted XML, statements and anomaly logs. | Records | Storage design |
| Q8 | **EDI.** Confirm the EDI channel (B012/B015/B021) is retired; archive `HBS_EDI_*` data? | Business | Cleanup |
| Q9 | **Scaler digital signatures.** Move the DBMS_CRYPTO key out of the WebADE preference into a Secret/Vault; rotate? Security review of the RSA keystore (key password = alias). | Security + DBA | B1012 unpack, scaler profiles |
| Q10 | **DB changes.** Approve `THE.HBS_SHEDLOCK`, the app-settings table, a unique index for "one active version per document", and grants/synonyms for the new proxy user. | DBA | Batch, Processing Parameters, data integrity |
| Q11 | **Missing reports.** HBS3R415 has no Jasper version (is the piece-scale invoice copy broken today?); HBS3R904/906, HBS4R457/467 don't exist — confirm dead. | Business | Reports |
| Q12 | **Legacy defects not carried over.** OK to fix rather than reproduce: EVENT_SEQUENCE ≤ 9 limit, cancel-invoice paid-by bug, B2022 replacement match ignoring scale date, client users seeing other clients' document lists? | Business | Logic port |
| Q13 | **Vanity hostname.** Production hostname for HBS (legacy `a100.gov.bc.ca/ext/hbs`)? Route TLS uses the standalone `route-tls.yml`. | Business + NRS | PROD |
| Q14 | **Support contacts.** Keep `FORHVAP.HBSHELP@gov.bc.ca` (help) / `FORHVAP.HBSSUPRT@gov.bc.ca` (batch alerts)? | Business | Config |
| Q15 | **Cut-over strategy.** Big-bang vs. read-only-first (new UI for queries/screens while legacy keeps batch + workflows, then port workflows/batch job by job). The repo is built to support the incremental path. | Business + team | Plan |
