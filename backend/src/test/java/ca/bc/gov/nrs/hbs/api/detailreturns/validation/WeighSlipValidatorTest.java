package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.WeighSampleFixtures.codes;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.WeighSampleFixtures.ts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.detailreturns.StubReferenceData;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.EditError;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.PopulationRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.ScaleSiteRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.TimberMarkRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.WeighSampleFixtures.StubWeighSample;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/** {@link WeighSlipValidator} (legacy {@code $E/dac/detail/control/WeighSlipValidator}). */
class WeighSlipValidatorTest {

  private final StubReferenceData ref = WeighSampleFixtures.reference();
  private final StubWeighSample lookups = new StubWeighSample();
  private Row slip = primaryScale();
  private DetailDocument doc;

  private static Row primaryScale() {
    return new Row().set("scale_event_type_code", "PS").set("scale_site_id_nmbr", "S001")
        .set("timber_mark", "ABC123").set("primary_license_number", "1234").set("weigh_slip_number", "123")
        .set("scale_date", ts("2026-10-01")).set("gross_weight", 30000L).set("tare_weight", 10000L)
        .set("hbs_sample_type_code", "N").set("field_scale_ind", "N");
  }

  private WeighSlipValidator validator() {
    doc = WeighSampleFixtures.document(DocType.WEIGH_SLIP, slip);
    return new WeighSlipValidator(doc, WeighSampleFixtures.context(ref), lookups);
  }

  private List<String> validate() {
    WeighSlipValidator w = validator();
    w.validate();
    return codes(w.errors());
  }

  private List<String> copyAndValidate() {
    WeighSlipValidator w = validator();
    w.validateAndCopyInputFields();
    w.validate();
    return codes(w.errors());
  }

  private void addMark(String mark, String fileType, String cruiseInd, String payment) {
    ref.marks.put(mark, new TimberMarkRef(mark, fileType, cruiseInd, payment, "00012345", "00", 1L, 1L, "RCO",
        "A", "HI", null, null, null));
    ref.rates.put(mark, ReferenceData.RateCheck.HAS_RATE);
    ref.gradeSchedules.put(mark, "C");
  }

  @Test
  void cleanPrimaryScaleHasNoErrorsAndIsBillable() {
    WeighSlipValidator w = validator();
    w.validate();
    assertThat(w.errors()).isEmpty();
    assertThat(w.isBillableScaleReturn()).isTrue();
  }

  // ── weights ───────────────────────────────────────────────────────

  @ParameterizedTest(name = "gross={0} tare={1} grapple={2} -> {3}")
  @CsvSource(nullValues = "null", value = {
      "0,null,null,GW01",
      "-5,null,null,GW01",
      "100,100,null,TW01",
      "100,150,null,TW01",
      "100,50,50,SDW01",
      "100,50,40,SDW01",
      "100,50,100,SDW02",
      "100,50,60,''",
      "100,null,50,''",
      "100,99,null,''",
      "null,10,null,''"})
  void weightEdits(Long gross, Long tare, Long grapple, String expected) {
    slip.set("gross_weight", gross).set("tare_weight", tare).set("sample_deduction_weight", grapple);
    List<String> want = expected.isEmpty() ? List.of() : List.of(expected);
    assertThat(validate()).isEqualTo(want);
  }

  @ParameterizedTest(name = "gross={0} tare={1} grapple={2} -> {3}")
  @CsvSource(nullValues = "null", value = {
      "30000,10000,null,20000",
      "30000,10000,0,20000",
      "30000,10000,25000,5000",
      "30000,null,null,30000",
      "null,10000,null,-10000",
      "null,null,null,0"})
  void sampleWeightIsGrossLessGrappleOrTare(Long gross, Long tare, Long grapple, long expected) {
    slip.set("gross_weight", gross).set("tare_weight", tare).set("sample_deduction_weight", grapple);
    assertThat(validator().calculateSampleWeight()).isEqualTo(expected);
  }

  // ── PSY ───────────────────────────────────────────────────────────

