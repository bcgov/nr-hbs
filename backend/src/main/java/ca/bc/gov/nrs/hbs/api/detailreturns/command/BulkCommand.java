package ca.bc.gov.nrs.hbs.api.detailreturns.command;

import ca.bc.gov.nrs.hbs.api.detailreturns.DetailAuthority;
import ca.bc.gov.nrs.hbs.api.detailreturns.DetailAuthority.Caller;
import ca.bc.gov.nrs.hbs.api.detailreturns.FieldAvailability;
import ca.bc.gov.nrs.hbs.api.detailreturns.UserDomains;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.DocumentState;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.VersionState;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.PopStratYear;
import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.security.HbsRoles;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowCommand;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Bulk requests (P560A / P554B / P561 → {@code $V/dac/detail/P570Action}): one
 * action applied to the selected rows of a detail search, each row re-checked
 * for state and authority ({@code applyAuthorizationCheck}), optionally updated
 * (scale site / scale date / timber mark / pop-strat-year "from → to",
 * {@code doUpdate} + {@code setUpdateField}) and then saved or released
 * ({@code finalDispositionState}).
 *
 * <p>Legacy autocommitted each row, so one row's failure did not undo the
 * others. Here each row runs in a nested transaction (savepoint) inside the
 * command's transaction: a failed row is rolled back to its savepoint and
 * reported ERROR, the others commit together at the end.
 *
 * <p>Body: {@code {action, rows: [{ddn, version}], update: {field, from, to,
 * companyUse}, disposition: SAVE|SAVE_RELEASE}}; answer: {@code {results:
 * [{ddn, version, status: PROCESSED|NOTPROCESSED|ERROR, message}]}}
 * ({@code DetailScaleDocumentProcessStatus}).
 */
@Component
@Slf4j
public class BulkCommand implements WorkflowCommand {

  /** {@code ActionType.StateChange} values offered on P560A. */
  public enum Action {
    CLEAR_SIGNATURE_FAILED, DISCARD_RETURN, APPROVE_CHANGE, REJECT_CHANGE, HOLD, REEDIT, CANCEL,
    CANCEL_REPLACE_NO_CHANGES, CANCEL_REPLACE_CHANGES, REPLACE_NO_CHANGES, REPLACE_CHANGES, RELEASE, DISCARD,
    UPDATE_RETURN_DATA
  }

  /** {@code ActionType.Update}. */
  public enum UpdateField { NO_UPDATE, SCALE_SITE, SCALE_DATE, TIMBER_MARK, POP_STRAT_YEAR }

  record Update(UpdateField field, String from, String to, boolean companyUse) {
  }

  private static final Set<Action> WITH_UPDATE = Set.of(Action.HOLD, Action.CANCEL_REPLACE_CHANGES,
      Action.REPLACE_CHANGES, Action.UPDATE_RETURN_DATA);

  private final DetailCommandSupport s;
  private final UserDomains domains;
  private final TransactionTemplate nested;

  public BulkCommand(DetailCommandSupport s, UserDomains domains, ObjectProvider<PlatformTransactionManager> tx) {
    this.s = s;
    this.domains = domains;
    PlatformTransactionManager tm = tx.getIfAvailable();
    if (tm == null) {
      this.nested = null;
    } else {
      this.nested = new TransactionTemplate(tm);
      this.nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    }
  }

  @Override
  public String id() {
    return "detailReturns.bulk";
  }

  @Override
  public String legacy() {
    return "P560A/P554B/P561 Bulk Request → P570Action.performBulkAction";
  }

