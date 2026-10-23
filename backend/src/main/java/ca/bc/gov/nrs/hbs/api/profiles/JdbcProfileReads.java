package ca.bc.gov.nrs.hbs.api.profiles;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * Oracle reads for the profile rules. Plain SELECTs on the THE synonyms, every
 * value a bound parameter (the legacy queries built literals into the SQL
 * text). Optional criteria are added as fixed fragments, never by value.
 */
@Component
public class JdbcProfileReads implements ProfileReads {

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcProfileReads(JdbcTemplate jdbc) {
    this.jdbc = new NamedParameterJdbcTemplate(jdbc);
  }

  private static final RowMapper<Range> RANGE = (rs, n) -> new Range(
      ProfileRules.dateTime(rs.getTimestamp("effective_date")),
      ProfileRules.dateTime(rs.getTimestamp("expiry_date")));

  private static Map<String, Object> row(ResultSet rs) throws SQLException {
    ResultSetMetaData md = rs.getMetaData();
    Map<String, Object> row = new LinkedHashMap<>();
    for (int c = 1; c <= md.getColumnCount(); c++) {
      int type = md.getColumnType(c);
      Object v = type == Types.DATE || type == Types.TIMESTAMP ? rs.getTimestamp(c) : rs.getObject(c);
      row.put(md.getColumnLabel(c).toLowerCase(Locale.ROOT), v);
    }
    return row;
  }

  private Map<String, Object> one(String sql, MapSqlParameterSource p) {
    List<Map<String, Object>> rows = jdbc.query(sql, p, (rs, n) -> row(rs));
    return rows.isEmpty() ? null : rows.get(0);
  }

  private int count(String sql, MapSqlParameterSource p) {
    Integer n = jdbc.queryForObject(sql, p, Integer.class);
    return n == null ? 0 : n;
  }

  private static MapSqlParameterSource params() {
    return new MapSqlParameterSource();
  }

  private static Timestamp ts(LocalDateTime t) {
    return Timestamp.valueOf(t);
  }

  private static Timestamp day(LocalDate d) {
    return Timestamp.valueOf(d.atStartOfDay());
  }

  // ---------------------------------------------------------------- scale sites

  @Override
  public Map<String, Object> scaleSite(String scaleSiteId) {
    return one("SELECT * FROM scale_site WHERE scale_site_id_nmbr = :site", params().addValue("site", scaleSiteId));
  }

  @Override
  public boolean siteDataDomain(String userId, String scaleSiteId) {
    return count("SELECT COUNT(*) FROM hbs_user_site_data_domain WHERE user_id = :userId"
        + " AND scale_site_id_nmbr = :site", params().addValue("userId", userId).addValue("site", scaleSiteId)) > 0;
  }

  @Override
  public List<Long> districtDataDomains(String userId) {
    return jdbc.queryForList("SELECT org_unit_no FROM hbs_user_dist_data_domain WHERE user_id = :userId",
        params().addValue("userId", userId), Long.class);
  }

  @Override
  public boolean tradingPartnerExists(String clientNumber, String clientLocnCode) {
    return count("SELECT COUNT(*) FROM trading_partner WHERE client_number = :client AND client_locn_code = :loc",
        params().addValue("client", clientNumber).addValue("loc", clientLocnCode)) > 0;
  }

  @Override
  public List<Range> scaleSiteProfileRanges(String scaleSiteId, String returnType, String docType,
      String clientNumber, String clientLocnCode) {
    MapSqlParameterSource p = params().addValue("site", scaleSiteId).addValue("rt", returnType)
        .addValue("doc", docType).addValue("client", clientNumber).addValue("loc", clientLocnCode);
    // Legacy: client/location narrow the detail rows by the site OWNER and the
    // summary rows by the submitter ("prevents all SSP records from being returned").
    String sql = "SELECT ssp.effective_date, ssp.expiry_date FROM scale_site_profile ssp"
        + " JOIN scale_site ss ON ss.scale_site_id_nmbr = ssp.scale_site_id_nmbr"
        + " WHERE ssp.scale_site_id_nmbr = :site AND ssp.hbs_return_type_code = :rt AND ssp.doc_type = :doc"
        + (clientNumber == null ? "" : " AND ss.owner_cli_number = :client")
        + (clientLocnCode == null ? "" : " AND ss.owner_cli_locn_cd = :loc")
        + " UNION ALL"
        + " SELECT tps.effective_date, tps.expiry_date FROM trading_partner_submitter tps"
        + " WHERE tps.scale_site_id_nmbr = :site AND tps.hbs_return_type_code = :rt AND tps.tp_doc_type = :doc"
        + (clientNumber == null ? "" : " AND tps.client_number = :client")
        + (clientLocnCode == null ? "" : " AND tps.client_locn_code = :loc");
    return jdbc.query(sql, p, RANGE);
  }

