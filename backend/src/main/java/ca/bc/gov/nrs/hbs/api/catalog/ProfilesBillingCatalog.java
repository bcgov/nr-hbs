package ca.bc.gov.nrs.hbs.api.catalog;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.query.CommandDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryCatalog;
import ca.bc.gov.nrs.hbs.api.query.QueryDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter.Type;
import org.springframework.stereotype.Component;

import java.util.List;

import static ca.bc.gov.nrs.hbs.api.catalog.ProfilesCatalog.clientName;

/**
 * Profiles area (legacy {@code /cpm/*}), part 2: Mark Holder Profiles
 * (P340–P344), Statement Delivery Profiles (P370–P374) and Cruise Based
 * Billing Profiles (P380–P385).
 *
 * <p>SQL ported from {@code HBS3R341}, {@code HBS3R371}, {@code HBS3R380} and
 * {@code CruiseBasedSpGrRatioQuery}; writes call the same table-API procs as
 * {@code ClientProfileManagerBean}. See docs/areas/profiles.md.
 */
@Component
public class ProfilesBillingCatalog implements QueryCatalog {

  // ---------------------------------------------------------------- P340–P344

  private static final String MARK_HOLDER_SELECT = """
      SELECT mhp.mhprof_id AS mhprof_id,
             mhp.client_number AS client_number,
             mhp.client_locn_code AS client_locn_code,
             mhp.client_number || ' / ' || mhp.client_locn_code AS client_loc,
             %s AS client_name,
             mhp.hbs_return_type_code AS return_type,
             rt.description AS return_type_desc,
             mhp.hbs_frequency_type_code AS frequency,
             ftc.description AS frequency_desc,
             mhp.forced_summarization_date AS forced_summarization_date,
             mhp.effective_date AS effective_date,
             mhp.expiry_date AS expiry_date,
             CASE WHEN mhp.effective_date > SYSDATE THEN 'Y' ELSE 'N' END AS future_dated,
             mhp.entry_userid AS entry_userid,
             mhp.entry_timestamp AS entry_timestamp,
             mhp.update_userid AS update_userid
        FROM hbs_mark_holder_profile mhp
        JOIN v_client_public fc ON fc.client_number = mhp.client_number
        JOIN hbs_return_type_code rt ON rt.hbs_return_type_code = mhp.hbs_return_type_code
        JOIN hbs_frequency_type_code ftc ON ftc.hbs_frequency_type_code = mhp.hbs_frequency_type_code
       WHERE 1=1""".formatted(clientName("fc"));

  /** HBS3R341's SELECT; the Timber Mark search joins MARK_BILLED_CLI as the proc does. */
  private static final QueryDefinition MARK_HOLDER_SEARCH = QueryDefinition.builder("profiles.markHolders.search")
      .legacy("P340/P341")
      .capability(Capability.PROFILES_VIEW)
      .sql(MARK_HOLDER_SELECT)
      .filter(QueryFilter.upper("timberMark", """
          AND EXISTS (SELECT 1 FROM mark_billed_cli mbc
                       WHERE mbc.client_number = mhp.client_number
                         AND mbc.client_locn_code = mhp.client_locn_code
                         AND mbc.timber_mark = :timberMark)"""))
      .filter(QueryFilter.eq("clientNumber", "AND mhp.client_number = :clientNumber"))
      .filter(QueryFilter.eq("clientLocnCode", "AND mhp.client_locn_code = :clientLocnCode"))
      .sort("clientNumber", "mhp.client_number")
      .sort("returnTypeDesc", "rt.description")
      .sort("effectiveDate", "mhp.effective_date")
      .sort("expiryDate", "mhp.expiry_date")
      .orderBy("mhp.client_number, mhp.client_locn_code, rt.description, mhp.effective_date")
      .clientScope("AND mhp.client_number = :scopeClientNumber")
      .maxRows(2000)
      .build();

