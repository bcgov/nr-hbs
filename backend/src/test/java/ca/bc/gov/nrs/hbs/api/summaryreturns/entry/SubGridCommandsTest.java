package ca.bc.gov.nrs.hbs.api.summaryreturns.entry;

import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.SUMM_DATA_CORR;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.SUMM_DATA_CTL;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType.PIECE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType.SAMPLE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType.WEIGHT;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.EntryFixture.assertProblem;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.EntryFixture.keys;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryFixtures;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi.Call;
import ca.bc.gov.nrs.hbs.api.workflow.StaleUpdateException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** P057/P157 segregations, P058/P109/P158 notations and P110 daily loads. */
class SubGridCommandsTest {

  private final EntryFixture x = new EntryFixture();

  SubGridCommandsTest() {
    x.caller = SummaryFixtures.caller(SUMM_DATA_CORR, SUMM_DATA_CTL);
  }

  private Row version(SummaryReturnType type, long dcn, String doc, String ver) {
    x.f.header(dcn, type, doc, false);
    return x.f.version(type, dcn, 1, ver, true);
  }

  private static Map<String, Object> row(Object... pairs) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) m.put((String) pairs[i], pairs[i + 1]);
    return m;
  }

  private static Map<String, Object> grid(long dcn, String key, List<Map<String, Object>> rows) {
    Map<String, Object> b = keys(dcn, 1);
    b.put(key, rows);
    return b;
  }

  private Call only(String proc) {
    List<Call> calls = x.api.callsTo(proc);
    assertThat(calls).hasSize(1);
    return calls.get(0);
  }

  // ── Piece segregations (P057) ────────────────────────────────────────────

  @Test
  void pieceSegregationsAreEditedAddedAndDeleted() {
    version(PIECE, 100, "OIP", "INC");
    x.f.pieceSegregation(100, 1, 500, "F", 3, "1.500");
    x.f.pieceSegregation(100, 1, 501, "H", 2, "2.000");

    Map<String, Object> out = x.segregations().execute(grid(100, "segregations", List.of(
        row("segregationId", 500, "species", "f", "product", "", "grade", "", "pieces", "4", "volume", "1.25",
            "sb4", "Y", "nmv", "N", "avoidable", "Y"),
        row("species", "c", "product", "lo", "grade", "2", "pieces", "1", "volume", "0.5", "sb4", "Y"),
        row("species", "", "pieces", "", "volume", ""))));

    assertThat(x.api.names()).containsSubsequence("HBS_STORE_HBS_SCALE_RETURN", "HBS_STORE_PIECE_SCALE_SUMMARY",
        "HBS_REMOVE_PIECE_SCL_SEG", "HBS_STORE_PIECE_SCL_SEG", "HBS_CREATE_PIECE_SCL_SEG", "HBS_CREATE_AUDIT");
    assertThat(only("HBS_REMOVE_PIECE_SCL_SEG").arg("i_segregation_id")).isEqualTo(new BigDecimal(501));
    Call edited = only("HBS_STORE_PIECE_SCL_SEG");
    assertThat(edited.arg("i_scale_species_code")).isEqualTo("F");
    assertThat(edited.arg("i_scale_product_code")).isEqualTo("LO"); // blank product → LO
    assertThat(edited.arg("i_scale_grade_code")).isEqualTo("U");    // blank grade → U
    assertThat(edited.arg("i_number_of_pieces")).isEqualTo(new BigDecimal(4));
    assertThat(edited.arg("i_volume")).isEqualTo(new BigDecimal("1.25"));
    assertThat(edited.arg("i_sb4_flag")).isEqualTo("Y");
    assertThat(edited.arg("i_avoidable_flag")).isEqualTo("N"); // not shown for a PR return
    Call added = only("HBS_CREATE_PIECE_SCL_SEG");
    assertThat(added.arg("i_psseg_type")).isEqualTo("PRIMARY");
    assertThat(added.arg("i_scale_species_code")).isEqualTo("C");
    assertThat(added.arg("i_scale_grade_code")).isEqualTo("2");
    assertThat(added.arg("i_segregation_id")).isNotNull();
    assertThat(added.arg("i_input_species")).isNull();
    assertThat(x.f.edits.calls).isEqualTo(1);
    assertThat(out).containsEntry("saved", true);
  }

  @Test
  void aBeachcombReturnKeepsItsMarksAndNmvOnly() {
    version(PIECE, 100, "OIP", "INC").set("scl_rtn_category_code", "BC");
    x.lookups.marks.put("BC0001", new EntryLookups.Mark("BC0001", "B17", "N", "00012345", "00"));
    x.segregations().execute(grid(100, "segregations", List.of(
        row("species", "F", "product", "LO", "grade", "U", "pieces", "1", "volume", "1", "nmv", "Y", "sb4", "Y",
            "bcombMark", "bc0001", "inputBcombMark", "bc9"))));
    Call added = only("HBS_CREATE_PIECE_SCL_SEG");
    assertThat(added.arg("i_psseg_type")).isEqualTo("BCOMB");
    assertThat(added.arg("i_timber_mark")).isEqualTo("BC0001");
    assertThat(added.arg("i_input_bcomb_timber_mark")).isEqualTo("BC9");
    assertThat(added.arg("i_nmv_ind")).isEqualTo("Y");
    assertThat(added.arg("i_sb4_ind")).isEqualTo("N");
  }

  @Test
  void anUnknownBeachcombMarkIsTheTm01EditMessage() {
    version(PIECE, 100, "OIP", "INC").set("scl_rtn_category_code", "BC");
    assertProblem(() -> x.segregations().execute(grid(100, "segregations", List.of(
            row("species", "F", "product", "LO", "grade", "U", "pieces", "1", "volume", "1", "bcombMark", "nope")))),
        "segregations[0].bcombMark", "TM01 description of SCL");
  }

  static Stream<Arguments> pieceRowValidations() {
    return Stream.of(
        Arguments.of(row("species", "F", "product", "LO", "grade", "U", "pieces", "1", "volume", "1", "sb4", "Y", "nmv", "Y"),
            "nmv", EntryMessages.SB4_AND_NMV),
        Arguments.of(row("product", "LO", "grade", "U", "pieces", "1", "volume", "1"), "species", EntryMessages.SPECIES_REQUIRED),
        Arguments.of(row("species", "F", "grade", "U", "pieces", "1", "volume", "1"), "product", EntryMessages.PRODUCT_REQUIRED),
        Arguments.of(row("species", "F", "product", "LO", "pieces", "1", "volume", "1"), "grade", EntryMessages.GRADE_REQUIRED),
        Arguments.of(row("species", "Z", "product", "LO", "grade", "U", "pieces", "1", "volume", "1"), "species", EntryMessages.SPG_INVALID),
        Arguments.of(row("species", "F", "product", "LO", "grade", "U", "pieces", "x", "volume", "1"), "pieces", EntryMessages.PIECES_REQUIRED),
        Arguments.of(row("species", "F", "product", "LO", "grade", "U", "pieces", "-1", "volume", "1"), "pieces", EntryMessages.PIECES_NEGATIVE),
        Arguments.of(row("species", "F", "product", "LO", "grade", "U", "pieces", "1", "volume", "-1"), "volume", EntryMessages.VOLUME_NEGATIVE),
        Arguments.of(row("species", "F", "product", "LO", "grade", "U", "pieces", "1", "volume", "1000000"), "volume", EntryMessages.VOLUME_TOO_LARGE),
        Arguments.of(row("species", "F", "product", "LO", "grade", "U", "pieces", "1", "volume", "1.2345"), "volume", EntryMessages.VOLUME_DECIMALS),
        Arguments.of(row("species", "F", "product", "LO", "grade", "U", "pieces", "1", "volume", "abc"), "volume", EntryMessages.VOLUME_REQUIRED),
        Arguments.of(row("species", "F", "product", "XM", "grade", "U", "pieces", "1", "volume", "2"), "volume", EntryMessages.VOLUME_XMAS));
  }

  @ParameterizedTest(name = "new piece row → {2}")
  @MethodSource("pieceRowValidations")
  void newPieceRowValidation(Map<String, Object> in, String field, String message) {
    version(PIECE, 100, "OIP", "INC");
    assertProblem(() -> x.segregations().execute(grid(100, "segregations", List.of(in))),
        "segregations[0]." + field, message);
    assertThat(x.api.calls).isEmpty();
  }

  @Test
  void aStoredRowIsValidatedToo() {
    version(PIECE, 100, "OIP", "INC");
    x.f.pieceSegregation(100, 1, 500, "F", 3, "1.500");
    assertProblem(() -> x.segregations().execute(grid(100, "segregations", List.of(
        row("segregationId", "500", "species", "", "pieces", "-2")))), "segregations[0].species", EntryMessages.SPECIES_REQUIRED);
    assertProblem(() -> x.segregations().execute(grid(100, "segregations", List.of(
        row("segregationId", "500", "pieces", "-2")))), "segregations[0].pieces", EntryMessages.PIECES_NEGATIVE);
    assertProblem(() -> x.segregations().execute(grid(100, "segregations", List.of(
        row("segregationId", "999")))), "segregations[0].segregationId", EntryMessages.UNKNOWN_SEGREGATION);
  }

  @Test
  void anEmptyListDeletesEveryStoredSegregation() {
    version(PIECE, 100, "OIP", "INC");
    x.f.pieceSegregation(100, 1, 500, "F", 3, "1.500");
    x.f.repo.segErrors = new ArrayList<>(List.of(9L));
    x.segregations().execute(grid(100, "segregations", List.of()));
    assertThat(x.api.names()).containsSubsequence("HBS_STORE_HBS_SCALE_RETURN", "HBS_REMOVE_PIECE_SCL_SEG_ERROR",
        "HBS_REMOVE_PIECE_SCL_SEG");
  }

  @Test
  void segregationsAreNotAvailableForWeightAreaBasedOrReadyReturns() {
    version(WEIGHT, 200, "OIP", "INC");
    assertProblem(() -> x.segregations().execute(grid(200, "segregations", List.of())), null,
        EntryMessages.SEGREGATIONS_NOT_AVAILABLE);
    version(PIECE, 100, "OIP", "INC").set("pssmry_type", "AREAEST");
    assertProblem(() -> x.segregations().execute(grid(100, "segregations", List.of())), null,
        EntryMessages.SEGREGATIONS_NOT_AVAILABLE);
    version(PIECE, 101, "OIP", "RDY");
    assertProblem(() -> x.segregations().execute(grid(101, "segregations", List.of())), null,
        EntryMessages.NOT_EDITABLE);
    assertThat(EntryCommand.segregationLinkShown(x.f.repo.loadActive(101))).isFalse();
  }

  @Test
  void theSegregationGridChecksTheLock() {
    version(PIECE, 100, "OIP", "INC");
    Map<String, Object> b = grid(100, "segregations", List.of());
    b.put("updateTimestamp", "2026-10-01T10:00:00");
    assertThatThrownBy(() -> x.segregations().execute(b)).isInstanceOf(StaleUpdateException.class);
  }

  // ── Sample segregations (P157) ───────────────────────────────────────────

  private Row sampleSegregation(long dcn, long id, String species, String grade) {
    Row s = new Row().set("segregation_id", id).set("document_control_number", dcn).set("version", 1L)
        .set("scale_species_code", species).set("scale_product_code", "LO").set("scale_grade_code", grade)
        .set("number_of_pieces", 2).set("volume", new BigDecimal("1.000"))
        .set("input_species", null).set("input_product", null).set("input_grade", null)
        .set("entry_userid", "X").set("entry_timestamp", SummaryFixtures.LOADED)
        .set("update_userid", "X").set("update_timestamp", SummaryFixtures.LOADED);
    x.f.repo.segs.computeIfAbsent("S:" + dcn + ":1", k -> new ArrayList<>()).add(s);
    return s;
  }

  @Test
  void sampleSegregationsUseLogProductAndUngradedByDefault() {
    version(SAMPLE, 300, "OIP", "INC");
    sampleSegregation(300, 600, "F", "U");
    x.segregations().execute(grid(300, "segregations", List.of(
        row("segregationId", 600, "species", "h", "grade", "", "pieces", "5", "volume", "2"),
        row("species", "c", "grade", "2", "pieces", "1", "volume", "0.5", "product", "XM"))));
    Call edited = only("HBS_STORE_SAMPLE_SEG");
    assertThat(edited.arg("i_scale_species_code")).isEqualTo("H");
    assertThat(edited.arg("i_scale_grade_code")).isEqualTo("U");
    assertThat(edited.arg("i_number_of_pieces")).isEqualTo(new BigDecimal(5));
    Call added = only("HBS_CREATE_SAMPLE_SEG");
    assertThat(added.arg("i_scale_product_code")).isEqualTo("LO");
    assertThat(added.arg("i_volume")).isEqualTo(new BigDecimal("0.5"));
  }

  @Test
  void aNewRowNeedsAVolumeExceptForChristmasTrees() {
    version(SAMPLE, 300, "OIP", "INC");
    assertProblem(() -> x.segregations().execute(grid(300, "segregations", List.of(
        row("species", "F", "grade", "U", "pieces", "1", "volume", "")))), "segregations[0].volume",
        EntryMessages.VOLUME_REQUIRED);
    version(PIECE, 100, "OIP", "INC");
    x.segregations().execute(grid(100, "segregations", List.of(
        row("species", "F", "product", "XM", "grade", "U", "pieces", "3", "volume", ""))));
    assertThat(only("HBS_CREATE_PIECE_SCL_SEG").arg("i_volume")).isEqualTo(new BigDecimal("0.0"));
  }

  @Test
  void aSampleSpeciesAndGradeMustBeAValidLogCombination() {
    version(SAMPLE, 300, "OIP", "INC");
    assertProblem(() -> x.segregations().execute(grid(300, "segregations", List.of(
        row("species", "F", "grade", "9", "pieces", "1", "volume", "1")))), "segregations[0].species",
        EntryMessages.SPECIES_GRADE_INVALID);
    assertProblem(() -> x.segregations().execute(grid(300, "segregations", List.of(
        row("grade", "U", "pieces", "1", "volume", "1")))), "segregations[0].species", EntryMessages.SPECIES_REQUIRED);
    assertProblem(() -> x.segregations().execute(grid(300, "segregations", List.of(
        row("species", "F", "pieces", "", "volume", "1")))), "segregations[0].pieces", EntryMessages.PIECES_REQUIRED);
  }

  // ── Notations (P058 / P109 / P158) ───────────────────────────────────────

  @Test
  void notationsAreEditedDeletedAndAddedWithTheNextNumber() {
    version(PIECE, 100, "OIP", "INC");
    x.f.notation(PIECE, 100, 1, 700, 1, "first");
    x.f.notation(PIECE, 100, 1, 701, 2, "second");
    x.f.notation(PIECE, 100, 1, 702, 3, "third");

    x.notations().execute(grid(100, "notations", List.of(
        row("ntnId", 700, "text", " changed "),
        row("ntnId", 701, "text", "second"),
        row("text", "a new one"),
        row("text", "  "))));

    assertThat(only("HBS_REMOVE_PIECE_SCL_NOTATION").arg("i_ntn_id")).isEqualTo(new BigDecimal(702));
    assertThat(only("HBS_STORE_PIECE_SCL_NOTATION").arg("i_text")).isEqualTo("changed");
    Call added = only("HBS_CREATE_PIECE_SCL_NOTATION");
    assertThat(added.arg("i_notation_number")).isEqualTo(new BigDecimal(3)); // after the highest remaining (2)
    assertThat(added.arg("i_notation_type_code")).isEqualTo("NOT");
    assertThat(added.arg("i_text")).isEqualTo("a new one");
    assertThat(x.api.names()).contains("HBS_CREATE_AUDIT");
  }

  @Test
  void aKeptNotationNeedsText() {
    version(WEIGHT, 200, "OIP", "INC");
    x.f.notation(WEIGHT, 200, 1, 800, 1, "first");
    assertProblem(() -> x.notations().execute(grid(200, "notations", List.of(row("ntnId", 800, "text", "")))),
        "notations[0].text", EntryMessages.NOTATION_EMPTY);
    assertProblem(() -> x.notations().execute(grid(200, "notations", List.of(row("ntnId", 801, "text", "x")))),
        "notations[0].ntnId", EntryMessages.UNKNOWN_NOTATION);
  }

  @Test
  void weightAndSampleNotationEditsAreSavedToo() {
    version(SAMPLE, 300, "OIP", "HLD");
    x.f.notation(SAMPLE, 300, 1, 900, 4, "old");
    x.notations().execute(grid(300, "notations", List.of(row("notationNumber", 4, "text", "new"))));
    assertThat(only("HBS_STORE_SAMPLE_SCL_NOTATION").arg("i_text")).isEqualTo("new");
  }

  @Test
  void unchangedNotationsAreNotSaved() {
    version(PIECE, 100, "OIP", "INC");
    x.f.notation(PIECE, 100, 1, 700, 1, "first");
    Map<String, Object> out = x.notations().execute(grid(100, "notations", List.of(row("ntnId", 700, "text", "first"))));
    assertThat(x.api.calls).isEmpty();
    assertThat(out).containsEntry("saved", false);
  }

  @Test
  void generatedReturnsHaveNoEditableNotations() {
    x.f.header(100, PIECE, "OIP", true);
    x.f.version(PIECE, 100, 1, "INC", true);
    assertProblem(() -> x.notations().execute(grid(100, "notations", List.of())), null,
        EntryMessages.GENERATED_NOT_EDITABLE);
  }

  // ── Daily loads (P110) ───────────────────────────────────────────────────

  @Test
  void dailyLoadsAreAddedUpdatedAndDeleted() {
    version(WEIGHT, 200, "OIP", "INC");
    x.f.dailyLoad(200, 1, 1, 2, 50);
    x.f.dailyLoad(200, 1, 2, 3, 60);
    x.f.dailyLoad(200, 1, 3, 4, 70);

    x.dailyLoads().execute(grid(200, "dailyLoads", List.of(
        row("day", 1, "loads", "5", "weight", "55"),
        row("day", 2, "loads", "0", "weight", "60"),
        row("day", 3, "loads", "4", "weight", "70"),
        row("day", 9, "loads", "1", "weight", "10"))));

    Call removed = only("HBS_REMOVE_WEI_SCL_DLY_LD_SUM");
    assertThat(removed.arg("i_day")).isEqualTo(new BigDecimal(2));
    Call stored = only("HBS_STORE_WEI_SCL_DLY_LD_SUM");
    assertThat(stored.arg("i_day")).isEqualTo(BigDecimal.ONE);
    assertThat(stored.arg("i_number_of_loads")).isEqualTo(new BigDecimal(5));
    Call added = only("HBS_CREATE_WEI_SCL_DLY_LD_SUM");
    assertThat(added.arg("i_day")).isEqualTo(new BigDecimal(9));
    assertThat(added.arg("i_weight")).isEqualTo(BigDecimal.TEN);
    assertThat(x.api.names()).contains("HBS_CREATE_AUDIT");
  }

  @Test
  void aDayLeftOutIsDeleted() {
    version(WEIGHT, 200, "OIP", "INC");
    x.f.dailyLoad(200, 1, 5, 2, 50);
    x.dailyLoads().execute(grid(200, "dailyLoads", List.of()));
    assertThat(only("HBS_REMOVE_WEI_SCL_DLY_LD_SUM").arg("i_day")).isEqualTo(new BigDecimal(5));
  }

  @Test
  void loadsAndWeightMustBeWholeNumbersNotBelowZero() {
    version(WEIGHT, 200, "OIP", "INC");
    assertProblem(() -> x.dailyLoads().execute(grid(200, "dailyLoads", List.of(row("day", 1, "loads", "-1", "weight", "5")))),
        "dailyLoads[0].loads", EntryMessages.LOADS_REQUIRED);
    assertProblem(() -> x.dailyLoads().execute(grid(200, "dailyLoads", List.of(row("day", 1, "loads", "1", "weight", "1.5")))),
        "dailyLoads[0].weight", EntryMessages.WEIGHT_REQUIRED);
    assertProblem(() -> x.dailyLoads().execute(grid(200, "dailyLoads", List.of(row("day", 32, "loads", "1", "weight", "1")))),
        "dailyLoads[0].day", EntryMessages.DAY_INVALID);
    assertProblem(() -> x.dailyLoads().execute(grid(200, "dailyLoads", List.of(
        row("day", 4, "loads", "1", "weight", "1"), row("day", 4, "loads", "2", "weight", "2")))),
        "dailyLoads[1].day", EntryMessages.DAY_DUPLICATE);
    assertThat(x.api.calls).isEmpty();
  }

  @Test
  void dailyLoadsOnlyExistOnWeightReturnsAndBlankCountsAsZero() {
    version(PIECE, 100, "OIP", "INC");
    assertProblem(() -> x.dailyLoads().execute(grid(100, "dailyLoads", List.of())), null,
        EntryMessages.DAILY_LOADS_NOT_AVAILABLE);
    version(WEIGHT, 200, "OIP", "INC");
    Map<String, Object> out = x.dailyLoads().execute(grid(200, "dailyLoads", List.of(row("day", 1, "loads", "", "weight", "9"))));
    assertThat(x.api.calls).isEmpty();
    assertThat(out).containsEntry("saved", false);
  }
}
