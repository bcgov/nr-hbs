package ca.bc.gov.nrs.hbs.api.profiles;

import static ca.bc.gov.nrs.hbs.api.profiles.PopulationProfileRules.*;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowCommand;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * P333 Add Population Profile — validation.xml {@code P333Form},
 * {@code $V/presentation/cpm/P333Action.validateForm} and {@code hbsPerform},
 * then {@code ClientProfileManagerBean.createAndStorePopulationProfile}
 * ({@code HBS_CREATE_POPULATION_PROFILE}).
 *
 * <p>Ratio statement schedule: next = {@link PopulationProfileRules#firstNextRatioStmtDate}
 * (23:59:59), last = the effective date, start = today 00:00; expiry is
 * stored at 23:59:59.
 *
 * <p>One deliberate difference: the legacy action still inserted the profile
 * when the "expiry before next run" check failed (it only skipped the insert
 * on an overlap) and showed the error; here any failure rejects the add.
 */
@Component
public class PopulationProfileCreate implements WorkflowCommand {

  static final Set<String> FREQUENCIES = Set.of("MTH", "BWK", "DLY");

  private final TableApi tableApi;
  private final ProfileReads reads;
  private final ProfileCaller caller;
  private final Audit audit;

  public PopulationProfileCreate(TableApi tableApi, ProfileReads reads, ProfileCaller caller, Audit audit) {
    this.tableApi = tableApi;
    this.reads = reads;
    this.caller = caller;
    this.audit = audit;
  }

  @Override
  public String id() {
    return "profiles.population.create";
  }

  @Override
  public String legacy() {
    return "P333 / ClientProfileManagerBean.createAndStorePopulationProfile";
  }

  @Override
  public Capability capability() {
    return Capability.SAMPLING_ADMIN;
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    String population = ProfileRules.text(body, "populationNumber");
    String yearText = ProfileRules.text(body, "samplingYear");
    String frequency = ProfileRules.upper(body, "frequency");
    LocalDate effective = ProfileRules.date(body, "effectiveDate");
    LocalDate expiry = ProfileRules.date(body, "expiryDate");

    // validation.xml P333Form (Struts validator, before the action ran)
    ProfileRules.Problems problems = new ProfileRules.Problems();
    Long year = null;
    if (yearText == null) {
      problems.add("samplingYear", ProfileRules.required("Year"));
    } else if (!yearText.matches("\\d{1,9}")) {
      problems.add("samplingYear", notNumeric("Year"));
    } else {
      year = Long.valueOf(yearText);
    }
    Map<String, Object> pop = null;
    if (population == null) {
      problems.add("populationNumber", ProfileRules.required("Population"));
    } else if (!population.matches("\\d+")) {
      problems.add("populationNumber", notNumeric("Population"));
    } else if (year != null) {
      pop = reads.population(population, year);
      if (pop == null) {
        problems.add("populationNumber", POPULATION_YEAR_INVALID);
      }
    }
    dateField(problems, body, "effectiveDate", "Effective Date", effective);
    dateField(problems, body, "expiryDate", "Expiry Date", expiry);
    if (effective != null && expiry != null && effective.isAfter(expiry)) {
      problems.add("effectiveDate", EFFECTIVE_AFTER_EXPIRY);
    }
    if (frequency == null || !FREQUENCIES.contains(frequency)) {
      problems.add("frequency", ProfileRules.required("Frequency"));
    }
    problems.throwIfAny();

    // An industry SAMPLING_ADMIN (CLI_SMP_ADMIN) only profiles their own client's populations.
    caller.requireClient((String) pop.get("client_number"));

    // P333Action.validateForm
    if (!yearInRange(effective)) {
      problems.add("effectiveDate", EFFECTIVE_YEAR_INVALID);
    } else if (effective.getDayOfMonth() != 1) {
      problems.add("effectiveDate", EFFECTIVE_NOT_FIRST);
    }
    if (!yearInRange(expiry)) {
      problems.add("expiryDate", EXPIRY_BEFORE_NEXT_RUN);
    }
    Timestamp now = audit.now();
    if (now.toLocalDateTime().isAfter(effective.atStartOfDay())) {
      problems.add("effectiveDate", EFFECTIVE_BEFORE_CURRENT_MONTH);
    }
    if (!ProfileRules.isLastDayOfMonth(expiry)) {
      problems.add("expiryDate", EXPIRY_NOT_LAST);
    }
    problems.throwIfAny();

    // P333Action.hbsPerform
    LocalDateTime next = firstNextRatioStmtDate(effective, frequency);
    LocalDateTime expiryAt = expiryAt(expiry);
    if (next.isAfter(expiryAt)) {
      problems.add("expiryDate", EXPIRY_BEFORE_NEXT_RUN);
    }
    if (reads.overlappingPopulationProfiles(population, year, effective, expiry, null) > 0) {
      problems.add("effectiveDate", DATE_OVERLAPPING);
    }
    problems.throwIfAny();

    long id = tableApi.nextId("POPULATION_PROFILE_SEQ");
    String user = audit.user();
    tableApi.call("HBS_CREATE_POPULATION_PROFILE", ProcArgs.of()
        .put("i_popprof_id", id)
        .put("i_population_number", population)
        .put("i_sampling_year", year)
        .put("i_hbs_frequency_type_code", frequency)
        .put("i_next_ratio_stmt_date", next)
        .put("i_last_ratio_stmt_date", effective.atStartOfDay())
        .put("i_start_ratio_stmt_date", now.toLocalDateTime().toLocalDate().atStartOfDay())
        .put("i_effective_date", effective.atStartOfDay())
        .put("i_expiry_date", expiryAt)
        .put("i_entry_userid", user)
        .put("i_entry_timestamp", now)
        .put("i_update_userid", user)
        .put("i_update_timestamp", now));
    return Map.of("popprofId", id, "populationProfileId", id, "populationNumber", population);
  }

  /** validation.xml dateRequired + hbsDate. */
  static void dateField(ProfileRules.Problems problems, Map<String, Object> body, String name, String label,
      LocalDate parsed) {
    if (ProfileRules.text(body, name) == null) {
      problems.add(name, ProfileRules.required(label));
    } else if (parsed == null) {
      problems.add(name, notADate(label));
    }
  }
}
