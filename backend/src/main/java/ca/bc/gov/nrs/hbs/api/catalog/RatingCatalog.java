package ca.bc.gov.nrs.hbs.api.catalog;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.query.CommandDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryCatalog;
import ca.bc.gov.nrs.hbs.api.query.QueryDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter.Type;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Rating (legacy tab "Rating", module {@code plu}, landing page P199).
 *
 * <p>Ported from {@code hbs-ejb/.../hbs/plu} ({@code OverrideRateRuleQuery},
 * {@code HbsDistrictDefaultRateQuery}, {@code WasteResidueRateQuery},
 * {@code RateManagerBean}) and the report procs HBS2R201 / HBS3R211 / HBS3R221.
 *
 * <p>Read side only. The writes (with the validations the legacy Struts
 * actions ran before the procs: rule / rate interval overlap, 7-year
 * back-dating limit, SPG validity, cut block cross-reference, status gating)
 * are workflow commands in {@code ca.bc.gov.nrs.hbs.api.rating}; the P230
 * stumpage rate lookup is {@code StumpageRateController}.
 */
@Component
public class RatingCatalog implements QueryCatalog {

  /** Override rule columns shared by the list and the detail (updateTimestamp for the workflows' RowLock). */
  private static final String RULE_COLUMNS = """
      SELECT orr.override_rating_rule_id,
             orr.timber_mark,
             orr.ovrrdrtrl_type,
             DECODE(orr.ovrrdrtrl_type, 'OVRRDRULE', 'Rate', 'Date') AS rule_type,
             orr.override_rule_category_code,
             orr.override_rule_status_code,
             orsc.description AS rule_status,
             orr.field_scale_deck_id,
             orr.scale_effective_date,
             orr.scale_expiry_date,
             orr.processing_effective_date,
             orr.processing_expiry_date,
             orr.override_rate_date,
             orr.ovrrdrtrl_comment,
             orr.entry_userid,
             orr.entry_timestamp,
             orr.update_timestamp""";

  private static final String RULE_FROM = """
        FROM override_rating_rule orr
        LEFT JOIN override_rule_status_code orsc
          ON orsc.override_rule_status_code = orr.override_rule_status_code
       WHERE 1=1""";

