package ca.bc.gov.nrs.hbs.api.catalog;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.query.QueryCatalog;
import ca.bc.gov.nrs.hbs.api.query.QueryDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter.Type;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Queries tab (legacy P400) — harvest history (P431/P441), Timber Mark
 * Information (P480/P481), Cut to Cruise (P490), Scale Site Summary (P401) and
 * Aged Unbilled Scale (P403). Each legacy screen produced a Jasper/Crystal
 * report from a REF-CURSOR proc; the proc's SELECT is ported here as a
 * searchable list and the SPA offers the original report on the same screen.
 *
 * <p>Sources (nr-mof-db THE/PROCEDURES unless noted): {@code HBS_GET_HISTORY}
 * (V7.01739; same SELECT as {@code HBS00001R}/{@code HBS3R441R}),
 * {@code HBS3R481} + FUNCTIONS/{@code HBS_3R481_CONDITIONAL},
 * {@code TimberMarkReaderBean} (StumpageRateQuery, ApprisalSpecieQuery,
 * ApprisalSpecieCoastQuery, ApprisalSPGQuery), {@code HBS3R490},
 * {@code HBS2R401/402/403}, {@code HBS2R411/412}.
 *
 * <p>FOI severing (legacy {@code TimberMarkReaderBean.isSevered} and the
 * BT17247 DECODE in HBS_GET_HISTORY): individual ({@code client_type_code = 'I'})
 * client names/addresses are "Not Releasable" unless the viewer is ministry or
 * the individual client itself.
 */
@Component
public class QueriesReportsCatalog implements QueryCatalog {

  /** Viewer may see an individual client's personal data (ministry, or the client itself). */
  static String releasable(String fc) {
    return "(:hbsIsMinistry = 'Y' OR NVL(" + fc + ".client_type_code, 'X') <> 'I' OR "
        + fc + ".client_number = :hbsViewerClient)";
  }

  static String sever(String fc, String expression, String alias) {
    return "CASE WHEN " + releasable(fc) + " THEN " + expression + " ELSE 'Not Releasable' END AS " + alias;
  }

  /** Billing-type checkbox groups → BILLING_TYPE_CODE (HBS_GET_HISTORY strBillingTypeFilter). */
  private static final String BILLING_TYPE = """
      AND ((:billingType = 'Normal' AND hh.billing_type_code = 'NP')
        OR (:billingType = 'Cruise' AND hh.billing_type_code = 'CR')
        OR (:billingType = 'Waste' AND hh.billing_type_code IN ('WA', 'WU'))
        OR (:billingType = 'Beachcomb' AND hh.billing_type_code IN ('B1', 'B2', 'B3', 'B4', 'B5', 'B6')))""";

  /** Latest confirmed appraisal of the mark (HBS_3R481_CONDITIONAL aw_max). */
  private static final String LATEST_CNF = """
      aw.appraisal_sts_st = 'CNF'
      AND aw.app_effective_date = (SELECT MAX(aw2.app_effective_date) FROM app_worksheet aw2
                                    WHERE aw2.timber_mark = aw.timber_mark AND aw2.appraisal_sts_st = 'CNF')""";

  @Override
  public List<QueryDefinition> queries() {
    return List.of(
        billingHistory(), scalingHistory(),
        timberMarkSearch(), timberMarkDetail(), timberMarkAppraisals(), timberMarkSpecies(),
        timberMarkStumpageRates(), timberMarkSpgRates(),
        cutToCruise(), scaleSiteSummary(), agedUnbilled());
  }

