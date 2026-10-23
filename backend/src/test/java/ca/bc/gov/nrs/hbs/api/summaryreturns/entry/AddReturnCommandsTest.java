package ca.bc.gov.nrs.hbs.api.summaryreturns.entry;

import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.BILL_ADMIN;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.CLI_CRUISE_ADMIN;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.CLI_SCALER;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.CLI_SITE_ADMIN;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.CLI_USER;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.SCALE_ADMIN;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.SPC_SUBM_AGNT;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.SUMM_DATA_CORR;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.SUMM_DATA_CTL;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.EntryFixture.assertProblem;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.EntryFixture.pieceBody;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.EntryFixture.problems;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.EntryFixture.sampleBody;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.EntryFixture.weightBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryFixtures;
import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi.Call;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.access.AccessDeniedException;

/** P050 / P100 / P150 Add ({@code summaryReturns.addPiece / addWeight / addSample}). */
class AddReturnCommandsTest {

  private final EntryFixture x = new EntryFixture();

  private Call only(String proc) {
    List<Call> calls = x.api.callsTo(proc);
    assertThat(calls).hasSize(1);
    return calls.get(0);
  }

  // ── Piece: happy paths ───────────────────────────────────────────────────

  @Test
  void addsAPieceReturnAsOipIncVersionOne() {
    Map<String, Object> out = x.addPiece().execute(pieceBody());

    assertThat(x.api.names()).containsExactly("HBS_CREATE_AUDIT", "HBS_CREATE_SCL_RTN_AUDIT",
        "HBS_CREATE_HBS_SCALE_RETURN", "HBS_CREATE_PIECE_SCALE_SUMMARY");
    Call header = only("HBS_CREATE_HBS_SCALE_RETURN");
    assertThat(header.arg("i_document_control_number")).isEqualTo(new BigDecimal(1001));
    assertThat(header.arg("i_scale_return_status_code")).isEqualTo("OIP");
    assertThat(header.arg("i_generated_summary_ind")).isEqualTo("N");
    assertThat(header.arg("i_hbs_return_type_code")).isEqualTo("P");
    assertThat(header.arg("i_volest_batch_id")).isNull();
    Call v = only("HBS_CREATE_PIECE_SCALE_SUMMARY");
    assertThat(v.arg("i_version")).isEqualTo(BigDecimal.ONE);
    assertThat(v.arg("i_scl_rtn_version_state_code")).isEqualTo("INC");
    assertThat(v.arg("i_pssmry_type")).isEqualTo("SMRYDOC");
    assertThat(v.arg("i_scl_rtn_category_code")).isEqualTo("PR");
    assertThat(v.arg("i_license_number")).isEqualTo("K123");
    assertThat(v.arg("i_quant_lic_type_cd")).isEqualTo("S");
    assertThat(v.arg("i_active_version_flag")).isEqualTo("Y");
    assertThat(v.arg("i_visibility_indicator")).isEqualTo("Y");
    assertThat(v.arg("i_queued_for_print_indicator")).isEqualTo("N");
    assertThat(v.arg("i_log_count")).isEqualTo(BigDecimal.TEN);
    assertThat(v.arg("i_place_of_scale")).isEqualTo("HERE");
    assertThat(v.arg("i_scale_date")).isEqualTo(Timestamp.valueOf("2026-09-15 00:00:00"));
    assertThat(v.arg("i_area_cut")).isNull();
    Call audit = only("HBS_CREATE_AUDIT");
    assertThat(audit.arg("i_action_type_code")).isEqualTo("INS");
    assertThat(audit.arg("i_object_new_id")).isEqualTo("1001,1");
    assertThat(only("HBS_CREATE_SCL_RTN_AUDIT").arg("i_field_name")).isEqualTo("Scale_Return");
    assertThat(out).containsEntry("documentControlNumber", 1001L).containsEntry("version", 1L)
        .containsEntry("returnStateCode", "OIP").containsEntry("status", "INC").containsKey("updateTimestamp");
  }

