package ca.bc.gov.nrs.hbs.api.profiles;

import ca.bc.gov.nrs.hbs.api.profiles.ProfileReads.Range;
import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowCommand;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * P343 Add Mark Holder Profile — {@code $V/presentation/cpm/P343Action.perform}
 * ("Submit") then {@code ClientProfileManagerBean.createAndStoreMarkHolderProfile}
 * ({@code HBS_CREATE_HBS_MARK_PROFILE}).
 *
 * <p>Rules, in the legacy order: client location exists; return type given;
 * effective date valid (year ≥ 1900), in the future and the 1st of a month;
 * expiry date valid, not before today and the last day of a month; expiry not
 * before effective; frequency given; the effective date is not inside an
 * existing profile of the client location / return type; the expiry is before
 * the next profile's effective date. The forced summarization date starts
 * empty.
 */
@Component
public class MarkHolderProfileCreate implements WorkflowCommand {

  /** error.type.required */
  static final String RETURN_TYPE_REQUIRED = "A return type is required.";
  /** error.sumfreq.required */
  static final String FREQUENCY_REQUIRED = "Summarization Frequency is required";
  /** error.effectivedate.invalid2 */
  static final String EFFECTIVE_IN_EXISTING_RANGE =
      "Effective date cannot occur in an existing mark holder profile range.";

  private final TableApi tableApi;
  private final ProfileReads reads;
  private final Audit audit;

  public MarkHolderProfileCreate(TableApi tableApi, ProfileReads reads, Audit audit) {
    this.tableApi = tableApi;
    this.reads = reads;
    this.audit = audit;
  }

  @Override
  public String id() {
    return "profiles.markHolders.create";
  }

  @Override
  public String legacy() {
    return "P343 / ClientProfileManagerBean.createAndStoreMarkHolderProfile";
  }

  @Override
  public Capability capability() {
    return Capability.PROFILE_BILLING_ADMIN;
  }

  /**
   * {@code MarkHolderProfileQuery.returnTypeForSummarization}: SFP log tallies
   * ('F') share the piece-scale ('P') profiles.
   */
  static String returnTypeForSummarization(String returnType) {
    return "F".equals(returnType) ? "P" : returnType;
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    String client = ProfileRules.padded(body, "clientNumber", 8);
    String loc = ProfileRules.padded(body, "clientLocnCode", 2);
    String returnType = ProfileRules.upper(body, "returnType");
    String frequency = ProfileRules.upper(body, "frequency");
    LocalDate effective = ProfileRules.date(body, "effectiveDate");
    LocalDate expiry = ProfileRules.date(body, "expiryDate");
    LocalDateTime now = audit.now().toLocalDateTime();

    ProfileRules.Problems problems = new ProfileRules.Problems();
    clientLocation(problems, reads, client, loc);
    if (returnType == null) {
      problems.add("returnType", RETURN_TYPE_REQUIRED);
    }
    if (effective == null) {
      problems.add("effectiveDate", ProfileRules.EFFECTIVE_DATE_INVALID);
    } else if (effective.getYear() < ProfileRules.HBS_LOW_YEAR) {
      problems.add("effectiveDate", ProfileRules.YEAR_BEFORE_1900);
    }
    if (problems.isEmpty()) {
      if (effective.atStartOfDay().isBefore(now)) {
        problems.add("effectiveDate", ProfileRules.EFFECTIVE_NOT_FUTURE);
      }
      if (effective.getDayOfMonth() != 1) {
        problems.add("effectiveDate", ProfileRules.EFFECTIVE_NOT_FIRST_OF_MONTH);
      }
    }
    if (expiry == null) {
      problems.add("expiryDate", ProfileRules.EXPIRY_DATE_INVALID);
    }
    if (problems.isEmpty()) {
      if (expiry.atStartOfDay().isBefore(now)) {
        problems.add("expiryDate", ProfileRules.EXPIRY_BEFORE_TODAY);
      } else if (!ProfileRules.isLastDayOfMonth(expiry)) {
        problems.add("expiryDate", ProfileRules.EXPIRY_NOT_LAST_OF_MONTH);
      }
    }
    if (problems.isEmpty() && effective.isAfter(expiry)) {
      problems.add("expiryDate", ProfileRules.EXPIRY_BEFORE_EFFECTIVE);
    }
    if (frequency == null) {
      problems.add("frequency", FREQUENCY_REQUIRED);
    }
    problems.throwIfAny();

    LocalDateTime eff = effective.atStartOfDay();
    LocalDateTime exp = expiry.atStartOfDay();
    String summarized = returnTypeForSummarization(returnType);
    for (Range r : reads.markHolderRanges(client, loc, summarized)) {
      if (ProfileRules.insideRange(r.start(), r.end(), eff)) {
        problems.add("effectiveDate", EFFECTIVE_IN_EXISTING_RANGE).throwIfAny();
      }
    }
    if (ProfileRules.reachesNext(exp, reads.nextMarkHolderProfile(client, loc, summarized, eff))) {
      problems.add("expiryDate", ProfileRules.RANGE_OVERLAPS_NEXT).throwIfAny();
    }

    long id = tableApi.nextId("HBS_MARK_HOLDER_PROFILE_SEQ");
    String user = audit.user();
    Timestamp stamp = audit.now();
    tableApi.call("HBS_CREATE_HBS_MARK_PROFILE", ProcArgs.of()
        .put("i_mhprof_id", id)
        .put("i_client_number", client)
        .put("i_client_locn_code", loc)
        .put("i_hbs_return_type_code", returnType)
        .put("i_hbs_frequency_type_code", frequency)
        .put("i_forced_summarization_date", null)
        .put("i_effective_date", eff)
        .put("i_expiry_date", exp)
        .put("i_entry_userid", user)
        .put("i_entry_timestamp", stamp)
        .put("i_update_userid", user)
        .put("i_update_timestamp", stamp));
    return Map.of("hbsMarkHolderProfileId", id, "mhprofId", id);
  }

  /** {@code P343Action / P373Action.validateClientNumberAndLocation}. Returns true when valid. */
  static boolean clientLocation(ProfileRules.Problems problems, ProfileReads reads, String client, String loc) {
    if (client == null || loc == null) {
      problems.add("clientNumber", ProfileRules.CLIENT_REQUIRED);
      return false;
    }
    if (!reads.clientLocationExists(client, loc)) {
      problems.add("clientNumber", ProfileRules.CLIENT_LOCATION_INVALID);
      return false;
    }
    return true;
  }
}
