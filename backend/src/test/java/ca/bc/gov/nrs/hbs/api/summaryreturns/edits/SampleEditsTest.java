package ca.bc.gov.nrs.hbs.api.summaryreturns.edits;

import static ca.bc.gov.nrs.hbs.api.summaryreturns.edits.EditsFixture.FAR_FUTURE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.edits.EditsFixture.FAR_PAST;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.edits.EditsFixture.MARK;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.edits.EditsFixture.codes;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.edits.EditsFixture.sampleSeg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.summaryreturns.EditError;
import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.DateWindow;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.Population;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.ScaleSite;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.Stratum;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.TimberMark;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SampleEditsTest {

  private final EditsFixture f = new EditsFixture();

  private List<EditError> release() {
    return f.edits.applyEditsAndValidate(f.sample());
  }

  @Test
  void cleanReturnHasNoErrorsAndNoRateCalls() {
    assertThat(release()).isEmpty();
    assertThat(f.api.calls).isEmpty();
  }

  private static TimberMark mark(String fileType, String cruise, String payment) {
    return new TimberMark(MARK, fileType, cruise, payment, 10, "A", "00012345", "00");
  }

  static Stream<Arguments> versionRules() {
    return Stream.of(
        rule("SD02", "DTE", f -> f.sampleVersion.set("scale_date", Timestamp.valueOf("2010-01-01 00:00:00"))),
        rule("SD03", "REL", f -> f.sampleVersion.set("scale_date", Timestamp.valueOf("2026-10-22 00:00:00"))),
        rule("LC01", "REL", f -> f.sampleVersion.set("log_count", 11L)),
        rule("NW01", "VAL", f -> f.sampleVersion.set("net_weight", 0L)),
        rule("LA00", "VAL", f -> f.sampleVersion.set("load_arrival_number", "A1")),
        rule("CBSS00", "SCL", f -> f.lookups.sites.put("S123",
            new ScaleSite("S123", "00099999", "01", "DKA Cruise Based Site", 10L))),
        rule("TM07", "REL", f -> f.lookups.marks.put(MARK, mark("A01", "Y", "S"))),
        rule("TM11", "REL", f -> f.lookups.marks.put(MARK, mark("A11", "Y", "S"))),
        rule("TM08", "REL", f -> f.lookups.marks.put(MARK, mark("A01", "N", "C"))),
        rule("SRT02", "SCL", f -> f.lookups.populations.put("P1/2026", new Population("00012345", "00", "D"))),
        rule("SD04", "REL", f -> f.lookups.strata.put("P1/S1/2026",
            new Stratum("00099999", "01", LocalDateTime.of(2026, 9, 15, 0, 0, 1), FAR_FUTURE, "C"))),
        rule("SD05", "REL", f -> f.lookups.strata.put("P1/S1/2026",
            new Stratum("00099999", "01", FAR_PAST, LocalDateTime.of(2026, 9, 14, 23, 59), "C"))),
        rule("CG03", "SCL", f -> f.lookups.strata.put("P1/S1/2026",
            new Stratum("00099999", "01", FAR_PAST, FAR_FUTURE, "I"))),
        rule("SV00", "FMT", f -> f.sampleSegs.add(sampleSeg(21, "FI", "U", 10).set("volume", null))),
        rule("BT03", "FMT", f -> f.lookups.locations.remove("00012345/00")),
        rule("CT04", "REL", f -> f.lookups.locations.put("00099999/01", new Lookups.ClientLocation(
            f.lookups.clients.get("00099999"), "00099999", "01", "Victoria", "BC", "V8W1A1", "Y"))));
  }

  private static Arguments rule(String code, String category, Consumer<EditsFixture> setup) {
    return Arguments.of(code, category, setup);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("versionRules")
  void eachVersionRuleRaisesItsCode(String code, String category, Consumer<EditsFixture> setup) {
    setup.accept(f);
    assertThat(release()).containsExactly(EditError.version(category, code));
  }

  @Test
  void segregationEdits() {
    f.sampleVersion.set("log_count", 10L);
    f.sampleSegs.add(sampleSeg(21, "FI", "U", 0));
    f.sampleSegs.add(sampleSeg(22, "FI", "U", 3));
    f.sampleSegs.add(sampleSeg(23, "FI", "7", 7));
    assertThat(release()).containsExactly(
        EditError.segregation(21, "VAL", "NP01"),
        EditError.segregation(22, "FMT", "SG03"),
        EditError.segregation(23, "SCL", "ISB789"));
  }

  @Test
  void movesInputLicenceTimberMarkStratumAndSpeciesProductGrade() {
    f.sampleVersion.set("license_number", null).set("timber_mark", null)
        .set("population_number", null).set("stratum_number", null).set("sampling_year", null);
    f.sampleSegs.add(sampleSeg(21, null, null, 10).set("scale_product_code", null)
        .set("input_species", "FI").set("input_product", " ").set("input_grade", "U"));
    SummaryReturn ret = f.sample();
    assertThat(f.edits.applyEditsAndValidate(ret)).isEmpty();
    Row v = ret.version();
    assertThat(v.str("license_number")).isEqualTo("K123");
    assertThat(v.str("timber_mark")).isEqualTo(MARK);
    assertThat(v.str("population_number")).isEqualTo("P1");
    assertThat(v.lng("sampling_year")).isEqualTo(2026L);
    assertThat(ret.segregations().get(0).str("scale_grade_code")).isEqualTo("U");
  }

  @Test
  void aMovedStratumIsDateCheckedTwiceAsInLegacy() {
    f.sampleVersion.set("population_number", null);
    f.lookups.strata.put("P1/S1/2026",
        new Stratum("00099999", "01", LocalDateTime.of(2026, 10, 1, 0, 0), FAR_FUTURE, "C"));
    assertThat(codes(release())).containsExactly("SD04", "SD04");
  }

  @Test
  void sl01Tm01St01() {
    f.sampleVersion.set("license_number", null).set("input_scaler_license_num", "X1")
        .set("timber_mark", null).set("input_timber_mark", "NOPE")
        .set("population_number", null).set("input_population", "P9");
    SampleEdits edits = new SampleEdits(f.sample(), f.lookups, f.clock);
    edits.validateAndMoveInputFields();
    assertThat(edits.errors).containsExactly(
        EditError.version("LKP", "SL01"), EditError.version("LKP", "TM01"), EditError.version("LKP", "ST01"));
  }

  @Test
  void st01ThenTheClientEditFailsAsLegacyDid() {
    f.sampleVersion.set("population_number", null).set("input_population", "P9");
    assertThatThrownBy(this::release).isInstanceOf(ReleaseEditException.class)
        .hasMessageContaining("Can't find owner for pop");
  }

  @Test
  void speciesProductGradeEditsOnSampleSegregations() {
    f.lookups.spg.put("FI/ /U", new DateWindow(FAR_PAST, LocalDateTime.of(2020, 1, 1, 0, 0)));
    assertThat(release()).containsExactly(EditError.segregation(21, "SCL", "SP02"));
  }

  @Test
  void applyEditsForClientUsesThePopulationAndStratumOwners() {
    f.lookups.locations.remove("00099999/01");
    assertThat(codes(f.edits.applyEditsForClient(f.sample()))).containsExactly("CT03");
  }

  @Test
  void loadArrivalNumber() {
    assertThat(SampleEdits.isValidLoadArrivalNumber("1")).isTrue();
    assertThat(SampleEdits.isValidLoadArrivalNumber("0")).isFalse();
    assertThat(SampleEdits.isValidLoadArrivalNumber(null)).isFalse();
    assertThat(SampleEdits.isValidLoadArrivalNumber(" 1")).isFalse();
  }
}
