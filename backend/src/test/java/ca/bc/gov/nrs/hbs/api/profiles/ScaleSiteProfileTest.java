package ca.bc.gov.nrs.hbs.api.profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.profiles.ProfileReads.Range;
import ca.bc.gov.nrs.hbs.api.security.HbsRoles;
import ca.bc.gov.nrs.hbs.api.workflow.StaleUpdateException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** P322 / P323 trading partner submitter (scale site profile) workflows. */
class ScaleSiteProfileTest extends ProfileFixtures {

  private ScaleSiteProfileCreate create;
  private ScaleSiteProfileUpdate update;

  @BeforeEach
  void setUp() {
    ScaleSiteProfileRules rules = new ScaleSiteProfileRules(reads, caller);
    create = new ScaleSiteProfileCreate(api, reads, rules, audit);
    update = new ScaleSiteProfileUpdate(api, reads, rules, audit);
    caller.roles.add(HbsRoles.SCALE_ADMIN);
    reads.districts.add(1873L);
    reads.scaleSites.put("012", new HashMap<>(Map.of("org_unit_no", BigDecimal.valueOf(1873))));
    reads.tradingPartners.add("00012345|00");
  }

  private Map<String, Object> summary(String effective, String expiry) {
    return body("scaleSiteId", "12", "docType", "S", "returnType", "W", "clientNumber", "12345",
        "clientLocnCode", "0", "effectiveDate", effective, "expiryDate", expiry);
  }

  @Test
  void add_summaryProfile_padsKeysAndCallsTheCreateProc() {
    Map<String, Object> out = create.execute(summary("2026-11-01", "2027-10-31"));

    assertThat(api.names()).containsExactly("HBS_CREATE_TRA_PART_SUBMITTER");
    var call = api.calls.get(0);
    assertThat(call.arg("i_tps_id")).isEqualTo(BigDecimal.valueOf(1001));
    assertThat(call.arg("i_tpsubmtr_type")).isEqualTo("TPSS");
    assertThat(call.arg("i_scale_site_id_nmbr")).isEqualTo("012");
    assertThat(call.arg("i_client_number")).isEqualTo("00012345");
    assertThat(call.arg("i_client_locn_code")).isEqualTo("00");
    assertThat(call.arg("i_tp_doc_type")).isEqualTo("S");
    assertThat(call.arg("i_effective_date")).isEqualTo(day("2026-11-01"));
    assertThat(call.arg("i_expiry_date")).isEqualTo(day("2027-10-31"));
    assertThat(out).containsEntry("profileKey", "TPS1001");
  }

  @Test
  void add_siteMustExistAndBeInTheCallersDistricts() {
    assertThat(messages(() -> create.execute(body("scaleSiteId", "999", "docType", "D", "returnType", "W",
        "effectiveDate", "2026-11-01", "expiryDate", "2027-10-31"))))
        .containsExactly("The scale site entered does not exist.");
    reads.districts.clear();
    reads.districts.add(1000L);
    assertThat(messages(() -> create.execute(summary("2026-11-01", "2027-10-31"))))
        .containsExactly("User not authorized to add/edit scale site profile for this scale site.");
  }

  @Test
  void add_siteAdminNeedsTheSiteInTheirSiteDomain() {
    caller.roles.clear();
    caller.roles.add(HbsRoles.CLI_SITE_ADMIN);
    assertThat(messages(() -> create.execute(summary("2026-11-01", "2027-10-31"))))
        .containsExactly("User not authorized to add/edit scale site profile for this scale site.");
    reads.siteDomains.add("IDIR\\TESTER|012");
    create.execute(summary("2026-11-01", "2027-10-31"));
    assertThat(api.calls).hasSize(1);
  }

  @Test
  void add_clientRulesByDocType() {
    assertThat(messages(() -> create.execute(body("scaleSiteId", "012", "docType", "D", "returnType", "W",
        "clientNumber", "12345", "effectiveDate", "2026-11-01", "expiryDate", "2027-10-31"))))
        .containsExactly("Client Number and Location must be blank when inserting a detail record");
    assertThat(messages(() -> create.execute(body("scaleSiteId", "012", "docType", "S", "returnType", "W",
        "effectiveDate", "2026-11-01", "expiryDate", "2027-10-31"))))
        .containsExactly("Client number and location are required.");
    assertThat(messages(() -> create.execute(body("scaleSiteId", "012", "docType", "S", "returnType", "W",
        "clientNumber", "99", "clientLocnCode", "00", "effectiveDate", "2026-11-01", "expiryDate", "2027-10-31"))))
        .containsExactly("Client or client location is invalid.");
  }

