package ca.bc.gov.nrs.hbs.api.summaryreturns.edits;

import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Rate existence for the SD08/SD10/SD11/SD17 edits: {@code RateManagerBean.pieceScaleReturnHasRates}
 * and {@code weightScaleReturnHasRates}, i.e. {@code ratePieceScaleReturn} /
 * {@code rateWeightScaleReturn} run for their exceptions only. The rates come from the
 * {@code HBS_RATES} package procs, called through {@link TableApi} (OUT parameters by name).
 */
@Component
public class Rates {

  static final String GET_OVERRIDE_AND_APPRAISAL = "HBS_RATES.GET_OVERRIDE_AND_APPRAISAL";
  static final String RETRIEVE_NORMAL_RATE = "HBS_RATES.RETRIEVE_NORMAL_RATE";
  static final String RETRIEVE_WASTE_RATE = "HBS_RATES.RETRIEVE_WASTE_RATE";
  static final String RETRIEVE_BEACHCOMB_RATE = "HBS_RATES.RETRIEVE_BEACHCOMB_RATE";

  /** {@code SegregationRate.NO_WORKSHEET / NO_STUMPAGE / OVERLAPPING_OVERRIDE_RULES}. */
  static final String NO_WORKSHEET = "NO WKSHT";
  static final String NO_STUMPAGE = "NO STUMP";
  static final String OVERLAPPING_OVERRIDE_RULES = "OVR OLAP";

  /** The legacy exceptions {@code validateRate} turns into SD08, SD10 and SD11. */
  public enum Failure { WORKSHEET_NOT_FOUND, STUMPAGE_NOT_FOUND, OVERLAPPING_RULES }

  /** {@code WorkSheetNotFoundException} / {@code StumpageNotFoundException} / {@code OverlappingRulesException}. */
  public static class RateException extends Exception {
    private final Failure failure;

    public RateException(Failure failure, String message) {
      super(message);
      this.failure = failure;
    }

    public Failure failure() {
      return failure;
    }
  }

  /** The OUT values of {@code Get_Override_And_Appraisal} ({@code OverrideRuleAndAppraisal}). */
  public record RuleAndAppraisal(Map<String, Object> out) {

    boolean ruleFound() {
      return "Y".equals(out.get("o_rate_rule_found"));
    }
  }

  /** {@code o_rate_found} and {@code o_failure_reason} of a rate proc. */
  public record RateResult(boolean found, String failureReason) {
  }

  private final TableApi api;
  private final Lookups lookups;

  public Rates(TableApi api, Lookups lookups) {
    this.api = api;
    this.lookups = lookups;
  }

  /** {@code DbMap.flagToBoolean} then {@code booleanToFlag}; legacy threw on an empty flag, here it is N. */
  static String flag(Object value) {
    return "Y".equals(value) ? "Y" : "N";
  }

  private static Long longOf(Object value) {
    return value == null ? null : ((Number) value).longValue();
  }

  private static Timestamp timestamp(Row row, String column) {
    return row.date(column);
  }

  // ── Piece ────────────────────────────────────────────────────────────────

  /**
   * {@code pieceScaleReturnHasRates} → {@code ratePieceScaleReturn}: private mark → rated; waste
   * return → waste rate per segregation; beachcomb → beachcomb rate per segregation; else the
   * mark's override rule / appraisal, then the normal rate per segregation. The first segregation
   * without a rate answers false.
   */
  public boolean pieceScaleReturnHasRates(SummaryReturn ret) throws RateException {
    Row v = ret.version();
    String mark = v.str("timber_mark");
    if (lookups.timberMark(mark).map(Lookups.TimberMark::isPrivateMark).orElse(false)) {
      return true;
    }
    boolean waste = SummaryReturnRules.PSSMRY_WASTE_TYPE.equals(v.str("pssmry_type"));
    boolean beachComb = SummaryReturnRules.CATEGORY_BEACHCOMB.equals(v.str("scl_rtn_category_code"));
    RuleAndAppraisal ruleAndApp = null;
    if (!waste && !beachComb) {
      ruleAndApp = overrideAndAppraisal(mark, v.str("field_scale_deck_id"), timestamp(v, "scale_date"));
    }
    for (Row seg : ret.segregations()) {
      if ("Y".equals(seg.str("sb4_ind"))) {
        continue; // SegregationRate.getScaleBeforeRate()
      }
      RateResult rate;
      if (waste) {
        if (!"Y".equals(seg.str("avoidable_ind"))) {
          continue; // getUnavoidableWasteRate()
        }
        rate = wasteRate(mark, seg, timestamp(v, "scale_date"), v.str("cut_block_id"));
        if (!rate.found()) {
          return false; // findWasteRate throws SegregationRateNotFoundException whatever the reason
        }
        continue;
      }
      if (beachComb) {
        String segMark = seg.str("timber_mark");
        if (segMark != null && !segMark.isEmpty() && privateMark(segMark)) {
          continue; // getPrivateMarkRate()
        }
        rate = beachcombRate(mark, seg, timestamp(v, "scale_date"));
        if (!rate.found()) {
          if (OVERLAPPING_OVERRIDE_RULES.equals(rate.failureReason())) {
            throw new RateException(Failure.OVERLAPPING_RULES, "findBeachcombRate");
          }
          return notFound(rate);
        }
        continue;
      }
      rate = normalRate(mark, seg.str("scale_species_code"), seg.str("scale_product_code"),
          seg.str("scale_grade_code"), timestamp(v, "scale_date"), ruleAndApp);
      if (!rate.found()) {
        return notFound(rate);
      }
    }
    return true;
  }

