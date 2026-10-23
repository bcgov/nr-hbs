package ca.bc.gov.nrs.hbs.api.billing;

import ca.bc.gov.nrs.hbs.api.legacyrules.Inputs;
import ca.bc.gov.nrs.hbs.api.legacyrules.LegacyMessages;
import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.RowLock;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowCommand;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * P836 "Delete" ({@code P836Action}, ACTION_DELETE): refused with
 * error.finalbillabledropoutmark.deleteerror when YTD cyclic billing
 * (invoices or statements) exists for the Pop/Yr/Mark, otherwise
 * HBS_REMOVE_FINAL_BILL_MARK.
 *
 * <p>Legacy only deleted when the record matched the P833 search the user
 * had come from (a session-state quirk that silently did nothing otherwise);
 * the delete here always applies to the record shown.
 *
 * <p>Body: {@code populationNumber}, {@code samplingYear}, {@code timberMark},
 * {@code updateTimestamp}.
 */
@Component
public class FinalBillTenureRemoveCommand implements WorkflowCommand {

  private final BillingReads reads;
  private final TableApi tableApi;

  public FinalBillTenureRemoveCommand(BillingReads reads, TableApi tableApi) {
    this.reads = reads;
    this.tableApi = tableApi;
  }

  @Override
  public String id() {
    return "billing.finalBillTenure.remove";
  }

  @Override
  public String legacy() {
    return "P836 Delete (P836Action)";
  }

  @Override
  public Capability capability() {
    return Capability.FINAL_BILL_ADMIN;
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    String population = Inputs.upper(body, "populationNumber");
    long samplingYear = Inputs.requiredLong(body, "samplingYear", "Sampling Year");
    String timberMark = Inputs.upper(body, "timberMark");
    Map<String, Object> current = population == null || timberMark == null
        ? null : reads.finalBillMark(population, samplingYear, timberMark);
    if (current == null) {
      throw new WorkflowValidationException("populationNumber", LegacyMessages.NO_RECORDS);
    }
    RowLock.check(Inputs.col(current, "UPDATE_TIMESTAMP"), body.get("updateTimestamp"));
    if (reads.cyclicBillingExists(population, samplingYear, timberMark)) {
      throw new WorkflowValidationException("timberMark", LegacyMessages.FB_DELETE_ERROR);
    }
    tableApi.call("HBS_REMOVE_FINAL_BILL_MARK", ProcArgs.of()
        .put("i_population_number", population)
        .put("i_sampling_year", samplingYear)
        .put("i_timber_mark", timberMark));
    return Map.of();
  }
}
