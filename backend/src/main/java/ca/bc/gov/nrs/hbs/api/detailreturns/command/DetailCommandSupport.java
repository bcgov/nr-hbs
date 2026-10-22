package ca.bc.gov.nrs.hbs.api.detailreturns.command;

import ca.bc.gov.nrs.hbs.api.detailreturns.DetailAuthority;
import ca.bc.gov.nrs.hbs.api.detailreturns.DetailAuthority.Caller;
import ca.bc.gov.nrs.hbs.api.detailreturns.DetailPayload;
import ca.bc.gov.nrs.hbs.api.detailreturns.DetailWorkflow;
import ca.bc.gov.nrs.hbs.api.detailreturns.UserDomains;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.detailreturns.store.DetailStore;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.DetailFormValidator;
import ca.bc.gov.nrs.hbs.api.exception.EntityNotFoundException;
import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.RowLock;
import ca.bc.gov.nrs.hbs.api.workflow.StaleUpdateException;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException.Problem;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * What every detail-return command does around the workflow: load the version
 * the SPA shows, fence industry callers to their client's returns, check the
 * optimistic lock, check the button rule, run the workflow and turn the legacy
 * workflow/state exceptions into 400s with their legacy text.
 */
@Component
public class DetailCommandSupport {

  private final DetailWorkflow workflow;
  private final DetailAuthority authority;
  private final UserDomains domains;
  private final DetailFormValidator form;
  private final Audit audit;
  private Supplier<Caller> caller = Caller::current;
  private Supplier<String> client = () -> RequestUtil.isMinistryUser() ? "" : RequestUtil.getCurrentClientNumber();

  public DetailCommandSupport(DetailWorkflow workflow, DetailAuthority authority, UserDomains domains,
                              DetailFormValidator form, Audit audit) {
    this.workflow = workflow;
    this.authority = authority;
    this.domains = domains;
    this.form = form;
    this.audit = audit;
  }

  /** Tests: the caller and its active client ("" for ministry). */
  void identity(Supplier<Caller> caller, Supplier<String> client) {
    this.caller = caller;
    this.client = client;
  }

  public DetailWorkflow workflow() {
    return workflow;
  }

  public DetailStore store() {
    return workflow.store();
  }

  public DetailAuthority authority() {
    return authority;
  }

  public Caller caller() {
    return caller.get();
  }

  public String user() {
    return audit.user();
  }

  /**
   * The version named by {@code ddn}/{@code version}, fenced and lock-checked
   * against {@code updateTimestamp} (the version's UPDATE_TIMESTAMP the SPA loaded).
   */
  public DetailDocument load(Map<String, Object> body) {
    String ddn = required(body, "ddn").toUpperCase();
    long version = new BigDecimal(required(body, "version")).longValue();
    DetailDocument doc = store().find(ddn, version)
        .orElseThrow(() -> new EntityNotFoundException(DetailDocument.class, "ddn", ddn, "version", "" + version));
    fence(doc);
    try {
      RowLock.check(doc.version().row().get("update_timestamp"), body.get("updateTimestamp"));
    } catch (StaleUpdateException ex) {
      throw new StaleUpdateException(DetailStore.STALE_MESSAGE);
    }
    return doc;
  }

  /** Industry users act only on returns of their client (legacy view rule). */
  public void fence(DetailDocument doc) {
    String c = client.get();
    if (c == null || c.isEmpty()) {
      return;
    }
    Row v = doc.version().row();
    String licence = v.has("license_number") ? v.str("license_number") : v.str("primary_license_number");
    if (!domains.visibleToClient(c, v.str("scale_site_id_nmbr"), v.str("timber_mark"), v.str("population_number"),
        v.str("stratum_number"), v.lng("sampling_year"), licence)) {
      throw new AccessDeniedException("hbs.no_access_right: detail scale return " + doc.ddn()
          + " does not belong to your client");
    }
  }

  /** The legacy form checks; refuses the request with their messages. */
  @SuppressWarnings("unchecked")
  public void checkForm(DetailDocument doc, boolean adding, Map<String, Object> body) {
    Map<String, Object> fields = (Map<String, Object>) body.getOrDefault("fields", Map.of());
    List<Map<String, Object>> logs = (List<Map<String, Object>>) body.get("logs");
    List<Problem> problems = form.validate(doc.type(), adding, fields, logs == null ? List.of() : logs,
        adding ? DetailFormValidator.Context.NONE : formContext(doc));
    if (!problems.isEmpty()) {
      throw new WorkflowValidationException(problems);
    }
  }

  /**
   * The stored document's state and previous version (highest version below the
   * edited one, {@code DetailManagerQuery.selectPreviousVersion}) for the form checks.
   */
  DetailFormValidator.Context formContext(DetailDocument doc) {
    Long current = doc.version().number();
    DetailDocument previous = null;
    if (current != null) {
      for (DetailDocument d : store().versions(doc.ddn())) {
        Long n = d.version().number();
        if (n != null && n < current && (previous == null || n > previous.version().number())) {
          previous = d;
        }
      }
    }
    String state = doc.state() == null ? null : doc.state().value();
    if (previous == null) {
      return new DetailFormValidator.Context(state, null, null);
    }
    Row pv = previous.version().row();
    return new DetailFormValidator.Context(state, Boolean.TRUE.equals(pv.yes("check_replaces_original_ind")),
        pv.str("scl_rtn_version_state_code"));
  }

  /** Applies the SPA's field and log edits (Save, or Release/Re-Edit from the entry page). */
  @SuppressWarnings("unchecked")
  public boolean applyEdits(DetailDocument doc, Map<String, Object> body) {
    boolean edits = body.containsKey("fields") || body.containsKey("logs");
    if (!edits) {
      return false;
    }
    checkForm(doc, false, body);
    DetailPayload.applyFields(doc, (Map<String, Object>) body.get("fields"));
    DetailPayload.applyLogs(doc, (List<Map<String, Object>>) body.get("logs"));
    DetailPayload.defaultCheckboxes(doc);
    return true;
  }

  /** Runs a workflow step, turning legacy workflow/state failures into a 400 with the legacy text. */
  public void run(Runnable step) {
    try {
      step.run();
    } catch (IllegalStateException ex) {
      throw new WorkflowValidationException("state", ex.getMessage());
    }
  }

  /** The command's answer: the version the SPA should show next and its state. */
  public Map<String, Object> result(DetailDocument doc) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("ddn", doc.ddn());
    out.put("version", doc.version().number());
    out.put("documentState", doc.state().value());
    out.put("versionState", doc.version().state().value());
    out.put("errorCount", doc.errors().size());
    return out;
  }

  static String required(Map<String, Object> body, String key) {
    Object v = body.get(key);
    if (v == null || v.toString().isBlank()) {
      throw new WorkflowValidationException(key, "'" + key + "' is required");
    }
    return v.toString().trim();
  }
}
