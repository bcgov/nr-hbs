# Billing and Administration areas

Legacy source root: `hba-archive/hbs/trunk/source/` (abbreviated below as `ear/…`).
DB DDL: `nr-mof-db/scripts/THE/`.

Files:

- Backend: `backend/src/main/java/ca/bc/gov/nrs/hbs/api/catalog/BillingCatalog.java`,
  `backend/src/main/java/ca/bc/gov/nrs/hbs/api/catalog/AdminCatalog.java`,
  `backend/src/main/java/ca/bc/gov/nrs/hbs/api/admin/UserDataDomainGuard.java` (client fence for the
  `admin.userDataDomains.*` commands)
- Frontend: `frontend/src/screens/areas/billing.ts`, `frontend/src/screens/areas/admin.ts`

## Billing (legacy Billing tab, P800, `/ivs/*`)

| Legacy screen | New screen id | Query / command ids | Ported from |
|---|---|---|---|
| P800 Billing Management Menu | Billing area SideNav (no page) | — | `ivs/P800.jsp`. The profile links go to the Profiles area screens. |
| P811 / P812 Piece Scale Tally Summarization (Force Next … Date) | `force-piece-summarization` | `billing.forceSummarization.pieceProfiles`; row action `billing.markHolderProfile.forceSummarizationDate` (`HBS_STORE_HBS_MARK_PROFILE`) | `ear/hbs-ejb/…/cpm/MarkHolderProfileQuery.java` (`selectByCriteria`, `selectCountByCriteria`); `ear/hbs-view/…/ivs/config/P811Action.java` |
| P813 / P814 Weigh Slip Summarization | `force-weigh-slip-summarization` | `billing.forceSummarization.weighSlipProfiles`; same command | as above, `P813Action.java` |
| P823 / P824 Force Ratio Computation | `force-ratio-computation` | `billing.forceRatioComputation.profiles`; row action `billing.populationProfile.forceRatioComputationDate` (`HBS_STORE_POPULATION_PROFILE`) | `ear/hbs-ejb/…/cpm/PopulationProfileQuery.java` (`selectByRClientCriteria`); `P823Action.java` |
| P828 Configure Final Bill Status Summary + P829 Final Bill Status Summary | `final-bill-status` | `billing.finalBillStatus.populations` | `PROCEDURES/V7.01585__HBS3R829.sql` (population branch); `ProfileManagerBean.findFinalBillStatusSummary` |
| P830 Final Bill Status Summary (single population w/ stratum list) | `final-bill-status-strata` | `billing.finalBillStatus.strata` | `HBS3R829` stratum branch |
| P829/P830 Print (HBS3R829.rpt) | not offered | — | see open items: needs `FB_USERID` |
| P831 / P832 Search For / List of Unissued Final Bills | `unissued-final-bills` (Print List = Jasper `HBS3R832`) | `billing.unissuedFinalBills.search` | `PROCEDURES/V7.01587__HBS3R832.sql`; `P831Action.java`, `WeightScaleFinalBillResultsTag.java` |
| P833 / P834 Non-Renewable Tenures That Receive Final Bills | `final-bill-tenures` (Print = Jasper `HBS3R834`) | `billing.finalBillTenures.search` | `PROCEDURES/V7.01588__HBS3R834.sql`; `ear/hbs-ejb/…/ivs/config/FinalBillableDropoutMarkQuery.java` |
| P835 Add Non-Renewable Tenure That Receives Final Bills | `final-bill-tenure-add` | `billing.finalBillTenure.create` (`HBS_CREATE_FINAL_BILL_MARK`) | `P835Action.java` |
| P836 Update Non-Renewable Tenure That Receives Final Bills | `final-bill-tenure` (view + Delete) | `billing.finalBillTenures.detail`; `billing.finalBillTenure.remove` (`HBS_REMOVE_FINAL_BILL_MARK`) | `FinalBillableDropoutMarkQuery.selectByPK`; `P836Action.java` |
| P837 / P838 Non-Renewable Tenures Not Receiving Final Bills | `frozen-ratio-tenures` | `billing.frozenRatioTenures.search` | `ProfileManagerBean.findFrozenRatioRange` / `DropoutMarkLatestRatioQuery.selectByRatioCriteria` |
| P840 Search For Scale Returns From Field Scaled Decks | `field-scaled-decks` | `billing.fieldScaledDecks.mark` | `P840Action.java` (timber mark validation via MARK_BILLED_CLI) |
| P841 Field Scale Overview | `field-scale-overview` (sections Overview, Override Rules) | `billing.fieldScaledDecks.overview`, `billing.fieldScaledDecks.overrideRules` | `V7.01589__HBS3R841_A.sql`, `V7.01590__HBS3R841_B.sql`, `P840Action.getEarliestScaleDate/getLatestScaleDate` |
| P842 Sample Tallies From Field Scaled Decks | `field-scale-overview` section | `billing.fieldScaledDecks.sampleTallies` | `V7.01591__HBS3R842.sql` |
| P843 Weigh Slips From Field Scaled Decks | `field-scale-overview` sections (Weigh Slips, Red Tags) | `billing.fieldScaledDecks.weighSlips`, `billing.fieldScaledDecks.redTags` | `V7.01592__HBS3R843.sql` |
| P844 Log Tallies From Field Scaled Decks | `field-scale-overview` section | `billing.fieldScaledDecks.logTallies` | `V7.01593__HBS3R844.sql` |

