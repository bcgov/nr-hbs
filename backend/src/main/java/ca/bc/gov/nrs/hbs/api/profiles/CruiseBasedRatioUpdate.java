package ca.bc.gov.nrs.hbs.api.profiles;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.RowLock;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowCommand;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * P383 grid row change — {@code P383Action.validateInputs} ({@link CruiseBasedRatioRules})
 * then {@code HBS_STORE_CB_SP_GRD_RATIO}. The legacy screen deleted and
 * re-inserted every row on Save; here only the m3/Ha of one row changes and
 * the row keeps its entry audit values.
 */
@Component
public class CruiseBasedRatioUpdate implements WorkflowCommand {

  private static final String STORE = "HBS_STORE_CB_SP_GRD_RATIO";

  private final TableApi tableApi;
  private final CruiseBasedRatioRules rules;
  private final Audit audit;

  public CruiseBasedRatioUpdate(TableApi tableApi, CruiseBasedRatioRules rules, Audit audit) {
    this.tableApi = tableApi;
    this.rules = rules;
    this.audit = audit;
  }

  @Override
  public String id() {
    return "profiles.cruiseBased.ratio.update";
  }

  @Override
  public String legacy() {
    return "P383 / CruiseBasedSpGrRatio store";
  }

  @Override
  public Capability capability() {
    return Capability.PROFILE_BILLING_ADMIN;
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    CruiseBasedRatioRules.Row r = rules.validate(body, false);
    Map<String, Object> row = rules.existing(r);
    RowLock.check(row.get("update_timestamp"), ProfileRules.lockValue(body));
    ProcArgs args = tableApi.argsFromRow(STORE, row)
        .put("i_volume_per_hectare", r.volume())
        .put("i_update_userid", audit.user())
        .put("i_update_timestamp", audit.now());
    tableApi.call(STORE, args);
    return Map.of("timberMark", r.timberMark(), "effectiveDate", r.effectiveDate().toString());
  }
}