  @Test
  void billableInputPsyIsCopiedToPopulationStratumYear() {
    slip.set("input_psy", "ABCD0126");
    assertThat(copyAndValidate()).isEmpty();
    assertThat(slip.str("population_number")).isEqualTo("ABCD");
    assertThat(slip.str("stratum_number")).isEqualTo("01");
    assertThat(slip.lng("sampling_year")).isEqualTo(2026L);
    assertThat(slip.str("company_use_stratum")).isNull();
  }

  @Test
  void inputPsyWithCenturyIsCopied() {
    slip.set("input_psy", "ABCD012026");
    assertThat(copyAndValidate()).isEmpty();
    assertThat(slip.lng("sampling_year")).isEqualTo(2026L);
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({
      "ZZZZ0126,ST01,ZZZZ0126",
      "ABCD01,ST03,ABCD01",
      "ABCD01XX,ST04,ABCD01XX",
      "ABCD012099,ST01,ABCD0199"})
  void unbillableInputPsyBecomesCompanyStratumAndFailsOnValidate(String psy, String code, String company) {
    slip.set("input_psy", psy);
    WeighSlipValidator w = validator();
    w.validateAndCopyInputFields();
    // the input edit itself raises nothing (legacy raiseError = blank input PSY)
    assertThat(w.errors()).isEmpty();
    assertThat(slip.str("company_use_stratum")).isEqualTo(company);
    assertThat(slip.str("population_number")).isNull();
    w.validate();
    assertThat(codes(w.errors())).containsExactly(code);
  }

  @Test
  void validPsyOnANonBillableMarkIsST05() {
    addMark("CASH1", "A01", "N", "C");
    slip.set("timber_mark", "CASH1").set("population_number", "ABCD").set("stratum_number", "01")
        .set("sampling_year", 2026L);
    assertThat(validate()).containsExactly("ST05");
    assertThat(slip.str("company_use_stratum")).isEqualTo("ABCD0126");
    assertThat(slip.str("population_number")).isNull();
  }

  @Test
  void validPsyOnANonBillableEventIsST02() {
    slip.set("scale_event_type_code", "DP").set("hbs_sample_type_code", null)
        .set("company_use_stratum", "ABCD0126");
    assertThat(validate()).containsExactly("ST02");
  }

  @Test
  void psyCheckSkippedOnPrimaryScaleWithoutMark() {
    slip.set("timber_mark", null).set("company_use_stratum", "ZZZZ0126");
    assertThat(validate()).isEmpty();
    assertThat(slip.str("company_use_stratum")).isEqualTo("ZZZZ0126");
  }

  @Test
  void isBillableStratumNeedsALengthAndAStratumOnFile() {
    WeighSlipValidator w = validator();
    assertThat(w.isBillableStratum("ABCD0126")).isTrue();
    assertThat(w.isBillableStratum("ABCD012026")).isTrue();
    assertThat(w.isBillableStratum("ABCD0125")).isFalse();
    assertThat(w.isBillableStratum("ABCD016")).isFalse();
    assertThat(w.isBillableStratum("ABCD01XX")).isFalse();
  }

  // ── stratum date ──────────────────────────────────────────────────

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({
      "2025-12-31,SD04",
      "2026-01-01,''",
      "2026-12-31,''",
      "2027-01-01,SD05"})
  void stratumDate(String scaleDate, String expected) {
    slip.set("scale_date", ts(scaleDate)).set("population_number", "ABCD").set("stratum_number", "01")
        .set("sampling_year", 2026L);
    WeighSlipValidator w = validator();
    w.validateStratumDate();
    assertThat(codes(w.errors())).isEqualTo(expected.isEmpty() ? List.of() : List.of(expected));
    assertThat(w.errors()).allMatch(e -> e.category().equals("BIL"));
  }

  @Test
  void stratumDateThrowsWhenTheStratumVanished() {
    slip.set("population_number", "QQQQ").set("stratum_number", "01").set("sampling_year", 2026L);
    WeighSlipValidator w = validator();
    assertThatThrownBy(w::validateStratumDate).hasMessageContaining("Unexpected stratum removal");
  }

  // ── mark, sample type ─────────────────────────────────────────────

  static Stream<Arguments> sampleMarks() {
    return Stream.of(
        Arguments.of("A01", "N", "C", List.of("TM08")),
        Arguments.of("A01", "Y", "B", List.of("TM07")),
        Arguments.of("A11", "Y", "B", List.of("TM11")),
        Arguments.of("A01", "N", "B", List.of()));
  }

  @ParameterizedTest
  @MethodSource("sampleMarks")
  void sampleScaleMarkMustBeBillable(String fileType, String cruise, String payment, List<String> expected) {
    addMark("M1", fileType, cruise, payment);
    slip.set("scale_event_type_code", "SS").set("timber_mark", "M1");
    WeighSlipValidator w = validator();
    w.validateTimberMark();
    assertThat(codes(w.errors())).isEqualTo(expected);
    assertThat(w.errors()).allMatch(e -> e.category().equals("REL"));
  }

  @Test
  void primaryScaleIgnoresCashSaleMark() {
    addMark("M1", "A01", "N", "C");
    slip.set("timber_mark", "M1");
    WeighSlipValidator w = validator();
    w.validateTimberMark();
    assertThat(w.errors()).isEmpty();
  }

  @Test
  void unknownMarkIsTM01() {
    slip.set("timber_mark", "NOPE");
    WeighSlipValidator w = validator();
    w.validateTimberMark();
    assertThat(codes(w.errors())).containsExactly("TM01");
  }

  @ParameterizedTest(name = "{0}/{1} -> {2}")
  @CsvSource({
      "SS,F,''", "SS,S,''", "SS,N,SAM01", "RS,C,SAM01",
      "PS,N,''", "PD,C,''", "PS,F,SAM02", "PD,S,SAM02",
      "RT,X,''"})
  void sampleType(String event, String type, String expected) {
    slip.set("scale_event_type_code", event).set("hbs_sample_type_code", type);
    WeighSlipValidator w = validator();
    w.validateSampleType();
    assertThat(codes(w.errors())).isEqualTo(expected.isEmpty() ? List.of() : List.of(expected));
  }

  // ── field deck, ratio, destination ────────────────────────────────

  @Test
  void fieldDeckMustBeUpperCaseAlphanumeric() {
    slip.set("field_scale_deck_id", "d1").set("field_scale_ind", "Y");
    assertThat(validate()).containsExactly("FSX03");
    slip = primaryScale().set("field_scale_deck_id", "D-1").set("field_scale_ind", "Y");
    assertThat(validate()).containsExactly("FSX03");
  }

  @Test
  void fieldDeckNeedsAFieldScaleEstimate() {
    slip.set("field_scale_deck_id", "D1").set("field_scale_ind", "Y");
    lookups.fieldScaleEstimates = 0;
    assertThat(validate()).containsExactly("FSX02");
    assertThat(lookups.calls).contains(Arrays.asList("fieldScaleEstimateCount", "D1", "ABC123"));
    lookups.fieldScaleEstimates = 1;
    assertThat(validate()).isEmpty();
  }

  @Test
  void fieldDeckOnADefaultRatioPopulationIsFSD03() {
    ref.populations.put("ABCD/2026", new PopulationRef("ABCD", 2026L, "00012345", "00", null, null, "D"));
    slip.set("field_scale_deck_id", "D1").set("field_scale_ind", "Y").set("population_number", "ABCD")
        .set("stratum_number", "01").set("sampling_year", 2026L);
    assertThat(validate()).containsExactly("FSD03");
  }

  @Test
  void sampleOnADefaultRatioPopulationIsSRT03() {
    ref.populations.put("ABCD/2026", new PopulationRef("ABCD", 2026L, "00012345", "00", null, null, "D"));
    slip.set("scale_event_type_code", "SS").set("hbs_sample_type_code", "S").set("population_number", "ABCD")
        .set("stratum_number", "01").set("sampling_year", 2026L);
    assertThat(validate()).containsExactly("SRT03");
  }

  /**
   * {@code StringUtil.empty(population, false)} trims (WeighSlipValidator 857/888,
   * DetailScaleDocumentValidator.findStratum 1827): a whitespace-only population
   * is "no population", so the default-ratio lookup is skipped.
   */
  @Test
  void whitespacePopulationSkipsTheDefaultRatioLookup() {
    slip.set("field_scale_deck_id", "D1").set("field_scale_ind", "Y").set("population_number", "    ")
        .set("sampling_year", 2026L);
    assertThat(validate()).doesNotContain("FSD03");
    slip = primaryScale();
    slip.set("scale_event_type_code", "SS").set("hbs_sample_type_code", "S").set("population_number", " ")
        .set("sampling_year", 2026L);
    assertThat(validate()).doesNotContain("SRT03");
  }

  @Test
  void missingPopulationAbortsTheEdits() {
    slip.set("scale_event_type_code", "SS").set("hbs_sample_type_code", "S").set("population_number", "ABCD")
        .set("stratum_number", "01").set("sampling_year", 2025L);
    assertThatThrownBy(this::validate).hasMessageContaining("getPopulation");
  }

  @Test
  void fieldScaleWithoutDeckIsFSD01AndDeckWithoutFieldScaleIsFSD02() {
    slip.set("field_scale_ind", "Y");
    assertThat(validate()).containsExactly("FSD01");
    slip = primaryScale().set("field_scale_deck_id", "D1");
    assertThat(validate()).containsExactly("FSD02");
    // not an original version: no FSD02
    WeighSlipValidator w = validator();
    slip.set("original_version_ind", "N");
    w.validate();
    assertThat(w.errors()).isEmpty();
  }

  @Test
  void destinationIsUpperCasedAndChecked() {
    ref.sites.put("ZZZ", new ScaleSiteRef("ZZZ", 1L, "00012345", "00", "Waste", null));
    ref.sites.put("D002", new ScaleSiteRef("D002", 1L, "00012345", "00", "xxx Cruise Based Site", null));
    slip.set("scale_event_type_code", "PD").set("destination", "zzz");
    assertThat(validate()).containsExactly("SSZZ");
    assertThat(slip.str("destination")).isEqualTo("ZZZ");
    slip = primaryScale().set("scale_event_type_code", "PD").set("destination", "D002");
    assertThat(validate()).containsExactly("CBDS00");
    slip = primaryScale().set("scale_event_type_code", "PD").set("destination", "NOPE");
    assertThat(validate()).containsExactly("SS01");
    // PS does not check the destination
    slip = primaryScale().set("destination", "NOPE");
    assertThat(validate()).isEmpty();
  }

  @Test
  void weighSlipNumberMustBeNumeric() {
    slip.set("weigh_slip_number", "12A");
    assertThat(validate()).containsExactly("WSN01");
  }

  // ── cross checks ──────────────────────────────────────────────────

  @Test
  void redTagUsedByMoreThanOneLogTallyIsMM06() {
    slip.set("scale_event_type_code", "RT").set("hbs_sample_type_code", null);
    lookups.redTagLogTallies = 2;
    assertThat(validate()).containsExactly("MM06");
    assertThat(lookups.calls.get(0)).isEqualTo(Arrays.asList("redTagLogTallyCount", "S001", null, "123", "RT"));
    lookups.redTagLogTallies = 1;
    assertThat(validate()).isEmpty();
  }

  @Test
  void remoteRedTagSearchesTheDestination() {
    slip.set("scale_event_type_code", "RR").set("hbs_sample_type_code", null).set("destination", "S001");
    lookups.redTagLogTallies = 2;
    assertThat(validate()).containsExactly("MM06");
    assertThat(lookups.calls.get(0)).isEqualTo(Arrays.asList("redTagLogTallyCount", "S001", "S001", "123", "RR"));
  }

  @Test
  void conflictingSampleTalliesAreMM05() {
    slip.set("scale_event_type_code", "SS").set("hbs_sample_type_code", "S").set("population_number", "ABCD")
        .set("stratum_number", "01").set("sampling_year", 2026L);
    lookups.sampleTallyConflict = 1;
    List<String> got = validate();
    assertThat(got).containsExactly("MM05");
    assertThat(lookups.calls.get(0)).isEqualTo(Arrays.asList("sampleTallyConflict", "S001", null, "123", "SS",
        "ABC123", "ABCD", "01", 2026L, 20000L));
  }

  @Test
  void sampleCrossCheckUsesTheCompanyStratumWithoutPsy() {
    slip.set("scale_event_type_code", "RS").set("hbs_sample_type_code", "S").set("destination", "S001")
        .set("company_use_stratum", "ZZZZ01XX");
    WeighSlipValidator w = validator();
    w.validateXCheckSampleLogTally();
    // bad year: population "" / stratum "" / no year
    assertThat(lookups.calls.get(0)).isEqualTo(Arrays.asList("sampleTallyConflict", "S001", "S001", "123", "RS",
        "ABC123", "", "", null, 20000L));
    lookups.calls.clear();
    slip.set("company_use_stratum", "ZZZZ0126");
    w.validateXCheckSampleLogTally();
    assertThat(lookups.calls.get(0)).isEqualTo(Arrays.asList("sampleTallyConflict", "S001", "S001", "123", "RS",
        "ABC123", "ZZZZ", "01", 2026L, 20000L));
  }

  // ── input copy, billable ──────────────────────────────────────────

  @Test
  void inputFieldsAreCopied() {
    slip = new Row().set("scale_event_type_code", "PS").set("input_scale_site", "S001")
        .set("input_source", "ABC123").set("input_cutblock_id", "CB1").set("input_primary_scaler_lic", "1234")
        .set("input_secondary_scaler_lic", "5678").set("input_signing_scaler_lic", "1234");
    WeighSlipValidator w = validator();
    w.validateAndCopyInputFields();
    assertThat(w.errors()).isEmpty();
    assertThat(slip.str("scale_site_id_nmbr")).isEqualTo("S001");
    assertThat(slip.str("timber_mark")).isEqualTo("ABC123");
    assertThat(slip.str("cut_block_id")).isEqualTo("CB1"); // CR 63: copied without the edit
    assertThat(slip.str("primary_license_number")).isEqualTo("1234");
    assertThat(slip.str("secondary_license_number")).isEqualTo("5678");
    assertThat(slip.str("signing_license_number")).isEqualTo("1234");
  }

  @Test
  void invalidInputsAreReported() {
    slip = new Row().set("scale_event_type_code", "PS").set("input_scale_site", "NOPE")
        .set("input_source", "NOPE").set("input_primary_scaler_lic", "9999");
    WeighSlipValidator w = validator();
    w.validateAndCopyInputFields();
    assertThat(codes(w.errors())).containsExactly("SS01", "TM01", "PSL01");
  }

  @Test
  void timberBrandOfTheOwnSiteIsTB03() {
    slip.set("timber_mark", null).set("timber_brand", "BRAND1");
    WeighSlipValidator w = validator();
    w.validateAndCopyInputFields();
    assertThat(codes(w.errors())).containsExactly("TB03");
  }

  @Test
  void billable() {
    WeighSlipValidator w = validator();
    assertThat(w.isBillableScaleReturn()).isTrue();
    slip.set("field_scale_ind", "Y");
    assertThat(w.isBillableScaleReturn()).isFalse();
    slip.set("field_scale_ind", "N").set("scale_event_type_code", "DP");
    assertThat(w.isBillableScaleReturn()).isFalse();
    slip.set("scale_event_type_code", "SS");
    assertThat(w.isBillableScaleReturn()).isTrue();
    w.errors().add(EditError.version("SCL", "X"));
    assertThat(w.isBillableScaleReturn()).isFalse();
  }

  @Test
  void copyInputFieldsAndValidateRunsBothOnAnEditableVersion() {
    slip.set("weigh_slip_number", "X");
    WeighSlipValidator w = validator();
    w.copyInputFieldsAndValidate();
    assertThat(codes(w.errors())).containsExactly("WSN01");
    assertThat(doc.errors()).hasSize(1);
  }
}
