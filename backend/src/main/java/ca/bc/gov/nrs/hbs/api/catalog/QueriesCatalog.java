package ca.bc.gov.nrs.hbs.api.catalog;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.query.QueryCatalog;
import ca.bc.gov.nrs.hbs.api.query.QueryDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter.Type;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Queries tab (legacy P400) — issued documents, statement transmissions and the
 * billing summary list. Harvest history, timber marks and the special reports
 * live in {@link QueriesReportsCatalog}.
 *
 * <p>Ported from:
 * <ul>
 *   <li>{@code issuedDocuments.search} — procs {@code HBS3R450_PS/_WS/_CS/_RS}
 *       (nr-mof-db THE/PROCEDURES/V7.0157[1-4]__HBS3R450_*.sql), called by
 *       {@code TransactionStatementManagerBean.getSearchIssuedDocumentStatement}.</li>
 *   <li>{@code statementTransmissions.search} — proc {@code HBS3R452}
 *       (V7.01576__HBS3R452.sql), called by
 *       {@code DocumentDeliveryTransmissionManagerBean.findByTransmissionSearchCriteria}.</li>
 *   <li>{@code billingSummary.search} — procs {@code HBS3R421}/{@code HBS3R422} +
 *       {@code HBS_BILLINGSMRYRPT_WHERECLAUSE}.</li>
 * </ul>
 * The dynamic-SQL procs concatenated every prompt; here each criterion is a
 * bound parameter.
 */
@Component
public class QueriesCatalog implements QueryCatalog {

  /** Legacy 7-year rule (LimitYear.SEVEN) and the 2003-11-01 HBS statement cut-over (P449). */
  static final String RETRIEVABLE =
      "d.hbs_file_id IS NOT NULL AND d.issue_date >= GREATEST(ADD_MONTHS(TRUNC(SYSDATE), -84), DATE '2003-11-01')";

