package ca.bc.gov.nrs.hbs.api.profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.exception.EntityNotFoundException;
import ca.bc.gov.nrs.hbs.api.workflow.StaleUpdateException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** P383 cruise-based billing profile rows (P383Action.validateInputs). */
class CruiseBasedRatioTest extends ProfileFixtures {

  private CruiseBasedRatioCreate create;
  private CruiseBasedRatioUpdate update;

  @BeforeEach
  void setUp() {
    CruiseBasedRatioRules rules = new CruiseBasedRatioRules(reads);
    create = new CruiseBasedRatioCreate(api, rules, audit);
    update = new CruiseBasedRatioUpdate(api, rules, audit);
    reads.appraisal = new HashMap<>(Map.of("appraisal_mthd_cd", "I", "int_deciduous_vol", BigDecimal.valueOf(120)));
    reads.appraisedSpecies.add("LO");
    reads.speciesGrades.add("LO|7");
    reads.speciesGrades.add("LO|1");
    reads.speciesGrades.add("AS|8");
    reads.speciesGrades.add("XX|7");
  }

  private Map<String, Object> row(String species, String grade, String m3) {
    return body("timberMark", "ab123", "effectiveDate", "2026-04-01", "speciesCode", species,
        "gradeCode", grade, "volumePerHectare", m3);
  }

  @Test
  void add_happyPath() {
    create.execute(row("lo", "7", "123.456"));
    assertThat(api.names()).containsExactly("HBS_CREATE_CB_SP_GRD_RATIO");
    var call = api.calls.get(0);
    assertThat(call.arg("i_timber_mark")).isEqualTo("AB123");
    assertThat(call.arg("i_app_effective_date")).isEqualTo(day("2026-04-01"));
    assertThat(call.arg("i_scale_species_code")).isEqualTo("LO");
    assertThat(call.arg("i_volume_per_hectare")).isEqualTo(new BigDecimal("123.456"));
  }

  @ParameterizedTest(name = "{0}/{1} {2} → {3}")
  @CsvSource(delimiter = '|', value = {
      "LO|7|0|m3/Ha must be greater than zero",
      "LO|7|-1|m3/Ha must be greater than zero",
      "LO|7|1000|m3/Ha must be less than 1000",
      "LO|7|abc|m3/Ha must be numeric",
      "LO|1|10|ICB789 Interior cruise-based volume estimate returns must use grade 7 or grade 8",
      "LO|9|10|Species, product and grade combination is invalid.",
      "FI|7|10|Species, product and grade combination is invalid.",
  })
  void add_rowRules(String species, String grade, String m3, String message) {
    assertThat(messages(() -> create.execute(row(species, grade, m3)))).containsExactly(message);
  }

  @Test
  void add_boundaryJustUnder1000IsAccepted() {
    create.execute(row("LO", "7", "999.999"));
    assertThat(api.calls).hasSize(1);
  }

  @Test
  void add_speciesMustBeOnTheWorksheet() {
    reads.speciesGrades.add("FI|7");
    assertThat(messages(() -> create.execute(row("FI", "7", "10")))).containsExactly("Coniferous Species not on worksheet");
    reads.appraisal.put("int_deciduous_vol", BigDecimal.ZERO);
    assertThat(messages(() -> create.execute(row("AS", "8", "10")))).containsExactly("Deciduous Species not on worksheet");
    reads.appraisal.put("int_deciduous_vol", BigDecimal.ONE);
    create.execute(row("AS", "8", "10"));
    assertThat(api.calls).hasSize(1);
  }

  @Test
  void add_coastalAppraisalAcceptsAnyGrade() {
    reads.appraisal.put("appraisal_mthd_cd", "C");
    create.execute(row("LO", "1", "10"));
    assertThat(api.calls).hasSize(1);
  }

  @Test
  void add_requiredFieldsDuplicatesAndMissingWorksheet() {
    assertThat(messages(() -> create.execute(row("", "", "10"))))
        .containsExactly("A species is required.", "A grade is required.");
    reads.ratios.put("LO|7", Map.of());
    assertThat(messages(() -> create.execute(row("LO", "7", "10")))).containsExactly("Duplicate Species Grade combination");
    reads.appraisal = null;
    assertThat(messages(() -> create.execute(row("LO", "7", "10"))))
        .containsExactly("Upstream data problem. Please advise help desk.");
  }

  @Test
  void add_otherSpeciesSkipTheLaterSteps() {
    // neither coniferous nor deciduous: legacy validateInputs stopped after the SPG check
    create.execute(row("XX", "7", "5000"));
    assertThat(api.calls).hasSize(1);
  }

  @Test
  void update_changesTheVolumeKeepsEntryAudit() {
    Map<String, Object> stored = new HashMap<>();
    stored.put("timber_mark", "AB123");
    stored.put("app_effective_date", day("2026-04-01"));
    stored.put("scale_species_code", "LO");
    stored.put("scale_grade_code", "7");
    stored.put("volume_per_hectare", BigDecimal.TEN);
    stored.put("entry_userid", "IDIR\\FIRST");
    stored.put("entry_timestamp", ts("2026-05-01T11:00:00"));
    stored.put("update_userid", "IDIR\\FIRST");
    stored.put("update_timestamp", ts("2026-05-01T11:00:00"));
    reads.ratios.put("LO|7", stored);

    Map<String, Object> b = row("LO", "7", "20");
    b.put("updateTimestamp", "2026-05-01T11:00:00");
    update.execute(b);
    var call = api.callsTo("HBS_STORE_CB_SP_GRD_RATIO").get(0);
    assertThat(call.arg("i_volume_per_hectare")).isEqualTo(new BigDecimal("20"));
    assertThat(call.arg("i_entry_userid")).isEqualTo("IDIR\\FIRST");
    assertThat(call.arg("i_update_userid")).isEqualTo("IDIR\\TESTER");

    api.calls.clear();
    b.put("updateTimestamp", "2026-05-01T11:00:01");
    assertThatThrownBy(() -> update.execute(b)).isInstanceOf(StaleUpdateException.class);
    assertThatThrownBy(() -> update.execute(row("LO", "1", "20"))).isInstanceOf(Exception.class);
    reads.ratios.clear();
    assertThatThrownBy(() -> update.execute(row("LO", "7", "20"))).isInstanceOf(EntityNotFoundException.class);
    assertThat(api.calls).isEmpty();
  }
}
