package ca.bc.gov.nrs.hbs.api.billing;

import ca.bc.gov.nrs.hbs.api.legacyrules.Inputs;
import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowCommand;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException.Problem;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * P835 "Add Non-Renewable Tenure That Receives Final Bills" Save
 * ({@code P835Action.hbsPerform} → {@code ClientHelper.createFinalBillableDropoutMark}):
 * {@link FinalBillTenureRules#validateCreate} (population exists, 4-digit
 * year 2000–2099, ratio type not Default, no cyclic billing for the
 * Pop/Yr/Mark, mark in TIMBER_MARK + MARK_BILLED_CLI and non-renewable,
 * Pop/Yr exists, not already elected), then HBS_CREATE_FINAL_BILL_MARK.
 *
 * <p>Body: {@code populationNumber}, {@code samplingYear}, {@code timberMark}.
 */
@Component
public class FinalBillTenureCreateCommand implements WorkflowCommand {

  static final String PROC = "HBS_CREATE_FINAL_BILL_MARK";

  private final FinalBillTenureRules rules;
  private final TableApi tableApi;
  private final Audit audit;

  public FinalBillTenureCreateCommand(FinalBillTenureRules rules, TableApi tableApi, Audit audit) {
    this.rules = rules;
    this.tableApi = tableApi;
    this.audit = audit;
  }

  @Override
  public String id() {
    return "billing.finalBillTenure.create";
  }

  @Override
  public String legacy() {
    return "P835 (P835Action)";
  }

  @Override
  public Capability capability() {
    return Capability.FINAL_BILL_ADMIN;
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    FinalBillTenureRules.Keys k = new FinalBillTenureRules.Keys(
        Inputs.upper(body, "populationNumber"), Inputs.text(body, "samplingYear"), Inputs.upper(body, "timberMark"));
    List<Problem> errors = rules.validateCreate(k);
    if (!errors.isEmpty()) {
      throw new WorkflowValidationException(errors);
    }
    String user = audit.user();
    Timestamp now = audit.now();
    tableApi.call(PROC, ProcArgs.of()
        .put("i_population_number", k.population())
        .put("i_sampling_year", k.year())
        .put("i_timber_mark", k.timberMark())
        .put("i_entry_userid", user)
        .put("i_entry_timestamp", now)
        .put("i_update_userid", user)
        .put("i_update_timestamp", now));
    return Map.of("populationNumber", k.population(), "samplingYear", k.year(), "timberMark", k.timberMark());
  }
}
