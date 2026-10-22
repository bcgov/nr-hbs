package ca.bc.gov.nrs.hbs.api.summaryreturns.entry;

import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.BILL_ADMIN;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.CLI_SCALER;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.SCALE_ADMIN;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.SUMM_DATA_CORR;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.SUMM_DATA_CTL;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType.PIECE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType.SAMPLE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType.WEIGHT;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.EntryFixture.assertProblem;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.EntryFixture.keys;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.summaryreturns.EditError;
import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryCaller;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryFixtures;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnAccess;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi.Call;
import ca.bc.gov.nrs.hbs.api.workflow.StaleUpdateException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/** P053 / P103 / P153 Save ({@code summaryReturns.update}). */
class UpdateReturnCommandTest {

  private final EntryFixture x = new EntryFixture();

  UpdateReturnCommandTest() {
    x.caller = SummaryFixtures.caller(SUMM_DATA_CORR);
  }

  private Row piece(String doc, String ver) {
    x.f.header(100, PIECE, doc, false);
    return x.f.version(PIECE, 100, 1, ver, true);
  }

  private Map<String, Object> body(Object... pairs) {
    Map<String, Object> b = keys(100, 1);
    for (int i = 0; i < pairs.length; i += 2) b.put((String) pairs[i], pairs[i + 1]);
    return b;
  }

  private Call only(String proc) {
    List<Call> calls = x.api.callsTo(proc);
    assertThat(calls).hasSize(1);
    return calls.get(0);
  }

  @Test
  void savesTheChangedFieldsThenRunsTheReleaseEditsAndStoresTheirErrors() {
    piece("OIP", "INC");
    x.f.repo.versionErrors = List.of(55L);
    x.f.edits.errors = List.of(EditError.version("SCL", "TM01"));

    Map<String, Object> out = x.update().execute(body("logCount", "11"));

    assertThat(x.api.names()).containsExactly("HBS_REMOVE_PIECE_SCL_VER_ERR", "HBS_STORE_HBS_SCALE_RETURN",
        "HBS_STORE_PIECE_SCALE_SUMMARY", "HBS_CREATE_AUDIT", "HBS_CREATE_SCL_RTN_AUDIT",
        "HBS_CREATE_PIECE_SCL_VER_ERR");
    assertThat(only("HBS_STORE_PIECE_SCALE_SUMMARY").arg("i_log_count")).isEqualTo(new BigDecimal(11));
    assertThat(only("HBS_STORE_PIECE_SCALE_SUMMARY").arg("i_scl_rtn_version_state_code")).isEqualTo("INC");
    assertThat(only("HBS_CREATE_AUDIT").arg("i_action_type_code")).isEqualTo("UPD");
    Call detail = only("HBS_CREATE_SCL_RTN_AUDIT");
    assertThat(detail.arg("i_field_name")).isEqualTo("Log_Count");
    assertThat(detail.arg("i_old_value")).isEqualTo("10");
    assertThat(detail.arg("i_new_value")).isEqualTo("11");
    assertThat(only("HBS_CREATE_PIECE_SCL_VER_ERR").arg("i_hbs_edit_err_message_code")).isEqualTo("TM01");
    assertThat(x.f.edits.calls).isEqualTo(1);
    assertThat(out).containsEntry("saved", true).containsEntry("status", "INC")
        .containsEntry("editErrors", List.of("SCL TM01"));
  }

  @Test
  void nothingIsSavedWhenNothingChanged() {
    piece("OIP", "INC");
    Map<String, Object> out = x.update().execute(body("logCount", "10", "timberMark", "abc123"));
    assertThat(x.api.calls).isEmpty();
    assertThat(x.f.edits.calls).isZero();
    assertThat(out).containsEntry("saved", false);
  }

  @Test
  void aValidationFailureWritesNothing() {
    piece("OIP", "INC");
    assertProblem(() -> x.update().execute(body("logCount", "x")), "logCount", EntryMessages.LOG_COUNT_INVALID);
    assertProblem(() -> x.update().execute(body("category", "CR")), "category", EntryMessages.TYPE_INVALID);
    assertThat(x.api.calls).isEmpty();
  }

  @Test
  void aChangedOrSupersededVersionIsRefused() {
    piece("OIP", "INC");
    Map<String, Object> stale = body("logCount", "11");
    stale.put("updateTimestamp", "2026-10-01T09:31:00");
    assertThatThrownBy(() -> x.update().execute(stale)).isInstanceOf(StaleUpdateException.class);
    Map<String, Object> old = body("logCount", "11");
    old.put("version", 2);
    assertThatThrownBy(() -> x.update().execute(old)).isInstanceOf(StaleUpdateException.class);
    assertThat(x.api.calls).isEmpty();
  }

