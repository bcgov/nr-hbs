package ca.bc.gov.nrs.hbs.api.billing;

import ca.bc.gov.nrs.hbs.api.catalog.BillingCatalog;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The reads behind the billing workflows (plain SQL, bound parameters, no
 * schema prefix — the same synonyms the registry queries use). Each method
 * names the legacy query it replaces. Kept separate from the commands so the
 * commands can be unit-tested without a database.
 */
@Component
public class BillingReads {

  private final NamedParameterJdbcTemplate jdbc;

  public BillingReads(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private Map<String, Object> one(String sql, MapSqlParameterSource p) {
    List<Map<String, Object>> rows = jdbc.queryForList(sql, p);
    return rows.isEmpty() ? null : rows.get(0);
  }

  // ---- P811 / P813 mark holder profiles ------------------------------------------------------

  /** MarkHolderProfileEntity by PK (all columns, for HBS_STORE_HBS_MARK_PROFILE). */
  public Map<String, Object> markHolderProfile(long mhprofId) {
    return one("SELECT * FROM hbs_mark_holder_profile WHERE mhprof_id = :id",
        new MapSqlParameterSource("id", mhprofId));
  }

  /**
   * MarkHolderProfileQuery.selectByCriteria(clientNumber, clientLoc, returnType,
   * frequency, scaleDate): custom profiles (never mhprof_id 1 / 2) of the
   * return type and frequency whose effective..expiry range holds the force
   * date (compared at 00:00, as P811Form built it). Client / location only
   * when the "Client Location" option is chosen.
   */
  public List<Map<String, Object>> markHolderProfilesToForce(String returnType, String frequency,
      String clientNumber, String clientLocnCode, LocalDate forceDate) {
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("returnType", returnType)
        .addValue("frequency", frequency)
        .addValue("clientNumber", clientNumber, Types.VARCHAR)
        .addValue("clientLocnCode", clientLocnCode, Types.VARCHAR)
        .addValue("forceDate", Timestamp.valueOf(forceDate.atStartOfDay()));
    return jdbc.queryForList("""
        SELECT * FROM hbs_mark_holder_profile
         WHERE hbs_return_type_code = :returnType
           AND hbs_frequency_type_code = :frequency
           AND (:clientNumber IS NULL OR client_number = :clientNumber)
           AND (:clientLocnCode IS NULL OR client_locn_code = :clientLocnCode)
           AND :forceDate >= effective_date
           AND :forceDate <= expiry_date
           AND mhprof_id <> 1 AND mhprof_id <> 2
         ORDER BY mhprof_id""", p);
  }

  // ---- P823 population profiles --------------------------------------------------------------

  /** PopulationProfileEntity by PK (all columns, for HBS_STORE_POPULATION_PROFILE). */
  public Map<String, Object> populationProfile(long popprofId) {
    return one("SELECT * FROM population_profile WHERE popprof_id = :id",
        new MapSqlParameterSource("id", popprofId));
  }

  // ---- P835 / P836 non-renewable tenures -----------------------------------------------------

  /** SmpHelper.findPopulationsCountByCriteria(pop, null, null, null): the population exists in any year. */
  public boolean populationExists(String population) {
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM population WHERE population_number = :pop",
        new MapSqlParameterSource("pop", population), Integer.class);
    return n != null && n > 0;
  }

  /** ClientHelper.findPopulation / SmpHelper.findPopulationByPK. */
  public Map<String, Object> population(String population, long samplingYear) {
    return one("SELECT * FROM population WHERE population_number = :pop AND sampling_year = :year",
        new MapSqlParameterSource("pop", population).addValue("year", samplingYear));
  }

  /**
   * TimberMarkReader.getTimberMarkByMark (TimberMarkQuery.selectByPK): the mark
   * must be in TIMBER_MARK and MARK_BILLED_CLI; file type and quota type
   * decide {@code TimberMark.isRenewable()}.
   */
  public Map<String, Object> timberMark(String timberMark) {
    return one("""
        SELECT t.timber_mark, t.quota_type_code, b.file_type_code, b.client_number, b.client_locn_code
          FROM timber_mark t, mark_billed_cli b
         WHERE b.timber_mark = t.timber_mark
           AND t.timber_mark = :mark""", new MapSqlParameterSource("mark", timberMark));
  }

  /** CyclicBillingDAO.findByPopulationSamplingYearTimberMark: YTD cyclic billing exists (invoices/statements). */
  public boolean cyclicBillingExists(String population, long samplingYear, String timberMark) {
    Integer n = jdbc.queryForObject("""
        SELECT COUNT(*) FROM ytd_cyclic_billing
         WHERE population_number = :pop AND sampling_year = :year AND timber_mark = :mark""",
        new MapSqlParameterSource("pop", population).addValue("year", samplingYear).addValue("mark", timberMark),
        Integer.class);
    return n != null && n > 0;
  }

  /** FinalBillableDropoutMarkQuery.selectByPK. */
  public Map<String, Object> finalBillMark(String population, long samplingYear, String timberMark) {
    return one("""
        SELECT * FROM final_billable_dropout_mark
         WHERE population_number = :pop AND sampling_year = :year AND timber_mark = :mark""",
        new MapSqlParameterSource("pop", population).addValue("year", samplingYear).addValue("mark", timberMark));
  }

  // ---- P829 / P830 final bill generation -----------------------------------------------------

  /**
   * The P829 / P830 row (HBS3R829) for one population (stratum null) or one
   * stratum, re-read before generating: its {@code ELIGIBLE_PSYM_FLAG} is '*'
   * when the population/stratum may be final-billed now (the only rows that
   * offered a checkbox).
   */
  public Map<String, Object> finalBillStatus(long samplingYear, String population, String stratum) {
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("samplingYear", samplingYear)
        .addValue("population", population);
    if (stratum == null) {
      return one(BillingCatalog.finalBillStatusSql(false)
          + " AND fb.sampling_year = :samplingYear AND fb.population_number = :population", p);
    }
    p.addValue("stratum", stratum);
    return one(BillingCatalog.finalBillStatusSql(true)
        + " AND fb.sampling_year = :samplingYear AND fb.population_number = :population"
        + " AND fb.stratum_number = :stratum", p);
  }
}
