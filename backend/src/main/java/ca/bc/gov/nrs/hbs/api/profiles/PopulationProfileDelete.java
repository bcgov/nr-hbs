package ca.bc.gov.nrs.hbs.api.profiles;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.RowLock;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowCommand;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * P332 Delete — {@code P332Action.hbsPerform} (ACTION_DELETE) then
 * {@code ClientProfileManagerBean.removePopulationProfile}
 * ({@code HBS_REMOVE_POPULATION_PROFILE}). The legacy action had no rule;
 * this adds the client fence (SAMPLING_ADMIN includes CLI_SMP_ADMIN) and the
 * optimistic lock.
 */
@Component
public class PopulationProfileDelete implements WorkflowCommand {

  private final TableApi tableApi;
  private final ProfileReads reads;
  private final ProfileCaller caller;

  public PopulationProfileDelete(TableApi tableApi, ProfileReads reads, ProfileCaller caller) {
    this.tableApi = tableApi;
    this.reads = reads;
    this.caller = caller;
  }

  @Override
  public String id() {
    return "profiles.population.delete";
  }

  @Override
  public String legacy() {
    return "P332 Delete / ClientProfileManagerBean.removePopulationProfile";
  }

  @Override
  public Capability capability() {
    return Capability.SAMPLING_ADMIN;
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    long id = ProfileRules.key(body, "popprofId");
    Map<String, Object> row = PopulationProfileUpdate.load(reads, caller, id);
    RowLock.check(row.get("update_timestamp"), ProfileRules.lockValue(body));
    tableApi.call("HBS_REMOVE_POPULATION_PROFILE", ProcArgs.of().put("i_popprof_id", id));
    return Map.of("popprofId", id);
  }
}
