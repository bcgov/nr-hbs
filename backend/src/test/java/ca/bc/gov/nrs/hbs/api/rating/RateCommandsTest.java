package ca.bc.gov.nrs.hbs.api.rating;

import static ca.bc.gov.nrs.hbs.api.rating.FakeRatingRepository.row;
import static ca.bc.gov.nrs.hbs.api.rating.FakeRatingRepository.spgKey;
import static ca.bc.gov.nrs.hbs.api.rating.FakeRatingRepository.ts;
import static ca.bc.gov.nrs.hbs.api.rating.OverrideRuleCommandsTest.body;
import static ca.bc.gov.nrs.hbs.api.rating.OverrideRuleCommandsTest.problems;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.rating.RatingRules.Interval;
import ca.bc.gov.nrs.hbs.api.rating.RatingRules.RateInterval;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi;
import ca.bc.gov.nrs.hbs.api.workflow.StaleUpdateException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** District default rates (P212/P213) and waste &amp; residue rates (P222/P223). */
class RateCommandsTest {

  static final LocalDate TODAY = LocalDate.parse("2026-10-21");
  static final Interval ALWAYS = new Interval(LocalDate.parse("2000-01-01"), LocalDate.parse("2099-12-31"));

  FakeRatingRepository repo;
  RecordingTableApi api;
  Audit audit;
  RatingClock clock;