  // ── P431 Mark Monthly Billing History ─────────────────────────────────────
  private static QueryDefinition billingHistory() {
    return QueryDefinition.builder("harvestHistory.billing")
        .legacy("P431/P432")
        .capability(Capability.QUERIES_VIEW)
        .sql("""
            SELECT hh.timber_mark, hh.billing_year, hh.billing_month, hh.billing_period,
                   hh.hdbs_tree_species AS species_code, ssc.description AS species_description,
                   hh.forest_product_cde AS product_code, spc.description AS product_description,
                   hh.log_grade AS grade_code, sgc.description AS grade_description,
                   hh.billing_type_code, clt.expanded_result AS billing_type_description,
                   hh.volume_billed AS total_volume_billed,
                   NVL(hh.reserve_stmpg_amt, 0) + NVL(hh.bonus_stumpage_amt, 0) + NVL(hh.silv_levy_amount, 0)
                     + NVL(hh.royalty_amount, 0) + NVL(hh.dev_levy_amount, 0) AS total_amount_billed,
                   regn.org_unit_code AS region_harvested_code, regn.org_unit_name AS region_harvested_name,
                   dist.org_unit_code AS district_harvested_code, dist.org_unit_name AS district_harvested_name,
                   mbc.forest_file_id, mbc.file_type_code, ftc.expanded_result AS file_type_description,
                   mbc.mgmt_unit_type,
                   DECODE(mbc.mgmt_unit_type, 'F', 'Woodlot', 'T', 'Tree Farm Licence',
                          'U', 'Timber Supply Area', 'V', 'Timber Supply Block') AS mgmt_unit_type_description,
                   mbc.mgmt_unit_id,
                   fc.client_type_code, cltc.expanded_result AS client_type_description,
                   fc.client_number,
                   %s,
                   cl.client_locn_code,
                   %s
              FROM harvest_history hh
              JOIN mark_billed_cli mbc ON mbc.timber_mark = hh.timber_mark
              LEFT JOIN org_unit dist ON dist.org_unit_no = mbc.forest_district
              LEFT JOIN org_unit regn ON regn.org_unit_no = mbc.forest_region
              JOIN v_client_public fc ON fc.client_number = mbc.client_number
              JOIN client_location cl ON cl.client_number = mbc.client_number
                                     AND cl.client_locn_code = mbc.client_locn_code
              JOIN code_list_table clt ON clt.code_argument = hh.billing_type_code
                                      AND clt.column_name = 'BILLING_TYPE_CODE'
              JOIN code_list_table ftc ON ftc.column_name = 'FILE_TYPE_CODE' AND ftc.code_argument = mbc.file_type_code
              JOIN code_list_table cltc ON cltc.column_name = 'CLIENT_TYPE_CODE' AND cltc.code_argument = fc.client_type_code
              LEFT JOIN scale_species_code ssc ON ssc.scale_species_code = hh.hdbs_tree_species
              LEFT JOIN scale_product_code spc ON spc.scale_product_code = hh.forest_product_cde
              LEFT JOIN scale_grade_code sgc ON sgc.scale_grade_code = hh.log_grade
             WHERE 1=1""".formatted(
                sever("fc", "fc.client_name", "client_name"),
                sever("fc", "cl.client_locn_name", "client_locn_name")))
        .filter(QueryFilter.required("billingFrom", "AND hh.billing_period >= TRUNC(:billingFrom, 'MM')", Type.DATE))
        .filter(QueryFilter.required("billingTo", "AND hh.billing_period < ADD_MONTHS(TRUNC(:billingTo, 'MM'), 1)",
            Type.DATE))
        .filter(QueryFilter.number("regionDistrict",
            "AND (mbc.forest_district = :regionDistrict OR mbc.forest_region = :regionDistrict)"))
        .filter(QueryFilter.eq("clientNumber", "AND mbc.client_number = :clientNumber"))
        .filter(QueryFilter.eq("clientLocation", "AND mbc.client_locn_code = :clientLocation"))
        .filter(QueryFilter.of("fileType", "AND mbc.file_type_code IN (:fileType)", Type.LIST))
        .filter(QueryFilter.of("mgmtUnitType", "AND mbc.mgmt_unit_type IN (:mgmtUnitType)", Type.LIST))
        .filter(QueryFilter.upper("mgmtUnitNo", "AND mbc.mgmt_unit_id = :mgmtUnitNo"))
        .filter(QueryFilter.of("forestFileId", "AND mbc.forest_file_id IN (:forestFileId)", Type.LIST))
        .filter(QueryFilter.of("timberMarks", "AND mbc.timber_mark IN (:timberMarks)", Type.LIST))
        .filter(QueryFilter.upper("species", "AND hh.hdbs_tree_species = :species"))
        .filter(QueryFilter.upper("product", "AND hh.forest_product_cde = :product"))
        .filter(QueryFilter.upper("grade", "AND hh.log_grade = :grade"))
        .filter(QueryFilter.eq("billingType", BILLING_TYPE))
        .sort("timberMark", "hh.timber_mark")
        .sort("billingPeriod", "hh.billing_period")
        .sort("totalVolumeBilled", "hh.volume_billed")
        .orderBy("hh.timber_mark, hh.billing_period, hh.hdbs_tree_species, hh.forest_product_cde, hh.log_grade")
        .build();
  }