  private static final QueryDefinition MARK_HOLDER_RECORD = QueryDefinition.builder("profiles.markHolders.record")
      .legacy("P342/P344")
      .capability(Capability.PROFILES_VIEW)
      .sql(MARK_HOLDER_SELECT)
      .filter(QueryFilter.required("mhprofId", "AND mhp.mhprof_id = :mhprofId", Type.NUMBER))
      .clientScope("AND mhp.client_number = :scopeClientNumber")
      .maxRows(1)
      .build();

  // ---------------------------------------------------------------- P370–P374

  private static final String DELIVERY_SELECT = """
      SELECT cdp.dlvprof_id AS dlvprof_id,
             cdp.client_number AS client_number,
             cdp.client_locn_code AS client_locn_code,
             cdp.client_number || ' / ' || cdp.client_locn_code AS client_loc,
             %s AS client_name,
             cdp.hbs_delivery_method_code AS delivery_method,
             dm.description AS delivery_method_desc,
             cdp.effective_date AS effective_date,
             cdp.expiry_date AS expiry_date,
             cdp.entry_userid AS entry_userid,
             cdp.entry_timestamp AS entry_timestamp,
             cdp.update_userid AS update_userid
        FROM hbs_client_delivery_profile cdp
        JOIN v_client_public fc ON fc.client_number = cdp.client_number
        JOIN hbs_delivery_method_code dm ON dm.hbs_delivery_method_code = cdp.hbs_delivery_method_code
       WHERE 1=1""".formatted(clientName("fc"));

  /** HBS3R371's SELECT. */
  private static final QueryDefinition DELIVERY_SEARCH = QueryDefinition.builder("profiles.delivery.search")
      .legacy("P370/P371")
      .capability(Capability.PROFILES_VIEW)
      .sql(DELIVERY_SELECT)
      .filter(QueryFilter.required("clientNumber", "AND cdp.client_number = :clientNumber", Type.STRING))
      .filter(QueryFilter.eq("clientLocnCode", "AND cdp.client_locn_code = :clientLocnCode"))
      .sort("clientNumber", "cdp.client_number")
      .sort("effectiveDate", "cdp.effective_date")
      .sort("expiryDate", "cdp.expiry_date")
      .orderBy("cdp.client_number, cdp.client_locn_code, cdp.effective_date")
      .clientScope("AND cdp.client_number = :scopeClientNumber")
      .maxRows(2000)
      .build();

  private static final QueryDefinition DELIVERY_RECORD = QueryDefinition.builder("profiles.delivery.record")
      .legacy("P372/P374")
      .capability(Capability.PROFILES_VIEW)
      .sql(DELIVERY_SELECT)
      .filter(QueryFilter.required("dlvprofId", "AND cdp.dlvprof_id = :dlvprofId", Type.NUMBER))
      .clientScope("AND cdp.client_number = :scopeClientNumber")
      .maxRows(1)
      .build();

  // ---------------------------------------------------------------- P380–P385