  @Test
  void aLicenceThatIsNotAScalingLicenceIsTypeR() {
    Map<String, Object> body = pieceBody();
    body.put("scalerLicence", "r900");
    x.addPiece().execute(body);
    Call v = only("HBS_CREATE_PIECE_SCALE_SUMMARY");
    assertThat(v.arg("i_license_number")).isEqualTo("R900");
    assertThat(v.arg("i_quant_lic_type_cd")).isEqualTo("R");
  }

  @Test
  void theBatchSlipGoesOnTheDocument() {
    Map<String, Object> body = pieceBody();
    body.put("pssmryType", "CRSBSDEST");
    body.put("batchSlipId", "77");
    x.caller = SummaryFixtures.caller(SUMM_DATA_CORR);
    x.addPiece().execute(body);
    assertThat(only("HBS_CREATE_HBS_SCALE_RETURN").arg("i_volest_batch_id")).isEqualTo(new BigDecimal(77));
    assertThat(only("HBS_CREATE_PIECE_SCALE_SUMMARY").arg("i_scl_rtn_category_code")).isEqualTo("CR");
  }

  @Test
  void areaBasedEstimateDerivesLicenceSiteReturnNumberAndLogCount() {
    x.caller = SummaryFixtures.caller(SCALE_ADMIN);
    x.lookups.maxReturn.put("CRS001", "0007");
    Map<String, Object> body = pieceBody();
    body.put("pssmryType", "areaest");
    body.put("timberMark", "CRS001");
    body.remove("scalerLicence");
    body.remove("returnNumber");
    body.remove("scaleSite");
    body.remove("logCount");
    body.put("category", "PR"); // ignored: the page sent CR
    body.put("areaCut", "12.5");
    x.addPiece().execute(body);
    Call v = only("HBS_CREATE_PIECE_SCALE_SUMMARY");
    assertThat(v.arg("i_pssmry_type")).isEqualTo("AREAEST");
    assertThat(v.arg("i_scl_rtn_category_code")).isEqualTo("CR");
    assertThat(v.arg("i_license_number")).isEqualTo("A999");
    assertThat(v.arg("i_scale_site_id_nmbr")).isEqualTo("S456");
    assertThat(v.arg("i_return_number")).isEqualTo("0008");
    assertThat(v.arg("i_log_count")).isEqualTo(BigDecimal.ZERO);
    assertThat(v.arg("i_area_cut")).isEqualTo(new BigDecimal("12.5"));
    assertThat(v.arg("i_quant_lic_type_cd")).isEqualTo("R"); // from the (blank) licence entered, as legacy
  }

  @Test
  void theFirstAreaBasedReturnOfAMarkIsNumbered0002AsInLegacy() {
    x.caller = SummaryFixtures.caller(CLI_CRUISE_ADMIN, CLI_USER);
    Map<String, Object> body = pieceBody();
    body.put("pssmryType", "AREAEST");
    body.put("timberMark", "CRS001");
    body.put("areaCut", "1");
    x.addPiece().execute(body);
    assertThat(only("HBS_CREATE_PIECE_SCALE_SUMMARY").arg("i_return_number")).isEqualTo("0002");
  }

  @Test
  void aWasteReturnUsesSiteZzzCategoryWaAndCutBlockNoneWhenBlank() {
    x.caller = SummaryFixtures.caller(SUMM_DATA_CORR);
    Map<String, Object> body = pieceBody();
    body.put("pssmryType", "WSTRESEST");
    body.put("scaleSite", "S123");
    x.addPiece().execute(body);
    Call v = only("HBS_CREATE_PIECE_SCALE_SUMMARY");
    assertThat(v.arg("i_scale_site_id_nmbr")).isEqualTo("ZZZ");
    assertThat(v.arg("i_scl_rtn_category_code")).isEqualTo("WA");
    assertThat(v.arg("i_cut_block_id")).isEqualTo("None");
  }

