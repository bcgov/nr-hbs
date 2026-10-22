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
 * P344 Confirm Mark Holder Profile Delete ("Yes") —
 * {@code $V/presentation/cpm/P344Action.perform} then
 * {@code ClientProfileManagerBean.removeMarkHolderProfile}
 * ({@code HBS_REMOVE_HBS_MARK_PROFILE}).
 *
 * <p>The legacy P342 page only offered Delete while the effective date was
 * after today ({@code P342Form.getIsFutureEffectiveDate}); that gate is now
 * enforced here, with the P342 message.
 */
@Component
public class MarkHolderProfileDelete implements WorkflowCommand {

  private final TableApi tableApi;
  private final ProfileReads reads;
  private final Audit audit;

  public MarkHolderProfileDelete(TableApi tableApi, ProfileReads reads, Audit audit) {
    this.tableApi = tableApi;
    this.reads = reads;
    this.audit = audit;
  }

  @Override
  public String id() {
    return "profiles.markHolders.delete";
  }

  @Override
  public String legacy() {
    return "P344 / ClientProfileManagerBean.removeMarkHolderProfile";
  }

  @Override
  public Capability capability() {
    return Capability.PROFILE_BILLING_ADMIN;
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    long id = ProfileRules.key(body, "mhprofId");
    Map<String, Object> row = MarkHolderProfileUpdate.load(reads, id);
    RowLock.check(row.get("update_timestamp"), ProfileRules.lockValue(body));
    if (!MarkHolderProfileUpdate.futureEffective(row, audit.now().toLocalDateTime())) {
      new ProfileRules.Problems().add("effectiveDate", ProfileRules.EFFECTIVE_NOT_FUTURE_FOR_UPDATE).throwIfAny();
    }
    tableApi.call("HBS_REMOVE_HBS_MARK_PROFILE", ProcArgs.of().put("i_mhprof_id", id));
    return Map.of("mhprofId", id);
  }
}