  private static final String ISSUED_DOCS = """
      SELECT d.statement_number, d.document_type, d.issue_date, d.txn_type, d.hbs_txn_type_code,
             d.scale_site, d.scale_date, d.population_number, d.stratum_number, d.sampling_year,
             CASE WHEN d.population_number IS NOT NULL
                  THEN d.population_number || '-' || d.stratum_number || '-' || d.sampling_year END AS pop_str_yr,
             d.timber_mark,
             d.client_number_send_to, d.client_locn_code_send_to,
             d.client_number_copy_to, d.client_locn_code_copy_to,
             TRIM(d.client_number_send_to || ' ' || d.client_locn_code_send_to) AS statement_to,
             TRIM(d.client_number_copy_to || ' ' || d.client_locn_code_copy_to) AS copy_to,
             d.total_volume, d.total_value, d.net_weight,
             d.document_control_number, d.version, d.return_type,
             CASE WHEN %1$s
                  THEN '/api/v1/hbs/statements/' || d.document_type || '/' || d.statement_number || '.pdf' END AS document_url,
             CASE WHEN %1$s
                  THEN '/api/v1/hbs/statements/' || d.document_type || '/' || d.statement_number || '.xml' END AS document_xml_url
        FROM (
          SELECT hps.statement_number, hps.psstmt_type AS document_type, hps.issue_date,
                 httc.description AS txn_type, psst.hbs_txn_type_code,
                 pss.scale_site_id_nmbr AS scale_site, pss.scale_date,
                 CAST(NULL AS VARCHAR2(4)) AS population_number, CAST(NULL AS VARCHAR2(2)) AS stratum_number,
                 CAST(NULL AS NUMBER) AS sampling_year, pss.timber_mark,
                 hps.client_number_send_to, hps.client_locn_code_send_to,
                 hps.client_number_copy_to, hps.client_locn_code_copy_to,
                 hps.total_volume, hps.total_value, CAST(NULL AS NUMBER) AS net_weight, hps.hbs_file_id,
                 pss.document_control_number, pss.version, 'P' AS return_type,
                 ss.org_unit_no AS scaled_org_unit_no, sou.rollup_region_no AS scaled_region_no,
                 tm.forest_district AS harvested_org_unit_no, hou.rollup_region_no AS harvested_region_no
            FROM hbs_ps_statement hps
            JOIN piece_scl_smry_txn psst ON psst.trx_id = hps.trx_id
            JOIN piece_scale_summary pss ON pss.document_control_number = psst.document_control_number
                                        AND pss.version = psst.version
            JOIN hbs_txn_type_code httc ON httc.hbs_txn_type_code = psst.hbs_txn_type_code
            JOIN timber_mark tm ON tm.timber_mark = pss.timber_mark
            JOIN scale_site ss ON ss.scale_site_id_nmbr = pss.scale_site_id_nmbr
            LEFT JOIN org_unit sou ON sou.org_unit_no = ss.org_unit_no
            LEFT JOIN org_unit hou ON hou.org_unit_no = tm.forest_district
          UNION ALL
          SELECT hws.statement_number, hws.wsstmt_type, hws.issue_date,
                 httc.description, wsst.hbs_txn_type_code,
                 wss.scale_site_id_nmbr, wss.scale_date,
                 wss.population_number, wss.stratum_number, wss.sampling_year, wss.timber_mark,
                 hws.client_number_send_to, hws.client_locn_code_send_to,
                 hws.client_number_copy_to, hws.client_locn_code_copy_to,
                 hws.total_volume, hws.total_value, CAST(NULL AS NUMBER), hws.hbs_file_id,
                 wss.document_control_number, wss.version, 'W',
                 ss.org_unit_no, sou.rollup_region_no, tm.forest_district, hou.rollup_region_no
            FROM hbs_ws_statement hws
            JOIN weight_scl_smry_txn wsst ON wsst.trx_id = hws.trx_id
            JOIN weight_scale_summary wss ON wss.document_control_number = wsst.document_control_number
                                         AND wss.version = wsst.version
            JOIN hbs_txn_type_code httc ON httc.hbs_txn_type_code = wsst.hbs_txn_type_code
            JOIN timber_mark tm ON tm.timber_mark = wss.timber_mark
            JOIN scale_site ss ON ss.scale_site_id_nmbr = wss.scale_site_id_nmbr
            LEFT JOIN org_unit sou ON sou.org_unit_no = ss.org_unit_no
            LEFT JOIN org_unit hou ON hou.org_unit_no = tm.forest_district
          UNION ALL
          SELECT hcss.statement_number, 'CSS', hcss.issue_date,
                 httc.description, ssst.hbs_txn_type_code,
                 sss.scale_site_id_nmbr, sss.scale_date,
                 sss.population_number, sss.stratum_number, sss.sampling_year, sss.timber_mark,
                 hcss.client_number_send_to, hcss.client_locn_code_send_to,
                 hcss.client_number_copy_to, hcss.client_locn_code_copy_to,
                 hcss.total_volume, CAST(NULL AS NUMBER), sss.net_weight, hcss.hbs_file_id,
                 sss.document_control_number, sss.version, 'S',
                 ss.org_unit_no, sou.rollup_region_no, tm.forest_district, hou.rollup_region_no
            FROM hbs_compiled_sample_stmnt hcss
            JOIN sample_scl_smry_txn ssst ON ssst.trx_id = hcss.trx_id
            JOIN sample_scale_summary sss ON sss.document_control_number = ssst.document_control_number
                                         AND sss.version = ssst.version
            JOIN hbs_txn_type_code httc ON httc.hbs_txn_type_code = ssst.hbs_txn_type_code
            JOIN timber_mark tm ON tm.timber_mark = sss.timber_mark
            JOIN scale_site ss ON ss.scale_site_id_nmbr = sss.scale_site_id_nmbr
            LEFT JOIN org_unit sou ON sou.org_unit_no = ss.org_unit_no
            LEFT JOIN org_unit hou ON hou.org_unit_no = tm.forest_district
          UNION ALL
          SELECT DISTINCT hrts.statement_number, 'RS', hrts.issue_date,
                 CAST(NULL AS VARCHAR2(120)), CAST(NULL AS VARCHAR2(3)),
                 CAST(NULL AS VARCHAR2(4)), CAST(NULL AS DATE),
                 rths.population_number, rths.stratum_number, rths.sampling_year, CAST(NULL AS VARCHAR2(6)),
                 hrts.client_number_send_to, hrts.client_locn_code_send_to,
                 hrts.client_number_copy_to, hrts.client_locn_code_copy_to,
                 CAST(NULL AS NUMBER), CAST(NULL AS NUMBER), CAST(NULL AS NUMBER), hrts.hbs_file_id,
                 CAST(NULL AS NUMBER), CAST(NULL AS NUMBER), CAST(NULL AS VARCHAR2(1)),
                 CAST(NULL AS NUMBER), CAST(NULL AS NUMBER), CAST(NULL AS NUMBER), CAST(NULL AS NUMBER)
            FROM hbs_ratio_statement hrts
            JOIN (SELECT r.statement_number, h.population_number, h.sampling_year, h.stratum_number
                    FROM hbs_ratio_statement r
                    JOIN ratio_history h ON h.statement_number = r.statement_number
                   WHERE r.ratio_type_code = 'S'
                  UNION
                  SELECT r.statement_number, sp.population_number, sp.sampling_year, s.stratum_number
                    FROM hbs_ratio_statement r
                    JOIN stratum_plan s ON s.stratum_plan_id = r.stratum_plan_id
                    JOIN sample_plan sp ON sp.plan_id = s.plan_id) rths
              ON rths.statement_number = hrts.statement_number
        ) d
       WHERE 1=1""".formatted(RETRIEVABLE);

