package ca.bc.gov.nrs.hbs.api.summaryreturns.edits;

import static ca.bc.gov.nrs.hbs.api.summaryreturns.edits.EditsFixture.FAR_FUTURE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.edits.EditsFixture.FAR_PAST;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.edits.EditsFixture.MARK;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.edits.EditsFixture.codes;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.edits.EditsFixture.pieceSeg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.summaryreturns.EditError;
import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.Client;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.ClientLocation;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.DateWindow;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.ScaleSite;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.VolumeEstimate;
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

class PieceEditsTest {

  private final EditsFixture f = new EditsFixture();

  private List<EditError> release() {
    return f.edits.applyEditsAndValidate(f.piece());
  }

  @Test
  void cleanReturnHasNoErrorsAndChecksTheRates() {
    assertThat(release()).isEmpty();
    assertThat(f.api.names()).containsExactly("HBS_RATES.GET_OVERRIDE_AND_APPRAISAL", "HBS_RATES.RETRIEVE_NORMAL_RATE");
    var rate = f.api.callsTo(Rates.RETRIEVE_NORMAL_RATE).get(0);
    assertThat(rate.arg("i_timber_mark")).isEqualTo(MARK);
    assertThat(rate.arg("i_species")).isEqualTo("FI");
    assertThat(rate.arg("i_grade")).isEqualTo("U");
    assertThat(f.lookups.called).contains("hasSplitRate 2026-09-15 2026-09-15");
  }

  // ── Table of single-rule edits ───────────────────────────────────────────