  @Override
  public Capability capability() {
    return Capability.DETAIL_RETURN_EDIT;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> execute(Map<String, Object> body) {
    Action action;
    try {
      action = Action.valueOf(DetailCommandSupport.required(body, "action"));
    } catch (IllegalArgumentException ex) {
      throw new WorkflowValidationException("action", "Unknown bulk action " + body.get("action"));
    }
    Map<String, Object> u = (Map<String, Object>) body.getOrDefault("update", Map.of());
    Update update = new Update(
        UpdateField.valueOf(String.valueOf(u.getOrDefault("field", "NO_UPDATE"))),
        u.get("from") == null ? null : u.get("from").toString().trim().toUpperCase(),
        u.get("to") == null ? null : u.get("to").toString().trim().toUpperCase(),
        Boolean.TRUE.equals(u.get("companyUse")) || "Y".equals(u.get("companyUse")));
    boolean release = "SAVE_RELEASE".equals(body.get("disposition"));
    List<Map<String, Object>> rows = (List<Map<String, Object>>) body.getOrDefault("rows", List.of());
    Caller caller = s.caller();
    List<Map<String, Object>> results = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("ddn", row.get("ddn"));
      result.put("version", row.get("version"));
      try {
        String status = inRowTransaction(() -> processRow(action, update, release, row, caller));
        result.put("status", status);
      } catch (RuntimeException ex) {
        log.info("Bulk {} on {} failed: {}", action, row, ex.getMessage());
        result.put("status", "ERROR");
        result.put("message", ex.getMessage());
      }
      results.add(result);
    }
    return Map.of("results", results);
  }

  private String inRowTransaction(java.util.function.Supplier<String> work) {
    return nested == null ? work.get() : nested.execute(st -> work.get());
  }

  String processRow(Action action, Update update, boolean release, Map<String, Object> row, Caller caller) {
    DetailDocument doc = s.load(row);
    if (!qualifiedAndAuthorized(action, update, doc, caller)) {
      return "NOTPROCESSED";
    }
    String user = s.user();
    VersionState v = doc.version().state();
    switch (action) {
      case CLEAR_SIGNATURE_FAILED -> {
        if (!(v.isSignatureFailed() && v.isActive())) return "NOTPROCESSED";
        s.run(() -> s.workflow().clearSignatureFailure(doc, user));
      }
      case DISCARD_RETURN, DISCARD -> {
        if (!(v.isDiscardAllowed() && v.isActive())) return "NOTPROCESSED";
        s.run(() -> s.workflow().discard(doc, user));
      }
      case APPROVE_CHANGE -> {
        if (!(v.isAwaitingApproval() && v.isActive())) return "NOTPROCESSED";
        s.run(() -> s.workflow().approve(doc, user));
      }
      case REJECT_CHANGE -> {
        if (!(v.isAwaitingApproval() && v.isActive())) return "NOTPROCESSED";
        s.run(() -> s.workflow().reject(doc, user));
      }
      case HOLD -> {
        if (!((v.isHoldAllowed() || v.isNotToBeBilled()) && v.isActive())) return "NOTPROCESSED";
        if (!doUpdate(doc, update)) return "NOTPROCESSED";
        s.run(() -> finish(s.workflow().hold(doc, user), update, release, user));
      }
      case CANCEL -> {
        if (!(v.isCancelAllowed() && v.isActive())) return "NOTPROCESSED";
        s.run(() -> s.workflow().cancel(doc, user));
      }
      case REPLACE_NO_CHANGES -> {
        if (!(v.isReplaceWithNoChangedAllowed() && v.isActive())) return "NOTPROCESSED";
        s.run(() -> s.workflow().replaceWithNoChanges(doc, user));
      }
      case CANCEL_REPLACE_NO_CHANGES -> {
        if (!(v.isCancelAllowed() && v.isActive())) return "NOTPROCESSED";
        s.run(() -> s.workflow().replaceWithNoChanges(doc, user));
      }
      case CANCEL_REPLACE_CHANGES -> {
        if (!(v.isCancelAllowed() && v.isActive())) return "NOTPROCESSED";
        if (!doUpdate(doc, update)) return "NOTPROCESSED";
        s.run(() -> finish(s.workflow().replaceWithChanges(doc, user), update, release, user));
      }
      case REPLACE_CHANGES -> {
        if (!(doc.state().isCancelComplete() && v.isActive())) return "NOTPROCESSED";
        if (!doUpdate(doc, update)) return "NOTPROCESSED";
        s.run(() -> finish(s.workflow().replaceWithChanges(doc, user), update, release, user));
      }
      case UPDATE_RETURN_DATA -> {
        if (!((v.isHeld() || v.isIncomplete()) && v.isActive())) return "NOTPROCESSED";
        if (!doUpdate(doc, update)) return "NOTPROCESSED";
        s.run(() -> finish(doc, update, release, user));
      }
      case RELEASE -> {
        if (!((v.isHeld() || v.isIncomplete()) && v.isActive())) return "NOTPROCESSED";
        s.run(() -> s.workflow().release(doc, user));
      }
      case REEDIT -> {
        if (!((v.isHoldAllowed() && v.isActive()) || v.isNotToBeBilled())) return "NOTPROCESSED";
        s.run(() -> s.workflow().release(doc, user));
      }
    }
    return "PROCESSED";
  }