  /** {@code findNormalSegregationRate} / {@code findBeachcombRate}: WorkSheet / Stumpage not found, else no rate. */
  private static boolean notFound(RateResult rate) throws RateException {
    if (NO_WORKSHEET.equals(rate.failureReason())) {
      throw new RateException(Failure.WORKSHEET_NOT_FOUND, rate.failureReason());
    }
    if (NO_STUMPAGE.equals(rate.failureReason())) {
      throw new RateException(Failure.STUMPAGE_NOT_FOUND, rate.failureReason());
    }
    return false;
  }

  /** {@code RateManagerBean.privateMark}: the beachcomb segregation's mark must exist. */
  private boolean privateMark(String timberMark) {
    return lookups.timberMark(timberMark)
        .orElseThrow(() -> new ReleaseEditException("TimberMark not found for timberMark:" + timberMark))
        .isPrivateMark();
  }

  // ── Weight ───────────────────────────────────────────────────────────────

  /**
   * {@code weightScaleReturnHasRates} → {@code rateWeightScaleReturn}: final / nil bill and private
   * marks are rated; otherwise the override rule / appraisal, then the normal rate for each latest
   * stratum ratio's species/product/grade.
   */
  public boolean weightScaleReturnHasRates(SummaryReturn ret, List<Lookups.RatioStatistic> ratios)
      throws RateException {
    Row v = ret.version();
    String category = v.str("scl_rtn_category_code");
    if (SummaryReturnRules.CATEGORY_FINAL_BILL.equals(category) || SummaryReturnRules.CATEGORY_NIL_BILL.equals(category)) {
      return true;
    }
    String mark = v.str("timber_mark");
    if (lookups.timberMark(mark).map(Lookups.TimberMark::isPrivateMark).orElse(false)) {
      return true;
    }
    RuleAndAppraisal ruleAndApp = overrideAndAppraisal(mark, v.str("field_scale_deck_id"), timestamp(v, "scale_date"));
    for (Lookups.RatioStatistic ratio : ratios) {
      RateResult rate = normalRate(mark, ratio.species(), ratio.product(), ratio.grade(), timestamp(v, "scale_date"),
          ruleAndApp);
      if (!rate.found()) {
        return notFound(rate);
      }
    }
    return true;
  }

  // ── HBS_RATES calls ──────────────────────────────────────────────────────

  /** {@code getOverrideRuleAndAppraisal}: overlapping override rules raise {@code OverlappingRulesException}. */
  protected RuleAndAppraisal overrideAndAppraisal(String timberMark, String fieldScaleDeckId, Timestamp scaleDate)
      throws RateException {
    Map<String, Object> out = api.call(GET_OVERRIDE_AND_APPRAISAL, ProcArgs.of()
        .put("i_timber_mark", timberMark)
        .put("i_field_scale_deck_id", fieldScaleDeckId)
        .put("i_scale_date", scaleDate));
    if ("Y".equals(out.get("o_rate_rule_overlap"))) {
      throw new RateException(Failure.OVERLAPPING_RULES,
          "getOverrideRuleAndAppraisal overlapping rate rules for timberMark=[" + timberMark + "]");
    }
    return new RuleAndAppraisal(out);
  }

