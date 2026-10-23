package ca.bc.gov.nrs.hbs.api.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.hbs.api.legacyrules.FixedAudit;
import ca.bc.gov.nrs.hbs.api.legacyrules.LegacyMessages;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi;
import ca.bc.gov.nrs.hbs.api.workflow.StaleUpdateException;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Force dates, final-bill generation and non-renewable tenures (P811–P836). */
class BillingWorkflowsTest {

  private static final Timestamp NOW = Timestamp.valueOf("2026-10-21 10:15:00");

  private final BillingReads reads = ca.bc.gov.nrs.hbs.api.legacyrules.NullMocks.of(BillingReads.class);
  private final RecordingTableApi api = new RecordingTableApi();
  private final FixedAudit audit = new FixedAudit(NOW.toLocalDateTime());

  private static Map<String, Object> markProfile(long id, String freq) {
    Map<String, Object> row = new HashMap<>();
    row.put("MHPROF_ID", BigDecimal.valueOf(id));
    row.put("CLIENT_NUMBER", "00012345");
    row.put("CLIENT_LOCN_CODE", "00");
    row.put("HBS_RETURN_TYPE_CODE", "P");
    row.put("HBS_FREQUENCY_TYPE_CODE", freq);
    row.put("FORCED_SUMMARIZATION_DATE", null);
    row.put("EFFECTIVE_DATE", Timestamp.valueOf("2026-01-01 00:00:00"));
    row.put("EXPIRY_DATE", Timestamp.valueOf("2026-12-31 00:00:00"));
    row.put("ENTRY_USERID", "IDIR\\OLD");
    row.put("ENTRY_TIMESTAMP", Timestamp.valueOf("2026-01-01 09:00:00"));
    row.put("UPDATE_USERID", "IDIR\\OLD");
    row.put("UPDATE_TIMESTAMP", Timestamp.valueOf("2026-02-01 09:00:00"));
    return row;
  }

  private static Map<String, Object> body(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
    return m;
  }

  private static void assertProblem(Runnable r, String message) {
    assertThatThrownBy(r::run).isInstanceOf(WorkflowValidationException.class)
        .satisfies(e -> assertThat(((WorkflowValidationException) e).problems())
            .extracting(WorkflowValidationException.Problem::message).contains(message));
  }

  // ---- P811 / P813 ---------------------------------------------------------------------------

  @Test
  void forceOneProfile_storesForcedDateAndKeepsOtherColumns() {
    when(reads.markHolderProfile(7)).thenReturn(markProfile(7, "MTH"));
    var cmd = new ForceSummarizationDateCommand(reads, new MarkHolderProfileForcer(api), audit);
    cmd.execute(body("mhprofId", "7", "forceDate", "2026-11-01", "updateTimestamp", "2026-02-01T09:00:00"));
    var call = api.callsTo("HBS_STORE_HBS_MARK_PROFILE").get(0);
    assertThat(call.arg("i_forced_summarization_date")).isEqualTo(Timestamp.valueOf("2026-11-01 00:00:00"));
    assertThat(call.arg("i_client_number")).isEqualTo("00012345");
    assertThat(call.arg("i_entry_userid")).isEqualTo("IDIR\\OLD");
    assertThat(call.arg("i_update_userid")).isEqualTo(FixedAudit.USER);
    assertThat(call.arg("i_update_timestamp")).isEqualTo(NOW);
  }

  @Test
  void forceOneProfile_staleTimestamp_isRefused() {
    when(reads.markHolderProfile(7)).thenReturn(markProfile(7, "MTH"));
    var cmd = new ForceSummarizationDateCommand(reads, new MarkHolderProfileForcer(api), audit);
    assertThatThrownBy(() -> cmd.execute(body("mhprofId", "7", "forceDate", "2026-11-01",
        "updateTimestamp", "2026-03-01T09:00:00"))).isInstanceOf(StaleUpdateException.class);
    assertThat(api.calls).isEmpty();
  }

