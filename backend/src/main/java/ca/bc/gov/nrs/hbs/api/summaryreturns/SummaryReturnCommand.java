package ca.bc.gov.nrs.hbs.api.summaryreturns;

import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.RowLock;
import ca.bc.gov.nrs.hbs.api.workflow.StaleUpdateException;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowCommand;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared plumbing of the {@code summaryReturns.*} workflow commands.
 *
 * <p>Body keys every command on an existing return accepts: {@code documentControlNumber},
 * {@code version} (the version the user was looking at) and {@code updateTimestamp} (that
 * version's UPDATE_TIMESTAMP as loaded). The command reloads the active version
 * ({@code findScaleReturnWithActiveVersion}, as every legacy action did), refuses when the
 * user's version is no longer the active one or was changed since (the legacy
 * {@code StaleStateException} "Form state does not match new scale return state" →
 * {@code error.unable.tosave.doc}), and applies the industry fence.
 */
public abstract class SummaryReturnCommand implements WorkflowCommand {

  protected final SummaryReturnRepository repo;
  protected final SummaryReturnWriter writer;
  protected final SummaryReturnAccess access;
  protected final Audit audit;

  protected SummaryReturnCommand(SummaryReturnRepository repo, SummaryReturnWriter writer,
      SummaryReturnAccess access, Audit audit) {
    this.repo = repo;
    this.writer = writer;
    this.access = access;
    this.audit = audit;
  }

  protected SummaryCaller caller() {
    return SummaryCaller.current(audit.user());
  }

  /** The active version of the return the body points at, checked for staleness and ownership. */
  protected SummaryReturn loadForAction(Map<String, Object> body, SummaryCaller caller) {
    long dcn = requiredLong(body, "documentControlNumber");
    SummaryReturn ret = repo.loadActive(dcn);
    Long version = optionalLong(body, "version");
    if (version != null && version != ret.versionNo()) {
      throw new StaleUpdateException();
    }
    RowLock.check(ret.version().get("update_timestamp"), body.get("updateTimestamp"));
    access.requireVisible(caller, ret);
    return ret;
  }

  /** Refusal with a legacy-worded message (400). */
  protected static WorkflowValidationException refuse(String message) {
    return new WorkflowValidationException(null, message);
  }

  /** The response the SPA uses to land on the resulting active version. */
  protected Map<String, Object> result(SummaryReturn ret) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("documentControlNumber", ret.dcn());
    out.put("version", ret.versionNo());
    out.put("returnType", ret.type().code());
    out.put("returnStateCode", ret.docState());
    out.put("status", ret.versionState());
    return out;
  }

  public static long requiredLong(Map<String, Object> body, String key) {
    Long v = optionalLong(body, key);
    if (v == null) {
      throw new WorkflowValidationException(key, "'" + key + "' is required");
    }
    return v;
  }

  public static Long optionalLong(Map<String, Object> body, String key) {
    Object raw = body.get(key);
    if (raw == null || raw.toString().isBlank()) return null;
    try {
      return new java.math.BigDecimal(raw.toString().trim()).longValueExact();
    } catch (NumberFormatException | ArithmeticException ex) {
      throw new WorkflowValidationException(key, "'" + key + "' must be a whole number");
    }
  }

  public static String text(Map<String, Object> body, String key) {
    Object raw = body.get(key);
    if (raw == null) return null;
    String s = raw.toString().trim();
    return s.isEmpty() ? null : s;
  }
}