  @Test
  void aWasteReturnOnAMarkWithCutBlocksNeedsOneOfThem() {
    x.caller = SummaryFixtures.caller(SUMM_DATA_CORR);
    x.lookups.cutBlocks.put("ABC123", Set.of("CB1"));
    Map<String, Object> body = pieceBody();
    body.put("pssmryType", "WSTRESEST");
    body.put("cutBlockId", "cb1");
    x.addPiece().execute(body);
    assertThat(only("HBS_CREATE_PIECE_SCALE_SUMMARY").arg("i_cut_block_id")).isEqualTo("CB1");
  }

  // ── Piece: roles (add/P050.jsp grants + the P002 menu) ───────────────────

  @Test
  void billingAdminMayNotAddPieceReturns() {
    x.caller = SummaryFixtures.caller(BILL_ADMIN);
    assertThatThrownBy(() -> x.addPiece().execute(pieceBody())).isInstanceOf(AccessDeniedException.class)
        .hasMessage(EntryMessages.ACCESS_DISALLOWED);
    assertThat(x.api.calls).isEmpty();
  }

  @Test
  void theCruiseAdminRoleOnlyAddsAreaBasedEstimates() {
    x.caller = SummaryFixtures.caller(CLI_CRUISE_ADMIN, CLI_USER);
    assertThatThrownBy(() -> x.addPiece().execute(pieceBody())).isInstanceOf(AccessDeniedException.class);
    assertThat(EntryRules.mayAddPiece(x.caller, "AREAEST")).isTrue();
    assertThat(EntryRules.mayAddPiece(SummaryFixtures.caller(SUMM_DATA_CTL), "AREAEST")).isFalse();
    assertThat(EntryRules.mayAddPiece(SummaryFixtures.caller(SUMM_DATA_CTL), "CRSBSDEST")).isFalse();
    assertThat(EntryRules.mayAddPiece(SummaryFixtures.caller(SPC_SUBM_AGNT), "OTVOLEST")).isTrue();
    assertThat(EntryRules.mayAddPiece(SummaryFixtures.caller(CLI_SCALER), "SMRYDOC")).isTrue();
  }

  @Test
  void anUnknownSummaryTypeIsRefused() {
    Map<String, Object> body = pieceBody();
    body.put("pssmryType", "SFPSMRY");
    assertProblem(() -> x.addPiece().execute(body), "pssmryType", "Unknown piece summary type 'SFPSMRY'.");
  }

  // ── Piece: validations (SummaryScaleReturnValidator.validateP050Form) ─────