  @Override
  public List<QueryDefinition> queries() {
    return List.of(
        // ── Code lists ──────────────────────────────────────────────
        CodesCatalog.codeTable("codes.rating.species", "SCALE_SPECIES_CODE"),
        CodesCatalog.codeTable("codes.rating.products", "SCALE_PRODUCT_CODE"),
        CodesCatalog.codeTable("codes.rating.grades", "SCALE_GRADE_CODE"),
        CodesCatalog.codeTable("codes.rating.wasteRateSources", "WASTE_RATE_SOURCE_CODE"),
        CodesCatalog.codeTable("codes.rating.ruleStatuses", "OVERRIDE_RULE_STATUS_CODE"),
        // hbs3:appraisalMethod — CODE_LIST_TABLE column APPRAISAL_MTHD_COD (C / I).
        QueryDefinition.builder("codes.rating.appraisalMethods")
            .legacy("P210")
            .capability(Capability.ANY_USER)
            .sql("""
                SELECT clt.code_argument AS code, clt.expanded_result AS description
                  FROM code_list_table clt
                 WHERE clt.column_name = 'APPRAISAL_MTHD_COD'
                   AND SYSDATE BETWEEN clt.effective_date AND clt.expiry_date""")
            .orderBy("clt.expanded_result")
            .maxRows(100)
            .build(),
        // hbs1:orgUnits (districts only) on P210 — keyed by ORG_UNIT_NO.
        QueryDefinition.builder("codes.rating.districts")
            .legacy("P210")
            .capability(Capability.ANY_USER)
            .sql("""
                SELECT TO_CHAR(ou.org_unit_no) AS code, ou.org_unit_code || ' - ' || ou.org_unit_name AS description
                  FROM org_unit ou
                 WHERE ou.org_level_code = 'D'
                   AND SYSDATE BETWEEN ou.effective_date AND ou.expiry_date""")
            .orderBy("ou.org_unit_name")
            .maxRows(500)
            .build(),
        // P240 Species: code subset APP_CON_SPECIES_ST ("All Coniferous" = empty).
        codeSubset("codes.rating.coniferousSpecies", "APP_CON_SPECIES_ST"),

        // ── P200/P201 Search for Override Rules / List of Override Rules ──
        // OverrideRateRuleQuery.selectByDate + HBS2R201 (columns).
        QueryDefinition.builder("rating.overrideRules.search")
            .legacy("P200/P201")
            .capability(Capability.RATING_MINISTRY_VIEW)
            .sql(RULE_COLUMNS + "\n" + RULE_FROM)
            .filter(QueryFilter.required("timberMark", "AND orr.timber_mark = :timberMark", Type.UPPER))
            // Regular Rules / Rules for All Decks / Rules for a Single Deck (BT10012).
            .filter(QueryFilter.required("ruleTypeFS",
                "AND ((:ruleTypeFS = 'regularRule' AND orr.field_scale_deck_id IS NULL)"
                    + " OR (:ruleTypeFS = 'allDeckFS' AND orr.field_scale_deck_id IS NOT NULL)"
                    + " OR :ruleTypeFS = 'singleDeckFS')", Type.STRING))
            .filter(QueryFilter.upper("singleDeck", "AND orr.field_scale_deck_id = :singleDeck"))
            // Query.appendWhereClause(eff, exp, from, to): interval overlap.
            .filter(QueryFilter.required("scaledFrom", "AND orr.scale_expiry_date >= :scaledFrom", Type.DATE))
            .filter(QueryFilter.required("scaledTo", "AND orr.scale_effective_date <= :scaledTo", Type.DATE))
            .filter(QueryFilter.required("processedFrom", "AND orr.processing_expiry_date >= :processedFrom", Type.DATE))
            .filter(QueryFilter.required("processedTo", "AND orr.processing_effective_date <= :processedTo", Type.DATE))
            // Hidden ruleStatus passed by Billing P841 drill-in links.
            .filter(QueryFilter.upper("ruleStatus", "AND orr.override_rule_status_code = :ruleStatus"))
            .sort("overrideRatingRuleId", "orr.override_rating_rule_id")
            .sort("ruleType", "orr.ovrrdrtrl_type")
            .sort("ruleStatus", "orsc.description")
            .sort("scaleEffectiveDate", "orr.scale_effective_date")
            .sort("processingEffectiveDate", "orr.processing_effective_date")
            .orderBy("orr.override_rule_status_code, orr.processing_effective_date, orr.processing_expiry_date,"
                + " orr.scale_effective_date, orr.scale_expiry_date")
            .maxRows(2000)
            .build(),

        // ── P202 / P206 / P208 View Override Rate / Date Rule ──
        // OverrideRateRuleQuery.selectByPK + return counts from
        // HBS_GET_OVRRATE_RETURN_COUNT (as HBS2R201 does; P202 used
        // RateManager.requestScaleReturnCount).
        QueryDefinition.builder("rating.overrideRules.detail")
            .legacy("P202/P206/P208")
            .capability(Capability.RATING_MINISTRY_VIEW)
            .sql(RULE_COLUMNS + """
                ,
                       hbs_get_ovrrate_return_count(TO_CHAR(orr.override_rating_rule_id), orr.timber_mark,
                         orr.scale_effective_date, orr.scale_expiry_date, 'PRC', orr.field_scale_deck_id) AS piece_return_count,
                       hbs_get_ovrrate_return_count(TO_CHAR(orr.override_rating_rule_id), orr.timber_mark,
                         orr.scale_effective_date, orr.scale_expiry_date, 'WRC', orr.field_scale_deck_id) AS weight_return_count,
                       hbs_get_ovrrate_return_count(TO_CHAR(orr.override_rating_rule_id), orr.timber_mark,
                         orr.scale_effective_date, orr.scale_expiry_date, 'PRFC', orr.field_scale_deck_id) AS piece_future_return_count,
                       hbs_get_ovrrate_return_count(TO_CHAR(orr.override_rating_rule_id), orr.timber_mark,
                         orr.scale_effective_date, orr.scale_expiry_date, 'WRFC', orr.field_scale_deck_id) AS weight_future_return_count
                """ + RULE_FROM)
            .filter(QueryFilter.required("overrideRatingRuleId",
                "AND orr.override_rating_rule_id = :overrideRatingRuleId", Type.NUMBER))
            .maxRows(1)
            .build(),

        // P202 return counts as rows, each with the parameters of the legacy drill-down link
        // ($W/plu/P202.jsp → summaryScaleReturnSearch.do?actionType=FilterSearch): confirmed
        // returns by override rule id (+ scale dates, field deck), candidate returns by timber
        // mark with chcStatus=OvrRule (HBS2R051/HBS2R101 strAndVersionStatusCodeOvr: not LCK /
        // ISS / CAN / DEL, category not WA) — summaryReturns.search status OVRRULE.
        QueryDefinition.builder("rating.overrideRules.returnCounts")
            .legacy("P202")
            .capability(Capability.RATING_MINISTRY_VIEW)
            .sql("""
                SELECT c.* FROM (
                  SELECT orr.override_rating_rule_id, 1 AS row_order, 'Confirmed Returns' AS return_kind,
                         'P' AS return_type, 'Piece Scale' AS scale_type,
                         hbs_get_ovrrate_return_count(TO_CHAR(orr.override_rating_rule_id), orr.timber_mark,
                           orr.scale_effective_date, orr.scale_expiry_date, 'PRC', orr.field_scale_deck_id) AS return_count,
                         orr.override_rating_rule_id AS link_rule_id, CAST(NULL AS VARCHAR2(6)) AS link_timber_mark,
                         CAST(NULL AS VARCHAR2(7)) AS link_status,
                         orr.scale_effective_date AS scale_date_from, orr.scale_expiry_date AS scale_date_to,
                         orr.field_scale_deck_id
                    FROM override_rating_rule orr
                  UNION ALL
                  SELECT orr.override_rating_rule_id, 2, 'Confirmed Returns', 'W', 'Weight Scale',
                         hbs_get_ovrrate_return_count(TO_CHAR(orr.override_rating_rule_id), orr.timber_mark,
                           orr.scale_effective_date, orr.scale_expiry_date, 'WRC', orr.field_scale_deck_id),
                         orr.override_rating_rule_id, NULL, NULL,
                         orr.scale_effective_date, orr.scale_expiry_date, orr.field_scale_deck_id
                    FROM override_rating_rule orr
                  UNION ALL
                  SELECT orr.override_rating_rule_id, 3, 'Candidate Returns', 'P', 'Piece Scale',
                         hbs_get_ovrrate_return_count(TO_CHAR(orr.override_rating_rule_id), orr.timber_mark,
                           orr.scale_effective_date, orr.scale_expiry_date, 'PRFC', orr.field_scale_deck_id),
                         NULL, orr.timber_mark, 'OVRRULE',
                         orr.scale_effective_date, orr.scale_expiry_date, orr.field_scale_deck_id
                    FROM override_rating_rule orr
                  UNION ALL
                  SELECT orr.override_rating_rule_id, 4, 'Candidate Returns', 'W', 'Weight Scale',
                         hbs_get_ovrrate_return_count(TO_CHAR(orr.override_rating_rule_id), orr.timber_mark,
                           orr.scale_effective_date, orr.scale_expiry_date, 'WRFC', orr.field_scale_deck_id),
                         NULL, orr.timber_mark, 'OVRRULE',
                         orr.scale_effective_date, orr.scale_expiry_date, orr.field_scale_deck_id
                    FROM override_rating_rule orr
                ) c
                 WHERE 1=1""")
            .filter(QueryFilter.required("overrideRatingRuleId",
                "AND c.override_rating_rule_id = :overrideRatingRuleId", Type.NUMBER))
            .orderBy("c.row_order")
            .maxRows(4)
            .build(),

        // P202 "Override Rate Details" table (null species / grade shown as "All").
        QueryDefinition.builder("rating.overrideRates.list")
            .legacy("P202/P204/P207")
            .capability(Capability.RATING_MINISTRY_VIEW)
            .sql("""
                SELECT ort.override_rate_id,
                       ort.override_rating_rule_id,
                       ort.scale_species_code,
                       NVL(ssc.description, 'All') AS species,
                       ort.scale_product_code,
                       spc.description AS product,
                       ort.scale_grade_code,
                       NVL(sgc.description, 'All') AS grade,
                       ort.override_rate,
                       ort.entry_userid,
                       ort.entry_timestamp,
                       ort.update_timestamp,
                       orr.override_rule_status_code
                  FROM override_rate ort
                  JOIN override_rating_rule orr ON orr.override_rating_rule_id = ort.override_rating_rule_id
                  LEFT JOIN scale_species_code ssc ON ssc.scale_species_code = ort.scale_species_code
                  LEFT JOIN scale_product_code spc ON spc.scale_product_code = ort.scale_product_code
                  LEFT JOIN scale_grade_code sgc ON sgc.scale_grade_code = ort.scale_grade_code
                 WHERE 1=1""")
            .filter(QueryFilter.number("overrideRatingRuleId", "AND ort.override_rating_rule_id = :overrideRatingRuleId"))
            .filter(QueryFilter.number("overrideRateId", "AND ort.override_rate_id = :overrideRateId"))
            .orderBy("ort.scale_species_code, ort.scale_product_code, ort.scale_grade_code")
            .maxRows(500)
            .build(),

        // ── P210/P211 District Default Rates ──
        // HbsDistrictDefaultRateQuery.selectByCriteria(orgUnitNo, method, eff, exp)
        // + HBS3R211 (columns/joins).
        QueryDefinition.builder("rating.districtRates.search")
            .legacy("P210/P211")
            .capability(Capability.RATING_MINISTRY_VIEW)
            .sql(districtRateSql())
            .filter(QueryFilter.number("orgUnitNo", "AND ddr.org_unit_no = :orgUnitNo"))
            .filter(QueryFilter.upper("appraisalMethod", "AND ddr.appraisal_mthd_code = :appraisalMethod"))
            .filter(QueryFilter.required("effectiveFrom", "AND ddr.expiry_date >= :effectiveFrom", Type.DATE))
            .filter(QueryFilter.required("effectiveTo", "AND ddr.effective_date <= :effectiveTo", Type.DATE))
            .sort("district", "ou.org_unit_code")
            .sort("species", "ddr.scale_species_code")
            .sort("product", "ddr.scale_product_code")
            .sort("grade", "ddr.scale_grade_code")
            .sort("rate", "ddr.rate")
            .sort("effectiveDate", "ddr.effective_date")
            .sort("expiryDate", "ddr.expiry_date")
            .orderBy("ou.org_unit_code, ddr.appraisal_mthd_code, ddr.scale_species_code, ddr.scale_product_code,"
                + " ddr.scale_grade_code, ddr.effective_date")
            .maxRows(2000)
            .build(),
        // P213 load (HbsDistrictDefaultRateQuery.selectByPK).
        QueryDefinition.builder("rating.districtRates.detail")
            .legacy("P213")
            .capability(Capability.RATING_MINISTRY_VIEW)
            .sql(districtRateSql())
            .filter(QueryFilter.required("distrtId", "AND ddr.distrt_id = :distrtId", Type.NUMBER))
            .maxRows(1)
            .build(),

        // ── P220/P221 Waste & Residue Rates ──
        // WasteResidueRateQuery.selectByCriteria(mark, cutBlock, eff, exp) + HBS3R221.
        QueryDefinition.builder("rating.wasteRates.search")
            .legacy("P220/P221")
            .capability(Capability.RATING_VIEW)
            .sql(wasteRateSql())
            .filter(QueryFilter.required("timberMark", "AND wrr.timber_mark = :timberMark", Type.UPPER))
            // "ALL" (or blank) = every cut block.
            .filter(QueryFilter.upper("cutBlock", "AND (:cutBlock = 'ALL' OR wrr.cut_block_id = :cutBlock)"))
            .filter(QueryFilter.required("scaleDateFrom", "AND wrr.expiry_date >= :scaleDateFrom", Type.DATE))
            .filter(QueryFilter.required("scaleDateTo", "AND wrr.effective_date <= :scaleDateTo", Type.DATE))
            .sort("cutBlockId", "wrr.cut_block_id")
            .sort("effectiveDate", "wrr.effective_date")
            .sort("rate", "wrr.rate")
            .orderBy("wrr.timber_mark, wrr.cut_block_id, wrr.effective_date, wrr.scale_species_code,"
                + " wrr.scale_grade_code, wrr.active_ind")
            .maxRows(2000)
            .build(),
        // P223 load (WasteResidueRateQuery.selectByPK).
        QueryDefinition.builder("rating.wasteRates.detail")
            .legacy("P223")
            .capability(Capability.RATING_VIEW)
            .sql(wasteRateSql())
            .filter(QueryFilter.required("wstrtId", "AND wrr.wstrt_id = :wstrtId", Type.NUMBER))
            .maxRows(1)
            .build(),

        // ── P240/P241 Compute Waste & Residue Average Rate ──
        // RateManagerBean.findHarvestRates + WasteResidueRateQuery.selectByHarvestCriteria:
        // sum(royalty + reserve + bonus + dev levy + silv levy) / sum(volume billed),
        // rounded to 2 decimals and capped at 999.99. Saw logs (product ' '),
        // billing types NP/CR, species in HDBS_VV_SPECIES_ST, grade in WASTE_RATE_GRD_ST.
        QueryDefinition.builder("rating.wasteAverageRate")
            .legacy("P240/P241")
            .capability(Capability.RATING_VIEW)
            .sql("""
                SELECT LEAST(NVL(ROUND(SUM(h.royalty_amount + h.reserve_stmpg_amt + h.bonus_stumpage_amt
                                        + h.dev_levy_amount + h.silv_levy_amount)
                                     / NULLIF(SUM(h.volume_billed), 0), 2), 0), 999.99) AS average_rate,
                       SUM(h.volume_billed) AS volume_billed,
                       COUNT(*) AS harvest_records
                  FROM harvest_history h
                 WHERE h.forest_product_cde = ' '
                   AND h.billing_type_code IN ('NP', 'CR')
                   AND h.hdbs_tree_species IN (SELECT cs.code_argument FROM code_subset_tbl cs
                                                WHERE cs.code_subset_name = 'HDBS_VV_SPECIES_ST')
                   AND h.log_grade IN (SELECT cs.code_argument FROM code_subset_tbl cs
                                        WHERE cs.code_subset_name = 'WASTE_RATE_GRD_ST')""")
            .filter(QueryFilter.required("timberMark", "AND h.timber_mark = :timberMark", Type.UPPER))
            .filter(QueryFilter.required("periodStart", "AND h.billing_period >= :periodStart", Type.DATE))
            .filter(QueryFilter.required("periodEnd", "AND h.billing_period <= :periodEnd", Type.DATE))
            .filter(QueryFilter.upper("species", "AND h.hdbs_tree_species = :species"))
            // grade 'ALL' = "All Eligible": exclude X, and HE/BA in U grade (BT 12721).
            .filter(QueryFilter.required("grade",
                "AND ((:grade = 'ALL' AND h.log_grade <> 'X'"
                    + " AND NOT (h.log_grade = 'U' AND h.hdbs_tree_species IN ('HE', 'BA')))"
                    + " OR h.log_grade = :grade)", Type.UPPER))
            .orderBy("1")
            .maxRows(1)
            .build()
    );
  }

