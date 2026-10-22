package ca.bc.gov.nrs.hbs.api.summaryreturns.edits;

import static ca.bc.gov.nrs.hbs.api.summaryreturns.edits.EditsFixture.FAR_FUTURE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.edits.EditsFixture.FAR_PAST;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.edits.EditsFixture.MARK;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.edits.EditsFixture.codes;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.edits.EditsFixture.load;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.summaryreturns.EditError;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.ScaleSite;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.Stratum;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.TimberMark;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.Worksheet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WeightEditsTest {

  private final EditsFixture f = new EditsFixture();

  private List<EditError> release() {
    return f.edits.applyEditsAndValidate(f.weight());
  }

  @Test
  void cleanReturnHasNoErrorsAndRatesTheLatestStratumRatios() {
    assertThat(release()).isEmpty();
    assertThat(f.api.names()).containsExactly("HBS_RATES.GET_OVERRIDE_AND_APPRAISAL", "HBS_RATES.RETRIEVE_NORMAL_RATE");
    assertThat(f.api.callsTo(Rates.RETRIEVE_NORMAL_RATE).get(0).arg("i_species")).isEqualTo("FI");
    // the rate period runs from the earliest daily load of the month to the scale date
    assertThat(f.lookups.called).contains("hasSplitRate 2026-09-10 2026-09-15");
  }

  private static TimberMark mark(String fileType, String cruise, String payment, String quota) {
    return new TimberMark(MARK, fileType, cruise, payment, 10, quota, "00012345", "00");
  }

  static Stream<Arguments> rules() {
    return Stream.of(
        rule("SD02", "DTE", f -> {
          f.weightVersion.set("scale_date", Timestamp.valueOf("2018-06-30 00:00:00"));
          f.dailyLoads.clear();
          f.dailyLoads.add(load(30, 1000));
        }),
        rule("SD03", "REL", f -> f.weightVersion.set("scale_date", Timestamp.valueOf("2026-11-30 00:00:00"))),
        rule("SD13", "REL", f -> f.dailyLoads.add(load(16, 1000))),
        rule("SD04", "REL", f -> f.lookups.strata.put("P1/S1/2026",
            new Stratum("00099999", "01", LocalDateTime.of(2026, 9, 16, 0, 0), FAR_FUTURE, "C"))),
        rule("SD05", "REL", f -> f.lookups.strata.put("P1/S1/2026",
            new Stratum("00099999", "01", FAR_PAST, LocalDateTime.of(2026, 9, 14, 0, 0), "C"))),
        rule("CG04", "SCL", f -> f.lookups.strata.put("P1/S1/2026",
            new Stratum("00099999", "01", FAR_PAST, FAR_FUTURE, "I"))),
        rule("TM04", "REL", f -> f.lookups.marks.put(MARK, mark("A01", "N", "C", "A"))),
        rule("TM09", "REL", f -> f.lookups.marks.put(MARK, mark("A01", "Y", "S", "A"))),
        rule("CSB1D", "SCL", f -> f.lookups.worksheets.set(0, new Worksheet(MARK, FAR_PAST, "C", "S", "X"))),
        rule("ISB1C", "SCL", f -> f.lookups.worksheets.set(0, new Worksheet(MARK, FAR_PAST, "I", "B", "S"))),
        rule("FB01", "FMT", f -> {
          f.weightVersion.set("scl_rtn_category_code", "FB");
          f.dailyLoads.replaceAll(l -> load(l.lng("day"), 0));
          f.lookups.marks.put(MARK, mark("A07", "N", "S", "A"));
        }),
        rule("RT01", "REL", f -> f.dailyLoads.replaceAll(l -> load(l.lng("day"), 0))),
        rule("RT02", "REL", f -> f.weightVersion.set("scl_rtn_category_code", "NB")),
        rule("CBSS00", "SCL", f -> f.lookups.sites.put("S123",
            new ScaleSite("S123", "00099999", "01", "DKA Cruise Based Site", 10L))),
        rule("BT03", "FMT", f -> f.lookups.locations.remove("00012345/00")),
        rule("CT01", "FMT", f -> f.weightVersion.set("client_number_copied_to", "00055555")
            .set("client_locn_code_copied_to", "01")),
        rule("SD17", "REL", f -> f.rate.put("o_rate_found", "N")),
        rule("SD12", "REL", f -> f.lookups.splitRate = true));
  }

  private static Arguments rule(String code, String category, Consumer<EditsFixture> setup) {
    return Arguments.of(code, category, setup);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rules")
  void eachRuleRaisesItsCode(String code, String category, Consumer<EditsFixture> setup) {
    setup.accept(f);
    List<EditError> errors = release();
    if (code.equals("CT01")) {
      // an unknown copy-to client is CT01, and its location CT03
      assertThat(codes(errors)).containsExactly("CT01", "CT03");
    } else {
      assertThat(codes(errors)).containsExactly(code);
    }
    assertThat(errors.get(0)).isEqualTo(EditError.version(category, code));
  }

  @Test
  void bt01AndBt03WhenThePaidByClientIsUnknown() {
    f.weightVersion.set("client_number_paid_by", "00077777").set("client_locn_code_paid_by", "00");
    assertThat(codes(release())).containsExactly("BT01", "BT03");
  }

  @Test
  void ig04ForAnInteriorMarkInACoastalStratum() {
    f.lookups.gradeSchedule = "I";
    assertThat(codes(release())).containsExactly("IG04");
  }

  @Test
  void areaBasedMarkRaisesTm10AndTm12() {
    f.lookups.marks.put(MARK, mark("A11", "Y", "S", "A"));
    assertThat(codes(release())).containsExactly("TM10", "TM12");
  }

  @Test
  void finalBillOnARenewableOrDropoutMarkIsBillable() {
    f.weightVersion.set("scl_rtn_category_code", "FB");
    f.dailyLoads.replaceAll(l -> load(l.lng("day"), 0));
    assertThat(release()).isEmpty();
    f.lookups.marks.put(MARK, mark("A07", "N", "S", "A"));
    f.lookups.dropouts.add("P1/2026/" + MARK);
    assertThat(f.edits.applyEditsAndValidate(f.weight())).isEmpty();
    // final and nil bills are rated without the HBS_RATES procs
    assertThat(f.api.calls).isEmpty();
  }

  @Test
  void movesTheInputTimberMarkAndStratum() {
    f.weightVersion.set("timber_mark", null).set("population_number", null).set("stratum_number", null)
        .set("sampling_year", null);
    SummaryReturn ret = f.weight();
    assertThat(f.edits.applyEditsAndValidate(ret)).isEmpty();
    assertThat(ret.version().str("timber_mark")).isEqualTo(MARK);
    assertThat(ret.version().str("population_number")).isEqualTo("P1");
    assertThat(ret.version().str("stratum_number")).isEqualTo("S1");
    assertThat(ret.version().lng("sampling_year")).isEqualTo(2026L);
  }

  @Test
  void st01ForAnUnknownStratumOrANonNumericYear() {
    f.weightVersion.set("population_number", null).set("input_stratum", "S9");
    WeightEdits edits = new WeightEdits(f.weight(), f.lookups, f.rates, f.clock);
    edits.validateAndMoveInputFields();
    assertThat(edits.errors).containsExactly(EditError.version("LKP", "ST01"));

    EditsFixture g = new EditsFixture();
    g.weightVersion.set("population_number", null).set("input_sampling_year", "2X");
    WeightEdits other = new WeightEdits(g.weight(), g.lookups, g.rates, g.clock);
    other.validateAndMoveInputFields();
    assertThat(other.errors).containsExactly(EditError.version("LKP", "ST01"));
  }

  @Test
  void tm01ThenTheTimberMarkEditFailsAsLegacyDid() {
    f.weightVersion.set("timber_mark", null).set("input_timber_mark", "NOPE");
    WeightEdits edits = new WeightEdits(f.weight(), f.lookups, f.rates, f.clock);
    edits.validateAndMoveInputFields();
    assertThat(edits.errors).containsExactly(EditError.version("LKP", "TM01"));
    assertThatThrownBy(this::release).isInstanceOf(ReleaseEditException.class).hasMessageContaining("Valid timbermark");
  }

  @Test
  void convertsTwoDigitYearsAsLegacy() throws Exception {
    assertThat(ReturnEdits.convertYearToLong("26")).isEqualTo(2026L);
    assertThat(ReturnEdits.convertYearToLong("97")).isEqualTo(1997L);
    assertThat(ReturnEdits.convertYearToLong("5")).isEqualTo(205L);
    assertThatThrownBy(() -> ReturnEdits.convertYearToLong(null)).isInstanceOf(ReleaseEditException.class);
  }
}