  @Override
  public LocalDateTime nextScaleSiteProfile(String scaleSiteId, String returnType, String docType,
      String clientNumber, String clientLocnCode, LocalDateTime effective) {
    MapSqlParameterSource p = params().addValue("site", scaleSiteId).addValue("rt", returnType)
        .addValue("doc", docType).addValue("client", clientNumber).addValue("loc", clientLocnCode)
        .addValue("eff", ts(effective));
    String sql;
    if ("D".equals(docType)) {
      // getNextEffectiveDate, Detail: the SCALE_SITE_PROFILE rows of the site
      sql = "SELECT MIN(effective_date) FROM scale_site_profile WHERE scale_site_id_nmbr = :site"
          + " AND hbs_return_type_code = :rt AND doc_type = :doc AND effective_date > :eff";
    } else if ("S".equals(docType)) {
      sql = "SELECT MIN(effective_date) FROM trading_partner_submitter WHERE scale_site_id_nmbr = :site"
          + " AND hbs_return_type_code = :rt AND tp_doc_type = :doc AND effective_date > :eff"
          + (clientNumber == null ? "" : " AND client_number = :client")
          + (clientLocnCode == null ? "" : " AND client_locn_code = :loc");
    } else {
      return null;
    }
    return ProfileRules.dateTime(jdbc.queryForObject(sql, p, Timestamp.class));
  }

  @Override
  public Map<String, Object> tradingPartnerSubmitter(long tpsId) {
    return one("SELECT * FROM trading_partner_submitter WHERE tps_id = :id", params().addValue("id", tpsId));
  }

  // ---------------------------------------------------------------- population

  @Override
  public Map<String, Object> population(String populationNumber, long samplingYear) {
    return one("SELECT * FROM population WHERE population_number = :pop AND sampling_year = :year",
        params().addValue("pop", populationNumber).addValue("year", samplingYear));
  }

  @Override
  public Map<String, Object> populationProfile(long popprofId) {
    return one("SELECT pp.*, pop.client_number AS pop_client_number FROM population_profile pp"
        + " LEFT JOIN population pop ON pop.population_number = pp.population_number"
        + " AND pop.sampling_year = pp.sampling_year WHERE pp.popprof_id = :id", params().addValue("id", popprofId));
  }

  @Override
  public int overlappingPopulationProfiles(String populationNumber, long samplingYear, LocalDate effective,
      LocalDate expiry, Long excludeId) {
    // Legacy compared against TO_DATE('dd-MON-yyyy') literals, i.e. midnight of each day.
    return count("SELECT COUNT(*) FROM population_profile WHERE population_number = :pop AND sampling_year = :year"
        + " AND effective_date <= :expiry AND expiry_date >= :effective"
        + (excludeId == null ? "" : " AND popprof_id <> :id"),
        params().addValue("pop", populationNumber).addValue("year", samplingYear)
            .addValue("expiry", day(expiry)).addValue("effective", day(effective)).addValue("id", excludeId));
  }

  // ---------------------------------------------------------------- mark holder

  @Override
  public boolean clientLocationExists(String clientNumber, String clientLocnCode) {
    return count("SELECT COUNT(*) FROM client_location WHERE client_number = :client AND client_locn_code = :loc",
        params().addValue("client", clientNumber).addValue("loc", clientLocnCode)) > 0;
  }

  @Override
  public List<Range> markHolderRanges(String clientNumber, String clientLocnCode, String returnType) {
    return jdbc.query("SELECT effective_date, expiry_date FROM hbs_mark_holder_profile"
        + " WHERE client_number = :client AND client_locn_code = :loc AND hbs_return_type_code = :rt",
        params().addValue("client", clientNumber).addValue("loc", clientLocnCode).addValue("rt", returnType), RANGE);
  }