  @BeforeEach
  void setUp() {
    repo = new FakeRatingRepository();
    api = new RecordingTableApi();
    audit = new Audit("TESTER");
    clock = new RatingClock(Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    repo.grades.put("C|U", ALWAYS);
    repo.grades.put("I|1", ALWAYS);
    repo.spg.put(spgKey("FI", " ", "U"), ALWAYS);
    repo.spg.put(spgKey("HE", " ", "U"), ALWAYS);
  }

  // ── P212 / P213 ──

  Map<String, Object> districtBody(Object... extra) {
    Map<String, Object> b = body("orgUnitNo", "1234", "appraisalMthdCode", "c", "scaleSpeciesCode", "",
        "scaleProductCode", " ", "scaleGradeCode", "U", "rate", "25.10",
        "effectiveDate", "2026-01-01", "expiryDate", "2026-12-31");
    b.putAll(body(extra));
    return b;
  }

  @Test
  void districtCreate_allSpeciesLogs() {
    Map<String, Object> out = new DistrictRateCommands.Create(repo, api, audit, clock).execute(districtBody());
    var call = api.calls.get(0);
    assertThat(call.name()).isEqualTo("HBS_CREATE_HBS_DISTRICT_RATE");
    assertThat(call.arg("i_org_unit_no")).isEqualTo(new BigDecimal(1234));
    assertThat(call.arg("i_scale_species_code")).isNull();
    assertThat(call.arg("i_scale_product_code")).isEqualTo(" ");
    assertThat(call.arg("i_appraisal_mthd_code")).isEqualTo("C");
    assertThat(call.arg("i_active_ind")).isEqualTo("Y");
    assertThat(call.arg("i_rate")).isEqualTo(new BigDecimal("25.10"));
    assertThat(out).containsEntry("distrtId", 1001L);
  }

  @Test
  void districtCreate_validationXmlFirst_thenTheActionChecks() {
    var create = new DistrictRateCommands.Create(repo, api, audit, clock);
    assertThat(problems(() -> create.execute(districtBody("effectiveDate", "", "rate", "12.345", "orgUnitNo", ""))))
        .containsExactly("Effective Date is required.", "District is required.",
            "Rate must have 2 or less decimal places.");
    assertThat(problems(() -> create.execute(districtBody("effectiveDate", "2027-01-01"))))
        .containsExactly(RatingMessages.EFF_ON_OR_BEFORE_EXP);

    repo.activeDistrictRates.add(new RateInterval(9, LocalDate.parse("2026-06-01"), LocalDate.parse("2027-06-30")));
    assertThat(problems(() -> create.execute(districtBody("effectiveDate", "2019-01-01"))))
        .containsExactly(RatingMessages.DDR_EFF_OLD, RatingMessages.DDR_EXP_IN_INTERVAL);
    repo.activeDistrictRates.clear();

    assertThat(problems(() -> create.execute(districtBody("scaleSpeciesCode", "FI", "scaleProductCode", "01"))))
        .containsExactly(RatingMessages.SPG_INVALID);
    assertThat(problems(() -> create.execute(districtBody("appraisalMthdCode", "I"))))
        .containsExactly(RatingMessages.INTERIOR_GRADE_NOT_EXIST);
    repo.grades.put("C|U", new Interval(LocalDate.parse("2026-03-01"), LocalDate.parse("2099-12-31")));
    assertThat(problems(() -> create.execute(districtBody())))
        .containsExactly(RatingMessages.COAST_GRADE_FROM);
    assertThat(api.calls).isEmpty();
  }

  Map<String, Object> districtRow(long id, String eff, String exp) {
    Map<String, Object> r = row("distrt_id", BigDecimal.valueOf(id), "org_unit_no", BigDecimal.valueOf(1234),
        "scale_species_code", null, "scale_product_code", " ", "scale_grade_code", "U",
        "appraisal_mthd_code", "C", "rate", new BigDecimal("10.00"), "active_ind", "Y",
        "effective_date", ts(eff), "expiry_date", ts(exp),
        "entry_userid", "IDIR\\ORIG", "entry_timestamp", ts("2018-01-01"),
        "update_userid", "IDIR\\ORIG", "update_timestamp", FakeRatingRepository.UPDATED);
    repo.districtRates.put(id, r);
    return r;
  }

  @Test
  void districtUpdate_storesRateAndDates_excludingItselfFromTheOverlap() {
    districtRow(3, "2026-01-01", "2026-12-31");
    repo.activeDistrictRates.add(new RateInterval(3, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")));
    new DistrictRateCommands.Update(repo, api, audit, clock).execute(body("distrtId", "3", "rate", "11.00",
        "effectiveDate", "2026-02-01", "expiryDate", "2026-11-30", "updateTimestamp", "2026-10-01T10:15:30"));
    var call = api.calls.get(0);
    assertThat(call.name()).isEqualTo("HBS_STORE_HBS_DISTRICT_RATE");
    assertThat(call.arg("i_rate")).isEqualTo(new BigDecimal("11.00"));
    assertThat(call.arg("i_effective_date")).isEqualTo(ts("2026-02-01"));
    assertThat(call.arg("i_scale_product_code")).isEqualTo(" ");
    assertThat(call.arg("i_entry_timestamp")).isEqualTo(ts("2018-01-01"));
    assertThat(call.arg("i_update_userid")).isEqualTo("TESTER");
  }

  @Test
  void districtUpdate_datesOnFileOlderThanSevenYearsCannotChange() {
    districtRow(3, "2018-01-01", "2026-12-31");
    var update = new DistrictRateCommands.Update(repo, api, audit, clock);
    assertThat(problems(() -> update.execute(body("distrtId", "3", "rate", "11", "effectiveDate", "2018-02-01",
        "expiryDate", "2026-12-31")))).containsExactly(RatingMessages.DDR_EFF_ON_FILE_OLD);
    // unchanged old effective date is fine; moving the expiry more than 7 years back is not
    repo.grades.put("C|U", ALWAYS);
    assertThat(problems(() -> update.execute(body("distrtId", "3", "rate", "11", "effectiveDate", "2018-01-01",
        "expiryDate", "2019-01-01")))).containsExactly(RatingMessages.DDR_EXP_OLD);
    update.execute(body("distrtId", "3", "rate", "12", "effectiveDate", "2018-01-01", "expiryDate", "2026-12-31"));
    assertThat(api.names()).containsExactly("HBS_STORE_HBS_DISTRICT_RATE");
    assertThatThrownBy(() -> update.execute(body("distrtId", "3", "rate", "12", "effectiveDate", "2018-01-01",
        "expiryDate", "2026-12-31", "updateTimestamp", "2020-01-01T00:00:00")))
        .isInstanceOf(StaleUpdateException.class);
  }

  // ── P222 / P223 ──

  Map<String, Object> wasteBody(Object... extra) {
    Map<String, Object> b = body("timberMark", "abc123", "cutBlockId", "cb1", "effectiveDate", "2026-05-01",
        "scaleSpeciesCode", "FI", "scaleGradeCode", "U", "rate", "12.34", "wasteRateSourceCode", "A");
    b.putAll(body(extra));
    return b;
  }

  @Test
  void wasteCreate_logsAvoidableActive_expiryEqualsScaleDate() {
    repo.cutBlocks.put("ABC123", List.of("CB1"));
    Map<String, Object> out = new WasteRateCommands.Create(repo, api, audit, clock).execute(wasteBody());
    var call = api.calls.get(0);
    assertThat(call.name()).isEqualTo("HBS_CREATE_WASTE_RESIDUE_RATE");
    assertThat(call.arg("i_scale_product_code")).isEqualTo(" ");
    assertThat(call.arg("i_active_ind")).isEqualTo("Y");
    assertThat(call.arg("i_avoidable_ind")).isEqualTo("Y");
    assertThat(call.arg("i_effective_date")).isEqualTo(ts("2026-05-01"));
    assertThat(call.arg("i_expiry_date")).isEqualTo(ts("2026-05-01"));
    assertThat(call.arg("i_cut_block_id")).isEqualTo("CB1");
    assertThat(out).containsEntry("wstrtId", 1001L);
  }

  @Test
  void wasteCreate_validations() {
    var create = new WasteRateCommands.Create(repo, api, audit, clock);
    assertThat(problems(() -> create.execute(wasteBody("timberMark", "ZZZ999", "rate", "1000"))))
        .containsExactly(RatingMessages.TIMBER_MARK_REF, "Rate cannot be greater than 999.99.");

    // a mark without cut blocks accepts any cut block made of the allowed characters
    create.execute(wasteBody("cutBlockId", "A-1"));
    assertThat(problems(() -> create.execute(wasteBody("cutBlockId", "A_1"))))
        .containsExactly(RatingMessages.CUT_BLOCK_CHARS);
    repo.cutBlocks.put("ABC123", List.of("CB1"));
    assertThat(problems(() -> create.execute(wasteBody("cutBlockId", "CB2"))))
        .containsExactly(RatingMessages.CUT_BLOCK_NOT_ON_MARK);
    assertThat(problems(() -> create.execute(wasteBody("effectiveDate", "2019-10-20"))))
        .containsExactly(RatingMessages.WRR_EFF_OLD);
    assertThat(problems(() -> create.execute(wasteBody("scaleSpeciesCode", "CW"))))
        .containsExactly(RatingMessages.SPG_INVALID);

    repo.activeWasteRates.add(row("wstrt_id", BigDecimal.ONE));
    assertThat(problems(() -> create.execute(wasteBody()))).containsExactly(RatingMessages.RECORD_EXISTS);
    // ported as written: P222 only rejected exactly one existing row
    repo.activeWasteRates.add(row("wstrt_id", BigDecimal.TWO));
    create.execute(wasteBody());
    assertThat(api.names()).containsExactly("HBS_CREATE_WASTE_RESIDUE_RATE", "HBS_CREATE_WASTE_RESIDUE_RATE");
  }

  Map<String, Object> wasteRow(long id, String active, String eff) {
    Map<String, Object> r = row("wstrt_id", BigDecimal.valueOf(id), "timber_mark", "ABC123", "cut_block_id", "CB1",
        "scale_species_code", "FI", "scale_product_code", " ", "scale_grade_code", "U",
        "waste_rate_source_code", "A", "active_ind", active, "avoidable_ind", "Y", "raw_rate", null,
        "rate", new BigDecimal("5.00"), "effective_date", ts(eff), "expiry_date", ts(eff),
        "entry_userid", "IDIR\\ORIG", "entry_timestamp", ts("2026-01-01"),
        "update_userid", "IDIR\\ORIG", "update_timestamp", FakeRatingRepository.UPDATED);
    repo.wasteRates.put(id, r);
    return r;
  }

  @Test
  void wasteUpdate_inactivatesTheFirstOtherMatchingRate_thenStoresTheEditedOne() {
    wasteRow(20, "Y", "2026-04-01");
    repo.activeWasteRates.add(wasteRow(21, "Y", "2026-05-01"));
    repo.activeWasteRates.add(wasteRow(22, "Y", "2026-05-01"));
    new WasteRateCommands.Update(repo, api, audit, clock).execute(body("wstrtId", "20", "effectiveDate", "2026-05-01",
        "scaleSpeciesCode", "HE", "scaleGradeCode", "U", "rate", "7.5", "wasteRateSourceCode", "B",
        "updateTimestamp", "2026-10-01T10:15:30"));

    assertThat(api.names()).containsExactly("HBS_STORE_WASTE_RESIDUE_RATE", "HBS_STORE_WASTE_RESIDUE_RATE");
    var inactivated = api.calls.get(0);
    assertThat(inactivated.arg("i_wstrt_id")).isEqualTo(new BigDecimal(21));
    assertThat(inactivated.arg("i_active_ind")).isEqualTo("N");
    var edited = api.calls.get(1);
    assertThat(edited.arg("i_wstrt_id")).isEqualTo(new BigDecimal(20));
    assertThat(edited.arg("i_scale_species_code")).isEqualTo("HE");
    assertThat(edited.arg("i_rate")).isEqualTo(new BigDecimal("7.5"));
    assertThat(edited.arg("i_effective_date")).isEqualTo(ts("2026-05-01"));
    assertThat(edited.arg("i_expiry_date")).isEqualTo(ts("2026-05-01"));
    assertThat(edited.arg("i_active_ind")).isEqualTo("Y");
    assertThat(edited.arg("i_waste_rate_source_code")).isEqualTo("B");
    assertThat(edited.arg("i_entry_userid")).isEqualTo("IDIR\\ORIG");
  }

  @Test
  void wasteUpdate_whenTheFirstMatchIsItself_onlyItIsStored() {
    repo.activeWasteRates.add(wasteRow(20, "Y", "2026-04-01"));
    repo.activeWasteRates.add(wasteRow(21, "Y", "2026-04-01"));
    new WasteRateCommands.Update(repo, api, audit, clock).execute(body("wstrtId", "20", "effectiveDate", "2026-04-01",
        "scaleSpeciesCode", "FI", "scaleGradeCode", "U", "rate", "6", "wasteRateSourceCode", "A"));
    assertThat(api.calls).hasSize(1);
    assertThat(api.calls.get(0).arg("i_wstrt_id")).isEqualTo(new BigDecimal(20));
  }

  @Test
  void wasteUpdate_refusesInactiveAndOldDates() {
    wasteRow(20, "N", "2026-04-01");
    var update = new WasteRateCommands.Update(repo, api, audit, clock);
    assertThat(problems(() -> update.execute(body("wstrtId", "20", "effectiveDate", "2026-04-01",
        "scaleSpeciesCode", "FI", "scaleGradeCode", "U", "rate", "6", "wasteRateSourceCode", "A"))))
        .containsExactly(RatingMessages.WRR_INACTIVE);
    wasteRow(30, "Y", "2018-04-01");
    assertThat(problems(() -> update.execute(body("wstrtId", "30", "effectiveDate", "2026-04-01",
        "scaleSpeciesCode", "FI", "scaleGradeCode", "U", "rate", "6", "wasteRateSourceCode", "A"))))
        .containsExactly(RatingMessages.WRR_EFF_ON_FILE_OLD);
    wasteRow(31, "Y", "2026-04-01");
    assertThat(problems(() -> update.execute(body("wstrtId", "31", "effectiveDate", "2019-01-01",
        "scaleSpeciesCode", "FI", "scaleGradeCode", "U", "rate", "6", "wasteRateSourceCode", "A"))))
        .containsExactly(RatingMessages.WRR_EFF_OLD);
    assertThat(api.calls).isEmpty();
  }

  @Test
  void wasteDeactivate_softDeletesAnActiveRate() {
    wasteRow(20, "Y", "2026-04-01");
    new WasteRateCommands.Deactivate(repo, api, audit, clock).execute(body("wstrtId", "20",
        "updateTimestamp", "2026-10-01T10:15:30"));
    var call = api.calls.get(0);
    assertThat(call.name()).isEqualTo("HBS_STORE_WASTE_RESIDUE_RATE");
    assertThat(call.arg("i_active_ind")).isEqualTo("N");
    assertThat(call.arg("i_rate")).isEqualTo(new BigDecimal("5.00"));

    wasteRow(21, "N", "2026-04-01");
    assertThat(problems(() -> new WasteRateCommands.Deactivate(repo, api, audit, clock)
        .execute(body("wstrtId", "21")))).containsExactly(RatingMessages.WRR_INACTIVE);
  }
}
