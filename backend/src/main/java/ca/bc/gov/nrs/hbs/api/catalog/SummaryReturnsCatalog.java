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
 * Scale Returns - Summary (legacy {@code /dac/summary/*}: P042, P045, P051/P101/P151,
 * P052/P102/P152 and their sub-pages P055/P056/P105/P106/P107/P155/P156).
 *
 * <p>The legacy search ran the REF-CURSOR procs {@code HBS2R051} (piece),
 * {@code HBS2R101} (weight) and {@code HBS2R151} (sample), which only build dynamic,
 * string-concatenated SQL. Their SELECTs are ported here as ONE union over the three
 * summary tables so the Home dashboard can deep-link a single screen with
 * {@code ?returnType=P|W|S&status=ERR|HLD&generated=Y|N}. The Bill To / Copy To
 * association is the one computed by {@code HBS_PIECE_SCALE_PAYBYCOPYTO} /
 * {@code HBS_WEIGHT_SCALE_PAYBYCOPYTO}. The seven-year limit ({@code strWhereLimitYear},
 * {@code DacValidator.compareForYearLimit}) is reproduced as {@code within_limit}.
 *
 * <p>Writes: none. Every legacy summary-return write (add, update, segregations,
 * notations, daily loads, hold/release/discard/cancel/replace, rate corrections,
 * re-queue) runs through {@code SummaryScaleReturnManagerBean} /
 * {@code SummaryWorkflowManagerBean} Java logic (versioning, edits, pricing), not a
 * single table-API proc call, so it is NOT exposed as a registry command. See
 * docs/areas/summary-returns.md, "Legacy logic not yet ported", for the reserved
 * command ids the screens already point at.
 */
@Component
public class SummaryReturnsCatalog implements QueryCatalog {

  /** Effective Bill To / Copy To exclusions used by the PAYBYCOPYTO functions. */
  private static final String BCOMB = "('BCOMB2', 'BCOMB3')";

  /** Active version older than Jan 1 of (current year - 7) → document unavailable (P174). */
  private static String limit(String table, String alias) {
    return "CASE WHEN EXISTS (SELECT 1 FROM " + table + " lim"
        + " WHERE lim.document_control_number = " + alias + ".document_control_number"
        + " AND lim.active_version_ind = 'Y'"
        + " AND lim.scale_date < ADD_MONTHS(TRUNC(SYSDATE, 'YYYY'), -84)) THEN 'N' ELSE 'Y' END AS within_limit";
  }

  /**
   * One row per summary-return version of every type. {@code detail} adds the
   * type-specific header fields shown on P052/P102/P152 and edited on P053/P103/P153.
   */
  static String union(boolean detail) {
    String piece = """
        SELECT 'P' AS return_type, pss.document_control_number, pss.version,
               hsr.scale_return_state_code AS return_state_code, hsr.generated_summary_ind AS generated,
               pss.scl_rtn_version_state_code AS status, pss.scl_rtn_category_code AS category,
               pss.pssmry_type, pss.scale_site_id_nmbr AS scale_site, pss.scale_date, pss.timber_mark,
               pss.license_number AS scaler_licence, pss.return_number,
               CAST(NULL AS VARCHAR2(4)) AS population_number, CAST(NULL AS VARCHAR2(2)) AS stratum_number,
               CAST(NULL AS NUMBER) AS sampling_year, pss.field_scale_deck_id,
               pss.active_version_ind, pss.visibility_ind,
               NVL(pss.client_number_paid_by, CASE WHEN pss.timber_mark IN %1$s THEN ss.owner_cli_number
                   ELSE mbc.client_number END) AS bill_to_client,
               NVL(pss.client_number_copied_to, CASE WHEN pss.timber_mark NOT IN %1$s THEN ss.owner_cli_number END)
                   AS copy_to_client,
               mbc.client_number AS mark_holder_client, ss.owner_cli_number AS site_owner_client,
               CAST(NULL AS VARCHAR2(8)) AS stratum_owner_client,
               ss.org_unit_no AS scale_district_no, ou.rollup_region_no AS scale_region_no,
               mbc.forest_district AS harvest_district_no, mbc.forest_region AS harvest_region_no,
               %2$s%3$s
          FROM piece_scale_summary pss
          JOIN hbs_scale_return hsr ON hsr.document_control_number = pss.document_control_number
          LEFT JOIN scale_site ss ON ss.scale_site_id_nmbr = pss.scale_site_id_nmbr
          LEFT JOIN org_unit ou ON ou.org_unit_no = ss.org_unit_no
          LEFT JOIN mark_billed_cli mbc ON mbc.timber_mark = pss.timber_mark"""
        .formatted(BCOMB, limit("piece_scale_summary", "pss"), detail ? """
            ,
                   pss.log_count, pss.number_of_chains, pss.camp_boom_number, pss.number_of_sections,
                   pss.place_of_scale, pss.area_cut, pss.cut_block_id, pss.version_comment AS comment_text,
                   pss.pending_txn_comment,
                   pss.client_number_paid_by AS bill_to_client_number, pss.client_locn_code_paid_by AS bill_to_client_locn,
                   pss.client_number_copied_to AS copy_to_client_number, pss.client_locn_code_copied_to AS copy_to_client_locn,
                   pss.input_timber_mark, pss.input_scaler_license_num AS input_scaler_licence,
                   CAST(NULL AS NUMBER) AS total_loads, CAST(NULL AS NUMBER) AS total_weight,
                   CAST(NULL AS NUMBER) AS net_weight, CAST(NULL AS VARCHAR2(10)) AS load_arrival_number,
                   CAST(NULL AS VARCHAR2(10)) AS weigh_slip_number, CAST(NULL AS NUMBER) AS sample_volume,
                   CAST(NULL AS VARCHAR2(1)) AS check_scale_ind,
                   pss.update_userid, pss.update_timestamp""" : "");

    String weight = """
        SELECT 'W', wss.document_control_number, wss.version,
               hsr.scale_return_state_code, hsr.generated_summary_ind,
               wss.scl_rtn_version_state_code, wss.scl_rtn_category_code,
               NULL, wss.scale_site_id_nmbr, wss.scale_date, wss.timber_mark,
               wss.license_number, NULL,
               wss.population_number, wss.stratum_number, wss.sampling_year, wss.field_scale_deck_id,
               wss.active_version_ind, wss.visibility_ind,
               NVL(wss.client_number_paid_by, CASE WHEN wss.timber_mark IN %1$s THEN ss.owner_cli_number
                   ELSE mbc.client_number END),
               NVL(wss.client_number_copied_to, CASE WHEN wss.timber_mark NOT IN %1$s THEN s.client_number END),
               mbc.client_number, ss.owner_cli_number, s.client_number,
               ss.org_unit_no, ou.rollup_region_no, mbc.forest_district, mbc.forest_region,
               %2$s%3$s
          FROM weight_scale_summary wss
          JOIN hbs_scale_return hsr ON hsr.document_control_number = wss.document_control_number
          LEFT JOIN scale_site ss ON ss.scale_site_id_nmbr = wss.scale_site_id_nmbr
          LEFT JOIN org_unit ou ON ou.org_unit_no = ss.org_unit_no
          LEFT JOIN mark_billed_cli mbc ON mbc.timber_mark = wss.timber_mark
          LEFT JOIN stratum s ON s.population_number = wss.population_number
                AND s.stratum_number = wss.stratum_number AND s.sampling_year = wss.sampling_year"""
        .formatted(BCOMB, limit("weight_scale_summary", "wss"), detail ? """
            ,
                   NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, wss.pending_txn_comment,
                   wss.client_number_paid_by, wss.client_locn_code_paid_by,
                   wss.client_number_copied_to, wss.client_locn_code_copied_to,
                   wss.input_timber_mark, NULL,
                   (SELECT SUM(d.number_of_loads) FROM weight_scl_daily_load_summary d
                     WHERE d.document_control_number = wss.document_control_number AND d.version = wss.version),
                   (SELECT SUM(d.weight) FROM weight_scl_daily_load_summary d
                     WHERE d.document_control_number = wss.document_control_number AND d.version = wss.version),
                   NULL, NULL, NULL, NULL, NULL,
                   wss.update_userid, wss.update_timestamp""" : "");

    String sample = """
        SELECT 'S', sss.document_control_number, sss.version,
               hsr.scale_return_state_code, hsr.generated_summary_ind,
               sss.scl_rtn_version_state_code, sss.scl_rtn_category_code,
               NULL, sss.scale_site_id_nmbr, sss.scale_date, sss.timber_mark,
               sss.license_number, sss.return_number,
               sss.population_number, sss.stratum_number, sss.sampling_year, NULL,
               sss.active_version_ind, sss.visibility_ind,
               NULL, NULL,
               mbc.client_number, ss.owner_cli_number, s.client_number,
               ss.org_unit_no, ou.rollup_region_no, mbc.forest_district, mbc.forest_region,
               %1$s%2$s
          FROM sample_scale_summary sss
          JOIN hbs_scale_return hsr ON hsr.document_control_number = sss.document_control_number
          LEFT JOIN scale_site ss ON ss.scale_site_id_nmbr = sss.scale_site_id_nmbr
          LEFT JOIN org_unit ou ON ou.org_unit_no = ss.org_unit_no
          LEFT JOIN mark_billed_cli mbc ON mbc.timber_mark = sss.timber_mark
          LEFT JOIN stratum s ON s.population_number = sss.population_number
                AND s.stratum_number = sss.stratum_number AND s.sampling_year = sss.sampling_year"""
        .formatted(limit("sample_scale_summary", "sss"), detail ? """
            ,
                   sss.log_count, NULL, NULL, NULL, NULL, NULL, NULL, NULL, sss.pending_txn_comment,
                   NULL, NULL, NULL, NULL,
                   sss.input_timber_mark, sss.input_scaler_license_num,
                   NULL, NULL,
                   sss.net_weight, sss.load_arrival_number, sss.weigh_slip_number,
                   (SELECT SUM(sg.volume) FROM sample_segregation sg
                     WHERE sg.document_control_number = sss.document_control_number AND sg.version = sss.version),
                   sss.check_scale_ind,
                   sss.update_userid, sss.update_timestamp""" : "");

    return piece + "\n        UNION ALL\n" + weight + "\n        UNION ALL\n" + sample;
  }

  /** Union + code descriptions; the alias {@code x} every filter fragment uses. */
  static String versions(boolean detail) {
    return """
        SELECT x.* FROM (
          SELECT u.*, srcc.description AS category_desc, vs.description AS status_desc,
                 rs.description AS return_state_desc, pst.description AS pssmry_type_desc,
                 CASE u.return_type WHEN 'P' THEN 'Piece' WHEN 'W' THEN 'Weight' WHEN 'S' THEN 'Sample' END
                   AS return_type_desc,
                 CASE WHEN u.population_number IS NOT NULL
                      THEN u.population_number || '/' || u.stratum_number || '/' || u.sampling_year END
                   AS pop_stratum_year
            FROM (
        %s
            ) u
            LEFT JOIN scl_rtn_category_code srcc ON srcc.scl_rtn_category_code = u.category
            LEFT JOIN scl_rtn_version_state_code vs ON vs.scl_rtn_version_state_code = u.status
            LEFT JOIN scale_return_state_code rs ON rs.scale_return_state_code = u.return_state_code
            LEFT JOIN piece_scl_smry_type_code pst ON pst.piece_scl_smry_type_code = u.pssmry_type
        ) x
        """.formatted(union(detail));
  }

  /** Industry fence: active client is Bill To, Copy To, mark holder, site owner or stratum owner. */
  private static final String OWN_FENCE =
      "AND :scopeClientNumber IN (x.bill_to_client, x.copy_to_client, x.mark_holder_client,"
          + " x.site_owner_client, x.stratum_owner_client)";

  /** Same fence for child tables keyed by document_control_number. */
  private static String childFence() {
    return "AND x.document_control_number IN (SELECT f.document_control_number FROM (\n" + union(false)
        + "\n) f WHERE :scopeClientNumber IN (f.bill_to_client, f.copy_to_client, f.mark_holder_client,"
        + " f.site_owner_client, f.stratum_owner_client))";
  }

  // ── Filters shared by the search screens ─────────────────────────────────────

  private static final String VERSION_ERROR_EXISTS = """
      SELECT 1 FROM piece_scl_version_error e WHERE x.return_type = 'P'
         AND e.document_control_number = x.document_control_number AND e.version = x.version%1$s
      UNION ALL
      SELECT 1 FROM weight_scl_version_error e WHERE x.return_type = 'W'
         AND e.document_control_number = x.document_control_number AND e.version = x.version%1$s
      UNION ALL
      SELECT 1 FROM sample_scl_version_error e WHERE x.return_type = 'S'
         AND e.document_control_number = x.document_control_number AND e.version = x.version%1$s""";

  private static String invoiceExists(String param) {
    return """
        AND EXISTS (
          SELECT 1 FROM piece_scl_smry_txn t WHERE x.return_type = 'P'
             AND t.document_control_number = x.document_control_number AND t.version = x.version
             AND t.invoice_number = :%1$s
          UNION ALL
          SELECT 1 FROM weight_scl_smry_txn t WHERE x.return_type = 'W'
             AND t.document_control_number = x.document_control_number AND t.version = x.version
             AND t.invoice_number = :%1$s
          UNION ALL
          SELECT 1 FROM sample_scl_smry_txn t WHERE x.return_type = 'S'
             AND t.document_control_number = x.document_control_number AND t.version = x.version
             AND t.statement_number = :%1$s)""".formatted(param);
  }

  /** Legacy "Issue Date" interval: FOREST_INVOICE.invoice_date (P/W), compiled statement issue date (S). */
  private static String issueDate(String param, String op) {
    return """
        AND EXISTS (
          SELECT 1 FROM piece_scl_smry_txn t JOIN forest_invoice fi
                 ON fi.invoice_number = t.invoice_number AND fi.cancellation_ind = t.cancellation_ind
           WHERE x.return_type = 'P' AND t.document_control_number = x.document_control_number
             AND t.version = x.version AND fi.invoice_date %2$s :%1$s
          UNION ALL
          SELECT 1 FROM weight_scl_smry_txn t JOIN forest_invoice fi
                 ON fi.invoice_number = t.invoice_number AND fi.cancellation_ind = t.cancellation_ind
           WHERE x.return_type = 'W' AND t.document_control_number = x.document_control_number
             AND t.version = x.version AND fi.invoice_date %2$s :%1$s
          UNION ALL
          SELECT 1 FROM sample_scl_smry_txn t JOIN hbs_compiled_sample_stmnt c ON c.trx_id = t.trx_id
           WHERE x.return_type = 'S' AND t.document_control_number = x.document_control_number
             AND t.version = x.version AND c.issue_date %2$s :%1$s)""".formatted(param, op);
  }

  private static QueryDefinition.Builder searchFilters(QueryDefinition.Builder b) {
    return b
        .filter(QueryFilter.of("documentControlNumber", "AND x.document_control_number = :documentControlNumber", Type.NUMBER))
        .filter(QueryFilter.upper("status", "AND x.status = :status"))
        .filter(QueryFilter.upper("generated", "AND x.generated = :generated"))
        .filter(QueryFilter.upper("category", "AND x.category = :category"))
        .filter(QueryFilter.upper("pssmryType", "AND x.pssmry_type = :pssmryType"))
        .filter(QueryFilter.upper("errorCode",
            "AND EXISTS (" + VERSION_ERROR_EXISTS.formatted(" AND e.hbs_edit_err_message_code = :errorCode") + ")"))
        // BT10645 "Invert Selection": versions in error, but not with this code.
        .filter(QueryFilter.upper("excludeErrorCode",
            "AND EXISTS (" + VERSION_ERROR_EXISTS.formatted("") + ")\nAND NOT EXISTS ("
                + VERSION_ERROR_EXISTS.formatted(" AND e.hbs_edit_err_message_code = :excludeErrorCode") + ")"))
        .filter(QueryFilter.date("scaleDateFrom", "AND x.scale_date >= :scaleDateFrom"))
        .filter(QueryFilter.dateTo("scaleDateTo", "AND x.scale_date < :scaleDateTo"))
        .filter(QueryFilter.date("issueDateFrom", issueDate("issueDateFrom", ">=")))
        .filter(QueryFilter.dateTo("issueDateTo", issueDate("issueDateTo", "<")))
        .filter(QueryFilter.upper("timberMark", "AND x.timber_mark = :timberMark"))
        .filter(QueryFilter.upper("scaleSite", "AND x.scale_site = :scaleSite"))
        .filter(QueryFilter.upper("scalerLicence", "AND x.scaler_licence = :scalerLicence"))
        .filter(QueryFilter.eq("returnNumber", "AND x.return_number = :returnNumber"))
        .filter(QueryFilter.upper("population", "AND x.population_number = :population"))
        .filter(QueryFilter.upper("stratum", "AND x.stratum_number = :stratum"))
        .filter(QueryFilter.number("samplingYear", "AND x.sampling_year = :samplingYear"))
        .filter(QueryFilter.upper("fieldScaleDeckId", "AND x.field_scale_deck_id = :fieldScaleDeckId"))
        // Region or district (legacy Hbs_Isregion switch): ORG_UNIT.rollup_region_no for regions.
        .filter(QueryFilter.number("districtScaled",
            "AND (x.scale_district_no = :districtScaled OR x.scale_region_no = :districtScaled)"))
        .filter(QueryFilter.number("districtHarvested",
            "AND (x.harvest_district_no = :districtHarvested OR x.harvest_region_no = :districtHarvested)"))
        .filter(QueryFilter.eq("markHolderClient", "AND x.mark_holder_client = :markHolderClient"))
        .filter(QueryFilter.eq("siteOwnerClient", "AND x.site_owner_client = :siteOwnerClient"))
        .filter(QueryFilter.eq("stratumOwnerClient", "AND x.stratum_owner_client = :stratumOwnerClient"))
        .filter(QueryFilter.eq("billToClient", "AND x.bill_to_client = :billToClient"))
        .filter(QueryFilter.eq("copyToClient", "AND x.copy_to_client = :copyToClient"))
        .filter(QueryFilter.upper("invoiceNumber", invoiceExists("invoiceNumber")))
        .filter(QueryFilter.number("overrideRatingRuleId", """
            AND EXISTS (
              SELECT 1 FROM piece_scl_smry_txn t WHERE x.return_type = 'P'
                 AND t.document_control_number = x.document_control_number AND t.version = x.version
                 AND t.override_rating_rule_id = :overrideRatingRuleId
              UNION ALL
              SELECT 1 FROM weight_scl_smry_txn t WHERE x.return_type = 'W'
                 AND t.document_control_number = x.document_control_number AND t.version = x.version
                 AND t.override_rating_rule_id = :overrideRatingRuleId)"""))
        .filter(QueryFilter.upper("activeOnly", "AND x.active_version_ind = :activeOnly"))
        .sort("documentControlNumber", "x.document_control_number")
        .sort("version", "x.version")
        .sort("status", "x.status")
        .sort("scaleDate", "x.scale_date")
        .sort("scaleSite", "x.scale_site")
        .sort("timberMark", "x.timber_mark")
        .sort("scalerLicence", "x.scaler_licence")
        .sort("categoryDesc", "x.category_desc")
        .orderBy("x.document_control_number, x.version")
        .clientScope(OWN_FENCE);
  }

  @Override
  public List<QueryDefinition> queries() {
    return List.of(
        // ── Area code lists ─────────────────────────────────────────────────
        CodesCatalog.codeTable("codes.summaryReturns.categories", "SCL_RTN_CATEGORY_CODE"),
        CodesCatalog.codeTable("codes.summaryReturns.pieceSummaryTypes", "PIECE_SCL_SMRY_TYPE_CODE"),

        // ── P045 → P051/P101/P151 search by criteria (HBS2R051/101/151) ────
        searchFilters(QueryDefinition.builder("summaryReturns.search")
            .legacy("P045/P051/P101/P151")
            .capability(Capability.SCALE_RETURNS_VIEW)
            .sql(versions(false) + " WHERE x.within_limit = 'Y'")
            .filter(QueryFilter.required("returnType", "AND x.return_type = :returnType", Type.UPPER)))
            .build(),

        // ── P042 single return by SDN or statement / invoice number ────────
        // No seven-year exclusion here: the row shows "Available" = N instead of
        // redirecting to P174 (the detail view then reports the record unavailable).
        QueryDefinition.builder("summaryReturns.find")
            .legacy("P042")
            .capability(Capability.SCALE_RETURNS_VIEW)
            .sql(versions(false) + " WHERE 1=1")
            .filter(QueryFilter.of("documentControlNumber", "AND x.document_control_number = :documentControlNumber", Type.NUMBER))
            .filter(QueryFilter.upper("invoiceNumber", invoiceExists("invoiceNumber")))
            .filter(QueryFilter.upper("activeOnly", "AND x.active_version_ind = :activeOnly"))
            .sort("documentControlNumber", "x.document_control_number")
            .sort("version", "x.version")
            .orderBy("x.document_control_number, x.version")
            .clientScope(OWN_FENCE)
            .build(),

        // ── P052 / P102 / P152 view one version (header + fields) ──────────
        QueryDefinition.builder("summaryReturns.detail")
            .legacy("P052/P102/P152/P053/P103/P153")
            .capability(Capability.SCALE_RETURNS_VIEW)
            .sql(versions(true) + " WHERE x.within_limit = 'Y'")
            .filter(QueryFilter.required("documentControlNumber", "AND x.document_control_number = :documentControlNumber", Type.NUMBER))
            .filter(QueryFilter.required("version", "AND x.version = :version", Type.NUMBER))
            .orderBy("x.version")
            .clientScope(OWN_FENCE)
            .maxRows(1)
            .build(),

        // ── Version pager / "Show History" (VersionPager.jsp, actionType=Show History) ──
        QueryDefinition.builder("summaryReturns.versions")
            .legacy("P051 Show History / VersionPager")
            .capability(Capability.SCALE_RETURNS_VIEW)
            .sql(versions(false) + " WHERE x.within_limit = 'Y'")
            .filter(QueryFilter.required("documentControlNumber", "AND x.document_control_number = :documentControlNumber", Type.NUMBER))
            .orderBy("x.version DESC")
            .clientScope(OWN_FENCE)
            .maxRows(500)
            .build(),

        // ── Version edit errors (hbs2:hbsEditErrors errorType="Ver") ──────
        QueryDefinition.builder("summaryReturns.versionErrors")
            .legacy("P052/P102/P152 HBSEditErrorsTag")
            .capability(Capability.SCALE_RETURNS_VIEW)
            .sql("""
                SELECT x.* FROM (
                  SELECT e.document_control_number, e.version, e.hbs_edit_err_message_code AS error_code,
                         m.description AS error_desc, m.hbs_edit_err_responsiblty_code AS responsibility,
                         e.error_timestamp
                    FROM (SELECT document_control_number, version, hbs_edit_err_message_code, error_timestamp
                            FROM piece_scl_version_error
                          UNION ALL
                          SELECT document_control_number, version, hbs_edit_err_message_code, error_timestamp
                            FROM weight_scl_version_error
                          UNION ALL
                          SELECT document_control_number, version, hbs_edit_err_message_code, error_timestamp
                            FROM sample_scl_version_error) e
                    LEFT JOIN hbs_edit_err_message_code m ON m.hbs_edit_err_message_code = e.hbs_edit_err_message_code
                ) x WHERE 1=1""")
            .filter(QueryFilter.required("documentControlNumber", "AND x.document_control_number = :documentControlNumber", Type.NUMBER))
            .filter(QueryFilter.required("version", "AND x.version = :version", Type.NUMBER))
            .orderBy("x.error_code")
            .clientScope(childFence())
            .maxRows(500)
            .build(),

        // ── Transactions table (transactionsList.jsp / SummaryScaleTransactionsTag) ──
        QueryDefinition.builder("summaryReturns.transactions")
            .legacy("P052/P102/P152 transactions")
            .capability(Capability.SCALE_RETURNS_VIEW)
            .sql("""
                SELECT x.* FROM (
                  SELECT t.trx_id, t.document_control_number, t.version, t.event_sequence AS txn_seq,
                         t.debit_credit_type AS dr_cr, t.hbs_txn_type_code AS txn_type, tt.description AS txn_type_desc,
                         t.hbs_txn_status_code AS txn_status, t.override_rating_rule_id,
                         orr.ovrrdrtrl_type AS override_rule_type, orr.override_rate_date AS rate_date,
                         ob.sent_date_time AS date_sent, t.invoice_number, fi.invoice_date AS issue_date,
                         CAST(NULL AS VARCHAR2(7)) AS ratio_statement_number,
                         (SELECT SUM(s.volume) FROM piece_scl_txn_segregation s WHERE s.trx_id = t.trx_id) AS volume,
                         (SELECT SUM(s.value) FROM piece_scl_txn_segregation s WHERE s.trx_id = t.trx_id) AS value,
                         CAST(NULL AS NUMBER) AS weight
                    FROM piece_scl_smry_txn t
                    LEFT JOIN hbs_txn_type_code tt ON tt.hbs_txn_type_code = t.hbs_txn_type_code
                    LEFT JOIN override_rating_rule orr ON orr.override_rating_rule_id = t.override_rating_rule_id
                    LEFT JOIN hbs_output_batch ob ON ob.batch_id = t.batch_id
                    LEFT JOIN forest_invoice fi ON fi.invoice_number = t.invoice_number AND fi.cancellation_ind = 'N'
                  UNION ALL
                  SELECT t.trx_id, t.document_control_number, t.version, t.event_sequence,
                         t.debit_credit_type, t.hbs_txn_type_code, tt.description,
                         t.hbs_txn_status_code, t.override_rating_rule_id,
                         orr.ovrrdrtrl_type, orr.override_rate_date,
                         ob.sent_date_time, t.invoice_number, fi.invoice_date,
                         t.ratio_statement_number,
                         (SELECT SUM(s.volume) FROM weight_scl_txn_segregation s WHERE s.trx_id = t.trx_id),
                         (SELECT SUM(s.value) FROM weight_scl_txn_segregation s WHERE s.trx_id = t.trx_id),
                         NULL
                    FROM weight_scl_smry_txn t
                    LEFT JOIN hbs_txn_type_code tt ON tt.hbs_txn_type_code = t.hbs_txn_type_code
                    LEFT JOIN override_rating_rule orr ON orr.override_rating_rule_id = t.override_rating_rule_id
                    LEFT JOIN hbs_output_batch ob ON ob.batch_id = t.batch_id
                    LEFT JOIN forest_invoice fi ON fi.invoice_number = t.invoice_number AND fi.cancellation_ind = 'N'
                  UNION ALL
                  SELECT t.trx_id, t.document_control_number, t.version, t.event_sequence,
                         t.debit_credit_type, t.hbs_txn_type_code, tt.description,
                         t.hbs_txn_status_code, NULL, NULL, NULL,
                         ob.sent_date_time,
                         CASE WHEN t.hbs_txn_status_code IN ('CRT', 'FAL') THEN NULL ELSE t.statement_number END,
                         CASE WHEN t.hbs_txn_status_code IN ('CRT', 'FAL') THEN NULL ELSE c.issue_date END,
                         t.ratio_statement_number,
                         (SELECT SUM(sg.volume) FROM sample_segregation sg
                           WHERE sg.document_control_number = t.document_control_number AND sg.version = t.version),
                         NULL,
                         sss.net_weight
                    FROM sample_scl_smry_txn t
                    JOIN sample_scale_summary sss ON sss.document_control_number = t.document_control_number
                         AND sss.version = t.version
                    LEFT JOIN hbs_txn_type_code tt ON tt.hbs_txn_type_code = t.hbs_txn_type_code
                    LEFT JOIN hbs_output_batch ob ON ob.batch_id = t.batch_id
                    LEFT JOIN hbs_compiled_sample_stmnt c ON c.trx_id = t.trx_id
                ) x WHERE 1=1""")
            .filter(QueryFilter.required("documentControlNumber", "AND x.document_control_number = :documentControlNumber", Type.NUMBER))
            .filter(QueryFilter.required("version", "AND x.version = :version", Type.NUMBER))
            .orderBy("x.txn_seq DESC")
            .clientScope(childFence())
            .maxRows(500)
            .build(),

        // ── Segregations (P055 piece, P105 weight, P155 sample) ─────────────
        QueryDefinition.builder("summaryReturns.segregations")
            .legacy("P055/P105/P155")
            .capability(Capability.SCALE_RETURNS_VIEW)
            .sql("""
                SELECT x.* FROM (
                  SELECT seg.document_control_number, seg.version, t.event_sequence AS txn_seq,
                         t.hbs_txn_type_code AS txn_type, t.debit_credit_type AS dr_cr,
                         seg.input_bcomb_timber_mark AS input_mark, seg.timber_mark AS billed_mark,
                         NVL(seg.scale_species_code, seg.input_species) AS species,
                         NVL(seg.scale_product_code, seg.input_product) AS product,
                         NVL(seg.scale_grade_code, seg.input_grade) AS grade,
                         seg.nmv_ind, seg.sb4_ind, seg.avoidable_ind, seg.number_of_pieces AS pieces,
                         CAST(NULL AS NUMBER) AS ratio, tsg.rate, tsg.rate_source,
                         NVL(tsg.volume, seg.volume) AS volume, tsg.value
                    FROM piece_scl_segregation seg
                    LEFT JOIN piece_scl_txn_segregation tsg ON tsg.segregation_id = seg.segregation_id
                    LEFT JOIN piece_scl_smry_txn t ON t.trx_id = tsg.trx_id
                  UNION ALL
                  SELECT tsg.document_control_number, tsg.version, t.event_sequence,
                         t.hbs_txn_type_code, t.debit_credit_type,
                         NULL, NULL, tsg.scale_species_code, tsg.scale_product_code, tsg.scale_grade_code,
                         NULL, NULL, NULL, NULL,
                         tsg.ratio, tsg.rate, tsg.rate_source, tsg.volume, tsg.value
                    FROM weight_scl_txn_segregation tsg
                    LEFT JOIN weight_scl_smry_txn t ON t.trx_id = tsg.trx_id
                  UNION ALL
                  SELECT seg.document_control_number, seg.version, t.event_sequence,
                         t.hbs_txn_type_code, t.debit_credit_type,
                         NULL, NULL,
                         NVL(seg.scale_species_code, seg.input_species),
                         NVL(seg.scale_product_code, seg.input_product),
                         NVL(seg.scale_grade_code, seg.input_grade),
                         NULL, NULL, NULL, seg.number_of_pieces,
                         tsg.ratio, NULL, NULL, seg.volume, NULL
                    FROM sample_segregation seg
                    LEFT JOIN sample_scl_txn_segregation tsg ON tsg.segregation_id = seg.segregation_id
                    LEFT JOIN sample_scl_smry_txn t ON t.trx_id = tsg.trx_id
                ) x WHERE 1=1""")
            .filter(QueryFilter.required("documentControlNumber", "AND x.document_control_number = :documentControlNumber", Type.NUMBER))
            .filter(QueryFilter.required("version", "AND x.version = :version", Type.NUMBER))
            .orderBy("x.txn_seq, x.species, x.product, x.grade")
            .clientScope(childFence())
            .maxRows(2000)
            .build(),

        // ── Notations (P056 / P106 / P156) ──────────────────────────────────
        QueryDefinition.builder("summaryReturns.notations")
            .legacy("P056/P106/P156")
            .capability(Capability.SCALE_RETURNS_VIEW)
            .sql("""
                SELECT x.* FROM (
                  SELECT document_control_number, version, notation_number, notation_type_code, text
                    FROM piece_scl_notation
                  UNION ALL
                  SELECT document_control_number, version, notation_number, notation_type_code, text
                    FROM weight_scale_notation
                  UNION ALL
                  SELECT document_control_number, version, notation_number, notation_type_code, text
                    FROM sample_scl_notation
                ) x WHERE 1=1""")
            .filter(QueryFilter.required("documentControlNumber", "AND x.document_control_number = :documentControlNumber", Type.NUMBER))
            .filter(QueryFilter.required("version", "AND x.version = :version", Type.NUMBER))
            .orderBy("x.notation_number")
            .clientScope(childFence())
            .maxRows(500)
            .build(),

        // ── Weight daily load summaries (P107, DailyLoadSummariesTag) ───────
        QueryDefinition.builder("summaryReturns.dailyLoads")
            .legacy("P107")
            .capability(Capability.SCALE_RETURNS_VIEW)
            .sql("""
                SELECT x.* FROM (
                  SELECT document_control_number, version, day, number_of_loads AS loads, weight
                    FROM weight_scl_daily_load_summary
                ) x WHERE 1=1""")
            .filter(QueryFilter.required("documentControlNumber", "AND x.document_control_number = :documentControlNumber", Type.NUMBER))
            .filter(QueryFilter.required("version", "AND x.version = :version", Type.NUMBER))
            .orderBy("x.day")
            .clientScope(childFence())
            .maxRows(31)
            .build(),

        // ── "Show Details" on generated summaries (DetailsLinkTag / detailTxnList.jsp) ──
        QueryDefinition.builder("summaryReturns.detailTransactions")
            .legacy("P052/P102/P152 Show Details")
            .capability(Capability.SCALE_RETURNS_VIEW)
            .sql("""
                SELECT x.* FROM (
                  SELECT 'Log Tally' AS detail_doc_type, d.detail_document_number, d.detail_document_version,
                         d.document_control_number, d.summary_document_version AS version,
                         d.event_sequence, d.debit_credit_type AS dr_cr, d.hbs_txn_type_code AS txn_type,
                         d.hbs_txn_status_code AS txn_status
                    FROM log_tally_txn d
                  UNION ALL
                  SELECT 'Weigh Slip', d.detail_document_number, d.detail_document_version,
                         d.document_control_number, d.summary_document_version,
                         d.event_sequence, d.debit_credit_type, d.hbs_txn_type_code, d.hbs_txn_status_code
                    FROM weigh_slip_txn d
                  UNION ALL
                  SELECT 'Sample Tally', d.detail_document_number, d.detail_document_version,
                         d.document_control_number, d.summary_document_version,
                         d.event_sequence, d.debit_credit_type, d.hbs_txn_type_code, d.hbs_txn_status_code
                    FROM sample_tally_txn d
                  UNION ALL
                  SELECT 'SFP Tally', d.detail_document_number, d.detail_document_version,
                         d.document_control_number, d.summary_document_version,
                         d.event_sequence, d.debit_credit_type, d.hbs_txn_type_code, d.hbs_txn_status_code
                    FROM sfp_tally_txn d
                ) x WHERE 1=1""")
            .filter(QueryFilter.required("documentControlNumber", "AND x.document_control_number = :documentControlNumber", Type.NUMBER))
            .filter(QueryFilter.required("version", "AND x.version = :version", Type.NUMBER))
            .orderBy("x.detail_document_number, x.detail_document_version")
            .clientScope(childFence())
            .maxRows(5000)
            .build()
    );
  }

  /**
   * Intentionally empty: no summary-return write is a single table-API proc call
   * (see class comment). The reserved ids used by the screens
   * ({@code summaryReturns.addPiece}, {@code .hold}, {@code .release}, …) are to be
   * served by a dedicated SummaryReturnWorkflowService.
   */
  @Override
  public List<CommandDefinition> commands() {
    return List.of();
  }
}
