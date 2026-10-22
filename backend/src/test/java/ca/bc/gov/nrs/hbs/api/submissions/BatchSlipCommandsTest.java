package ca.bc.gov.nrs.hbs.api.submissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.hbs.api.legacyrules.FixedAudit;
import ca.bc.gov.nrs.hbs.api.legacyrules.LegacyMessages;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi;
import ca.bc.gov.nrs.hbs.api.workflow.StaleUpdateException;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** P280 / P283 batch slips. */
class BatchSlipCommandsTest {

  private static final Timestamp NOW = Timestamp.valueOf("2026-10-21 10:15:00");

  private final BatchSlipReads reads = ca.bc.gov.nrs.hbs.api.legacyrules.NullMocks.of(BatchSlipReads.class);
  private final RecordingTableApi api = new RecordingTableApi();
  private final FixedAudit audit = new FixedAudit(NOW.toLocalDateTime());

  private static Map<String, Object> body(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
    return m;
  }

  private static Map<String, Object> form() {
    return body("scaleSiteNo", "a12", "scaledFrom", "2026-10-01", "scaledTo", "2026-10-15", "orgUnitNo", "1",
        "senderUserid", "idir\\jsmith", "hdqSentDate", "2026-10-16", "documentCount", "12",
        "paperBatchComment", "box 3");
  }

  private static List<String> messages(Runnable r) {
    try {
      r.run();
    } catch (WorkflowValidationException e) {
      return e.problems().stream().map(WorkflowValidationException.Problem::message).toList();
    }
    throw new AssertionError("no validation error");
  }

  private static Map<String, Object> slip(String status) {
    Map<String, Object> row = new HashMap<>();
    row.put("BATCH_ID", BigDecimal.valueOf(77));
    row.put("PAPER_BATCH_STATUS_CODE", status);
    row.put("SCALE_SITE_ID_NMBR", "A12");
    row.put("DTL_DOC_BATCH_ID", BigDecimal.valueOf(5001));
    row.put("HBS_RETURN_TYPE_CODE", "P");
    row.put("FROM_SCALE_DATE", Timestamp.valueOf("2026-10-01 00:00:00"));
    row.put("TO_SCALE_DATE", Timestamp.valueOf("2026-10-15 00:00:00"));
    row.put("DOCUMENT_COUNT", BigDecimal.valueOf(12));
    row.put("HDQ_SENT_DATE", Timestamp.valueOf("2026-10-16 00:00:00"));
    row.put("SENDER_USERID", "IDIR\\JSMITH");
    row.put("PAPER_BATCH_COMMENT", "box 3");
    row.put("ORG_UNIT_NO", BigDecimal.ONE);
    row.put("ENTRY_USERID", "IDIR\\FIRST");
    row.put("ENTRY_TIMESTAMP", Timestamp.valueOf("2026-10-16 08:30:00"));
    row.put("UPDATE_USERID", null);
    row.put("UPDATE_TIMESTAMP", null);
    return row;
  }

  @Test
  void create_pendingSlipWithSequenceKey() {
    when(reads.scaleSiteExists("A12")).thenReturn(true);
    when(reads.orgUnitExists(1)).thenReturn(true);
    Map<String, Object> out = new BatchSlipCommands.Create(reads, api, audit).execute(form());
    var call = api.callsTo("HBS_CREATE_HBS_PAPER_BATCH").get(0);
    assertThat(out.get("hbsPaperBatchId")).isEqualTo(1001L);
    assertThat(call.arg("i_batch_id")).isEqualTo(BigDecimal.valueOf(1001));
    assertThat(call.arg("i_paper_batch_status_code")).isEqualTo("PND");
    assertThat(call.arg("i_scale_site_id_nmbr")).isEqualTo("A12");
    assertThat(call.arg("i_sender_userid")).isEqualTo("IDIR\\JSMITH");
    assertThat(call.arg("i_entry_userid")).isEqualTo(FixedAudit.USER);
    assertThat(call.arg("i_update_timestamp")).isNull();
  }

  @Test
  void create_p280FormValidations() {
    var cmd = new BatchSlipCommands.Create(reads, api, audit);
    Map<String, Object> bad = form();
    bad.put("scaledFrom", "2026-10-30");
    bad.put("documentCount", "0");
    bad.put("senderUserid", "JSMITH");
    bad.put("paperBatchComment", "x".repeat(256));
    assertThat(messages(() -> cmd.execute(bad))).containsExactly(
        LegacyMessages.SCALE_SITE_REF,
        "Scale from Date must be before the current date.",
        LegacyMessages.FROM_BEFORE_TO,
        LegacyMessages.REGION_DISTRICT_REF,
        LegacyMessages.USER_ID_DOMAIN_REQUIRED,
        "Document Count cannot be less than 1.",
        "Instruction/notes cannot be greater than 255 characters.");
    assertThat(messages(() -> cmd.execute(body()))).contains("Scale Site is required.", "Scale from Date is required.",
        "Region is required.", "Date Sent to HDQ is required.", "User Id is required.", "Document Count is required.");
    assertThat(api.calls).isEmpty();
  }