  static Stream<Arguments> pieceValidations() {
    String old = (LocalDate.now().getYear() - 8) + "-06-01";
    return Stream.of(
        Arguments.of("category", null, "SMRYDOC", "category", EntryMessages.TYPE_REQUIRED),
        Arguments.of("category", "XX", "SMRYDOC", "category", EntryMessages.TYPE_INVALID),
        Arguments.of("category", "WA", "SMRYDOC", "category", EntryMessages.TYPE_INVALID), // not offered by P050
        Arguments.of("scalerLicence", "Q999", "SMRYDOC", "scalerLicence", EntryMessages.LICENCE_INVALID),
        Arguments.of("scalerLicence", null, "SMRYDOC", "scalerLicence", EntryMessages.LICENCE_REQUIRED),
        Arguments.of("returnNumber", null, "SMRYDOC", "returnNumber", EntryMessages.RETURN_NO_REQUIRED),
        Arguments.of("returnNumber", "0", "SMRYDOC", "returnNumber", EntryMessages.RETURN_NO_NUMERIC),
        Arguments.of("returnNumber", "1A", "SMRYDOC", "returnNumber", EntryMessages.RETURN_NO_NUMERIC),
        Arguments.of("scaleSite", "X1", "SMRYDOC", "scaleSite", EntryMessages.SCALE_SITE_INVALID),
        Arguments.of("scaleSite", null, "SMRYDOC", "scaleSite", EntryMessages.SCALE_SITE_REQUIRED),
        Arguments.of("timberMark", "NOPE", "SMRYDOC", "timberMark", EntryMessages.MARK_INVALID),
        Arguments.of("timberMark", null, "SMRYDOC", "timberMark", EntryMessages.MARK_REQUIRED_PIECE),
        Arguments.of("billToClientNumber", "00012345", "SMRYDOC", "billToClientNumber", EntryMessages.BILL_TO_INVALID),
        Arguments.of("copyToClientLocn", "00", "SMRYDOC", "copyToClientNumber", EntryMessages.COPY_TO_INVALID),
        Arguments.of("numberOfSections", "100", "SMRYDOC", "numberOfSections", EntryMessages.SECTIONS_INVALID),
        Arguments.of("numberOfSections", "x", "SMRYDOC", "numberOfSections", EntryMessages.SECTIONS_INVALID),
        Arguments.of("logCount", "1.5", "SMRYDOC", "logCount", EntryMessages.LOG_COUNT_INVALID),
        Arguments.of("logCount", null, "SMRYDOC", "logCount", EntryMessages.LOG_COUNT_REQUIRED),
        Arguments.of("scaleDate", null, "SMRYDOC", "scaleDate", EntryMessages.SCALE_DATE_REQUIRED),
        Arguments.of("scaleDate", "2026-02-30", "SMRYDOC", "scaleDate", EntryMessages.SCALE_DATE_INVALID),
        Arguments.of("scaleDate", old, "SMRYDOC", "scaleDate", EntryMessages.SCALE_DATE_LIMIT),
        Arguments.of("fieldScaleDeckId", null, "FLDSCLEST", "fieldScaleDeckId", EntryMessages.DECK_ID_REQUIRED),
        Arguments.of("batchSlipId", "7a", "OTVOLEST", "batchSlipId", EntryMessages.BATCH_SLIP_NUMBER),
        Arguments.of("batchSlipId", "99", "OTVOLEST", "batchSlipId", EntryMessages.BATCH_SLIP_INVALID),
        Arguments.of("batchSlipId", "78", "OTVOLEST", "batchSlipId", EntryMessages.BATCH_SLIP_CLEARED),
        Arguments.of("timberMark", "PRV001", "CRSBSDEST", "category", EntryMessages.PRIVATE_MARK_CRUISE),
        Arguments.of("cutBlockId", "XYZ", "WSTRESEST", "cutBlockId", EntryMessages.CUT_BLOCK_CTB04));
  }

  @ParameterizedTest(name = "{0}={1} ({2}) → {4}")
  @MethodSource("pieceValidations")
  void pieceValidation(String key, String value, String pssmry, String field, String message) {
    x.caller = SummaryFixtures.caller(SUMM_DATA_CORR, SUMM_DATA_CTL);
    Map<String, Object> body = pieceBody();
    body.put("pssmryType", pssmry);
    body.put(key, value);
    assertProblem(() -> x.addPiece().execute(body), field, message);
    assertThat(x.api.calls).isEmpty();
  }

  @Test
  void aWasteCutBlockMustBeOneOfTheMarksCutBlocks() {
    x.caller = SummaryFixtures.caller(SUMM_DATA_CORR);
    x.lookups.cutBlocks.put("ABC123", Set.of("CB1"));
    Map<String, Object> body = pieceBody();
    body.put("pssmryType", "WSTRESEST");
    body.put("cutBlockId", "None");
    assertProblem(() -> x.addPiece().execute(body), "cutBlockId", EntryMessages.CUT_BLOCK_CTB01);
  }

  static Stream<Arguments> areaBasedValidations() {
    String old = (LocalDate.now().getYear() - 8) + "-06-30";
    return Stream.of(
        Arguments.of("areaCut", null, EntryMessages.AREA_CUT_REQUIRED),
        Arguments.of("areaCut", "-1", EntryMessages.AREA_CUT_NUMERIC),
        Arguments.of("areaCut", "abc", EntryMessages.AREA_CUT_NUMERIC),
        Arguments.of("areaCut", "10000", EntryMessages.AREA_CUT_EXCEEDS),
        Arguments.of("areaCut", "1.25", EntryMessages.AREA_CUT_DECIMALS),
        Arguments.of("scaleDate", null, EntryMessages.HARVEST_DATE_REQUIRED),
        Arguments.of("scaleDate", "2026-13-01", EntryMessages.HARVEST_DATE_INVALID),
        Arguments.of("scaleDate", old, EntryMessages.HARVEST_DATE_LIMIT));
  }

