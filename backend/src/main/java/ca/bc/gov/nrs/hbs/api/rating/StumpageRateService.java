package ca.bc.gov.nrs.hbs.api.rating;

import ca.bc.gov.nrs.hbs.api.rating.RatingInput.DateValue;
import ca.bc.gov.nrs.hbs.api.rating.RatingInput.Problems;
import ca.bc.gov.nrs.hbs.api.rating.RatingRules.Interval;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * P230/P231 Search for Stumpage Rate — the rate the billing would apply to one
 * segregation, from the legacy rating package {@code HBS_RATES}:
 * {@code $E/plu/RateManagerBean.findSegregationRate} (private mark → zero
 * rate; Beachcomb → {@code Retrieve_Beachcomb_Rate}; Waste →
 * {@code Retrieve_Waste_Rate}; otherwise {@code Get_Override_And_Appraisal} then
 * {@code Retrieve_Normal_Rate}), called as {@code SegregationRateResultsTag}
 * did (avoidable Y, not scaled-before). Read-only: the procedures only read,
 * but return through OUT parameters, so they are called through
 * {@link TableApi} (named, signature-checked) rather than a SELECT.
 *
 * <p>Input validation is validation.xml P230Form (scale date required, valid,
 * not in the future; timber mark required and in TIMBER_MARK +
 * MARK_BILLED_CLI) then {@code $V/plu/P230Action.validateForm}. The appraisal
 * block is {@code OpqHelper.getTimberMarkInfo}.
 */
@Service
public class StumpageRateService {

  /** P230 Return Type options. */
  public static final String NORMAL = "Normal";
  public static final String BEACHCOMB = "Beachcomb";
  public static final String WASTE = "Waste";

  /** {@code TimberMark.isPrivateMark} file types; {@code TimberMark.BEACHCOMB_FILETYPE}. */
  private static final Set<String> PRIVATE_FILE_TYPES = Set.of("B08", "B09", "B13", "B14", "B15", "B16");
  private static final String BEACHCOMB_FILE_TYPE = "B17";
  /** Segregation marks accepted without a timber mark lookup (P230Action). */
  private static final Set<String> BCOMB_MARKS = Set.of("BCOMB1", "BCOMB2", "BCOMB3");
  /** {@code OverrideRateRule.OVERRIDE_RATE}: the rate source naming an override rate. */
  private static final String OVERRIDE_RATE_SOURCE = "Override Rate";
  /** {@code SegregationRate.ZeroRateSource.PRIVATE_MARK}. */
  private static final String PRIVATE_MARK_SOURCE = "Private Mark";

  private final RatingRepository repo;
  private final TableApi tableApi;
  private final RatingClock clock;
  private final NamedParameterJdbcTemplate jdbc;

  @org.springframework.beans.factory.annotation.Autowired
  public StumpageRateService(RatingRepository repo, TableApi tableApi, RatingClock clock, JdbcTemplate jdbc) {
    this(repo, tableApi, clock, new NamedParameterJdbcTemplate(jdbc));
  }

  StumpageRateService(RatingRepository repo, TableApi tableApi, RatingClock clock, NamedParameterJdbcTemplate jdbc) {
    this.repo = repo;
    this.tableApi = tableApi;
    this.clock = clock;
    this.jdbc = jdbc;
  }

  /** The P230 criteria (field names as the SPA sends them). */
  public record Request(String timberMark, String fieldScaleDeckId, String species, String product, String grade,
      DateValue scaleDate, String returnType, String beachcombTimberMark, String cutBlock) {

    public static Request from(Map<String, Object> q) {
      String type = RatingInput.text(q, "returnType");
      return new Request(
          RatingInput.upper(q, "timberMark"),
          RatingInput.upper(q, "fieldScaleDeckId"),
          RatingInput.upper(q, "species"),
          RatingInput.product(q, "product"),
          RatingInput.upper(q, "grade"),
          RatingInput.date(q, "scaleDate"),
          type == null ? NORMAL : type,
          RatingInput.upper(q, "beachcombTimberMark"),
          RatingInput.upper(q, "cutBlock"));
    }
  }

  /** P231 result: the criteria echo is the SPA's; appraisal block and rate row (null rate = "Not Found"). */
  public Map<String, Object> lookup(Request r) {
    validate(r);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("appraisal", appraisal(r.timberMark(), r.scaleDate().date()));
    out.put("rate", rate(r));
    return out;
  }