  @Test
  void forceOneProfile_windowAndRangeChecks() {
    when(reads.markHolderProfile(7)).thenReturn(markProfile(7, "BWK"));
    var cmd = new ForceSummarizationDateCommand(reads, new MarkHolderProfileForcer(api), audit);
    assertProblem(() -> cmd.execute(body("mhprofId", "7", "forceDate", "2026-11-10")),
        LegacyMessages.FORCE_DATE_BIWEEKLY);
    Map<String, Object> expiring = markProfile(8, "MTH");
    expiring.put("EXPIRY_DATE", Timestamp.valueOf("2026-10-31 00:00:00"));
    when(reads.markHolderProfile(8)).thenReturn(expiring);
    assertProblem(() -> cmd.execute(body("mhprofId", "8", "forceDate", "2026-11-01")), LegacyMessages.NO_RECORDS);
    assertThat(api.calls).isEmpty();
  }

  @Test
  void forceAll_updatesEveryCustomProfilePlusTheDefault() {
    when(reads.markHolderProfilesToForce(eq("W"), eq("MTH"), isNull(), isNull(), any()))
        .thenReturn(List.of(markProfile(10, "MTH"), markProfile(11, "MTH")));
    when(reads.markHolderProfile(2)).thenReturn(markProfile(2, "MTH"));
    var cmd = new ForceSummarizationCommand(reads, new MarkHolderProfileForcer(api), audit);
    Map<String, Object> out = cmd.execute(body("returnType", "W", "frequency", "MTH", "selected", "no",
        "forceDate", "2026-11-01"));
    assertThat(out.get("count")).isEqualTo(3);
    assertThat(api.callsTo("HBS_STORE_HBS_MARK_PROFILE")).extracting(c -> c.arg("i_mhprof_id"))
        .containsExactly(BigDecimal.valueOf(10), BigDecimal.valueOf(11), BigDecimal.valueOf(2));
  }

  @Test
  void forceForClient_padsTheClientAndSkipsTheDefault() {
    when(reads.markHolderProfilesToForce("P", "DLY", "00000123", "01", java.time.LocalDate.of(2026, 10, 22)))
        .thenReturn(List.of(markProfile(12, "DLY")));
    var cmd = new ForceSummarizationCommand(reads, new MarkHolderProfileForcer(api), audit);
    cmd.execute(body("returnType", "P", "frequency", "DLY", "selected", "yes", "clientNumber", "123",
        "clientLocnCode", "1", "forceDate", "2026-10-22"));
    assertThat(api.calls).hasSize(1);
  }

  @Test
  void forceAll_noProfiles_isNoRecords() {
    when(reads.markHolderProfilesToForce(anyString(), anyString(), any(), any(), any())).thenReturn(List.of());
    var cmd = new ForceSummarizationCommand(reads, new MarkHolderProfileForcer(api), audit);
    assertProblem(() -> cmd.execute(body("returnType", "P", "frequency", "MTH", "selected", "no",
        "forceDate", "2026-11-01")), LegacyMessages.NO_RECORDS);
    assertProblem(() -> cmd.execute(body("returnType", "P", "frequency", "MTH", "selected", "no",
        "forceDate", "2027-02-01")), LegacyMessages.FORCE_DATE_MONTHLY);
    assertThat(api.calls).isEmpty();
  }

  // ---- P823 ----------------------------------------------------------------------------------

  private static Map<String, Object> popProfile(long id) {
    Map<String, Object> row = new HashMap<>();
    row.put("POPPROF_ID", BigDecimal.valueOf(id));
    row.put("POPULATION_NUMBER", id == 1 ? null : "1234");
    row.put("SAMPLING_YEAR", id == 1 ? null : BigDecimal.valueOf(2026));
    row.put("HBS_FREQUENCY_TYPE_CODE", "MTH");
    row.put("NEXT_RATIO_STMT_DATE", Timestamp.valueOf("2026-11-30 23:59:59"));
    row.put("LAST_RATIO_STMT_DATE", Timestamp.valueOf("2026-10-01 23:59:59"));
    row.put("START_RATIO_STMT_DATE", Timestamp.valueOf("2026-01-01 00:00:00"));
    row.put("EFFECTIVE_DATE", Timestamp.valueOf(id == 1 ? "2000-01-01 00:00:00" : "2026-10-25 00:00:00"));
    row.put("EXPIRY_DATE", Timestamp.valueOf("2026-12-31 00:00:00"));
    row.put("ENTRY_USERID", "IDIR\\OLD");
    row.put("ENTRY_TIMESTAMP", Timestamp.valueOf("2026-01-01 09:00:00"));
    row.put("UPDATE_USERID", null);
    row.put("UPDATE_TIMESTAMP", null);
    return row;
  }

