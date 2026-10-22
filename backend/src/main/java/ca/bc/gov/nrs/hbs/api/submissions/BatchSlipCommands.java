package ca.bc.gov.nrs.hbs.api.submissions;

import ca.bc.gov.nrs.hbs.api.legacyrules.Inputs;
import ca.bc.gov.nrs.hbs.api.legacyrules.LegacyMessages;
import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.RowLock;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowCommand;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException.Problem;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * P280 "Generate a Batch Slip" and P283 "Update a Batch Slip" Save / Cancel /
 * Confirm ({@code dac.srt.detail.P280Action}; validation.xml {@code P280Form};
 * P283.jsp state rules), table HBS_PAPER_BATCH.
 *
 * <p>State rules (legacy P283.jsp showed only the allowed buttons/fields; the
 * server enforces them here):
 * <ul>
 *   <li>Save is not offered once the slip is Cleared (CLR);</li>
 *   <li>Cancel (→ CLR) and Confirm (→ MAT) only while Pending (PND), Confirm
 *       only when Documents Received is not empty;</li>
 *   <li>scale site, scaled from/to, region, sender and date sent to HDQ are
 *       editable only while Pending; afterwards only Document Count and
 *       Instruction/notes;</li>
 *   <li>Confirm requires Documents Received == Document Count
 *       (error.doc-count-received.invalid).</li>
 * </ul>
 * HBS_STORE_HBS_PAPER_BATCH rewrites every column: BATCH_ID, DTL_DOC_BATCH_ID,
 * HBS_RETURN_TYPE_CODE and the ENTRY_ columns always come from the row re-read
 * in the transaction, never from the browser.
 */
public final class BatchSlipCommands {

  static final String CREATE = "HBS_CREATE_HBS_PAPER_BATCH";
  static final String STORE = "HBS_STORE_HBS_PAPER_BATCH";
  static final String PENDING = "PND";
  static final String CLEARED = "CLR";
  static final String MATCHED = "MAT";

  /** ApplicationResources labels of the P280Form fields. */
  static final String L_SCALE_SITE = "Scale Site";
  static final String L_FROM = "Scale from Date";
  static final String L_TO = "Scale to Date";
  static final String L_COUNT = "Document Count";
  static final String L_REGION = "Region";
  static final String L_HDQ = "Date Sent to HDQ";
  static final String L_USER = "User Id";
  static final String L_COMMENT = "Instruction/notes";

  private BatchSlipCommands() {
  }

  /** The values a P280 / P283 form posts, validated as validation.xml P280Form. */
  record SlipFields(String scaleSite, LocalDate from, LocalDate to, Long documentCount, Long orgUnitNo,
                    LocalDate hdqSentDate, String sender, String comment) {}

  /**
   * validation.xml P280Form. {@code editableAll} = Pending (or a new slip):
   * every field; otherwise only Document Count and Instruction/notes.
   */
  static SlipFields validate(Map<String, Object> body, boolean editableAll, BatchSlipReads reads, LocalDate today,
      List<Problem> errors) {
    String site = null;
    LocalDate from = null;
    LocalDate to = null;
    Long region = null;
    LocalDate hdq = null;
    String sender = null;
    if (editableAll) {
      site = Inputs.upper(body, "scaleSiteNo");
      if (site == null) {
        errors.add(new Problem("scaleSiteNo", LegacyMessages.required(L_SCALE_SITE)));
      } else if (!reads.scaleSiteExists(site)) {
        errors.add(new Problem("scaleSiteNo", LegacyMessages.SCALE_SITE_REF));
      }
      from = date(body, "scaledFrom", L_FROM, errors);
      to = date(body, "scaledTo", L_TO, errors);
      // beforeCurrent: the date (00:00) must be before now, i.e. not after today.
      if (from != null && from.isAfter(today)) {
        errors.add(new Problem("scaledFrom", LegacyMessages.beforeCurrent(L_FROM)));
      }
      if (from != null && to != null && from.isAfter(to)) {
        errors.add(new Problem("scaledFrom", LegacyMessages.FROM_BEFORE_TO));
      }
      if (to != null && to.isAfter(today)) {
        errors.add(new Problem("scaledTo", LegacyMessages.beforeCurrent(L_TO)));
      }
      String regionText = Inputs.text(body, "orgUnitNo");
      if (regionText == null) {
        errors.add(new Problem("orgUnitNo", LegacyMessages.required(L_REGION)));
      } else {
        try {
          region = Long.parseLong(regionText);
          if (!reads.orgUnitExists(region)) {
            errors.add(new Problem("orgUnitNo", LegacyMessages.REGION_DISTRICT_REF));
          }
        } catch (NumberFormatException ex) {
          errors.add(new Problem("orgUnitNo", LegacyMessages.REGION_DISTRICT_REF));
        }
      }
      hdq = date(body, "hdqSentDate", L_HDQ, errors);
      // txtSenderId: required, userDomainRequired (DOMAIN and user id), userId.
      sender = Inputs.upper(body, "senderUserid");
      if (sender == null) {
        errors.add(new Problem("senderUserid", LegacyMessages.required(L_USER)));
      } else {
        int slash = sender.indexOf('\\');
        if (slash <= 0 || slash == sender.length() - 1) {
          errors.add(new Problem("senderUserid", LegacyMessages.USER_ID_DOMAIN_REQUIRED));
        }
      }
    }
    Long count = null;
    String countText = Inputs.text(body, "documentCount");
    if (countText == null) {
      errors.add(new Problem("documentCount", LegacyMessages.required(L_COUNT)));
    } else {
      try {
        count = Long.parseLong(countText);
        if (count < 1) {
          errors.add(new Problem("documentCount", LegacyMessages.lessThan(L_COUNT, 1)));
        }
      } catch (NumberFormatException ex) {
        errors.add(new Problem("documentCount", LegacyMessages.notNumeric(L_COUNT)));
      }
    }
    String comment = Inputs.text(body, "paperBatchComment");
    if (comment != null && comment.length() > 255) {
      errors.add(new Problem("paperBatchComment", LegacyMessages.maxLength(L_COMMENT, 255)));
    }
    return new SlipFields(site, from, to, count, region, hdq, sender, comment);
  }

