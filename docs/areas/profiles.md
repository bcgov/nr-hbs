# Profiles area (legacy `/cpm/*`)

Files:

- Backend: `backend/src/main/java/ca/bc/gov/nrs/hbs/api/catalog/ProfilesCatalog.java` (client, scale site, population and scaler profiles) and `ProfilesBillingCatalog.java` (mark holder, statement delivery and cruise-based billing profiles)
- Frontend: `frontend/src/screens/areas/profiles.ts`

Legacy paths below are relative to `hba-archive/hbs/trunk/source/ear/`:

- `E` = `hbs-ejb/src/main/java/ca/bc/gov/mof/hbs/cpm`
- `V` = `hbs-view/src/main/java/ca/bc/gov/mof/hbs/presentation/cpm`
- `DDL` = `nr-mof-db/scripts/THE`

## Screen map

| Legacy | Legacy title | New screen id(s) | Query / command ids | Ported from |
|---|---|---|---|---|
| P300 | Client Profile Menu | not carried over | – | It is an orphan menu that no tab links to, and its links are broken. The SideNav replaces it. |
| P310 | Search for a Client Profile | `client-profile-search` | `profiles.client.search` | `V/P310Action`, `legacy.cli.ClientReader.findClientLocation` (CLIENT_LOCATION + FOREST_CLIENT) |
| P311 | Client Profile | `client-profile` | `profiles.client.detail`. Its tables use `profiles.scaleSites.search`, `profiles.markHolders.search`, `profiles.population.search` and `profiles.delivery.search` | `V/ClientProfileTag`. Counts come from `findTPSubmitterCountByCriteria` (HBS2R321), `findMHPCountByCriteria` (HBS3R341), `findProfileCountByCriteria` and `findDeliveryProfileCountByCriteria` (HBS3R371). The legacy count links are replaced by inline tables of the linked profiles. |
| P320 / P321 | Search for / List of Scale Site Profiles | `scale-site-profiles` (Print = HBS2R321) | `profiles.scaleSites.search` | `DDL/PROCEDURES/V7.01526__HBS2R321.sql` (the TPS ∪ SSP SELECT); search types from `V/ScaleSiteConfigurationsTag.java:96-180` |
| P322 | Update Scale Site Profile | `scale-site-profile-update` | `profiles.scaleSites.record`, `profiles.scaleSites.update` → `HBS_STORE_TRA_PART_SUBMITTER` | `V/P322Action`, `E/ClientProfileManagerBean.updateTradingPartnerSubmitter` |
| P323 | Add Scale Site Profile | `scale-site-profile-add` | `profiles.scaleSites.create` → `HBS_CREATE_TRA_PART_SUBMITTER` (sequence `TRADING_PARTNER_SUBMITTER_SEQ`, type `TPSS`) | `V/P323Action`, `createAndStoreTradingPartnerSubmitter` |
| P330 / P331 | Search for / List of Population Profiles | `population-profiles` (Print = HBS3R331) | `profiles.population.search` | `DDL/PROCEDURES/V7.01563__HBS3R331.sql`, `E/PopulationProfileQuery` |
| P332 | Update Population Profile | `population-profile` (view + Delete) | `profiles.population.record`, `profiles.population.delete` → `HBS_REMOVE_POPULATION_PROFILE` | `V/P332Action`. The expiry-date update is **not ported** (see below). |
| P333 | Add Population Profile | not carried over | – | Needs a server-side ratio-schedule calculation (see below). |
| P340 / P341 | Search for / List of Mark Holder Profiles | `mark-holder-profiles` (Print = HBS3R341) | `profiles.markHolders.search` | `DDL/PROCEDURES/V7.01564__HBS3R341.sql` (the timber-mark search joins MARK_BILLED_CLI), `V/P340Form.doQuery` |
| P342 | Update Mark Holder Profile | `mark-holder-profile` (view), `mark-holder-profile-update` | `profiles.markHolders.record`, `profiles.markHolders.update` → `HBS_STORE_HBS_MARK_PROFILE` | `V/P342Action`, `updateMarkHolderProfile` |
| P343 | Add Mark Holder Profile | `mark-holder-profile-add` | `profiles.markHolders.create` → `HBS_CREATE_HBS_MARK_PROFILE` (`HBS_MARK_HOLDER_PROFILE_SEQ`) | `V/P343Action` |
| P344 | Confirm Mark Holder Profile Delete | The Delete action on `mark-holder-profile` (confirm modal) | `profiles.markHolders.delete` → `HBS_REMOVE_HBS_MARK_PROFILE` | `V/P344Action` |
| P350–P353 | Summarization Frequency | not carried over | – | These are unwired static mock-ups with no Struts action. Mark Holder Profiles (P34x) superseded them. |
| P361, P330S001, P331S001 | Mock-ups | not carried over | – | They are static prototypes. |
| P360 | Search for Scaler Profile | `scaler-profile-search` (by licence), `scaler-expiring-keys` (by AK expiry) | `profiles.scalers.search`, `profiles.scalers.expiring` | `V/P360Action`. Expiry search from `E/ClientProfileManagerQuery.selectScalerToUpdate` |
| P364 | Select Scaler to Update | `scaler-expiring-keys` | `profiles.scalers.expiring` | `E/ClientProfileManagerQuery.selectScalerToUpdate` (now uses bound parameters) |
| P362 | Update Scaler Profile | `scaler-profile` (**read-only**: licence, current link, key history) | `profiles.scalers.detail`, `profiles.scalers.keyHistory` | `V/P362Action`, `E/HbsScalerAuthKeyQuery.selectByCriteria`. The encrypted key is never selected. The key request (Submit) is **not ported**. |
| P363 | Authentication Key Confirmation | not carried over | – | It displays the plaintext key. This is a security pinch point (see below). |
| P365 | No active Scaler Profile link | not carried over | – | Only the self-service key request used it. The `scaler-profile-search` notes banner carries the "contact your District Scaling Supervisor" text. |
| P370 / P371 | Search for / List of Statement Delivery Profiles | `delivery-profiles` (Print = HBS3R371) | `profiles.delivery.search` | `DDL/PROCEDURES/V7.01565__HBS3R371.sql`, `V/P370Form.doQuery` |
| P372 | Update Statement Delivery Profile | `delivery-profile` (view), `delivery-profile-update` | `profiles.delivery.record`, `profiles.delivery.update` → `HBS_STORE_HBS_CLI_DEL_PROFILE` | `V/P372Action` |
| P373 | Add Statement Delivery Profile | `delivery-profile-add` | `profiles.delivery.create` → `HBS_CREATE_HBS_CLI_DEL_PROFILE` (`HBS_CLI_DEL_PROFILE_SEQ`) | `V/P373Action` |
| P374 | Confirm Delivery Profile Delete | The Delete action on `delivery-profile` | `profiles.delivery.delete` → `HBS_REMOVE_HBS_CLI_DEL_PROFILE` | `V/P374Action` |
| P380 / P381 | Search for / List of Cruise Based Billing Profiles | `cruise-based-profiles` | `profiles.cruiseBased.search` | `DDL/PROCEDURES/V7.01566__HBS3R380.sql`. The REF-CURSOR SELECT is ported; no Jasper report exists for it. |
| P385 | View Cruise Based Billing Profile | `cruise-based-profile` | `profiles.cruiseBased.detail`, `profiles.cruiseBased.ratios` | `V/P385Action` (APP_WORKSHEET, MARK_BILLED_CLI, TIMBER_MARK, `findCruiseBasedSpGrRatiosByCriteria`) |
| P383 | Update Cruise Based Billing Profile | `cruise-based-ratios` (grid + row Delete), `cruise-based-ratio-add`, `cruise-based-ratio-update` | `profiles.cruiseBased.ratios`, `profiles.cruiseBased.ratioRecord`, `profiles.cruiseBased.ratio.create` / `.update` / `.delete` → `HBS_CREATE_` / `HBS_STORE_` / `HBS_REMOVE_CB_SP_GRD_RATIO` | `V/P383Action`. The legacy screen deleted every row and re-inserted them on Save. The new screens edit one row at a time. |
| P801 / P802 | Client Lookup popup | replaced | – | The `client` field type opens the shared ClientSearchModal (nr-forest-client-api — [forest-client-integration.md](../forest-client-integration.md)); picking a location also fills the `Loc` field. |