  /** HBS3R380's SELECT (cruise-based marks with confirmed appraisals). */
  private static final QueryDefinition CBB_SEARCH = QueryDefinition.builder("profiles.cruiseBased.search")
      .legacy("P380/P381")
      .capability(Capability.PROFILES_VIEW)
      .sql("""
          SELECT * FROM (
            SELECT apw.timber_mark AS timber_mark,
                   apw.app_effective_date AS effective_date,
                   TO_CHAR(apw.app_effective_date, 'YYYY-MM-DD') AS effective_date_key,
                   apw.expiry_date AS expiry_date,
                   apw.ttl_merchntbl_area AS ttl_merchntbl_area,
                   apw.net_cruise_volume AS net_cruise_vol,
                   apw.int_deciduous_vol AS int_dec_vol,
                   apw.net_cruise_volume + apw.int_deciduous_vol AS total_vol,
                   CASE WHEN NVL(apw.ttl_merchntbl_area, 0) = 0 THEN 0
                        ELSE ROUND((apw.net_cruise_volume + apw.int_deciduous_vol) / apw.ttl_merchntbl_area, 3)
                   END AS m3_per_ha,
                   NVL(cb.cb_profile_m3, 0) AS cb_profile_m3,
                   CASE WHEN NVL(cb.cb_profile_m3, 0) = 0 THEN 'None'
                        ELSE TO_CHAR(cb.cb_profile_m3, 'FM999990.000') END AS profile_m3_display,
                   NVL(err.errors, 0) AS error_count,
                   ou1.org_unit_no AS org_unit_region,
                   ou2.org_unit_no AS org_unit_district,
                   ou2.org_unit_name AS forest_district_name,
                   mbc.client_number AS client_number
              FROM app_worksheet apw
              JOIN mark_billed_cli mbc ON mbc.timber_mark = apw.timber_mark
              JOIN org_unit ou1 ON ou1.org_unit_no = mbc.forest_region
              JOIN org_unit ou2 ON ou2.org_unit_no = mbc.forest_district
              LEFT JOIN (SELECT pss.timber_mark, apw1.app_effective_date, COUNT(*) AS errors
                           FROM piece_scale_summary pss
                           JOIN app_worksheet apw1 ON apw1.timber_mark = pss.timber_mark
                          WHERE pss.active_version_ind = 'Y'
                            AND pss.scl_rtn_version_state_code IN ('ERR', 'HLD', 'LCK')
                            AND pss.scale_date >= apw1.app_effective_date
                            AND pss.scale_date <= apw1.expiry_date
                          GROUP BY pss.timber_mark, apw1.app_effective_date) err
                ON err.timber_mark = apw.timber_mark AND err.app_effective_date = apw.app_effective_date
              LEFT JOIN (SELECT r.timber_mark, r.app_effective_date, SUM(r.volume_per_hectare) AS cb_profile_m3
                           FROM cruise_based_sp_grd_ratio r
                          GROUP BY r.timber_mark, r.app_effective_date) cb
                ON cb.timber_mark = apw.timber_mark AND cb.app_effective_date = apw.app_effective_date
             WHERE apw.appraisal_sts_st = 'CNF'
               AND mbc.cruise_based_ind = 'Y'
               AND TO_CHAR(apw.expiry_date, 'YYYYMM') >= '201006'
               AND mbc.file_type_code <> 'A11'
          ) cbb
          WHERE 1=1""")
      .filter(QueryFilter.upper("timberMark", "AND cbb.timber_mark = :timberMark"))
      .filter(QueryFilter.number("locatedIn",
          "AND (cbb.cb_profile_m3 <> cbb.m3_per_ha OR cbb.error_count <> 0)"
              + " AND :locatedIn IN (cbb.org_unit_region, cbb.org_unit_district)"))
      .sort("timberMark", "cbb.timber_mark")
      .sort("effectiveDate", "cbb.effective_date")
      .sort("errorCount", "cbb.error_count")
      .orderBy("cbb.timber_mark, cbb.effective_date")
      .clientScope("AND cbb.client_number = :scopeClientNumber")
      .build();