  static Stream<Arguments> rules() {
    return Stream.of(
        rule("SD02 scale date before 1 Jan seven years ago", "SD02", "DTE",
            f -> f.pieceVersion.set("scale_date", Timestamp.valueOf("2018-12-31 00:00:00"))),
        rule("SD03 scale date after today", "SD03", "REL",
            f -> f.pieceVersion.set("scale_date", Timestamp.valueOf("2026-10-22 00:00:00"))),
        rule("FSX03 field scale deck id not upper case", "FSX03", "SCL",
            f -> f.pieceVersion.set("scl_rtn_category_code", "FI").set("pssmry_type", "FLDSCLEST")
                .set("field_scale_deck_id", "deck1")),
        rule("RC02 beachcomb mark on a non-beachcomb return", "RC02", "REL",
            f -> f.lookups.marks.put(MARK, EditsFixture.mark("B17", "N", "S"))),
        rule("TM04 cash sale", "TM04", "REL",
            f -> f.lookups.marks.put(MARK, EditsFixture.mark("A01", "N", "C"))),
        rule("TM09 cruise-based mark on a primary return", "TM09", "REL",
            f -> f.lookups.marks.put(MARK, EditsFixture.mark("A01", "Y", "S"))),
        rule("TM06 cruise return on a scale-based mark", "TM06", "REL",
            f -> {
              f.pieceVersion.set("scl_rtn_category_code", "CR");
              f.lookups.sites.put("S123", new ScaleSite("S123", "00099999", "01", "DKA Cruise Based Site", 10L));
            }),
        rule("CSB1C coastal worksheet conifer SRE code", "CSB1C", "SCL",
            f -> f.lookups.worksheets.set(0, new Worksheet(MARK, FAR_PAST, "C", "B", "S"))),
        rule("CSB1D coastal worksheet deciduous SRE code", "CSB1D", "SCL",
            f -> f.lookups.worksheets.set(0, new Worksheet(MARK, FAR_PAST, "C", "S", "C"))),
        rule("ISB1C interior worksheet, scale-based return", "ISB1C", "SCL",
            f -> f.lookups.worksheets.set(0, new Worksheet(MARK, FAR_PAST, "I", null, "N"))),
        rule("BT03 bill-to location not found", "BT03", "FMT",
            f -> f.pieceVersion.set("client_locn_code_paid_by", "77")),
        rule("BT02 bill-to client deactivated", "BT02", "REL",
            f -> f.lookups.locations.put("00012345/00", EditsFixture.location(
                new Client("00012345", "ACME", null, null, "DAC", "C"), "00"))),
        rule("BT04 bill-to location expired", "BT04", "REL",
            f -> f.lookups.locations.put("00012345/00", new ClientLocation(
                f.lookups.clients.get("00012345"), "00012345", "00", "Victoria", "BC", "V8W1A1", "Y"))),
        rule("BT05 individual name longer than 60", "BT05", "FMT",
            f -> f.lookups.locations.put("00012345/00", EditsFixture.location(
                new Client("00012345", "A".repeat(50), "BARTHOLOMEW", "Q", "ACT", "I"), "00"))),
        rule("BT06 bill-to address incomplete", "BT06", "FMT",
            f -> f.lookups.locations.put("00012345/00", new ClientLocation(
                f.lookups.clients.get("00012345"), "00012345", "00", " ", "BC", "V8W1A1", "N"))),
        rule("CT03 copy-to (scale site owner) location not found", "CT03", "FMT",
            f -> f.lookups.locations.remove("00099999/01")),
        rule("CT02 copy-to client deactivated", "CT02", "REL",
            f -> f.lookups.locations.put("00099999/01", EditsFixture.location(
                new Client("00099999", "OWNER", null, null, "DAC", "C"), "01"))),
        rule("CT06 copy-to address incomplete", "CT06", "FMT",
            f -> f.lookups.locations.put("00099999/01", new ClientLocation(
                f.lookups.clients.get("00099999"), "00099999", "01", "Victoria", null, "V8W1A1", "N"))),
        rule("SS05 waste scale site on a non-waste return", "SS05", "REL",
            f -> {
              f.pieceVersion.set("scale_site_id_nmbr", "ZZZ");
              f.lookups.sites.put("ZZZ", new ScaleSite("ZZZ", "00099999", "01", "Waste", 10L));
            }),
        rule("SS04 waste return not at the waste scale site", "SS04", "REL",
            f -> f.pieceVersion.set("pssmry_type", "WSTRESEST").set("scl_rtn_category_code", "WA")),
        rule("CBSS00 dedicated cruise-based site on a primary return", "CBSS00", "SCL",
            f -> f.lookups.sites.put("S123", new ScaleSite("S123", "00099999", "01", "DKA Cruise Based Site", 10L))),
        rule("CBSS01 cruise return not at a cruise-based site", "CBSS01", "SCL",
            f -> {
              f.pieceVersion.set("scl_rtn_category_code", "CR");
              f.lookups.marks.put(MARK, EditsFixture.mark("A01", "Y", "S"));
            }),
        rule("CBSS02 cruise-based site of another district", "CBSS02", "SCL",
            f -> {
              f.pieceVersion.set("scl_rtn_category_code", "CR");
              f.lookups.marks.put(MARK, EditsFixture.mark("A01", "Y", "S"));
              f.lookups.sites.put("S123", new ScaleSite("S123", "00099999", "01", "DKA Cruise Based Site", 99L));
            }),
        rule("DK00 later duplicate volume estimate", "DK00", "FMT",
            f -> {
              f.pieceVersion.set("pssmry_type", "OTVOLEST").set("scl_rtn_category_code", "OT");
              f.lookups.estimates.add(new VolumeEstimate(1001, 1, LocalDateTime.of(2026, 9, 16, 8, 0)));
              f.lookups.estimates.add(new VolumeEstimate(900, 1, LocalDateTime.of(2026, 9, 1, 8, 0)));
            }),
        rule("SD12 rate change splits the scale period", "SD12", "REL", f -> f.lookups.splitRate = true),
        rule("SD17 a segregation has no rate", "SD17", "REL", f -> f.rate.put("o_rate_found", "N")),
        rule("SD08 no confirmed worksheet", "SD08", "REL",
            f -> f.rate.putAll(java.util.Map.of("o_rate_found", "N", "o_failure_reason", "NO WKSHT"))),
        rule("SD10 no stumpage", "SD10", "REL",
            f -> f.rate.putAll(java.util.Map.of("o_rate_found", "N", "o_failure_reason", "NO STUMP"))),
        rule("SD11 overlapping override rules", "SD11", "REL",
            f -> f.ruleAndAppraisal.put("o_rate_rule_overlap", "Y")),
        rule("NM03 no mark visible on a non-log segregation", "NM03", "REL",
            f -> {
              f.pieceSegs.add(pieceSeg(11, "FI", "U", 10).set("nmv_ind", "Y").set("scale_product_code", "CH"));
              f.lookups.spg.put("FI/CH/U", new DateWindow(FAR_PAST, FAR_FUTURE));
            }),
        rule("ISB789 grade 7 on a scale-based return", "ISB789", "SCL",
            f -> f.pieceSegs.add(pieceSeg(11, "FI", "7", 10))),
        rule("RC01 segregation mark on a non-beachcomb return", "RC01", "REL",
            f -> f.pieceSegs.add(pieceSeg(11, "FI", "U", 10).set("timber_mark", MARK))),
        rule("NP01 no pieces", "NP01", "VAL",
            f -> {
              f.pieceSegs.add(pieceSeg(11, "FI", "U", 0));
              f.pieceVersion.set("log_count", 0L);
            }),
        rule("QV00 no volume", "QV00", "FMT", f -> f.pieceSegs.add(pieceSeg(11, "FI", "U", 10).set("volume", null))),
        rule("LC01 pieces do not add up to the log count", "LC01", "REL", f -> f.pieceVersion.set("log_count", 9L)),
        rule("SG04 no segregations", "SG04", "FMT",
            f -> {
              f.pieceVersion.set("log_count", 0L).set("pssmry_type", "OTVOLEST").set("return_number", null);
              f.noPieceSegs = true;
            }),
        rule("SD15 avoidable waste without a waste rate", "SD15", "REL",
            f -> {
              f.pieceVersion.set("pssmry_type", "WSTRESEST").set("scl_rtn_category_code", "WA")
                  .set("scale_site_id_nmbr", "ZZZ").set("cut_block_id", "NONE");
              f.lookups.sites.put("ZZZ", new ScaleSite("ZZZ", "00099999", "01", "Waste", 10L));
              f.pieceSegs.add(pieceSeg(11, "FI", "U", 10).set("avoidable_ind", "Y"));
              f.lookups.wasteRate = false;
            }),
        rule("CTB02 mark has no cut block", "CTB02", "SCL",
            f -> {
              f.pieceVersion.set("pssmry_type", "WSTRESEST").set("scl_rtn_category_code", "WA")
                  .set("scale_site_id_nmbr", "ZZZ").set("cut_block_id", "CB1");
              f.lookups.sites.put("ZZZ", new ScaleSite("ZZZ", "00099999", "01", "Waste", 10L));
            }),
        rule("CTB01 cut block not one of the mark's", "CTB01", "SCL",
            f -> {
              f.pieceVersion.set("pssmry_type", "WSTRESEST").set("scl_rtn_category_code", "WA")
                  .set("scale_site_id_nmbr", "ZZZ").set("cut_block_id", "CB1");
              f.lookups.sites.put("ZZZ", new ScaleSite("ZZZ", "00099999", "01", "Waste", 10L));
              f.lookups.cutBlocks.add("CB9/" + MARK);
            }));
  }