  @Test
  void update_matchedSlipChangesOnlyCountAndNotes() {
    when(reads.paperBatch(77)).thenReturn(slip("MAT"));
    Map<String, Object> b = form();
    b.put("batchId", "77");
    b.put("scaleSiteNo", "ZZZ");
    b.put("documentCount", "13");
    b.put("paperBatchComment", "recount");
    new BatchSlipCommands.Save(reads, api, audit).execute(b);
    var call = api.callsTo("HBS_STORE_HBS_PAPER_BATCH").get(0);
    assertThat(call.arg("i_scale_site_id_nmbr")).isEqualTo("A12");
    assertThat(call.arg("i_document_count")).isEqualTo(BigDecimal.valueOf(13));
    assertThat(call.arg("i_paper_batch_comment")).isEqualTo("recount");
    assertThat(call.arg("i_paper_batch_status_code")).isEqualTo("MAT");
    assertThat(call.arg("i_dtl_doc_batch_id")).isEqualTo(BigDecimal.valueOf(5001));
    assertThat(call.arg("i_entry_timestamp")).isEqualTo(Timestamp.valueOf("2026-10-16 08:30:00"));
    assertThat(call.arg("i_update_userid")).isEqualTo(FixedAudit.USER);
  }

  @Test
  void update_clearedSlipIsRefused_andLockChecked() {
    when(reads.paperBatch(77)).thenReturn(slip("CLR"));
    Map<String, Object> b = form();
    b.put("batchId", "77");
    assertThatThrownBy(() -> new BatchSlipCommands.Save(reads, api, audit).execute(b))
        .isInstanceOf(WorkflowValidationException.class);
    Map<String, Object> pending = slip("PND");
    pending.put("UPDATE_TIMESTAMP", Timestamp.valueOf("2026-10-17 08:30:00"));
    when(reads.paperBatch(78)).thenReturn(pending);
    b.put("batchId", "78");
    b.put("updateTimestamp", "2026-10-17T08:00:00");
    assertThatThrownBy(() -> new BatchSlipCommands.Save(reads, api, audit).execute(b))
        .isInstanceOf(StaleUpdateException.class);
    assertThat(api.calls).isEmpty();
  }

  @Test
  void cancel_onlyWhilePending() {
    when(reads.paperBatch(77)).thenReturn(slip("PND"));
    new BatchSlipCommands.Cancel(reads, api, audit).execute(body("batchId", "77"));
    assertThat(api.calls.get(0).arg("i_paper_batch_status_code")).isEqualTo("CLR");
    when(reads.paperBatch(77)).thenReturn(slip("MAT"));
    assertThatThrownBy(() -> new BatchSlipCommands.Cancel(reads, api, audit).execute(body("batchId", "77")))
        .isInstanceOf(WorkflowValidationException.class);
  }

  @Test
  void confirm_requiresReceivedEqualsCount() {
    when(reads.paperBatch(77)).thenReturn(slip("PND"));
    when(reads.documentsReceived(anyLong())).thenReturn(11L);
    var cmd = new BatchSlipCommands.Confirm(reads, api, audit);
    assertThat(messages(() -> cmd.execute(body("batchId", "77")))).containsExactly(LegacyMessages.DOC_COUNT_RECEIVED);
    when(reads.documentsReceived(anyLong())).thenReturn(0L);
    assertThatThrownBy(() -> cmd.execute(body("batchId", "77"))).isInstanceOf(WorkflowValidationException.class);
    assertThat(api.calls).isEmpty();
    when(reads.documentsReceived(anyLong())).thenReturn(12L);
    cmd.execute(body("batchId", "77"));
    assertThat(api.calls.get(0).arg("i_paper_batch_status_code")).isEqualTo("MAT");
  }

  @Test
  void unknownSlip_isNoRecords() {
    when(reads.scaleSiteExists(anyString())).thenReturn(true);
    assertThat(messages(() -> new BatchSlipCommands.Cancel(reads, api, audit).execute(body("batchId", "1"))))
        .containsExactly(LegacyMessages.NO_RECORDS);
  }
}