  @Test
  void forceRatio_setsNextDateAt235959() {
    when(reads.populationProfile(1)).thenReturn(popProfile(1));
    var cmd = new ForceRatioComputationDateCommand(reads, api, audit);
    cmd.execute(body("popprofId", "1", "forceDate", "2026-11-05"));
    var call = api.callsTo("HBS_STORE_POPULATION_PROFILE").get(0);
    assertThat(call.arg("i_next_ratio_stmt_date")).isEqualTo(Timestamp.valueOf("2026-11-05 23:59:59"));
    assertThat(call.arg("i_last_ratio_stmt_date")).isEqualTo(Timestamp.valueOf("2026-10-01 23:59:59"));
    assertThat(call.arg("i_update_userid")).isEqualTo(FixedAudit.USER);
  }

  @Test
  void forceRatio_customProfileMustBeInEffect_andPeriodChecks() {
    when(reads.populationProfile(5)).thenReturn(popProfile(5));
    var cmd = new ForceRatioComputationDateCommand(reads, api, audit);
    assertProblem(() -> cmd.execute(body("popprofId", "5", "forceDate", "2026-10-22")),
        LegacyMessages.PROFILE_NOT_FOUND);
    assertProblem(() -> cmd.execute(body("popprofId", "5", "forceDate", "2026-12-01")),
        LegacyMessages.FORCE_DATE_MONTHLY);
    when(reads.populationProfile(1)).thenReturn(popProfile(1));
    assertProblem(() -> cmd.execute(body("popprofId", "1", "forceDate", "2026-12-01")),
        LegacyMessages.FORCE_AFTER_PERIOD_END);
    assertThat(api.calls).isEmpty();
    cmd.execute(body("popprofId", "5", "forceDate", "2026-10-26"));
    assertThat(api.calls).hasSize(1);
  }

  // ---- P829 / P830 ---------------------------------------------------------------------------

  @Test
  void generateFinalBill_callsHbs3r830WithOutParameter() {
    when(reads.finalBillStatus(2025, "1234", null)).thenReturn(Map.of("ELIGIBLE_PSYM_FLAG", "*"));
    api.answer("HBS3R830", c -> Map.of("fb_returnvalue", BigDecimal.ZERO));
    Map<String, Object> out = new GenerateFinalBillReleaseCommand(reads, api, audit)
        .execute(body("samplingYear", "2025", "population", "1234"));
    var call = api.callsTo("HBS3R830").get(0);
    assertThat(call.arg("fb_sampling_year")).isEqualTo("2025");
    assertThat(call.arg("fb_population_nmbr")).isEqualTo("1234");
    assertThat(call.arg("fb_stratum_nmbr")).isNull();
    assertThat(call.arg("fb_hld_rel")).isEqualTo("RDY");
    assertThat(call.arg("fb_userid")).isEqualTo(FixedAudit.USER);
    assertThat(out.get("message")).isEqualTo(LegacyMessages.PROCESSED);
  }

  @Test
  void generateFinalBill_notEligibleOrFailed_isNotProcessed() {
    var hold = new GenerateFinalBillHoldCommand(reads, api, audit);
    when(reads.finalBillStatus(2025, "1234", "01")).thenReturn(Map.of("ELIGIBLE_PSYM_FLAG", ""));
    assertProblem(() -> hold.execute(body("samplingYear", "2025", "population", "1234", "stratum", "01")),
        LegacyMessages.NOT_PROCESSED);
    assertThat(api.calls).isEmpty();
    when(reads.finalBillStatus(2025, "1234", "02")).thenReturn(Map.of("ELIGIBLE_PSYM_FLAG", "*"));
    api.answer("HBS3R830", c -> Map.of("fb_returnvalue", BigDecimal.valueOf(-1)));
    assertProblem(() -> hold.execute(body("samplingYear", "2025", "population", "1234", "stratum", "02")),
        LegacyMessages.NOT_PROCESSED);
    assertThat(api.callsTo("HBS3R830").get(0).arg("fb_hld_rel")).isEqualTo("HLD");
  }

