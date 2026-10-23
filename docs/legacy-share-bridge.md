# Legacy share bridge

While the legacy batch keeps running ([coexistence.md](coexistence.md)), it
still reads its processing parameters (invoicing start location and time
limit, batch printer names, alert text) from flat files in the `ProcessParam`
share. Those values now live in the backend's settings ConfigMap
([coexistence.md](coexistence.md#settings-configmap)). The bridge copies them to
the files the batch reads.

```
HBS app (OpenShift)                         Windows batch host
GET /api/v1/hbs/integrations/legacy/   ◀──  Sync-HbsProcessParameters.ps1 (AutoMate)
    process-parameters                        │ writes changed files only
    (HBS_LEGACY_BRIDGE role)                  ▼
                                            ProcessParam share ──▶ legacy batch
```

## Pieces

| Piece | Where |
|---|---|
| Script | `tools/legacy-share-bridge/Sync-HbsProcessParameters.ps1` |
| Endpoint | `GET /api/v1/hbs/integrations/legacy/process-parameters`, service-account role `HBS_LEGACY_BRIDGE` only |
| Response | `{"generatedAt": "…", "files": [{"name": "…", "content": "…", "encoding": "…"}]}`: one entry per legacy file, content exactly as the legacy P038 screen wrote it |

The script validates the whole response before writing anything, writes only
files whose bytes changed (temp file then replace, previous copy kept as
`.bak`), refuses unexpected file names, and exits 1 on any problem so AutoMate
flags the run. A failed run leaves the last good files in place, which is the
same state the legacy batch had before.

## Setup (operations)

0. Create the settings ConfigMap with today's values first
   ([coexistence.md](coexistence.md#settings-configmap)). Until all four
   process values are set, the endpoint answers 503 and the bridge leaves the
   files alone.

1. Keycloak: a confidential client with the client-credentials grant and the
   `HBS_LEGACY_BRIDGE` role (team / SSO admins; not done by this repo).
2. On the batch host: save the client secret in a file only the AutoMate
   service user can read.
3. AutoMate task, before the invoicing jobs (B2031 / B2041):

   ```
   powershell -NoProfile -ExecutionPolicy Bypass -File Sync-HbsProcessParameters.ps1 ^
     -ApiBaseUrl https://<hbs-backend-host> ^
     -TokenUrl https://<sso-host>/auth/realms/<realm>/protocol/openid-connect/token ^
     -ClientId <client-id> -ClientSecretFile <path-to-secret-file> ^
     -TargetDirectory <ProcessParam directory the batch reads> ^
     -AllowedFile <file names listed below>
   ```

4. Test once with `-WhatIf`, which logs what would be written without
   writing.

## Files

Exactly as legacy `P038Action` wrote them (LF line endings, `windows-1252`,
the Windows batch host's default charset):

| File | Content | Read by |
|---|---|---|
| `HBSProcessingParameter.csv` | `B2031,<start>,<limit>` and `B2041,<start>,<limit>` lines (legacy defaults 0 / 9999999999 when unset) | B2031, B2041 |
| `Printer_names.txt` | `COPY_TO_PRINTER=<name>` / `SEND_TO_PRINTER=<name>`, no final newline; **left out until both printer names are set**, so blanks never reach B2075 | B2075 |
| `HBS_ALERT_FILE.txt` | the alert text, no newline; empty after Delete | the legacy web tier only (switched off) |

So `-AllowedFile HBSProcessingParameter.csv,Printer_names.txt,HBS_ALERT_FILE.txt`.
The target directory is the batch's `PROCESS_PARAMETER_FILE_PATH`
(`cfg_p_01/02_parameter_file_path`).

## Retiring it

Remove the AutoMate task and the Keycloak client once the jobs that read these
files (invoicing) are ported or the legacy batch is retired.