  @Override
  public LocalDateTime nextMarkHolderProfile(String clientNumber, String clientLocnCode, String returnType,
      LocalDateTime effective) {
    return ProfileRules.dateTime(jdbc.queryForObject("SELECT MIN(effective_date) FROM hbs_mark_holder_profile"
        + " WHERE client_number = :client AND client_locn_code = :loc AND hbs_return_type_code = :rt"
        + " AND effective_date > :eff",
        params().addValue("client", clientNumber).addValue("loc", clientLocnCode).addValue("rt", returnType)
            .addValue("eff", ts(effective)), Timestamp.class));
  }

  @Override
  public Map<String, Object> markHolderProfile(long mhprofId) {
    return one("SELECT * FROM hbs_mark_holder_profile WHERE mhprof_id = :id", params().addValue("id", mhprofId));
  }

  // ---------------------------------------------------------------- delivery

  @Override
  public List<Range> deliveryRanges(String clientNumber, String clientLocnCode) {
    return jdbc.query("SELECT effective_date, expiry_date FROM hbs_client_delivery_profile"
        + " WHERE client_number = :client AND client_locn_code = :loc",
        params().addValue("client", clientNumber).addValue("loc", clientLocnCode), RANGE);
  }

  @Override
  public Next nextDeliveryProfile(String clientNumber, String clientLocnCode, LocalDateTime effective) {
    List<Next> rows = jdbc.query("SELECT dlvprof_id, effective_date FROM hbs_client_delivery_profile"
        + " WHERE client_number = :client AND client_locn_code = :loc"
        + " AND effective_date = (SELECT MIN(effective_date) FROM hbs_client_delivery_profile"
        + " WHERE client_number = :client AND client_locn_code = :loc AND effective_date >= :eff)"
        + " ORDER BY effective_date, dlvprof_id",
        params().addValue("client", clientNumber).addValue("loc", clientLocnCode).addValue("eff", ts(effective)),
        (rs, n) -> new Next(rs.getLong("dlvprof_id"), ProfileRules.dateTime(rs.getTimestamp("effective_date"))));
    return rows.isEmpty() ? null : rows.get(0);
  }

  @Override
  public int documentReceivers(String clientNumber, String clientLocnCode) {
    return count("SELECT COUNT(*) FROM hbs_user_cli_loc_data_domain WHERE client_number = :client"
        + " AND client_locn_code = :loc", params().addValue("client", clientNumber).addValue("loc", clientLocnCode));
  }

  @Override
  public int activeDocumentReceivers(String clientNumber, String clientLocnCode) {
    return count("SELECT COUNT(*) FROM hbs_user_cli_loc_data_domain d JOIN hbs_user u ON u.user_id = d.user_id"
        + " WHERE d.client_number = :client AND d.client_locn_code = :loc AND u.hbs_user_status_code = 'ACT'",
        params().addValue("client", clientNumber).addValue("loc", clientLocnCode));
  }

  @Override
  public Map<String, Object> deliveryProfile(long dlvprofId) {
    return one("SELECT * FROM hbs_client_delivery_profile WHERE dlvprof_id = :id", params().addValue("id", dlvprofId));
  }

  // ---------------------------------------------------------------- cruise based

  @Override
  public Map<String, Object> appraisal(String timberMark, LocalDate effectiveDate) {
    return one("SELECT * FROM app_worksheet WHERE timber_mark = :mark AND TRUNC(app_effective_date) = :eff",
        params().addValue("mark", timberMark).addValue("eff", day(effectiveDate)));
  }

  @Override
  public List<String> appraisedSpecies(String timberMark, LocalDate effectiveDate) {
    return jdbc.queryForList("SELECT hdbs_tree_species FROM app_species WHERE timber_mark = :mark"
        + " AND TRUNC(app_effective_date) = :eff",
        params().addValue("mark", timberMark).addValue("eff", day(effectiveDate)), String.class);
  }

  @Override
  public boolean validSpeciesGrade(String speciesCode, String gradeCode) {
    return count("SELECT COUNT(*) FROM species_product_grade_xref WHERE scale_species_code = :species"
        + " AND scale_grade_code = :grade", params().addValue("species", speciesCode).addValue("grade", gradeCode)) > 0;
  }

  @Override
  public Map<String, Object> ratio(String timberMark, LocalDate effectiveDate, String speciesCode, String gradeCode) {
    return one("SELECT * FROM cruise_based_sp_grd_ratio WHERE timber_mark = :mark"
        + " AND TRUNC(app_effective_date) = :eff AND scale_species_code = :species AND scale_grade_code = :grade",
        params().addValue("mark", timberMark).addValue("eff", day(effectiveDate))
            .addValue("species", speciesCode).addValue("grade", gradeCode));
  }
}