  @Test
  void onlyAnEditableVersionMayBeChanged() {
    piece("OIP", "RDY");
    assertProblem(() -> x.update().execute(body("logCount", "11")), null, EntryMessages.NOT_EDITABLE);
    x.f.header(100, PIECE, "OIP", true);
    x.f.version(PIECE, 100, 1, "INC", true);
    assertProblem(() -> x.update().execute(body("logCount", "11")), null, EntryMessages.GENERATED_NOT_EDITABLE);
    x.f.header(100, PIECE, "OIP", false);
    x.caller = SummaryFixtures.caller(BILL_ADMIN);
    assertProblem(() -> x.update().execute(body("logCount", "11")), null, EntryMessages.NOT_EDITABLE);
  }

  @Test
  void weightAndSampleUpdatesNeedSummaryDataControlOrCorrection() {
    x.f.header(200, WEIGHT, "OIP", false);
    x.f.version(WEIGHT, 200, 1, "INC", true);
    x.caller = SummaryFixtures.caller(SCALE_ADMIN);
    Map<String, Object> b = keys(200, 1);
    assertThatThrownBy(() -> x.update().execute(b)).isInstanceOf(AccessDeniedException.class)
        .hasMessage(EntryMessages.ACCESS_DISALLOWED);
  }

  @Test
  void industryUsersOnlyUpdateTheirClientsReturns() {
    piece("OIP", "INC");
    x.caller = new SummaryCaller("BCEID\\X", Set.of(CLI_SCALER), false, "00077777");
    assertThatThrownBy(() -> x.update().execute(body("logCount", "11"))).isInstanceOf(AccessDeniedException.class)
        .hasMessage(SummaryReturnAccess.NOT_YOUR_RETURN);
  }

  @Test
  void theSegregationsFollowTheReturnCategory() {
    piece("OIP", "INC");
    x.f.pieceSegregation(100, 1, 500, "F", 3, "1.500").set("sb4_ind", "Y").set("timber_mark", null);
    x.update().execute(body("category", "BC"));
    Call seg = only("HBS_STORE_PIECE_SCL_SEG");
    assertThat(seg.arg("i_psseg_type")).isEqualTo("BCOMB");
    assertThat(seg.arg("i_sb4_flag")).isEqualTo("N");
    assertThat(seg.arg("i_avoidable_flag")).isEqualTo("N");
    assertThat(only("HBS_STORE_PIECE_SCALE_SUMMARY").arg("i_scl_rtn_category_code")).isEqualTo("BC");
  }

  @Test
  void anotherScaleSiteClearsCopyToAndAnotherMarkClearsBillTo() {
    Row v = piece("OIP", "INC");
    v.set("client_number_copied_to", "00099999").set("client_locn_code_copied_to", "00");
    x.update().execute(body("scaleSite", "S456", "timberMark", "NEW999"));
    Call store = only("HBS_STORE_PIECE_SCALE_SUMMARY");
    assertThat(store.arg("i_scale_site_id_nmbr")).isEqualTo("S456");
    assertThat(store.arg("i_client_number_copied_to")).isNull();
    assertThat(store.arg("i_client_locn_code_copied_to")).isNull();
    assertThat(store.arg("i_timber_mark")).isEqualTo("NEW999");
    assertThat(store.arg("i_client_number_paid_by")).isNull();
    assertThat(store.arg("i_client_locn_code_paid_by")).isNull();
  }

  @Test
  void aWasteReplacementLosesItsCopyTo() {
    Row v = piece("RCP", "INC");
    v.set("pssmry_type", "WSTRESEST").set("scl_rtn_category_code", "WA").set("scale_site_id_nmbr", "ZZZ")
        .set("cut_block_id", "None")
        .set("client_number_copied_to", "00099999").set("client_locn_code_copied_to", "00");
    x.update().execute(body("logCount", "11", "scaleSite", "S123"));
    Call store = only("HBS_STORE_PIECE_SCALE_SUMMARY");
    assertThat(store.arg("i_client_number_copied_to")).isNull();
    assertThat(store.arg("i_scale_site_id_nmbr")).isEqualTo("ZZZ"); // not editable on a waste return
  }

  @Test
  void aCustodyTransferOfAHeldEstimateMustBeConfirmed() {
    Row v = piece("OIP", "HLD");
    v.set("pssmry_type", "CRSBSDEST").set("scl_rtn_category_code", "CR");
    x.caller = SummaryFixtures.caller(CLI_SCALER);
    x.f.access.licences = Set.of("K123"); // the scaler may edit the return under its current licence

    assertProblem(() -> x.update().execute(body("scalerLicence", "R900")), "confirmCustodyTransfer",
        EntryMessages.confirmCustodyTransfer("S123", "S123", "K123", "R900"));
    assertThat(x.api.calls).isEmpty();

    x.update().execute(body("scalerLicence", "R900", "confirmCustodyTransfer", "Y"));
    Call store = only("HBS_STORE_PIECE_SCALE_SUMMARY");
    assertThat(store.arg("i_license_number")).isEqualTo("R900");
    assertThat(store.arg("i_quant_lic_type_cd")).isEqualTo("R");
  }