  /** {@code setUpdateField} then Save, or Release when "Save and Release" was chosen. */
  private void finish(DetailDocument doc, Update update, boolean release, String user) {
    setUpdateField(doc, update);
    if (release) {
      s.workflow().release(doc, user);
    } else {
      s.workflow().save(doc, user);
    }
  }

  /** {@code applyAuthorizationCheck}: the row's state qualifies and the caller may act on it. */
  boolean qualifiedAndAuthorized(Action action, Update update, DetailDocument doc, Caller caller) {
    VersionState v = new VersionState(doc.version().state().value(), true); // the legacy row state is "active"
    DocumentState d = doc.state();
    Row r = doc.version().row();
    DetailAuthority a = s.authority();
    String site = r.str("scale_site_id_nmbr");
    String mark = r.empty("timber_mark") ? r.str("input_source") : r.str("timber_mark");
    String primary = doc.type() == DocType.ARRIVAL_LEDGER ? r.str("license_number") : r.str("primary_license_number");
    String original = doc.type() == DocType.LOG_TALLY || doc.type() == DocType.SAMPLE_LOG_TALLY
        ? r.str("orig_chk_scl_license_number") : null;
    boolean siteOrScaler = a.hasScaleSiteAuthorization(site, caller)
        || a.hasScalerAuthorization(caller, primary, r.str("secondary_license_number"),
            r.str("signing_license_number"), original);
    boolean qualified;
    boolean authorized;
    switch (action) {
      case CLEAR_SIGNATURE_FAILED -> {
        qualified = v.isSignatureFailed();
        authorized = siteOrScaler && caller.has(HbsRoles.SCALE_ADMIN);
      }
      case DISCARD_RETURN -> {
        qualified = v.isSignatureFailed();
        authorized = siteOrScaler;
      }
      case APPROVE_CHANGE, REJECT_CHANGE -> {
        qualified = v.isAwaitingApproval();
        authorized = a.hasDistrictAuthorization(mark, caller) && caller.has(HbsRoles.INV_CORR_APP);
      }
      case HOLD -> {
        qualified = v.isHoldAllowed() || v.isNotToBeBilled();
        authorized = siteOrScaler;
      }
      case REEDIT -> {
        qualified = v.isHoldAllowed() || v.isNotToBeBilled();
        authorized = siteOrScaler;
      }
      case CANCEL, CANCEL_REPLACE_NO_CHANGES -> {
        qualified = v.isCancelAllowed();
        authorized = siteOrScaler;
      }
      case CANCEL_REPLACE_CHANGES -> {
        qualified = v.isCancelAllowed()
            && (d.isOriginalComplete() || d.isReplaceWithChangesComplete() || d.isReplaceWithNoChangesComplete());
        authorized = siteOrScaler;
      }
      case REPLACE_NO_CHANGES -> {
        qualified = v.isReplaceWithNoChangedAllowed();
        authorized = siteOrScaler;
      }
      case REPLACE_CHANGES -> {
        qualified = d.isCancelComplete();
        authorized = siteOrScaler;
      }
      case RELEASE, DISCARD, UPDATE_RETURN_DATA -> {
        qualified = v.isHeld() || v.isIncomplete();
        authorized = siteOrScaler;
      }
      default -> throw new IllegalStateException(action.name());
    }
    if (qualified && authorized && (WITH_UPDATE.contains(action) || action == Action.REPLACE_NO_CHANGES)) {
      authorized = updateAuthorized(update, doc, caller);
    }
    return qualified && authorized;
  }