  /** {@code findNormalSegregationRate}: {@code Hbs_Rates.retrieve_normal_rate} with the rule / appraisal passed back in. */
  protected RateResult normalRate(String timberMark, String species, String product, String grade, Timestamp scaleDate,
      RuleAndAppraisal ruleAndApp) {
    Map<String, Object> o = ruleAndApp.out();
    Map<String, Object> out = api.call(RETRIEVE_NORMAL_RATE, ProcArgs.of()
        .put("i_timber_mark", timberMark)
        .put("i_species", species)
        .put("i_product", product)
        .put("i_grade", grade)
        .put("i_scale_date", scaleDate)
        .put("i_beachcomb_timber_mark", null)
        .put("i_cut_block", null)
        .put("i_avoidable", "N")
        .put("i_rate_rule_found", flag(o.get("o_rate_rule_found")))
        .put("i_rate_rule_id", longOf(o.get("o_rate_rule_id")))
        .put("i_rate_rule_type", ruleAndApp.ruleFound() ? o.get("o_rate_rule_type") : null)
        .put("i_rate_date", o.get("o_rate_date"))
        .put("i_worksheet_found", flag(o.get("o_worksheet_found")))
        .put("i_worksheet_eff_date", o.get("o_worksheet_eff_date"))
        .put("i_stumpage_found", flag(o.get("o_stumpage_found")))
        .put("i_other_species", o.get("o_other_species"))
        .put("i_appraisal_method", o.get("o_appraisal_method"))
        .put("i_direct_entry_ind", flag(o.get("o_direct_entry_ind")))
        .put("i_reserve_stumpage", o.get("o_reserve_stumpage"))
        .put("i_bonus_bid", o.get("o_bonus_bid"))
        .put("i_dev_levy", o.get("o_dev_levy"))
        .put("i_silv_levy", o.get("o_silv_levy"))
        .put("i_mark_district", longOf(o.get("o_mark_district")))
        .put("i_fixed_rates_found", flag(o.get("o_fixed_rates_found")))
        .put("i_species_list_found", flag(o.get("o_species_list_found")))
        .put("i_full_appraisal", flag(o.get("o_full_appraisal")))
        .put("i_conif_stand_rate_elig_code", o.get("o_conif_stand_rate_elig_code"))
        .put("i_decid_stand_rate_elig_code", o.get("o_decid_stand_rate_elig_code")));
    return result(out);
  }

  /** {@code findWasteRate}: {@code Hbs_Rates.retrieve_waste_rate} for an avoidable, not scaled-before segregation. */
  protected RateResult wasteRate(String timberMark, Row seg, Timestamp scaleDate, String cutBlock) {
    Map<String, Object> out = api.call(RETRIEVE_WASTE_RATE, ProcArgs.of()
        .put("i_timber_mark", timberMark)
        .put("i_species", seg.str("scale_species_code"))
        .put("i_product", seg.str("scale_product_code"))
        .put("i_grade", seg.str("scale_grade_code"))
        .put("i_scale_date", scaleDate)
        .put("i_beachcomb_timber_mark", null)
        .put("i_cut_block", cutBlock)
        .put("i_avoidable", "Y"));
    return result(out);
  }

  /**
   * {@code findBeachcombRate}: {@code Hbs_Rates.Retrieve_Beachcomb_Rate}. The legacy request never
   * carried the field scale deck id or a cut block for a beachcomb segregation (null).
   */
  protected RateResult beachcombRate(String timberMark, Row seg, Timestamp scaleDate) {
    Map<String, Object> out = api.call(RETRIEVE_BEACHCOMB_RATE, ProcArgs.of()
        .put("i_timber_mark", timberMark)
        .put("i_field_scale_deck_id", null)
        .put("i_species", seg.str("scale_species_code"))
        .put("i_product", seg.str("scale_product_code"))
        .put("i_grade", seg.str("scale_grade_code"))
        .put("i_scale_date", scaleDate)
        .put("i_beachcomb_seg_timber_mark", seg.str("timber_mark"))
        .put("i_cut_block", null)
        .put("i_avoidable", "N"));
    return result(out);
  }

  private static RateResult result(Map<String, Object> out) {
    return new RateResult("Y".equals(out.get("o_rate_found")), (String) out.get("o_failure_reason"));
  }
}