  @Test
  void anIncompleteEstimateOutsideTheUsersAuthorityIsAnErrorNotAConfirmation() {
    Row v = piece("OIP", "INC");
    v.set("pssmry_type", "CRSBSDEST").set("scl_rtn_category_code", "CR");
    x.caller = SummaryFixtures.caller(CLI_SCALER);
    x.f.access.licences = Set.of("K123");
    assertProblem(() -> x.update().execute(body("scalerLicence", "R900", "confirmCustodyTransfer", "Y")),
        "scalerLicence", EntryMessages.LICENCE_USER_ACCESS);
  }

  @Test
  void anAreaBasedReturnIsAlwaysSavedAndKeepsItsDerivedFields() {
    Row v = piece("OIP", "INC");
    v.set("pssmry_type", "AREAEST").set("scl_rtn_category_code", "CR").set("timber_mark", "CRS001")
        .set("scale_site_id_nmbr", "S456").set("area_cut", new BigDecimal("5")).set("log_count", 0)
        .set("license_number", "A999").set("return_number", "0002");

    Map<String, Object> out = x.update().execute(body("scalerLicence", "K123", "returnNumber", "9", "logCount", "4"));

    assertThat(out).containsEntry("saved", true);
    assertThat(x.api.names()).contains("HBS_STORE_PIECE_SCALE_SUMMARY").doesNotContain("HBS_CREATE_AUDIT");
    Call store = only("HBS_STORE_PIECE_SCALE_SUMMARY");
    assertThat(store.arg("i_license_number")).isEqualTo("A999");
    assertThat(store.arg("i_return_number")).isEqualTo("0002");
    assertThat(store.arg("i_log_count")).isEqualTo(BigDecimal.ZERO);
    assertThat(store.arg("i_quant_lic_type_cd")).isEqualTo("S"); // from the licence entered
    assertThat(x.f.edits.calls).isEqualTo(1);
  }

  @Test
  void anAreaBasedReturnsScaleSiteFollowsTheMark() {
    Row v = piece("OIP", "INC");
    v.set("pssmry_type", "AREAEST").set("scl_rtn_category_code", "CR").set("timber_mark", "CRS001")
        .set("area_cut", new BigDecimal("5"));
    x.update().execute(body("areaCut", "6.5"));
    Call store = only("HBS_STORE_PIECE_SCALE_SUMMARY");
    assertThat(store.arg("i_scale_site_id_nmbr")).isEqualTo("S456"); // BT18110
    assertThat(store.arg("i_area_cut")).isEqualTo(new BigDecimal("6.5"));
  }

  @Test
  void aWeightReturnLosesItsCopyToWhenThePopulationChanges() {
    x.f.header(200, WEIGHT, "OIP", false);
    Row v = x.f.version(WEIGHT, 200, 1, "INC", true);
    v.set("population_number", "1002").set("stratum_number", "02")
        .set("client_number_copied_to", "00012345").set("client_locn_code_copied_to", "00");
    x.caller = SummaryFixtures.caller(SUMM_DATA_CTL);
    Map<String, Object> b = keys(200, 1);
    b.put("populationNumber", "1001");
    b.put("stratumNumber", "01");
    x.update().execute(b);
    Call store = only("HBS_STORE_WEIGHT_SCL_SUMMARY");
    assertThat(store.arg("i_population_number")).isEqualTo("1001");
    assertThat(store.arg("i_client_number_copied_to")).isNull();
    assertThat(x.api.names()).contains("HBS_STORE_HBS_SCALE_RETURN");
  }

  @Test
  void aWeightFinalBillNeedsCyclicBilling() {
    x.f.header(200, WEIGHT, "OIP", false);
    x.f.version(WEIGHT, 200, 1, "INC", true);
    x.caller = SummaryFixtures.caller(SUMM_DATA_CTL);
    Map<String, Object> b = keys(200, 1);
    b.put("category", "FB");
    assertProblem(() -> x.update().execute(b), null, EntryMessages.PSYM_REQUIRED);
  }

  @Test
  void aSampleReturnTakesTheLicenceTypeOfItsLicence() {
    x.f.header(300, SAMPLE, "OIP", false);
    x.f.version(SAMPLE, 300, 1, "HLD", true);
    Map<String, Object> b = keys(300, 1);
    b.put("scalerLicence", "R900");
    b.put("loadArrivalNumber", "2");
    x.update().execute(b);
    Call store = only("HBS_STORE_SAMPLE_SCL_SUMMARY");
    assertThat(store.arg("i_license_number")).isEqualTo("R900");
    assertThat(store.arg("i_quant_lic_type_cd")).isEqualTo("R");
    assertThat(store.arg("i_load_arrival_number")).isEqualTo("2");
  }
}
