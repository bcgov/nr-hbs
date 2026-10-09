# File shares → storage

The legacy app integrated web tier, batch and clients through 12 Windows SMB
shares on the HBS file server. OpenShift
pods can't mount IDIR SMB shares, and per-client `<CLIENT>$` share ACLs don't
map to containers, so each share needs a replacement:

| Legacy share | Used for | Replacement | Status |
|---|---|---|---|
| `XMLinput` | P505 uploads + B1011 output, read by B1012 | PVC `/data/hbs/xml/input/<client>/<transmissionId>.xml` (`SubmissionStorage`) | ✅ |
| `XMLarchive` | unpacked XML | PVC `/data/hbs/xml/archive/…`, purged by B9000 | ✅ (purge); unpack 🔴 |
| `FTPfiles\<client>\HBS\Inbound` | scale-software vendors drop XML | authenticated upload API for vendors, or managed SFTP | 🔴 decision ([questions.md](questions.md) Q3) |
| `FTPfiles\<client>\HBS\Outbound` | statement delivery by "FTP" | download from the Queries screens (statement endpoint) + e-mail notice | 🟡 |
| `ReportingOutput` | on-screen report drops + e-mail pickup links (P410) | not needed — reports stream to the browser | ✅ |
| `ProcessParam` | processing-parameter CSV, printer names, alert banner (edited on P038) | DB settings table + Administration › Processing Parameters | 🟡 needs DDL ([db/proposed-ddl.sql](db/proposed-ddl.sql)) |
| `Microfiche` | legal archive copy of every invoice/statement | immutable object storage with records retention | 🔴 decision (Q7) |
| `InvoicePrint\SendTo` / `\CopyTo` | print queue (B2065 → B2075) | print vendor / BC Mail Plus or a print-queue screen | 🔴 decision (Q6) |
| `PrintedInvoice\*`, `PrintArchive` | printed output archive | as above | 🔴 |
| `EDIarchive`, `D:\EDIfiles\*` | legacy EDI | none if EDI is retired | 🔴 confirm (Q8) |

## The PVC

`backend/openshift.deploy.yml` creates `${NAME}-backend-xml-${ZONE}`
(ReadWriteMany, `netapp-file-standard`, `XML_STORAGE_SIZE` default 5Gi) and
mounts it at `/data/hbs/xml` on every backend pod, so the upload endpoint and
whichever pod wins the ShedLock for the intake job see the same files.

Keys are always `<area>/<8-digit client>/<numeric id>.xml` and validated, so
a caller can't escape the root (the legacy report servlet had a path traversal
hole).

If the team prefers S3-compatible object storage (NRS ECS), only
`SubmissionStorage` changes.
