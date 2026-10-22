package ca.bc.gov.nrs.hbs.api.rating;

import ca.bc.gov.nrs.hbs.api.exception.EntityNotFoundException;
import ca.bc.gov.nrs.hbs.api.rating.RatingRules.Interval;
import ca.bc.gov.nrs.hbs.api.rating.RatingRules.RateInterval;
import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The reads the Rating workflows make before writing: the current row (for
 * {@code RowLock} and the full-row STORE procs) and the lookups behind the
 * legacy validations. Plain SELECTs, bound parameters, no schema prefix (the
 * THE public synonyms), as the registry queries. Each method cites the legacy
 * query it replaces; the legacy versions concatenated the values into SQL.
 */
@Component
public class RatingRepository {

  private final NamedParameterJdbcTemplate jdbc;

  @org.springframework.beans.factory.annotation.Autowired
  public RatingRepository(JdbcTemplate jdbc) {
    this.jdbc = new NamedParameterJdbcTemplate(jdbc);
  }

  /** For tests. */
  protected RatingRepository() {
    this.jdbc = null;
  }

  // ── Rows (SELECT *: column names match the STORE proc parameters, TableApi.argsFromRow) ──

  public Map<String, Object> overrideRule(long id) {
    return one("SELECT * FROM override_rating_rule WHERE override_rating_rule_id = :id", id, "overrideRatingRuleId");
  }

  public Map<String, Object> overrideRate(long id) {
    return one("SELECT * FROM override_rate WHERE override_rate_id = :id", id, "overrideRateId");
  }

  public Map<String, Object> districtRate(long id) {
    return one("SELECT * FROM hbs_district_default_rate WHERE distrt_id = :id", id, "distrtId");
  }

  public Map<String, Object> wasteRate(long id) {
    return one("SELECT * FROM waste_residue_rate WHERE wstrt_id = :id", id, "wstrtId");
  }

  private Map<String, Object> one(String sql, long id, String key) {
    List<Map<String, Object>> rows = jdbc.queryForList(sql, new MapSqlParameterSource("id", id));
    if (rows.isEmpty()) {
      throw new EntityNotFoundException(Map.class, key, String.valueOf(id));
    }
    return rows.get(0);
  }

  // ── Override rules ──

  /**
   * Active rules for the mark (and deck, or regular rules when no deck) whose
   * scale AND processing intervals both overlap the given ones —
   * {@code OverrideRateRuleQuery.countOverlapByCriteria}.
   */
  public long countOverlappingActiveRules(String timberMark, String deck, LocalDate processFrom,
      LocalDate processTo, LocalDate scaleFrom, LocalDate scaleTo) {
    var p = new MapSqlParameterSource()
        .addValue("timberMark", timberMark)
        .addValue("deck", deck, Types.VARCHAR)
        .addValue("processFrom", Date.valueOf(processFrom))
        .addValue("processTo", Date.valueOf(processTo))
        .addValue("scaleFrom", Date.valueOf(scaleFrom))
        .addValue("scaleTo", Date.valueOf(scaleTo));
    return count("""
        SELECT COUNT(*)
          FROM override_rating_rule
         WHERE timber_mark = :timberMark
           AND override_rule_status_code = 'ACT'
           AND ((:deck IS NULL AND field_scale_deck_id IS NULL) OR field_scale_deck_id = :deck)
           AND scale_effective_date <= :scaleTo
           AND scale_expiry_date >= :scaleFrom
           AND processing_effective_date <= :processTo
           AND processing_expiry_date >= :processFrom""", p);
  }

  /** {@code RuleHelper.getRates} → count of override rate detail rows of a rule. */
  public long countOverrideRates(long ruleId) {
    return count("SELECT COUNT(*) FROM override_rate WHERE override_rating_rule_id = :id",
        new MapSqlParameterSource("id", ruleId));
  }

  /** {@code RuleHelper.isValidTimberMark}: TIMBER_MARK by primary key. */
  public boolean timberMarkExists(String timberMark) {
    return count("SELECT COUNT(*) FROM timber_mark WHERE timber_mark = :tm",
        new MapSqlParameterSource("tm", timberMark)) > 0;
  }