  // ── validation ──

  void validate(Request r) {
    Problems p = new Problems();
    LocalDate scaleDate = r.scaleDate().date();
    if (RatingRules.validatorDate(p, "scaleDate", "Scale Date", r.scaleDate()) && scaleDate.isAfter(clock.today())) {
      p.add("scaleDate", RatingMessages.onOrBeforeToday("Scale Date"));
    }
    Optional<String> fileType = Optional.empty();
    if (r.timberMark() == null) {
      p.add("timberMark", RatingMessages.required("Timber Mark"));
    } else {
      fileType = repo.billedMarkFileType(r.timberMark());
      if (fileType.isEmpty()) p.add("timberMark", RatingMessages.TIMBER_MARK_REF);
    }
    if (!Set.of(NORMAL, BEACHCOMB, WASTE).contains(r.returnType())) {
      p.add("returnType", "Return Type is invalid.");
    }
    p.throwIfAny();

    boolean beachcombMark = fileType.map(BEACHCOMB_FILE_TYPE::equals).orElse(false);
    switch (r.returnType()) {
      case NORMAL -> {
        if (beachcombMark) p.add("timberMark", RatingMessages.RETURN_TYPE_BEACHCOMB);
        if (r.cutBlock() != null) p.add("cutBlock", RatingMessages.CUT_BLOCK_BLANK);
        if (r.beachcombTimberMark() != null) p.add("beachcombTimberMark", RatingMessages.BEACHCOMB_MARK_BLANK);
      }
      case BEACHCOMB -> {
        if (!beachcombMark) p.add("timberMark", RatingMessages.NOT_BEACHCOMB_MARK);
        if (r.cutBlock() != null) p.add("cutBlock", RatingMessages.CUT_BLOCK_BLANK);
        if (r.beachcombTimberMark() == null) {
          p.add("beachcombTimberMark", RatingMessages.BEACHCOMB_MARK_REQUIRED);
        } else if (!BCOMB_MARKS.contains(r.beachcombTimberMark())
            && !repo.timberMarkExists(r.beachcombTimberMark())) {
          p.add("beachcombTimberMark", RatingMessages.BEACHCOMB_MARK_INVALID);
        }
      }
      default -> { // WASTE
        if (beachcombMark) p.add("timberMark", RatingMessages.RETURN_TYPE_BEACHCOMB);
        if (r.cutBlock() == null) {
          p.add("cutBlock", RatingMessages.WASTE_CUT_BLOCK_REQUIRED);
        } else if (!repo.cutBlockExists(r.timberMark(), r.cutBlock())) {
          p.add("cutBlock", RatingMessages.CUT_BLOCK_INVALID_FOR_MARK);
        }
        if (r.beachcombTimberMark() != null) p.add("beachcombTimberMark", RatingMessages.BEACHCOMB_MARK_BLANK);
      }
    }
    if (r.species() == null) p.add("species", RatingMessages.SPECIES_REQUIRED);
    if (r.product() == null) p.add("product", RatingMessages.PRODUCT_REQUIRED);
    if (r.grade() == null) p.add("grade", RatingMessages.GRADE_REQUIRED);
    RatingRules.deck(p, r.fieldScaleDeckId());

    // BT 10113: species-product-grade in effect on the scale date; logs need a grade of the mark's schedule.
    if (r.species() != null && r.grade() != null) {
      Optional<Interval> spg = repo.speciesProductGrade(r.species(), r.product(), r.grade());
      if (spg.isEmpty()) {
        p.add("species", RatingMessages.SPG_INVALID);
      } else if (!spg.get().contains(scaleDate)) {
        p.add("species", RatingMessages.SPG_NOT_ON_SCALE_DATE);
      } else if (RatingInput.LOGS.equals(r.product())) {
        String schedule = gradeScheduleType(r.timberMark(), scaleDate);
        boolean coast = RatingRules.COAST.equalsIgnoreCase(schedule);
        boolean interior = RatingRules.INTERIOR.equalsIgnoreCase(schedule);
        Optional<Interval> grade = repo.scaleGrade(r.grade(), schedule);
        if (grade.isEmpty()) {
          if (coast) p.add("grade", RatingMessages.GRADE_COAST);
          else if (interior) p.add("grade", RatingMessages.GRADE_INTERIOR);
        } else if (!grade.get().contains(scaleDate)) {
          if (coast) p.add("grade", RatingMessages.GRADE_COAST_DATE);
          else if (interior) p.add("grade", RatingMessages.GRADE_INTERIOR_DATE);
        }
      }
    }
    p.throwIfAny();
  }