## Access (fixes of legacy defects)

- **Reads.** All profile reads use `PROFILES_VIEW` and a `clientScope`. Industry users only see rows for their active client. In the legacy app this was a UI-only restriction or was missing entirely: P340 timber-mark search, P385 via URL, and P311 `SearchLoc`. Client names of individuals are FOI-severed.
- **Writes.** In the legacy app most profile writes had no server-side role check: P332/P333, P342–P344, P372–P374 and P383. The new commands require a capability:
  - Scale site: `SCALE_SITE_ADMIN`
  - Mark holder, delivery and cruise-based ratios: `PROFILE_BILLING_ADMIN`
  - Population delete: `SAMPLING_ADMIN`
- **Scaler data.**
  - `profiles.scalers.expiring` (P364) is restricted to `SCALE_SITE_ADMIN` (HBS_SCALE_ADMIN).
  - P360 search, P362 detail and key history use `SCALER_PROFILE_EDIT` with a client fence. A CLI_SCALER only sees scaler clients whose keys are linked to HBS users (`HBS_USER.CLIENT_NUMBER`) of their own active client.
  - Exact "own key only" matching (`HBS_SCALER_AUTH_KEY.USER_ID = caller`) needs a viewer user-id bind (for example `:hbsViewerUserId`), which `QueryService` does not provide yet. This is listed below.