  /**
   * The mark's FILE_TYPE_CODE from MARK_BILLED_CLI, empty when the mark is not
   * in TIMBER_MARK + MARK_BILLED_CLI — {@code TimberMarkReader.getTimberMarkByMark}
   * ({@code TimberMarkQuery.SELECT_LOAD} joins both), behind the
   * {@code timbermarkCrossRef} validator, {@code isBeachcomb} and {@code privateMark}.
   */
  public Optional<String> billedMarkFileType(String timberMark) {
    List<String> rows = jdbc.queryForList("""
        SELECT NVL(TRIM(b.file_type_code), ' ')
          FROM timber_mark t
          JOIN mark_billed_cli b ON b.timber_mark = t.timber_mark
         WHERE t.timber_mark = :tm""", new MapSqlParameterSource("tm", timberMark), String.class);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0).trim());
  }

  // ── Species / product / grade ──

  /** SPECIES_PRODUCT_GRADE_XREF by primary key ({@code SpeciesProductGradeQuery.selectByPK}). */
  public Optional<Interval> speciesProductGrade(String species, String product, String grade) {
    var p = new MapSqlParameterSource()
        .addValue("species", species)
        .addValue("product", product)
        .addValue("grade", grade);
    return interval("""
        SELECT effective_date, expiry_date
          FROM species_product_grade_xref
         WHERE scale_product_code = :product
           AND scale_grade_code = :grade
           AND scale_species_code = :species""", p);
  }

  /**
   * The grade in COAST_SCALE_GRADE_CODE or INTERIOR_SCALE_GRADE_CODE by
   * schedule — {@code ScaleGradeDAO.getScaleGradeInfo}. Empty for another
   * schedule code.
   */
  public Optional<Interval> scaleGrade(String grade, String schedule) {
    String s = schedule == null ? "" : schedule.trim();
    String sql;
    if (RatingRules.COAST.equalsIgnoreCase(s)) {
      sql = "SELECT effective_date, expiry_date FROM coast_scale_grade_code WHERE coast_scale_grade_code = :grade";
    } else if (RatingRules.INTERIOR.equalsIgnoreCase(s)) {
      sql = "SELECT effective_date, expiry_date FROM interior_scale_grade_code"
          + " WHERE interior_scale_grade_code = :grade";
    } else {
      return Optional.empty();
    }
    return interval(sql, new MapSqlParameterSource("grade", grade));
  }

  // ── District default rates ──

  /**
   * Active rates for the district / method / species (null = the "All" rows) /
   * product / grade — {@code HbsDistrictDefaultRateQuery.selectByCRange} as
   * P212/P213 called it (no date or rate criteria). Codes are compared as
   * given, untrimmed (the ungraded grade is one space).
   */
  public List<RateInterval> activeDistrictRates(long orgUnitNo, String method, String species, String product,
      String grade) {
    var p = new MapSqlParameterSource()
        .addValue("org", orgUnitNo)
        .addValue("method", method)
        .addValue("species", species, Types.VARCHAR)
        .addValue("product", product)
        .addValue("grade", grade);
    return jdbc.query("""
        SELECT distrt_id, effective_date, expiry_date
          FROM hbs_district_default_rate
         WHERE org_unit_no = :org
           AND appraisal_mthd_code = :method
           AND ((:species IS NULL AND scale_species_code IS NULL) OR scale_species_code = :species)
           AND scale_product_code = :product
           AND scale_grade_code = :grade
           AND active_ind = 'Y'
         ORDER BY distrt_id""", p, (rs, n) -> new RateInterval(rs.getLong(1),
        day(rs.getDate(2), LocalDate.MIN), day(rs.getDate(3), LocalDate.MAX)));
  }

  // ── Waste & residue rates ──

  /** Cut blocks of the mark ({@code DacHelper.findCutBlocksByCriteria(mark)}). */
  public long countCutBlocks(String timberMark) {
    return count("SELECT COUNT(*) FROM cut_block WHERE timber_mark = :tm", new MapSqlParameterSource("tm", timberMark));
  }

  /** CUT_BLOCK by primary key ({@code RateHelper.isValidCutBlock}). */
  public boolean cutBlockExists(String timberMark, String cutBlock) {
    var p = new MapSqlParameterSource().addValue("tm", timberMark).addValue("cb", cutBlock);
    return count("SELECT COUNT(*) FROM cut_block WHERE timber_mark = :tm AND cut_block_id = :cb", p) > 0;
  }

  /**
   * Active, avoidable log rates (product ' ') for the mark / cut block /
   * species / grade in effect on the day, optionally of one rate source —
   * {@code WasteResidueRateQuery.selectByCriteriaRange} with the P222/P223
   * arguments (active Y, avoidable Y, effective = expiry = the scale date).
   * Ordered by id: the legacy finder had no ORDER BY.
   */
  public List<Map<String, Object>> activeWasteRates(String timberMark, String cutBlock, String species,
      String grade, LocalDate day, String rateSource) {
    var p = new MapSqlParameterSource()
        .addValue("tm", timberMark)
        .addValue("cb", cutBlock)
        .addValue("species", species)
        .addValue("grade", grade)
        .addValue("day", Date.valueOf(day))
        .addValue("source", rateSource, Types.VARCHAR);
    return jdbc.queryForList("""
        SELECT *
          FROM waste_residue_rate
         WHERE timber_mark = :tm
           AND cut_block_id = :cb
           AND scale_species_code = :species
           AND scale_product_code = ' '
           AND scale_grade_code = :grade
           AND active_ind = 'Y'
           AND avoidable_ind = 'Y'
           AND effective_date <= :day
           AND expiry_date >= :day
           AND (:source IS NULL OR waste_rate_source_code = :source)
         ORDER BY wstrt_id""", p);
  }

  // ── helpers ──

  private static LocalDate day(Date d, LocalDate ifNull) {
    return d == null ? ifNull : d.toLocalDate();
  }

  private long count(String sql, MapSqlParameterSource p) {
    Long n = jdbc.queryForObject(sql, p, Long.class);
    return n == null ? 0 : n;
  }

  private Optional<Interval> interval(String sql, MapSqlParameterSource p) {
    List<Interval> rows = jdbc.query(sql, p, (rs, n) -> new Interval(
        day(rs.getDate(1), LocalDate.MIN), day(rs.getDate(2), LocalDate.MAX)));
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }
}
