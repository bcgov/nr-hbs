# HBS Scale Data API: guide for clients and software vendors

**Draft.** Values marked *(to be confirmed)* may change before the API opens.

The HBS Scale Data API replaces the FTP folders you use today to send scale
return files to HBS and to collect statements. You send the same XML files as
today, over HTTPS, and HBS processes each file while you wait: the response
tells you which documents were accepted and which have errors.

FTP closes for all clients on one cutover date, announced in advance. There is
no period where you can use both.

## What changes and what doesn't

| | FTP today | Scale Data API |
|---|---|---|
| File format | HBS scale-return XML (`HBS_Schema_V6_1b`, namespace `gov.bc.ca/forests/hbs/v3`) | **Unchanged.** The same file |
| How you send it | Upload to `<client>\HBS\inbound` | `POST` the file to the API |
| Login | FTP user name and password | Client id and secret, exchanged for a short-lived token |
| Result | After the next batch run; look it up on screen or call the help desk | In the response, usually within seconds, document by document |
| Statements | Collect PDF + XML from `<client>\HBS\outbound`; deleted after 31 days | List and download them from the API; available for 7 years |

## Endpoints

All paths are under `{API base URL}/api/v1/hbs/integrations/scale-data`, and
every call needs a token ([step 2](#2-get-a-token)).

| Method and path | Use it to | Replaces | Details |
|---|---|---|---|
| `POST /submissions` | Send one XML file and get its result | Uploading to `inbound` | [Step 3](#3-send-a-file) |
| `POST /submissions:validate` | Check a file without submitting it; nothing is stored | — | [Step 3](#3-send-a-file) |
| `GET /submissions/{transmissionId}` | Get a file's current result, errors included | Asking the help desk | [Step 4](#4-read-the-result) |
| `GET /submissions/{transmissionId}/documents?status=` | List a file's documents and their errors, page by page | — | [Step 4](#4-read-the-result) |
| `GET /submissions?updatedSince=&clientNumber=&status=` | List your files whose results changed since a time | — | [Step 4](#4-read-the-result) |
| `GET /statements?since=&clientNumber=` | List statements issued to you since a time | Checking `outbound` | [Step 5](#5-collect-statements) |
| `GET /statements/{transmissionId}.pdf` and `.xml` | Download a statement | Downloading from `outbound` | [Step 5](#5-collect-statements) |

`clientNumber` is needed only if you submit for more than one client; without
it, list calls cover all your clients. List results are newest first and come
in pages: when a response has `next`, call again with it to get the next page.

## 1. Get access

E-mail the HBS help desk with:

- the client numbers you submit files for (software vendors: every client you
  submit for);
- the person in your organization who owns the integration;
- your scale software's name and version.

You receive TEST credentials first: a **client id**, a **client secret** (sent
through a one-time link), a **token URL** and an **API base URL**. Production
credentials follow once your TEST run passes, and work from the cutover date.

Keep the secret in a secret store or in a file only your job's service account
can read. Never put it in source code or e-mail. Tell the help desk at once if
it may have leaked, or when the person who holds it leaves; secrets are also
rotated at least once a year.

## 2. Get a token

Exchange your client id and secret for an access token (OAuth 2.0 client
credentials). Tokens last a few minutes. Get a new one when the current one
expires, or whenever the API answers `401`.

```bash
TOKEN=$(curl -s -X POST "$TOKEN_URL" \
  -d grant_type=client_credentials \
  -d client_id="$CLIENT_ID" \
  -d client_secret="$CLIENT_SECRET" | jq -r .access_token)
```

```powershell
$Token = (Invoke-RestMethod -Method Post -Uri $TokenUrl -Body @{
  grant_type = 'client_credentials'; client_id = $ClientId; client_secret = $ClientSecret }).access_token
```

Send the token on every API call as `Authorization: Bearer <token>`.

## 3. Send a file

```http
POST {API base URL}/api/v1/hbs/integrations/scale-data/submissions
Authorization: Bearer <token>
Content-Type: application/xml
X-HBS-Client-Number: 00012345
X-HBS-File-Name: SITE42_20261029_0600.xml

<the XML file as the body>
```

| Header | Required | Meaning |
|---|---|---|
| `X-HBS-Client-Number` | if you submit for more than one client | The 8-digit client number the file is for (what the FTP folder name told us before). |
| `X-HBS-File-Name` | no | Your file name; shown to HBS staff and returned to you. |
| `Idempotency-Key` | no | Only if you want explicit control over retries; see [Sending a file again](#sending-a-file-again). |

One file per request, up to 10 MB *(to be confirmed)*. You can also send it as
`multipart/form-data` with the file in a part named `file`.

```bash
curl -s -X POST "$API/api/v1/hbs/integrations/scale-data/submissions" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/xml" \
  -H "X-HBS-Client-Number: 00012345" \
  -H "X-HBS-File-Name: $(basename "$FILE")" \
  --data-binary @"$FILE"
```

```powershell
Invoke-RestMethod -Method Post -Uri "$Api/api/v1/hbs/integrations/scale-data/submissions" `
  -Headers @{ Authorization = "Bearer $Token"; 'X-HBS-Client-Number' = '00012345'; 'X-HBS-File-Name' = (Split-Path $File -Leaf) } `
  -ContentType 'application/xml' -InFile $File
```

Allow up to 5 minutes for the response before treating it as a timeout. Most
files answer in seconds.

To check a file without submitting it, for example when testing a new release
of your software, `POST` it to `/submissions:validate` instead. You get the
same file checks and errors, and nothing is stored.

## 4. Read the result

Every validation error comes back in the response to your upload. There are
three outcomes.

**The file doesn't match the schema** (`422`, nothing is stored). Every problem
is listed with its line and column:

```json
{
  "code": "SCHEMA_INVALID",
  "message": "The file does not match HBS_Schema_V6_1b.",
  "errors": [
    { "level": "FILE", "line": 14, "column": 9, "message": "cvc-complex-type.2.4.a: Invalid content was found starting with element 'ScaleDate'. …" },
    { "level": "FILE", "line": 211, "column": 22, "message": "cvc-datatype-valid.1.2.1: '2026-13-01' is not a valid value for 'date'." }
  ],
  "correlationId": "…"
}
```

**The file is rejected as a whole** (`422`). It was received, but a file-level
check failed, so none of it was processed:

```json
{
  "code": "FILE_REJECTED",
  "message": "The file was received but could not be processed.",
  "transmissionId": 812346,
  "errors": [ { "level": "TRANSMISSION", "code": "FXA02", "message": "…" } ],
  "correlationId": "…"
}
```

**The file is processed** (`201 Created`, usually within seconds). Each batch
and document has its own result, and any that failed are listed in `errors`:

```json
{
  "transmissionId": 812345,
  "clientNumber": "00012345",
  "fileName": "SITE42_20261029_0600.xml",
  "receivedAt": "2026-10-29T06:00:12-07:00",
  "updatedAt": "2026-10-29T06:00:19-07:00",
  "status": "PROCESSED",
  "testFile": false,
  "duplicate": false,
  "batchCount": 3,
  "batchesRejected": 1,
  "documentCount": 214,
  "documents": { "ready": 209, "error": 4, "signatureFailed": 1 },
  "errors": [
    { "level": "BATCH", "code": "FBA…", "message": "…", "batch": "P", "batchNumber": "…" },
    { "level": "DOCUMENT", "code": "…", "message": "…", "batch": "W", "documentNumber": "…", "version": 1, "field": "…" },
    { "level": "DOCUMENT", "code": "…", "message": "Scaler signature could not be verified.", "batch": "W", "documentNumber": "…", "version": 1 }
  ],
  "errorsTruncated": false,
  "statusUrl": "/api/v1/hbs/integrations/scale-data/submissions/812345",
  "documentsUrl": "/api/v1/hbs/integrations/scale-data/submissions/812345/documents"
}
```

| Error level | Means | What to do |
|---|---|---|
| `FILE` | The XML doesn't match the schema. | Fix the file and send it again. |
| `TRANSMISSION` | The whole file was rejected. | Fix the file and send it again. |
| `BATCH` | That batch was rejected; the rest of the file was processed. | Resend the batch in a new file. |
| `DOCUMENT` | That document failed an edit or its signature check. | Correct it and send a replacement version. |

`code` is the HBS edit code; quote it to the help desk. `errors` is empty when
everything was accepted. Keep the `transmissionId`.

If a file has more than 500 document errors, the first 500 are returned,
`errorsTruncated` is `true`, and you page through the rest:

```http
GET {API base URL}/api/v1/hbs/integrations/scale-data/submissions/812345/documents?status=ERROR
Authorization: Bearer <token>
```

```json
{ "items": [ { "batch": "W", "documentNumber": "…", "version": 2, "status": "ERROR", "updatedAt": "…",
               "errors": [ { "code": "…", "message": "…", "field": "…" } ] } ],
  "next": "<cursor>" }
```

| File status | What it means | What to do |
|---|---|---|
| `PROCESSED` | Every batch and document has a result. | Act on `errors` (table above). |
| `QUEUED`, `PROCESSING` | Large file, or HBS was busy: answered `202 Accepted`. | Check `statusUrl` every 30 seconds, then every 5 minutes, until `PROCESSED`. It then returns the same body as a `201`, errors included. |
| `REJECTED` | The file could not be processed (answered `422`, code `FILE_REJECTED`). | Read the errors, fix the file and send it again. |

| Document status | Meaning |
|---|---|
| `READY` | Accepted. |
| `ERROR` | Failed one or more edits; see `errors`. |
| `SIGNATURE_FAILED` | The scaler's digital signature didn't verify. |
| `SUMMARIZED` | Accepted and included in a summary return for billing. |

**A document in error can become ready later.** Some edits fail only because
related data (for example the samples for a weigh slip) hasn't arrived yet.
When it arrives, in your next file or someone else's, HBS re-checks the
document. To pick up these changes, once an hour or once a day call:

```http
GET {API base URL}/api/v1/hbs/integrations/scale-data/submissions?updatedSince=<time of your last check>
Authorization: Bearer <token>
```

It lists every file whose results changed (optionally `&clientNumber=` and
`&status=`); read their documents again.

## 5. Collect statements

If you collect invoices and statements from your FTP `outbound` folder today:

```http
GET {API base URL}/api/v1/hbs/integrations/scale-data/statements?since=2026-10-01T00:00:00-07:00
Authorization: Bearer <token>
```

```json
{ "items": [ { "transmissionId": 90211, "issuedAt": "2026-10-28T19:02:44-07:00",
               "documentTypes": ["PSI", "VS"], "pdfUrl": "…", "xmlUrl": "…" } ],
  "next": "<cursor>" }
```

Download each item from its `pdfUrl` and `xmlUrl` with the same token. Save the
`issuedAt` of the last item you processed and pass it as `since` next time. If
`next` is present, more results are waiting: call again with `next`.

## Errors and retries

Every error has the same body. Quote the `correlationId` when you contact the
help desk.

```json
{ "code": "SCHEMA_INVALID", "message": "The file does not match HBS_Schema_V6_1b.",
  "errors": [ { "line": 14, "column": 9, "message": "…" } ], "correlationId": "…" }
```

| HTTP | Code | Retry? | What to do |
|---|---|---|---|
| `401` | — | yes, after a new token | Get a new token and try again. |
| `403` | `CLIENT_NOT_PERMITTED` | no | Your account isn't set up for that client number. Contact the help desk. |
| `409` | `IDEMPOTENCY_KEY_REUSED` | no | Only if you send `Idempotency-Key`: you reused a key for a different file. Use a new key. |
| `413` | `FILE_TOO_LARGE` | no | Split the file. |
| `422` | `SCHEMA_INVALID`, `VIRUS_DETECTED`, `FILE_REJECTED` | no | Fix the file; `errors` lists each problem. |
| `429` | — | yes | Too many requests (limit 60 a minute, *to be confirmed*). Wait for the `Retry-After` header. |
| `503` | `VIRUS_SCAN_UNAVAILABLE` | yes | Wait for `Retry-After`, then send the same file again. |
| `500` or no response | — | yes | Send the same file again, waiting 1, 2, 4, then 8 minutes. |

### Sending a file again

If you get no answer, or an error that says to retry, send the same file
again. It is always safe: HBS recognises a file it already has for that client
(the exact same bytes, within 30 days) and returns the original result with
`200 OK` and `"duplicate": true`, or `202` if it is still being processed.
Nothing is stored twice.

- A file that was `REJECTED` is processed again when you resend it, so you can
  resend after the help desk fixes a problem on HBS's side.
- A changed file (even only a new timestamp or different line endings) is a
  new file. So when you retry, resend the file you saved, byte for byte; don't
  have your software generate it again. If you correct a file, send it as you
  normally would.
- **Optional:** if your software already uses the `Idempotency-Key` header,
  send a new UUID per file and the same one on every retry. HBS then goes by
  the key instead of the file's contents: a new key is always processed, and
  the same key with a different file is refused with `409`.

## Moving from FTP

All clients move on the same cutover date. The cutover notice gives you the
TEST sign-off deadline, the time of the last FTP pickup and the cutover date.

1. **Request access** (step 1) and receive TEST credentials.
2. **Connect your software** to the TEST API: tokens, uploads, results, later
   changes and, if you use them, statements.
3. **Pass the TEST checklist** before the sign-off deadline, using files marked
   as test files:
   - [ ] A valid file returns `201` with per-document results.
   - [ ] A large file returns `202`, and your software checks back until
     `PROCESSED`.
   - [ ] A deliberately broken file returns `422` with the errors.
   - [ ] Sending the same file twice returns the same transmission id with
     `"duplicate": true`.
   - [ ] Your software shows document errors and picks up later changes with
     `updatedSince`.
   - [ ] An expired token is replaced automatically.
   - [ ] Statements list and download (if you collect them by FTP today).
4. **Receive PROD credentials.** They work from the cutover date.
5. **Before cutover**, upload your last FTP files before the last pickup time,
   and collect anything you want from `outbound` (or fetch it later from the
   API).
6. **From the cutover date**, send files only to the API. The FTP folders are
   closed.

If your integration stops working after cutover, you can upload files by hand
on the HBS website (Submit File of Detail Returns) with your Business BCeID
login while you fix it. Contact the help desk straight away.

## Support

HBS help desk: `FORHVAP.HBSHELP@gov.bc.ca`. Include your client number, the
`transmissionId` and the `correlationId` from any error.