  // ── P441 Mark Monthly Scaling History ─────────────────────────────────────
  private static QueryDefinition scalingHistory() {
    return QueryDefinition.builder("harvestHistory.scaling")
        .legacy("P441/P442")
        .capability(Capability.QUERIES_VIEW)
        .sql("""
            SELECT hh.timber_mark, hh.scaling_period,
                   hh.scale_species_code AS species_code, ssc.description AS species_description,
                   hh.scale_product_code AS product_code, spc.description AS product_description,
                   hh.scale_grade_code AS grade_code, sgc.description AS grade_description,
                   hh.billing_type_code, clt.expanded_result AS billing_type_description,
                   hh.volume_scaled, hh.total_amount,
                   regn.org_unit_code AS region_harvested_code, regn.org_unit_name AS region_harvested_name,
                   dist.org_unit_code AS district_harvested_code, dist.org_unit_name AS district_harvested_name,
                   mbc.forest_file_id, mbc.file_type_code, ftc.expanded_result AS file_type_description,
                   mbc.mgmt_unit_type,
                   DECODE(mbc.mgmt_unit_type, 'F', 'Woodlot', 'T', 'Tree Farm Licence',
                          'U', 'Timber Supply Area', 'V', 'Timber Supply Block') AS mgmt_unit_type_description,
                   mbc.mgmt_unit_id,
                   fc.client_number, %s, cl.client_locn_code, %s,
                   orgdist.org_unit_code AS district_scaled_code, orgdist.org_unit_name AS district_scaled_name,
                   orgregn.org_unit_code AS region_scaled_code, orgregn.org_unit_name AS region_scaled_name,
                   ss.scale_site_id_nmbr AS scale_site, ss.site_name,
                   fcs.client_number AS site_owner_client_number, %s
              FROM scaling_history hh
              JOIN mark_billed_cli mbc ON mbc.timber_mark = hh.timber_mark
              LEFT JOIN org_unit dist ON dist.org_unit_no = mbc.forest_district
              LEFT JOIN org_unit regn ON regn.org_unit_no = mbc.forest_region
              JOIN v_client_public fc ON fc.client_number = mbc.client_number
              JOIN client_location cl ON cl.client_number = mbc.client_number
                                     AND cl.client_locn_code = mbc.client_locn_code
              JOIN code_list_table clt ON clt.code_argument = hh.billing_type_code
                                      AND clt.column_name = 'BILLING_TYPE_CODE'
              JOIN code_list_table ftc ON ftc.column_name = 'FILE_TYPE_CODE' AND ftc.code_argument = mbc.file_type_code
              LEFT JOIN scale_species_code ssc ON ssc.scale_species_code = hh.scale_species_code
              LEFT JOIN scale_product_code spc ON spc.scale_product_code = hh.scale_product_code
              LEFT JOIN scale_grade_code sgc ON sgc.scale_grade_code = hh.scale_grade_code
              JOIN scale_site ss ON ss.scale_site_id_nmbr = hh.scale_site
              LEFT JOIN org_unit orgdist ON orgdist.org_unit_no = ss.org_unit_no
              LEFT JOIN org_unit orgregn ON orgregn.org_unit_no = orgdist.rollup_region_no
              JOIN v_client_public fcs ON fcs.client_number = ss.owner_cli_number
             WHERE 1=1""".formatted(
                sever("fc", "fc.client_name", "client_name"),
                sever("fc", "cl.client_locn_name", "client_locn_name"),
                sever("fcs", "fcs.client_name", "site_owner_client_name")))
        .filter(QueryFilter.required("scalingFrom", "AND hh.scaling_period >= TRUNC(:scalingFrom, 'MM')", Type.DATE))
        .filter(QueryFilter.required("scalingTo",
            "AND hh.scaling_period < ADD_MONTHS(TRUNC(:scalingTo, 'MM'), 1)", Type.DATE))
        .filter(QueryFilter.number("regionDistrict",
            "AND (mbc.forest_district = :regionDistrict OR mbc.forest_region = :regionDistrict)"))
        .filter(QueryFilter.number("regionScaled",
            "AND (orgdist.org_unit_no = :regionScaled OR orgdist.rollup_region_no = :regionScaled)"))
        .filter(QueryFilter.upper("scaleSite", "AND hh.scale_site = :scaleSite"))
        .filter(QueryFilter.eq("clientNumber", "AND mbc.client_number = :clientNumber"))
        .filter(QueryFilter.eq("clientLocation", "AND mbc.client_locn_code = :clientLocation"))
        .filter(QueryFilter.of("fileType", "AND mbc.file_type_code IN (:fileType)", Type.LIST))
        .filter(QueryFilter.of("mgmtUnitType", "AND mbc.mgmt_unit_type IN (:mgmtUnitType)", Type.LIST))
        .filter(QueryFilter.upper("mgmtUnitNo", "AND mbc.mgmt_unit_id = :mgmtUnitNo"))
        .filter(QueryFilter.of("forestFileId", "AND mbc.forest_file_id IN (:forestFileId)", Type.LIST))
        .filter(QueryFilter.of("timberMarks", "AND mbc.timber_mark IN (:timberMarks)", Type.LIST))
        .filter(QueryFilter.upper("species", "AND hh.scale_species_code = :species"))
        .filter(QueryFilter.upper("product", "AND hh.scale_product_code = :product"))
        .filter(QueryFilter.upper("grade", "AND hh.scale_grade_code = :grade"))
        .filter(QueryFilter.eq("billingType", BILLING_TYPE))
        .sort("timberMark", "hh.timber_mark")
        .sort("scalingPeriod", "hh.scaling_period")
        .sort("scaleSite", "hh.scale_site")
        .orderBy("hh.timber_mark, hh.scaling_period, hh.scale_site")
        .build();
  }

  // ── P480 Search for Timber Mark ───────────────────────────────────────────
  private static QueryDefinition timberMarkSearch() {
    return QueryDefinition.builder("timberMarks.search")
        .legacy("P480")
        .capability(Capability.QUERIES_VIEW)
        .sql("""
            SELECT tm.timber_mark, tm.cutting_permit_id, tm.forest_file_id,
                   tm.mark_status_st, tsc.expanded_result AS mark_status_description,
                   tm.mark_issue_date, tm.mark_expiry_date,
                   dist.org_unit_code AS district_code, dist.org_unit_name AS district_name,
                   mbc.client_number, mbc.client_locn_code, %s
              FROM timber_mark tm
              LEFT JOIN mark_billed_cli mbc ON mbc.timber_mark = tm.timber_mark
              LEFT JOIN v_client_public fc ON fc.client_number = mbc.client_number
              LEFT JOIN code_list_table tsc ON tsc.column_name = 'TIMBER_STATUS_CODE' AND tsc.code_argument = tm.mark_status_st
              LEFT JOIN org_unit dist ON dist.org_unit_no = tm.forest_district
             WHERE 1=1""".formatted(sever("fc", "fc.client_name", "client_name")))
        .filter(QueryFilter.required("timberMark", "AND tm.timber_mark LIKE :timberMark", Type.PREFIX))
        .sort("timberMark", "tm.timber_mark")
        .sort("forestFileId", "tm.forest_file_id")
        .orderBy("tm.timber_mark")
        .maxRows(500)
        .build();
  }

