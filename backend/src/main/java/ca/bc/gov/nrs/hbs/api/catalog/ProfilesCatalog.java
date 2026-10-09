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
 * Profiles area (legacy {@code /cpm/*}), part 1: Client Profile (P310/P311),
 * Scale Site Profiles (P320–P323), Population Profiles (P330–P333) and Scaler
 * Profiles (P360–P365). Mark holder, statement delivery and cruise-based
 * billing profiles are in {@link ProfilesBillingCatalog}.
 *
 * <p>SQL ported from the legacy {@code HBS2R321} / {@code HBS3R331} procs,
 * {@code ClientProfileManagerBean} and {@code ClientProfileManagerQuery}
 * (hbs-ejb/src/main/java/ca/bc/gov/mof/hbs/cpm). See docs/areas/profiles.md.
 */
@Component
public class ProfilesCatalog implements QueryCatalog {

  /** FOI severing of an individual client's name (legacy "Not Releasable"). */
  static String clientName(String fc) {
    return "CASE WHEN :hbsIsMinistry = 'Y' OR " + fc + ".client_type_code <> 'I' OR "
        + fc + ".client_number = :hbsViewerClient THEN " + fc + ".client_name"
        + " ELSE 'Not Releasable' END";
  }

  // ---------------------------------------------------------------- P310/P311

  private static final QueryDefinition CLIENT_SEARCH = QueryDefinition.builder("profiles.client.search")
      .legacy("P310")
      .capability(Capability.PROFILES_VIEW)
      .sql("""
          SELECT cl.client_number AS client_number,
                 cl.client_locn_code AS client_locn_code,
                 %s AS client_name,
                 cl.client_locn_name AS location_name,
                 cl.city AS city
            FROM client_location cl
            JOIN v_client_public fc ON fc.client_number = cl.client_number
           WHERE 1=1""".formatted(clientName("fc")))
      .filter(QueryFilter.required("clientNumber", "AND cl.client_number = :clientNumber", Type.STRING))
      .filter(QueryFilter.eq("clientLocnCode", "AND cl.client_locn_code = :clientLocnCode"))
      .sort("clientNumber", "cl.client_number")
      .sort("clientLocnCode", "cl.client_locn_code")
      .orderBy("cl.client_number, cl.client_locn_code")
      .clientScope("AND cl.client_number = :scopeClientNumber")
      .build();

  /** P311 — client/location master data plus the "Client Profile Links" counts. */
  private static final QueryDefinition CLIENT_DETAIL = QueryDefinition.builder("profiles.client.detail")
      .legacy("P311")
      .capability(Capability.PROFILES_VIEW)
      .sql("""
          SELECT cl.client_number AS client_number,
                 cl.client_locn_code AS client_locn_code,
                 %s AS client_name,
                 ctc.expanded_result AS client_type,
                 cl.client_locn_name AS location_name,
                 cl.address_1 AS address_1,
                 cl.address_2 AS address_2,
                 cl.address_3 AS address_3,
                 cl.city AS city,
                 cl.province AS province,
                 cl.country AS country,
                 cl.postal_code AS postal_code,
                 CASE WHEN LENGTH(cl.business_phone) = 10
                      THEN '(' || SUBSTR(cl.business_phone, 1, 3) || ')-' || SUBSTR(cl.business_phone, 4, 3)
                           || '-' || SUBSTR(cl.business_phone, 7)
                      ELSE cl.business_phone END AS business_phone,
                 (SELECT COUNT(*) FROM trading_partner_submitter tps
                   WHERE tps.client_number = cl.client_number AND tps.client_locn_code = cl.client_locn_code)
                 + (SELECT COUNT(*) FROM scale_site_profile ssp
                      JOIN scale_site ss ON ss.scale_site_id_nmbr = ssp.scale_site_id_nmbr
                     WHERE ss.owner_cli_number = cl.client_number AND ss.owner_cli_locn_cd = cl.client_locn_code)
                   AS scale_site_profile_count,
                 (SELECT COUNT(*) FROM hbs_mark_holder_profile mhp
                   WHERE mhp.client_number = cl.client_number AND mhp.client_locn_code = cl.client_locn_code)
                   AS mark_holder_profile_count,
                 (SELECT COUNT(*) FROM population_profile pp
                    JOIN population pop ON pop.population_number = pp.population_number
                                       AND pop.sampling_year = pp.sampling_year
                   WHERE pop.client_number = cl.client_number AND pop.client_locn_code = cl.client_locn_code)
                   AS population_profile_count,
                 (SELECT COUNT(*) FROM hbs_client_delivery_profile cdp
                   WHERE cdp.client_number = cl.client_number AND cdp.client_locn_code = cl.client_locn_code)
                   AS delivery_profile_count
            FROM client_location cl
            JOIN v_client_public fc ON fc.client_number = cl.client_number
            LEFT JOIN code_list_table ctc ON ctc.column_name = 'CLIENT_TYPE_CODE' AND ctc.code_argument = fc.client_type_code
           WHERE 1=1""".formatted(clientName("fc")))
      .filter(QueryFilter.required("clientNumber", "AND cl.client_number = :clientNumber", Type.STRING))
      .filter(QueryFilter.required("clientLocnCode", "AND cl.client_locn_code = :clientLocnCode", Type.STRING))
      .clientScope("AND cl.client_number = :scopeClientNumber")
      .maxRows(1)
      .build();

  // ---------------------------------------------------------------- P320–P323

  /**
   * HBS2R321's SELECT (TRADING_PARTNER_SUBMITTER summary rows UNION
   * SCALE_SITE_PROFILE detail rows), wrapped so the three legacy search types
   * (Scale Site / Trading Partner / Site Owner) are plain filters.
   * {@code profile_key} disambiguates the two id spaces ('TPS…' / 'SSP…').
   */
  private static final QueryDefinition SCALE_SITE_SEARCH = QueryDefinition.builder("profiles.scaleSites.search")
      .legacy("P320/P321")
      .capability(Capability.PROFILES_VIEW)
      .sql("""
          SELECT * FROM (
            SELECT 'TPS' || tps.tps_id AS profile_key,
                   'TPS' AS profile_source,
                   tps.tps_id AS tps_id,
                   tps.scale_site_id_nmbr AS scale_site_id,
                   tps.tp_doc_type AS doc_type,
                   CASE tps.tp_doc_type WHEN 'S' THEN 'Summary' WHEN 'D' THEN 'Detail' END AS submission_type,
                   tps.hbs_return_type_code AS return_type,
                   rt.description AS return_type_desc,
                   tps.effective_date AS effective_date,
                   tps.expiry_date AS expiry_date,
                   tps.client_number AS client_number,
                   tps.client_locn_code AS client_locn_code,
                   tps.tpsubmtr_type AS submitter_type,
                   CASE WHEN fc.client_number IS NULL THEN NULL ELSE %s END AS client_name,
                   ss.owner_cli_number AS owner_client_number,
                   ss.owner_cli_locn_cd AS owner_client_locn_code
              FROM trading_partner_submitter tps
              LEFT JOIN v_client_public fc ON fc.client_number = tps.client_number
              LEFT JOIN scale_site ss ON ss.scale_site_id_nmbr = tps.scale_site_id_nmbr
              LEFT JOIN hbs_return_type_code rt ON rt.hbs_return_type_code = tps.hbs_return_type_code
            UNION ALL
            SELECT 'SSP' || ssp.ssprof_id,
                   'SSP',
                   NULL,
                   ssp.scale_site_id_nmbr,
                   ssp.doc_type,
                   CASE ssp.doc_type WHEN 'S' THEN 'Summary' WHEN 'D' THEN 'Detail' END,
                   ssp.hbs_return_type_code,
                   rt.description,
                   ssp.effective_date,
                   ssp.expiry_date,
                   NULL,
                   NULL,
                   NULL,
                   CASE WHEN fc.client_number IS NULL THEN NULL ELSE %s END,
                   ss.owner_cli_number,
                   ss.owner_cli_locn_cd
              FROM scale_site_profile ssp
              JOIN scale_site ss ON ss.scale_site_id_nmbr = ssp.scale_site_id_nmbr
              LEFT JOIN v_client_public fc ON fc.client_number = ss.owner_cli_number
              LEFT JOIN hbs_return_type_code rt ON rt.hbs_return_type_code = ssp.hbs_return_type_code
          ) p
          WHERE 1=1""".formatted(clientName("fc"), clientName("fc")))
      .filter(QueryFilter.upper("scaleSiteId", "AND p.scale_site_id = :scaleSiteId"))
      // Trading partner (HBS2R321 RB_CLIENT_NUMBER: TPS.CLIENT_NUMBER / SS.OWNER_CLI_NUMBER)
      .filter(QueryFilter.eq("clientNumber",
          "AND (p.client_number = :clientNumber OR (p.profile_source = 'SSP' AND p.owner_client_number = :clientNumber))"))
      .filter(QueryFilter.eq("clientLocnCode",
          "AND (p.client_locn_code = :clientLocnCode OR (p.profile_source = 'SSP' AND p.owner_client_locn_code = :clientLocnCode))"))
      // Site owner (P320 "Site Owner": every profile of the sites the client owns)
      .filter(QueryFilter.eq("ownerClientNumber", "AND p.owner_client_number = :ownerClientNumber"))
      .filter(QueryFilter.eq("ownerClientLocnCode", "AND p.owner_client_locn_code = :ownerClientLocnCode"))
      .sort("scaleSiteId", "p.scale_site_id")
      .sort("submissionType", "p.submission_type")
      .sort("returnTypeDesc", "p.return_type_desc")
      .sort("effectiveDate", "p.effective_date")
      .sort("expiryDate", "p.expiry_date")
      .sort("clientNumber", "p.client_number")
      .orderBy("p.doc_type, p.scale_site_id, p.client_number, p.effective_date")
      .clientScope("AND (p.client_number = :scopeClientNumber OR p.owner_client_number = :scopeClientNumber)")
      .build();

  /** P322 — one TRADING_PARTNER_SUBMITTER row, keyed by the list's profile_key. */
  private static final QueryDefinition SCALE_SITE_RECORD = QueryDefinition.builder("profiles.scaleSites.record")
      .legacy("P322")
      .capability(Capability.SCALE_SITE_ADMIN)
      .sql("""
          SELECT tps.tps_id AS tps_id,
                 tps.tpsubmtr_type AS submitter_type,
                 tps.client_number AS client_number,
                 tps.client_locn_code AS client_locn_code,
                 tps.scale_site_id_nmbr AS scale_site_id,
                 tps.hbs_return_type_code AS return_type,
                 tps.tp_doc_type AS doc_type,
                 tps.effective_date AS effective_date,
                 tps.expiry_date AS expiry_date,
                 tps.entry_userid AS entry_userid,
                 tps.entry_timestamp AS entry_timestamp
            FROM trading_partner_submitter tps
           WHERE 1=1""")
      .filter(QueryFilter.required("profileKey",
          "AND tps.tps_id = CASE WHEN SUBSTR(:profileKey, 1, 3) = 'TPS'"
              + " AND REGEXP_LIKE(SUBSTR(:profileKey, 4), '^[0-9]{1,30}$')"
              + " THEN TO_NUMBER(SUBSTR(:profileKey, 4)) END",
          Type.UPPER))
      .maxRows(1)
      .build();

  // ---------------------------------------------------------------- P330–P333

  /** HBS3R331's SELECT (population profiles by client/location or population). */
  private static final QueryDefinition POPULATION_SEARCH = QueryDefinition.builder("profiles.population.search")
      .legacy("P330/P331")
      .capability(Capability.PROFILES_VIEW)
      .sql("""
          SELECT pp.popprof_id AS popprof_id,
                 pp.population_number AS population_number,
                 pp.sampling_year AS sampling_year,
                 pp.effective_date AS effective_date,
                 pp.expiry_date AS expiry_date,
                 pp.hbs_frequency_type_code AS frequency,
                 ftc.description AS frequency_desc,
                 pp.last_ratio_stmt_date AS last_ratio_stmt_date,
                 pp.next_ratio_stmt_date AS next_ratio_stmt_date,
                 pop.client_number AS client_number,
                 pop.client_locn_code AS client_locn_code,
                 CASE WHEN fc.client_number IS NULL THEN NULL ELSE %s END AS client_name
            FROM population_profile pp
            LEFT JOIN population pop ON pop.population_number = pp.population_number
                                    AND pop.sampling_year = pp.sampling_year
            LEFT JOIN v_client_public fc ON fc.client_number = pop.client_number
            LEFT JOIN hbs_frequency_type_code ftc ON ftc.hbs_frequency_type_code = pp.hbs_frequency_type_code
           WHERE 1=1""".formatted(clientName("fc")))
      .filter(QueryFilter.eq("populationNumber", "AND pp.population_number = :populationNumber"))
      .filter(QueryFilter.eq("clientNumber", "AND pop.client_number = :clientNumber"))
      .filter(QueryFilter.eq("clientLocnCode", "AND pop.client_locn_code = :clientLocnCode"))
      .sort("populationNumber", "pp.population_number")
      .sort("samplingYear", "pp.sampling_year")
      .sort("effectiveDate", "pp.effective_date")
      .sort("expiryDate", "pp.expiry_date")
      .orderBy("pp.population_number ASC, pp.popprof_id DESC")
      .clientScope("AND pop.client_number = :scopeClientNumber")
      .maxRows(2000)
      .build();

  private static final QueryDefinition POPULATION_RECORD = QueryDefinition.builder("profiles.population.record")
      .legacy("P332")
      .capability(Capability.PROFILES_VIEW)
      .sql("""
          SELECT pp.popprof_id AS popprof_id,
                 pp.population_number AS population_number,
                 pp.sampling_year AS sampling_year,
                 pp.effective_date AS effective_date,
                 pp.expiry_date AS expiry_date,
                 pp.hbs_frequency_type_code AS frequency,
                 ftc.description AS frequency_desc,
                 pp.last_ratio_stmt_date AS last_ratio_stmt_date,
                 pp.next_ratio_stmt_date AS next_ratio_stmt_date,
                 pp.start_ratio_stmt_date AS start_ratio_stmt_date,
                 pp.final_bill_date AS final_bill_date,
                 pop.client_number AS client_number,
                 pop.client_locn_code AS client_locn_code,
                 pp.entry_userid AS entry_userid,
                 pp.update_userid AS update_userid
            FROM population_profile pp
            LEFT JOIN population pop ON pop.population_number = pp.population_number
                                    AND pop.sampling_year = pp.sampling_year
            LEFT JOIN hbs_frequency_type_code ftc ON ftc.hbs_frequency_type_code = pp.hbs_frequency_type_code
           WHERE 1=1""")
      .filter(QueryFilter.required("popprofId", "AND pp.popprof_id = :popprofId", Type.NUMBER))
      .clientScope("AND pop.client_number = :scopeClientNumber")
      .maxRows(1)
      .build();

  // ---------------------------------------------------------------- P360–P365

  /**
   * Industry fence for scaler data: a CLI_SCALER only sees scaler clients
   * whose authentication keys are linked to HBS users of the caller's own
   * client. (Exact "own key only" matching needs a viewer user-id bind the
   * framework doesn't provide yet — see docs/areas/profiles.md.)
   */
  private static final String SCALER_SCOPE = """
      AND EXISTS (SELECT 1 FROM hbs_scaler_auth_key sk
                    JOIN hbs_user hu ON hu.user_id = sk.user_id
                   WHERE sk.client_number = ql.client_number
                     AND hu.client_number = :scopeClientNumber)""";

  /** P360 — scaler licence lookup (QUANT_LICENSE type 'S'). */
  private static final QueryDefinition SCALER_SEARCH = QueryDefinition.builder("profiles.scalers.search")
      .legacy("P360")
      .capability(Capability.SCALER_PROFILE_EDIT)
      .sql("""
          SELECT ql.license_number AS licence_number,
                 ql.client_number AS client_number,
                 %s AS client_name,
                 ql.quant_lic_sts_st AS licence_status,
                 (SELECT MAX(k.expiry_date) FROM hbs_scaler_auth_key k
                   WHERE k.client_number = ql.client_number) AS ak_expiry_date
            FROM quant_license ql
            JOIN v_client_public fc ON fc.client_number = ql.client_number
           WHERE ql.quant_lic_type_cd = 'S'""".formatted(clientName("fc")))
      .filter(QueryFilter.required("licenceNumber", "AND ql.license_number = :licenceNumber", Type.UPPER))
      .orderBy("ql.license_number")
      .clientScope(SCALER_SCOPE)
      .build();

  /** P364 — ClientProfileManagerQuery.selectScalerToUpdate, with bound parameters. */
  private static final QueryDefinition SCALER_EXPIRING = QueryDefinition.builder("profiles.scalers.expiring")
      .legacy("P360/P364")
      .capability(Capability.SCALE_SITE_ADMIN)
      .sql("""
          SELECT * FROM (
            SELECT scl.license_number AS licence_number,
                   fc.client_name AS client_name,
                   hsak.user_id AS user_id,
                   ld.last_active AS last_active,
                   hsak.effective_date AS effective_date,
                   hsak.expiry_date AS expiry_date,
                   hsak.entry_userid AS entry_userid,
                   hsak.update_userid AS update_userid,
                   scl.scalers_ou AS scalers_ou,
                   ou.rollup_dist_no AS dst_no,
                   ou.rollup_region_no AS rgn_no
              FROM hbs_scaler_auth_key hsak
              JOIN (SELECT DISTINCT b.license_number, b.client_number, a.org_unit_no AS scalers_ou
                      FROM scaler a JOIN quant_license b ON a.client_number = b.client_number) scl
                ON hsak.client_number = scl.client_number
              JOIN org_unit ou ON ou.org_unit_no = scl.scalers_ou
              JOIN v_client_public fc ON fc.client_number = scl.client_number
              JOIN (SELECT u.signing_license_number, TRUNC(MAX(u.scale_date)) AS last_active
                      FROM (SELECT signing_license_number, scale_date FROM log_tally WHERE active_version_ind = 'Y'
                            UNION ALL
                            SELECT signing_license_number, scale_date FROM weigh_slip WHERE active_version_ind = 'Y'
                            UNION ALL
                            SELECT signing_license_number, scale_date FROM sample_log_tally WHERE active_version_ind = 'Y') u
                     GROUP BY u.signing_license_number) ld
                ON ld.signing_license_number = scl.license_number
             WHERE hsak.hsak_id IN (SELECT MAX(k2.hsak_id) FROM hbs_scaler_auth_key k2
                                     WHERE k2.client_number = hsak.client_number
                                       AND k2.expiry_date = (SELECT MAX(k3.expiry_date) FROM hbs_scaler_auth_key k3
                                                              WHERE k3.client_number = hsak.client_number))
          ) x
          WHERE x.last_active > (SYSDATE - 365)""")
      .filter(QueryFilter.required("expiryDate", "AND TRUNC(x.expiry_date) <= :expiryDate", Type.DATE))
      .filter(QueryFilter.number("orgUnitNo", "AND :orgUnitNo IN (x.scalers_ou, x.dst_no, x.rgn_no)"))
      .sort("licenceNumber", "x.licence_number")
      .sort("lastActive", "x.last_active")
      .sort("expiryDate", "x.expiry_date")
      .orderBy("x.expiry_date, x.last_active DESC")
      .build();

  /** P362 header — the scaler licence being maintained. */
  private static final QueryDefinition SCALER_DETAIL = QueryDefinition.builder("profiles.scalers.detail")
      .legacy("P362")
      .capability(Capability.SCALER_PROFILE_EDIT)
      .sql("""
          SELECT ql.license_number AS licence_number,
                 ql.client_number AS client_number,
                 %s AS client_name,
                 cur.user_id AS user_id,
                 cur.effective_date AS active_from,
                 cur.expiry_date AS active_to
            FROM quant_license ql
            JOIN v_client_public fc ON fc.client_number = ql.client_number
            LEFT JOIN (SELECT k.client_number, k.user_id, k.effective_date, k.expiry_date,
                              ROW_NUMBER() OVER (PARTITION BY k.client_number ORDER BY k.effective_date DESC) AS rn
                         FROM hbs_scaler_auth_key k) cur
              ON cur.client_number = ql.client_number AND cur.rn = 1
           WHERE ql.quant_lic_type_cd = 'S'""".formatted(clientName("fc")))
      .filter(QueryFilter.required("licenceNumber", "AND ql.license_number = :licenceNumber", Type.UPPER))
      .clientScope(SCALER_SCOPE)
      .maxRows(1)
      .build();

  /**
   * P362 "Scaler Profile History". The encrypted key column is never
   * selected (the legacy screen decrypted it with DBMS_CRYPTO and masked it).
   */
  private static final QueryDefinition SCALER_KEY_HISTORY = QueryDefinition.builder("profiles.scalers.keyHistory")
      .legacy("P362")
      .capability(Capability.SCALER_PROFILE_EDIT)
      .sql("""
          SELECT k.hsak_id AS hsak_id,
                 k.user_id AS user_id,
                 '*********' AS authentication_key,
                 k.effective_date AS effective_date,
                 k.expiry_date AS expiry_date,
                 k.entry_userid AS entry_userid,
                 k.update_userid AS update_userid
            FROM hbs_scaler_auth_key k
           WHERE 1=1""")
      .filter(QueryFilter.required("licenceNumber",
          "AND k.client_number IN (SELECT q.client_number FROM quant_license q WHERE q.license_number = :licenceNumber)",
          Type.UPPER))
      .orderBy("k.effective_date DESC")
      .clientScope("""
          AND EXISTS (SELECT 1 FROM hbs_scaler_auth_key sk
                        JOIN hbs_user hu ON hu.user_id = sk.user_id
                       WHERE sk.client_number = k.client_number
                         AND hu.client_number = :scopeClientNumber)""")
      .build();

  @Override
  public List<QueryDefinition> queries() {
    return List.of(
        CLIENT_SEARCH, CLIENT_DETAIL,
        SCALE_SITE_SEARCH, SCALE_SITE_RECORD,
        POPULATION_SEARCH, POPULATION_RECORD,
        SCALER_SEARCH, SCALER_EXPIRING, SCALER_DETAIL, SCALER_KEY_HISTORY);
  }

  @Override
  public List<CommandDefinition> commands() {
    return List.of(
        // P323 — ClientProfileManagerBean.createAndStoreTradingPartnerSubmitter
        CommandDefinition.builder("profiles.scaleSites.create")
            .legacy("P323")
            .capability(Capability.SCALE_SITE_ADMIN)
            .procedure("HBS_CREATE_TRA_PART_SUBMITTER")
            .sequence("TRADING_PARTNER_SUBMITTER_SEQ")       // i_tps_id
            .constant("TPSS")                                 // i_tpsubmtr_type
            .text("clientNumber")                             // i_client_number (blank for Detail)
            .text("clientLocnCode")                           // i_client_locn_code
            .requiredBody("scaleSiteId", Type.UPPER)          // i_scale_site_id_nmbr
            .requiredBody("returnType", Type.UPPER)           // i_hbs_return_type_code
            .auditUser()                                      // i_entry_userid
            .now()                                            // i_entry_timestamp
            .auditUser()                                      // i_update_userid
            .now()                                            // i_update_timestamp
            .requiredBody("effectiveDate", Type.DATE)         // i_effective_date
            .requiredBody("expiryDate", Type.DATE)            // i_expiry_date
            .requiredBody("docType", Type.UPPER)              // i_tp_doc_type
            .build(),
        // P322 — ClientProfileManagerBean.updateTradingPartnerSubmitter (only expiry is editable)
        CommandDefinition.builder("profiles.scaleSites.update")
            .legacy("P322")
            .capability(Capability.SCALE_SITE_ADMIN)
            .procedure("HBS_STORE_TRA_PART_SUBMITTER")
            .existingRow("SELECT entry_userid, entry_timestamp FROM trading_partner_submitter WHERE tps_id = :tpsId")
            .requiredBody("tpsId", Type.NUMBER)
            .requiredBody("submitterType", Type.UPPER)
            .text("clientNumber")
            .text("clientLocnCode")
            .requiredBody("scaleSiteId", Type.UPPER)
            .requiredBody("returnType", Type.UPPER)
            .existing("entryUserid")
            .existing("entryTimestamp")
            .auditUser()
            .now()
            .requiredBody("effectiveDate", Type.DATE)
            .requiredBody("expiryDate", Type.DATE)
            .requiredBody("docType", Type.UPPER)
            .build(),
        // P332 Delete — ClientProfileManagerBean.removePopulationProfile
        CommandDefinition.builder("profiles.population.delete")
            .legacy("P332")
            .capability(Capability.SAMPLING_ADMIN)
            .procedure("HBS_REMOVE_POPULATION_PROFILE")
            .requiredBody("popprofId", Type.NUMBER)
            .build());
  }
}
