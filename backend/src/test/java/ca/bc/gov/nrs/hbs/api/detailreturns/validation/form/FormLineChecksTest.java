package ca.bc.gov.nrs.hbs.api.detailreturns.validation.form;

import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.FormFixtures.defect;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.FormFixtures.line;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.FormFixtures.list;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.FormFixtures.logLine;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.FormFixtures.logTally;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.FormFixtures.map;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.FormFixtures.messages;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.FormFixtures.sampleTally;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.FormFixtures.sfpLine;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.FormFixtures.sfpTally;
import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException.Problem;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** {@link FormLineChecks}: log tally, sample tally, SFP lines and defects. */
class FormLineChecksTest {

  private final PermissiveFormLookups lookups = new PermissiveFormLookups();
  private final DetailFormValidator validator = new DetailFormValidator(lookups);

  /** Lines saved alone (no header keys), scale method as given. */
  private List<Problem> logLines(String scaleMethod, Map<String, Object> fields) {
    Map<String, Object> header = scaleMethod == null ? map() : map("volumeCalcMethodType", scaleMethod);
    return validator.validate(DocType.LOG_TALLY, false, header, list(line(fields)));
  }

  private static Map<String, Object> with(Map<String, Object> m, Object... kv) {
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  // ── log tally lines (P552Form) ───────────────────────────────────

  @Test
  void cleanLogLinePasses() {
    assertThat(logLines("G", logLine())).isEmpty();
    assertThat(validator.validate(DocType.LOG_TALLY, true, logTally(), list(line(logLine())))).isEmpty();
  }

  @Test
  void emptyNewLinesAreSkipped() {
    assertThat(logLines("G", map("logNumber", null, "length", "", "nmvInd", "N"))).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
      "length,logs[0].length,Length is required.", "top,logs[0].top,Top is required.",
      "butt,logs[0].butt,Butt is required.", "scaleSpeciesCode,logs[0].scaleSpeciesCode,Species is required.",
      "logNumber,logs[0].logNumber,Log Number is required."})
  void rowFieldsRequiredByTheScaleMethod(String key, String field, String message) {
    assertThat(logLines("N", with(logLine(), key, null))).containsExactly(new Problem(field, message));
    // no scale method (online log tallies): nothing is required
    assertThat(logLines(null, with(logLine(), key, null))).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
      "length,100,Log length must be less than 100 metres",
      "length,-1,Length must be an unsigned valid number",
      "length,abc,Length must be an unsigned valid number",
      "length,5.25,Length can have no more than one digit to the right of the decimal point",
      "length,99.9,",
      "top,-1,Top must be an unsigned integer", "top,2.5,Top must be an unsigned integer",
      "butt,x,Butt must be an unsigned integer",
      "logNumber,0,Log Number must be an integer.", "logNumber,x,Log Number must be an integer."})
  void logDetailsFormats(String key, String value, String message) {
    List<String> got = messages(logLines(null, with(logLine(), key, value)));
    assertThat(got).isEqualTo(message == null ? List.of() : List.of(message));
  }

  @Test
  void beachcombMarkUsesTheEditMessage() {
    lookups.unknownMarks.add("ZZZ999");
    assertThat(logLines(null, with(logLine(), "timberMark", "zzz999"))).containsExactly(
        new Problem("logs[0].timberMark", "BM02 Beachcomb mark not valid"));
    lookups.editErrorDescription = null;
    assertThat(messages(logLines(null, with(logLine(), "timberMark", "ZZZ999")))).containsExactly("MSGCODE:BM02,SCL");
  }

  @Test
  void speciesProductGradeCombination() {
    lookups.unknownSpg.add("FI/ /X");
    assertThat(logLines(null, with(logLine(), "scaleGradeCode", "x"))).containsExactly(
        new Problem("logs[0].scaleSpeciesCode", "Species and grade combination is invalid."));
    // product and grade default to one space
    lookups.spgCalls.clear();
    logLines(null, with(logLine(), "scaleSpeciesCode", "fi", "scaleGradeCode", null));
    assertThat(lookups.spgCalls).containsExactly("FI/ / ");
  }

  @Test
  void netVolumeRange() {
    assertThat(logLines(null, with(logLine(), "length", "99.9", "top", 999, "butt", 999))).containsExactly(
        new Problem("logs[0].netVolume", "Net volume must be less than 1000 m3"));
    List<Problem> less = validator.validate(DocType.LOG_TALLY, false, map(),
        list(line(null, logLine(), list(defect(1, "R", "5", "rot")))));
    assertThat(less).containsExactly(new Problem("logs[0].netVolume", "Net volume cannot be less than zero"));
    // not checked on a line that already has an error
    assertThat(messages(logLines(null, with(logLine(), "length", "99.9", "top", 999, "butt", 999,
        "logNumber", 0)))).containsExactly("Log Number must be an integer.");
  }

  // ── defects (P559Action / P659Action) ────────────────────────────

  @Test
  void cleanDefectPasses() {
    assertThat(validator.validate(DocType.LOG_TALLY, false, map(),
        list(line(null, logLine(), list(defect(1, "R", "0.012", "rot")))))).isEmpty();
  }

  @Test
  void defectRules() {
    List<Problem> p = validator.validate(DocType.SAMPLE_LOG_TALLY, false, map(), list(line(null, logLine(), list(
        defect(null, "R", "0.1", "rot"),
        defect(2, "R", null, "rot"),
        defect(2, " ", "1000", " "),
        defect(3, "R", "0.12345", "rot"),
        defect(null, null, null, null)))));
    assertThat(p).containsExactly(
        // the 1000 m3 defect also drives the line's net volume below zero
        new Problem("logs[0].netVolume", "Net volume cannot be less than zero"),
        new Problem("logs[0].defects[0].defectNumber", "Defect Number must be a valid number."),
        new Problem("logs[0].defects[1].volume", "Volume must be a valid number."),
        new Problem("logs[0].defects[2].defectNumber", "Defect Number must be unique."),
        new Problem("logs[0].defects[2].volume", "Defect Volume must be a valid number less than 1000.0."),
        new Problem("logs[0].defects[2].defectType", "A defect code is required."),
        new Problem("logs[0].defects[2].descriptor", "A defect description is required."),
        new Problem("logs[0].defects[3].volume", "Defect Volume must have 3 or less decimal places."));
  }

  @ParameterizedTest
  @CsvSource({"1.2,true", "1.234,true", "1.2345,false", "1.2340,true", "12,true", "0.12300,true"})
  void validPrecision(String value, boolean expected) {
    assertThat(FormLineChecks.isValidPrecision(value, 3)).isEqualTo(expected);
  }

  // ── sample tally lines (P658Action) ──────────────────────────────

  private List<Problem> sampleLine(Map<String, Object> fields) {
    return validator.validate(DocType.SAMPLE_LOG_TALLY, false, map(), list(line(fields)));
  }

  @Test
  void cleanSampleLinePasses() {
    assertThat(sampleLine(logLine())).isEmpty();
    assertThat(validator.validate(DocType.SAMPLE_LOG_TALLY, true, sampleTally(), list(line(logLine())))).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
      "logNumber,,logs[0].logNumber,Log Number must be a valid number.",
      "logNumber,0,logs[0].logNumber,Log Number must be a valid number.",
      "scaleSpeciesCode,,logs[0].scaleSpeciesCode,A species is required.",
      "length,,logs[0].length,Length must be an unsigned valid number",
      "length,-1,logs[0].length,Length must be an unsigned valid number",
      "length,100,logs[0].length,Log length must be less than 100 metres",
      "length,5.25,logs[0].length,Length can have no more than one digit to the right of the decimal point",
      "top,,logs[0].top,Top must be an unsigned integer", "top,-2,logs[0].top,Top must be an unsigned integer",
      "butt,,logs[0].butt,Butt must be an unsigned integer"})
  void sampleLineRules(String key, String value, String field, String message) {
    assertThat(sampleLine(with(logLine(), key, value))).containsExactly(new Problem(field, message));
  }

  @Test
  void sampleLineSpeciesProductGradeAndVolume() {
    lookups.unknownSpg.add("FI/ /J");
    assertThat(messages(sampleLine(logLine()))).containsExactly("Species and grade combination is invalid.");
    lookups.unknownSpg.clear();
    assertThat(sampleLine(with(logLine(), "length", "99.9", "top", 999, "butt", 999))).containsExactly(
        new Problem("logs[0].netVolume", "Net volume must be less than 1000 m3"));
  }

  @Test
  void sampleLineWithOnlyALogNumberIsNotValidated() {
    assertThat(sampleLine(map("logNumber", 3))).isEmpty();
  }

  // ── SFP lines (P752Form) ─────────────────────────────────────────

  private List<Problem> sfp(String method, Map<String, Object> fields) {
    return validator.validate(DocType.SFP_TALLY, false, map("hbsSfpScaleTypeCode", method), list(line(fields)));
  }

  @Test
  void cleanSfpLinePasses() {
    assertThat(sfp("L", sfpLine())).isEmpty();
    assertThat(validator.validate(DocType.SFP_TALLY, true, sfpTally(), list(line(sfpLine())))).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
      "prodLogLength,100,Length is not in the specified range.",
      "prodLogLength,0,Length is not in the specified range.",
      "prodLogLength,abc,",
      "stackLength,100,Stack Length is not in the specified range.",
      "prodLogTop,0,Top must be an integer.", "prodLogButt,x,Butt must be an integer.",
      "netVolume,999.999,Net Volume is not in the specified range.", "netVolume,999.998,",
      "stackWidth,1000,Stack Length is not in the specified range.",
      "stackHeight,-1,Stack Height is not in the specified range.",
      "pieceCount,100,Piece Count is not in the specified range.",
      "pieceCount,x,Piece Count is not in the specified range.", "pieceCount,99,",
      "conversionFactor,1000,Conversion Factor is not in the specified range."})
  void sfpRanges(String key, String value, String message) {
    List<Problem> p = sfp("L", with(sfpLine(), key, value));
    assertThat(p).isEqualTo(message == null ? List.of() : List.of(new Problem("logs[0]." + key, message)));
  }

  @Test
  void sfpRequiredByScaleMethod() {
    assertThat(sfp("L", with(sfpLine(), "prodLogTop", null))).containsExactly(
        new Problem("logs[0].prodLogTop", "Top is required."));
    assertThat(sfp("L", with(sfpLine(), "sfpNumber", null))).containsExactly(
        new Problem("logs[0].sfpNumber", "Log Number is required."));
    // stack method: no length/top/butt/log number rules
    assertThat(sfp("K", with(sfpLine(), "prodLogTop", null, "sfpNumber", null))).isEmpty();
    // P752Form's default scale method is L
    List<Problem> p = validator.validate(DocType.SFP_TALLY, false, map(),
        list(line(with(sfpLine(), "prodLogButt", null))));
    assertThat(p).containsExactly(new Problem("logs[0].prodLogButt", "Butt is required."));
  }

  @Test
  void sfpSpeciesProductGrade() {
    lookups.unknownSpg.add("FI/XM/J");
    assertThat(sfp("L", sfpLine())).containsExactly(
        new Problem("logs[0].scaleSpeciesCode", "Species and grade combination is invalid."));
  }

  @Test
  void ledgersHaveNoLines() {
    assertThat(validator.validate(DocType.ARRIVAL_LEDGER, false, map(), list(line(logLine())))).isEmpty();
  }
}
