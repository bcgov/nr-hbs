package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.TallyFixtures.codes;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.TallyFixtures.document;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.TallyFixtures.logLine;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.TallyFixtures.logTallyRow;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.TallyFixtures.ts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.EditError;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.ScaleSiteRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.TimberMarkRef;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LogTallyValidatorTest {

  private final TallyFixtures f = new TallyFixtures();

  private DetailDocument doc(Row v, Row... lines) {
    return document(DocType.LOG_TALLY, v, lines);
  }

  private List<String> run(DetailDocument d) {
    f.logTally(d).copyInputFieldsAndValidate();
    return codes(d.errors());
  }

  private void beachcombMark() {
    f.ref.marks.put("BCM1", new TimberMarkRef("BCM1", "B17", "N", "B", "00012345", "00", 1L, 1L, "RCO", "A", "HI",
        null, null, null));
    f.ref.rates.put("BCM1", ReferenceData.RateCheck.HAS_RATE);
    f.ref.gradeSchedules.put("BCM1", "C");
  }

  // ── whole run ─────────────────────────────────────────────────────

  @Test
  void cleanTallyHasNoErrors() {
    assertThat(run(doc(logTallyRow(), logLine(1)))).isEmpty();
  }

  @Test
  void noEditsWhenVersionIsIssued() {
    Row v = logTallyRow().set("scl_rtn_version_state_code", "ISS").set("scale_site_id_nmbr", null);
    assertThat(run(doc(v, logLine(1)))).isEmpty();
  }

  // ── version input fields ──────────────────────────────────────────

  @Test
  void inputScaleSiteCopiedOrSs01() {
    Row v = logTallyRow().set("scale_site_id_nmbr", null).set("input_scale_site", "S001");
    DetailDocument d = doc(v, logLine(1));
    f.logTally(d).validateAndCopyVersionInputFields();
    assertThat(v.str("scale_site_id_nmbr")).isEqualTo("S001");
    assertThat(d.errors()).isEmpty();

    Row bad = logTallyRow().set("scale_site_id_nmbr", null).set("input_scale_site", "NOPE");
    DetailDocument d2 = doc(bad, logLine(1));
    f.logTally(d2).validateAndCopyVersionInputFields();
    assertThat(codes(d2.errors())).containsExactly("SS01");
  }

  @Test
  void inputSourceCopiedToTimberMarkOrTm01() {
    Row v = logTallyRow().set("timber_mark", null).set("input_source", "ABC123");
    f.logTally(doc(v)).validateAndCopyVersionInputFields();
    assertThat(v.str("timber_mark")).isEqualTo("ABC123");

    Row bad = logTallyRow().set("timber_mark", null).set("input_source", "ZZZ999");
    DetailDocument d = doc(bad);
    f.logTally(d).validateAndCopyVersionInputFields();
    assertThat(codes(d.errors())).containsExactly("TM01");
  }

  @Test
  void timberBrandOfOwnSiteIsTb02() {
    Row v = logTallyRow().set("timber_mark", null).set("timber_brand", "BRAND1");
    DetailDocument d = doc(v);
    f.logTally(d).validateAndCopyVersionInputFields();
    assertThat(codes(d.errors())).containsExactly("TB02");
  }

  @Test
  void cutBlockCopiedWithoutValidation() {
    Row v = logTallyRow().set("input_cutblock_id", "NOT-ON-FILE");
    DetailDocument d = doc(v);
    f.logTally(d).validateAndCopyVersionInputFields();
    assertThat(v.str("cut_block_id")).isEqualTo("NOT-ON-FILE");
    assertThat(d.errors()).isEmpty();
  }

  /**
   * {@code StringUtil.empty(String)} keeps whitespace: the one-space value the
   * legacy change-event actions put into an input field is NOT empty, so it is
   * copied / validated like any other input (OSS01: no scale site " ").
   */
  @Test
  void singleSpaceInputIsNotEmpty() {
    Row v = logTallyRow().set("input_cutblock_id", " ").set("input_orig_redtag_scl_site", " ");
    DetailDocument d = doc(v);
    f.logTally(d).validateAndCopyVersionInputFields();
    assertThat(v.str("cut_block_id")).isEqualTo(" ");
    assertThat(codes(d.errors())).containsExactly("OSS01");
  }

  @Test
  void originatingScaleSiteCopiedOrOss01() {
    Row v = logTallyRow().set("input_orig_redtag_scl_site", "S001");
    f.logTally(doc(v)).validateAndCopyVersionInputFields();
    assertThat(v.str("orig_redtag_scale_site_id_nmbr")).isEqualTo("S001");

    Row bad = logTallyRow().set("input_orig_redtag_scl_site", "NOPE");
    DetailDocument d = doc(bad);
    f.logTally(d).validateAndCopyVersionInputFields();
    assertThat(codes(d.errors())).containsExactly("OSS01");
  }

  @Test
  void originalScalerLicenceCopiedOrOsl01() {
    Row v = logTallyRow().set("input_orig_chk_scl_scaler_lic", "5678");
    f.logTally(doc(v)).validateAndCopyVersionInputFields();
    assertThat(v.str("orig_chk_scl_license_number")).isEqualTo("5678");
    assertThat(v.str("orig_chk_scl_quant_lic_type_cd")).isEqualTo("S");

    Row bad = logTallyRow().set("input_orig_chk_scl_scaler_lic", "0000");
    DetailDocument d = doc(bad);
    f.logTally(d).validateAndCopyVersionInputFields();
    assertThat(codes(d.errors())).containsExactly("OSL01");
  }

  @Test
  void scalerLicencesCopiedOrPsl01Ssl01Sgl01() {
    Row v = logTallyRow().set("primary_license_number", null).set("input_primary_scaler_lic", "9")
        .set("input_secondary_scaler_lic", "9").set("input_signing_scaler_lic", "9");
    DetailDocument d = doc(v);
    f.logTally(d).validateAndCopyVersionInputFields();
    assertThat(codes(d.errors())).containsExactly("PSL01", "SSL01", "SGL01");

    Row ok = logTallyRow().set("primary_license_number", null).set("input_primary_scaler_lic", "1234")
        .set("input_secondary_scaler_lic", "5678").set("input_signing_scaler_lic", "1234");
    f.logTally(doc(ok)).validateAndCopyVersionInputFields();
    assertThat(ok.str("primary_license_number")).isEqualTo("1234");
    assertThat(ok.str("secondary_license_number")).isEqualTo("5678");
    assertThat(ok.str("signing_license_number")).isEqualTo("1234");
  }

  @ParameterizedTest
  @CsvSource({"deck1,FSX03", "DECK-1,FSX03", "DECK1,FSX01"})
  void fieldDeckIdMustBeUpperAlphanumericAndKnown(String deck, String code) {
    Row v = logTallyRow().set("field_scale_deck_id", deck);
    DetailDocument d = doc(v);
    f.logTally(d).validateAndCopyVersionInputFields();
    assertThat(codes(d.errors())).containsExactly(code);
  }

  @Test
  void knownFieldDeckPasses() {
    f.tally.decks.put("DECK1/ABC123", 1);
    DetailDocument d = doc(logTallyRow().set("field_scale_deck_id", "DECK1"));
    f.logTally(d).validateAndCopyVersionInputFields();
    assertThat(d.errors()).isEmpty();
  }

  // ── line input fields ─────────────────────────────────────────────

  @Test
  void beachcombLineMarkCopiedWhenOnFile() {
    beachcombMark();
    Row v = logTallyRow().set("timber_mark", "BCM1");
    Row line = logLine(1).set("input_bcomb_timber_mark", "ABC123");
    Row unknown = logLine(2).set("input_bcomb_timber_mark", "NOPE");
    DetailDocument d = doc(v, line, unknown);
    f.logTally(d).validateAndCopyLogsInputFields();
    assertThat(line.str("timber_mark")).isEqualTo("ABC123");
    assertThat(unknown.str("timber_mark")).isNull();
    assertThat(d.errors()).isEmpty();
  }

  @Test
  void beachcombLineMarkNotCopiedOnRegularTally() {
    Row line = logLine(1).set("input_bcomb_timber_mark", "ABC123");
    f.logTally(doc(logTallyRow(), line)).validateAndCopyLogsInputFields();
    assertThat(line.str("timber_mark")).isNull();
  }

  // ── species / product / grade (BT 7012) ───────────────────────────

  @Test
  void spgFoundCopiesScaleCodesWithLogProduct() {
    Row line = logLine(1).set("scale_species_code", null).set("scale_product_code", null)
        .set("scale_grade_code", null);
    DetailDocument d = doc(logTallyRow(), line);
    f.logTally(d).validateSpg();
    assertThat(d.errors()).isEmpty();
    assertThat(line.str("scale_species_code")).isEqualTo("FI");
    assertThat(line.str("scale_grade_code")).isEqualTo("J");
    assertThat(line.str("scale_product_code")).isEqualTo(" ");
  }

  @Test
  void spgNotOnFileIsSp01LoggedOncePerTally() {
    DetailDocument d = doc(logTallyRow(), logLine(1).set("scale_grade_code", "Z"),
        logLine(2).set("scale_grade_code", "Z"));
    f.logTally(d).validateSpg();
    assertThat(codes(d.errors())).containsExactly("SP01");
    EditError e = d.errors().get(0);
    assertThat(e.category()).isEqualTo("SCL");
    assertThat(e.line()).isSameAs(d.version().logs().get(0));
  }

  @Test
  void spgNotEffectiveIsSp02() {
    f.tally.addSpg("FI", " ", "U", TallyFixtures.LONG_AGO, ts("2020-01-01 00:00:00"));
    DetailDocument d = doc(logTallyRow(), logLine(1).set("scale_grade_code", "U"));
    f.logTally(d).validateSpg();
    assertThat(codes(d.errors())).containsExactly("SP02");
  }

  @Test
  void spgEffectiveOnLastDayOfExpiry() {
    f.tally.addSpg("FI", " ", "U", TallyFixtures.LONG_AGO, ts("2026-06-01 00:00:00"));
    f.tally.addGrade("C", "U", TallyFixtures.LONG_AGO, TallyFixtures.FAR_FUTURE);
    DetailDocument d = doc(logTallyRow().set("scale_date", ts("2026-06-01 15:30:00")),
        logLine(1).set("scale_grade_code", "U"));
    f.logTally(d).validateSpg();
    assertThat(d.errors()).isEmpty();
  }

  @Test
  void gradeNotInCoastalScheduleIsCg01() {
    f.tally.addSpg("FI", " ", "U", TallyFixtures.LONG_AGO, TallyFixtures.FAR_FUTURE);
    DetailDocument d = doc(logTallyRow(), logLine(1).set("scale_grade_code", "U"));
    f.logTally(d).validateSpg();
    assertThat(codes(d.errors())).containsExactly("CG01");
  }

  @Test
  void gradeNotInInteriorScheduleIsIg01() {
    f.ref.gradeSchedules.put("ABC123", "I");
    DetailDocument d = doc(logTallyRow(), logLine(1));
    f.logTally(d).validateSpg();
    assertThat(codes(d.errors())).containsExactly("IG01");
  }

  @Test
  void gradeNotEffectiveIsCg02OrIg02() {
    f.tally.addGrade("C", "J", ts("2027-01-01 00:00:00"), TallyFixtures.FAR_FUTURE);
    DetailDocument d = doc(logTallyRow(), logLine(1));
    f.logTally(d).validateSpg();
    assertThat(codes(d.errors())).containsExactly("CG02");

    f.ref.gradeSchedules.put("ABC123", "I");
    f.tally.addGrade("I", "J", TallyFixtures.LONG_AGO, ts("2020-01-01 00:00:00"));
    DetailDocument d2 = doc(logTallyRow(), logLine(1));
    f.logTally(d2).validateSpg();
    assertThat(codes(d2.errors())).containsExactly("IG02");
  }

  @Test
  void spgSkippedWhenACodeIsMissing() {
    DetailDocument d = doc(logTallyRow(), logLine(1).set("scale_species_code", null).set("input_species", null));
    f.logTally(d).validateSpg();
    assertThat(d.errors()).isEmpty();
  }

  // ── version edits ─────────────────────────────────────────────────

  @Test
  void wasteSiteIsSszz() {
    f.ref.sites.put("ZZZ", new ScaleSiteRef("ZZZ", 1L, "00012345", "00", "Waste", null));
    DetailDocument d = doc(logTallyRow().set("scale_site_id_nmbr", "ZZZ"), logLine(1));
    f.logTally(d).validateVersion();
    assertThat(codes(d.errors())).containsExactly("SSZZ");
  }

  @Test
  void unknownSiteOwnerIsCt03() {
    f.ref.sites.put("S002", new ScaleSiteRef("S002", 1L, "99999999", "00", "Other", null));
    DetailDocument d = doc(logTallyRow().set("scale_site_id_nmbr", "S002"), logLine(1));
    f.logTally(d).validateVersion();
    assertThat(codes(d.errors())).containsExactly("CT03");
  }

  @Test
  void cruiseBasedOriginatingSiteIsCbos00() {
    f.ref.sites.put("CB01", new ScaleSiteRef("CB01", 1L, "00012345", "00", "XXX Cruise Based Site", null));
    DetailDocument d = doc(logTallyRow().set("orig_redtag_scale_site_id_nmbr", "CB01"), logLine(1));
    f.logTally(d).validateVersion();
    assertThat(codes(d.errors())).containsExactly("CBOS00");
  }

  @Test
  void beachcombIndicatorMustMatchMark() {
    DetailDocument d = doc(logTallyRow().set("beachcomb_ind", "Y"), logLine(1));
    f.logTally(d).validateVersion();
    assertThat(codes(d.errors())).containsExactly("BC01");

    beachcombMark();
    DetailDocument d2 = doc(logTallyRow().set("timber_mark", "BCM1"),
        logLine(1).set("timber_mark", "ABC123").set("input_bcomb_timber_mark", "ABC123"));
    f.logTally(d2).validateVersion();
    assertThat(codes(d2.errors())).containsExactly("BC02");

    DetailDocument d3 = doc(logTallyRow().set("timber_mark", "BCM1").set("beachcomb_ind", "Y"), logLine(1));
    f.logTally(d3).validateVersion();
    assertThat(d3.errors()).isEmpty();
  }

  @Test
  void beachcombOfUnknownMarkThrowsAsLegacy() {
    DetailDocument d = doc(logTallyRow().set("timber_mark", "NOPE"), logLine(1));
    assertThatThrownBy(() -> f.logTally(d).validateVersion()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void unregulatedBeachcombBillsSiteOwner() {
    // BCOMB2's own client is unknown: billing the mark's client would be BT03
    f.ref.marks.put("BCOMB2", new TimberMarkRef("BCOMB2", "B17", "N", "B", "77777777", "00", 1L, 1L, "RCO", "A",
        "HI", null, null, null));
    f.ref.rates.put("BCOMB2", ReferenceData.RateCheck.HAS_RATE);
    DetailDocument d = doc(logTallyRow().set("timber_mark", "BCOMB2").set("beachcomb_ind", "Y"), logLine(1));
    f.logTally(d).validateVersion();
    assertThat(d.errors()).isEmpty();
  }

  @Test
  void unknownMarkClientIsBt03() {
    f.ref.marks.put("XYZ1", new TimberMarkRef("XYZ1", "A01", "N", "B", "77777777", "00", 1L, 1L, "RCO", "A",
        "HI", null, null, null));
    f.ref.rates.put("XYZ1", ReferenceData.RateCheck.HAS_RATE);
    DetailDocument d = doc(logTallyRow().set("timber_mark", "XYZ1"), logLine(1));
    f.logTally(d).validateVersion();
    assertThat(codes(d.errors())).containsExactly("BT03");
  }

  @Test
  void fieldScaleWithoutDeckIsFsd01AndDeckIsTrimmed() {
    DetailDocument d = doc(logTallyRow().set("field_scale_ind", "Y"), logLine(1));
    f.logTally(d).validateVersion();
    assertThat(codes(d.errors())).containsExactly("FSD01");

    Row v = logTallyRow().set("field_scale_ind", "Y").set("field_scale_deck_id", " D1 ");
    DetailDocument d2 = doc(v, logLine(1));
    f.logTally(d2).validateVersion();
    assertThat(v.str("field_scale_deck_id")).isEqualTo("D1");
    assertThat(d2.errors()).isEmpty();
  }

  @Test
  void deckOnNonFieldScaledOriginalIsFsd02() {
    DetailDocument d = doc(logTallyRow().set("field_scale_deck_id", "D1"), logLine(1));
    f.logTally(d).validateVersion();
    assertThat(codes(d.errors())).containsExactly("FSD02");
  }

  @Test
  void scaleDateAfterTodayIsSd03() {
    DetailDocument d = doc(logTallyRow().set("scale_date", ts("2026-06-16 00:00:00")), logLine(1));
    f.logTally(d).validateVersion();
    assertThat(codes(d.errors())).containsExactly("SD03");
  }

  @Test
  void noRateIsSd08Billing() {
    f.ref.rates.remove("ABC123");
    DetailDocument d = doc(logTallyRow(), logLine(1));
    f.logTally(d).validateVersion();
    assertThat(codes(d.errors())).containsExactly("SD08");
    assertThat(d.errors().get(0).category()).isEqualTo("BIL");
  }

  @Test
  void parcelEdits() {
    DetailDocument d = doc(logTallyRow().set("parcel_identifier", 3L).set("parcel_count", 2L), logLine(1));
    f.logTally(d).validateVersion();
    assertThat(codes(d.errors())).containsExactly("PI01");

    DetailDocument d2 = doc(logTallyRow().set("parcel_identifier", 1L), logLine(1));
    f.logTally(d2).validateVersion();
    assertThat(codes(d2.errors())).containsExactly("PC00");
  }

  @Test
  void signingScalerMustBePrimaryOrSecondary() {
    DetailDocument d = doc(logTallyRow().set("signing_license_number", "5678"), logLine(1));
    f.logTally(d).validateVersion();
    assertThat(codes(d.errors())).containsExactly("SGL02");
  }

  @Test
  void redTagWeighSlipMustBeNumeric() {
    DetailDocument d = doc(logTallyRow().set("red_tag_weigh_slip", "12A"), logLine(1));
    f.logTally(d).validateVersion();
    assertThat(codes(d.errors())).containsExactly("WSN01");

    DetailDocument d2 = doc(logTallyRow().set("red_tag_weigh_slip", "123"), logLine(1));
    f.logTally(d2).validateVersion();
    assertThat(d2.errors()).isEmpty();
  }

  @Test
  void logCountMustMatchLines() {
    DetailDocument d = doc(logTallyRow().set("log_count", 2L), logLine(1));
    f.logTally(d).validateVersion();
    assertThat(codes(d.errors())).containsExactly("LC01");
  }

  // ── check scale ───────────────────────────────────────────────────

  private Row checkScale() {
    return logTallyRow().set("scale_event_type_code", "CS").set("orig_chk_scl_license_number", "5678")
        .set("orig_chk_scl_return_number", 77L).set("check_replaces_original_ind", "Y");
  }

  @Test
  void checkScaleByDdnAgainstValidOriginal() {
    f.original("ORIG", DocType.LOG_TALLY, "OIP", "RDY");
    Row v = checkScale().set("original_ddn", "ORIG");
    DetailDocument d = doc(v, logLine(1));
    f.logTally(d).validateVersion();
    assertThat(d.errors()).isEmpty();
    assertThat(v.lng("original_ddv")).isEqualTo(1L);
  }

  @Test
  void checkScaleByDdnMismatchIsChks12AndChks13() {
    f.original("ORIG", DocType.LOG_TALLY, "OIP", "RDY").set("primary_license_number", "1111")
        .set("return_number", 1L);
    DetailDocument d = doc(checkScale().set("original_ddn", "ORIG"), logLine(1));
    f.logTally(d).validateVersion();
    assertThat(codes(d.errors())).containsExactly("CHKS12", "CHKS13");
  }

  @Test
  void checkScaleByDdnWithoutActiveVersionIsChks11() {
    f.original("ORIG", DocType.LOG_TALLY, "OIP", "RDY").set("active_version_ind", "N");
    DetailDocument d = doc(checkScale().set("original_ddn", "ORIG"), logLine(1));
    f.logTally(d).validateVersion();
    assertThat(codes(d.errors())).containsExactly("CHKS11");
  }

  @Test
  void checkScaleByUnknownDdnIsSilent() {
    DetailDocument d = doc(checkScale().set("original_ddn", "NOPE"), logLine(1));
    f.logTally(d).validateVersion();
    assertThat(d.errors()).isEmpty();
  }

  @Test
  void checkScaleByScalerAndReturnNumber() {
    f.original("ORIG", DocType.LOG_TALLY, "OIP", "RDY");
    f.tally.originals.add("ORIG");
    Row v = checkScale();
    DetailDocument d = doc(v, logLine(1));
    f.logTally(d).validateVersion();
    assertThat(d.errors()).isEmpty();
    assertThat(f.tally.searchedType).isEqualTo(DocType.LOG_TALLY);
    assertThat(v.str("original_ddn")).isEqualTo("ORIG");
    assertThat(v.lng("original_ddv")).isEqualTo(1L);
  }

  @Test
  void noOriginalIsChks01AndSeveralIsChks02() {
    DetailDocument d = doc(checkScale(), logLine(1));
    f.logTally(d).validateVersion();
    assertThat(codes(d.errors())).containsExactly("CHKS01");

    f.tally.originals.addAll(List.of("A", "B"));
    DetailDocument d2 = doc(checkScale(), logLine(1));
    f.logTally(d2).validateVersion();
    assertThat(codes(d2.errors())).containsExactly("CHKS02");
  }

  @Test
  void checkScalerSameAsOriginalIsChks00() {
    f.original("ORIG", DocType.LOG_TALLY, "OIP", "RDY");
    DetailDocument d = doc(checkScale().set("original_ddn", "ORIG").set("primary_license_number", "5678"),
        logLine(1));
    f.logTally(d).validateVersion();
    assertThat(codes(d.errors())).containsExactly("CHKS00");
  }

  @Test
  void notReplacingCheckScaleSkipsOriginalEdits() {
    f.original("ORIG", DocType.LOG_TALLY, "OIP", "LCK");
    DetailDocument d = doc(checkScale().set("original_ddn", "ORIG").set("check_replaces_original_ind", "N"),
        logLine(1));
    f.logTally(d).validateVersion();
    assertThat(d.errors()).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({
      "after check scale,     2026-06-02 00:00:00, PS, N, OIP, RDY, CHKSD1",
      "more than 45 days,     2026-04-16 00:00:00, PS, N, OIP, RDY, CHKSD2",
      "exactly 45 days,       2026-04-17 00:00:00, PS, N, OIP, RDY, ",
      "original check scale,  2026-05-20 00:00:00, CS, N, OIP, RDY, CHKS03",
      "SB4 rescale,           2026-05-20 00:00:00, 4R, N, OIP, RDY, CHKS04",
      "company use,           2026-05-20 00:00:00, CU, N, OIP, RDY, CHKS05",
      "already replaced,      2026-05-20 00:00:00, PS, Y, OIP, RDY, CHKS06",
      "not ready,             2026-05-20 00:00:00, PS, N, OIP, ERR, CHKS07",
      "original complete,     2026-05-20 00:00:00, PS, N, OCM, RDY, CHKS07",
      "replace no changes,    2026-05-20 00:00:00, PS, N, RNP, RDY, ",
      "replace in progress,   2026-05-20 00:00:00, PS, N, RCP, RDY, ",
      "date wins over event,  2026-06-02 00:00:00, CS, Y, OIP, ERR, CHKSD1",
  })
  void validateAgainstOriginal(String name, String originalDate, String event, String replaced, String docState,
                               String versionState, String expected) {
    f.original("ORIG", DocType.LOG_TALLY, docState, versionState).set("scale_date", ts(originalDate))
        .set("scale_event_type_code", event).set("check_replaces_original_ind", replaced);
    DetailDocument d = doc(checkScale().set("original_ddn", "ORIG"), logLine(1));
    f.logTally(d).validateVersion();
    assertThat(codes(d.errors())).isEqualTo(expected == null ? List.of() : List.of(expected));
  }

  // ── line edits ────────────────────────────────────────────────────

  @ParameterizedTest(name = "length={0} top={1} butt={2}")
  @CsvSource({
      "5.0,   20,  25, ",
      "0,     20,  25, LT01",
      "100.0, 20,  25, LT01",
      "99.9,  20,  25, ",
      "5.0,   0,   25, TP01",
      "5.0,   200, 25, TPMax",
      "5.0,   199, 25, ",
      "5.0,   20,  0,  BT01",
      "5.0,   20,  200, BTMax",
  })
  void dimensions(String length, String top, String butt, String expected) {
    DetailDocument d = doc(logTallyRow(), logLine(1).set("length", length).set("top", top).set("butt", butt));
    f.logTally(d).validateLogs();
    assertThat(codes(d.errors())).isEqualTo(expected == null ? List.of() : List.of(expected));
  }

  @Test
  void missingDimensionsAreReported() {
    DetailDocument d = doc(logTallyRow(), logLine(1).set("length", null).set("top", null).set("butt", null));
    f.logTally(d).validateLogs();
    assertThat(codes(d.errors())).containsExactly("LT01", "TP01", "BT01");
  }

  @ParameterizedTest
  @CsvSource({"7,ISB789", "8,ISB789", "J,"})
  void interiorCruiseBasedGrade(String grade, String expected) {
    DetailDocument d = doc(logTallyRow(), logLine(1).set("scale_grade_code", grade));
    f.logTally(d).validateLogs();
    assertThat(codes(d.errors())).isEqualTo(expected == null ? List.of() : List.of(expected));
  }

  @Test
  void duplicateLogNumberIsLg00OnTheRepeat() {
    DetailDocument d = doc(logTallyRow(), logLine(1), logLine(2), logLine(1).set("logdtl_id", 999L));
    f.logTally(d).validateLogs();
    assertThat(codes(d.errors())).containsExactly("LG00");
    assertThat(d.errors().get(0).line()).isSameAs(d.version().logs().get(2));
  }

  @Test
  void beachcombLineMarks() {
    beachcombMark();
    Row v = logTallyRow().set("timber_mark", "BCM1").set("beachcomb_ind", "Y");
    Row noMark = logLine(1).set("nmv_ind", "Y");
    Row markAndNmv = logLine(2).set("timber_mark", "ABC123").set("input_bcomb_timber_mark", "ABC123")
        .set("nmv_ind", "Y");
    Row neither = logLine(3).set("timber_mark", "ABC123").set("nmv_ind", "N");
    Row ok = logLine(4).set("timber_mark", "ABC123").set("input_bcomb_timber_mark", "ABC123");
    DetailDocument d = doc(v, noMark, markAndNmv, neither, ok);
    f.logTally(d).validateLogs();
    List<String> perLine = new ArrayList<>();
    d.errors().forEach(e -> perLine.add(e.line().row().lng("log_number") + ":" + e.code()));
    assertThat(perLine).containsExactly("1:TM02", "2:NM04", "3:NM02");
  }

  @Test
  void lineMarksNotCheckedOnRegularTally() {
    DetailDocument d = doc(logTallyRow(), logLine(1));
    f.logTally(d).validateLogs();
    assertThat(d.errors()).isEmpty();
  }

  // ── billable (BT 15036) ───────────────────────────────────────────

  @ParameterizedTest(name = "event={0} fieldScale={1} mark={2} replaces={3} -> {4}")
  @CsvSource({
      "PS, N, ABC123, ,  true",
      "FD, N, ABC123, N, true",
      "RT, N, ABC123, ,  true",
      "RR, N, ABC123, ,  true",
      "TD, N, ABC123, ,  false",
      "4R, N, ABC123, ,  false",
      "PS, Y, ABC123, ,  false",
      "PS, ,  ABC123, ,  true",
      "PS, N, CASH1,  ,  false",
      "PS, N, NOPE,   ,  false",
      "PS, N, ABC123, Y, false",
      "CS, N, ABC123, Y, true",
      "CS, N, ABC123, N, false",
      "CS, N, ABC123, ,  false",
      "CS, Y, ABC123, Y, false",
      "CS, N, CASH1,  Y, false",
  })
  void isBillableScaleReturn(String event, String fieldScale, String mark, String replaces, boolean billable) {
    f.ref.marks.put("CASH1", new TimberMarkRef("CASH1", "A01", "N", "C", "00012345", "00", 1L, 1L, "RCO", "A",
        "HI", null, null, null));
    Row v = logTallyRow().set("scale_event_type_code", event).set("field_scale_ind", fieldScale)
        .set("timber_mark", mark).set("check_replaces_original_ind", replaces);
    assertThat(f.logTally(doc(v)).isBillableScaleReturn()).isEqualTo(billable);
  }

  @Test
  void isAlphaNumeric() {
    assertThat(LogTallyValidator.isAlphaNumeric("AB12")).isTrue();
    assertThat(LogTallyValidator.isAlphaNumeric("")).isFalse();
    assertThat(LogTallyValidator.isAlphaNumeric(null)).isFalse();
    assertThat(LogTallyValidator.isAlphaNumeric("A B")).isFalse();
  }
}