  // ---- P835 / P836 ---------------------------------------------------------------------------

  private void validTenure(String pop, long year, String mark) {
    when(reads.populationExists(pop)).thenReturn(true);
    when(reads.population(pop, year)).thenReturn(Map.of("RATIO_TYPE_CODE", "S"));
    when(reads.timberMark(mark)).thenReturn(Map.of("FILE_TYPE_CODE", "A07", "QUOTA_TYPE_CODE", "C"));
  }

  @Test
  void createTenure_happyPath() {
    validTenure("1234", 2025, "AB123");
    new FinalBillTenureCreateCommand(new FinalBillTenureRules(reads), api, audit)
        .execute(body("populationNumber", "1234", "samplingYear", "2025", "timberMark", "ab123"));
    var call = api.callsTo("HBS_CREATE_FINAL_BILL_MARK").get(0);
    assertThat(call.arg("i_timber_mark")).isEqualTo("AB123");
    assertThat(call.arg("i_sampling_year")).isEqualTo(BigDecimal.valueOf(2025));
    assertThat(call.arg("i_entry_userid")).isEqualTo(FixedAudit.USER);
  }

  @Test
  void createTenure_legacyValidations() {
    var cmd = new FinalBillTenureCreateCommand(new FinalBillTenureRules(reads), api, audit);
    assertProblem(() -> cmd.execute(body("samplingYear", "2025", "timberMark", "AB123")),
        LegacyMessages.POPULATION_REQUIRED);
    assertProblem(() -> cmd.execute(body("populationNumber", "12A4", "samplingYear", "2025", "timberMark", "X")),
        LegacyMessages.POPULATION_DIGITS);
    assertProblem(() -> cmd.execute(body("populationNumber", "9999", "samplingYear", "1999", "timberMark", "X")),
        LegacyMessages.FB_POPULATION_INVALID);

    validTenure("1234", 2025, "AB123");
    assertProblem(() -> cmd.execute(body("populationNumber", "1234", "samplingYear", "1999", "timberMark", "AB123")),
        LegacyMessages.FB_YEAR_INVALID);
    when(reads.population("1234", 2025)).thenReturn(Map.of("RATIO_TYPE_CODE", "D"));
    assertProblem(() -> cmd.execute(body("populationNumber", "1234", "samplingYear", "2025", "timberMark", "AB123")),
        LegacyMessages.FB_DEFAULT_RATIO);
    when(reads.population("1234", 2025)).thenReturn(Map.of("RATIO_TYPE_CODE", "S"));
    when(reads.timberMark("REN01")).thenReturn(Map.of("FILE_TYPE_CODE", "A01 ", "QUOTA_TYPE_CODE", "A"));
    assertProblem(() -> cmd.execute(body("populationNumber", "1234", "samplingYear", "2025", "timberMark", "REN01")),
        LegacyMessages.TIMBER_MARK_RENEWABLE);
    assertProblem(() -> cmd.execute(body("populationNumber", "1234", "samplingYear", "2025", "timberMark", "NOPE")),
        LegacyMessages.TIMBER_MARK_INVALID);
    when(reads.cyclicBillingExists("1234", 2025, "AB123")).thenReturn(true);
    assertProblem(() -> cmd.execute(body("populationNumber", "1234", "samplingYear", "2025", "timberMark", "AB123")),
        LegacyMessages.FB_INSERT_ERROR);
    when(reads.cyclicBillingExists("1234", 2025, "AB123")).thenReturn(false);
    when(reads.finalBillMark("1234", 2025, "AB123")).thenReturn(Map.of("TIMBER_MARK", "AB123"));
    assertProblem(() -> cmd.execute(body("populationNumber", "1234", "samplingYear", "2025", "timberMark", "AB123")),
        LegacyMessages.FB_ALREADY_ELECTED);
    assertThat(api.calls).isEmpty();
  }

