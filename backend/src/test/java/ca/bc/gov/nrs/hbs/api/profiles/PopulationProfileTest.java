package ca.bc.gov.nrs.hbs.api.profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.workflow.StaleUpdateException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.access.AccessDeniedException;

/** P332 / P333 population profile workflows (ratio schedule at 23:59:59). */
class PopulationProfileTest extends ProfileFixtures {

  private PopulationProfileCreate create;
  private PopulationProfileUpdate update;
  private PopulationProfileDelete delete;

  @BeforeEach
  void setUp() {
    create = new PopulationProfileCreate(api, reads, caller, audit);
    update = new PopulationProfileUpdate(api, reads, caller, audit);
    delete = new PopulationProfileDelete(api, reads, caller);
    reads.populations.put("1234|2026", new HashMap<>(Map.of("client_number", "00012345")));
  }

  // ------------------------------------------------------------ schedule (P333Action BT4677)

  @ParameterizedTest(name = "{1} from {0} → {2}")
  @CsvSource({
      "2026-11-01, MTH, 2026-11-30T23:59:59",
      "2027-02-01, MTH, 2027-02-28T23:59:59",
      "2028-02-01, MTH, 2028-02-29T23:59:59", // leap year
      "2026-12-01, MTH, 2026-12-31T23:59:59",
      "2026-11-01, BWK, 2026-11-15T23:59:59",
      "2026-11-01, DLY, 2026-11-01T23:59:59",
  })
  void firstNextRatioStatementDate(String effective, String frequency, String expected) {
    assertThat(PopulationProfileRules.firstNextRatioStmtDate(LocalDate.parse(effective), frequency))
        .isEqualTo(LocalDateTime.parse(expected));
  }

  private Map<String, Object> addBody(String effective, String expiry, String frequency) {
    return body("populationNumber", "1234", "samplingYear", "2026", "frequency", frequency,
        "effectiveDate", effective, "expiryDate", expiry);
  }

  @Test
  void add_storesTheRatioScheduleWithEndOfDayTimes() {
    Map<String, Object> out = create.execute(addBody("2026-11-01", "2027-10-31", "MTH"));

    assertThat(api.names()).containsExactly("HBS_CREATE_POPULATION_PROFILE");
    var call = api.calls.get(0);
    assertThat(call.arg("i_popprof_id")).isEqualTo(BigDecimal.valueOf(1001));
    assertThat(call.arg("i_population_number")).isEqualTo("1234");
    assertThat(call.arg("i_sampling_year")).isEqualTo(BigDecimal.valueOf(2026));
    assertThat(call.arg("i_hbs_frequency_type_code")).isEqualTo("MTH");
    assertThat(call.arg("i_next_ratio_stmt_date")).isEqualTo(ts("2026-11-30T23:59:59"));
    assertThat(call.arg("i_last_ratio_stmt_date")).isEqualTo(day("2026-11-01"));
    assertThat(call.arg("i_start_ratio_stmt_date")).isEqualTo(day("2026-10-21"));
    assertThat(call.arg("i_effective_date")).isEqualTo(day("2026-11-01"));
    assertThat(call.arg("i_expiry_date")).isEqualTo(ts("2027-10-31T23:59:59"));
    assertThat(call.arg("i_entry_userid")).isEqualTo("IDIR\\TESTER");
    assertThat(out).containsEntry("popprofId", 1001L);
    Object[] overlap = reads.overlapCalls.get(0);
    assertThat(overlap[2]).isEqualTo(LocalDate.parse("2026-11-01"));
    assertThat(overlap[3]).isEqualTo(LocalDate.parse("2027-10-31"));
  }

  @Test
  void add_expiryOnTheFirstRatioStatementDayIsAccepted() {
    // next = 2026-11-30 23:59:59, expiry = 2026-11-30 23:59:59: equal, not after
    create.execute(addBody("2026-11-01", "2026-11-30", "MTH"));
    assertThat(api.names()).containsExactly("HBS_CREATE_POPULATION_PROFILE");
  }

  @Test
  void add_effectiveDateMustBeTheFirstAndNotInThePast() {
    assertThat(messages(() -> create.execute(addBody("2026-11-02", "2027-10-31", "MTH"))))
        .containsExactly("Effective day must be the first day of the month.");
    // the 1st of the current month is already past (00:00 < now)
    assertThat(messages(() -> create.execute(addBody("2026-10-01", "2027-10-31", "MTH"))))
        .containsExactly("Effective date cannot precede current month.");
  }

  @Test
  void add_firstOfTheMonthAtMidnightIsNotPast() {
    audit.now = LocalDateTime.of(2026, 11, 1, 0, 0, 0);
    create.execute(addBody("2026-11-01", "2027-10-31", "MTH"));
    assertThat(api.names()).containsExactly("HBS_CREATE_POPULATION_PROFILE");
  }

  @Test
  void add_expiryMustBeTheLastDayOfAMonth() {
    assertThat(messages(() -> create.execute(addBody("2026-11-01", "2027-10-30", "MTH"))))
        .containsExactly("Expiry day must be the last day of the month.");
    assertThat(messages(() -> create.execute(addBody("2028-12-01", "2028-02-28", "MTH"))))
        .contains("Effective date should be before Expiry date.");
  }

  @Test
  void add_yearsOutside2000To2099() {
    assertThat(messages(() -> create.execute(addBody("2100-01-01", "2100-01-31", "MTH"))))
        .containsExactly("Effective year is not valid.", "Expiry Date must be after nextRun date.");
  }