  @ParameterizedTest(name = "{0}={1} → {2}")
  @MethodSource("areaBasedValidations")
  void areaBasedValidation(String key, String value, String message) {
    x.caller = SummaryFixtures.caller(SCALE_ADMIN);
    Map<String, Object> body = pieceBody();
    body.put("pssmryType", "AREAEST");
    body.put("timberMark", "CRS001");
    body.put("areaCut", "5");
    body.put(key, value);
    assertThat(problems(() -> x.addPiece().execute(body))).extracting(p -> p.message()).contains(message);
  }

  @Test
  void anAreaBasedMarkMustBeCruiseBased() {
    x.caller = SummaryFixtures.caller(SCALE_ADMIN);
    Map<String, Object> body = pieceBody();
    body.put("pssmryType", "AREAEST");
    body.put("areaCut", "5");
    assertProblem(() -> x.addPiece().execute(body), "timberMark", EntryMessages.MARK_NOT_CRUISE_BASED);
  }

  @Test
  void anIndustryCruiseAdminMustOwnTheMark() {
    x.caller = new ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryCaller("BCEID\\CBA", Set.of(CLI_CRUISE_ADMIN, CLI_USER),
        false, "00077777");
    Map<String, Object> body = pieceBody();
    body.put("pssmryType", "AREAEST");
    body.put("timberMark", "CRS001");
    body.put("areaCut", "5");
    assertProblem(() -> x.addPiece().execute(body), "timberMark", EntryMessages.MARK_NOT_CLIENTS);
  }

  @Test
  void anAreaBasedMarkNeedsACruiseScaleSite() {
    x.caller = SummaryFixtures.caller(CLI_CRUISE_ADMIN, CLI_USER);
    x.lookups.areaSite = "ZZZ";
    Map<String, Object> body = pieceBody();
    body.put("pssmryType", "AREAEST");
    body.put("timberMark", "CRS001");
    body.put("areaCut", "5");
    assertProblem(() -> x.addPiece().execute(body), "timberMark", EntryMessages.MARK_BROKEN_LINK);
    // legacy defect kept: a ministry user's mark is never flagged valid, so the link is not checked
    x.caller = SummaryFixtures.caller(SCALE_ADMIN);
    x.addPiece().execute(body);
    assertThat(only("HBS_CREATE_PIECE_SCALE_SUMMARY").arg("i_scale_site_id_nmbr")).isEqualTo("ZZZ");
  }

  // ── validateDocumentEligibleForUpdate (volume estimates) ─────────────────

  @Test
  void aClientScalerNeedsAnAuthKeyForTheLicenceOnAnEstimate() {
    x.caller = SummaryFixtures.caller(CLI_SCALER);
    Map<String, Object> body = pieceBody();
    body.put("pssmryType", "OTVOLEST");
    assertProblem(() -> x.addPiece().execute(body), "scalerLicence", EntryMessages.LICENCE_USER_ACCESS);
    x.f.access.licences = Set.of("K123");
    x.addPiece().execute(body);
    assertThat(x.api.callsTo("HBS_CREATE_PIECE_SCALE_SUMMARY")).hasSize(1);
  }

  @Test
  void aSiteAdminOrScaleAdminNeedsTheSiteInTheirDomain() {
    Map<String, Object> body = pieceBody();
    body.put("pssmryType", "OTVOLEST");
    x.caller = SummaryFixtures.caller(CLI_SITE_ADMIN);
    assertProblem(() -> x.addPiece().execute(body), "scaleSite", EntryMessages.SITE_USER_ACCESS);
    x.caller = SummaryFixtures.caller(SCALE_ADMIN);
    assertProblem(() -> x.addPiece().execute(body), "scaleSite", EntryMessages.SITE_USER_ACCESS);
    x.caller = SummaryFixtures.caller(CLI_SCALER, CLI_SITE_ADMIN);
    assertThat(problems(() -> x.addPiece().execute(body))).extracting(p -> p.message())
        .contains(EntryMessages.SITE_USER_ACCESS, EntryMessages.LICENCE_USER_ACCESS);
    x.f.access.siteDomain = Set.of("S123");
    x.addPiece().execute(body); // the dual role needs either one
    assertThat(x.api.callsTo("HBS_CREATE_PIECE_SCALE_SUMMARY")).hasSize(1);
  }