  private static Map<String, Object> election() {
    Map<String, Object> row = new HashMap<>();
    row.put("ENTRY_USERID", "IDIR\\FIRST");
    row.put("ENTRY_TIMESTAMP", Timestamp.valueOf("2025-05-05 08:00:00"));
    row.put("UPDATE_TIMESTAMP", Timestamp.valueOf("2025-05-05 08:00:00"));
    return row;
  }

  @Test
  void updateTenure_isRemovePlusCreateKeepingTheEntryColumns() {
    when(reads.finalBillMark("1234", 2025, "AB123")).thenReturn(election());
    validTenure("1234", 2025, "CD456");
    new FinalBillTenureUpdateCommand(new FinalBillTenureRules(reads), reads, api, audit).execute(body(
        "opu", "1234", "osy", "2025", "otm", "AB123",
        "populationNumber", "1234", "samplingYear", "2025", "timberMark", "CD456",
        "updateTimestamp", "2025-05-05T08:00:00"));
    assertThat(api.names()).containsExactly("HBS_REMOVE_FINAL_BILL_MARK", "HBS_CREATE_FINAL_BILL_MARK");
    var create = api.callsTo("HBS_CREATE_FINAL_BILL_MARK").get(0);
    assertThat(create.arg("i_timber_mark")).isEqualTo("CD456");
    assertThat(create.arg("i_entry_userid")).isEqualTo("IDIR\\FIRST");
    assertThat(create.arg("i_update_userid")).isEqualTo(FixedAudit.USER);
  }

  @Test
  void updateTenure_cyclicBillingBlocksTheChange() {
    when(reads.finalBillMark("1234", 2025, "AB123")).thenReturn(election());
    validTenure("1234", 2025, "CD456");
    when(reads.cyclicBillingExists("1234", 2025, "AB123")).thenReturn(true);
    var cmd = new FinalBillTenureUpdateCommand(new FinalBillTenureRules(reads), reads, api, audit);
    assertProblem(() -> cmd.execute(body("opu", "1234", "osy", "2025", "otm", "AB123",
        "populationNumber", "1234", "samplingYear", "2025", "timberMark", "CD456")), LegacyMessages.FB_DELETE_ERROR);
    // unchanged keys with cyclic billing: both messages (BT 2810)
    assertThatThrownBy(() -> cmd.execute(body("opu", "1234", "osy", "2025", "otm", "AB123",
        "populationNumber", "1234", "samplingYear", "2025", "timberMark", "AB123")))
        .satisfies(e -> assertThat(((WorkflowValidationException) e).problems()).hasSize(2));
    assertThat(api.calls).isEmpty();
  }

  @Test
  void removeTenure_blockedByCyclicBilling() {
    when(reads.finalBillMark("1234", 2025, "AB123")).thenReturn(election());
    var cmd = new FinalBillTenureRemoveCommand(reads, api);
    when(reads.cyclicBillingExists("1234", 2025, "AB123")).thenReturn(true);
    assertProblem(() -> cmd.execute(body("populationNumber", "1234", "samplingYear", "2025", "timberMark", "AB123")),
        LegacyMessages.FB_DELETE_ERROR);
    when(reads.cyclicBillingExists("1234", 2025, "AB123")).thenReturn(false);
    cmd.execute(body("populationNumber", "1234", "samplingYear", "2025", "timberMark", "AB123"));
    assertThat(api.names()).containsExactly("HBS_REMOVE_FINAL_BILL_MARK");
  }

  @Test
  void removeTenure_unknownRecord() {
    when(reads.finalBillMark(anyString(), anyLong(), anyString())).thenReturn(null);
    assertProblem(() -> new FinalBillTenureRemoveCommand(reads, api)
            .execute(body("populationNumber", "1234", "samplingYear", "2025", "timberMark", "AB123")),
        LegacyMessages.NO_RECORDS);
  }

  @Test
  void now_isFixed() {
    assertThat(audit.now().toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 10, 21, 10, 15));
  }
}