  // ── P481 Timber Mark Query (Timber Mark / Tenure / Client / latest Appraisal) ─
  private static QueryDefinition timberMarkDetail() {
    return QueryDefinition.builder("timberMarks.detail")
        .legacy("P481")
        .capability(Capability.QUERIES_VIEW)
        .sql("""
            SELECT tm.timber_mark, tm.cutting_permit_id,
                   hs.sale_method_code, smc.expanded_result AS sale_method_description,
                   tm.mark_status_st, tsc.expanded_result AS mark_status_description,
                   tm.catastrophic_ind, tm.cruise_based_ind,
                   ou2.org_unit_code AS admin_org_code, ou2.org_unit_name AS admin_org_name,
                   tm.mark_expiry_date, tm.mark_extend_date, tm.mark_issue_date,
                   ou4.org_unit_code AS geo_org_code, ou3.org_unit_name AS geo_org_name,
                   hs.sold_sb_cat_code AS sb_category, hs.sb_fund_ind,
                   tm.quota_type_code, qtc.expanded_result AS quota_type_description,
                   ou1.org_unit_code AS region_code, ou1.org_unit_name AS region_name,
                   (SELECT MAX(pb.permit_block_locn) FROM permit_block pb
                     WHERE pb.forest_file_id = mbc.forest_file_id
                       AND pb.cutting_permit_id = mbc.cutting_permit_id) AS location,
                   tm.forest_file_id AS licence,
                   pfu.file_type_code, ftc.expanded_result AS file_type_description,
                   hc.schedule_b_aac AS aac,
                   pfu.file_status_st, tfsc.expanded_result AS file_status_description,
                   pfu.file_status_date AS awarded_date,
                   hs.payment_method_cd, pmc.expanded_result AS payment_method_description,
                   tt.legal_effective_dt, tt.initial_expiry_dt AS tenure_expiry_date,
                   tt.current_expiry_dt AS tenure_extended_date, tt.tenure_extend_cnt AS extensions,
                   pfu.mgmt_unit_type,
                   DECODE(pfu.mgmt_unit_type, 'F', 'Woodlot', 'T', 'Tree Farm Licence',
                          'U', 'Timber Supply Area', 'V', 'Timber Supply Block') AS mgmt_unit_type_description,
                   pfu.mgmt_unit_id,
                   mbc.client_number, %s, mbc.client_locn_code, %s,
                   fc.client_type_code, ctc.expanded_result AS client_type_description,
                   %s, %s, %s, %s, %s, %s, %s,
                   fc.client_status_code, csc.expanded_result AS client_status_description,
                   aw.appraisal_sts_st, aw.app_effective_date, aw.expiry_date AS app_expiry_date,
                   aw.rate_calc_mthd_cd, aw.appraisal_mthd_cd, amc.expanded_result AS appraisal_method_description,
                   aw.adjust_qrterly_ind, aw.ttl_merchntbl_area, aw.others_tied_to_cd,
                   aw.conif_stand_rate_elig_code, cse.expanded_result AS conif_stand_rate_elig_description,
                   aw.decid_stand_rate_elig_code, dse.expanded_result AS decid_stand_rate_elig_description,
                   aw.net_cruise_volume, NVL(aw.int_deciduous_vol, 0) AS int_deciduous_vol,
                   NVL(aw.net_cruise_volume, 0) + NVL(aw.int_deciduous_vol, 0) AS total_cruise_volume
              FROM timber_mark tm
              JOIN mark_billed_cli mbc ON mbc.timber_mark = tm.timber_mark
              JOIN v_client_public fc ON fc.client_number = mbc.client_number
              LEFT JOIN client_location cl ON cl.client_number = mbc.client_number
                                          AND cl.client_locn_code = mbc.client_locn_code
              LEFT JOIN org_unit ou1 ON ou1.org_unit_no = mbc.forest_region
              LEFT JOIN org_unit ou2 ON ou2.org_unit_no = mbc.forest_district
              LEFT JOIN org_unit ou4 ON ou4.org_unit_no = mbc.geographic_distrct
              LEFT JOIN org_unit ou3 ON ou3.org_unit_no = tm.geographic_distrct
              LEFT JOIN harvest_commit hc ON hc.forest_file_id = mbc.forest_file_id
              LEFT JOIN harvest_sale hs ON hs.forest_file_id = mbc.forest_file_id
              LEFT JOIN prov_forest_use pfu ON pfu.forest_file_id = mbc.forest_file_id
              LEFT JOIN tenure_term tt ON tt.forest_file_id = mbc.forest_file_id
                     AND tt.tenure_term = (SELECT MAX(t2.tenure_term) FROM tenure_term t2
                                            WHERE t2.forest_file_id = mbc.forest_file_id)
              LEFT JOIN app_worksheet aw ON aw.timber_mark = tm.timber_mark AND %s
              LEFT JOIN code_list_table smc ON smc.column_name = 'SALE_METHOD_CODE' AND smc.code_argument = hs.sale_method_code
              LEFT JOIN code_list_table tsc ON tsc.column_name = 'TIMBER_STATUS_CODE' AND tsc.code_argument = tm.mark_status_st
              LEFT JOIN code_list_table qtc ON qtc.column_name = 'QUOTA_TYPE_CODE' AND qtc.code_argument = tm.quota_type_code
              LEFT JOIN code_list_table ftc ON ftc.column_name = 'FILE_TYPE_CODE' AND ftc.code_argument = pfu.file_type_code
              LEFT JOIN code_list_table tfsc ON tfsc.column_name = 'TENURE_FILE_STS_CD' AND tfsc.code_argument = pfu.file_status_st
              LEFT JOIN code_list_table pmc ON pmc.column_name = 'PAYMENT_METHOD_CD' AND pmc.code_argument = hs.payment_method_cd
              LEFT JOIN code_list_table ctc ON ctc.column_name = 'CLIENT_TYPE_CODE' AND ctc.code_argument = fc.client_type_code
              LEFT JOIN code_list_table csc ON csc.column_name = 'CLIENT_STATUS_CODE' AND csc.code_argument = fc.client_status_code
              LEFT JOIN code_list_table amc ON amc.column_name = 'APPRAISAL_MTHD_COD' AND amc.code_argument = aw.appraisal_mthd_cd
              LEFT JOIN code_list_table cse
                     ON cse.column_name = 'STAND_RATE_ELGB_CD' AND cse.code_argument = aw.conif_stand_rate_elig_code
              LEFT JOIN code_list_table dse
                     ON dse.column_name = 'STAND_RATE_ELGB_CD' AND dse.code_argument = aw.decid_stand_rate_elig_code
             WHERE 1=1""".formatted(
                sever("fc", "fc.client_name", "client_name"),
                sever("fc", "cl.client_locn_name", "client_locn_name"),
                sever("fc", "cl.address_1", "address_1"),
                sever("fc", "cl.address_2", "address_2"),
                sever("fc", "cl.address_3", "address_3"),
                sever("fc", "cl.city", "city"),
                sever("fc", "cl.province", "province"),
                sever("fc", "cl.country", "country"),
                sever("fc", "cl.postal_code", "postal_code"),
                LATEST_CNF))
        .filter(QueryFilter.required("timberMark", "AND tm.timber_mark = :timberMark", Type.UPPER))
        .orderBy("tm.timber_mark")
        .maxRows(1)
        .build();
  }

