package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import ca.bc.gov.nrs.hbs.api.detailreturns.validation.RateCheckRules.OverrideRule;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.RateCheckRules.WorksheetPeriod;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link ReferenceData} over Oracle: the SQL of the legacy readers, one query
 * per legacy finder (bound parameters only, no schema prefix). A legacy
 * {@code *NotFoundException} / {@code FinderException} is an empty result.
 *
 * <p>Date criteria are days: the legacy queries formatted dates as
 * {@code DD-MON-YYYY} literals, so they are bound here as the Pacific day at
 * 00:00 ({@link RateCheckRules#day}).
 */
@Component
public class JdbcReferenceData implements ReferenceData {

  /**
   * {@code TimberMarkQuery.SELECT_LOAD + " AND t.timber_mark = ?"} (via
   * {@code TimberMarkReader.getTimberMarkByMark}; {@code selectPKByPK} required
   * the MARK_BILLED_CLI row). The payment method is HARVEST_SALE's; the
   * LICENCE_TO_CUT outer join only fed {@code licenceToCutCD}, which no
   * detail-return edit reads, so it is left out (legacy read the first row).
   */
  static final String TIMBER_MARK = """
      SELECT t.timber_mark, b.file_type_code, t.cruise_based_ind, s.payment_method_cd,
             b.client_number, b.client_locn_code, t.forest_district, b.forest_region, b.region_code,
             t.quota_type_code, t.mark_status_st, t.mark_issue_date, t.mark_expiry_date, t.mark_extend_date
        FROM timber_mark t
        JOIN mark_billed_cli b ON b.timber_mark = t.timber_mark
        LEFT JOIN harvest_sale s ON s.forest_file_id = b.forest_file_id
       WHERE t.timber_mark = :mark""";

  private static final String SCALE_SITE_COLUMNS = """
      SELECT scale_site_id_nmbr, org_unit_no, owner_cli_number, owner_cli_locn_cd, site_name, timber_brand
        FROM scale_site
      """;

  /** {@code ScaleSiteQuery.selectByPK} (via {@code ScaleSiteReader.getScaleSite}). */
  static final String SCALE_SITE = SCALE_SITE_COLUMNS + " WHERE scale_site_id_nmbr = :site";

  /**
   * {@code ScaleSiteQuery.selectByCriteria(timberBrand only)} via
   * {@code ScaleSiteEntityBean.ejbFindByCriteria} → {@code singleFindBySql}:
   * none or several rows is a FinderException (not found).
   */
  static final String SCALE_SITE_BY_BRAND = SCALE_SITE_COLUMNS + " WHERE timber_brand = :brand";

  /** {@code CutBlockQuery.selectPKByPK} (via {@code CutBlockManager.findCutBlockByPrimaryKey}). */
  static final String CUT_BLOCK = """
      SELECT COUNT(*)
        FROM cut_block
       WHERE cut_block_id = :cutBlockId AND timber_mark = :mark""";

  /** {@code QuantLicenseQuery.selectPKByPK} (via {@code QuantLicenseManager.findQuantLicenseByPrimaryKey}). */
  static final String QUANT_LICENSE = """
      SELECT COUNT(*)
        FROM quant_license
       WHERE license_number = :licence AND quant_lic_type_cd = :type""";

  /** {@code QuantLicenseManager}'s scaler licence type ({@code findQuantLicenseByPrimaryKey(licence, "S")}). */
  static final String QUANT_LIC_TYPE_SCALER = "S";

  /**
   * {@code StratumQuery.selectByPK} (via {@code SrtsManager.findStratumByPrimaryKey});
   * {@code Stratum.getGradeConstraint} is GRADE_SCHEDULE_CODE.
   */
  static final String STRATUM = """
      SELECT population_number, stratum_number, sampling_year, client_number, client_locn_code,
             grade_schedule_code, effective_date, expiry_date
        FROM stratum
       WHERE population_number = :population AND sampling_year = :year AND stratum_number = :stratum""";

  /** {@code PopulationQuery.selectByPK} (via {@code SamplePlanner.findPopulationByPrimaryKey}). */
  static final String POPULATION = """
      SELECT population_number, sampling_year, client_number, client_locn_code, ratio_type_code
        FROM population
       WHERE population_number = :population AND sampling_year = :year""";

  /**
   * {@code ClientEntityBean.queryByPrimaryKey} + {@code selectLocationColumns}
   * (via {@code ClientReader.findClientLocation}): FOREST_CLIENT is not granted,
   * so the client status comes from V_CLIENT_PUBLIC (docs/database.md). Both
   * keys are zero-padded first ({@code padClientNumber} / {@code padLocationCode}).
   */
  static final String CLIENT_LOCATION = """
      SELECT RTRIM(fc.client_number) AS client_number, RTRIM(cl.client_locn_code) AS client_locn_code,
             fc.client_status_code, cl.locn_expired_ind
        FROM v_client_public fc
        JOIN client_location cl ON cl.client_number = fc.client_number
       WHERE fc.client_number = :clientNumber AND cl.client_locn_code = :clientLocnCode""";

  /**
   * {@code OverrideRateRuleQuery.selectByMarkAndDates} (via
   * {@code RateManagerBean.findActiveOverrideRateRule}) + the rule's type and
   * override rate date ({@code selectByPK}); BT10012 deck clause appended.
   */
  static final String OVERRIDE_RULES = """
      SELECT ovrrdrtrl_type, override_rate_date
        FROM override_rating_rule
       WHERE timber_mark = :mark
         AND override_rule_status_code = :status
         AND :scaleDay BETWEEN scale_effective_date AND scale_expiry_date
         AND :processingDay BETWEEN processing_effective_date AND processing_expiry_date""";

  static final String DECK_MATCH = " AND field_scale_deck_id = :deck";
  static final String DECK_NONE = " AND field_scale_deck_id IS NULL";

  /**
   * {@code AppraisalWorksheetQuery.selectByTimberMarkAndDateRange(mark,
   * startDayToEndDay(day), CNF)} (via {@code findWorksheetsByMarkAndDateAndStatus})
   * with the worksheet columns the edits read. Legacy had no ORDER BY; the
   * effective date makes "the first worksheet" deterministic.
   */
  static final String WORKSHEETS = """
      SELECT app_effective_date, expiry_date, appraisal_mthd_cd,
             conif_stand_rate_elig_code, decid_stand_rate_elig_code
        FROM app_worksheet
       WHERE timber_mark = :mark
         AND :day <= expiry_date
         AND :day >= app_effective_date
         AND appraisal_sts_st = :status
       ORDER BY app_effective_date""";

  /**
   * {@code StumpageRateQuery.selectByCriteria(timberMark, appEffectiveDate)}
   * (via {@code findStumpageRatesForAppraisal}): a date criterion is the whole day.
   */
  static final String STUMPAGE_RATES = """
      SELECT COUNT(*)
        FROM app_stmpg_rate
       WHERE timber_mark = :mark
         AND app_effective_date >= :day
         AND app_effective_date < :nextDay""";

  /**
   * {@code AppraisalWorksheetQuery.selectGradeScheduleTypeCode} (via
   * {@code RateManagerBean.getGradeScheduleType}): the confirmed appraisal's
   * method on the day, else the schedule of the mark's region (legacy DECODE
   * written as CASE; RNI/RSI/RSK/RNO/ROM/RTO/RCB/RKB interior, RCO/RSC/RWC coastal).
   */
  static final String GRADE_SCHEDULE = """
      SELECT NVL(w.code,
                 (SELECT CASE MAX(o.org_unit_code)
                           WHEN 'RNI' THEN 'I' WHEN 'RSI' THEN 'I' WHEN 'RCO' THEN 'C'
                           WHEN 'RSK' THEN 'I' WHEN 'RNO' THEN 'I' WHEN 'ROM' THEN 'I'
                           WHEN 'RTO' THEN 'I' WHEN 'RCB' THEN 'I' WHEN 'RKB' THEN 'I'
                           WHEN 'RSC' THEN 'C' WHEN 'RWC' THEN 'C'
                         END
                    FROM org_unit o, mark_billed_cli m
                   WHERE m.timber_mark = :mark
                     AND m.forest_region = o.org_unit_no)) AS grade_schedule
        FROM (SELECT MAX(a.appraisal_mthd_cd) AS code
                FROM app_worksheet a, timber_mark t
               WHERE t.timber_mark = :mark
                 AND a.timber_mark = t.timber_mark
                 AND :day BETWEEN a.app_effective_date AND a.expiry_date
                 AND a.appraisal_sts_st = :status) w""";

  private final NamedParameterJdbcTemplate jdbc;
  private final RateCheckRules.Source rateSource = new RateSource();

  public JdbcReferenceData(JdbcTemplate jdbc) {
    this.jdbc = new NamedParameterJdbcTemplate(jdbc);
  }

  @Override
  public Optional<TimberMarkRef> timberMark(String timberMark) {
    if (timberMark == null) {
      return Optional.empty();
    }
    return first(jdbc.query(TIMBER_MARK, new MapSqlParameterSource("mark", timberMark), (rs, i) ->
        new TimberMarkRef(rs.getString("timber_mark"), rs.getString("file_type_code"),
            rs.getString("cruise_based_ind"), rs.getString("payment_method_cd"), rs.getString("client_number"),
            rs.getString("client_locn_code"), longOrNull(rs, "forest_district"), longOrNull(rs, "forest_region"),
            rs.getString("region_code"), rs.getString("quota_type_code"), rs.getString("mark_status_st"),
            rs.getTimestamp("mark_issue_date"), rs.getTimestamp("mark_expiry_date"),
            rs.getTimestamp("mark_extend_date"))));
  }

  @Override
  public Optional<ScaleSiteRef> scaleSite(String scaleSiteId) {
    if (scaleSiteId == null) {
      return Optional.empty();
    }
    return first(jdbc.query(SCALE_SITE, new MapSqlParameterSource("site", scaleSiteId), JdbcReferenceData::site));
  }

  @Override
  public Optional<ScaleSiteRef> scaleSiteByTimberBrand(String timberBrand) {
    if (legacyEmpty(timberBrand)) {
      // appendWhereClause skipped an empty brand: every site matched → "multiple found"
      return Optional.empty();
    }
    List<ScaleSiteRef> sites = jdbc.query(SCALE_SITE_BY_BRAND, new MapSqlParameterSource("brand", timberBrand),
        JdbcReferenceData::site);
    return sites.size() == 1 ? Optional.of(sites.get(0)) : Optional.empty();
  }

  @Override
  public boolean cutBlockExists(String cutBlockId, String timberMark) {
    if (cutBlockId == null || timberMark == null) {
      return false;
    }
    return count(CUT_BLOCK, new MapSqlParameterSource("cutBlockId", cutBlockId).addValue("mark", timberMark)) > 0;
  }

  @Override
  public boolean scalerLicenceExists(String licenseNumber) {
    if (licenseNumber == null) {
      return false;
    }
    return count(QUANT_LICENSE, new MapSqlParameterSource("licence", licenseNumber)
        .addValue("type", QUANT_LIC_TYPE_SCALER)) > 0;
  }

  @Override
  public Optional<StratumRef> stratum(String population, String stratum, Long samplingYear) {
    if (population == null || stratum == null || samplingYear == null) {
      return Optional.empty();
    }
    MapSqlParameterSource p = new MapSqlParameterSource("population", population)
        .addValue("stratum", stratum).addValue("year", samplingYear);
    return first(jdbc.query(STRATUM, p, (rs, i) -> new StratumRef(rs.getString("population_number"),
        rs.getString("stratum_number"), longOrNull(rs, "sampling_year"), rs.getString("client_number"),
        rs.getString("client_locn_code"), rs.getString("grade_schedule_code"), rs.getTimestamp("effective_date"),
        rs.getTimestamp("expiry_date"))));
  }

  @Override
  public Optional<PopulationRef> population(String population, Long samplingYear) {
    if (population == null || samplingYear == null) {
      return Optional.empty();
    }
    MapSqlParameterSource p = new MapSqlParameterSource("population", population).addValue("year", samplingYear);
    // POPULATION has no status / grade constraint column: always null
    return first(jdbc.query(POPULATION, p, (rs, i) -> new PopulationRef(rs.getString("population_number"),
        longOrNull(rs, "sampling_year"), rs.getString("client_number"), rs.getString("client_locn_code"),
        null, null, rs.getString("ratio_type_code"))));
  }

  @Override
  public Optional<ClientLocationRef> clientLocation(String clientNumber, String clientLocnCode) {
    if (clientNumber == null || clientLocnCode == null) {
      // legacy padClientNumber / padLocationCode threw a NullPointerException
      return Optional.empty();
    }
    MapSqlParameterSource p = new MapSqlParameterSource("clientNumber", padClientNumber(clientNumber))
        .addValue("clientLocnCode", padLocationCode(clientLocnCode));
    return first(jdbc.query(CLIENT_LOCATION, p, (rs, i) -> new ClientLocationRef(rs.getString("client_number"),
        rs.getString("client_locn_code"), rs.getString("client_status_code"), rs.getString("locn_expired_ind"))));
  }

  @Override
  public RateCheck rateCheck(String timberMark, Date scaleDate, String fieldScaleDeckId) {
    return RateCheckRules.hasRate(rateSource, timberMark, RateCheckRules.day(scaleDate), fieldScaleDeckId,
        RateCheckRules.today());
  }

  @Override
  public List<WorksheetRef> confirmedWorksheets(String timberMark, Date scaleDate) {
    if (timberMark == null || scaleDate == null) {
      // legacy: startDayToEndDay(null) NPE, swallowed by validateRate (no edit ran)
      return List.of();
    }
    return jdbc.query(WORKSHEETS, worksheetParams(timberMark, RateCheckRules.day(scaleDate)), (rs, i) ->
        new WorksheetRef(rs.getString("appraisal_mthd_cd"), rs.getString("conif_stand_rate_elig_code"),
            rs.getString("decid_stand_rate_elig_code")));
  }

  @Override
  public String gradeScheduleType(String timberMark, Date scaleDate) {
    // ScaledLogXrefValidator.getGradeScheduleType: "" unless both are given
    if (legacyEmpty(timberMark) || scaleDate == null) {
      return "";
    }
    MapSqlParameterSource p = new MapSqlParameterSource("mark", timberMark)
        .addValue("day", at(RateCheckRules.day(scaleDate)))
        .addValue("status", RateCheckRules.WORKSHEET_CONFIRMED);
    List<String> rows = jdbc.queryForList(GRADE_SCHEDULE, p, String.class);
    // RateManagerBean: "" when no row, else the (possibly null) value
    return rows.isEmpty() ? "" : rows.get(0);
  }

  /** The database side of {@link RateCheckRules}. */
  private final class RateSource implements RateCheckRules.Source {

    @Override
    public List<OverrideRule> activeOverrideRules(String timberMark, LocalDate scaleDay, LocalDate processingDay,
                                                  String fieldScaleDeckId) {
      MapSqlParameterSource p = new MapSqlParameterSource("mark", timberMark)
          .addValue("status", RateCheckRules.RULE_STATUS_ACTIVE)
          .addValue("scaleDay", at(scaleDay))
          .addValue("processingDay", at(processingDay));
      String sql;
      if (legacyEmpty(fieldScaleDeckId)) {
        sql = OVERRIDE_RULES + DECK_NONE;
      } else {
        sql = OVERRIDE_RULES + DECK_MATCH;
        p.addValue("deck", fieldScaleDeckId);
      }
      return jdbc.query(sql, p, (rs, i) -> new OverrideRule(rs.getString("ovrrdrtrl_type"),
          wallDay(rs.getTimestamp("override_rate_date"))));
    }

    @Override
    public List<WorksheetPeriod> confirmedWorksheets(String timberMark, LocalDate day) {
      return jdbc.query(WORKSHEETS, worksheetParams(timberMark, day), (rs, i) ->
          new WorksheetPeriod(wallDay(rs.getTimestamp("app_effective_date")),
              wallDay(rs.getTimestamp("expiry_date"))));
    }

    @Override
    public int stumpageRateCount(String timberMark, LocalDate appEffectiveDay) {
      return count(STUMPAGE_RATES, new MapSqlParameterSource("mark", timberMark)
          .addValue("day", at(appEffectiveDay))
          .addValue("nextDay", at(appEffectiveDay.plusDays(1))));
    }
  }

  private static MapSqlParameterSource worksheetParams(String timberMark, LocalDate day) {
    return new MapSqlParameterSource("mark", timberMark)
        .addValue("day", at(day))
        .addValue("status", RateCheckRules.WORKSHEET_CONFIRMED);
  }

  private int count(String sql, MapSqlParameterSource p) {
    Integer n = jdbc.queryForObject(sql, p, Integer.class);
    return n == null ? 0 : n;
  }

  private static ScaleSiteRef site(ResultSet rs, int i) throws SQLException {
    return new ScaleSiteRef(rs.getString("scale_site_id_nmbr"), longOrNull(rs, "org_unit_no"),
        rs.getString("owner_cli_number"), rs.getString("owner_cli_locn_cd"), rs.getString("site_name"),
        rs.getString("timber_brand"));
  }

  private static <T> Optional<T> first(List<T> rows) {
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  private static Long longOrNull(ResultSet rs, String column) throws SQLException {
    long v = rs.getLong(column);
    return rs.wasNull() ? null : v;
  }

  /** A day bound as a DATE at 00:00 (the legacy {@code TO_DATE('dd-MMM-yyyy')} literal). */
  static Timestamp at(LocalDate day) {
    return day == null ? null : Timestamp.valueOf(day.atStartOfDay());
  }

  /** The calendar day of a DATE as stored (wall clock). */
  static LocalDate wallDay(Timestamp t) {
    return t == null ? null : t.toLocalDateTime().toLocalDate();
  }

  /** {@code StringUtil.empty}: null or "" (whitespace is not empty). */
  static boolean legacyEmpty(String s) {
    return s == null || s.isEmpty();
  }

  /** {@code ClientEntityBean.padClientNumber}: left-pad with zeros to 8. */
  static String padClientNumber(String clientNumber) {
    StringBuilder s = new StringBuilder(clientNumber);
    while (s.length() < 8) {
      s.insert(0, '0');
    }
    return s.toString();
  }

  /** {@code ClientEntityBean.padLocationCode}: left-pad with zeros to 2. */
  static String padLocationCode(String locationCode) {
    StringBuilder s = new StringBuilder(locationCode);
    while (s.length() < 2) {
      s.insert(0, '0');
    }
    return s.toString();
  }
}
