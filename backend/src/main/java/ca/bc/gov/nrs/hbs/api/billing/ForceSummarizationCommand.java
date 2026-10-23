package ca.bc.gov.nrs.hbs.api.billing;

import ca.bc.gov.nrs.hbs.api.legacyrules.Inputs;
import ca.bc.gov.nrs.hbs.api.legacyrules.LegacyMessages;
import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowCommand;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * P811/P812 "Force Next Piece Scale Tally Summarization Date" and P813/P814
 * "Force Next Weight Slip Summarization Date" as legacy ran them
 * ({@code P811Action.hbsPerform} / {@code P813Action.hbsPerform}, Save branch):
 *
 * <ul>
 *   <li>{@code selected = yes} ("Client Location"): every custom profile of
 *       that client/location;</li>
 *   <li>{@code selected = no} ("For All Client With Custom Mark Holder
 *       Profile"): every custom profile, <b>plus the default profile</b>
 *       (mhprof_id 1 for piece, 2 for weight — forced whatever its range);</li>
 * </ul>
 * of the return type and frequency, in effect on the force date
 * ({@code MarkHolderProfileQuery.selectByCriteria}, which never returns 1/2).
 * The force date passes {@link ForceDateRules#summarization} first; no
 * matching profile is "No Records", as the P812 count step reported. All
 * updates are one transaction (legacy committed each profile).
 *
 * <p>Body: {@code returnType} (P|W), {@code frequency} (MTH|BWK|DLY),
 * {@code selected} (yes|no), {@code clientNumber}, {@code clientLocnCode},
 * {@code forceDate}. Returns {@code count} = profiles updated.
 */
@Component
public class ForceSummarizationCommand implements WorkflowCommand {

  private final BillingReads reads;
  private final MarkHolderProfileForcer forcer;
  private final Audit audit;

  public ForceSummarizationCommand(BillingReads reads, MarkHolderProfileForcer forcer, Audit audit) {
    this.reads = reads;
    this.forcer = forcer;
    this.audit = audit;
  }

  @Override
  public String id() {
    return "billing.forceSummarization.force";
  }

  @Override
  public String legacy() {
    return "P811/P812, P813/P814 (P811Action/P813Action Save)";
  }

  @Override
  public Capability capability() {
    return Capability.BILLING_FORCE;
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    String returnType = Inputs.upper(body, "returnType");
    if (!"P".equals(returnType) && !"W".equals(returnType)) {
      throw new WorkflowValidationException("returnType", LegacyMessages.invalid("Return Type"));
    }
    String frequency = Inputs.upper(body, "frequency");
    if (frequency == null) {
      throw new WorkflowValidationException("frequency", LegacyMessages.required("Frequency"));
    }
    LocalDate forceDate = Inputs.date(body, "forceDate", "Force date");
    if (forceDate == null) {
      throw new WorkflowValidationException("forceDate", LegacyMessages.FORCE_YEAR_INVALID);
    }
    Timestamp now = audit.now();
    List<String> errors = ForceDateRules.summarization(now.toLocalDateTime().toLocalDate(), forceDate, frequency);
    if (!errors.isEmpty()) {
      throw new WorkflowValidationException(errors.stream()
          .map(m -> new WorkflowValidationException.Problem("forceDate", m)).toList());
    }

    boolean forClient = "yes".equalsIgnoreCase(Inputs.text(body, "selected"));
    String clientNumber = null;
    String clientLocnCode = null;
    if (forClient) {
      // P811Form.setClientNumber / setClientLoc: trimmed and left-padded with zeros.
      String c = Inputs.text(body, "clientNumber");
      String l = Inputs.text(body, "clientLocnCode");
      clientNumber = c == null ? null : StringUtils.leftPad(c, 8, '0');
      clientLocnCode = l == null ? null : StringUtils.leftPad(l, 2, '0');
    }

    List<Map<String, Object>> profiles =
        reads.markHolderProfilesToForce(returnType, frequency, clientNumber, clientLocnCode, forceDate);
    if (profiles.isEmpty()) {
      throw new WorkflowValidationException("forceDate", LegacyMessages.NO_RECORDS);
    }
    String user = audit.user();
    for (Map<String, Object> profile : profiles) {
      forcer.force(profile, forceDate, user, now);
    }
    int count = profiles.size();
    if (!forClient) {
      long defaultId = "P".equals(returnType) ? 1L : 2L;
      Map<String, Object> defaultProfile = reads.markHolderProfile(defaultId);
      if (defaultProfile != null) {
        forcer.force(defaultProfile, forceDate, user, now);
        count++;
      }
    }
    return Map.of("count", count, "forcedSummarizationDate", forceDate.toString());
  }
}