  private static LocalDate date(Map<String, Object> body, String key, String label, List<Problem> errors) {
    String s = Inputs.text(body, key);
    if (s == null) {
      errors.add(new Problem(key, LegacyMessages.required(label)));
      return null;
    }
    try {
      return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
    } catch (DateTimeParseException ex) {
      errors.add(new Problem(key, LegacyMessages.invalidDate(label)));
      return null;
    }
  }

  static void throwIfAny(List<Problem> errors) {
    if (!errors.isEmpty()) {
      throw new WorkflowValidationException(errors);
    }
  }

  /** Base for the three P283 actions: re-read, lock, state check, store. */
  abstract static class Update implements WorkflowCommand {
    final BatchSlipReads reads;
    final TableApi tableApi;
    final Audit audit;

    Update(BatchSlipReads reads, TableApi tableApi, Audit audit) {
      this.reads = reads;
      this.tableApi = tableApi;
      this.audit = audit;
    }

    @Override
    public Capability capability() {
      return Capability.BATCH_SLIP_EDIT;
    }

    Map<String, Object> load(Map<String, Object> body) {
      long batchId = Inputs.requiredLong(body, "batchId", "Batch Slip Id");
      Map<String, Object> row = reads.paperBatch(batchId);
      if (row == null) {
        throw new WorkflowValidationException("batchId", LegacyMessages.NO_RECORDS);
      }
      RowLock.check(Inputs.col(row, "UPDATE_TIMESTAMP"), body.get("updateTimestamp"));
      return row;
    }

    static String status(Map<String, Object> row) {
      return Inputs.colText(row, "PAPER_BATCH_STATUS_CODE");
    }

    void store(ProcArgs args) {
      tableApi.call(STORE, args
          .put("i_update_userid", audit.user())
          .put("i_update_timestamp", audit.now()));
    }

    /**
     * Legacy hid the button instead (P283.jsp), so there is no legacy text;
     * the SPA hides the action the same way and this is the server's backstop.
     */
    static WorkflowValidationException notAllowed(String action, String status) {
      return new WorkflowValidationException("statusCode",
          action + " is not available for a batch slip with status " + status + ".");
    }
  }

  /** P280 Save ({@code performAddBatchSlip}): a new Pending slip. Returns {@code hbsPaperBatchId}. */
  @Component
  public static class Create implements WorkflowCommand {
    private final BatchSlipReads reads;
    private final TableApi tableApi;
    private final Audit audit;

    public Create(BatchSlipReads reads, TableApi tableApi, Audit audit) {
      this.reads = reads;
      this.tableApi = tableApi;
      this.audit = audit;
    }

    @Override
    public String id() {
      return "batchSlip.create";
    }

    @Override
    public String legacy() {
      return "P280 (P280Action.performAddBatchSlip, validation.xml P280Form)";
    }

