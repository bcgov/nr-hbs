package ca.bc.gov.nrs.hbs.api.summaryreturns.entry;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.summaryreturns.EditError;
import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryCaller;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException.Problem;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * P058 / P109 / P158 Update Notations: {@code $V/dac/summary/P058Action}, {@code P109Action},
 * {@code P158Action} ({@code validateNotations}, {@code setNotations}, {@code addUpdateNotations},
 * {@code addNotation}), then the save path of {@code updateSummaryScaleReturnAndChildren} — only
 * when something changed, as the actions checked {@code getScaleReturnEdits().length > 0}.
 *
 * <p>Body {@code notations}: the full new list, {@code [{ntnId?, notationNumber?, text}]}. A stored
 * notation is matched by {@code ntnId} (or, when no row carries one, by its notation number as the
 * legacy page did); a
 * stored notation missing from the list is deleted. A kept notation needs text
 * ({@code error.notation.invalid}); a new row with blank text is ignored. New notations are type NOT
 * and numbered after the highest remaining number ({@code getNextNotationNumber}).
 *
 * <p>Legacy defect not kept: P109 (weight) and P158 (sample) applied the text edits and deletes to
 * the session copy instead of the re-read return (BT-7980 was fixed for piece only), so only an added
 * notation was saved there. One grid now behaves as P058 for every type.
 */
@Component
public class SaveNotationsCommand extends EntryCommand {

  public SaveNotationsCommand(Deps deps) {
    super(deps);
  }

  @Override
  public String id() {
    return "summaryReturns.saveNotations";
  }

  @Override
  public String legacy() {
    return "P058/P109/P158 Update Notations — P058Action / P109Action / P158Action";
  }

  @Override
  public Capability capability() {
    return Capability.SUMMARY_RETURN_EDIT;
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    SummaryCaller caller = caller();
    SummaryReturn ret = loadEditable(body, caller);
    List<Map<String, Object>> rows = list(body, "notations");
    Map<Long, Row> stored = new LinkedHashMap<>();
    ret.notations().forEach(n -> stored.put(n.lng("ntn_id"), n));
    List<Problem> problems = new ArrayList<>();
    List<Row> kept = new ArrayList<>();
    List<String> newTexts = new ArrayList<>();
    boolean changed = false;
    // a client that sends ntnIds is matched by them only; one that sends none by notation number
    boolean byNumber = rows.stream().noneMatch(r -> str(r.get("ntnId")) != null);
    for (int i = 0; i < rows.size(); i++) {
      Map<String, Object> in = rows.get(i);
      String prefix = "notations[" + i + "].";
      String text = str(in.get("text"));
      Row note = match(stored, in, byNumber);
      if (note == null && (str(in.get("ntnId")) != null)) {
        problems.add(new Problem(prefix + "ntnId", EntryMessages.UNKNOWN_NOTATION));
        continue;
      }
      if (note == null) {
        if (text != null) newTexts.add(text);
        continue;
      }
      stored.remove(note.lng("ntn_id"));
      if (text == null) {
        problems.add(new Problem(prefix + "text", EntryMessages.NOTATION_EMPTY));
        continue;
      }
      if (!Objects.equals(note.str("text"), text)) {
        note.set("text", text);
        changed = true;
      }
      kept.add(note);
    }
    throwIfAny(problems);

    changed |= !stored.isEmpty() || !newTexts.isEmpty();
    long next = kept.stream().mapToLong(n -> n.lng("notation_number") == null ? 0 : n.lng("notation_number"))
        .max().orElse(0) + 1;
    List<Row> notations = new ArrayList<>(kept);
    for (String text : newTexts) {
      notations.add(new Row().set("ntn_id", null).set("notation_type_code", EntryRules.NOTATION_TYPE)
          .set("version", ret.versionNo()).set("document_control_number", ret.dcn())
          .set("notation_number", next++).set("text", text));
    }
    if (!changed) {
      return saved(ret, false, List.of());
    }
    ret.notations().clear();
    ret.notations().addAll(notations);
    List<EditError> errors = saveAndValidate(ret, true);
    return saved(ret, true, errors);
  }

  /** The stored notation a row refers to: by {@code ntnId}, else by {@code notationNumber} (P058 note_N). */
  private static Row match(Map<Long, Row> stored, Map<String, Object> in, boolean byNumber) {
    Long id = number(in.get("ntnId"));
    if (id != null) return stored.get(id);
    if (str(in.get("ntnId")) != null || !byNumber) return null;
    Long number = number(in.get("notationNumber"));
    if (number == null) return null;
    return stored.values().stream().filter(n -> number.equals(n.lng("notation_number"))).findFirst().orElse(null);
  }

  private static Long number(Object raw) {
    String s = str(raw);
    if (s == null) return null;
    try {
      return new BigDecimal(s).longValueExact();
    } catch (NumberFormatException | ArithmeticException ex) {
      return null;
    }
  }
}
