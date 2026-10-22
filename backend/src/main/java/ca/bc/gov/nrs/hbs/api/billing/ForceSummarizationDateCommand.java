package ca.bc.gov.nrs.hbs.api.billing;

import ca.bc.gov.nrs.hbs.api.legacyrules.Inputs;
import ca.bc.gov.nrs.hbs.api.legacyrules.LegacyMessages;
import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.RowLock;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowCommand;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * P811/P812 and P813/P814 for one listed Mark Holder Profile (the row action
 * of {@code force-piece-summarization} / {@code force-weigh-slip-summarization}).
 * Legacy forced every profile matching the criteria at once — that is
 * {@link ForceSummarizationCommand}; this is the same rule set applied to the
 * single profile the user picked:
 *
 * <ol>
 *   <li>re-read the profile and check its UPDATE_TIMESTAMP ({@link RowLock});</li>
 *   <li>{@code P811Action.validateForm}: year 2000–2099, the frequency window
 *       from today ({@link ForceDateRules#summarization}) using the profile's
 *       frequency (the P811 criteria frequency the row matched);</li>
 *   <li>the profile must be in effect on the force date — the
 *       {@code MarkHolderProfileQuery.selectByCriteria} range — else
 *       "No Records" (all.noresults.header);</li>
 *   <li>HBS_STORE_HBS_MARK_PROFILE with FORCED_SUMMARIZATION_DATE = force date.</li>
 * </ol>
 *
 * Body: {@code mhprofId}, {@code forceDate} (yyyy-mm-dd), {@code updateTimestamp}.
 */
@Component
public class ForceSummarizationDateCommand implements WorkflowCommand {

  private final BillingReads reads;
  private final MarkHolderProfileForcer forcer;
  private final Audit audit;

  public ForceSummarizationDateCommand(BillingReads reads, MarkHolderProfileForcer forcer, Audit audit) {
    this.reads = reads;
    this.forcer = forcer;
    this.audit = audit;
  }

  @Override
  public String id() {
    return "billing.markHolderProfile.forceSummarizationDate";
  }

  @Override
  public String legacy() {
    return "P811/P812, P813/P814 (P811Action/P813Action)";
  }

  @Override
  public Capability capability() {
    return Capability.BILLING_FORCE;
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    long mhprofId = Inputs.requiredLong(body, "mhprofId", "Mark Holder Profile");
    LocalDate forceDate = Inputs.date(body, "forceDate", "Force date");
    if (forceDate == null) {
      throw new WorkflowValidationException("forceDate", LegacyMessages.FORCE_YEAR_INVALID);
    }
    Map<String, Object> row = reads.markHolderProfile(mhprofId);
    if (row == null) {
      throw new WorkflowValidationException("mhprofId", LegacyMessages.NO_RECORDS);
    }
    RowLock.check(Inputs.col(row, "UPDATE_TIMESTAMP"), body.get("updateTimestamp"));

    Timestamp now = audit.now();
    List<String> errors = ForceDateRules.summarization(now.toLocalDateTime().toLocalDate(), forceDate,
        Inputs.colText(row, "HBS_FREQUENCY_TYPE_CODE"));
    if (!errors.isEmpty()) {
      throw new WorkflowValidationException(errors.stream()
          .map(m -> new WorkflowValidationException.Problem("forceDate", m)).toList());
    }
    if (!inEffect(row, forceDate)) {
      throw new WorkflowValidationException("forceDate", LegacyMessages.NO_RECORDS);
    }
    forcer.force(row, forceDate, audit.user(), now);
    return Map.of("mhprofId", mhprofId, "forcedSummarizationDate", forceDate.toString());
  }

  /** {@code :force >= effective_date AND :force <= expiry_date} with the force date at 00:00. */
  static boolean inEffect(Map<String, Object> row, LocalDate forceDate) {
    LocalDateTime at = forceDate.atStartOfDay();
    LocalDateTime effective = Inputs.dateTime(Inputs.col(row, "EFFECTIVE_DATE"));
    LocalDateTime expiry = Inputs.dateTime(Inputs.col(row, "EXPIRY_DATE"));
    return (effective == null || !at.isBefore(effective)) && (expiry == null || !at.isAfter(expiry));
  }
}
