# Role rationalization (proposal)

**Status: proposal, not implemented.** The app today carries the legacy
WebADE roles over 1:1 (see [roles-and-security.md](roles-and-security.md)).
This page lists what each of those roles actually does in the new app, and
proposes a smaller set of FAM roles to replace them. Nothing here changes
until the business agrees to the open decisions at the end.

For every page and what each role can do on it, see the generated
[access matrix](access-matrix.md); `access-matrix-proposed.xlsx` shows the
same for the proposed roles below and what each current holder gains or loses.

Sources: `security/HbsRoles.java`, `security/HbsAuthorities.java`,
`query/Capability.java`, `summaryreturns/SummaryButtons.java`,
`detailreturns/DetailAuthority.java`.

## Current roles (legacy, carried over 1:1)

28 person roles plus one service account.

### Ministry (IDIR)

| Role | What it does |
|---|---|
| `HBS_MOF_USER` | Base ministry role. Read access to everything, including ministry-only views (queries, returns, rating, billing, profiles, scale control). |
| `HBS_MOF_ADMIN` | User administration (legacy User Services; now only User Data Domains). |
| `HBS_MOF_MGR` | Approved account requests. Also manages user domains **and can generate final bills**, which is an odd grant for a user manager. |
| `HBS_HELP_DESK` | **Nothing beyond the base role.** No capability references it. |
| `HBS_DTL_DATA_ENT` | Enters and edits detail returns, submits XML, edits batch slips. |
| `HBS_MOF_SCALER` | Ministry check scaler. Enters and edits detail returns and submits XML. Same as `HBS_DTL_DATA_ENT` except for batch slips. |
| `HBS_SUMM_DATA_CTL` | Enters and edits summary returns, edits batch slips. |
| `HBS_SUMM_DATA_CORR` | Edits summary returns and can cancel or replace invoiced returns, plus hold, release and discard. |
| `HBS_SCALE_ADMIN` | Scale sites, scale control, anomalies, scaler profiles, field-scaled decks, plus detail and summary edit, XML and batch slips. |
| `HBS_SMP_ADMIN` | Sample plan administration. |
| `HBS_RATE_ADMIN` | District and override rates, override rules. |
| `HBS_WRA_ADMIN` | Waste/residue rates. |
| `HBS_BILL_ADMIN` | Billing and final billing admin, profile billing, summary-return admin, field-scaled decks, final-bill generation, requesting rate corrections, **and approving detail returns**. |
| `HBS_INV_CORR_APP` | Approves invoice and rate corrections (district-scoped). |
| `HBS_SUPER_MGR` | Billing and final billing admin, field-scaled decks, and **forced billing** (the only role that can force billing). |
| `HBS_PROD_CTL` | Production control (batch), batch slips, cache admin. |
| `HBS_DISABLED_BYPASS` | Uses the app while it is administratively disabled. **The new app has no disabled mode, so nothing enforces this.** |

### Forest industry clients (BCeID, granted per forest client)

| Role | What it does |
|---|---|
| `HBS_CLI_USER` | Base client role. Read-only view of the client's own data. |
| `HBS_CLI_ADMIN` | Manages user domains for the client's users. |
| `HBS_CLI_SITE_ADMIN` | Detail and summary edit, XML submit, anomaly edit, site data domains. |
| `HBS_CLI_SCALER` | Same as `HBS_CLI_SITE_ADMIN`, plus editing scaler profiles. |
| `HBS_CLI_CRUISE_ADMIN` | Summary edit, plus hold and discard on area-based (cruise-based) returns for marks the client holds. |
| `HBS_CLI_SMP_ADMIN` | Sample plan administration for the client. |
| `HBS_CLI_DOC_RCVR` | Only adds an "Associated Locations" filter on statement transmissions. Otherwise read-only. |

### Other industry (BCeID, granted per client)

| Role | What it does |
|---|---|
| `HBS_SPC_USER` | Base role for agents and vendors. Can view scale returns. |
| `HBS_SPC_ADMIN` | Manages user domains for the organization. |
| `HBS_SPC_SUBM_AGNT` | Data submission agent: summary edit and XML submit. |
| `HBS_SPC_SFTWR_VNDR` | Scale-software vendor: XML submit and software-use view. |

### Service account

| Role | What it does |
|---|---|
| `HBS_LEGACY_BRIDGE` | Legacy share bridge only, confined to `/api/v1/hbs/integrations/legacy/**`. No person holds it. |

