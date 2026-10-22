package ca.bc.gov.nrs.hbs.api.profiles;

import ca.bc.gov.nrs.hbs.api.exception.EntityNotFoundException;
import ca.bc.gov.nrs.hbs.api.profiles.ProfileReads.Next;
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
 * P372 Update Statement Delivery Profile — {@code $V/presentation/cpm/P372Action.perform}
 * ("Submit") then {@code ClientProfileManagerBean.updateClientDeliveryProfile}
 * ({@code HBS_STORE_HBS_CLI_DEL_PROFILE}).
 *
 * <p>Editable: effective date, expiry date and delivery method; client,
 * location and entry audit values come from the row. Rules: E-mail / FTP
 * delivery needs an active document receiver for the location; both dates
 * valid (year ≥ 1900); expiry not before effective; expiry before the next
 * profile's effective date.
 *
 * <p>The next-profile check is ported as written: the legacy query returns
 * the profile(s) with the earliest effective date on or after the new one,
 * and when the first of them is the profile being updated it is skipped
 * rather than looking further, so the check only fires when another profile
 * starts at or after the new effective date ahead of this one.
 */
@Component
public class DeliveryProfileUpdate implements WorkflowCommand {

  private static final String STORE = "HBS_STORE_HBS_CLI_DEL_PROFILE";

  private final TableApi tableApi;
  private final ProfileReads reads;
  private final Audit audit;

  public DeliveryProfileUpdate(TableApi tableApi, ProfileReads reads, Audit audit) {
    this.tableApi = tableApi;
    this.reads = reads;
    this.audit = audit;
  }

  @Override
  public String id() {
    return "profiles.delivery.update";
  }

  @Override
  public String legacy() {
    return "P372 / ClientProfileManagerBean.updateClientDeliveryProfile";
  }

  @Override
  public Capability capability() {
    return Capability.PROFILE_BILLING_ADMIN;
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    long id = ProfileRules.key(body, "dlvprofId");
    Map<String, Object> row = reads.deliveryProfile(id);
    if (row == null) {
      throw new EntityNotFoundException(Map.class, "dlvprofId", String.valueOf(id));
    }
    RowLock.check(row.get("update_timestamp"), ProfileRules.lockValue(body));

    String client = (String) row.get("client_number");
    String loc = (String) row.get("client_locn_code");
    String method = ProfileRules.upper(body, "deliveryMethod");
    LocalDate effective = ProfileRules.date(body, "effectiveDate");
    LocalDate expiry = ProfileRules.date(body, "expiryDate");

    ProfileRules.Problems problems = new ProfileRules.Problems();
    if (method == null) {
      problems.add("deliveryMethod", ProfileRules.required("Document Delivery Method"));
    } else {
      DeliveryProfileCreate.documentReceivers(problems, reads, client, loc, method);
    }
    if (expiry == null) {
      problems.add("expiryDate", ProfileRules.EXPIRY_DATE_INVALID);
    } else if (expiry.getYear() < ProfileRules.HBS_LOW_YEAR) {
      problems.add("expiryDate", ProfileRules.YEAR_BEFORE_1900);
    }
    DeliveryProfileCreate.effectiveDate(problems, effective);
    problems.throwIfAny();

    LocalDateTime eff = effective.atStartOfDay();
    LocalDateTime exp = expiry.atStartOfDay();
    if (eff.isAfter(exp)) {
      problems.add("expiryDate", ProfileRules.EXPIRY_BEFORE_EFFECTIVE).throwIfAny();
    }
    Next next = reads.nextDeliveryProfile(client, loc, eff);
    if (next != null && !Long.valueOf(id).equals(next.id()) && ProfileRules.reachesNext(exp, next.effective())) {
      problems.add("expiryDate", ProfileRules.RANGE_OVERLAPS_NEXT).throwIfAny();
    }

    ProcArgs args = tableApi.argsFromRow(STORE, row)
        .put("i_hbs_delivery_method_code", method)
        .put("i_effective_date", eff)
        .put("i_expiry_date", exp)
        .put("i_update_userid", audit.user())
        .put("i_update_timestamp", audit.now());
    tableApi.call(STORE, args);
    return Map.of("dlvprofId", id);
  }
}