    @Override
    public Capability capability() {
      return Capability.BATCH_SLIP_EDIT;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      Timestamp now = audit.now();
      List<Problem> errors = new ArrayList<>();
      SlipFields f = validate(body, true, reads, now.toLocalDateTime().toLocalDate(), errors);
      throwIfAny(errors);
      long batchId = tableApi.nextId("HBS_PAPER_BATCH_SEQ");
      tableApi.call(CREATE, ProcArgs.of()
          .put("i_batch_id", batchId)
          .put("i_paper_batch_status_code", PENDING)
          .put("i_scale_site_id_nmbr", f.scaleSite())
          .put("i_dtl_doc_batch_id", null)
          .put("i_hbs_return_type_code", null)
          .put("i_from_scale_date", f.from())
          .put("i_to_scale_date", f.to())
          .put("i_document_count", f.documentCount())
          .put("i_hdq_sent_date", f.hdqSentDate())
          .put("i_sender_userid", f.sender())
          .put("i_paper_batch_comment", f.comment())
          .put("i_org_unit_no", f.orgUnitNo())
          .put("i_entry_userid", audit.user())
          .put("i_entry_timestamp", now)
          .put("i_update_userid", null)
          .put("i_update_timestamp", null));
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("hbsPaperBatchId", batchId);
      result.put("batchId", batchId);
      return result;
    }
  }

  /** P283 Save ({@code performUpdateBatchSlip}, status unchanged). */
  @Component
  public static class Save extends Update {
    public Save(BatchSlipReads reads, TableApi tableApi, Audit audit) {
      super(reads, tableApi, audit);
    }

    @Override
    public String id() {
      return "batchSlip.update";
    }

    @Override
    public String legacy() {
      return "P283 Save (P280Action.performUpdateBatchSlip)";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      Map<String, Object> row = load(body);
      String status = status(row);
      if (CLEARED.equals(status)) {
        throw notAllowed("Save", status);
      }
      boolean pending = PENDING.equals(status);
      List<Problem> errors = new ArrayList<>();
      SlipFields f = validate(body, pending, reads, audit.now().toLocalDateTime().toLocalDate(), errors);
      throwIfAny(errors);
      ProcArgs args = tableApi.argsFromRow(STORE, row)
          .put("i_document_count", f.documentCount())
          .put("i_paper_batch_comment", f.comment());
      if (pending) {
        args.put("i_scale_site_id_nmbr", f.scaleSite())
            .put("i_from_scale_date", f.from())
            .put("i_to_scale_date", f.to())
            .put("i_hdq_sent_date", f.hdqSentDate())
            .put("i_sender_userid", f.sender())
            .put("i_org_unit_no", f.orgUnitNo());
      }
      store(args);
      return Map.of("batchId", Inputs.col(row, "BATCH_ID"));
    }
  }

  /** P283 Cancel: Pending → CLR. */
  @Component
  public static class Cancel extends Update {
    public Cancel(BatchSlipReads reads, TableApi tableApi, Audit audit) {
      super(reads, tableApi, audit);
    }

    @Override
    public String id() {
      return "batchSlip.cancel";
    }

    @Override
    public String legacy() {
      return "P283 Cancel (P280Action, ACTION_CANCEL)";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      Map<String, Object> row = load(body);
      String status = status(row);
      if (!PENDING.equals(status)) {
        throw notAllowed("Cancel", status);
      }
      store(tableApi.argsFromRow(STORE, row).put("i_paper_batch_status_code", CLEARED));
      return Map.of("batchId", Inputs.col(row, "BATCH_ID"), "statusCode", CLEARED);
    }
  }

  /** P283 Confirm: Pending → MAT when Documents Received == Document Count. */
  @Component
  public static class Confirm extends Update {
    public Confirm(BatchSlipReads reads, TableApi tableApi, Audit audit) {
      super(reads, tableApi, audit);
    }

    @Override
    public String id() {
      return "batchSlip.confirm";
    }

    @Override
    public String legacy() {
      return "P283 Confirm (P280Action, ACTION_CONFIRM)";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      Map<String, Object> row = load(body);
      String status = status(row);
      long batchId = ((Number) Inputs.col(row, "BATCH_ID")).longValue();
      long received = reads.documentsReceived(batchId);
      if (!PENDING.equals(status) || received == 0) {
        throw notAllowed("Confirm", status);
      }
      Object count = Inputs.col(row, "DOCUMENT_COUNT");
      if (!(count instanceof Number n) || n.longValue() != received) {
        throw new WorkflowValidationException("documentCount", LegacyMessages.DOC_COUNT_RECEIVED);
      }
      store(tableApi.argsFromRow(STORE, row).put("i_paper_batch_status_code", MATCHED));
      return Map.of("batchId", batchId, "statusCode", MATCHED);
    }
  }
}