  /**
   * No registry commands: every Rating write is a {@code WorkflowCommand} in
   * {@code ca.bc.gov.nrs.hbs.api.rating} (the legacy validations, overlap
   * checks and status gating run before the procs).
   */
  @Override
  public List<CommandDefinition> commands() {
    return List.of();
  }

  private static String districtRateSql() {
    return """
        SELECT ddr.distrt_id,
               ddr.org_unit_no,
               ou.org_unit_code AS district,
               ou.org_unit_name AS district_name,
               ddr.appraisal_mthd_code,
               clt.expanded_result AS appraisal_method,
               ddr.scale_species_code,
               NVL(ssc.description, 'All') AS species,
               ddr.scale_product_code,
               spc.description AS product,
               ddr.scale_grade_code,
               NVL(sgc.description, 'All') AS grade,
               ddr.rate,
               ddr.active_ind,
               ddr.effective_date,
               ddr.expiry_date,
               ddr.entry_userid,
               ddr.entry_timestamp,
               ddr.update_timestamp
          FROM hbs_district_default_rate ddr
          JOIN org_unit ou ON ou.org_unit_no = ddr.org_unit_no
          LEFT JOIN scale_species_code ssc ON ssc.scale_species_code = ddr.scale_species_code
          LEFT JOIN scale_product_code spc ON spc.scale_product_code = ddr.scale_product_code
          LEFT JOIN scale_grade_code sgc ON sgc.scale_grade_code = ddr.scale_grade_code
          LEFT JOIN code_list_table clt ON clt.column_name = 'APPRAISAL_MTHD_COD'
                                       AND clt.code_argument = ddr.appraisal_mthd_code
         WHERE 1=1""";
  }