  @Test
  void add_overlapAndUnknownPopulation() {
    reads.overlaps = 1;
    assertThat(messages(() -> create.execute(addBody("2026-11-01", "2027-10-31", "MTH"))))
        .containsExactly("Date overlapping.");
    assertThat(messages(() -> create.execute(body("populationNumber", "9999", "samplingYear", "2026",
        "frequency", "MTH", "effectiveDate", "2026-11-01", "expiryDate", "2027-10-31"))))
        .containsExactly("Population / Year combination is invalid.");
    assertThat(messages(() -> create.execute(body("populationNumber", "AB", "samplingYear", "x",
        "frequency", "MTH", "effectiveDate", "", "expiryDate", "2027-13-01"))))
        .containsExactly("Year must be numeric.", "Population must be numeric.", "Effective Date is required.",
            "Expiry Date is not a valid date.");
  }

  @Test
  void add_industryCallerOnlyForOwnPopulations() {
    caller.ministry = false;
    caller.client = "00099999";
    assertThatThrownBy(() -> create.execute(addBody("2026-11-01", "2027-10-31", "MTH")))
        .isInstanceOf(AccessDeniedException.class);
    caller.client = "00012345";
    create.execute(addBody("2026-11-01", "2027-10-31", "MTH"));
    assertThat(api.calls).hasSize(1);
  }

  // ------------------------------------------------------------ P332 update / delete

  private void storedProfile() {
    Map<String, Object> row = new HashMap<>();
    row.put("popprof_id", BigDecimal.valueOf(77));
    row.put("population_number", "1234");
    row.put("sampling_year", BigDecimal.valueOf(2026));
    row.put("hbs_frequency_type_code", "MTH");
    row.put("next_ratio_stmt_date", ts("2026-10-31T23:59:59"));
    row.put("last_ratio_stmt_date", day("2026-10-01"));
    row.put("start_ratio_stmt_date", day("2026-09-15"));
    row.put("final_bill_date", null);
    row.put("effective_date", day("2026-10-01"));
    row.put("expiry_date", ts("2027-03-31T23:59:59"));
    row.put("entry_userid", "IDIR\\FIRST");
    row.put("entry_timestamp", ts("2026-09-15T08:30:12"));
    row.put("update_userid", "IDIR\\FIRST");
    row.put("update_timestamp", ts("2026-09-15T08:30:12"));
    row.put("pop_client_number", "00012345");
    reads.populationProfiles.put(77L, row);
  }

  @Test
  void update_changesOnlyTheExpiryAndKeepsTheScheduleTimes() {
    storedProfile();
    update.execute(body("popprofId", "77", "expiryDate", "2027-06-30", "updateTimestamp", "2026-09-15T08:30:12"));

    var call = api.callsTo("HBS_STORE_POPULATION_PROFILE").get(0);
    assertThat(call.arg("i_expiry_date")).isEqualTo(ts("2027-06-30T23:59:59"));
    assertThat(call.arg("i_next_ratio_stmt_date")).isEqualTo(ts("2026-10-31T23:59:59"));
    assertThat(call.arg("i_last_ratio_stmt_date")).isEqualTo(day("2026-10-01"));
    assertThat(call.arg("i_start_ratio_stmt_date")).isEqualTo(day("2026-09-15"));
    assertThat(call.arg("i_entry_userid")).isEqualTo("IDIR\\FIRST");
    assertThat(call.arg("i_entry_timestamp")).isEqualTo(ts("2026-09-15T08:30:12"));
    assertThat(call.arg("i_update_userid")).isEqualTo("IDIR\\TESTER");
    assertThat(reads.overlapCalls.get(0)[4]).isEqualTo(77L);
  }

  @Test
  void update_expiryMayEqualTheNextRunDayButNotPrecedeIt() {
    storedProfile();
    update.execute(body("popprofId", "77", "expiryDate", "2026-10-31"));
    assertThat(api.calls).hasSize(1);
    api.calls.clear();
    assertThat(messages(() -> update.execute(body("popprofId", "77", "expiryDate", "2026-09-30"))))
        .containsExactly("Expiry Date must be after nextRun date.");
  }

  @Test
  void update_rulesLockAndFence() {
    storedProfile();
    assertThat(messages(() -> update.execute(body("popprofId", "77", "expiryDate", "2027-06-29"))))
        .containsExactly("Expiry day must be the last day of the month.");
    reads.overlaps = 2;
    assertThat(messages(() -> update.execute(body("popprofId", "77", "expiryDate", "2027-06-30"))))
        .containsExactly("Date overlapping.");
    assertThatThrownBy(() -> update.execute(body("popprofId", "77", "expiryDate", "2027-06-30",
        "updateTimestamp", "2026-09-15T08:30:13"))).isInstanceOf(StaleUpdateException.class);
    caller.ministry = false;
    caller.client = "00099999";
    assertThatThrownBy(() -> update.execute(body("popprofId", "77", "expiryDate", "2027-06-30")))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(api.calls).isEmpty();
  }

  @Test
  void delete_fencedAndLocked() {
    storedProfile();
    delete.execute(body("popprofId", 77, "updateTimestamp", "2026-09-15T08:30:12"));
    assertThat(api.names()).containsExactly("HBS_REMOVE_POPULATION_PROFILE");
    assertThat(api.calls.get(0).arg("i_popprof_id")).isEqualTo(BigDecimal.valueOf(77));
    api.calls.clear();
    caller.ministry = false;
    caller.client = "00099999";
    assertThatThrownBy(() -> delete.execute(body("popprofId", 77))).isInstanceOf(AccessDeniedException.class);
    assertThat(api.calls).isEmpty();
  }
}
