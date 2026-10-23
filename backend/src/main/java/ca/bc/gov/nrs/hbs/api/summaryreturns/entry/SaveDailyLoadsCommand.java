package ca.bc.gov.nrs.hbs.api.summaryreturns.entry;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.summaryreturns.EditError;
import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryCaller;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException.Problem;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * P110 Update Daily Load Summaries: {@code $V/dac/summary/P110Action} ({@code setValidateLoadSummaries},
 * {@code validateLoadSummary}, {@code addUpdateDailyLoadSummaries}) over the 31-day grid of
 * {@code DailyLoadSummariesTag}, then the save path of {@code updateSummaryScaleReturnAndChildren} when
 * something changed.
 *
 * <p>Body {@code dailyLoads}: {@code [{day, loads, weight}]}, the full new grid; a day left out is
 * blank. Blank loads / weight count as 0 and must otherwise be whole numbers ≥ 0
 * ({@code error.loads.required}, {@code error.weight.required}). A day with loads and weight both
 * non-zero is stored (added or updated); any other day is deleted when it exists.
 */
@Component
public class SaveDailyLoadsCommand extends EntryCommand {

  public SaveDailyLoadsCommand(Deps deps) {
    super(deps);
  }

  @Override
  public String id() {
    return "summaryReturns.saveDailyLoads";
  }

  @Override
  public String legacy() {
    return "P110 Update Daily Load Summaries — P110Action.addUpdateDailyLoadSummaries";
  }

  @Override
  public Capability capability() {
    return Capability.SUMMARY_RETURN_EDIT;
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    SummaryCaller caller = caller();
    SummaryReturn ret = loadEditable(body, caller);
    if (ret.type() != SummaryReturnType.WEIGHT) {
      throw refuse(EntryMessages.DAILY_LOADS_NOT_AVAILABLE);
    }
    List<Map<String, Object>> rows = list(body, "dailyLoads");
    List<Problem> problems = new ArrayList<>();
    Map<Long, long[]> grid = new TreeMap<>();
    for (int i = 0; i < rows.size(); i++) {
      Map<String, Object> in = rows.get(i);
      String prefix = "dailyLoads[" + i + "].";
      Long day = whole(str(in.get("day")));
      if (day == null || day < 1 || day > EntryRules.DAYS_IN_PERIOD) {
        problems.add(new Problem(prefix + "day", EntryMessages.DAY_INVALID));
        continue;
      }
      if (grid.containsKey(day)) {
        problems.add(new Problem(prefix + "day", EntryMessages.DAY_DUPLICATE));
        continue;
      }
      Long loads = amount(str(in.get("loads")));
      Long weight = amount(str(in.get("weight")));
      if (weight == null || weight < 0) problems.add(new Problem(prefix + "weight", EntryMessages.WEIGHT_REQUIRED));
      if (loads == null || loads < 0) problems.add(new Problem(prefix + "loads", EntryMessages.LOADS_REQUIRED));
      if (loads != null && weight != null) grid.put(day, new long[] {loads, weight});
    }
    throwIfAny(problems);

    Map<Long, Row> stored = new HashMap<>();
    ret.dailyLoads().forEach(d -> stored.put(d.lng("day"), d));
    List<Row> result = new ArrayList<>();
    boolean changed = false;
    for (long day = 1; day <= EntryRules.DAYS_IN_PERIOD; day++) {
      long[] entry = grid.getOrDefault(day, new long[] {0, 0});
      Row old = stored.get(day);
      if (entry[0] != 0 && entry[1] != 0) {
        if (old != null) {
          if (!Objects.equals(old.lng("number_of_loads"), entry[0]) || !Objects.equals(old.lng("weight"), entry[1])) {
            old.set("number_of_loads", entry[0]).set("weight", entry[1]);
            changed = true;
          }
          result.add(old);
        } else {
          result.add(new Row().set("document_control_number", ret.dcn()).set("version", ret.versionNo())
              .set("day", day).set("number_of_loads", entry[0]).set("weight", entry[1]));
          changed = true;
        }
      } else if (old != null) {
        changed = true; // deleted
      }
    }
    // stored days outside 1..31 (the legacy tag refused to show those) are left as they are
    stored.values().stream().filter(d -> d.lng("day") == null || d.lng("day") < 1 || d.lng("day") > EntryRules.DAYS_IN_PERIOD)
        .forEach(result::add);
    if (!changed) {
      return saved(ret, false, List.of());
    }
    ret.dailyLoads().clear();
    ret.dailyLoads().addAll(result);
    List<EditError> errors = saveAndValidate(ret, true);
    return saved(ret, true, errors);
  }

  /** Blank is 0; otherwise {@code convertToLong} (a whole number, no decimals); unreadable is null. */
  private static Long amount(String s) {
    if (s == null) return 0L;
    try {
      return EntryRules.toLong(s);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static Long whole(String s) {
    if (s == null) return null;
    try {
      return new BigDecimal(s).longValueExact();
    } catch (NumberFormatException | ArithmeticException ex) {
      return null;
    }
  }
}