  /** {@code doUpdateAction}: scale admin/scaler pairs may always; otherwise "from" must match and site authority. */
  boolean updateAuthorized(Update update, DetailDocument doc, Caller caller) {
    if ((caller.has(HbsRoles.CLI_SITE_ADMIN) && caller.has(HbsRoles.CLI_SCALER))
        || (caller.has(HbsRoles.SCALE_ADMIN) && caller.has(HbsRoles.MOF_SCALER))) {
      return true;
    }
    Row r = doc.version().row();
    boolean siteAuth = s.authority().hasScaleSiteAuthorization(r.str("scale_site_id_nmbr"), caller);
    return switch (update.field()) {
      case SCALE_DATE -> eq(update.from(), yyyymmdd(r.date("scale_date"))) && siteAuth;
      case SCALE_SITE -> eq(update.from(), r.str("scale_site_id_nmbr")) && siteAuth;
      case TIMBER_MARK -> eq(update.from(), r.empty("timber_mark") ? r.str("input_source") : r.str("timber_mark"))
          && siteAuth;
      case POP_STRAT_YEAR -> eq(stripDelimiters(update.from()), currentPsy(r)) && siteAuth;
      case NO_UPDATE -> siteAuth;
    };
  }

  /** {@code doUpdate}: the update applies to this row (and the target site has a detail profile). */
  boolean doUpdate(DetailDocument doc, Update update) {
    Row r = doc.version().row();
    return switch (update.field()) {
      case NO_UPDATE -> true;
      case SCALE_SITE -> eq(update.from(), r.str("scale_site_id_nmbr"))
          && domains.hasDetailSiteProfile(update.to(), doc.type().code(), r.date("scale_date"));
      case SCALE_DATE -> eq(update.from(), yyyymmdd(r.date("scale_date")))
          && domains.hasDetailSiteProfile(r.str("scale_site_id_nmbr"), doc.type().code(), toDate(update.to()));
      case TIMBER_MARK -> timberMarkAvailable(doc) && (r.empty("timber_mark")
          ? r.str("input_source") != null && update.from().equals(r.str("input_source"))
          : update.from().equals(r.str("timber_mark")));
      case POP_STRAT_YEAR -> popStratYearAllowed(doc, update.from());
    };
  }

  /** {@code isPopStratYearUpdateAllowedForWeighSlip / ForSampleLog}. */
  private boolean popStratYearAllowed(DetailDocument doc, String from) {
    if (doc.type() != DocType.WEIGH_SLIP && doc.type() != DocType.SAMPLE_LOG_TALLY) return false;
    Row r = doc.version().row();
    String population = currentPsy(r);
    String inputPsy = r.str("input_psy");
    if (doc.type() == DocType.SAMPLE_LOG_TALLY) {
      if (population.isEmpty() && inputPsy != null && inputPsy.equals(psyShort(from))) return true;
      return population.equals(psyLong(from));
    }
    String companyStratum = r.str("company_use_stratum");
    if (population.isEmpty() && (companyStratum == null || companyStratum.isEmpty())
        && inputPsy != null && inputPsy.equals(psyShort(from))) {
      return true;
    }
    if (population.isEmpty() && companyStratum != null && !companyStratum.isEmpty()
        && companyStratum.equals(psyShort(from))) {
      return true;
    }
    return billableWeighSlip(doc) && population.equals(psyLong(from));
  }

  /** {@code P570Action.isBillableScaleReturn}: billable event, billable mark, not field scaled, a population. */
  private boolean billableWeighSlip(DetailDocument doc) {
    Row r = doc.version().row();
    String e = r.str("scale_event_type_code");
    boolean billableEvent = Set.of("PS", "SS", "RS", "PD").contains(e);
    return billableEvent && !r.empty("timber_mark") && s.workflow().isBillableMark(r.str("timber_mark"))
        && !Boolean.TRUE.equals(r.yes("field_scale_ind")) && r.get("population_number") != null;
  }