  private static Arguments rule(String name, String code, String category, Consumer<EditsFixture> setup) {
    return Arguments.of(name, code, category, setup);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rules")
  void eachRuleRaisesItsCode(String name, String code, String category, Consumer<EditsFixture> setup) {
    setup.accept(f);
    List<EditError> errors = release();
    assertThat(codes(errors)).containsExactly(code);
    assertThat(errors.get(0).category()).isEqualTo(category);
  }

  // ── Input fields ─────────────────────────────────────────────────────────

  @Test
  void movesTheInputTimberMarkLicenceBeachcombMarkAndOtherDescription() {
    f.pieceVersion.set("timber_mark", null).set("license_number", null).set("scl_rtn_category_code", "BC");
    f.lookups.marks.put(MARK, EditsFixture.mark("B17", "N", "S"));
    f.lookups.marks.put("BCM001", EditsFixture.mark("B08", "N", "S"));
    f.lookups.otherDescriptions.add("CED");
    f.pieceSegs.add(pieceSeg(11, "FI", "U", 10).set("input_bcomb_timber_mark", "BCM001").set("input_other_desc", "CED"));
    SummaryReturn ret = f.piece();
    assertThat(f.edits.applyEditsAndValidate(ret)).isEmpty();
    assertThat(ret.version().str("timber_mark")).isEqualTo(MARK);
    assertThat(ret.version().str("license_number")).isEqualTo("K123");
    assertThat(ret.segregations().get(0).str("timber_mark")).isEqualTo("BCM001");
    assertThat(ret.segregations().get(0).str("other_description_type_code")).isEqualTo("CED");
  }

  @Test
  void tm01AndSl01WhenTheInputsAreNotOnFile() {
    f.pieceVersion.set("timber_mark", null).set("input_timber_mark", "NOPE").set("license_number", null)
        .set("input_scaler_license_num", "X999");
    PieceEdits edits = new PieceEdits(f.piece(), f.lookups, f.rates, f.clock);
    edits.validateAndMoveInputFields();
    assertThat(codes(edits.errors)).containsExactly("TM01", "SL01");
    assertThat(edits.errors).extracting(EditError::category).containsExactly("LKP", "LKP");
  }

  @Test
  void tm01ThenValidateSegregationsFailsAsLegacyDid() {
    f.pieceVersion.set("timber_mark", null).set("input_timber_mark", "NOPE");
    assertThatThrownBy(this::release).isInstanceOf(ReleaseEditException.class)
        .hasMessageContaining("Previously validated mark not found!");
  }

  @Test
  void wasteReturnLicenceFallsBackToWasteResidueType() {
    f.pieceVersion.set("license_number", null).set("input_scaler_license_num", "W77")
        .set("scl_rtn_category_code", "WA").set("scl_rtn_version_state_code", "ERR");
    f.lookups.licences.add("W77/R");
    PieceEdits edits = new PieceEdits(f.piece(), f.lookups, f.rates, f.clock);
    edits.validateAndMoveInputFields();
    assertThat(edits.errors).isEmpty();
    assertThat(f.pieceVersion.str("quant_lic_type_cd")).isEqualTo("R");
    assertThat(f.pieceVersion.str("license_number")).isEqualTo("W77");
  }

  @Test
  void sl02WhenAWasteReturnLicenceIsNeitherType() {
    f.pieceVersion.set("license_number", null).set("input_scaler_license_num", "W77").set("scl_rtn_category_code", "WA");
    PieceEdits edits = new PieceEdits(f.piece(), f.lookups, f.rates, f.clock);
    edits.validateAndMoveInputFields();
    assertThat(codes(edits.errors)).containsExactly("SL02");
    assertThat(edits.errors.get(0).category()).isEqualTo("FMT");
  }

  @Test
  void sp01ForAnUnknownOtherDescription() {
    f.pieceSegs.add(pieceSeg(11, "FI", "U", 10).set("input_other_desc", "ZZZ"));
    List<EditError> errors = release();
    assertThat(errors).containsExactly(EditError.segregation(11, "LKP", "SP01"));
  }

  // ── Species / product / grade ────────────────────────────────────────────

  @Test
  void movesInputSpeciesProductGradeIntoTheBusinessCodes() {
    f.pieceSegs.add(pieceSeg(11, null, null, 10).set("scale_product_code", null)
        .set("input_species", "FI").set("input_product", " ").set("input_grade", "U"));
    assertThat(release()).isEmpty();
    Row seg = f.pieceSegs.get(0);
    assertThat(seg.str("scale_species_code")).isEqualTo("FI");
    assertThat(seg.str("scale_product_code")).isEqualTo(" ");
    assertThat(seg.str("scale_grade_code")).isEqualTo("U");
  }

  @Test
  void sp01IsRaisedOncePerReturn() {
    f.pieceSegs.add(pieceSeg(11, "XX", "U", 5));
    f.pieceSegs.add(pieceSeg(12, "YY", "U", 5));
    assertThat(release()).containsExactly(EditError.segregation(11, "SCL", "SP01"));
  }

  @Test
  void sp02WhenTheCombinationIsNotEffective() {
    f.lookups.spg.put("FI/ /U", new DateWindow(FAR_PAST, LocalDateTime.of(2026, 9, 14, 0, 0)));
    assertThat(release()).containsExactly(EditError.segregation(11, "SCL", "SP02"));
  }

  @Test
  void spgExpiringOnTheScaleDayIsStillEffective() {
    f.lookups.spg.put("FI/ /U", new DateWindow(FAR_PAST, LocalDateTime.of(2026, 9, 15, 0, 0)));
    assertThat(release()).isEmpty();
  }

  @Test
  void cg01AndIg01WhenTheGradeIsNotOnTheMarksSchedule() {
    f.lookups.grades.clear();
    assertThat(release()).containsExactly(EditError.segregation(11, "SCL", "CG01"));
    EditsFixture g = new EditsFixture();
    g.lookups.grades.clear();
    g.lookups.gradeSchedule = "I";
    g.lookups.worksheets.clear();
    assertThat(g.edits.applyEditsAndValidate(g.piece())).containsExactly(EditError.segregation(11, "SCL", "IG01"));
  }

  @Test
  void cg02WhenTheGradeIsNotEffective() {
    f.lookups.grades.put("U/C", new DateWindow(LocalDateTime.of(2026, 9, 16, 0, 0), FAR_FUTURE));
    assertThat(release()).containsExactly(EditError.segregation(11, "SCL", "CG02"));
  }

  // ── Cruise based (BT5884, BT 16523) ──────────────────────────────────────

  private void cruiseInteriorMark() {
    f.pieceVersion.set("scl_rtn_category_code", "CR").set("pssmry_type", "CRSBSDEST");
    f.lookups.marks.put(MARK, EditsFixture.mark("A01", "Y", "S"));
    f.lookups.sites.put("S123", new ScaleSite("S123", "00099999", "01", "DKA Cruise Based Site", 10L));
    f.lookups.gradeSchedule = "I";
    f.lookups.grades.put("7/I", new DateWindow(FAR_PAST, FAR_FUTURE));
    f.lookups.grades.put("U/I", new DateWindow(FAR_PAST, FAR_FUTURE));
  }

  @Test
  void cruiseReturnAfterThePolicyDateChecksCruiseBasedSreCodesAndGrades() {
    cruiseInteriorMark();
    f.lookups.worksheets.set(0, new Worksheet(MARK, FAR_PAST, "I", "S", "C"));
    List<EditError> errors = release();
    assertThat(codes(errors)).containsExactly("ICB1C", "ICB2MM", "ICB789");
    assertThat(errors.get(2)).isEqualTo(EditError.segregation(11, "SCL", "ICB789"));
  }

  @Test
  void cruiseReturnBeforeThePolicyDateChecksScaleBasedSreCodes() {
    cruiseInteriorMark();
    f.pieceSegs.add(pieceSeg(11, "FI", "7", 10));
    f.lookups.policyDate = LocalDateTime.of(2026, 9, 16, 0, 0);
    f.lookups.worksheets.set(0, new Worksheet(MARK, FAR_PAST, "I", "B", "D"));
    assertThat(codes(release())).containsExactly("ICB4C", "ICB4D");
  }

  @Test
  void areaBasedMarkOnAPieceReturn() {
    f.lookups.marks.put(MARK, EditsFixture.mark("A11", "Y", "S"));
    assertThat(codes(release())).containsExactly("TM10", "TM12");
  }

  // ── Beachcomb ────────────────────────────────────────────────────────────

  @Test
  void beachcombSegregationMarkEdits() {
    f.pieceVersion.set("scl_rtn_category_code", "BC");
    f.lookups.marks.put(MARK, EditsFixture.mark("B17", "N", "S"));
    f.pieceSegs.add(pieceSeg(11, "FI", "U", 5));
    f.pieceSegs.add(pieceSeg(12, "HE", "U", 5).set("nmv_ind", "Y").set("input_bcomb_timber_mark", "ZZZ999"));
    f.lookups.spg.put("HE/ /U", new DateWindow(FAR_PAST, FAR_FUTURE));
    List<EditError> errors = release();
    assertThat(errors).containsExactly(
        EditError.segregation(11, "REL", "NM02"), EditError.segregation(11, "FMT", "TM02"),
        EditError.segregation(12, "REL", "NM04"), EditError.segregation(12, "FMT", "TM02"));
  }

  @Test
  void unregulatedBeachcombMarkWithoutCopyToSkipsTheCopyToEdits() {
    f.pieceVersion.set("timber_mark", "BCOMB2").set("client_number_paid_by", null).set("scl_rtn_category_code", "BC");
    f.lookups.marks.put("BCOMB2", new Lookups.TimberMark("BCOMB2", "B17", "N", "S", 10, "A", "00012345", "00"));
    f.lookups.locations.remove("00099999/01");
    f.pieceSegs.add(pieceSeg(11, "FI", "U", 10).set("input_bcomb_timber_mark", MARK).set("timber_mark", MARK));
    // bill-to is the scale site owner (missing → BT03); copy-to is skipped
    assertThat(codes(release())).containsExactly("BT03");
  }

  @Test
  void sg03ForDuplicateSegregationKeys() {
    f.pieceSegs.add(pieceSeg(11, "FI", "U", 5));
    f.pieceSegs.add(pieceSeg(12, "FI", "U", 5));
    assertThat(release()).containsExactly(EditError.segregation(12, "FMT", "SG03"));
  }

  @Test
  void volumeEstimatesNeedNoPieceCount() {
    f.pieceVersion.set("pssmry_type", "OTVOLEST").set("scl_rtn_category_code", "OT").set("log_count", 0L);
    f.pieceSegs.add(pieceSeg(11, "FI", "U", 0));
    assertThat(release()).isEmpty();
  }

  @Test
  void privateMarkSkipsTheRateEdits() {
    f.lookups.marks.put(MARK, EditsFixture.mark("B08", "N", "S"));
    f.lookups.splitRate = true;
    assertThat(release()).isEmpty();
    assertThat(f.api.calls).isEmpty();
  }

  @Test
  void wasteReturnRatesUseTheWasteProcForAvoidableSegregations() {
    f.pieceVersion.set("pssmry_type", "WSTRESEST").set("scl_rtn_category_code", "WA")
        .set("scale_site_id_nmbr", "ZZZ").set("cut_block_id", "CB1");
    f.lookups.sites.put("ZZZ", new ScaleSite("ZZZ", "00099999", "01", "Waste", 10L));
    f.lookups.cutBlocks.add("CB1/" + MARK);
    f.pieceSegs.add(pieceSeg(11, "FI", "U", 10).set("avoidable_ind", "Y"));
    assertThat(release()).isEmpty();
    // SD08 / SD17 do not apply to the waste category; SD15 checks the waste rate table
    assertThat(f.api.calls).isEmpty();
    assertThat(f.lookups.called).contains("hasWasteRate CB1 2026-09-15T00:00 2026-09-15T23:59:59.999");
  }

  @Test
  void beachcombRatesUseTheBeachcombProc() {
    f.pieceVersion.set("scl_rtn_category_code", "BC");
    f.lookups.marks.put(MARK, EditsFixture.mark("B17", "N", "S"));
    f.lookups.marks.put("SEG001", new Lookups.TimberMark("SEG001", "A01", "N", "S", 10, "A", "00012345", "00"));
    f.pieceSegs.add(pieceSeg(11, "FI", "U", 10).set("timber_mark", "SEG001").set("input_bcomb_timber_mark", "SEG001"));
    assertThat(release()).isEmpty();
    assertThat(f.api.names()).containsExactly("HBS_RATES.RETRIEVE_BEACHCOMB_RATE");
    assertThat(f.api.calls.get(0).arg("i_beachcomb_seg_timber_mark")).isEqualTo("SEG001");
  }

  // ── Area based (BT15212) ─────────────────────────────────────────────────

  private void areaBased() {
    f.pieceVersion.set("pssmry_type", "AREAEST").set("scl_rtn_category_code", "CR").set("area_cut", 12.5)
        .set("log_count", 0L);
    f.lookups.marks.put(MARK, EditsFixture.mark("A01", "Y", "S"));
    f.lookups.sites.put("S123", new ScaleSite("S123", "00099999", "01", "DKA Cruise Based Site", 10L));
    f.noPieceSegs = true;
  }

  static Stream<Arguments> areaRules() {
    return Stream.of(
        Arguments.of("CBSD01", (Consumer<FakeLookups>) l -> l.harvestDateValid = false),
        Arguments.of("CBSD02", (Consumer<FakeLookups>) l -> l.sameScaleDateCount = 2),
        Arguments.of("CBTP01", (Consumer<FakeLookups>) l -> l.cruiseProfile = 0),
        Arguments.of("CBCV01", (Consumer<FakeLookups>) l -> l.worksheetVals = new Lookups.WorksheetVals(0, 90, 0)),
        Arguments.of("CBCV01", (Consumer<FakeLookups>) l -> {
          l.worksheetVals = new Lookups.WorksheetVals(5, 100, 10);
          l.activeVolumePerHectare = 95;
        }),
        Arguments.of("CBAF01", (Consumer<FakeLookups>) l -> l.totalAreaCut = 101));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("areaRules")
  void areaBasedEdits(String code, Consumer<FakeLookups> setup) {
    areaBased();
    setup.accept(f.lookups);
    List<EditError> errors = release();
    assertThat(codes(errors)).containsExactly(code);
    assertThat(errors.get(0).category()).isEqualTo("SCL");
  }

  @Test
  void areaBasedReturnWithSegregationsNotYetGeneratedIsClean() {
    areaBased();
    assertThat(release()).isEmpty();
  }

  @Test
  void areaBasedReturnWithNoAreaCutStopsAfterTheHeader() {
    areaBased();
    f.pieceVersion.set("area_cut", 0);
    f.lookups.cruiseProfile = 0;
    assertThat(release()).isEmpty();
    assertThat(f.api.calls).isEmpty();
  }

  @Test
  void applyEditsForClientRunsOnlyTheClientEdits() {
    f.pieceVersion.set("client_locn_code_paid_by", "77").set("scale_date", Timestamp.valueOf("2030-01-01 00:00:00"));
    assertThat(codes(f.edits.applyEditsForClient(f.piece()))).containsExactly("BT03");
  }
}
