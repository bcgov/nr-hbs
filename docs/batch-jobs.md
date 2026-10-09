# Batch jobs

## Legacy

The legacy batch layer (`hbs-batch` + `source/batch`) ran on a Windows host
under **AutoMate 5**. Each task ran `HBS_Bxxxx.BAT` → `BatchRunner` → a second
JVM that called the WebLogic EJBs **remotely over T3** and shared state with
the web app through the HBS SMB file shares. Schedules live only on the
AutoMate server (not in source).

Inferred pipeline:

```
B1011 receive + XSD-validate (FTP inbound → XMLinput)
  → B1012 unpack (Castor, scaler signature check)   → B1021 detail edits
  → B1031* summarize detail → summary returns (9 variants)
  → B19xx anomaly audits (gaps/duplicates/mismatches → SCALE_ANOMALY_LOG)
  → B0013 waste → summary returns
  → B2022 sample statistics/statements · B2024 ratio statements
  → B2031 piece-scale invoicing · B2041 weight-scale invoicing  (+ microfiche, AR rows)
  → B2042 statistics → B2065 statement delivery (print/email/FTP)
  → B2075 print (DISABLED since BT3399) → B9000–B9003 purges
```

## New

Jobs run **inside the backend pods** as `@Scheduled` methods coordinated by
**ShedLock** (`THE.HBS_SHEDLOCK`, [db/hbs-shedlock.sql](db/hbs-shedlock.sql)) —
the same pattern nr-fsp-new uses for its designate digest. No remote calls, no
second JVM, no SMB.

- `batch/BatchConfig` — enables scheduling + ShedLock **only when
  `HBS_BATCH_ENABLED=true`** (off by default: the legacy batch keeps running
  until cut-over, and two schedulers would double-process returns/invoices).
- `batch/BatchJobRunner` — start/end logging with the legacy job id and a
  "Batch Failure - <job>" email to `FORHVAP.HBSSUPRT` on error (replaces the
  log4j SMTP appender, without its "email on every run" behaviour).
- Cron + zone per job in `application.properties` (`hbs.batch.*`).

## Status per job

| Legacy job | What it does | Status |
|---|---|---|
| B1011 receive/validate | FTP inbound → XSD validate → XMLinput | ✅ web path ported (`submission/`, P505); 🔴 machine-to-machine channel open (pinch point 5). `XmlIntakeMonitorJob` alerts on stranded files |
| B1012 unpack | XML → transmission/batch/document/version rows, signature verification | 🔴 not ported (2.7K LOC, DBMS_CRYPTO — pinch points 1, 6) |
| B1021 detail edits | business edits per version → RDY/ERR | 🔴 not ported |
| B1031 A–J summarize | detail → summary returns, re-edit errors | 🔴 not ported |
| B1912…B1953 anomaly audits | write `SCALE_ANOMALY_LOG` | 🔴 not ported (the anomaly *screens* and reports are) |
| B0013 waste | waste "ready to bill" → summary returns | 🔴 not ported |
| B2022 / B2024 | sample statistics, ratio statements | 🔴 not ported |
| B2031 / B2041 | piece / weight invoicing, statements, microfiche, AR | 🔴 not ported — highest risk (pinch points 2, 7, 8) |
| B2042 | billing statistics | 🔴 not ported |
| B2065 | statement delivery (print/email/FTP) | 🔴 not ported; email template ready (`statement_delivery_email.txt`) |
| B2075 | printing | ⛔ disabled in legacy — needs a business decision (pinch point 10) |
| B9000 | purge archived XML | ✅ `XmlArchivePurgeJob` (7-year retention, configurable) |
| B9001–B9003 | purge FTP / report drops / print archive | ⛔ not needed — no FTP folders or report drop folders in the new design |
| Annual anomaly purge (manual SQL) | fiscal-year-end `SCALE_ANOMALY_LOG` purge | 🔴 should become a scheduled job (pinch point 18) |
| Annual cruise-profile copy-forward (manual SQL) | copy cruise-based billing profiles | 🔴 should become a job/admin action |

## Porting guidance

- One summary return / invoice = **one local transaction**; write files and
  emails **after commit**.
- Make each job restartable from a checkpoint (Spring Batch job repository or
  a status column), replacing the legacy "start DCN" CSV hack.
- Never run a ported invoicing job against the same environment as the legacy
  batch. Cut over job-by-job with `HBS_BATCH_ENABLED` + per-job switches.
- Recover the real AutoMate schedule and business calendar first
  ([questions.md](questions.md) Q4).
