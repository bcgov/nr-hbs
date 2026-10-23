package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.WeighSampleFixtures.codes;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.WeighSampleFixtures.ts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.detailreturns.StubReferenceData;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailVersion;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.LogLine;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.DocumentState;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.VersionState;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.PopulationRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.ScaleSiteRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.StratumRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.TimberMarkRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.WeighSampleFixtures.StubTally;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.WeighSampleFixtures.StubWeighSample;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/** {@link SampleLogTallyValidator} (legacy {@code $E/dac/detail/control/SampleLogTallyValidator}). */
class SampleLogTallyValidatorTest {

  private final StubReferenceData ref = WeighSampleFixtures.reference();
  private final StubWeighSample lookups = new StubWeighSample();
  private Row tally = localSample();
  private final List<Row> lines = new java.util.ArrayList<>(List.of(line(1)));

  SampleLogTallyValidatorTest() {
    lookups.sampledWeighSlip = matchingWeighSlip();
  }

  private static Row localSample() {
    return new Row().set("scale_event_type_code", "SS").set("scale_site_id_nmbr", "S001")
        .set("timber_mark", "ABC123").set("primary_license_number", "1234").set("sampled_weigh_slip", "123")
        .set("scale_date", ts("2026-10-01")).set("sample_weight", 20000L).set("population_number", "ABCD")
        .set("stratum_number", "01").set("sampling_year", 2026L).set("input_psy", "ABCD0126").set("log_count", 1L);
  }

  private static Row line(long logNumber) {
    return new Row().set("log_number", logNumber).set("length", new BigDecimal("5.0")).set("top", 20L)
        .set("butt", 25L).set("net_volume", new BigDecimal("1.5")).set("input_species", "F")
        .set("input_grade", "J").set("scale_grade_code", "J");
  }

  private static Row matchingWeighSlip() {
    return new Row().set("scale_event_type_code", "SS").set("population_number", "ABCD").set("stratum_number", "01")
        .set("sampling_year", 2026L).set("timber_mark", "ABC123").set("gross_weight", 30000L)
        .set("tare_weight", 10000L).set("scale_date", ts("2026-09-30"));
  }

  private SampleLogTallyValidator validator() {
    DetailDocument doc = WeighSampleFixtures.document(DocType.SAMPLE_LOG_TALLY, tally);
    lines.forEach(l -> doc.version().logs().add(new LogLine(l)));
    return new SampleLogTallyValidator(doc, WeighSampleFixtures.context(ref), new StubTally(), lookups);
  }

  private List<String> run(Consumer<SampleLogTallyValidator> edit) {
    SampleLogTallyValidator s = validator();
    edit.accept(s);
    return codes(s.errors());
  }

  private void addMark(String mark, String fileType, String cruiseInd, String payment) {
    ref.marks.put(mark, new TimberMarkRef(mark, fileType, cruiseInd, payment, "00012345", "00", 1L, 1L, "RCO",
        "A", "HI", null, null, null));
    ref.gradeSchedules.put(mark, "C");
  }

  @Test
  void cleanLocalSampleHasNoErrors() {
    SampleLogTallyValidator s = validator();
    s.copyInputFieldsAndValidate();
    assertThat(s.errors()).isEmpty();
    assertThat(lines.get(0).str("scale_species_code")).isEqualTo("F");
    assertThat(lines.get(0).str("scale_grade_code")).isEqualTo("J");
    assertThat(s.isBillableScaleReturn()).isTrue();
  }

