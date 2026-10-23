package ca.bc.gov.nrs.hbs.api.profiles;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowCommand;
import java.sql.Timestamp;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * P383 grid row add — {@code P383Action.validateInputs} ({@link CruiseBasedRatioRules})
 * then {@code ClientProfileManagerBean.createAndStoreCruiseBasedSpGrRatio}
 * ({@code HBS_CREATE_CB_SP_GRD_RATIO}).
 */
@Component
public class CruiseBasedRatioCreate implements WorkflowCommand {

  private final TableApi tableApi;
  private final CruiseBasedRatioRules rules;
  private final Audit audit;

  public CruiseBasedRatioCreate(TableApi tableApi, CruiseBasedRatioRules rules, Audit audit) {
    this.tableApi = tableApi;
    this.rules = rules;
    this.audit = audit;
  }

  @Override
  public String id() {
    return "profiles.cruiseBased.ratio.create";
  }

  @Override
  public String legacy() {
    return "P383 / ClientProfileManagerBean.createAndStoreCruiseBasedSpGrRatio";
  }

  @Override
  public Capability capability() {
    return Capability.PROFILE_BILLING_ADMIN;
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    CruiseBasedRatioRules.Row r = rules.validate(body, true);
    String user = audit.user();
    Timestamp now = audit.now();
    tableApi.call("HBS_CREATE_CB_SP_GRD_RATIO", ProcArgs.of()
        .put("i_timber_mark", r.timberMark())
        .put("i_app_effective_date", r.effectiveDate())
        .put("i_scale_species_code", r.species())
        .put("i_scale_grade_code", r.grade())
        .put("i_volume_per_hectare", r.volume())
        .put("i_entry_userid", user)
        .put("i_entry_timestamp", now)
        .put("i_update_userid", user)
        .put("i_update_timestamp", now));
    return Map.of("timberMark", r.timberMark(), "effectiveDate", r.effectiveDate().toString());
  }
}