  /** P481 First/Previous/Next/Last appraisal paging → the confirmed appraisal history as a table. */
  private static QueryDefinition timberMarkAppraisals() {
    return QueryDefinition.builder("timberMarks.appraisals")
        .legacy("P481")
        .capability(Capability.QUERIES_VIEW)
        .sql("""
            SELECT aw.timber_mark, aw.appraisal_sts_st, aw.app_effective_date, aw.expiry_date AS app_expiry_date,
                   aw.rate_calc_mthd_cd, aw.appraisal_mthd_cd, amc.expanded_result AS appraisal_method_description,
                   aw.adjust_qrterly_ind, aw.ttl_merchntbl_area, aw.others_tied_to_cd,
                   aw.conif_stand_rate_elig_code, aw.decid_stand_rate_elig_code,
                   aw.net_cruise_volume, aw.int_deciduous_vol
              FROM app_worksheet aw
              LEFT JOIN code_list_table amc ON amc.column_name = 'APPRAISAL_MTHD_COD' AND amc.code_argument = aw.appraisal_mthd_cd
             WHERE aw.appraisal_sts_st = 'CNF'""")
        .filter(QueryFilter.required("timberMark", "AND aw.timber_mark = :timberMark", Type.UPPER))
        .orderBy("aw.app_effective_date DESC")
        .maxRows(200)
        .build();
  }

  /** Appraised Species / Cruise Volume — APPINT_SPECIES (Interior) or APP_SPECIES (Coast) per APPRAISAL_MTHD_CD. */
  private static QueryDefinition timberMarkSpecies() {
    return QueryDefinition.builder("timberMarks.species")
        .legacy("P481")
        .capability(Capability.QUERIES_VIEW)
        .sql("""
            SELECT d.timber_mark, d.app_effective_date, d.species, d.species_volume
              FROM (SELECT s.timber_mark, s.app_effective_date, s.hdbs_tree_species AS species, s.species_volume
                      FROM appint_species s
                      JOIN app_worksheet aw ON aw.timber_mark = s.timber_mark
                                           AND aw.app_effective_date = s.app_effective_date
                     WHERE aw.appraisal_mthd_cd = 'I' AND %1$s
                    UNION ALL
                    SELECT s.timber_mark, s.app_effective_date, s.hdbs_tree_species, s.species_volume
                      FROM app_species s
                      JOIN app_worksheet aw ON aw.timber_mark = s.timber_mark
                                           AND aw.app_effective_date = s.app_effective_date
                     WHERE aw.appraisal_mthd_cd = 'C' AND %1$s) d
             WHERE 1=1""".formatted(LATEST_CNF))
        .filter(QueryFilter.required("timberMark", "AND d.timber_mark = :timberMark", Type.UPPER))
        .orderBy("d.species")
        .maxRows(200)
        .build();
  }