  private static String wasteRateSql() {
    return """
        SELECT wrr.wstrt_id,
               wrr.timber_mark,
               wrr.cut_block_id,
               wrr.effective_date,
               wrr.expiry_date,
               wrr.scale_species_code,
               NVL(ssc.description, 'All') AS species,
               wrr.scale_product_code,
               wrr.scale_grade_code,
               NVL(sgc.description, 'All') AS grade,
               wrr.active_ind,
               wrr.avoidable_ind,
               wrr.rate,
               wrr.waste_rate_source_code,
               wrsc.description AS rate_source,
               wrr.entry_userid,
               wrr.entry_timestamp,
               wrr.update_timestamp
          FROM waste_residue_rate wrr
          LEFT JOIN scale_species_code ssc ON ssc.scale_species_code = wrr.scale_species_code
          LEFT JOIN scale_grade_code sgc ON sgc.scale_grade_code = wrr.scale_grade_code
          LEFT JOIN waste_rate_source_code wrsc ON wrsc.waste_rate_source_code = wrr.waste_rate_source_code
         WHERE 1=1""";
  }

  /** A CODE_SUBSET_TBL subset, described from CODE_LIST_TABLE where available. */
  private static QueryDefinition codeSubset(String id, String subset) {
    return QueryDefinition.builder(id)
        .legacy("P240")
        .capability(Capability.ANY_USER)
        .sql("SELECT cs.code_argument AS code, MAX(clt.expanded_result) AS description"
            + " FROM code_subset_tbl cs"
            + " LEFT JOIN code_list_table clt ON clt.column_name = cs.column_name"
            + " AND clt.code_argument = cs.code_argument"
            + " WHERE cs.code_subset_name = '" + subset + "'"
            + " AND (cs.expired_date IS NULL OR cs.expired_date > SYSDATE)"
            + " GROUP BY cs.code_argument")
        .orderBy("cs.code_argument")
        .maxRows(200)
        .build();
  }
}
