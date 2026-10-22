package ca.bc.gov.nrs.hbs.api.profiles;

import static ca.bc.gov.nrs.hbs.api.profiles.PopulationProfileRules.*;

import ca.bc.gov.nrs.hbs.api.exception.EntityNotFoundException;
import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.RowLock;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowCommand;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * P332 Update Population Profile (Save) — validation.xml {@code P332Form},
 * {@code $V/presentation/cpm/P332Action.validateForm} and {@code hbsPerform}
 * (ACTION_SAVE), then {@code ClientProfileManagerBean.updatePopulationProfile}
 * ({@code HBS_STORE_POPULATION_PROFILE}).
 *
 * <p>Only the expiry date changes (stored at 23:59:59). The ratio schedule
 * (next / last / start ratio statement dates) and every other column are
 * re-sent from the current row with their time of day intact. Rules: expiry
 * year 2000–2099, not before the next ratio statement date, the last day of
 * its month, and no overlap with another profile of the population/year.
 */
@Component
public class PopulationProfileUpdate implements WorkflowCommand {

  private static final String STORE = "HBS_STORE_POPULATION_PROFILE";

  private final TableApi tableApi;
  private final ProfileReads reads;
  private final ProfileCaller caller;
  private final Audit audit;

  public PopulationProfileUpdate(TableApi tableApi, ProfileReads reads, ProfileCaller caller, Audit audit) {
    this.tableApi = tableApi;
    this.reads = reads;
    this.caller = caller;
    this.audit = audit;
  }

  @Override
  public String id() {
    return "profiles.population.update";
  }

  @Override
  public String legacy() {
    return "P332 Save / ClientProfileManagerBean.updatePopulationProfile";
  }

  @Override
  public Capability capability() {
    return Capability.SAMPLING_ADMIN;
  }

  static Map<String, Object> load(ProfileReads reads, ProfileCaller caller, long popprofId) {
    Map<String, Object> row = reads.populationProfile(popprofId);
    if (row == null) {
      throw new EntityNotFoundException(Map.class, "popprofId", String.valueOf(popprofId));
    }
    caller.requireClient((String) row.get("pop_client_number"));
    return row;
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    long id = ProfileRules.key(body, "popprofId");
    Map<String, Object> row = load(reads, caller, id);
    RowLock.check(row.get("update_timestamp"), ProfileRules.lockValue(body));

    LocalDate expiry = ProfileRules.date(body, "expiryDate");
    ProfileRules.Problems problems = new ProfileRules.Problems();
    PopulationProfileCreate.dateField(problems, body, "expiryDate", "Expiry Date", expiry);
    problems.throwIfAny();

    LocalDateTime expiryAt = expiryAt(expiry);
    if (!yearInRange(expiry)) {
      problems.add("expiryDate", EXPIRY_BEFORE_NEXT_RUN);
    }
    LocalDateTime next = ProfileRules.dateTime(row.get("next_ratio_stmt_date"));
    if (next != null && next.isAfter(expiryAt)) {
      problems.add("expiryDate", EXPIRY_BEFORE_NEXT_RUN);
    }
    if (!ProfileRules.isLastDayOfMonth(expiry)) {
      problems.add("expiryDate", EXPIRY_NOT_LAST);
    }
    problems.throwIfAny();

    LocalDateTime effective = ProfileRules.dateTime(row.get("effective_date"));
    String population = (String) row.get("population_number");
    long year = ((Number) row.get("sampling_year")).longValue();
    if (effective != null
        && reads.overlappingPopulationProfiles(population, year, effective.toLocalDate(), expiry, id) > 0) {
      problems.add("effectiveDateRange", DATE_OVERLAPPING).throwIfAny();
    }

    ProcArgs args = tableApi.argsFromRow(STORE, row)
        .put("i_expiry_date", expiryAt)
        .put("i_update_userid", audit.user())
        .put("i_update_timestamp", audit.now());
    tableApi.call(STORE, args);
    return Map.of("popprofId", id, "populationNumber", population);
  }
}