  /** Stumpage rates of the latest confirmed appraisal; Reserve Rate = total − (bonus + dev + silv) (StumpageRate.getReserveRate). */
  private static QueryDefinition timberMarkStumpageRates() {
    return QueryDefinition.builder("timberMarks.stumpageRates")
        .legacy("P481")
        .capability(Capability.QUERIES_VIEW)
        .sql("""
            SELECT asr.timber_mark, asr.app_effective_date, asr.stmpg_rte_efctv_dt,
                   asr.tot_stumpage_rate - (NVL(asr.bonus_bid_amount, 0) + NVL(asr.development_levy, 0)
                     + NVL(asr.silviculture_levy, 0)) AS reserve_rate,
                   asr.development_levy, asr.silviculture_levy, asr.bonus_bid_amount,
                   asr.tot_stumpage_rate AS stand_rate
              FROM app_stmpg_rate asr
              JOIN app_worksheet aw ON aw.timber_mark = asr.timber_mark
                                   AND aw.app_effective_date = asr.app_effective_date
             WHERE %s""".formatted(LATEST_CNF))
        .filter(QueryFilter.required("timberMark", "AND asr.timber_mark = :timberMark", Type.UPPER))
        .orderBy("asr.stmpg_rte_efctv_dt")
        .maxRows(500)
        .build();
  }

  /** SPG rates (APP_SPP_SP_PROD); HDBS_SPP_PRD_GRADE = species(2) + product(2) + grade (ApprisalSPG). */
  private static QueryDefinition timberMarkSpgRates() {
    return QueryDefinition.builder("timberMarks.spgRates")
        .legacy("P481")
        .capability(Capability.QUERIES_VIEW)
        .sql("""
            SELECT sp.timber_mark, sp.app_effective_date,
                   SUBSTR(sp.hdbs_spp_prd_grade, 1, 2) AS species_code,
                   SUBSTR(sp.hdbs_spp_prd_grade, 5) AS grade_code,
                   SUBSTR(sp.hdbs_spp_prd_grade, 3, 2) AS product_code,
                   sp.reserve_stumpg_rt AS reserve_rate, sp.development_levy, sp.basic_silv_levy AS silviculture_levy,
                   sp.bonus_bid_amount,
                   NVL(sp.bonus_bid_amount, 0) + NVL(sp.reserve_stumpg_rt, 0) + NVL(sp.development_levy, 0)
                     + NVL(sp.basic_silv_levy, 0) AS fixed_rate
              FROM app_spp_sp_prod sp
              JOIN app_worksheet aw ON aw.timber_mark = sp.timber_mark
                                   AND aw.app_effective_date = sp.app_effective_date
             WHERE %s""".formatted(LATEST_CNF))
        .filter(QueryFilter.required("timberMark", "AND sp.timber_mark = :timberMark", Type.UPPER))
        .orderBy("sp.hdbs_spp_prd_grade")
        .maxRows(1000)
        .build();
  }

  // ── P490 Cut to Cruise Comparison ─────────────────────────────────────────
  private static QueryDefinition cutToCruise() {
    return QueryDefinition.builder("cutToCruise.search")
        .legacy("P490")
        .capability(Capability.QUERIES_VIEW)
        .sql("""
            SELECT mbc.client_number, mbc.client_locn_code, mbc.timber_mark,
                   mbc.forest_file_id AS licence, mbc.cutting_permit_id, mbc.file_type_code,
                   clt.expanded_result AS file_type_descr,
                   tm.mark_extend_date, tm.mark_expiry_date,
                   NVL(hh.billed_volume, 0) AS billed_volume,
                   hh.max_billed_date, hh.min_billed_date,
                   -- HBS_GET_CRUISE_VOLUME, inlined (no EXECUTE grant on it): the
                   -- latest confirmed appraisal's cruise volume — Coastal: the
                   -- worksheet's net cruise volume; Interior: net cruise + interior
                   -- deciduous volume; no confirmed appraisal: 0. The function preferred
                   -- APP_WEIGHT_VOL's latest volume for Coastal marks, but the HBS role
                   -- has no grant on APP_WEIGHT_VOL (docs/database.md) — this is the
                   -- function's own fallback for a Coastal mark without one.
                   CASE WHEN cnf.eff IS NULL THEN 0
                        WHEN cnf.mthd = 'C' THEN wk.net_vol
                        ELSE wk.net_vol + wk.dec_vol
                   END AS appraisal_volume
              FROM mark_billed_cli mbc
              LEFT JOIN timber_mark tm ON tm.timber_mark = mbc.timber_mark
              LEFT JOIN LATERAL (
                     SELECT MAX(a.app_effective_date) AS eff,
                            MAX(a.appraisal_mthd_cd) KEEP (DENSE_RANK LAST ORDER BY a.app_effective_date) AS mthd
                       FROM app_worksheet a
                      WHERE a.timber_mark = mbc.timber_mark AND a.appraisal_sts_st = 'CNF') cnf ON 1 = 1
              LEFT JOIN LATERAL (
                     SELECT MAX(w.net_cruise_volume) AS net_vol, MAX(w.int_deciduous_vol) AS dec_vol
                       FROM app_worksheet w
                      WHERE w.timber_mark = mbc.timber_mark AND w.app_effective_date = cnf.eff) wk ON 1 = 1
              JOIN code_list_table clt ON clt.code_argument = mbc.file_type_code
                                      AND clt.column_name = 'FILE_TYPE_CODE'
              LEFT JOIN (SELECT timber_mark, SUM(volume_billed) AS billed_volume,
                                MAX(billing_period) AS max_billed_date, MIN(billing_period) AS min_billed_date
                           FROM harvest_history GROUP BY timber_mark) hh
                     ON hh.timber_mark = mbc.timber_mark
             WHERE 1=1""")
        .filter(QueryFilter.upper("timberMark", "AND mbc.timber_mark = :timberMark"))
        .filter(QueryFilter.upper("forestFileId", "AND mbc.forest_file_id = :forestFileId"))
        .filter(QueryFilter.eq("clientNumber", "AND mbc.client_number = :clientNumber"))
        .filter(QueryFilter.eq("clientLocation", "AND mbc.client_locn_code = :clientLocation"))
        .filter(QueryFilter.date("expiryDate",
            "AND (tm.mark_expiry_date > :expiryDate OR tm.mark_extend_date > :expiryDate)"))
        .sort("timberMark", "mbc.timber_mark")
        .sort("licence", "mbc.forest_file_id")
        .orderBy("mbc.client_number, mbc.client_locn_code, mbc.forest_file_id, mbc.timber_mark")
        .build();
  }

