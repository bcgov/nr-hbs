package ca.bc.gov.nrs.hbs.api.profiles;

import ca.bc.gov.nrs.hbs.api.profiles.ProfileReads.Next;
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
 * P373 Add Statement Delivery Profile — {@code $V/presentation/cpm/P373Action.perform}
 * ("Submit") then {@code ClientProfileManagerBean.createAndStoreClientDeliveryProfile}
 * ({@code HBS_CREATE_HBS_CLI_DEL_PROFILE}).
 *
 * <p>Rules, in the legacy order: client location exists; E-mail / FTP
 * delivery needs at least one active user holding the location in their
 * client-location data domain (the document receivers); effective date valid
 * (year ≥ 1900) and in the future; expiry valid and not before today; expiry
 * not before effective; the effective date is not inside an existing profile
 * of the location; the expiry is before the next profile's effective date.
 */
@Component
public class DeliveryProfileCreate implements WorkflowCommand {

  /** error.effectivedate.invalid5 */
  static final String EFFECTIVE_IN_EXISTING_RANGE = "Effective date cannot occur in an existing delivery profile range.";
  /** errors.statementdeliveryprofileclient.error */
  static final String NO_DOCUMENT_RECEIVERS =
      "There are no users with the document receiver role for this client location.";
  /** errors.activedocumentreceiveruser.error */
  static final String NO_ACTIVE_DOCUMENT_RECEIVERS =
      "There are no active users with the document receiver role for this client location.";
  /** ClientDeliveryProfile.DeliveryMethodCode.PRINT */
  static final String PRINT = "P";

  private final TableApi tableApi;
  private final ProfileReads reads;
  private final Audit audit;

  public DeliveryProfileCreate(TableApi tableApi, ProfileReads reads, Audit audit) {
    this.tableApi = tableApi;
    this.reads = reads;
    this.audit = audit;
  }

  @Override
  public String id() {
    return "profiles.delivery.create";
  }

  @Override
  public String legacy() {
    return "P373 / ClientProfileManagerBean.createAndStoreClientDeliveryProfile";
  }

  @Override
  public Capability capability() {
    return Capability.PROFILE_BILLING_ADMIN;
  }

  /**
   * The document-receiver part of {@code P372Action / P373Action.validateClientNumberAndLocation}:
   * electronic delivery needs a user with the location in their data domain
   * who is an HBS user with status ACT ({@code getHbsPresent()} and "ACT").
   */
  static void documentReceivers(ProfileRules.Problems problems, ProfileReads reads, String client, String loc,
      String method) {
    if (PRINT.equals(method)) return;
    if (reads.documentReceivers(client, loc) == 0) {
      problems.add("deliveryMethod", NO_DOCUMENT_RECEIVERS);
    } else if (reads.activeDocumentReceivers(client, loc) == 0) {
      problems.add("deliveryMethod", NO_ACTIVE_DOCUMENT_RECEIVERS);
    }
  }

  /** {@code validateEffectiveDate} of P372/P373. */
  static void effectiveDate(ProfileRules.Problems problems, LocalDate effective) {
    if (effective == null) {
      problems.add("effectiveDate", ProfileRules.EFFECTIVE_DATE_INVALID);
    } else if (effective.getYear() < ProfileRules.HBS_LOW_YEAR) {
      problems.add("effectiveDate", ProfileRules.YEAR_BEFORE_1900);
    }
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    String client = ProfileRules.padded(body, "clientNumber", 8);
    String loc = ProfileRules.padded(body, "clientLocnCode", 2);
    String method = ProfileRules.upper(body, "deliveryMethod");
    LocalDate effective = ProfileRules.date(body, "effectiveDate");
    LocalDate expiry = ProfileRules.date(body, "expiryDate");
    LocalDateTime now = audit.now().toLocalDateTime();

    ProfileRules.Problems problems = new ProfileRules.Problems();
    if (method == null) {
      problems.add("deliveryMethod", ProfileRules.required("Document Delivery Method"));
    }
    if (MarkHolderProfileCreate.clientLocation(problems, reads, client, loc) && method != null) {
      documentReceivers(problems, reads, client, loc, method);
    }
    effectiveDate(problems, effective);
    if (problems.isEmpty() && effective.atStartOfDay().isBefore(now)) {
      problems.add("effectiveDate", ProfileRules.EFFECTIVE_NOT_FUTURE);
    }
    if (expiry == null) {
      problems.add("expiryDate", ProfileRules.EXPIRY_DATE_INVALID);
    }
    if (problems.isEmpty() && expiry.atStartOfDay().isBefore(now)) {
      problems.add("expiryDate", ProfileRules.EXPIRY_BEFORE_TODAY);
    }
    if (problems.isEmpty() && effective.isAfter(expiry)) {
      problems.add("expiryDate", ProfileRules.EXPIRY_BEFORE_EFFECTIVE);
    }
    problems.throwIfAny();

    LocalDateTime eff = effective.atStartOfDay();
    LocalDateTime exp = expiry.atStartOfDay();
    for (Range r : reads.deliveryRanges(client, loc)) {
      if (ProfileRules.insideRange(r.start(), r.end(), eff)) {
        problems.add("effectiveDate", EFFECTIVE_IN_EXISTING_RANGE).throwIfAny();
      }
    }
    Next next = reads.nextDeliveryProfile(client, loc, eff);
    if (next != null && ProfileRules.reachesNext(exp, next.effective())) {
      problems.add("expiryDate", ProfileRules.RANGE_OVERLAPS_NEXT).throwIfAny();
    }

    long id = tableApi.nextId("HBS_CLI_DEL_PROFILE_SEQ");
    String user = audit.user();
    Timestamp stamp = audit.now();
    tableApi.call("HBS_CREATE_HBS_CLI_DEL_PROFILE", ProcArgs.of()
        .put("i_dlvprof_id", id)
        .put("i_client_number", client)
        .put("i_client_locn_code", loc)
        .put("i_hbs_delivery_method_code", method)
        .put("i_effective_date", eff)
        .put("i_expiry_date", exp)
        .put("i_entry_userid", user)
        .put("i_entry_timestamp", stamp)
        .put("i_update_userid", user)
        .put("i_update_timestamp", stamp));
    return Map.of("hbsCliDelProfileId", id, "dlvprofId", id);
  }
}