  @Test
  void add_dateRules() {
    assertThat(messages(() -> create.execute(summary("1899-12-31", "nope"))))
        .containsExactly("Year cannot be prior to 1900", "Invalid Date");
    assertThat(messages(() -> create.execute(summary("2026-11-02", "2026-11-01"))))
        .containsExactly("Expiry date cannot be before effective date.");
    // same day is a valid one-day range
    create.execute(summary("2026-11-01", "2026-11-01"));
    assertThat(api.calls).hasSize(1);
  }

  @Test
  void add_effectiveInsideAnExistingRange_boundariesIncluded() {
    reads.scaleSiteRanges.add(new Range(LocalDateTime.parse("2026-01-01T00:00"), LocalDateTime.parse("2026-11-01T00:00")));
    assertThat(messages(() -> create.execute(summary("2026-11-01", "2027-10-31"))))
        .containsExactly("Effective date cannot occur in an existing scale site profile range.");
    assertThat(messages(() -> create.execute(summary("2026-01-01", "2026-01-31"))))
        .containsExactly("Effective date cannot occur in an existing scale site profile range.");
    create.execute(summary("2026-11-02", "2027-10-31"));
    assertThat(api.calls).hasSize(1);
  }

  @Test
  void add_expiryMustEndBeforeTheNextProfile() {
    reads.nextScaleSite = LocalDateTime.parse("2027-01-01T00:00");
    assertThat(messages(() -> create.execute(summary("2026-11-01", "2027-01-01"))))
        .containsExactly("Date range cannot overlap an existing valid range");
    create.execute(summary("2026-11-01", "2026-12-31"));
    assertThat(api.calls).hasSize(1);
  }

  // ------------------------------------------------------------ P322

  private void storedTps() {
    Map<String, Object> row = new HashMap<>();
    row.put("tps_id", BigDecimal.valueOf(55));
    row.put("tpsubmtr_type", "TPSS");
    row.put("tp_doc_type", "S");
    row.put("client_number", "00012345");
    row.put("client_locn_code", "00");
    row.put("hbs_return_type_code", "W");
    row.put("scale_site_id_nmbr", "012");
    row.put("effective_date", day("2026-01-01"));
    row.put("expiry_date", day("2026-12-31"));
    row.put("entry_userid", "IDIR\\FIRST");
    row.put("entry_timestamp", ts("2025-12-01T09:15:00"));
    row.put("update_userid", "IDIR\\FIRST");
    row.put("update_timestamp", ts("2025-12-01T09:15:00"));
    reads.tps.put(55L, row);
  }

  @Test
  void update_onlyTheExpiryChanges_restComesFromTheRow() {
    storedTps();
    // a tampered body cannot move the profile to another site or client
    update.execute(body("profileKey", "TPS55", "expiryDate", "2027-06-30", "scaleSiteId", "999",
        "clientNumber", "00000001", "updateTimestamp", "2025-12-01T09:15:00"));

    var call = api.callsTo("HBS_STORE_TRA_PART_SUBMITTER").get(0);
    assertThat(call.arg("i_tps_id")).isEqualTo(BigDecimal.valueOf(55));
    assertThat(call.arg("i_scale_site_id_nmbr")).isEqualTo("012");
    assertThat(call.arg("i_client_number")).isEqualTo("00012345");
    assertThat(call.arg("i_expiry_date")).isEqualTo(day("2027-06-30"));
    assertThat(call.arg("i_entry_timestamp")).isEqualTo(ts("2025-12-01T09:15:00"));
    assertThat(call.arg("i_update_userid")).isEqualTo("IDIR\\TESTER");
    assertThat(call.arg("i_update_timestamp")).isEqualTo(java.sql.Timestamp.valueOf(NOW));
  }

  @Test
  void update_rules() {
    storedTps();
    assertThat(messages(() -> update.execute(body("tpsId", "55", "expiryDate", "2025-12-31"))))
        .containsExactly("Expiry date cannot be before effective date.");
    reads.nextScaleSite = LocalDateTime.parse("2027-01-01T00:00");
    assertThat(messages(() -> update.execute(body("tpsId", "55", "expiryDate", "2027-01-01"))))
        .containsExactly("Date range cannot overlap an existing valid range");
    reads.districts.clear();
    reads.districts.add(1L);
    assertThat(messages(() -> update.execute(body("tpsId", "55", "expiryDate", "2026-12-30"))))
        .containsExactly("User not authorized to add/edit scale site profile for this scale site.");
  }

  @Test
  void update_staleRecordIsRefused() {
    storedTps();
    assertThatThrownBy(() -> update.execute(body("tpsId", "55", "expiryDate", "2026-12-30",
        "updateTimestamp", "2025-12-01T09:14:59"))).isInstanceOf(StaleUpdateException.class);
    assertThat(api.calls).isEmpty();
  }
}