  // ── P401 Scale Site Summary — the summary-return rows HBS2R401/402/403 aggregate ─
  private static QueryDefinition scaleSiteSummary() {
    return QueryDefinition.builder("scaleSiteSummary.search")
        .legacy("P401")
        .capability(Capability.QUERIES_VIEW)
        .sql("""
            SELECT v.return_type, v.document_control_number, v.version, v.scl_rtn_version_state_code,
                   v.scale_date, v.scale_site_id_nmbr AS scale_site, ss.site_name,
                   ou.org_unit_code AS district_scaled_code, ou.org_unit_name AS district_scaled_name,
                   v.timber_mark, v.license_number AS scaler_licence,
                   v.population_number, v.stratum_number, v.sampling_year,
                   ss.owner_cli_number AS site_owner_client_number, ss.owner_cli_locn_cd AS site_owner_location,
                   mbc.client_number AS mark_holder_client_number, mbc.client_locn_code AS mark_holder_location
              FROM (SELECT 'P' AS return_type, document_control_number, version, scl_rtn_version_state_code,
                           scale_date, scale_site_id_nmbr, timber_mark, license_number,
                           CAST(NULL AS VARCHAR2(4)) AS population_number, CAST(NULL AS VARCHAR2(2)) AS stratum_number,
                           CAST(NULL AS NUMBER) AS sampling_year
                      FROM piece_scale_summary WHERE active_version_ind = 'Y'
                    UNION ALL
                    SELECT 'W', document_control_number, version, scl_rtn_version_state_code,
                           scale_date, scale_site_id_nmbr, timber_mark, license_number,
                           population_number, stratum_number, sampling_year
                      FROM weight_scale_summary WHERE active_version_ind = 'Y'
                    UNION ALL
                    SELECT 'S', document_control_number, version, scl_rtn_version_state_code,
                           scale_date, scale_site_id_nmbr, timber_mark, license_number,
                           population_number, stratum_number, sampling_year
                      FROM sample_scale_summary WHERE active_version_ind = 'Y') v
              JOIN scale_site ss ON ss.scale_site_id_nmbr = v.scale_site_id_nmbr
              JOIN org_unit ou ON ou.org_unit_no = ss.org_unit_no
              LEFT JOIN mark_billed_cli mbc ON mbc.timber_mark = v.timber_mark
             WHERE 1=1""")
        .filter(QueryFilter.required("returnType", "AND v.return_type = :returnType", Type.UPPER))
        .filter(QueryFilter.upper("versionStatus", "AND v.scl_rtn_version_state_code = :versionStatus"))
        .filter(QueryFilter.required("scaleDateFrom", "AND v.scale_date >= :scaleDateFrom", Type.DATE))
        .filter(QueryFilter.required("scaleDateTo", "AND v.scale_date < :scaleDateTo", Type.DATE_TO))
        .filter(QueryFilter.number("districtScaled",
            "AND (ou.org_unit_no = :districtScaled OR ou.rollup_region_no = :districtScaled)"))
        .filter(QueryFilter.upper("scaleSite", "AND v.scale_site_id_nmbr = :scaleSite"))
        .filter(QueryFilter.number("districtHarvested",
            "AND (mbc.forest_district = :districtHarvested OR mbc.forest_region = :districtHarvested)"))
        .filter(QueryFilter.upper("timberMark", "AND v.timber_mark = :timberMark"))
        .filter(QueryFilter.upper("scalerLicence", "AND v.license_number = :scalerLicence"))
        .filter(QueryFilter.upper("population", "AND v.population_number = :population"))
        .filter(QueryFilter.upper("stratum", "AND v.stratum_number = :stratum"))
        .filter(QueryFilter.number("samplingYear", "AND v.sampling_year = :samplingYear"))
        .filter(QueryFilter.eq("clientNumber",
            "AND (mbc.client_number = :clientNumber OR ss.owner_cli_number = :clientNumber)"))
        .filter(QueryFilter.eq("clientLocation",
            "AND (mbc.client_locn_code = :clientLocation OR ss.owner_cli_locn_cd = :clientLocation)"))
        .sort("scaleSite", "v.scale_site_id_nmbr")
        .sort("scaleDate", "v.scale_date")
        .sort("timberMark", "v.timber_mark")
        .sort("districtScaledCode", "ou.org_unit_code")
        .orderBy("ou.org_unit_code, v.scale_site_id_nmbr, v.document_control_number, v.version")
        .build();
  }

