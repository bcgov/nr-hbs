package ca.bc.gov.nrs.hbs.api.summaryreturns.entry;

import ca.bc.gov.nrs.hbs.api.summaryreturns.EditError;
import ca.bc.gov.nrs.hbs.api.summaryreturns.ReleaseEdits;
import ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryCaller;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnAccess;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnCommand;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRepository;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnWriter;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException.Problem;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Shared plumbing of the data-entry commands ({@code summaryReturns.addPiece / addWeight / addSample /
 * update / saveSegregations / saveNotations / saveDailyLoads}).
 *
 * <p>The edit precondition every command on an existing return applies is the one the legacy
 * P052/P102/P152 page used to offer the update page (P053/P103/P153):
 * {@link SummaryReturnAccess#mayEdit} on a non-generated return. The legacy sub-grid pages
 * (P057/P157, P058/P109/P158, P110) checked neither role nor state; they get the same rule here.
 */
public abstract class EntryCommand extends SummaryReturnCommand {

  protected final EntryLookups lookups;
  protected final HeaderValidator validator;
  private final ObjectProvider<ReleaseEdits> edits;

  protected EntryCommand(Deps deps) {
    super(deps.repo, deps.writer, deps.access, deps.audit);
    this.lookups = deps.lookups;
    this.validator = deps.validator;
    this.edits = deps.edits;
  }

  /** The beans every entry command needs. */
  @Component("summaryReturnEntryDeps")
  public static class Deps {
    final SummaryReturnRepository repo;
    final SummaryReturnWriter writer;
    final SummaryReturnAccess access;
    final Audit audit;
    final EntryLookups lookups;
    final HeaderValidator validator;
    final ObjectProvider<ReleaseEdits> edits;

    public Deps(SummaryReturnRepository repo, SummaryReturnWriter writer, SummaryReturnAccess access, Audit audit,
        EntryLookups lookups, HeaderValidator validator, ObjectProvider<ReleaseEdits> edits) {
      this.repo = repo;
      this.writer = writer;
      this.access = access;
      this.audit = audit;
      this.lookups = lookups;
      this.validator = validator;
      this.edits = edits;
    }
  }

  /** Loads the active version and checks it may be edited by this caller. */
  protected SummaryReturn loadEditable(Map<String, Object> body, SummaryCaller caller) {
    SummaryReturn ret = loadForAction(body, caller);
    if (ret.generated()) {
      throw refuse(EntryMessages.GENERATED_NOT_EDITABLE);
    }
    if (ret.type() != SummaryReturnType.PIECE && !EntryRules.mayOpenWeightOrSampleUpdate(caller)) {
      throw new AccessDeniedException(EntryMessages.ACCESS_DISALLOWED); // P103/P153 grants
    }
    if (!access.mayEdit(caller, ret)) {
      throw refuse(EntryMessages.NOT_EDITABLE);
    }
    return ret;
  }

  /**
   * {@code SummaryReturnFieldsTag} SEGREGATION_LINK (BT17364): no segregations link on an OIP/RDY
   * version, nor on a piece NTI version.
   */
  protected static boolean segregationLinkShown(SummaryReturn ret) {
    String version = ret.versionState();
    if (ScaleReturnState.INVALID_STATE.equals(version)) return false;
    return !((ScaleReturnState.ORIGINAL_IN_PROGRESS.equals(ret.docState()) && ScaleReturnState.READY.equals(version))
        || (ScaleReturnState.NOT_TO_BE_BILLED.equals(version) && ret.type() == SummaryReturnType.PIECE));
  }

  /**
   * {@code SummaryScaleReturnStateHelper.updateSummaryScaleReturnAndChildren}: remove the old edit
   * errors, save the return and its children (audited when {@code doAudits}), run the release edits
   * on the saved return and store their errors. The state does not change, and the in-memory moves
   * the edits make are not saved (exactly as legacy).
   */
  protected List<EditError> saveAndValidate(SummaryReturn ret, boolean doAudits) {
    String user = audit.user();
    writer.removeErrors(ret);
    writer.saveReturnAndChildren(ret, user, doAudits);
    Object savedTimestamp = ret.version().get("update_timestamp");
    List<EditError> errors = edits.getObject().applyEditsAndValidate(ret);
    if (!errors.isEmpty()) {
      writer.saveErrors(ret, errors);
    }
    ret.version().set("update_timestamp", savedTimestamp);
    return errors;
  }

  /** The common response, plus what the SPA needs to keep editing. */
  protected Map<String, Object> saved(SummaryReturn ret, boolean changed, List<EditError> errors) {
    Map<String, Object> out = result(ret);
    out.put("updateTimestamp", ret.version().get("update_timestamp"));
    out.put("saved", changed);
    out.put("editErrors", errors.stream().map(e -> e.category() + " " + e.message()).toList());
    return out;
  }

  protected static void throwIfAny(List<Problem> problems) {
    if (!problems.isEmpty()) {
      throw new WorkflowValidationException(problems);
    }
  }

  protected static String str(Object raw) {
    if (raw == null) return null;
    String s = raw.toString().trim();
    return s.isEmpty() ? null : s;
  }

  protected static boolean yes(Object raw) {
    if (raw instanceof Boolean b) return b;
    return raw != null && "Y".equalsIgnoreCase(raw.toString().trim());
  }

  /** A sub-grid list from the body; a missing key is an empty list. */
  @SuppressWarnings("unchecked")
  protected static List<Map<String, Object>> list(Map<String, Object> body, String key) {
    Object raw = body.get(key);
    if (raw == null) return List.of();
    if (!(raw instanceof List<?> l)) {
      throw new WorkflowValidationException(key, "'" + key + "' must be a list");
    }
    for (Object o : l) {
      if (!(o instanceof Map)) throw new WorkflowValidationException(key, "'" + key + "' must be a list of objects");
    }
    return (List<Map<String, Object>>) raw;
  }
}
