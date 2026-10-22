package ca.bc.gov.nrs.hbs.api.sampling;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Oracle reads for the Stratum Planner workflows. SQL ported from the legacy
 * entity queries ({@code $E/smp/SamplePlanQuery}, {@code StratumPlanQuery},
 * {@code StratumPlanCompositionQuery}, {@code StratumQuery},
 * {@code $E/vrc/RatioHistoryQuery}, {@code ScaleGradeDAO},
 * {@code SpeciesProductGradeQuery}) and from the read-only procs
 * HBS_STRANDED_SAMPLE_SCALE / _SAMPLE_LOG / _WEIGH_SCALE / _WEIGHSLIPS and
 * HBS_UNUSED_STRATA_COUNT, whose SELECTs are reproduced here with bound
 * parameters (the procs' multi-stratum mode concatenates its inputs into a
 * REF CURSOR query). No schema prefix, as the registry queries.
 */
@Component
public class JdbcSamplingReads implements SamplingReads {

  /** Open (not yet billed / rejected) document states the HBS_STRANDED_* procs count. */
  private static final String OPEN_STATES = "('RDY', 'AWP', 'LCK', 'ISS', 'NTI', 'PND', 'PNV')";

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcSamplingReads(JdbcTemplate jdbc) {
    this.jdbc = new NamedParameterJdbcTemplate(jdbc);
  }

  @Override
  public SamplePlanRow plan(long planId) {
    List<SamplePlanRow> rows = jdbc.query("SELECT * FROM sample_plan WHERE plan_id = :id",
        new MapSqlParameterSource("id", planId), PLAN);
    return rows.isEmpty() ? null : rows.get(0);
  }

  @Override
  public List<StratumPlanRow> stratumPlans(long planId) {
    return jdbc.query("SELECT * FROM stratum_plan WHERE plan_id = :id ORDER BY stratum_number",
        new MapSqlParameterSource("id", planId), STRATUM_PLAN);
  }

  @Override
  public StratumPlanRow stratumPlan(long stratumPlanId) {
    List<StratumPlanRow> rows = jdbc.query("SELECT * FROM stratum_plan WHERE stratum_plan_id = :id",
        new MapSqlParameterSource("id", stratumPlanId), STRATUM_PLAN);
    return rows.isEmpty() ? null : rows.get(0);
  }

  @Override
  public List<Map<String, Object>> compositions(long stratumPlanId) {
    return jdbc.query("SELECT * FROM stratum_plan_composition WHERE stratum_plan_id = :id"
            + " ORDER BY scale_species_code, scale_grade_code",
        new MapSqlParameterSource("id", stratumPlanId), JdbcSamplingReads::lowerRow);
  }

  @Override
  public Map<String, Object> population(String populationNumber, long samplingYear) {
    List<Map<String, Object>> rows = jdbc.query(
        "SELECT * FROM population WHERE population_number = :pop AND sampling_year = :year",
        new MapSqlParameterSource("pop", populationNumber).addValue("year", samplingYear),
        JdbcSamplingReads::lowerRow);
    return rows.isEmpty() ? null : rows.get(0);
  }

  @Override
  public List<SamplePlanRow> plans(String status, String populationNumber, long samplingYear,
      String planAbbreviation, Boolean acceptedPlan) {
    var p = new MapSqlParameterSource("pop", populationNumber).addValue("year", samplingYear)
        .addValue("status", blankToNull(status), Types.VARCHAR)
        .addValue("abbr", blankToNull(planAbbreviation), Types.VARCHAR)
        .addValue("accepted", acceptedPlan == null ? null : (acceptedPlan ? "Y" : "N"), Types.VARCHAR);
    return jdbc.query("""
        SELECT * FROM sample_plan
         WHERE population_number = :pop
           AND sampling_year = :year
           AND (:status IS NULL OR sample_plan_status_code = :status)
           AND (:abbr IS NULL OR plan_abreviation = :abbr)
           AND (:accepted IS NULL OR accepted_plan_ind = :accepted)
         ORDER BY plan_id""", p, PLAN);
  }

  @Override
  public List<Map<String, Object>> strata(String populationNumber, long samplingYear) {
    return jdbc.query("SELECT * FROM stratum WHERE population_number = :pop AND sampling_year = :year"
            + " ORDER BY stratum_number",
        new MapSqlParameterSource("pop", populationNumber).addValue("year", samplingYear),
        JdbcSamplingReads::lowerRow);
  }

