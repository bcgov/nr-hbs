package ca.bc.gov.nrs.hbs.api.profiles;

import ca.bc.gov.nrs.hbs.api.exception.EntityNotFoundException;
import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.RowLock;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowCommand;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * P342 Update Mark Holder Profile — {@code $V/presentation/cpm/P342Action.perform}
 * ("Submit") then {@code ClientProfileManagerBean.updateMarkHolderProfile}
 * ({@code HBS_STORE_HBS_MARK_PROFILE}).
 *
 * <p>Only the expiry date is editable, and only while the profile's
 * effective date is after today (the legacy page hid Submit/Delete
 * otherwise and the action re-checked it). Rules: expiry valid (year ≥ 1900),
 * not before effective, not before today, the last day of a month, and
 * before the next profile's effective date.
 *
 * <p>As in the legacy action, the forced summarization date is cleared on
 * update ({@code new MarkHolderProfile(.., null, ..)}).
 */
@Component
public class MarkHolderProfileUpdate implements WorkflowCommand {

  private static final String STORE = "HBS_STORE_HBS_MARK_PROFILE";

  private final TableApi tableApi;
  private final ProfileReads reads;
  private final Audit audit;

  public MarkHolderProfileUpdate(TableApi tableApi, ProfileReads reads, Audit audit) {
    this.tableApi = tableApi;
    this.reads = reads;
    this.audit = audit;
  }

  @Override
  public String id() {
    return "profiles.markHolders.update";
  }

  @Override
  public String legacy() {
    return "P342 / ClientProfileManagerBean.updateMarkHolderProfile";
  }

  @Override
  public Capability capability() {
    return Capability.PROFILE_BILLING_ADMIN;
  }

  static Map<String, Object> load(ProfileReads reads, long mhprofId) {
    Map<String, Object> row = reads.markHolderProfile(mhprofId);
    if (row == null) {
      throw new EntityNotFoundException(Map.class, "mhprofId", String.valueOf(mhprofId));
    }
    return row;
  }

  /** {@code P342Form.getIsFutureEffectiveDate}: effective date after the start of today. */
  static boolean futureEffective(Map<String, Object> row, LocalDateTime now) {
    LocalDateTime effective = ProfileRules.dateTime(row.get("effective_date"));
    return effective != null && effective.isAfter(now.toLocalDate().atStartOfDay());
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    long id = ProfileRules.key(body, "mhprofId");
    Map<String, Object> row = load(reads, id);
    RowLock.check(row.get("update_timestamp"), ProfileRules.lockValue(body));
    LocalDateTime now = audit.now().toLocalDateTime();

    ProfileRules.Problems problems = new ProfileRules.Problems();
    if (!futureEffective(row, now)) {
      problems.add("effectiveDate", ProfileRules.EFFECTIVE_NOT_FUTURE_FOR_UPDATE);
    }
    LocalDate expiry = ProfileRules.date(body, "expiryDate");
    ScaleSiteProfileRules.checkDate(problems, "expiryDate", expiry, true);
    problems.throwIfAny();

    LocalDateTime effective = ProfileRules.dateTime(row.get("effective_date"));
    LocalDateTime exp = expiry.atStartOfDay();
    if (effective.isAfter(exp)) {
      problems.add("expiryDate", ProfileRules.EXPIRY_BEFORE_EFFECTIVE);
    } else if (exp.isBefore(now)) {
      problems.add("expiryDate", ProfileRules.EXPIRY_BEFORE_TODAY);
    } else if (!ProfileRules.isLastDayOfMonth(expiry)) {
      problems.add("expiryDate", ProfileRules.EXPIRY_NOT_LAST_OF_MONTH);
    }
    problems.throwIfAny();

    String client = (String) row.get("client_number");
    String loc = (String) row.get("client_locn_code");
    String returnType = MarkHolderProfileCreate.returnTypeForSummarization((String) row.get("hbs_return_type_code"));
    if (ProfileRules.reachesNext(exp, reads.nextMarkHolderProfile(client, loc, returnType, effective))) {
      problems.add("expiryDate", ProfileRules.RANGE_OVERLAPS_NEXT).throwIfAny();
    }

    ProcArgs args = tableApi.argsFromRow(STORE, row)
        .put("i_forced_summarization_date", null)
        .put("i_expiry_date", exp)
        .put("i_update_userid", audit.user())
        .put("i_update_timestamp", audit.now());
    tableApi.call(STORE, args);
    return Map.of("mhprofId", id);
  }
}
