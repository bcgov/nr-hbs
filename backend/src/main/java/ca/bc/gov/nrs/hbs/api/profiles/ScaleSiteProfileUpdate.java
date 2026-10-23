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
 * P322 Update Scale Site Profile — {@code $V/presentation/cpm/P322Action.perform}
 * ("Submit") then {@code ClientProfileManagerBean.updateTradingPartnerSubmitter}
 * ({@code HBS_STORE_TRA_PART_SUBMITTER}).
 *
 * <p>Only the expiry date is editable (the legacy screen showed the rest
 * read-only); every other column is re-read from the row, so the client can't
 * change keys or entry audit values. Rules: expiry valid (year ≥ 1900); the
 * site is in the caller's site / district data domain; expiry not before
 * effective; expiry before the next profile's effective date.
 */
@Component
public class ScaleSiteProfileUpdate implements WorkflowCommand {

  private static final String STORE = "HBS_STORE_TRA_PART_SUBMITTER";

  private final TableApi tableApi;
  private final ProfileReads reads;
  private final ScaleSiteProfileRules rules;
  private final Audit audit;

  public ScaleSiteProfileUpdate(TableApi tableApi, ProfileReads reads, ScaleSiteProfileRules rules, Audit audit) {
    this.tableApi = tableApi;
    this.reads = reads;
    this.rules = rules;
    this.audit = audit;
  }

  @Override
  public String id() {
    return "profiles.scaleSites.update";
  }

  @Override
  public String legacy() {
    return "P322 / ClientProfileManagerBean.updateTradingPartnerSubmitter";
  }

  @Override
  public Capability capability() {
    return Capability.SCALE_SITE_ADMIN;
  }

  /** The TPS id from {@code tpsId}, or from the list's {@code profileKey} ('TPS…'). */
  static long tpsId(Map<String, Object> body) {
    Long id = ProfileRules.number(body, "tpsId");
    if (id != null) return id;
    String key = ProfileRules.upper(body, "profileKey");
    if (key != null && key.matches("TPS\\d{1,18}")) return Long.parseLong(key.substring(3));
    throw new IllegalArgumentException("'tpsId' is required");
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    long tpsId = tpsId(body);
    Map<String, Object> row = reads.tradingPartnerSubmitter(tpsId);
    if (row == null) {
      throw new EntityNotFoundException(Map.class, "tpsId", String.valueOf(tpsId));
    }
    RowLock.check(row.get("update_timestamp"), ProfileRules.lockValue(body));

    String site = (String) row.get("scale_site_id_nmbr");
    LocalDate expiry = ProfileRules.date(body, "expiryDate");
    ProfileRules.Problems problems = new ProfileRules.Problems();
    ScaleSiteProfileRules.checkDate(problems, "expiryDate", expiry, true);
    if (!rules.associatedWithCaller(site, reads.scaleSite(site))) {
      problems.add("scaleSiteId", ScaleSiteProfileRules.SITE_NOT_AUTHORIZED);
    }
    problems.throwIfAny();

    LocalDateTime effective = ProfileRules.dateTime(row.get("effective_date"));
    LocalDateTime exp = expiry.atStartOfDay();
    if (effective == null || effective.isAfter(exp)) {
      problems.add("expiryDate", ProfileRules.EXPIRY_BEFORE_EFFECTIVE).throwIfAny();
    }
    LocalDateTime next = reads.nextScaleSiteProfile(site, (String) row.get("hbs_return_type_code"),
        (String) row.get("tp_doc_type"), blankToNull(row.get("client_number")),
        blankToNull(row.get("client_locn_code")), effective);
    if (ProfileRules.reachesNext(exp, next)) {
      problems.add("expiryDate", ProfileRules.RANGE_OVERLAPS_NEXT).throwIfAny();
    }

    ProcArgs args = tableApi.argsFromRow(STORE, row)
        .put("i_expiry_date", exp)
        .put("i_update_userid", audit.user())
        .put("i_update_timestamp", audit.now());
    tableApi.call(STORE, args);
    return Map.of("tpsId", tpsId, "scaleSiteId", site);
  }

  private static String blankToNull(Object v) {
    return v == null || v.toString().isBlank() ? null : v.toString();
  }
}