  /** P383/P385 header — Timber Mark Info + Appraisal Info + completeness status. */
  private static final QueryDefinition CBB_DETAIL = QueryDefinition.builder("profiles.cruiseBased.detail")
      .legacy("P383/P385")
      .capability(Capability.PROFILES_VIEW)
      .sql("""
          SELECT apw.timber_mark AS timber_mark,
                 apw.app_effective_date AS effective_date,
                 TO_CHAR(apw.app_effective_date, 'YYYY-MM-DD') AS effective_date_key,
                 mbc.cutting_permit_id AS cutting_permit_id,
                 mbc.mark_status_st AS mark_status,
                 tm.catastrophic_ind AS catastrophic_ind,
                 ou.org_unit_code || ' - ' || ou.org_unit_name AS admin_org,
                 mbc.cruise_based_ind AS cruise_based_ind,
                 apw.appraisal_sts_st AS appraisal_status,
                 apw.rate_calc_mthd_cd AS rate_calc_method,
                 apw.appraisal_mthd_cd AS appraisal_method,
                 apw.expiry_date AS expiry_date,
                 apw.adjust_qrterly_ind AS adjust_quarterly_ind,
                 apw.ttl_merchntbl_area AS ttl_merchntbl_area,
                 apw.others_tied_to_cd AS others_tied_to,
                 apw.conif_stand_rate_elig_code AS conif_stand_rate_elig,
                 apw.decid_stand_rate_elig_code AS decid_stand_rate_elig,
                 apw.net_cruise_volume AS net_cruise_vol,
                 apw.int_deciduous_vol AS int_dec_vol,
                 CASE WHEN NVL(apw.ttl_merchntbl_area, 0) = 0 THEN 0
                      ELSE ROUND((apw.net_cruise_volume + apw.int_deciduous_vol) / apw.ttl_merchntbl_area, 3)
                 END AS m3_per_ha,
                 NVL((SELECT SUM(r.volume_per_hectare) FROM cruise_based_sp_grd_ratio r
                       WHERE r.timber_mark = apw.timber_mark
                         AND r.app_effective_date = apw.app_effective_date), 0) AS cb_profile_m3,
                 CASE WHEN NVL((SELECT SUM(r.volume_per_hectare) FROM cruise_based_sp_grd_ratio r
                                 WHERE r.timber_mark = apw.timber_mark
                                   AND r.app_effective_date = apw.app_effective_date), 0)
                           = CASE WHEN NVL(apw.ttl_merchntbl_area, 0) = 0 THEN 0
                                  ELSE ROUND((apw.net_cruise_volume + apw.int_deciduous_vol) / apw.ttl_merchntbl_area, 3) END
                      THEN 'Billing Profile Complete' ELSE 'Billing Profile Incomplete' END AS status_message
            FROM app_worksheet apw
            JOIN mark_billed_cli mbc ON mbc.timber_mark = apw.timber_mark
            LEFT JOIN timber_mark tm ON tm.timber_mark = apw.timber_mark
            LEFT JOIN org_unit ou ON ou.org_unit_no = mbc.forest_district
           WHERE 1=1""")
      .filter(QueryFilter.required("timberMark", "AND apw.timber_mark = :timberMark", Type.UPPER))
      .filter(QueryFilter.required("effectiveDate", "AND TRUNC(apw.app_effective_date) = :effectiveDate", Type.DATE))
      .clientScope("AND mbc.client_number = :scopeClientNumber")
      .maxRows(1)
      .build();

  private static final String RATIO_SELECT = """
      SELECT r.timber_mark AS timber_mark,
             r.app_effective_date AS effective_date,
             TO_CHAR(r.app_effective_date, 'YYYY-MM-DD') AS effective_date_key,
             r.scale_species_code AS species_code,
             r.scale_grade_code AS grade_code,
             r.volume_per_hectare AS volume_per_hectare,
             SUM(r.volume_per_hectare) OVER (PARTITION BY r.timber_mark, r.app_effective_date, r.scale_species_code)
               AS species_volume_per_hectare,
             r.entry_userid AS entry_userid,
             r.entry_timestamp AS entry_timestamp
        FROM cruise_based_sp_grd_ratio r
       WHERE 1=1""";

  private static final String RATIO_SCOPE = """
      AND EXISTS (SELECT 1 FROM mark_billed_cli mbc
                   WHERE mbc.timber_mark = r.timber_mark
                     AND mbc.client_number = :scopeClientNumber)""";

  /** CruiseBasedSpGrRatioQuery.selectByCriteria — the P383/P385 profile grid. */
  private static final QueryDefinition CBB_RATIOS = QueryDefinition.builder("profiles.cruiseBased.ratios")
      .legacy("P383/P385")
      .capability(Capability.PROFILES_VIEW)
      .sql(RATIO_SELECT)
      .filter(QueryFilter.required("timberMark", "AND r.timber_mark = :timberMark", Type.UPPER))
      .filter(QueryFilter.required("effectiveDate", "AND TRUNC(r.app_effective_date) = :effectiveDate", Type.DATE))
      .sort("speciesCode", "r.scale_species_code")
      .sort("gradeCode", "r.scale_grade_code")
      .orderBy("r.scale_species_code, r.scale_grade_code")
      .clientScope(RATIO_SCOPE)
      .maxRows(500)
      .build();

