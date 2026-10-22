package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.TallyFixtures.codes;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.TallyFixtures.document;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.TallyFixtures.sfpLine;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.TallyFixtures.sfpTallyRow;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.TallyFixtures.ts;
import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SfpTallyValidatorTest {

  private final TallyFixtures f = new TallyFixtures();

  @BeforeEach
  void sfpProduct() {
    f.tally.addSpg("FI", "XM", "J", TallyFixtures.LONG_AGO, TallyFixtures.FAR_FUTURE);
  }

  private DetailDocument doc(Row v, Row... lines) {
    return document(DocType.SFP_TALLY, v, lines);
  }

  private static List<String> expect(String code) {
    return code == null ? List.of() : List.of(code);
  }

  @Test
  void cleanTallyHasNoErrors() {
    DetailDocument d = doc(sfpTallyRow(), sfpLine(1));
    f.sfp(d).copyInputFieldsAndValidate();
    assertThat(d.errors()).isEmpty();
  }

  // ── version ───────────────────────────────────────────────────────

  @ParameterizedTest
  @CsvSource({"L,", "C,", "P,", "K,", "X,SM22", "l,SM22", ",SM22"})
  void scaleMethod(String method, String expected) {
    DetailDocument d = doc(sfpTallyRow().set("hbs_sfp_scale_type_code", method), sfpLine(1));
    f.sfp(d).validateVersion();
    assertThat(codes(d.errors())).isEqualTo(expect(expected));
  }

  @ParameterizedTest
  @CsvSource({"1,", "0,PPC01", "-3,PPC01", ","})
  void proratedPieceCount(String count, String expected) {
    DetailDocument d = doc(sfpTallyRow().set("sample_piece_count", count), sfpLine(1));
    f.sfp(d).validateVersion();
    assertThat(codes(d.errors())).isEqualTo(expect(expected));
  }

  @Test
  void beachcombMarkOnSfpIsNotABeachcombError() {
    // SFP uses the shared TM edit only (no BC01/BC02)
    DetailDocument d = doc(sfpTallyRow().set("timber_mark", "NOPE"), sfpLine(1));
    f.sfp(d).validateVersion();
    assertThat(codes(d.errors())).containsExactly("TM01");
  }

  @Test
  void noRateIsSd08() {
    f.ref.rates.remove("ABC123");
    DetailDocument d = doc(sfpTallyRow(), sfpLine(1));
    f.sfp(d).validateVersion();
    assertThat(codes(d.errors())).containsExactly("SD08");
  }

  @Test
  void noCopyToOrBillToEditsOnSfp() {
    f.ref.clients.clear();
    DetailDocument d = doc(sfpTallyRow(), sfpLine(1));
    f.sfp(d).validateVersion();
    assertThat(d.errors()).isEmpty();
  }

  @Test
  void fieldScaleWithoutDeckIsFsd01() {
    DetailDocument d = doc(sfpTallyRow().set("field_scale_ind", "Y"), sfpLine(1));
    f.sfp(d).validateVersion();
    assertThat(codes(d.errors())).containsExactly("FSD01");
  }

  @Test
  void versionEditsInLegacyOrder() {
    Row v = sfpTallyRow().set("hbs_sfp_scale_type_code", "X").set("scale_date", ts("2026-07-01 00:00:00"))
        .set("parcel_count", 1L).set("signing_license_number", "5678").set("log_count", 3L)
        .set("sample_piece_count", 0L);
    DetailDocument d = doc(v, sfpLine(1));
    f.sfp(d).validateVersion();
    assertThat(codes(d.errors())).containsExactly("SM22", "SD03", "PI00", "SGL02", "LC01", "PPC01");
  }

  // ── check scale ───────────────────────────────────────────────────

  private Row checkScale() {
    return sfpTallyRow().set("scale_event_type_code", "CS").set("orig_chk_scl_license_number", "5678")
        .set("orig_chk_scl_return_number", 77L).set("check_replaces_original_ind", "Y");
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({
      "valid,                PS, N, OIP, RDY, ",
      "original check scale, CS, N, OIP, RDY, CHKS03",
      "already replaced,     PS, Y, OIP, RDY, CHKS06",
      "not ready,            PS, N, OIP, HLD, CHKS07",
      "SB4 is allowed,       4R, N, OIP, RDY, ",
  })
  void validateAgainstOriginal(String name, String event, String replaced, String docState, String versionState,
                               String expected) {
    f.original("ORIG", DocType.SFP_TALLY, docState, versionState).set("scale_event_type_code", event)
        .set("check_replaces_original_ind", replaced).set("scale_date", ts("2026-01-01 00:00:00"));
    f.tally.originals.add("ORIG");
    Row v = checkScale();
    DetailDocument d = doc(v, sfpLine(1));
    f.sfp(d).validateVersion();
    assertThat(codes(d.errors())).isEqualTo(expect(expected));
    assertThat(f.tally.searchedType).isEqualTo(DocType.SFP_TALLY);
    assertThat(v.str("original_ddn")).isEqualTo("ORIG");
  }

  @Test
  void checkScaleWithoutOriginalIsChks01() {
    DetailDocument d = doc(checkScale(), sfpLine(1));
    f.sfp(d).validateVersion();
    assertThat(codes(d.errors())).containsExactly("CHKS01");
  }

  // ── lines ─────────────────────────────────────────────────────────

  @ParameterizedTest(name = "{0}={1}")
  @CsvSource({
      "prod_log_length, 0,    LT01",
      "prod_log_length, 100,  LT01",
      "prod_log_top,    0,    TP01",
      "prod_log_top,    200,  TPMax",
      "prod_log_butt,   0,    BT01",
      "prod_log_butt,   200,  BTMax",
      "stack_length,    -1,   LN02",
      "stack_length,    0,    ",
      "stack_width,     -2,   WD01",
      "stack_height,    -1,   HT01",
      "conversion_factor, -0.5, CF01",
      "conversion_factor, 0.5,  ",
  })
  void lineDimensions(String column, String value, String expected) {
    DetailDocument d = doc(sfpTallyRow(), sfpLine(1).set(column, value));
    f.sfp(d).validateLogs();
    assertThat(codes(d.errors())).isEqualTo(expect(expected));
  }

  @Test
  void missingDimensionsAreSkipped() {
    DetailDocument d = doc(sfpTallyRow(), sfpLine(1).set("prod_log_length", null).set("prod_log_top", null)
        .set("prod_log_butt", null));
    f.sfp(d).validateLogs();
    assertThat(d.errors()).isEmpty();
  }

  @Test
  void beachcombTallyLinesNeverFlaggedWithoutLineMark() {
    // SFP_LOG_DETAIL has no TIMBER_MARK / NMV_IND: legacy mark null, NMV TRUE
    DetailDocument d = doc(sfpTallyRow().set("timber_mark", "BCOMB1"), sfpLine(1));
    f.sfp(d).validateLogs();
    assertThat(d.errors()).isEmpty();
  }

  @Test
  void beachcombTallyLineMarkRulesWhenColumnsPresent() {
    DetailDocument d = doc(sfpTallyRow().set("timber_mark", "BCOMB3"),
        sfpLine(1).set("timber_mark", "ABC123"), sfpLine(2).set("nmv_ind", "N"));
    f.sfp(d).validateLogs();
    assertThat(codes(d.errors())).containsExactly("NM04", "NM02");
  }

  // ── input fields ──────────────────────────────────────────────────

  @Test
  void cutBlockCopiedFromInputCutBlockId() {
    Row v = sfpTallyRow().set("input_cut_block_id", "CB9");
    f.sfp(doc(v)).validateAndCopyVersionInputFields();
    assertThat(v.str("cut_block_id")).isEqualTo("CB9");
  }

  @Test
  void versionInputsCopied() {
    Row v = sfpTallyRow().set("scale_site_id_nmbr", null).set("input_scale_site", "S001")
        .set("timber_mark", null).set("input_source", "ABC123").set("primary_license_number", null)
        .set("input_primary_scaler_lic", "1234").set("input_orig_chk_scl_scaler_lic", "0000");
    DetailDocument d = doc(v);
    f.sfp(d).validateAndCopyVersionInputFields();
    assertThat(v.str("scale_site_id_nmbr")).isEqualTo("S001");
    assertThat(v.str("timber_mark")).isEqualTo("ABC123");
    assertThat(v.str("primary_license_number")).isEqualTo("1234");
    assertThat(codes(d.errors())).containsExactly("OSL01");
  }

  @Test
  void spgCopiesInputProductEvenWhenScaleCodesUsed() {
    Row line = sfpLine(1).set("input_product", "OTHER");
    DetailDocument d = doc(sfpTallyRow(), line);
    f.sfp(d).validateAndCopyLogsInputFields();
    assertThat(d.errors()).isEmpty();
    assertThat(line.str("scale_product_code")).isEqualTo("OTHER");
  }

  @Test
  void spgNotOnFileIsSp01Once() {
    DetailDocument d = doc(sfpTallyRow(), sfpLine(1).set("scale_grade_code", "Z"),
        sfpLine(2).set("scale_grade_code", "Z"));
    f.sfp(d).validateAndCopyLogsInputFields();
    assertThat(codes(d.errors())).containsExactly("SP01");
  }

  @Test
  void noGradeScheduleEditsOnSfpLines() {
    f.tally.addSpg("FI", "XM", "U", TallyFixtures.LONG_AGO, TallyFixtures.FAR_FUTURE);
    DetailDocument d = doc(sfpTallyRow(), sfpLine(1).set("scale_grade_code", "U"));
    f.sfp(d).validateAndCopyLogsInputFields();
    assertThat(d.errors()).isEmpty();
  }

  // ── billable ──────────────────────────────────────────────────────

  @ParameterizedTest
  @CsvSource({"PS,true", "CS,false", "FD,false", "RT,false", ",false"})
  void isBillableScaleReturn(String event, boolean billable) {
    assertThat(f.sfp(doc(sfpTallyRow().set("scale_event_type_code", event))).isBillableScaleReturn())
        .isEqualTo(billable);
  }
}