  /**
   * {@code AppraisalWorksheetQuery.selectGradeScheduleTypeCode}: the method of
   * the confirmed appraisal in effect on the date, else the schedule of the
   * mark's forest region (legacy and current region codes).
   */
  String gradeScheduleType(String timberMark, LocalDate day) {
    var params = new MapSqlParameterSource().addValue("tm", timberMark).addValue("day", Date.valueOf(day));
    List<String> rows = jdbc.queryForList("""
        SELECT NVL(code,
                   (SELECT DECODE(MAX(o.org_unit_code),
                                  'RNI', 'I', 'RSI', 'I', 'RCO', 'C',
                                  'RSK', 'I', 'RNO', 'I', 'ROM', 'I', 'RTO', 'I', 'RCB', 'I', 'RKB', 'I',
                                  'RSC', 'C', 'RWC', 'C')
                      FROM org_unit o, mark_billed_cli m
                     WHERE m.timber_mark = :tm
                       AND m.forest_region = o.org_unit_no))
          FROM (SELECT MAX(a.appraisal_mthd_cd) code
                  FROM app_worksheet a, timber_mark t
                 WHERE t.timber_mark = :tm
                   AND a.timber_mark = t.timber_mark
                   AND :day BETWEEN a.app_effective_date AND a.expiry_date
                   AND a.appraisal_sts_st = 'CNF')""", params, String.class);
    return rows.isEmpty() || rows.get(0) == null ? "" : rows.get(0).trim();
  }

  // ── P231 appraisal block (OpqHelper.getTimberMarkInfo) ──

  Map<String, Object> appraisal(String timberMark, LocalDate day) {
    var params = new MapSqlParameterSource().addValue("tm", timberMark).addValue("day", Date.valueOf(day));
    Map<String, Object> out = new LinkedHashMap<>();
    List<Map<String, Object>> org = jdbc.queryForList("""
        SELECT od.org_unit_name AS admin_org, orr.org_unit_name AS region
          FROM mark_billed_cli mbc
          LEFT JOIN org_unit od ON od.org_unit_no = mbc.forest_district
          LEFT JOIN org_unit orr ON orr.org_unit_no = mbc.forest_region
         WHERE mbc.timber_mark = :tm""", params);
    out.put("adminOrg", org.isEmpty() ? null : org.get(0).get("ADMIN_ORG"));
    out.put("region", org.isEmpty() ? null : org.get(0).get("REGION"));
    // A confirmed appraisal with a method, in effect on the scale date (day-inclusive); else "N/A".
    List<Map<String, Object>> app = jdbc.queryForList("""
        SELECT aw.appraisal_mthd_cd, clt.expanded_result AS method_description,
               aw.app_effective_date, aw.expiry_date
          FROM app_worksheet aw
          LEFT JOIN code_list_table clt ON clt.column_name = 'APPRAISAL_MTHD_COD'
                                       AND clt.code_argument = aw.appraisal_mthd_cd
         WHERE aw.timber_mark = :tm
           AND aw.appraisal_sts_st = 'CNF'
           AND TRIM(aw.appraisal_mthd_cd) IS NOT NULL
           AND :day BETWEEN TRUNC(aw.app_effective_date) AND TRUNC(aw.expiry_date)
         ORDER BY aw.app_effective_date DESC""", params);
    if (app.isEmpty()) {
      out.put("appraisalMethod", "N/A");
      out.put("appraisalMethodDescription", "N/A");
      out.put("appEffectiveDate", null);
      out.put("appExpiryDate", null);
    } else {
      Map<String, Object> a = app.get(0);
      out.put("appraisalMethod", a.get("APPRAISAL_MTHD_CD"));
      out.put("appraisalMethodDescription", a.get("METHOD_DESCRIPTION"));
      out.put("appEffectiveDate", isoDay(a.get("APP_EFFECTIVE_DATE")));
      out.put("appExpiryDate", isoDay(a.get("EXPIRY_DATE")));
    }
    return out;
  }

  // ── the rate (RateManagerBean.findSegregationRate) ──