  // ── P403 Aged Unbilled Scale (ministry) — HBS2R411 (piece) / HBS2R412 (weight) ─
  private static QueryDefinition agedUnbilled() {
    String states = "('ACC', 'AWP', 'DSF', 'ERR', 'FAL', 'HLD', 'RDY')";
    return QueryDefinition.builder("agedUnbilled.search")
        .legacy("P403")
        .capability(Capability.MINISTRY)
        .sql("""
            SELECT u.scale_method, u.document_control_number, u.version, u.scl_rtn_version_state_code,
                   u.scale_date, u.scale_site, u.volume, u.weight,
                   -- HBS_GET_AGED_UNBILLED_REGION, inlined (no EXECUTE grant on it): the
                   -- mark's billed region, else the scale site's rollup region, else 0 /
                   -- 'NOT FOUND'.
                   CASE WHEN mro.org_unit_no IS NOT NULL THEN mro.org_unit_no
                        WHEN sro.org_unit_no IS NOT NULL THEN sro.org_unit_no
                        ELSE 0 END AS rollup_region_no,
                   CASE WHEN mro.org_unit_no IS NOT NULL THEN mro.org_unit_name
                        WHEN sro.org_unit_no IS NOT NULL THEN sro.org_unit_name
                        ELSE 'NOT FOUND' END AS region_name
              FROM (SELECT 'P' AS scale_method, TO_CHAR(pss.document_control_number) AS document_control_number,
                           pss.version, pss.scl_rtn_version_state_code, pss.scale_date,
                           pss.scale_site_id_nmbr AS scale_site, pss.timber_mark,
                           SUM(psseg.volume) AS volume, CAST(NULL AS NUMBER) AS weight
                      FROM piece_scale_summary pss
                      LEFT JOIN piece_scl_segregation psseg ON psseg.document_control_number = pss.document_control_number
                                                           AND psseg.version = pss.version
                     WHERE pss.active_version_ind = 'Y' AND pss.scl_rtn_version_state_code IN %1$s
                       AND pss.scale_site_id_nmbr <> 'ZZZ'
                     GROUP BY pss.document_control_number, pss.version, pss.scl_rtn_version_state_code,
                              pss.scale_date, pss.scale_site_id_nmbr, pss.timber_mark
                    UNION ALL
                    SELECT 'P', lt.detail_document_number, lt.version, lt.scl_rtn_version_state_code, lt.scale_date,
                           lt.scale_site_id_nmbr, lt.timber_mark, SUM(ltd.net_volume), CAST(NULL AS NUMBER)
                      FROM log_tally lt
                      LEFT JOIN log_tally_detail ltd ON ltd.detail_document_number = lt.detail_document_number
                                                    AND ltd.version = lt.version
                     WHERE lt.active_version_ind = 'Y' AND lt.scl_rtn_version_state_code IN %1$s
                       AND lt.scale_site_id_nmbr <> 'ZZZ'
                     GROUP BY lt.detail_document_number, lt.version, lt.scl_rtn_version_state_code,
                              lt.scale_date, lt.scale_site_id_nmbr, lt.timber_mark
                    UNION ALL
                    SELECT 'W', TO_CHAR(wss.document_control_number), wss.version, wss.scl_rtn_version_state_code,
                           wss.scale_date, wss.scale_site_id_nmbr, wss.timber_mark, CAST(NULL AS NUMBER), SUM(wsdls.weight)
                      FROM weight_scale_summary wss
                      LEFT JOIN weight_scl_daily_load_summary wsdls ON wsdls.document_control_number = wss.document_control_number
                                                                   AND wsdls.version = wss.version
                     WHERE wss.active_version_ind = 'Y' AND wss.scl_rtn_version_state_code IN %1$s
                       AND wss.scale_site_id_nmbr <> 'ZZZ'
                     GROUP BY wss.document_control_number, wss.version, wss.scl_rtn_version_state_code,
                              wss.scale_date, wss.scale_site_id_nmbr, wss.timber_mark
                    UNION ALL
                    SELECT 'W', ws.detail_document_number, ws.version, ws.scl_rtn_version_state_code, ws.scale_date,
                           ws.scale_site_id_nmbr, ws.timber_mark, CAST(NULL AS NUMBER), ws.gross_weight
                      FROM weigh_slip ws
                     WHERE ws.active_version_ind = 'Y' AND ws.scl_rtn_version_state_code IN %1$s
                       AND ws.scale_site_id_nmbr <> 'ZZZ') u
              LEFT JOIN (SELECT timber_mark, MIN(forest_region) AS forest_region
                           FROM mark_billed_cli GROUP BY timber_mark) mr ON mr.timber_mark = u.timber_mark
              LEFT JOIN org_unit mro ON mro.org_unit_no = mr.forest_region
              LEFT JOIN scale_site ssr ON ssr.scale_site_id_nmbr = u.scale_site
              LEFT JOIN org_unit sso ON sso.org_unit_no = ssr.org_unit_no
              LEFT JOIN org_unit sro ON sro.org_unit_no = sso.rollup_region_no
             WHERE 1=1""".formatted(states))
        .filter(QueryFilter.required("scaleMethod", "AND u.scale_method = :scaleMethod", Type.UPPER))
        .filter(QueryFilter.required("scaledPriorTo",
            "AND HBS_GET_AGE_OF_RETURN(u.scale_date, :scaledPriorTo) > 0", Type.DATE))
        .sort("scaleDate", "u.scale_date")
        .sort("scaleSite", "u.scale_site")
        .orderBy("rollup_region_no, u.scale_date")
        .build();
  }
}