`HBS_SUBMISSION_API` (November 2026) is a second service role, for the scale
data API accounts that replace FTP ([roles-and-security.md](roles-and-security.md#service-accounts-scale-data-api)).
It is new, held by no person, and outside this consolidation; the counts below
leave it out.

## Who actually holds what (production grants)

Source: a WebADE extract of HBS grants (`EUSER` × `EAUTHORIZATION` ×
`ORGANIZATION`), shared as `HBS-roles.xlsx`. Only enabled, non-deleted grants
are counted: **16,249 grants held by 5,236 people** (1,922 IDIR, 3,314
Business BCeID). Another 10,093 grants are marked deleted and should not be
migrated.

| Role | People | Role | People |
|---|---|---|---|
| `MOF_USER` | 1,884 | `CLI_USER` | 3,335 |
| `HBS_SUMM_DATA_CORR` | 451 | `CLI_ADMIN` | 1,956 |
| `HBS_SUMM_DATA_CTL` | 421 | `CLI_SITE_ADMIN` | 1,431 |
| `HBS_SCALE_ADMIN` | 279 | `CLI_DOC_RCVR` | 1,199 |
| `MOF_ADMIN` | 274 | `CLI_SCALER` | 977 |
| `HBS_DTL_DATA_ENT` | 248 | `CLI_SMP_ADMIN` | 440 |
| `MOF_SCALER` | 154 | `CLI_CRUISE_ADMIN` | 405 |
| `HBS_INV_CORR_APP` | 130 | `SPC_USER` | 5 |
| `HBS_SMP_ADMIN` | 123 | `SPC_SUBM_AGNT` | 5 |
| `HBS_WRA_ADMIN` | 123 | `SPC_ADMIN` | **0** |
| `HBS_BILL_ADMIN` | 121 | `SPC_SFTWR_VNDR` | **0** |
| `HBS_RATE_ADMIN` | 81 | | |
| `HBS_PROD_CTL` | 12 | | |
| `HBS_DISABLED_BYPASS` | 11 | | |
| `HBS_HELP_DESK` | 10 | | |
| `HBS_SUPER_MGR` | 9 | | |
| `MOF_MGR` | 9 | | |

What the data says:

- **Base roles are pure overhead.** 99–100% of every other role's holders
  also hold `MOF_USER` or `CLI_USER`. 1,557 ministry users and 162 industry
  users hold only the base role.
- **Two roles have nobody in them.** `SPC_ADMIN` and `SPC_SFTWR_VNDR`.
- **The five smallest ministry roles are one team.** `HELP_DESK`, `PROD_CTL`,
  `SUPER_MGR`, `MOF_MGR` and `DISABLED_BYPASS` are held by 21 people in
  total. 8 of the 9 `SUPER_MGR` holders also hold `MOF_MGR`, and all 9
  `PROD_CTL`/`HELP_DESK` overlaps are the same people.
- **Summary control and correction are held together.** 95% of
  `SUMM_DATA_CTL` holders also hold `SUMM_DATA_CORR`; merging them gives the
  correction right to only 19 more people.
- **Sampling admins are scaling admins.** 96% of `HBS_SMP_ADMIN` holders
  also hold `HBS_SCALE_ADMIN`.
- **Separation of duties on billing is thin.** 101 of the 150 people with
  `BILL_ADMIN` or `INV_CORR_APP` hold both.
- **Rate and waste rates are different people.** Of 148 holders, 67 hold only
  `WRA_ADMIN` and 25 only `RATE_ADMIN`. Merging would hand stumpage-rate
  maintenance to 67 waste-rate staff.
- **Cruise billing is its own population.** 183 people hold
  `CLI_CRUISE_ADMIN` without any other returns role; only 61 hold all three
  industry returns roles.
- **Client sampling is separate too.** 1,129 site administrators don't hold
  `CLI_SMP_ADMIN`.

"Gains" below are people who would get a right they don't hold today.
Industry figures are per person per client.

## Proposed roles (29 → 16)

Principles:

1. **No separate base roles.** Every role includes read access for its user
   type, so nobody needs a base role plus a functional role. `ANY_USER` becomes
   "holds any recognised HBS role"; `MINISTRY` becomes "holds any ministry role".
2. **Merge roles the same people already hold together**, and roles whose
   rights are nearly identical.
3. **Delete roles that nobody holds or that do nothing.**
4. **Keep a split where the grants show distinct groups of people**, and
   where money moves (billing work vs approval).

### Ministry (IDIR): 17 → 9

| New role | Replaces | Does | Gains |
|---|---|---|---|
| `HBS_VIEWER` | `MOF_USER`, `HELP_DESK` | Read-only, all ministry views | none (`HELP_DESK` grants nothing) |
| `HBS_RETURNS` | `DTL_DATA_ENT`, `MOF_SCALER`, `SUMM_DATA_CTL`, `SUMM_DATA_CORR` | Enter, edit and correct detail and summary returns; cancel/replace, hold, release, discard; XML; batch slips | 227 gain detail entry; 55 gain summary correction |
| `HBS_SCALING_ADMIN` | `SCALE_ADMIN`, `SMP_ADMIN` | Scale sites, scale control, anomalies, scaler profiles, field-scaled decks, sampling plans | 160 gain sampling plans |
| `HBS_RATE_ADMIN` | `RATE_ADMIN` | District and override rates, override rules | none |
| `HBS_WASTE_RATE_ADMIN` | `WRA_ADMIN` | Waste/residue rates | none |
| `HBS_BILLING_ADMIN` | `BILL_ADMIN` | Billing, final bills, rate-correction requests, detail-return approval | none |
| `HBS_BILLING_APPROVER` | `INV_CORR_APP` | Approve invoice and rate corrections | none |
| `HBS_SYSTEM_MANAGER` | `SUPER_MGR`, `MOF_MGR`, `PROD_CTL`, `DISABLED_BYPASS` | Forced billing, final-bill generation, production control, cache, maintenance bypass | 17 of 21 (the same support team) |
| `HBS_USER_ADMIN` | `MOF_ADMIN` | User data domains (account approval is now FAM's job) | none |

### Industry (BCeID, granted per client): 11 → 6

| New role | Replaces | Does | Gains |
|---|---|---|---|
| `HBS_CLIENT_VIEWER` | `CLI_USER`, `CLI_DOC_RCVR` | Read-only for own client. The "Associated Locations" filter becomes available to everyone | a search filter |
| `HBS_CLIENT_ADMIN` | `CLI_ADMIN`, `SPC_ADMIN` | User domains for own organization | none (`SPC_ADMIN` is empty) |
| `HBS_CLIENT_RETURNS` | `CLI_SITE_ADMIN`, `CLI_SCALER` | Returns entry, XML, anomalies, site domains, scaler profiles | 980 site admins gain scaler-profile edits (own client only) |
| `HBS_CLIENT_CRUISE` | `CLI_CRUISE_ADMIN` | Summary edit, hold and discard on cruise-based returns | none |
| `HBS_CLIENT_SAMPLING` | `CLI_SMP_ADMIN` | Sample plans for own client | none |
| `HBS_SUBMISSION_AGENT` | `SPC_USER`, `SPC_SUBM_AGNT`, `SPC_SFTWR_VNDR` | View returns, summary edit, XML submit, software-use view | none (all 5 agents hold both; vendor role is empty) |

Client-scoped roles keep FAM's per-client naming
(`HBS_CLIENT_RETURNS_FOREST_CLIENT-00012345`), and the client fence and FOI
severing are unchanged.

### Service account: 1 → 1

| New role | Replaces | Does |
|---|---|---|
| `HBS_LEGACY_BRIDGE` | (same) | Unchanged |

9 ministry + 6 industry + 1 service = **16 roles**, down from 29.

### Further cuts the data would allow (not recommended)

| Merge | Saves | Cost |
|---|---|---|
| `HBS_RATE_ADMIN` + `HBS_WASTE_RATE_ADMIN` | 1 | 67 waste-rate staff gain stumpage rates |
| `HBS_BILLING_ADMIN` + `HBS_BILLING_APPROVER` | 1 | Ends separation of duties for 49 people who have it today |
| `HBS_CLIENT_RETURNS` + `HBS_CLIENT_CRUISE` | 1 | 183 cruise-only users gain detail returns and XML; 835 site admins gain cruise hold/discard |
| `HBS_CLIENT_RETURNS` + `HBS_CLIENT_SAMPLING` | 1 | 1,129 site admins gain sample plans |

## Open decisions

- **Returns merge.** `HBS_RETURNS` gives summary correction (cancel/replace
  invoiced returns) to 55 people who only enter returns today. If that's
  unacceptable, keep `HBS_RETURNS_CORRECTOR` separate (+1 role, 19 of whom
  would still need both).
- **Scaling + sampling.** 160 scaling admins would gain sampling-plan
  maintenance.
- **System manager.** Forced billing moves into a role that also does
  production control. All 9 people who force billing today are in that team.
- **Detail-return approval** stays with `HBS_BILLING_ADMIN` (today's
  behaviour). The earlier draft moved it to an approver role.
- **Software vendors.** Nobody holds `SPC_SFTWR_VNDR`. Folding it into
  `HBS_SUBMISSION_AGENT` gives a future vendor summary edit as well; confirm
  that is acceptable or drop vendor access entirely.
- **Migration scope.** Migrate only the 16,249 active grants; 252 IDIR users
  hold roles in more than one ministry org unit, which collapse to one FAM
  grant each.
- **Traceability.** The 1:1 naming was chosen so `hbs_action_lnk.sql` stays the
  source of truth. Renaming means keeping the "Replaces" columns above as the
  legacy → new mapping, and using it for the WebADE → FAM cut-over.

## What changes in code if adopted

- `backend/.../security/HbsRoles.java`: new role constants and sets
- `backend/.../security/HbsAuthorities.java` and `query/Capability.java`: the
  capability matrix
- `frontend/src/routes/access.ts` and `frontend/src/context/auth/types.ts`:
  the SPA mirror and role labels
- Direct role checks in `summaryreturns/SummaryButtons.java`,
  `detailreturns/DetailAuthority.java`, `detailreturns/command/BulkCommand.java`,
  `sampling/SamplingAccess.java`, `scalecontrol/*` and `admin/UserDataDomainGuard.java`
- [roles-and-security.md](roles-and-security.md): role table, capability
  matrix and FAM setup checklist