Notes on the port:

- The capabilities follow the legacy grants: P811–P824 `BILLING_FORCE` (HBS_SUPER_MGR only, because the `config/*`
  wildcard doesn't walk up); P828–P830 `BILLING_VIEW`; P831–P838 `FINAL_BILL_ADMIN`; P840–P844 `FIELD_SCALED_DECKS`.
- Final Bill Status is client-fenced (`fb.client_number = :scopeClientNumber`). This matches HBS3R829's CLI branch
  (`POP.CLIENT_NUMBER IN (SELECT client_number FROM hbs_user WHERE user_id = …)`).
- The legacy radio buttons (P828 "All / Region-District / Single / Owned By", P831/P833/P837 "For This Timber Mark /
  … Owned By / …") became optional criteria that can be combined. When all criteria are left blank, the search returns
  everything, as the legacy "All" option did.
- Field scale deck: the legacy "blank = all decks" is `*` (the default) in the new screen. That way the deck id is
  always a bound parameter inside every UNION branch.
- The HBS3R841_A/842/843 "default ratio" is the constant `BillingCatalog.DEFAULT_RATIO = 1.24856`, taken from the
  legacy deploy token `cfg_p_default_ratio` (`ear/configure.properties`). It should become application config.
- HBS3R832 joins `weight_scl_smry_txn` with an outer join but never uses it. This is kept for parity with the Jasper
  report. If a summary return has more than one txn row, its estimated volume and value are multiplied (legacy
  behaviour).
- The force commands post the row's current values back, because the `HBS_STORE_*` procs rewrite every column. The
  date-typed arguments lose their time of day:
  - ENTRY_TIMESTAMP is stored at midnight.
  - NEXT_RATIO_STMT_DATE is stored at 00:00. Legacy stored 23:59:59.

### Billing — legacy logic not yet ported

1. **Force summarization validation and the "all" option** (`ear/hbs-view/…/ivs/config/P811Action.java` and
   `P813Action.java`, `validateForm` and the Yes branch):
   - Force year must be 2000–2099. The date must be on or after today.
   - Window from today by frequency: MTH ≤ 28 days, BWK ≤ 14, DLY ≤ 1. A December→January year rollover only.
   - "For All Client With Custom Mark Holder Profile" updates every matching profile AND the default profile
     (mhprof_id 1 for piece, 2 for weight) in one action.
   - The row command does not enforce any of this server-side. It needs a `BillingForceService`.
2. **Force ratio computation validation** (`P823Action.java`):
   - The profile must exist for Population/Sampling Year/Effective Date.
   - The same frequency window rule applies.
   - The force date must be > period start and < period end ("Force Date cannot be less than Ratio Computation Period
     Start" / "… greater than … Period End").
   - Legacy sets the time to 23:59:59.
   - "For The Global Profile" updates every profile active at the force date.
3. **Generate and Hold / Generate and Release final bills** (P829/P830):
   - Call path: `P828Action` → `ProfileManagerBean.generateFinalBill` → `HBS3R830(OUT status, year, pop, stratum,
     'HLD'|'RDY', userId)`.
   - The first argument is an OUT parameter, so `CommandService` can't call it.
   - Limited to HBS_BILL_ADMIN / MOF_MGR. Only rows with the eligible flag `*` are offered. Each row is re-queried
     afterwards.
4. **"Populations in Associated Districts"** (P828 radio): HBS3R829 filters by `HBS_USER_DIST_DATA_DOMAIN` for the
   logged-in user id.
   - Note: the legacy MOF branch (`strSelectStarMOF` + `Z.ratio_type_code` filter) effectively always limited ministry
     users to their own district domains.
   - Query definitions get no viewer user-id bind, so the new screen shows all populations to ministry users. Fixing
     this needs a `:hbsUserId` viewer bind in `QueryService` (shared file).
   - The P828 role-based defaults (all three suppress boxes for admin roles; CLI_SITE_ADMIN preset client) also need
     the user's roles. Today only "No eligible PSYMarks billed" defaults on.
5. **P831/P832 Hold Selected / Release Selected**: `ClientHelper.updateSummaryWeightScaleReturnHold/Release`
   (`ear/hbs-view/…/ivs/config/ClientHelper.java` ~line 1435). This is summary-return workflow (hold RDY/ERR, release
   HLD) and belongs with the summary-return services. The row link opens the summary return (`summary-return`).
6. **P835 add validations** (`P835Action.java`):
   - Population exists. Year is 4 digits. Population / Sampling Year combination exists.
   - Population ratio type is not Default ("An election for final billing cannot be made for a population where Ratio
     Type is Default.").
   - The timber mark is valid and non-renewable ("…must be exempt from final bills (must be non-renewable)").
   - No invoices or statements exist for the Pop/Yr/Mark.
   - No duplicate (the PK enforces this).
7. **P836 update and delete**:
   - Changing any key was delete + insert (`P836Action.java`). Today the user deletes and adds instead.
   - Delete is blocked when statements exist ("Cannot delete this Mark from final billing…"). The command doesn't check
     this.
8. **P842–P844 bulk actions** (`ear/hbs-view/…/ivs/config/P840Action.java`, `bulkActionForSampleTally`,
   `bulkActionForWeighSlip`, `bulkActionForLogTally`, `doCancelReturn`, `doClearFiledScaleFlag`):
   - "Cancel Issued Sample Tallies in Selected Strata": `DetailScaleDocumentHelper.cancel()` per active document.
   - "Clear Field Scale Flag for Weigh Slips in Selected Strata" / "…for Selected Log Tallies": hold, set
     `field_scale_ind = N`, then release.
   - The buttons show only when the mark's district is in the user's district data domain (the procs' `slect` column).
     That needs the viewer user id, so the column is omitted.
   - The P841 overview/override-rule count links (to detail search and override rule search) are also not reproduced.
9. **HBS3R829 Jasper print**: the report needs `FB_USERID`, and the proc does `SELECT … INTO` from HBS_USER, so a
   missing or foreign user fails. `HbsReportService` should fill `*_USERID` from the JWT, as it already does for
   `*_USERTYPE`, before the print is offered.

## Administration (User Services P009–P019, Cache, Miscellaneous)

| Legacy screen | New screen id | Query / command ids | Ported from / reason |
|---|---|---|---|
| P009 User Services Menu | Administration area SideNav | — | Menu only |
| P015 HBS User Search | `user-data-domains` | `admin.users.search` | `ear/hbs-ejb/…/uss/BuildUserQuery.java`. Industry admins are fenced to their client and user type (legacy `searchUsersForClients`). |
| P011 User List | `user-data-domains` (results) | `admin.users.search` | `uss/admin/userAccountList.jsp` columns. The WebADE merge and the "Pending Request" column are dropped (FAM). |
| P010 User Details (data-domain part) | `user-data-domain` | `admin.users.detail`, `admin.userDistricts.list`, `admin.userSites.list`, `admin.userLocations.list` | Enrolment and role fields: not carried over (FAM) |
| P019 Update Associated Districts | `user-data-domain` (Add) / `user-district-domains` (Remove) | `admin.userDataDomains.addDistrict` / `removeDistrict` (`HBS_CREATE_DIST_DATA_DOMAIN` / `HBS_REMOVE_DIST_DATA_DOMAIN`) | `uss/DistrictDataDomainQuery.java` |
| P017 / P018 Update Associated Sites (Ministry / Industry) | `user-data-domain` (Add) / `user-site-domains` (Remove) | `admin.userDataDomains.addSite` / `removeSite` (`HBS_CREATE_SITE_DATA_DOMAIN` / `HBS_REMOVE_SITE_DATA_DOMAIN`); `codes.admin.scaleSites` | `uss/SiteDataDomainQuery.java` |
| "P017" Update Associated Client Locations (AssociatedLocations.jsp) | `user-data-domain` (Add) / `user-location-domains` (Remove) | `admin.userDataDomains.addLocation` / `removeLocation` (`HBS_CREATE_LOCATION_DOMAIN` / `HBS_REMOVE_LOCATION_DOMAIN`) | `uss/LocationDomainQuery.java` |
| P010 Enrolment Request (new / update / activate / deactivate) | not carried over | — | Replaced by FAM: accounts, roles and activation are managed in FAM |
| P012 Request Email Sent | not carried over | — | FAM |
| Register (Restricted Client Administrator Verification, public) | not carried over | — | FAM onboarding. The legacy registration "key" was derivable from the client number (square roots of digits), so it was insecure. |
| P016 Review Requests | not carried over | — | FAM (no HBS_USER_REQUEST workflow) |
| User Request Details / User Id Confirmation / generic Confirmation | not carried over | — | FAM (accept, reject and confirm against WebADE plus IMG email) |
| Static help pages `hbs/uss/*.html` | not carried over | — | Describe the enrolment flow that FAM replaces |
| Cache admin (`/cache/*`, Harvest Cache Sub System) | not carried over | — | The per-JVM legacy cache is replaced by Spring Cache. The legacy pages were read-only key/value dumps, unlinked, for PROD_CTL only. Use actuator cache metrics instead. |
| about (About HBS) | not carried over | — | Static external links. Belongs in the app shell/footer help links, not a screen. |
| hbs_info (diagnostic info) | not carried over | — | An unauthenticated developer page that leaked roles, actions and build info (a security defect) |
| Problem Request / Problem Request Confirmed | not carried over | — | The email-only help-desk form (with an XSS in the confirmation) is replaced by the standard NRS support channel or help link |
| DBCompare | not carried over | — | Dead dev utility (its results page doesn't exist; it collected DB passwords in plain text) |
| template*.jsp | not carried over | — | Developer skeletons |
| Error pages (`errors/*.jsp`) | not carried over as screens | — | The SPA's own error and 403 handling covers them. The "application disabled / HBS_DISABLED_BYPASS" mode is a cross-cutting concern, not an admin screen. |

Security notes:

- `admin.*` queries use `clientScope` `AND u.client_number = :scopeClientNumber AND u.hbs_user_type_code = :hbsUserType`.
  A CLI_ADMIN sees CLI users of their client and an SPC_ADMIN sees SPC users of their client. Ministry admins see
  everyone.
- `UserDataDomainGuard` (an `@Before` advice on `CommandService.execute` for `admin.userDataDomains.*`) applies the same
  fence to writes. For industry callers it also:
  - forbids district domains (they only apply to ministry roles);
  - forbids adding scale sites the caller's client doesn't own.
- Location domains force the client number to the caller's client through the CLIENT_NUMBER argument.
- The legacy rule "page is read-only when the target holds MOF_MGR and the viewer doesn't" isn't reproduced. Role
  membership now lives in FAM and isn't visible to HBS.

### Administration — legacy logic not yet ported

- P017/P018/P019 rendered every candidate district or site as a checkbox list and saved the diff in one step
  (`updateSiteDataDomains` / `updateDistrictDataDomains` in `ear/hbs-ejb/…/uss/HBSUserManager2.java`, `ear/hbs-view/…/presentation/uss/SiteDataDomainAction.java`). The new
  screens add and remove one domain at a time. The effect is the same.
- The legacy UI only linked the data-domain pages next to the roles that use them. Districts went with
  HBS_SCALE_ADMIN, BILL_ADMIN, INV_CORR_APP and SMP_ADMIN; sites with CLI_SITE_ADMIN; locations with CLI_DOC_RCVR.
  Roles now live in FAM, so the new screen offers all three domain types for every user.

## Open questions for the business

1. Should ministry users keep the legacy (accidental?) behaviour where Final Bill Status shows only populations in
   their district data domains? This also decides whether `:hbsUserId` should be added to the query framework.
2. Is the default ratio still 1.24856 in production (`cfg_p_default_ratio`)?
3. Should the Force Summarization / Ratio Computation screens keep the "force all" bulk option, or is per-profile
   forcing enough?
4. Should Problem Request / About be replaced by links in the app footer, and to which support channel?