  private static final String BILLING_SUMMARY = """
      SELECT d.scale_type, d.statement_number, d.document_type, d.issue_date, d.scale_site, d.scale_date,
             d.timber_mark, d.scl_rtn_category_code, d.return_category, d.log_count,
             d.population_number, d.stratum_number, d.sampling_year,
             TRIM(d.client_number_send_to || ' ' || d.client_locn_code_send_to) AS statement_to,
             TRIM(d.client_number_copy_to || ' ' || d.client_locn_code_copy_to) AS copy_to,
             d.client_number_send_to, d.client_number_copy_to,
             d.document_control_number, d.district_harvested, d.district_scaled,
             d.forest_file_id, d.file_type_code, d.mgmt_unit_type, d.mgmt_unit_id,
             d.total_volume, d.total_value
        FROM (
          SELECT 'P' AS scale_type, hps.statement_number, hps.psstmt_type AS document_type, hps.issue_date,
                 pss.scale_site_id_nmbr AS scale_site, pss.scale_date, pss.timber_mark,
                 pss.scl_rtn_category_code, srcc.description AS return_category,
                 DECODE(psst.hbs_txn_type_code, 'CAN', -pss.log_count, pss.log_count) AS log_count,
                 CAST(NULL AS VARCHAR2(4)) AS population_number, CAST(NULL AS VARCHAR2(2)) AS stratum_number,
                 CAST(NULL AS NUMBER) AS sampling_year,
                 hps.client_number_send_to, hps.client_locn_code_send_to,
                 hps.client_number_copy_to, hps.client_locn_code_copy_to,
                 psst.document_control_number,
                 dh.org_unit_code AS district_harvested, ds.org_unit_code AS district_scaled,
                 dh.org_unit_no AS harvested_org_unit_no, dh.rollup_region_no AS harvested_region_no,
                 ds.org_unit_no AS scaled_org_unit_no, ds.rollup_region_no AS scaled_region_no,
                 mbc.forest_file_id, mbc.file_type_code, mbc.mgmt_unit_type, mbc.mgmt_unit_id,
                 hps.total_volume, hps.total_value
            FROM hbs_ps_statement hps
            JOIN piece_scl_smry_txn psst ON psst.trx_id = hps.trx_id
            JOIN piece_scale_summary pss ON pss.document_control_number = psst.document_control_number
                                        AND pss.version = psst.version
            JOIN scl_rtn_category_code srcc ON srcc.scl_rtn_category_code = pss.scl_rtn_category_code
            JOIN mark_billed_cli mbc ON mbc.timber_mark = pss.timber_mark
            JOIN timber_mark tm ON tm.timber_mark = pss.timber_mark
            JOIN org_unit dh ON dh.org_unit_no = tm.forest_district
            JOIN scale_site ss ON ss.scale_site_id_nmbr = pss.scale_site_id_nmbr
            LEFT JOIN org_unit ds ON ds.org_unit_no = ss.org_unit_no
           WHERE hps.psstmt_type IN ('PSI', 'PSV')
          UNION ALL
          SELECT 'W', hws.statement_number, hws.wsstmt_type, hws.issue_date,
                 wss.scale_site_id_nmbr, wss.scale_date, wss.timber_mark,
                 wss.scl_rtn_category_code, srcc.description, CAST(NULL AS NUMBER),
                 wss.population_number, wss.stratum_number, wss.sampling_year,
                 hws.client_number_send_to, hws.client_locn_code_send_to,
                 hws.client_number_copy_to, hws.client_locn_code_copy_to,
                 wsst.document_control_number,
                 dh.org_unit_code, ds.org_unit_code,
                 dh.org_unit_no, dh.rollup_region_no, ds.org_unit_no, ds.rollup_region_no,
                 mbc.forest_file_id, mbc.file_type_code, mbc.mgmt_unit_type, mbc.mgmt_unit_id,
                 hws.total_volume, hws.total_value
            FROM hbs_ws_statement hws
            JOIN weight_scl_smry_txn wsst ON wsst.trx_id = hws.trx_id
            JOIN weight_scale_summary wss ON wss.document_control_number = wsst.document_control_number
                                         AND wss.version = wsst.version
            JOIN scl_rtn_category_code srcc ON srcc.scl_rtn_category_code = wss.scl_rtn_category_code
            JOIN mark_billed_cli mbc ON mbc.timber_mark = wss.timber_mark
            JOIN timber_mark tm ON tm.timber_mark = wss.timber_mark
            JOIN org_unit dh ON dh.org_unit_no = tm.forest_district
            JOIN scale_site ss ON ss.scale_site_id_nmbr = wss.scale_site_id_nmbr
            LEFT JOIN org_unit ds ON ds.org_unit_no = ss.org_unit_no
           WHERE hws.wsstmt_type IN ('WSI', 'WSV')
        ) d
       WHERE 1=1""";