  /** The rate row, or null for "Not Found" (RateHelper.findSegregationRate swallowed every not-found/overlap). */
  Map<String, Object> rate(Request r) {
    if (isPrivateMark(r.timberMark())) {
      return zeroRate(PRIVATE_MARK_SOURCE);
    }
    Timestamp scaleDate = Timestamp.valueOf(r.scaleDate().date().atStartOfDay());
    // An Oracle error is not a "Not Found": it propagates (HBSJDBCException in the legacy bean).
    return switch (r.returnType()) {
      case BEACHCOMB -> beachcomb(r, scaleDate);
      case WASTE -> waste(r, scaleDate);
      default -> normal(r, scaleDate);
    };
  }

  private boolean isPrivateMark(String timberMark) {
    return repo.billedMarkFileType(timberMark).map(PRIVATE_FILE_TYPES::contains).orElse(false);
  }

  private Map<String, Object> beachcomb(Request r, Timestamp scaleDate) {
    if (r.beachcombTimberMark() != null && isPrivateMark(r.beachcombTimberMark())) {
      return zeroRate(PRIVATE_MARK_SOURCE);
    }
    Map<String, Object> out = tableApi.call("HBS_RATES.RETRIEVE_BEACHCOMB_RATE", ProcArgs.of()
        .put("i_timber_mark", r.timberMark())
        .put("i_field_scale_deck_id", r.fieldScaleDeckId())
        .put("i_species", r.species())
        .put("i_product", r.product())
        .put("i_grade", r.grade())
        .put("i_scale_date", scaleDate)
        .put("i_beachcomb_seg_timber_mark", r.beachcombTimberMark())
        .put("i_cut_block", r.cutBlock())
        .put("i_avoidable", "Y"));
    if (!"Y".equals(out.get("o_rate_found"))) {
      return null;
    }
    Map<String, Object> rate = components(out);
    rate.put("overrideRuleId", out.get("o_override_rule_id"));
    return rate;
  }

  private Map<String, Object> waste(Request r, Timestamp scaleDate) {
    Map<String, Object> out = tableApi.call("HBS_RATES.RETRIEVE_WASTE_RATE", ProcArgs.of()
        .put("i_timber_mark", r.timberMark())
        .put("i_species", r.species())
        .put("i_product", r.product())
        .put("i_grade", r.grade())
        .put("i_scale_date", scaleDate)
        .put("i_beachcomb_timber_mark", r.beachcombTimberMark())
        .put("i_cut_block", r.cutBlock())
        .put("i_avoidable", "Y"));
    if (!"Y".equals(out.get("o_rate_found"))) {
      return null;
    }
    Map<String, Object> rate = components(out);
    rate.put("wasteRateId", out.get("o_rate_id"));
    return rate;
  }