  @Override
  public boolean userHasDistrict(String userId, long orgUnitNo) {
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM hbs_user_dist_data_domain"
            + " WHERE UPPER(user_id) = UPPER(:userId) AND org_unit_no = :org",
        new MapSqlParameterSource("userId", userId).addValue("org", orgUnitNo), Integer.class);
    return n != null && n > 0;
  }

  @Override
  public OrgUnit orgUnit(long orgUnitNo) {
    List<OrgUnit> rows = jdbc.query(
        "SELECT org_unit_no, org_unit_code, rollup_region_code FROM org_unit WHERE org_unit_no = :org",
        new MapSqlParameterSource("org", orgUnitNo),
        (rs, n) -> new OrgUnit(rs.getLong(1), rs.getString(2), rs.getString(3)));
    return rows.isEmpty() ? null : rows.get(0);
  }

  @Override
  public boolean clientLocationExists(String clientNumber, String clientLocnCode) {
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM client_location"
            + " WHERE client_number = :client AND client_locn_code = :locn",
        new MapSqlParameterSource("client", clientNumber).addValue("locn", clientLocnCode), Integer.class);
    return n != null && n > 0;
  }

  @Override
  public Dates speciesProductGrade(String species, String product, String grade) {
    return dates("SELECT effective_date, expiry_date FROM species_product_grade_xref"
            + " WHERE scale_product_code = :product AND scale_grade_code = :grade AND scale_species_code = :species",
        new MapSqlParameterSource("product", product).addValue("grade", grade).addValue("species", species));
  }

  @Override
  public Dates scheduleGrade(String grade, String gradeSchedule) {
    String sql;
    if ("C".equalsIgnoreCase(trim(gradeSchedule))) {
      sql = "SELECT effective_date, expiry_date FROM coast_scale_grade_code WHERE coast_scale_grade_code = :grade";
    } else if ("I".equalsIgnoreCase(trim(gradeSchedule))) {
      sql = "SELECT effective_date, expiry_date FROM interior_scale_grade_code"
          + " WHERE interior_scale_grade_code = :grade";
    } else {
      return null;
    }
    return dates(sql, new MapSqlParameterSource("grade", grade));
  }

  @Override
  public DocumentCounts documentCounts(String populationNumber, String stratumNumber, long samplingYear) {
    var p = new MapSqlParameterSource("pop", populationNumber).addValue("stratum", stratumNumber)
        .addValue("year", samplingYear);
    String where = " WHERE population_number = :pop AND stratum_number = :stratum AND sampling_year = :year";
    return new DocumentCounts(
        count("SELECT COUNT(*) FROM sample_scale_summary" + where, p),
        count("SELECT COUNT(*) FROM sample_log_tally" + where, p),
        count("SELECT COUNT(*) FROM weight_scale_summary" + where, p),
        count("SELECT COUNT(*) FROM weigh_slip" + where, p));
  }

  @Override
  public DocumentCounts strandedSingle(String populationNumber, String stratumNumber, long samplingYear,
      LocalDate effective, LocalDate expiry) {
    // PSR_EFFECTIVEDATE || ' 00:00:00' and PSR_EXPIRYDATE || ' 23:59:59' (sample log: + 90 days).
    LocalDateTime from = effective == null ? null : effective.atStartOfDay();
    LocalDateTime to = expiry == null ? null : expiry.atTime(23, 59, 59);
    var p = new MapSqlParameterSource("pop", populationNumber).addValue("stratum", stratumNumber)
        .addValue("year", samplingYear)
        .addValue("eff", from == null ? null : Timestamp.valueOf(from), Types.TIMESTAMP)
        .addValue("exp", to == null ? null : Timestamp.valueOf(to), Types.TIMESTAMP)
        .addValue("expLog", to == null ? null : Timestamp.valueOf(to.plusDays(90)), Types.TIMESTAMP);
    String common = " x.active_version_ind = 'Y' AND x.scl_rtn_version_state_code IN " + OPEN_STATES
        + " AND x.population_number = :pop AND x.stratum_number = :stratum AND x.sampling_year = :year";
    long sss = count("SELECT COUNT(*) FROM sample_scale_summary x WHERE" + common
        + " AND (x.scale_date < :eff OR x.scale_date > :exp)", p);
    long slt = count("SELECT COUNT(*) FROM sample_log_tally x WHERE" + common
        + " AND ((x.scale_event_type_code = 'CS' AND (x.scale_date < :eff OR x.scale_date > :expLog))"
        + " OR (x.scale_event_type_code IN ('SS', 'RS') AND (x.scale_date < :eff OR x.scale_date > :expLog)))", p);
    long wss = count("SELECT COUNT(*) FROM weight_scale_summary x WHERE" + common
        + " AND (x.scale_date < :eff OR x.scale_date > :exp)", p);
    long ws = count("SELECT COUNT(*) FROM weigh_slip x WHERE" + common
        + " AND x.scale_event_type_code IN ('PS', 'SS', 'RS', 'PD')"
        + " AND (x.scale_date < :eff OR x.scale_date > :exp)", p);
    return new DocumentCounts(sss, slt, wss, ws);
  }

  @Override
  public List<Stranded> strandedMultiple(String planAbbreviation, String populationNumber, long samplingYear) {
    var p = new MapSqlParameterSource("pop", populationNumber).addValue("year", samplingYear)
        .addValue("abbr", planAbbreviation);
    String from = " sample_plan sp, stratum_plan st WHERE sp.population_number = :pop AND sp.sampling_year = :year"
        + " AND sp.plan_abreviation = :abbr AND sp.plan_id = st.plan_id AND st.stratum_number = x.stratum_number"
        + " AND sp.population_number = x.population_number AND sp.sampling_year = x.sampling_year"
        + " AND x.active_version_ind = 'Y' AND x.scl_rtn_version_state_code IN " + OPEN_STATES;
    List<Stranded> out = new ArrayList<>();
    out.addAll(grouped("SSS", "SELECT st.stratum_number, COUNT(x.scale_date) FROM sample_scale_summary x," + from
        + " AND x.scale_date NOT BETWEEN st.effective_date AND st.expiry_date GROUP BY st.stratum_number", p));
    out.addAll(grouped("SLT", "SELECT st.stratum_number, COUNT(x.scale_date) FROM sample_log_tally x," + from
        + " AND ((x.scale_event_type_code = 'CS' AND x.scale_date NOT BETWEEN st.effective_date AND st.expiry_date)"
        + " OR (x.scale_event_type_code IN ('SS', 'RS')"
        + " AND x.scale_date NOT BETWEEN st.effective_date AND st.expiry_date + 90))"
        + " GROUP BY st.stratum_number", p));
    out.addAll(grouped("WSS", "SELECT st.stratum_number, COUNT(x.scale_date) FROM weight_scale_summary x," + from
        + " AND x.scale_date NOT BETWEEN st.effective_date AND st.expiry_date GROUP BY st.stratum_number", p));
    out.addAll(grouped("WS", "SELECT st.stratum_number, COUNT(x.scale_date) FROM weigh_slip x," + from
        + " AND x.scale_event_type_code IN ('PS', 'SS', 'RS', 'PD')"
        + " AND TRUNC(x.scale_date) NOT BETWEEN TRUNC(st.effective_date) AND TRUNC(st.expiry_date)"
        + " GROUP BY st.stratum_number", p));
    return out;
  }

  @Override
  public List<Map<String, Object>> latestRatios(String populationNumber, String stratumNumber, long samplingYear) {
    return jdbc.query("""
        SELECT * FROM ratio_history
         WHERE population_number = :pop AND stratum_number = :stratum AND sampling_year = :year
           AND ratio_timestamp = (SELECT MAX(ratio_timestamp) FROM ratio_history
                                   WHERE population_number = :pop AND stratum_number = :stratum
                                     AND sampling_year = :year)
         ORDER BY ratio_hist_id""",
        new MapSqlParameterSource("pop", populationNumber).addValue("stratum", stratumNumber)
            .addValue("year", samplingYear),
        JdbcSamplingReads::lowerRow);
  }

  // ── helpers ─────────────────────────────────────────────────────

  private List<Stranded> grouped(String type, String sql, MapSqlParameterSource p) {
    return jdbc.query(sql, p, (rs, n) -> new Stranded(type, rs.getString(1), rs.getLong(2)));
  }

  private long count(String sql, MapSqlParameterSource p) {
    Long n = jdbc.queryForObject(sql, p, Long.class);
    return n == null ? 0 : n;
  }

  private Dates dates(String sql, MapSqlParameterSource p) {
    // ScaleGradeDAO / ScaledLogXrefValidator: DateUtil.startDay(effective), DateUtil.endDay(expiry).
    List<Dates> rows = jdbc.query(sql, p, (rs, n) -> new Dates(
        rs.getTimestamp(1).toLocalDateTime().toLocalDate().atStartOfDay(),
        rs.getTimestamp(2).toLocalDateTime().toLocalDate().atTime(23, 59, 59)));
    return rows.isEmpty() ? null : rows.get(0);
  }

  private static String trim(String s) {
    return s == null ? "" : s.trim();
  }

  private static String blankToNull(String s) {
    return s == null || s.isEmpty() ? null : s;
  }

  static Map<String, Object> lowerRow(ResultSet rs, int rowNum) throws SQLException {
    Map<String, Object> row = new LinkedHashMap<>();
    ResultSetMetaData md = rs.getMetaData();
    for (int c = 1; c <= md.getColumnCount(); c++) {
      Object v = rs.getObject(c);
      if (v instanceof Timestamp t) v = t.toLocalDateTime();
      else if (v instanceof java.sql.Date d) v = d.toLocalDate().atStartOfDay();
      row.put(md.getColumnLabel(c).toLowerCase(Locale.ROOT), v);
    }
    return row;
  }

  private static final RowMapper<SamplePlanRow> PLAN = (rs, n) -> {
    SamplePlanRow r = new SamplePlanRow();
    r.planId = lng(rs, "plan_id");
    r.samplePlanStatusCode = rs.getString("sample_plan_status_code");
    r.populationNumber = rs.getString("population_number");
    r.samplingYear = lng(rs, "sampling_year");
    r.planAbreviation = rs.getString("plan_abreviation");
    r.planName = rs.getString("plan_name");
    r.ratioTypeCode = rs.getString("ratio_type_code");
    r.precisionLimit = dbl(rs, "precision_limit");
    r.sampleFloor = lng(rs, "sample_floor");
    r.currentConstraintType = rs.getString("current_constraint_type");
    r.currentEstimateType = rs.getString("current_estimate_type");
    r.acceptedPlanInd = rs.getString("accepted_plan_ind");
    r.optimumPrecisionCalc = dbl(rs, "optimum_precision_calc");
    r.targetPrecisionCalc = dbl(rs, "target_precision_calc");
    r.orgUnitNo = lng(rs, "org_unit_no");
    r.clientNumber = rs.getString("client_number");
    r.clientLocnCode = rs.getString("client_locn_code");
    r.effectiveDate = ts(rs, "effective_date");
    r.expiryDate = ts(rs, "expiry_date");
    r.entryUserid = rs.getString("entry_userid");
    r.entryTimestamp = ts(rs, "entry_timestamp");
    r.updateUserid = rs.getString("update_userid");
    r.updateTimestamp = ts(rs, "update_timestamp");
    return r;
  };

  private static final RowMapper<StratumPlanRow> STRATUM_PLAN = (rs, n) -> {
    StratumPlanRow r = new StratumPlanRow();
    r.stratumPlanId = lng(rs, "stratum_plan_id");
    r.planId = lng(rs, "plan_id");
    r.stratumNumber = rs.getString("stratum_number");
    r.stratumName = rs.getString("stratum_name");
    r.clientNumber = rs.getString("client_number");
    r.clientLocnCode = rs.getString("client_locn_code");
    r.sampleCountConstraint = lng(rs, "sample_count_constraint");
    r.precisionConstraint = dbl(rs, "precision_constraint");
    r.estimatedVolume = dbl(rs, "estimated_volume");
    r.estimatedLoads = lng(rs, "estimated_loads");
    r.estimatedLoadSize = dbl(rs, "estimated_load_size");
    r.estimatedStandardDeviation = dbl(rs, "estimated_standard_deviation");
    r.optimumSampleCalc = lng(rs, "optimum_sample_calc");
    r.optimumPrecisionCalc = dbl(rs, "optimum_precision_calc");
    r.optimumFrequencyCalc = lng(rs, "optimum_frequency_calc");
    r.targetSampleCalc = lng(rs, "target_sample_calc");
    r.targetPrecisionCalc = dbl(rs, "target_precision_calc");
    r.targetFrequencyCalc = lng(rs, "target_frequency_calc");
    r.acceptedStratumPlanInd = rs.getString("accepted_stratum_plan_ind");
    r.expiryDate = ts(rs, "expiry_date");
    r.effectiveDate = ts(rs, "effective_date");
    r.stratumDesc = rs.getString("stratum_desc");
    r.timberSourceDesc = rs.getString("timber_source_desc");
    r.speciesGradeProfileDesc = rs.getString("species_grade_profile_desc");
    r.entryUserid = rs.getString("entry_userid");
    r.entryTimestamp = ts(rs, "entry_timestamp");
    r.updateUserid = rs.getString("update_userid");
    r.updateTimestamp = ts(rs, "update_timestamp");
    r.gradeScheduleCode = rs.getString("grade_schedule_code");
    r.scaleProductCode = rs.getString("scale_product_code");
    return r;
  };

  private static Long lng(ResultSet rs, String col) throws SQLException {
    BigDecimal v = rs.getBigDecimal(col);
    return v == null ? null : v.longValue();
  }

  private static Double dbl(ResultSet rs, String col) throws SQLException {
    BigDecimal v = rs.getBigDecimal(col);
    return v == null ? null : v.doubleValue();
  }

  private static LocalDateTime ts(ResultSet rs, String col) throws SQLException {
    Timestamp t = rs.getTimestamp(col);
    return t == null ? null : t.toLocalDateTime();
  }
}