- **Industry report prompts.** The Print reports (HBS2R321, HBS3R331, HBS3R341, HBS3R371) take prompts from the criteria. Whether the report endpoint applies the client fence for industry users depends on the shared report service.

## Legacy logic not yet ported

**1. Scaler authentication keys (P362 Submit, P363). This is a security pinch point. Do not port it as-is.**

- The key is requested by the user, a check digit is appended, and it is stored encrypted in `HBS_SCALER_AUTH_KEY.ENCRYPTED_AUTHENTICATION_KEY`.
- Code locations:
  - Requested key + check digit: `V/P362Action.java:244-246` (`ca.bc.gov.mof.hbs.util.math.CheckDigit`).
  - Encryption: `DBMS_CRYPTO.encrypt(..., 4096+256+1, '<key>')` in `E/HbsScalerAuthKeyQuery.java:28-48` (create/store). The legacy code builds the key literal into the SQL text.
  - Decryption for P363 display: `E/HbsScalerAuthKeyQuery.java:66-72`.
  - The DBMS_CRYPTO key is the WebADE preference `dbms-crypto-key`. It is read in `hbs-ejb/.../infrastructure/signature/DigitalSignatureService.java:74`.
  - Aging days: `HBSConfiguration.getScalerAuthenticationKeyAgingDays()`, used at `V/P362Action.java:72`.
  - Managed vs self-service flow, expire-active / overwrite-future / insert-new logic, validations (7–9 chars, upper-case alphanumeric, From date window) and the P365 redirect: `V/P362Action.java`.
  - Writes: `E/ClientProfileManagerBean.createAndStoreHbsScalerAuthKey` / `updateHbsScalerAuthKey`.
- The rebuild needs:
  - a decision on key management, because the key cannot come from WebADE any more;
  - a dedicated service that binds the crypto key, never puts it in SQL text, and never returns the plaintext key except once at creation;
  - a viewer user-id bind, so a CLI_SCALER can only act on their own active key.

**2. Population profile add / expiry update (P333, P332 Save).**