  private Map<String, Object> normal(Request r, Timestamp scaleDate) {
    Map<String, Object> rule = tableApi.call("HBS_RATES.GET_OVERRIDE_AND_APPRAISAL", ProcArgs.of()
        .put("i_timber_mark", r.timberMark())
        .put("i_field_scale_deck_id", r.fieldScaleDeckId())
        .put("i_scale_date", scaleDate));
    if ("Y".equals(rule.get("o_rate_rule_overlap"))) {
      return null; // OverlappingRulesException → "Not Found"
    }
    boolean ruleFound = "Y".equals(rule.get("o_rate_rule_found"));
    Object ruleId = rule.get("o_rate_rule_id");
    String ruleType = ruleFound ? (String) rule.get("o_rate_rule_type") : null;
    Object rateDate = rule.get("o_rate_date");

    Map<String, Object> out = tableApi.call("HBS_RATES.RETRIEVE_NORMAL_RATE", ProcArgs.of()
        .put("i_timber_mark", r.timberMark())
        .put("i_species", r.species())
        .put("i_product", r.product())
        .put("i_grade", r.grade())
        .put("i_scale_date", scaleDate)
        .put("i_beachcomb_timber_mark", r.beachcombTimberMark())
        .put("i_cut_block", r.cutBlock())
        .put("i_avoidable", "Y")
        .put("i_rate_rule_found", flag(rule.get("o_rate_rule_found")))
        .put("i_rate_rule_id", ruleId)
        .put("i_rate_rule_type", ruleType)
        .put("i_rate_date", rateDate)
        .put("i_worksheet_found", flag(rule.get("o_worksheet_found")))
        .put("i_worksheet_eff_date", rule.get("o_worksheet_eff_date"))
        .put("i_stumpage_found", flag(rule.get("o_stumpage_found")))
        .put("i_other_species", rule.get("o_other_species"))
        .put("i_appraisal_method", rule.get("o_appraisal_method"))
        .put("i_direct_entry_ind", flag(rule.get("o_direct_entry_ind")))
        .put("i_reserve_stumpage", rule.get("o_reserve_stumpage"))
        .put("i_bonus_bid", rule.get("o_bonus_bid"))
        .put("i_dev_levy", rule.get("o_dev_levy"))
        .put("i_silv_levy", rule.get("o_silv_levy"))
        .put("i_mark_district", rule.get("o_mark_district"))
        .put("i_fixed_rates_found", flag(rule.get("o_fixed_rates_found")))
        .put("i_species_list_found", flag(rule.get("o_species_list_found")))
        .put("i_full_appraisal", flag(rule.get("o_full_appraisal")))
        .put("i_conif_stand_rate_elig_code", rule.get("o_conif_stand_rate_elig_code"))
        .put("i_decid_stand_rate_elig_code", rule.get("o_decid_stand_rate_elig_code")));
    if (!"Y".equals(out.get("o_rate_found"))) {
      return null;
    }
    Map<String, Object> rate = components(out);
    String source = (String) out.get("o_rate_source");
    // findNormalSegregationRate (BT 3693): the rule is reported for an override-rate source or a date rule.
    if ((ruleId != null && OVERRIDE_RATE_SOURCE.equalsIgnoreCase(source)) || "RTDTRL".equals(ruleType)) {
      rate.put("overrideRuleId", ruleId);
      if ("OVRRDRULE".equals(ruleType) && out.get("o_override_rate_id") != null) {
        rate.put("overrideRateId", out.get("o_override_rate_id"));
      }
      rate.put("overrideRateDate", isoDay(rateDate));
      rate.put("overrideRuleType", ruleType == null ? null : ("RTDTRL".equals(ruleType) ? "Date" : "Rate"));
    }
    return rate;
  }

  /** DbMap.booleanToFlag(DbMap.flagToBoolean(x)): Y stays Y, anything else N. */
  private static String flag(Object v) {
    return "Y".equals(v) ? "Y" : "N";
  }

  private static Map<String, Object> components(Map<String, Object> out) {
    Map<String, Object> rate = new LinkedHashMap<>();
    BigDecimal reserve = num(out.get("o_reserve_stumpage"));
    BigDecimal bonus = num(out.get("o_bonus_bid"));
    BigDecimal dev = num(out.get("o_dev_levy"));
    BigDecimal silv = num(out.get("o_silv_levy"));
    rate.put("totalRate", zero(reserve).add(zero(bonus)).add(zero(dev)).add(zero(silv)));
    rate.put("rateSource", out.get("o_rate_source"));
    rate.put("reserveStumpage", reserve);
    rate.put("bonusBid", bonus);
    rate.put("silvLevy", silv);
    rate.put("devLevy", dev);
    return rate;
  }

  /** {@code SegregationRate.getPrivateMarkRate()}: every component 0.00. */
  private static Map<String, Object> zeroRate(String source) {
    Map<String, Object> rate = new LinkedHashMap<>();
    BigDecimal z = new BigDecimal("0.00");
    rate.put("totalRate", z);
    rate.put("rateSource", source);
    rate.put("reserveStumpage", z);
    rate.put("bonusBid", z);
    rate.put("silvLevy", z);
    rate.put("devLevy", z);
    return rate;
  }

  private static BigDecimal num(Object v) {
    if (v == null) return null;
    if (v instanceof BigDecimal b) return b;
    return new BigDecimal(v.toString());
  }

  private static BigDecimal zero(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }

  private static String isoDay(Object v) {
    if (v == null) return null;
    if (v instanceof Timestamp t) return t.toLocalDateTime().toLocalDate().toString();
    if (v instanceof java.sql.Date d) return d.toLocalDate().toString();
    if (v instanceof java.util.Date d) return new Timestamp(d.getTime()).toLocalDateTime().toLocalDate().toString();
    String s = v.toString();
    return s.length() >= 10 ? s.substring(0, 10) : s;
  }
}