  private static boolean timberMarkAvailable(DetailDocument doc) {
    String matrix = switch (doc.type()) {
      case LOG_TALLY -> "LOG_TALLY";
      case WEIGH_SLIP -> "WEIGH_SLIP";
      case SAMPLE_LOG_TALLY -> "SAMPLE_LOG_TALLY";
      case SFP_TALLY -> "SFP_TALLY";
      default -> "LEDGER";
    };
    return FieldAvailability.isAvailable(matrix, doc.version().row().str("scale_event_type_code"), "TIMBER_MARK");
  }

  /** {@code setUpdateField}. */
  void setUpdateField(DetailDocument doc, Update update) {
    Row r = doc.version().row();
    switch (update.field()) {
      case SCALE_SITE -> r.set("scale_site_id_nmbr", update.to());
      case SCALE_DATE -> r.set("scale_date", toDate(update.to()));
      case TIMBER_MARK -> r.set("timber_mark", update.to());
      case POP_STRAT_YEAR -> {
        if (doc.type() == DocType.WEIGH_SLIP && update.companyUse()) {
          r.set("company_use_stratum", update.to());
          r.set("population_number", null);
          r.set("stratum_number", null);
          r.set("sampling_year", null);
        } else {
          PopStratYear p = PopStratYear.parse(update.to());
          r.set("population_number", p.population());
          r.set("stratum_number", p.stratum());
          r.set("sampling_year", p.yearOrNull());
          if (doc.type() == DocType.WEIGH_SLIP) {
            r.set("company_use_stratum", null);
          }
        }
      }
      case NO_UPDATE -> { }
    }
  }

  private static String currentPsy(Row r) {
    return nz(r.str("population_number")) + nz(r.str("stratum_number"))
        + (r.lng("sampling_year") == null ? "" : r.lng("sampling_year").toString());
  }

  /** {@code psyInLongFormat}: population + stratum + four-digit year. */
  static String psyLong(String psy) {
    if (psy == null) return "";
    PopStratYear p = PopStratYear.parse(psy);
    Long year = p.yearOrNull();
    return nz(p.population()) + nz(p.stratum()) + (year == null ? "" : year.toString());
  }

  /** {@code psyInShortFormat}: population + stratum + the year as typed (2, 1 digits, or last 2 of 4). */
  static String psyShort(String psy) {
    if (psy == null) return "";
    PopStratYear p = PopStratYear.parse(psy);
    String year = switch (psy.length()) {
      case 8 -> p.twoDigitYear();
      case 7 -> p.oneDigitYear();
      case 10 -> psy.substring(psy.length() - 2);
      default -> "";
    };
    return nz(p.population()) + nz(p.stratum()) + nz(year);
  }

  private static String stripDelimiters(String psy) {
    return psy == null ? null : psy.replaceAll("[^A-Za-z0-9]", "");
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }

  private static boolean eq(String a, String b) {
    return a != null && a.equals(b);
  }

  private static String yyyymmdd(java.util.Date d) {
    return d == null ? null : new Timestamp(d.getTime()).toLocalDateTime().toLocalDate()
        .format(DateTimeFormatter.BASIC_ISO_DATE);
  }

  /** The P560A dates are yyyyMMdd ({@code DateUtil.DATE_FORMAT_NO_TIME}); ISO is accepted too. */
  static Timestamp toDate(String s) {
    if (s == null || s.isBlank()) return null;
    String t = s.trim();
    LocalDate d = t.contains("-") ? LocalDate.parse(t) : LocalDate.parse(t, DateTimeFormatter.BASIC_ISO_DATE);
    return Timestamp.valueOf(d.atStartOfDay());
  }

  @SuppressWarnings("unused")
  private static Long lng(Object o) {
    return o == null ? null : new BigDecimal(o.toString()).longValue();
  }
}
