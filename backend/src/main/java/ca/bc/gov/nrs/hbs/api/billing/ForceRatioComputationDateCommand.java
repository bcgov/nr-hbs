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
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * P823/P824 "Force Next Ratio Computation Date" ({@code P823Action}). Legacy
 * offered two options, each touching exactly one Population Profile, so the
 * row action on {@code force-ratio-computation} covers both:
 *
 * <ul>
 *   <li>"For A Single Custom Profile" (population + sampling year + effective
 *       date): the profile must be in effect on the force date
 *       ({@code PopulationProfileQuery.selectCountByRClientCriteria}), else
 *       error.profile.notfound;</li>
 *   <li>"For The Global Profile": popprof_id 1, whatever its range
 *       ({@code selectByRClientCriteria} else-branch).</li>
 * </ul>
 *
 * Validation is {@code P823Action.validateForm} ({@link ForceDateRules#ratioComputation}):
 * year, frequency window (the profile's frequency — the P823 frequency select
 * was commented out), after LAST_RATIO_STMT_DATE, before NEXT_RATIO_STMT_DATE.
 * Write: HBS_STORE_POPULATION_PROFILE with NEXT_RATIO_STMT_DATE = force date at
 * 23:59:59 (BT4677) and the update audit columns; every other column from the
 * row re-read in this transaction.
 *
 * <p>Body: {@code popprofId}, {@code forceDate}, {@code updateTimestamp}.
 */
@Component
public class ForceRatioComputationDateCommand implements WorkflowCommand {

  static final String PROC = "HBS_STORE_POPULATION_PROFILE";
  static final long GLOBAL_PROFILE_ID = 1L;

  private final BillingReads reads;
  private final TableApi tableApi;
  private final Audit audit;

  public ForceRatioComputationDateCommand(BillingReads reads, TableApi tableApi, Audit audit) {
    this.reads = reads;
    this.tableApi = tableApi;
    this.audit = audit;
  }

  @Override
  public String id() {
    return "billing.populationProfile.forceRatioComputationDate";
  }

  @Override
  public String legacy() {
    return "P823/P824 (P823Action)";
  }

  @Override
  public Capability capability() {
    return Capability.BILLING_FORCE;
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    long popprofId = Inputs.requiredLong(body, "popprofId", "Population Profile");
    LocalDate forceDate = Inputs.date(body, "forceDate", "Force date");
    if (forceDate == null) {
      throw new WorkflowValidationException("forceDate", LegacyMessages.FORCE_YEAR_INVALID);
    }
    Map<String, Object> row = reads.populationProfile(popprofId);
    if (row == null) {
      throw new WorkflowValidationException("forceDate", LegacyMessages.PROFILE_NOT_FOUND);
    }
    RowLock.check(Inputs.col(row, "UPDATE_TIMESTAMP"), body.get("updateTimestamp"));

    Timestamp now = audit.now();
    List<String> errors = ForceDateRules.ratioComputation(now.toLocalDateTime().toLocalDate(), forceDate,
        Inputs.colText(row, "HBS_FREQUENCY_TYPE_CODE"),
        Inputs.dateTime(Inputs.col(row, "LAST_RATIO_STMT_DATE")),
        Inputs.dateTime(Inputs.col(row, "NEXT_RATIO_STMT_DATE")));
    if (!errors.isEmpty()) {
      throw new WorkflowValidationException(errors.stream()
          .map(m -> new WorkflowValidationException.Problem("forceDate", m)).toList());
    }
    if (popprofId != GLOBAL_PROFILE_ID && !ForceSummarizationDateCommand.inEffect(row, forceDate)) {
      throw new WorkflowValidationException("forceDate", LegacyMessages.PROFILE_NOT_FOUND);
    }

    ProcArgs args = tableApi.argsFromRow(PROC, row)
        .put("i_next_ratio_stmt_date", forceDate.atTime(LocalTime.of(23, 59, 59)))
        .put("i_update_userid", audit.user())
        .put("i_update_timestamp", now);
    tableApi.call(PROC, args);
    return Map.of("popprofId", popprofId, "nextRatioStmtDate", forceDate.toString());
  }
}