  private static final QueryDefinition CBB_RATIO_RECORD = QueryDefinition.builder("profiles.cruiseBased.ratioRecord")
      .legacy("P383")
      .capability(Capability.PROFILES_VIEW)
      .sql(RATIO_SELECT)
      .filter(QueryFilter.required("timberMark", "AND r.timber_mark = :timberMark", Type.UPPER))
      .filter(QueryFilter.required("effectiveDate", "AND TRUNC(r.app_effective_date) = :effectiveDate", Type.DATE))
      .filter(QueryFilter.required("speciesCode", "AND r.scale_species_code = :speciesCode", Type.UPPER))
      .filter(QueryFilter.required("gradeCode", "AND r.scale_grade_code = :gradeCode", Type.UPPER))
      .clientScope(RATIO_SCOPE)
      .maxRows(1)
      .build();

  @Override
  public List<QueryDefinition> queries() {
    return List.of(
        MARK_HOLDER_SEARCH, MARK_HOLDER_RECORD,
        DELIVERY_SEARCH, DELIVERY_RECORD,
        CBB_SEARCH, CBB_DETAIL, CBB_RATIOS, CBB_RATIO_RECORD);
  }

  @Override
  public List<CommandDefinition> commands() {
    return List.of(
        // P343 — createAndStoreMarkHolderProfile
        CommandDefinition.builder("profiles.markHolders.create")
            .legacy("P343")
            .capability(Capability.PROFILE_BILLING_ADMIN)
            .procedure("HBS_CREATE_HBS_MARK_PROFILE")
            .sequence("HBS_MARK_HOLDER_PROFILE_SEQ")      // i_mhprof_id
            .requiredBody("clientNumber", Type.STRING)     // i_client_number
            .requiredBody("clientLocnCode", Type.STRING)   // i_client_locn_code
            .requiredBody("returnType", Type.UPPER)        // i_hbs_return_type_code
            .requiredBody("frequency", Type.UPPER)         // i_hbs_frequency_type_code
            .date("forcedSummarizationDate")               // i_forced_summarization_date (null on add)
            .requiredBody("effectiveDate", Type.DATE)      // i_effective_date
            .requiredBody("expiryDate", Type.DATE)         // i_expiry_date
            .auditUser()
            .now()
            .auditUser()
            .now()
            .build(),
        // P342 — updateMarkHolderProfile (expiry only)
        CommandDefinition.builder("profiles.markHolders.update")
            .legacy("P342")
            .capability(Capability.PROFILE_BILLING_ADMIN)
            .procedure("HBS_STORE_HBS_MARK_PROFILE")
            .existingRow("SELECT entry_userid, entry_timestamp FROM hbs_mark_holder_profile WHERE mhprof_id = :mhprofId")
            .requiredBody("mhprofId", Type.NUMBER)
            .requiredBody("clientNumber", Type.STRING)
            .requiredBody("clientLocnCode", Type.STRING)
            .requiredBody("returnType", Type.UPPER)
            .requiredBody("frequency", Type.UPPER)
            .date("forcedSummarizationDate")
            .requiredBody("effectiveDate", Type.DATE)
            .requiredBody("expiryDate", Type.DATE)
            .existing("entryUserid")
            .existing("entryTimestamp")
            .auditUser()
            .now()
            .build(),
        // P344 Yes — removeMarkHolderProfile
        CommandDefinition.builder("profiles.markHolders.delete")
            .legacy("P344")
            .capability(Capability.PROFILE_BILLING_ADMIN)
            .procedure("HBS_REMOVE_HBS_MARK_PROFILE")
            .requiredBody("mhprofId", Type.NUMBER)
            .build(),
        // P373 — createAndStoreClientDeliveryProfile
        CommandDefinition.builder("profiles.delivery.create")
            .legacy("P373")
            .capability(Capability.PROFILE_BILLING_ADMIN)
            .procedure("HBS_CREATE_HBS_CLI_DEL_PROFILE")
            .sequence("HBS_CLI_DEL_PROFILE_SEQ")           // i_dlvprof_id
            .requiredBody("clientNumber", Type.STRING)
            .requiredBody("clientLocnCode", Type.STRING)
            .requiredBody("deliveryMethod", Type.UPPER)
            .requiredBody("effectiveDate", Type.DATE)
            .requiredBody("expiryDate", Type.DATE)
            .auditUser()
            .now()
            .auditUser()
            .now()
            .build(),
        // P372 — updateClientDeliveryProfile (entry user kept; update user now recorded correctly)
        CommandDefinition.builder("profiles.delivery.update")
            .legacy("P372")
            .capability(Capability.PROFILE_BILLING_ADMIN)
            .procedure("HBS_STORE_HBS_CLI_DEL_PROFILE")
            .existingRow("SELECT entry_userid, entry_timestamp FROM hbs_client_delivery_profile WHERE dlvprof_id = :dlvprofId")
            .requiredBody("dlvprofId", Type.NUMBER)
            .requiredBody("clientNumber", Type.STRING)
            .requiredBody("clientLocnCode", Type.STRING)
            .requiredBody("deliveryMethod", Type.UPPER)
            .requiredBody("effectiveDate", Type.DATE)
            .requiredBody("expiryDate", Type.DATE)
            .existing("entryUserid")
            .existing("entryTimestamp")
            .auditUser()
            .now()
            .build(),
        // P374 Yes — removeClientDeliveryProfile
        CommandDefinition.builder("profiles.delivery.delete")
            .legacy("P374")
            .capability(Capability.PROFILE_BILLING_ADMIN)
            .procedure("HBS_REMOVE_HBS_CLI_DEL_PROFILE")
            .requiredBody("dlvprofId", Type.NUMBER)
            .build(),
        // P383 grid row add — createAndStoreCruiseBasedSpGrRatio
        CommandDefinition.builder("profiles.cruiseBased.ratio.create")
            .legacy("P383")
            .capability(Capability.PROFILE_BILLING_ADMIN)
            .procedure("HBS_CREATE_CB_SP_GRD_RATIO")
            .requiredBody("timberMark", Type.UPPER)
            .requiredBody("effectiveDate", Type.DATE)
            .requiredBody("speciesCode", Type.UPPER)
            .requiredBody("gradeCode", Type.UPPER)
            .requiredBody("volumePerHectare", Type.NUMBER)
            .auditUser()
            .now()
            .auditUser()
            .now()
            .build(),
        // P383 grid row change (legacy deleted + re-inserted every row; the store proc keeps the row)
        CommandDefinition.builder("profiles.cruiseBased.ratio.update")
            .legacy("P383")
            .capability(Capability.PROFILE_BILLING_ADMIN)
            .procedure("HBS_STORE_CB_SP_GRD_RATIO")
            .existingRow("SELECT entry_userid, entry_timestamp FROM cruise_based_sp_grd_ratio"
                + " WHERE timber_mark = UPPER(:timberMark)"
                + " AND app_effective_date = TO_DATE(SUBSTR(:effectiveDate, 1, 10), 'YYYY-MM-DD')"
                + " AND scale_species_code = UPPER(:speciesCode) AND scale_grade_code = UPPER(:gradeCode)")
            .requiredBody("timberMark", Type.UPPER)
            .requiredBody("effectiveDate", Type.DATE)
            .requiredBody("speciesCode", Type.UPPER)
            .requiredBody("gradeCode", Type.UPPER)
            .requiredBody("volumePerHectare", Type.NUMBER)
            .existing("entryUserid")
            .existing("entryTimestamp")
            .auditUser()
            .now()
            .build(),
        // P383 "Delete?" checkbox — removeCruiseBasedSpGrRatio
        CommandDefinition.builder("profiles.cruiseBased.ratio.delete")
            .legacy("P383")
            .capability(Capability.PROFILE_BILLING_ADMIN)
            .procedure("HBS_REMOVE_CB_SP_GRD_RATIO")
            .requiredBody("timberMark", Type.UPPER)
            .requiredBody("effectiveDate", Type.DATE)
            .requiredBody("speciesCode", Type.UPPER)
            .requiredBody("gradeCode", Type.UPPER)
            .build());
  }
}
