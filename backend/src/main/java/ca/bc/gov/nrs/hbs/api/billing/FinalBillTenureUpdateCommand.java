package ca.bc.gov.nrs.hbs.api.billing;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * P836 "Update Non-Renewable Tenure That Receives Final Bills" Save
 * ({@code P836Action}). The election has no data columns besides its key, so
 * an update changes the key: legacy deleted the old row and inserted the new
 * one, keeping the old ENTRY_USERID / ENTRY_TIMESTAMP — done here as
 * HBS_REMOVE_FINAL_BILL_MARK + HBS_CREATE_FINAL_BILL_MARK in one transaction.
 *
 * <p>Validation ({@code P836Action.validateForm}, Save):
 * <ul>
 *   <li>new keys: {@link FinalBillTenureRules#validateNewKeys};</li>
 *   <li>cyclic billing (invoices/statements) for the <i>new</i> Pop/Yr/Mark →
 *       error.finalbillabledropoutmark.inserterror; for the <i>old</i> one →
 *       …deleteerror (both when both have it);</li>
 *   <li>keys unchanged: both messages when the old Pop/Yr/Mark has cyclic
 *       billing (BT 2810), otherwise nothing to do.</li>
 * </ul>
 *
 * <p>Body: old keys {@code opu}, {@code osy}, {@code otm} (the record
 * loaded), new {@code populationNumber}, {@code samplingYear}, {@code timberMark},
 * and {@code updateTimestamp}.
 */
@Component
public class FinalBillTenureUpdateCommand implements WorkflowCommand {

  private final FinalBillTenureRules rules;
  private final BillingReads reads;
  private final TableApi tableApi;
  private final Audit audit;

  public FinalBillTenureUpdateCommand(FinalBillTenureRules rules, BillingReads reads, TableApi tableApi,
      Audit audit) {
    this.rules = rules;
    this.reads = reads;
    this.tableApi = tableApi;
    this.audit = audit;
  }

  @Override
  public String id() {
    return "billing.finalBillTenure.update";
  }

  @Override
  public String legacy() {
    return "P836 Save (P836Action)";
  }

  @Override
  public Capability capability() {
    return Capability.FINAL_BILL_ADMIN;
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    FinalBillTenureRules.Keys old = new FinalBillTenureRules.Keys(
        Inputs.upper(body, "opu"), Inputs.text(body, "osy"), Inputs.upper(body, "otm"));
    FinalBillTenureRules.Keys k = new FinalBillTenureRules.Keys(
        Inputs.upper(body, "populationNumber"), Inputs.text(body, "samplingYear"), Inputs.upper(body, "timberMark"));
    if (old.population() == null || !FinalBillTenureRules.allNumbers(old.samplingYear()) || old.timberMark() == null) {
      throw new WorkflowValidationException("opu", LegacyMessages.NO_RECORDS);
    }
    Map<String, Object> current = reads.finalBillMark(old.population(), old.year(), old.timberMark());
    if (current == null) {
      throw new WorkflowValidationException("opu", LegacyMessages.NO_RECORDS);
    }
    RowLock.check(Inputs.col(current, "UPDATE_TIMESTAMP"), body.get("updateTimestamp"));

    boolean changed = !Objects.equals(old.population(), k.population())
        || !Objects.equals(old.samplingYear(), k.samplingYear())
        || !Objects.equals(old.timberMark(), k.timberMark());
    List<Problem> errors = new ArrayList<>();
    if (!changed) {
      if (rules.cyclicBillingExists(old)) {
        errors.add(new Problem("timberMark", LegacyMessages.FB_INSERT_ERROR));
        errors.add(new Problem("", LegacyMessages.FB_DELETE_ERROR));
        throw new WorkflowValidationException(errors);
      }
      return Map.of("changed", false);
    }

    boolean notDuplicate = rules.validateNewKeys(k, errors);
    if (errors.isEmpty() && notDuplicate) {
      if (rules.cyclicBillingExists(k)) {
        errors.add(new Problem("timberMark", LegacyMessages.FB_INSERT_ERROR));
      }
      if (rules.cyclicBillingExists(old)) {
        errors.add(new Problem("timberMark", LegacyMessages.FB_DELETE_ERROR));
      }
    }
    if (!errors.isEmpty()) {
      throw new WorkflowValidationException(errors);
    }

    tableApi.call("HBS_REMOVE_FINAL_BILL_MARK", ProcArgs.of()
        .put("i_population_number", old.population())
        .put("i_sampling_year", old.year())
        .put("i_timber_mark", old.timberMark()));
    tableApi.call("HBS_CREATE_FINAL_BILL_MARK", ProcArgs.of()
        .put("i_population_number", k.population())
        .put("i_sampling_year", k.year())
        .put("i_timber_mark", k.timberMark())
        .put("i_entry_userid", Inputs.col(current, "ENTRY_USERID"))
        .put("i_entry_timestamp", Inputs.col(current, "ENTRY_TIMESTAMP"))
        .put("i_update_userid", audit.user())
        .put("i_update_timestamp", audit.now()));
    return Map.of("changed", true, "populationNumber", k.population(), "samplingYear", k.year(),
        "timberMark", k.timberMark());
  }
}