  @Test
  void theEligibilityRuleDoesNotApplyToSubmittedSummariesOrDataCorrection() {
    x.caller = SummaryFixtures.caller(CLI_SCALER);
    x.addPiece().execute(pieceBody()); // SMRYDOC
    x.caller = SummaryFixtures.caller(SUMM_DATA_CORR);
    Map<String, Object> body = pieceBody();
    body.put("pssmryType", "OTVOLEST");
    x.addPiece().execute(body);
    assertThat(x.api.callsTo("HBS_CREATE_PIECE_SCALE_SUMMARY")).hasSize(2);
  }

  // ── Weight ───────────────────────────────────────────────────────────────

  @Test
  void addsAWeightReturn() {
    Map<String, Object> body = weightBody();
    body.put("billToClientNumber", "00012345");
    body.put("billToClientLocn", "00");
    x.addWeight().execute(body);
    assertThat(x.api.names()).containsExactly("HBS_CREATE_AUDIT", "HBS_CREATE_SCL_RTN_AUDIT",
        "HBS_CREATE_HBS_SCALE_RETURN", "HBS_CREATE_WEIGHT_SCL_SUMMARY");
    Call v = only("HBS_CREATE_WEIGHT_SCL_SUMMARY");
    assertThat(v.arg("i_population_number")).isEqualTo("1001");
    assertThat(v.arg("i_sampling_year")).isEqualTo(new BigDecimal(2026));
    assertThat(v.arg("i_visibility_ind")).isEqualTo("N");
    assertThat(v.arg("i_active_version_ind")).isEqualTo("Y");
    assertThat(v.arg("i_license_number")).isNull();
    assertThat(v.arg("i_client_number_paid_by")).isEqualTo("00012345");
    assertThat(only("HBS_CREATE_HBS_SCALE_RETURN").arg("i_hbs_return_type_code")).isEqualTo("W");
  }