- `HBS_CREATE_` / `HBS_STORE_POPULATION_PROFILE` need `NEXT_RATIO_STMT_DATE`, `LAST_RATIO_STMT_DATE` and `START_RATIO_STMT_DATE`. Legacy computes them in `V/P333Action.java:110-170`:
  - next = end of the effective month (MTH), the 15th (BWK) or the same day (DLY), at 23:59:59;
  - last = the effective date;
  - start = today 00:00.
- The batch selects profiles with `next_ratio_stmt_date < runDate` (`E/PopulationProfileQuery.java:595`). The generic command's day-precision DATE args would truncate the time to 00:00, so the ratio statement would run a day early.
- The validations also need porting:
  - P333 (`V/P333Action`): effective date is the 1st of the month and not before the current month; expiry is the last day of the month; years 2000–2099; no overlap via `findOLPopulationProfileCount`; population and year exist.
  - P332 (`V/P332Action`): expiry is not before the next ratio statement date; no overlap.
- Implement this as a dedicated service, or add a TIMESTAMP arg type to `CommandDefinition`.

**3. Date-range and business validations the procs don't enforce.** The generic commands call the table-API procs directly, so the following checks are not run yet:

- **P322 / P323** (`V/P322Action`, `V/P323Action`):
  - scale site exists;
  - the caller's district or site data domain contains the site (`DacHelper.isScaleSiteAssociatedWithOrgUnit`);
  - a Detail record must have a blank client and location; a Summary record requires a TRADING_PARTNER client;
  - expiry is on or after the effective date;
  - the effective date doesn't fall inside an existing range, and the expiry is before the next range's effective date (`getNextTradingPartnerSubmitter`).
- **P342 / P343** (`V/P342Action`, `V/P343Action`):
  - the effective date is in the future (P342 also refuses update and delete otherwise) and is the 1st of the month;
  - the expiry date is the last day of the month and not before today;
  - no overlap (`getNextMarkHolderProfile`, `findMarkHolderProfileCountByCriteria`).
- **P372 / P373** (`V/P372Action`, `V/P373Action`):
  - no overlap (`getNextClientDeliveryProfile`);
  - the effective date is in the future (add);
  - E-mail or FTP delivery requires location-domain users with an active document-receiver role.
- **P383** (`V/P383Action.java`):
  - the species, product and grade combination is valid;
  - a coniferous species must be on the appraisal worksheet;
  - a deciduous species requires a deciduous volume;
  - appraisal method `I` requires grade 7 or 8;
  - 0 < m3/Ha < 1000;
  - no duplicate species and grade.

**4. Update commands re-send immutable columns.**

- The `HBS_STORE_*` procs overwrite every column. The update forms therefore post the loaded row back, including key, client and entry audit values (shown read-only).
- `ENTRY_TIMESTAMP` and `FORCED_SUMMARIZATION_DATE` lose their time of day.
- A dedicated service that re-reads the row server-side and only applies the editable fields would close the tampering and precision gap.

**5. Smaller gaps.**

- The P320 Trading Partner search's legacy pre-checks ("Client has not been setup as trading partner", "no current scale site profiles") are not run.
- The P364 "In my Org Unit" checkbox is replaced by an org unit picker, because there is no viewer org-unit bind.
- P383/P385 "Appraised Timber Profile" and stumpage-rate panels come from `OpqHelper.getTimberMarkDatas`. The Timber Mark Information query (Queries area) covers them.
- P385's mark sale-method and status descriptions are not shown.

## Open questions / capability gaps

- The legacy P331 links and P332 were gated on HBS_SMP_ADMIN only, but `SAMPLING_ADMIN` also grants CLI_SMP_ADMIN. There is no ministry-only sampling capability, and commands have no client fence.
- Cruise-based profile writes were legacy HBS_RATE_ADMIN, HBS_BILL_ADMIN or HBS_SCALE_ADMIN. No existing capability covers all three, so `PROFILE_BILLING_ADMIN` (HBS_BILL_ADMIN) is used.
- P342 is now shown to everyone with `PROFILES_VIEW`, with update and delete for `PROFILE_BILLING_ADMIN`. The legacy app had no grant at all for P344.
