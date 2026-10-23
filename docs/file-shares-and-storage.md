# File shares → storage

The legacy app integrated web tier, batch and clients through 12 Windows SMB
shares on the HBS file server. OpenShift
pods can't mount IDIR SMB shares, and per-client `<CLIENT>$` share ACLs don't
map to containers, so each share needs a replacement.

> **Legacy deadline.** The legacy Solaris web tier reaches these folders through
> SMBFS mounts, and the 2026 Q4 Solaris patch removes SMBFS. Interim options for
> keeping legacy running are in [smbfs-removal.md](smbfs-removal.md).

Each share and its replacement:

| Legacy share | Used for | Replacement | Status |
|---|---|---|---|
| `XMLinput` | P505 uploads + B1011 output, read by B1012 | PVC `/data/hbs/xml/input/<client>/<transmissionId>.xml` (`SubmissionStorage`) | ✅ |
| `XMLarchive` | unpacked XML | PVC `/data/hbs/xml/archive/…`, purged by B9000 | ✅ (purge); ✅ `intake.UnpackJob` moves each unpacked P505 file (and its `.submitter` sidecar) here |
| `FTPfiles\<client>\HBS\Inbound` | scale-software vendors drop XML | authenticated upload API for vendors, or managed SFTP | 🔴 decision ([questions.md](questions.md) Q3) |
| `FTPfiles\<client>\HBS\Outbound` | statement delivery by "FTP" | download from the Queries screens (statement endpoint) + e-mail notice | 🟡 FTP push stays in legacy B2065 |
| Statement drop directory (`DocumentDeliveryTransmissionHelper.getStatementDropDirectory`, B2065 `<id>.xml` / `<id>.pdf`) | the statement e-mail links (`/StatementDelivery/transmissions/<id>.pdf\|xml`) | not needed — the new app rebuilds each transmission's XML/PDF from Oracle (`StatementTransmissionController`, [areas/queries.md](areas/queries.md#ported)) | ✅ (TEST regression) |
| `ReportingOutput` | on-screen report drops + e-mail pickup links (P410) | not needed — reports stream to the browser | ✅ |
| `ProcessParam` | processing-parameter CSV, printer names, alert banner (edited on P038) | Settings ConfigMap, shown read-only in Administration › Processing Parameters ([coexistence.md](coexistence.md#settings-configmap)) | ✅ |
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