  private static final String STATEMENT_PARTY =
      "AND (d.client_number_send_to = :scopeClientNumber OR d.client_number_copy_to = :scopeClientNumber)";

  @Override
  public List<QueryDefinition> queries() {
    return List.of(
        // ── P449 / P450 → P451/P461/P456/P466/P471/P476 ─────────────────────
        QueryDefinition.builder("issuedDocuments.search")
            .legacy("P449/P450/P451/P461/P456/P466/P471/P476")
            .capability(Capability.QUERIES_VIEW)
            .sql(ISSUED_DOCS)
            .filter(QueryFilter.upper("documentType", "AND d.document_type = :documentType"))
            .filter(QueryFilter.upper("statementNumber", "AND d.statement_number = :statementNumber"))
            .filter(QueryFilter.date("scaleDateFrom", "AND d.scale_date >= :scaleDateFrom"))
            .filter(QueryFilter.dateTo("scaleDateTo", "AND d.scale_date < :scaleDateTo"))
            .filter(QueryFilter.date("issueDateFrom", "AND d.issue_date >= :issueDateFrom"))
            .filter(QueryFilter.dateTo("issueDateTo", "AND d.issue_date < :issueDateTo"))
            .filter(QueryFilter.upper("timberMark", "AND d.timber_mark = :timberMark"))
            .filter(QueryFilter.upper("scaleSite", "AND d.scale_site = :scaleSite"))
            .filter(QueryFilter.upper("population", "AND d.population_number = :population"))
            .filter(QueryFilter.upper("stratum", "AND d.stratum_number = :stratum"))
            .filter(QueryFilter.number("samplingYear", "AND d.sampling_year = :samplingYear"))
            .filter(QueryFilter.number("regionScaled",
                "AND (d.scaled_org_unit_no = :regionScaled OR d.scaled_region_no = :regionScaled)"))
            .filter(QueryFilter.number("regionHarvested",
                "AND (d.harvested_org_unit_no = :regionHarvested OR d.harvested_region_no = :regionHarvested)"))
            // Client Association: Both / Original / Copy To (HBS_CONSTANTS STATEMENT_TO_BOTH etc.)
            .filter(QueryFilter.eq("clientNumber",
                "AND (d.client_number_send_to = :clientNumber OR d.client_number_copy_to = :clientNumber)"))
            .filter(QueryFilter.eq("sendToClientNumber", "AND d.client_number_send_to = :sendToClientNumber"))
            .filter(QueryFilter.eq("copyToClientNumber", "AND d.client_number_copy_to = :copyToClientNumber"))
            .filter(QueryFilter.eq("clientLocation",
                "AND (d.client_locn_code_send_to = :clientLocation OR d.client_locn_code_copy_to = :clientLocation)"))
            .filter(QueryFilter.number("transmissionId",
                "AND d.hbs_file_id IN (SELECT ddbi.hbs_file_id FROM document_delivery_batch_item ddbi"
                    + " WHERE ddbi.transmission_id = :transmissionId)"))
            .sort("statementNumber", "d.statement_number")
            .sort("issueDate", "d.issue_date")
            .sort("scaleDate", "d.scale_date")
            .sort("timberMark", "d.timber_mark")
            .sort("scaleSite", "d.scale_site")
            .sort("popStrYr", "d.population_number, d.sampling_year, d.stratum_number")
            .orderBy("d.statement_number")
            .clientScope(STATEMENT_PARTY)
            .build(),

        // ── P452 / P453 Transmissions of Issued Statements ──────────────────
        QueryDefinition.builder("statementTransmissions.search")
            .legacy("P452/P453")
            .capability(Capability.QUERIES_VIEW)
            .sql("""
                SELECT ddt.transmission_id, ddt.client_number, ddt.client_locn_code, cl.client_locn_name,
                       ddt.hbs_delivery_method_code, dmc.description AS delivery_method,
                       ddt.file_name, ddt.pdf_file_name, ddt.creation_date, ddt.sent_date,
                       ddt.recipient_type,
                       DECODE(ddt.recipient_type, 'SEND_TO', 'Send To', 'COPY_TO', 'Copy To', ddt.recipient_type)
                         AS recipient_type_description,
                       ddt.ps_invoice_count, ddt.ps_vol_statement_count, ddt.ws_invoice_count,
                       ddt.ws_vol_statement_count, ddt.compiled_sample_stmt_count, ddt.ratio_statement_count
                  FROM document_delivery_transmission ddt
                  JOIN client_location cl ON cl.client_number = ddt.client_number
                                         AND cl.client_locn_code = ddt.client_locn_code
                  LEFT JOIN hbs_delivery_method_code dmc
                         ON dmc.hbs_delivery_method_code = ddt.hbs_delivery_method_code
                 WHERE (ddt.ps_invoice_count <> 0 OR ddt.ps_vol_statement_count <> 0
                        OR ddt.ws_invoice_count <> 0 OR ddt.ws_vol_statement_count <> 0
                        OR ddt.compiled_sample_stmt_count <> 0 OR ddt.ratio_statement_count <> 0)""")
            .filter(QueryFilter.eq("clientNumber", "AND ddt.client_number = :clientNumber"))
            .filter(QueryFilter.eq("clientLocation", "AND ddt.client_locn_code = :clientLocation"))
            .filter(QueryFilter.date("fromDate", "AND ddt.creation_date >= :fromDate"))
            .filter(QueryFilter.dateTo("toDate", "AND ddt.creation_date < :toDate"))
            .filter(QueryFilter.upper("recipientType", "AND ddt.recipient_type = :recipientType"))
            // Home "Recent Transmissions of Issued Statements" (P452 actionType=SearchFromHome):
            // last 30 days (Constants.WORKBENCH_DATE_RANGE) for the user's client.
            .filter(QueryFilter.upper("recent",
                "AND (:recent <> 'Y' OR ddt.creation_date >= TRUNC(SYSDATE) - 30)"))
            .sort("clientLocation", "ddt.client_locn_code")
            .sort("creationDate", "ddt.creation_date")
            .sort("transmissionId", "ddt.transmission_id")
            .orderBy("ddt.client_locn_code, ddt.creation_date DESC")
            .clientScope("AND ddt.client_number = :scopeClientNumber")
            .build(),

        // ── P421 Billing Summary ("List of Invoices and Volume Statements") ─
        QueryDefinition.builder("billingSummary.search")
            .legacy("P421")
            .capability(Capability.QUERIES_VIEW)
            .sql(BILLING_SUMMARY)
            .filter(QueryFilter.upper("scaleType", "AND d.scale_type = :scaleType"))
            .filter(QueryFilter.date("issueDateFrom", "AND d.issue_date >= :issueDateFrom"))
            .filter(QueryFilter.dateTo("issueDateTo", "AND d.issue_date < :issueDateTo"))
            .filter(QueryFilter.date("scaleDateFrom", "AND d.scale_date >= :scaleDateFrom"))
            .filter(QueryFilter.dateTo("scaleDateTo", "AND d.scale_date < :scaleDateTo"))
            .filter(QueryFilter.number("regionHarvested",
                "AND (d.harvested_org_unit_no = :regionHarvested OR d.harvested_region_no = :regionHarvested)"))
            .filter(QueryFilter.of("fileType", "AND d.file_type_code IN (:fileType)", Type.LIST))
            .filter(QueryFilter.of("mgmtUnitType", "AND d.mgmt_unit_type IN (:mgmtUnitType)", Type.LIST))
            .filter(QueryFilter.upper("mgmtUnitNo", "AND d.mgmt_unit_id = :mgmtUnitNo"))
            .filter(QueryFilter.upper("forestFileId", "AND d.forest_file_id = :forestFileId"))
            .filter(QueryFilter.upper("timberMark", "AND d.timber_mark = :timberMark"))
            .filter(QueryFilter.number("regionScaled",
                "AND (d.scaled_org_unit_no = :regionScaled OR d.scaled_region_no = :regionScaled)"))
            .filter(QueryFilter.upper("scaleSite", "AND d.scale_site = :scaleSite"))
            .filter(QueryFilter.upper("population", "AND d.population_number = :population"))
            .filter(QueryFilter.upper("stratum", "AND d.stratum_number = :stratum"))
            .filter(QueryFilter.number("samplingYear", "AND d.sampling_year = :samplingYear"))
            .filter(QueryFilter.upper("returnCategory", "AND d.scl_rtn_category_code = :returnCategory"))
            .filter(QueryFilter.eq("clientNumber",
                "AND (d.client_number_send_to = :clientNumber OR d.client_number_copy_to = :clientNumber)"))
            .filter(QueryFilter.eq("sendToClientNumber", "AND d.client_number_send_to = :sendToClientNumber"))
            .filter(QueryFilter.eq("copyToClientNumber", "AND d.client_number_copy_to = :copyToClientNumber"))
            .sort("timberMark", "d.timber_mark")
            .sort("statementNumber", "d.statement_number")
            .sort("issueDate", "d.issue_date")
            .sort("scaleDate", "d.scale_date")
            .orderBy("d.timber_mark, d.scl_rtn_category_code, d.statement_number")
            .clientScope(STATEMENT_PARTY)
            .build(),

        // ── Area code lists ─────────────────────────────────────────────────
        CodesCatalog.codeTable("codes.queries.species", "SCALE_SPECIES_CODE"),
        CodesCatalog.codeTable("codes.queries.products", "SCALE_PRODUCT_CODE"),
        CodesCatalog.codeTable("codes.queries.grades", "SCALE_GRADE_CODE"),
        CodesCatalog.codeList("codes.queries.fileTypes", "FILE_TYPE_CODE"),
        CodesCatalog.codeTable("codes.queries.returnCategories", "SCL_RTN_CATEGORY_CODE")
    );
  }
}
