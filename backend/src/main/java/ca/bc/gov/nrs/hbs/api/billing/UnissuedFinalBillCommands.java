package ca.bc.gov.nrs.hbs.api.billing;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryCaller;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnAccess;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnCommand;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRepository;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnWorkflows;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnWriter;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.StaleUpdateException;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * P832 "Hold Selected" / "Release Selected" on the List of Unissued Final Bills
 * ({@code $V/ivs/config/P831Action} ACTION_HOLD / ACTION_RELEASE →
 * {@code ClientHelper.updateSummaryWeightScaleReturnHold / Release}).
 *
 * <p>Legacy walked the checked rows of the list page and, for each, loaded the
 * return's active version and held it when the listed status was RDY or ERR,
 * or released it when the listed status was HLD; other rows were skipped and
 * every failure was swallowed (one autocommitted transaction per return). Here
 * each selected row runs in its own savepoint and is reported
 * PROCESSED / NOTPROCESSED / ERROR ({@code DetailScaleDocumentProcessStatus}
 * vocabulary, as {@code detailReturns.bulk}), with the reason.
 *
 * <p>Hold uses {@link SummaryReturnWorkflows#hold} (BT16641: the ready version
 * becomes SPR and a new held version is created), the hold every summary-return
 * screen uses. Legacy P831 called the older {@code WorkflowStateHelper.hold},
 * which after BT16641 only made the ready version SPR/inactive and created no
 * held version, leaving the return without an active version; that defect is
 * not reproduced. ERR is a ready state ({@code ScaleReturnReadyState}), so RDY
 * and ERR versions of an OIP / RWC return are held. Under any other return
 * state {@code holdAllowed} is false: legacy only logged that assertion
 * ({@code LOG.assertLog}) and superseded the version anyway; here the hold is
 * refused, as on the summary-return screens, and the row reported ERROR with
 * the state message.
 * Release is {@code SummaryWorkflowManager.release} = {@link SummaryReturnWorkflows#release}.
 *
 * <p>Body: {@code {rows: [{documentControlNumber, version, updateTimestamp}]}};
 * answer: {@code {results: [{documentControlNumber, version, label, status, message}]}}.
 * Only weight-scale final bills (category FB) — the rows P832 lists — are acted on.
 */
public final class UnissuedFinalBillCommands {

  static final String PROCESSED = "PROCESSED";
  static final String NOT_PROCESSED = "NOTPROCESSED";
  static final String ERROR = "ERROR";

  private UnissuedFinalBillCommands() {
  }

  /** The beans both commands use. */
  @Component
  public static class Deps {
    final SummaryReturnRepository repo;
    final SummaryReturnWriter writer;
    final SummaryReturnAccess access;
    final Audit audit;
    final SummaryReturnWorkflows workflows;
    final ObjectProvider<PlatformTransactionManager> tx;

    public Deps(SummaryReturnRepository repo, SummaryReturnWriter writer, SummaryReturnAccess access, Audit audit,
        SummaryReturnWorkflows workflows, ObjectProvider<PlatformTransactionManager> tx) {
      this.repo = repo;
      this.writer = writer;
      this.access = access;
      this.audit = audit;
      this.workflows = workflows;
      this.tx = tx;
    }
  }

  /** Shared loop: one savepoint and one result per selected row. */
  @Slf4j
  abstract static class Bulk extends SummaryReturnCommand {

    protected final SummaryReturnWorkflows workflows;
    private final RowTransactions rows;

    Bulk(Deps d) {
      super(d.repo, d.writer, d.access, d.audit);
      this.workflows = d.workflows;
      this.rows = new RowTransactions(d.tx);
    }

    @Override
    public Capability capability() {
      return Capability.FINAL_BILL_ADMIN;
    }

    /** The listed statuses the legacy loop acted on. */
    abstract Set<String> qualifyingStates();

    /** The workflow step; returns the result message. */
    abstract String apply(SummaryReturn ret, String user);

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> body) {
      Object raw = body.get("rows");
      if (!(raw instanceof List<?> selected) || selected.isEmpty()) {
        throw new WorkflowValidationException("rows", "Select at least one summary return.");
      }
      SummaryCaller caller = caller();
      List<Map<String, Object>> results = new ArrayList<>();
      for (Object o : selected) {
        Map<String, Object> row = o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentControlNumber", row.get("documentControlNumber"));
        result.put("version", row.get("version"));
        result.put("label", "Document " + row.get("documentControlNumber"));
        try {
          String[] outcome = rows.run(() -> processRow(row, caller));
          result.put("status", outcome[0]);
          if (outcome[1] != null) result.put("message", outcome[1]);
        } catch (StaleUpdateException ex) {
          result.put("status", ERROR);
          result.put("message", ex.getMessage());
        } catch (RuntimeException ex) {
          log.info("{} on {} failed: {}", id(), row, ex.getMessage());
          result.put("status", ERROR);
          result.put("message", ex.getMessage());
        }
        results.add(result);
      }
      return Map.of("results", results);
    }

    String[] processRow(Map<String, Object> row, SummaryCaller caller) {
      SummaryReturn ret = loadForAction(row, caller);
      if (ret.type() != SummaryReturnType.WEIGHT
          || !SummaryReturnRules.CATEGORY_FINAL_BILL.equals(ret.version().str("scl_rtn_category_code"))) {
        return new String[] {NOT_PROCESSED, "Not a weight scale final bill."};
      }
      if (!qualifyingStates().contains(ret.versionState())) {
        return new String[] {NOT_PROCESSED, "Status " + ret.versionState() + " does not qualify."};
      }
      return new String[] {PROCESSED, apply(ret, audit.user())};
    }
  }

  /** P832 Hold Selected: RDY / ERR rows. */
  @Component
  public static class Hold extends Bulk {
    public Hold(Deps d) {
      super(d);
    }

    @Override
    public String id() {
      return "billing.unissuedFinalBills.hold";
    }

    @Override
    public String legacy() {
      return "P832 Hold Selected — P831Action ACTION_HOLD / ClientHelper.updateSummaryWeightScaleReturnHold";
    }

    @Override
    Set<String> qualifyingStates() {
      return Set.of(ScaleReturnState.READY, ScaleReturnState.ERROR);
    }

    @Override
    String apply(SummaryReturn ret, String user) {
      SummaryReturn held = workflows.hold(ret, user);
      return "Held (version " + held.versionNo() + ").";
    }
  }

  /** P832 Release Selected: HLD rows. */
  @Component
  public static class Release extends Bulk {
    public Release(Deps d) {
      super(d);
    }

    @Override
    public String id() {
      return "billing.unissuedFinalBills.release";
    }

    @Override
    public String legacy() {
      return "P832 Release Selected — P831Action ACTION_RELEASE / ClientHelper.updateSummaryWeightScaleReturnRelease";
    }

    @Override
    Set<String> qualifyingStates() {
      return Set.of(ScaleReturnState.HELD);
    }

    @Override
    String apply(SummaryReturn ret, String user) {
      SummaryReturnWorkflows.ReleaseOutcome out = workflows.release(ret, user);
      StringBuilder msg = new StringBuilder("Released: status ").append(out.active().versionState()).append('.');
      if (!out.errors().isEmpty()) {
        msg.append(" ").append(out.errors().size()).append(" edit error(s).");
      }
      out.messages().forEach(m -> msg.append(' ').append(m));
      return msg.toString();
    }
  }
}