  // ── PSY ───────────────────────────────────────────────────────────

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({
      "ABCD0126,''",
      "ABCD012026,''",
      "ABCD01,ST01",
      "AB1D0126,ST01",
      "ABCD0X26,ST01",
      "ABCD01XX,ST01",
      "ZZZZ0126,ST01"})
  void inputPsy(String psy, String expected) {
    tally.set("population_number", null).set("stratum_number", null).set("sampling_year", null)
        .set("input_psy", psy);
    List<String> got = run(SampleLogTallyValidator::validateInputPSY);
    assertThat(got).isEqualTo(expected.isEmpty() ? List.of() : List.of(expected));
    if (expected.isEmpty()) {
      assertThat(tally.str("population_number")).isEqualTo("ABCD");
      assertThat(tally.str("stratum_number")).isEqualTo("01");
      assertThat(tally.lng("sampling_year")).isEqualTo(2026L);
    } else {
      assertThat(tally.str("population_number")).isNull();
    }
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource(nullValues = "null", value = {
      "ABCD0126,false",
      "1234AB26,false",
      "ABCD012026,false",
      "null,true",
      "ABCD01,true",
      "ABCD015,true",
      "AB1D0126,true",
      "ABCDX126,true",
      "ABCD01XX,true",
      "'  ABCD01',true"})
  void malformedPsy(String psy, boolean malformed) {
    assertThat(SampleLogTallyValidator.isPopOrStratrNullOrEmpty(psy)).isEqualTo(malformed);
  }

  @Test
  void inputPsyIsOnlyCopiedWhenThePsyIsNotSet() {
    tally.set("population_number", null).set("input_psy", "ZZZZ0126");
    lookups.sampledWeighSlip = null; // skip the BT 14566 comparison
    assertThat(run(SampleLogTallyValidator::validateAndCopyVersionInputFields)).containsExactly("ST01");
    tally = localSample().set("input_psy", "ZZZZ0126");
    assertThat(run(SampleLogTallyValidator::validateAndCopyVersionInputFields)).isEmpty();
  }

  // ── version edits ─────────────────────────────────────────────────

  @ParameterizedTest(name = "weight={0}")
  @CsvSource(nullValues = "null", value = {"null,SW01", "0,SW01", "-1,SW01", "1,''"})
  void sampleWeight(Long weight, String expected) {
    tally.set("sample_weight", weight);
    assertThat(run(SampleLogTallyValidator::validateSampleWeight))
        .isEqualTo(expected.isEmpty() ? List.of() : List.of(expected));
  }

  static Stream<Arguments> marks() {
    return Stream.of(
        Arguments.of("A01", "N", "C", List.of("TM08")),
        Arguments.of("A01", "Y", "B", List.of("TM07")),
        Arguments.of("A01", "Y", "C", List.of("TM08", "TM07")),
        Arguments.of("A11", "Y", "C", List.of("TM08", "TM11")),
        Arguments.of("A01", "N", "B", List.of()));
  }

  @ParameterizedTest
  @MethodSource("marks")
  void markMustBeBillable(String fileType, String cruise, String payment, List<String> expected) {
    addMark("M1", fileType, cruise, payment);
    tally.set("timber_mark", "M1");
    assertThat(run(SampleLogTallyValidator::validateTimberMark)).isEqualTo(expected);
  }

  @Test
  void defaultRatioPopulationIsSRT01() {
    ref.populations.put("ABCD/2026", new PopulationRef("ABCD", 2026L, "00012345", "00", null, null, "D"));
    assertThat(run(SampleLogTallyValidator::validateVersion)).containsExactly("SRT01");
  }

  @Test
  void missingPopulationAbortsTheEdits() {
    tally.set("sampling_year", null);
    assertThatThrownBy(() -> run(SampleLogTallyValidator::validateVersion)).hasMessageContaining("getPopulation");
  }

  @Test
  void versionEditsInOrder() {
    tally.set("sample_weight", null).set("sampled_weigh_slip", "12A").set("log_count", 3L)
        .set("scale_date", ts("2026-10-22"));
    assertThat(run(SampleLogTallyValidator::validateVersion)).containsExactly("SW01", "SD03", "WSN01", "LC01");
  }

  @Test
  void cruiseBasedOriginatingSiteIsCBOS00() {
    ref.sites.put("D002", new ScaleSiteRef("D002", 1L, "00012345", "00", "xxx Cruise Based Site", null));
    tally.set("orig_redtag_scale_site_id_nmbr", "D002");
    assertThat(run(SampleLogTallyValidator::validateVersion)).containsExactly("CBOS00");
  }

  // ── stratum date (BT6519) ─────────────────────────────────────────

  private void expiredStratum(String event, String scaleDate) {
    ref.strata.put("EFGH/01/2026", new StratumRef("EFGH", "01", 2026L, "00012345", "00", "C", ts("2026-01-01"),
        ts("2026-06-30")));
    tally.set("scale_event_type_code", event).set("population_number", "EFGH").set("input_psy", "EFGH0126")
        .set("scale_date", ts(scaleDate)).set("orig_redtag_scale_site_id_nmbr", "S009");
  }

  @Test
  void scaleDateBeforeStratumIsSD04() {
    tally.set("scale_date", ts("2025-12-31"));
    assertThat(run(SampleLogTallyValidator::validateStratumDate)).containsExactly("SD04");
  }

  @Test
  void stratumDateSkippedWithoutAWellFormedInputPsy() {
    tally.set("scale_date", ts("2025-12-31")).set("input_psy", null);
    assertThat(run(SampleLogTallyValidator::validateStratumDate)).isEmpty();
  }

  @Test
  void expiredStratumOnACheckScaleIsSD05() {
    expiredStratum("CS", "2026-08-01");
    assertThat(run(SampleLogTallyValidator::validateStratumDate)).containsExactly("SD05");
    assertThat(lookups.calls).isEmpty();
  }

  @ParameterizedTest(name = "{0} slip {1} {2} -> {3}")
  @CsvSource({
      "SS,SS,2026-06-15,''",
      "SS,SS,2026-05-03,''",
      "SS,SS,2026-05-01,SD05",
      "SS,SS,2026-03-01,SD05",
      "SS,RS,2026-06-15,SD05",
      "RS,RS,2026-06-15,''",
      "RS,SS,2026-06-15,SD05",
      "SS,SS,2025-12-01,SD05"})
  void expiredStratumOnASampleNeedsAWeighSlipWithin90Days(String event, String slipEvent, String slipDate,
                                                          String expected) {
    expiredStratum(event, "2026-08-01");
    lookups.weighSlipsByCriteria = List.of(new Row().set("scale_event_type_code", slipEvent)
        .set("scale_date", ts(slipDate)));
    assertThat(run(SampleLogTallyValidator::validateStratumDate))
        .isEqualTo(expected.isEmpty() ? List.of() : List.of(expected));
    String site = "SS".equals(event) ? "S001" : "S009";
    assertThat(lookups.calls.get(0)).isEqualTo(Arrays.asList("weighSlipsByCriteria", site, "EFGH", "01", 2026L,
        "123"));
  }

  @Test
  void expiredStratumWithoutWeighSlipsIsSD05() {
    expiredStratum("SS", "2026-08-01");
    assertThat(run(SampleLogTallyValidator::validateStratumDate)).containsExactly("SD05");
  }

  @ParameterizedTest(name = "{0} / {1} -> {2}")
  @CsvSource({
      "2026-08-01T00:00:00,2026-05-03T00:00:00,90.0",
      "2026-05-03T00:00:00,2026-08-01T00:00:00,90.0",
      "2026-08-01T12:00:00,2026-08-01T00:00:00,0.5"})
  void daysDifference(String a, String b, float expected) {
    assertThat(SampleLogTallyValidator.daysDifference(java.sql.Timestamp.valueOf(a.replace('T', ' ')),
        java.sql.Timestamp.valueOf(b.replace('T', ' ')))).isEqualTo(expected);
  }

  // ── originating site ──────────────────────────────────────────────

  @Test
  void originatingSite() {
    ref.sites.put("ZZZ", new ScaleSiteRef("ZZZ", 1L, "00012345", "00", "Waste", null));
    tally.set("orig_redtag_scale_site_id_nmbr", "zzz");
    assertThat(run(SampleLogTallyValidator::validateOrgScaleSite)).containsExactly("SSZZ");
    tally.set("orig_redtag_scale_site_id_nmbr", "NOPE");
    assertThat(run(SampleLogTallyValidator::validateOrgScaleSite)).containsExactly("SS01");
    tally.set("orig_redtag_scale_site_id_nmbr", "S001");
    assertThat(run(SampleLogTallyValidator::validateOrgScaleSite)).isEmpty();
  }

  @Test
  void inputOriginatingSiteAndOriginalScalerAreCopied() {
    tally.set("input_orig_redtag_scl_site", "S001").set("input_orig_chk_scl_scaler_lic", "5678");
    assertThat(run(SampleLogTallyValidator::validateAndCopyVersionInputFields)).isEmpty();
    assertThat(tally.str("orig_redtag_scale_site_id_nmbr")).isEqualTo("S001");
    assertThat(tally.str("orig_chk_scl_license_number")).isEqualTo("5678");
  }

  // ── weigh slip cross checks (BT 8637 / 14566) ─────────────────────

  @ParameterizedTest(name = "{0} count={1} -> {2}")
  @CsvSource({"SS,0,MM01", "SS,2,MM11", "RS,0,MM03", "RS,2,MM13", "CS,0,''"})
  void sampledWeighSlipMustExistOnce(String event, long count, String expected) {
    tally.set("scale_event_type_code", event);
    lookups.sampledWeighSlips = count;
    List<String> got = run(SampleLogTallyValidator::validateXCheckWeighSlip);
    assertThat(got).isEqualTo(expected.isEmpty() ? List.of() : List.of(expected));
    if (!expected.isEmpty()) {
      assertThat(lookups.calls.get(0)).isEqualTo(Arrays.asList("sampledWeighSlipCount", "S001", null, "123", event,
          false));
    }
  }

  @Test
  void theOneSampledWeighSlipIsReadFromTheRightSite() {
    run(SampleLogTallyValidator::validateXCheckWeighSlip);
    assertThat(lookups.calls.get(1)).isEqualTo(Arrays.asList("sampledWeighSlip", "123", "S001"));
    lookups.calls.clear();
    tally.set("scale_event_type_code", "RS").set("orig_redtag_scale_site_id_nmbr", "S009");
    lookups.sampledWeighSlip = matchingWeighSlip().set("scale_event_type_code", "RS").set("destination", "S001");
    assertThat(run(SampleLogTallyValidator::validateXCheckWeighSlip)).isEmpty();
    assertThat(lookups.calls.get(1)).isEqualTo(Arrays.asList("sampledWeighSlip", "123", "S009"));
  }

  static Stream<Arguments> mismatches() {
    return Stream.of(
        Arguments.of("scale_date", ts("2026-10-02"), "MM09", "MM10"),
        Arguments.of("population_number", "WXYZ", "MM22", "MM42"),
        Arguments.of("population_number", null, "MM22", "MM42"),
        Arguments.of("sampling_year", 2025L, "MM23", "MM43"),
        Arguments.of("stratum_number", "02", "MM24", "MM44"),
        Arguments.of("timber_mark", "OTHER", "MM25", "MM45"),
        Arguments.of("tare_weight", 10001L, "MM26", "MM46"),
        Arguments.of("sample_deduction_weight", 25000L, "MM26", "MM46"));
  }

  @ParameterizedTest(name = "{0}={1} -> {2}/{3}")
  @MethodSource("mismatches")
  void sampledWeighSlipMustMatch(String column, Object value, String local, String remote) {
    Row ws = matchingWeighSlip().set(column, value);
    assertThat(run(s -> s.checkForErrors(ws))).containsExactly(local);
    tally.set("scale_event_type_code", "RS");
    Row rs = matchingWeighSlip().set("scale_event_type_code", "RS").set(column, value);
    assertThat(run(s -> s.checkForErrors(rs))).containsExactly(remote);
  }

  @Test
  void sameDayWeighSlipIsNotAfterTheSample() {
    Row ws = matchingWeighSlip().set("scale_date", java.sql.Timestamp.valueOf("2026-10-01 23:00:00"));
    assertThat(run(s -> s.checkForErrors(ws))).isEmpty();
  }

  @Test
  void eventTypeAndDestinationMismatches() {
    Row ws = matchingWeighSlip().set("scale_event_type_code", "RS");
    assertThat(run(s -> s.checkForErrors(ws))).containsExactly("MM21");
    tally.set("scale_event_type_code", "RS");
    Row rs = matchingWeighSlip().set("scale_event_type_code", "RS").set("destination", "S002");
    assertThat(run(s -> s.checkForErrors(rs))).containsExactly("MM47");
    Row ss = matchingWeighSlip();
    assertThat(run(s -> s.checkForErrors(ss))).containsExactly("MM41");
  }

  @Test
  void crossCheckErrorsAreLookupsExceptTheDate() {
    Row ws = matchingWeighSlip().set("scale_date", ts("2026-10-02")).set("timber_mark", "X");
    SampleLogTallyValidator s = validator();
    s.checkForErrors(ws);
    assertThat(s.errors()).extracting(e -> e.category() + ":" + e.code()).containsExactly("SCL:MM09", "LKP:MM25");
  }

  // ── logs ──────────────────────────────────────────────────────────

  @Test
  void lineEdits() {
    lines.clear();
    lines.add(line(1).set("length", BigDecimal.ZERO));
    lines.add(line(2).set("top", 0L).set("butt", 200L));
    lines.add(line(3).set("scale_grade_code", "7"));
    lines.add(line(3));
    SampleLogTallyValidator s = validator();
    s.validateLogs();
    assertThat(s.errors()).extracting(e -> e.code() + "@" + e.line().row().lng("log_number"))
        .containsExactly("LT01@1", "TP01@2", "BTMax@2", "ISB789@3", "LG00@3");
  }

  @ParameterizedTest(name = "volume={0} weight={1} -> valid={2}")
  @CsvSource({
      "1.5,20000,true",
      "9.999,1000,true",
      "10,1000,false",
      "9.9999,1000,false",
      "50,0,true",
      "0,1,true"})
  void sampleRatio(String volume, long weight, boolean valid) {
    assertThat(WeighSampleSupport.isValidRatio(new BigDecimal(volume), new BigDecimal(weight))).isEqualTo(valid);
  }

  @Test
  void highRatioIsSR01() {
    tally.set("sample_weight", 100L);
    SampleLogTallyValidator s = validator();
    s.validateLogs();
    assertThat(s.errors()).extracting(e -> e.category() + ":" + e.code()).containsExactly("LKP:SR01");
  }

  // ── check scale ───────────────────────────────────────────────────

  private DetailDocument original(Row row, String docState, String versionState) {
    Row r = localSample().set("detail_document_number", "ORIG").set("version", 1L).set("primary_license_number",
        "5678");
    row.asMap().forEach(r::set);
    VersionState vs = new VersionState(versionState, true);
    return new DetailDocument(DocType.SAMPLE_LOG_TALLY, new Row().set("detail_document_number", "ORIG"),
        new DocumentState(docState, vs), new DetailVersion(r, vs, true), true);
  }

  static Stream<Arguments> againstOriginal() {
    return Stream.of(
        Arguments.of(new Row(), "OIP", "RDY", List.of()),
        Arguments.of(new Row().set("scale_date", ts("2026-10-02")), "OIP", "RDY", List.of("CHKSD1")),
        Arguments.of(new Row().set("scale_date", ts("2026-08-16")), "OIP", "RDY", List.of("CHKSD2")),
        Arguments.of(new Row().set("scale_date", ts("2026-08-17")), "OIP", "RDY", List.of()),
        Arguments.of(new Row().set("scale_event_type_code", "CS"), "OIP", "RDY", List.of("CHKS03")),
        Arguments.of(new Row().set("check_replaces_original_ind", "Y"), "OIP", "RDY", List.of("CHKS06")),
        Arguments.of(new Row(), "OIP", "ERR", List.of("CHKS07")),
        Arguments.of(new Row(), "OCM", "RDY", List.of("CHKS07")),
        Arguments.of(new Row().set("sampled_weigh_slip", "999"), "RNP", "RDY", List.of("CHKS08")),
        Arguments.of(new Row().set("sample_weight", 1L), "RCP", "RDY", List.of("CHKS09")),
        Arguments.of(new Row().set("sampling_year", null), "OIP", "RDY", List.of("CHKS09")));
  }

  @ParameterizedTest
  @MethodSource("againstOriginal")
  void checkScaleAgainstOriginal(Row changes, String docState, String versionState, List<String> expected) {
    tally.set("scale_event_type_code", "CS");
    DetailDocument orig = original(changes, docState, versionState);
    assertThat(run(s -> s.validateAgainstOriginal(orig))).isEqualTo(expected);
  }

  @ParameterizedTest(name = "original {0} count={1} -> {2}")
  @CsvSource({"SS,0,MM07", "SS,2,MM17", "RS,0,MM08", "RS,2,MM18", "SS,1,''"})
  void checkScaleNeedsTheSampledWeighSlip(String originalEvent, long count, String expected) {
    tally.set("scale_event_type_code", "CS");
    lookups.sampledWeighSlips = count;
    DetailDocument orig = original(new Row().set("scale_event_type_code", originalEvent), "OIP", "RDY");
    assertThat(run(s -> s.validateAgainstOriginal(orig)))
        .isEqualTo(expected.isEmpty() ? List.of() : List.of(expected));
    assertThat(lookups.calls.get(0)).isEqualTo(Arrays.asList("sampledWeighSlipCount", "S001", null, "123",
        originalEvent, true));
  }

  @Test
  void dateEditsOnANonCheckEventSkipTheRest() {
    DetailDocument orig = original(new Row().set("scale_event_type_code", "CS"), "OIP", "RDY");
    assertThat(run(s -> s.validateAgainstOriginal(orig))).isEmpty();
    assertThat(lookups.calls).isEmpty();
  }

  // ── field deck (BT 10013), billable ───────────────────────────────

  @Test
  void sampleTakesTheSampledWeighSlipDeck() {
    lookups.deckByEvent.put("SS", "D1");
    run(SampleLogTallyValidator::validatFieldScaleDeckId);
    assertThat(tally.str("field_scale_deck_id")).isEqualTo("D1");
    assertThat(lookups.calls.get(0)).isEqualTo(Arrays.asList("sampledWeighSlipFieldScaleDeckId", "S001", "123",
        "SS"));
  }

  @ParameterizedTest(name = "local={0} remote={1} orig scaler={2} -> {3}")
  @CsvSource(nullValues = "null", value = {
      "D1,D2,5678,D1",
      "null,D2,5678,D2",
      "null,null,5678,null",
      "D1,D2,1234,null"})
  void checkScaleDeck(String local, String remote, String origScaler, String expected) {
    tally.set("scale_event_type_code", "CS").set("orig_chk_scl_license_number", origScaler)
        .set("field_scale_deck_id", "OLD");
    lookups.deckByEvent.put("SS", local);
    lookups.deckByEvent.put("RS", remote);
    run(SampleLogTallyValidator::validatFieldScaleDeckId);
    assertThat(tally.str("field_scale_deck_id")).isEqualTo(expected);
  }

  @ParameterizedTest(name = "{0} replaces={1} -> {2}")
  @CsvSource(nullValues = "null", value = {
      "CS,Y,true", "CS,N,false", "CS,null,false", "SS,Y,false", "SS,N,true", "SS,null,true"})
  void billable(String event, String replaces, boolean billable) {
    tally.set("scale_event_type_code", event).set("check_replaces_original_ind", replaces);
    assertThat(validator().isBillableScaleReturn()).isEqualTo(billable);
  }
}