  @Test
  void onlySummaryDataControlAddsWeightAndSampleReturns() {
    x.caller = SummaryFixtures.caller(SUMM_DATA_CORR);
    assertThatThrownBy(() -> x.addWeight().execute(weightBody())).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> x.addSample().execute(sampleBody())).isInstanceOf(AccessDeniedException.class);
  }

  static Stream<Arguments> weightValidations() {
    return Stream.of(
        Arguments.of("category", null, "category", EntryMessages.TYPE_REQUIRED),
        Arguments.of("category", "CH", "category", EntryMessages.TYPE_INVALID),
        Arguments.of("populationNumber", null, "populationNumber", EntryMessages.PSY_REQUIRED),
        Arguments.of("populationNumber", "10011", "populationNumber", EntryMessages.POPULATION_INVALID),
        Arguments.of("stratumNumber", "AB", "stratumNumber", EntryMessages.STRATUM_INVALID),
        Arguments.of("samplingYear", "0", "samplingYear", EntryMessages.POP_YEAR_INVALID),
        Arguments.of("stratumNumber", "09", "populationNumber", EntryMessages.PSY_INVALID),
        Arguments.of("scaleSite", null, "scaleSite", EntryMessages.SCALE_SITE_REQUIRED),
        Arguments.of("scaleSite", "Q1", "scaleSite", EntryMessages.SCALE_SITE_INVALID),
        Arguments.of("timberMark", null, "timberMark", EntryMessages.MARK_REQUIRED),
        Arguments.of("timberMark", "NOPE", "timberMark", EntryMessages.MARK_INVALID),
        Arguments.of("billToClientLocn", "00", "billToClientNumber", EntryMessages.BILL_TO_INVALID),
        Arguments.of("scaleDate", null, "scaleDate", EntryMessages.SCALE_DATE_REQUIRED));
  }

  @ParameterizedTest(name = "{0}={1} → {3}")
  @MethodSource("weightValidations")
  void weightValidation(String key, String value, String field, String message) {
    Map<String, Object> body = weightBody();
    body.put(key, value);
    assertProblem(() -> x.addWeight().execute(body), field, message);
  }

  @Test
  void aDefaultRatioPopulationTakesPrimaryScaleOnly() {
    x.lookups.ratioTypes.put("1001/2026", "D");
    Map<String, Object> body = weightBody();
    body.put("category", "NB");
    assertProblem(() -> x.addWeight().execute(body), "category", EntryMessages.DEFAULT_POP_CATEGORY);
  }

  @Test
  void aFinalBillNeedsCyclicBillingActivity() {
    Map<String, Object> body = weightBody();
    body.put("category", "FB");
    assertProblem(() -> x.addWeight().execute(body), null, EntryMessages.PSYM_REQUIRED);
    x.f.repo.cyclicBilling = new Row().set("final_bill_ind", "N");
    x.addWeight().execute(body);
    assertThat(x.api.callsTo("HBS_CREATE_WEIGHT_SCL_SUMMARY")).hasSize(1);
  }

  // ── Sample ───────────────────────────────────────────────────────────────

  @Test
  void addsASampleReturn() {
    x.addSample().execute(sampleBody());
    assertThat(x.api.names()).containsExactly("HBS_CREATE_AUDIT", "HBS_CREATE_SCL_RTN_AUDIT",
        "HBS_CREATE_HBS_SCALE_RETURN", "HBS_CREATE_SAMPLE_SCL_SUMMARY");
    Call v = only("HBS_CREATE_SAMPLE_SCL_SUMMARY");
    assertThat(v.arg("i_quant_lic_type_cd")).isEqualTo("S");
    assertThat(v.arg("i_net_weight")).isEqualTo(new BigDecimal(30));
    assertThat(v.arg("i_weigh_slip_number")).isEqualTo("W1");
    assertThat(v.arg("i_load_arrival_number")).isEqualTo("3");
    assertThat(v.arg("i_visibility_indicator")).isEqualTo("N");
    assertThat(only("HBS_CREATE_HBS_SCALE_RETURN").arg("i_hbs_return_type_code")).isEqualTo("S");
  }

  static Stream<Arguments> sampleValidations() {
    return Stream.of(
        Arguments.of("category", "FB", "category", EntryMessages.TYPE_INVALID),
        Arguments.of("scalerLicence", null, "scalerLicence", EntryMessages.LICENCE_REQUIRED),
        Arguments.of("scalerLicence", "Q1", "scalerLicence", EntryMessages.LICENCE_INVALID),
        Arguments.of("returnNumber", null, "returnNumber", EntryMessages.RETURN_NO_REQUIRED),
        Arguments.of("returnNumber", "-3", "returnNumber", EntryMessages.RETURN_NO_NUMERIC),
        Arguments.of("netWeight", null, "netWeight", EntryMessages.SAMPLE_WEIGHT_REQUIRED),
        Arguments.of("netWeight", "1.5", "netWeight", "NW00 description of FMT"),
        Arguments.of("logCount", null, "logCount", EntryMessages.LOG_COUNT_REQUIRED),
        Arguments.of("logCount", "x", "logCount", EntryMessages.LOG_COUNT_INVALID),
        Arguments.of("loadArrivalNumber", "0", "loadArrivalNumber", EntryMessages.LOAD_ARRIVAL_INVALID),
        Arguments.of("loadArrivalNumber", null, "loadArrivalNumber", EntryMessages.LOAD_ARRIVAL_INVALID),
        Arguments.of("samplingYear", null, "populationNumber", EntryMessages.PSY_REQUIRED),
        Arguments.of("timberMark", null, "timberMark", EntryMessages.MARK_REQUIRED));
  }

  @ParameterizedTest(name = "{0}={1} → {3}")
  @MethodSource("sampleValidations")
  void sampleValidation(String key, String value, String field, String message) {
    Map<String, Object> body = sampleBody();
    body.put(key, value);
    assertProblem(() -> x.addSample().execute(body), field, message);
  }
}
