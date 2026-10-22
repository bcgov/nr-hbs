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
 * P323 Add Scale Site Profile — {@code $V/presentation/cpm/P323Action.perform}
 * ("Submit") then {@code ClientProfileManagerBean.createAndStoreTradingPartnerSubmitter}
 * ({@code HBS_CREATE_TRA_PART_SUBMITTER}, submitter type TPSS).
 *
 * <p>Rules, in the legacy order: the scale site exists and is in the caller's
 * site / district data domain; a Detail record has a blank client and
 * location, a Summary record needs a TRADING_PARTNER client location; both
 * dates valid (year ≥ 1900); expiry not before effective; the effective date
 * is not inside an existing profile's range; the expiry is before the next
 * profile's effective date.
 */
@Component
public class ScaleSiteProfileCreate implements WorkflowCommand {

  private final TableApi tableApi;
  private final ProfileReads reads;
  private final ScaleSiteProfileRules rules;
  private final Audit audit;

  public ScaleSiteProfileCreate(TableApi tableApi, ProfileReads reads, ScaleSiteProfileRules rules, Audit audit) {
    this.tableApi = tableApi;
    this.reads = reads;
    this.rules = rules;
    this.audit = audit;
  }

  @Override
  public String id() {
    return "profiles.scaleSites.create";
  }

  @Override
  public String legacy() {
    return "P323 / ClientProfileManagerBean.createAndStoreTradingPartnerSubmitter";
  }

  @Override
  public Capability capability() {
    return Capability.SCALE_SITE_ADMIN;
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    String site = ProfileRules.padded(body, "scaleSiteId", 3);
    String docType = ProfileRules.upper(body, "docType");
    String returnType = ProfileRules.upper(body, "returnType");
    String client = ProfileRules.padded(body, "clientNumber", 8);
    String loc = ProfileRules.padded(body, "clientLocnCode", 2);
    LocalDate effective = ProfileRules.date(body, "effectiveDate");
    LocalDate expiry = ProfileRules.date(body, "expiryDate");

    ProfileRules.Problems problems = new ProfileRules.Problems();
    if (site == null) problems.add("scaleSiteId", ProfileRules.required("Scale Site"));
    if (docType == null) problems.add("docType", ProfileRules.required("Submission Type"));
    if (returnType == null) problems.add("returnType", ProfileRules.required("Return Type"));
    problems.throwIfAny();

    Map<String, Object> scaleSite = reads.scaleSite(site);
    if (scaleSite == null) {
      problems.add("scaleSiteId", ScaleSiteProfileRules.SITE_NOT_FOUND);
    } else if (!rules.associatedWithCaller(site, scaleSite)) {
      problems.add("scaleSiteId", ScaleSiteProfileRules.SITE_NOT_AUTHORIZED);
    }
    // P323Action.validateClientNumberAndLocation
    if ("D".equals(docType)) {
      if (client != null || loc != null) {
        problems.add("clientNumber", ScaleSiteProfileRules.DETAIL_CLIENT_NOT_BLANK);
      }
    } else if (client == null || loc == null) {
      problems.add("clientNumber", ProfileRules.CLIENT_REQUIRED);
    } else if (!reads.tradingPartnerExists(client, loc)) {
      problems.add("clientNumber", ScaleSiteProfileRules.TRADING_PARTNER_INVALID);
    }
    ScaleSiteProfileRules.checkDate(problems, "effectiveDate", effective, true);
    ScaleSiteProfileRules.checkDate(problems, "expiryDate", expiry, true);
    problems.throwIfAny();

    LocalDateTime eff = effective.atStartOfDay();
    LocalDateTime exp = expiry.atStartOfDay();
    if (eff.isAfter(exp)) {
      problems.add("expiryDate", ProfileRules.EXPIRY_BEFORE_EFFECTIVE).throwIfAny();
    }
    for (Range r : reads.scaleSiteProfileRanges(site, returnType, docType, client, loc)) {
      if (ProfileRules.insideRange(r.start(), r.end(), eff)) {
        problems.add("effectiveDate", ScaleSiteProfileRules.EFFECTIVE_IN_EXISTING_RANGE).throwIfAny();
      }
    }
    LocalDateTime next = reads.nextScaleSiteProfile(site, returnType, docType, client, loc, eff);
    if (ProfileRules.reachesNext(exp, next)) {
      problems.add("expiryDate", ProfileRules.RANGE_OVERLAPS_NEXT).throwIfAny();
    }

    long tpsId = tableApi.nextId("TRADING_PARTNER_SUBMITTER_SEQ");
    String user = audit.user();
    Timestamp now = audit.now();
    tableApi.call("HBS_CREATE_TRA_PART_SUBMITTER", ProcArgs.of()
        .put("i_tps_id", tpsId)
        .put("i_tpsubmtr_type", "TPSS")
        .put("i_client_number", client)
        .put("i_client_locn_code", loc)
        .put("i_scale_site_id_nmbr", site)
        .put("i_hbs_return_type_code", returnType)
        .put("i_entry_userid", user)
        .put("i_entry_timestamp", now)
        .put("i_update_userid", user)
        .put("i_update_timestamp", now)
        .put("i_effective_date", eff)
        .put("i_expiry_date", exp)
        .put("i_tp_doc_type", docType));
    return Map.of("tradingPartnerSubmitterId", tpsId, "profileKey", "TPS" + tpsId, "scaleSiteId", site);
  }
}
