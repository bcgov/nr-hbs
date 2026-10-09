package ca.bc.gov.nrs.hbs.api.catalog;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.query.QueryCatalog;
import ca.bc.gov.nrs.hbs.api.query.QueryDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter.Type;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Submissions & batch tracking — monitoring of XML e-submissions of detail
 * scale data:
 *
 * <ul>
 *   <li>P041/P032 "Search for Detail Scale Transmissions" / "List of Received
 *       Detail Scale Transmissions (XML)" — ported from the dynamic SELECT
 *       built by proc {@code HBS2R041} (called from
 *       {@code dac.srt.detail.ManagerBean.findTransmissionsByCriteria}).</li>
 *   <li>P024/P027 "Search for Submitted Detail Batches" / "List of Submitted
 *       Detail Scale Data Batches (XML)" — ported from proc {@code HBS2R027}
 *       (called from {@code ManagerBean.findBatchCountByCriteria} /
 *       {@code BatchEntityBean.ejbFindByCriteria}).</li>
 * </ul>
 *
 * <p>Both procs concatenated user input into dynamic SQL; here every value is
 * a bound parameter. The procs' {@code DTL_DOC_BATCH} / {@code
 * DTL_BAT_FORMAT_ERROR} outer joins (which could duplicate rows) are replaced
 * by {@code EXISTS} / scalar sub-queries.
 *
 * <p>Client fence: industry/SPC users only see transmissions they created
 * ({@code CLIENT_NUMBER}), that were received for them
 * ({@code RECD_CLIENT_NUMBER}), or that hold batches for a scale site their
 * client owns (the legacy {@code L<userId>} "all sites" domain of HBS2R027).
 */
@Component
public class SubmissionsCatalog implements QueryCatalog {

  // ── P041/P032 transmissions (HBS2R041) ─────────────────────────────────

  /** Columns of HBS2R041's strSelectData + step/status descriptions + the P032 "Reason" (error type 8). */
  static final String TRANSMISSION_SQL = """
      SELECT t.transmission_id,
             t.entry_timestamp AS received_timestamp,
             t.file_name,
             t.file_datetime,
             t.batch_count,
             t.record_count,
             t.test_file_ind,
             CASE WHEN t.test_file_ind = 'Y' THEN 'Test' ELSE 'Prod' END AS transmission_type,
             t.hbs_xml_trans_step_code AS step_code,
             stp.description AS step_description,
             t.hbs_processing_status_code AS status_code,
             ps.description AS status_description,
             t.accepted_ind,
             t.client_number,
             t.client_locn_code,
             t.recd_client_number,
             t.input_userid,
             t.user_id,
             t.software_product,
             t.software_version,
             t.software_revision,
             t.entry_userid,
             t.update_userid,
             t.update_timestamp,
             (SELECT MIN(e.hbs_edit_err_message_code) KEEP (DENSE_RANK FIRST ORDER BY e.error_id)
                FROM transmission_format_error e
               WHERE e.transmission_id = t.transmission_id) AS reason_code,
             (SELECT MIN(m.description) KEEP (DENSE_RANK FIRST ORDER BY e.error_id)
                FROM transmission_format_error e
                JOIN hbs_edit_err_message_code m
                  ON m.hbs_edit_err_message_code = e.hbs_edit_err_message_code
                 AND m.hbs_edit_err_category_code = e.hbs_edit_err_category_code
               WHERE e.transmission_id = t.transmission_id) AS reason
        FROM dtl_scl_doc_transmission t
        LEFT JOIN hbs_xml_trans_step_code stp ON stp.hbs_xml_trans_step_code = t.hbs_xml_trans_step_code
        LEFT JOIN hbs_processing_status_code ps ON ps.hbs_processing_status_code = t.hbs_processing_status_code
       WHERE 1=1""";

  static final String TRANSMISSION_SCOPE = """
      AND (t.client_number = :scopeClientNumber
           OR t.recd_client_number = :scopeClientNumber
           OR EXISTS (SELECT 1
                        FROM dtl_doc_batch fb
                        JOIN scale_site fs ON fs.scale_site_id_nmbr = fb.scale_site_id_nmbr
                       WHERE fb.transmission_id = t.transmission_id
                         AND fs.owner_cli_number = :scopeClientNumber))""";

  // ── P024/P027 batches (HBS2R027) ───────────────────────────────────────

  /** HBS2R027 strSelectData (+ "Reason" first DTL_BAT_FORMAT_ERROR, + rejected = return - (err+other+ready)). */
  static final String BATCH_SQL = """
      SELECT b.batch_id,
             b.transmission_id,
             b.entry_timestamp AS batch_received,
             b.detail_scale_doc_type_code AS doc_type,
             dt.description AS doc_type_description,
             b.hbs_return_type_code AS return_type,
             b.scale_site_id_nmbr AS scale_site_no,
             TO_CHAR(ss.org_unit_no) AS org_unit_no,
             ou.org_unit_name,
             b.return_count,
             b.return_count - (b.in_error_count + b.other_count + b.ready_count) AS rejected_count,
             b.in_error_count,
             b.ready_count,
             b.other_count,
             b.batch_control_total,
             b.from_scale_date,
             b.to_scale_date,
             b.submitter_batch_id,
             b.accepted_ind,
             t.client_number,
             t.client_locn_code,
             t.recd_client_number,
             t.input_userid,
             t.file_name,
             t.entry_timestamp AS transmission_received,
             t.hbs_xml_trans_step_code AS step_code,
             stp.description AS step_description,
             t.hbs_processing_status_code AS status_code,
             ps.description AS status_description,
             (SELECT MIN(m.description) KEEP (DENSE_RANK FIRST ORDER BY e.error_id)
                FROM dtl_bat_format_error e
                JOIN hbs_edit_err_message_code m
                  ON m.hbs_edit_err_message_code = e.hbs_edit_err_message_code
                 AND m.hbs_edit_err_category_code = e.hbs_edit_err_category_code
               WHERE e.batch_id = b.batch_id) AS reason
        FROM dtl_doc_batch b
        JOIN dtl_scl_doc_transmission t ON t.transmission_id = b.transmission_id
        JOIN detail_scale_doc_type_code dt ON dt.detail_scale_doc_type_code = b.detail_scale_doc_type_code
        LEFT JOIN scale_site ss ON ss.scale_site_id_nmbr = b.scale_site_id_nmbr
        LEFT JOIN org_unit ou ON ou.org_unit_no = ss.org_unit_no
        LEFT JOIN hbs_xml_trans_step_code stp ON stp.hbs_xml_trans_step_code = t.hbs_xml_trans_step_code
        LEFT JOIN hbs_processing_status_code ps ON ps.hbs_processing_status_code = t.hbs_processing_status_code
       WHERE 1=1""";

  static final String BATCH_SCOPE = """
      AND (t.client_number = :scopeClientNumber
           OR t.recd_client_number = :scopeClientNumber
           OR ss.owner_cli_number = :scopeClientNumber)""";

  static final String REJECTED = "(b.return_count - (b.in_error_count + b.other_count + b.ready_count))";

  @Override
  public List<QueryDefinition> queries() {
    return List.of(
        // P041 → P032. Legacy "Specific Transmission ID" OR "General Criteria".
        QueryDefinition.builder("submissions.transmissions")
            .legacy("P041/P032")
            .capability(Capability.SCALE_RETURNS_VIEW)
            .sql(TRANSMISSION_SQL)
            .filter(QueryFilter.number("transmissionId", "AND t.transmission_id = :transmissionId"))
            .filter(QueryFilter.upper("transmissionType", "AND t.test_file_ind = :transmissionType"))
            .filter(QueryFilter.upper("transmissionStep", "AND t.hbs_xml_trans_step_code = :transmissionStep"))
            .filter(QueryFilter.upper("processingStatus", "AND t.hbs_processing_status_code = :processingStatus"))
            .filter(QueryFilter.date("receivedFrom", "AND t.entry_timestamp >= :receivedFrom"))
            .filter(QueryFilter.dateTo("receivedTo", "AND t.entry_timestamp < :receivedTo"))
            // BT9492: exclude transmissions without batches (legacy default on for MOF users).
            .filter(QueryFilter.upper("excludeCheck", "AND (:excludeCheck <> 'Y' OR t.batch_count <> 0)"))
            // HBS2R041 joins the site filters through DTL_DOC_BATCH and UNIONs the
            // batch-less transmissions back in (when not excluded) — hence "batch_count = 0 OR".
            .filter(QueryFilter.upper("scaleSiteNo", """
                AND (t.batch_count = 0 OR EXISTS (SELECT 1 FROM dtl_doc_batch sb
                      WHERE sb.transmission_id = t.transmission_id
                        AND sb.scale_site_id_nmbr = :scaleSiteNo))"""))
            .filter(QueryFilter.number("orgUnitNo", """
                AND (t.batch_count = 0 OR EXISTS (SELECT 1 FROM dtl_doc_batch ob
                      JOIN scale_site oss ON oss.scale_site_id_nmbr = ob.scale_site_id_nmbr
                      JOIN org_unit oou ON oou.org_unit_no = oss.org_unit_no
                      WHERE ob.transmission_id = t.transmission_id
                        AND (oss.org_unit_no = :orgUnitNo OR oou.rollup_region_no = :orgUnitNo)))"""))
            .filter(QueryFilter.eq("clientNumber",
                "AND (t.client_number = :clientNumber OR t.recd_client_number = :clientNumber)"))
            .filter(QueryFilter.eq("clientLocnCode", "AND t.client_locn_code = :clientLocnCode"))
            .filter(QueryFilter.upper("inputUserId", "AND UPPER(t.input_userid) = :inputUserId"))
            .sort("receivedTimestamp", "t.entry_timestamp")
            .sort("transmissionId", "t.transmission_id")
            .sort("fileName", "t.file_name")
            .sort("batchCount", "t.batch_count")
            .sort("recordCount", "t.record_count")
            .sort("stepDescription", "stp.description")
            .sort("statusCode", "t.hbs_processing_status_code")
            .orderBy("t.entry_timestamp DESC")
            .clientScope(TRANSMISSION_SCOPE)
            .build(),

        QueryDefinition.builder("submissions.transmission")
            .legacy("P032")
            .capability(Capability.SCALE_RETURNS_VIEW)
            .sql(TRANSMISSION_SQL)
            .filter(QueryFilter.required("transmissionId", "AND t.transmission_id = :transmissionId", Type.NUMBER))
            .clientScope(TRANSMISSION_SCOPE)
            .maxRows(1)
            .build(),

        // P024 → P027.
        QueryDefinition.builder("submissions.batches")
            .legacy("P024/P027")
            .capability(Capability.SCALE_RETURNS_VIEW)
            .sql(BATCH_SQL)
            .filter(QueryFilter.upper("returnType",
                "AND (:returnType = 'ALL' OR b.detail_scale_doc_type_code = :returnType)"))
            .filter(QueryFilter.date("receivedFrom", "AND t.entry_timestamp >= :receivedFrom"))
            .filter(QueryFilter.dateTo("receivedTo", "AND t.entry_timestamp < :receivedTo"))
            .filter(QueryFilter.number("transmissionId", "AND b.transmission_id = :transmissionId"))
            // "Scale Site" / "Submitter" radios (siteSearch, submitter) are only passed to the
            // HBS2R027 report; the value fields below filter whenever they are filled.
            .filter(QueryFilter.upper("scaleSiteNo", "AND b.scale_site_id_nmbr = :scaleSiteNo"))
            .filter(QueryFilter.number("orgUnitNo",
                "AND (ss.org_unit_no = :orgUnitNo OR ou.rollup_region_no = :orgUnitNo)"))
            .filter(QueryFilter.eq("clientNumber", "AND t.client_number = :clientNumber"))
            .filter(QueryFilter.eq("clientLocnCode", "AND t.client_locn_code = :clientLocnCode"))
            .filter(QueryFilter.upper("inputUserId", "AND UPPER(t.input_userid) = :inputUserId"))
            // "Batches" radio (BT630).
            .filter(QueryFilter.eq("batchType", """
                AND (:batchType = 'allbatches'
                     OR (:batchType = 'withrejecteddocuments' AND %1$s > 0)
                     OR (:batchType = 'withnorejecteddocuments' AND %1$s = 0)
                     OR (:batchType = 'rejectedbatches' AND %1$s > 0
                         AND EXISTS (SELECT 1 FROM dtl_bat_format_error re WHERE re.batch_id = b.batch_id)))"""
                .formatted(REJECTED)))
            .sort("batchId", "b.batch_id")
            .sort("batchReceived", "b.entry_timestamp")
            .sort("transmissionId", "b.transmission_id")
            .sort("docType", "b.detail_scale_doc_type_code")
            .sort("scaleSiteNo", "b.scale_site_id_nmbr")
            .sort("returnCount", "b.return_count")
            .sort("rejectedCount", REJECTED)
            .sort("inErrorCount", "b.in_error_count")
            .sort("readyCount", "b.ready_count")
            .sort("otherCount", "b.other_count")
            .orderBy("b.transmission_id DESC, b.batch_id DESC")
            .clientScope(BATCH_SCOPE)
            .build(),

        // P032 "Batch Count" link → batches of one transmission (transmission detail table).
        QueryDefinition.builder("submissions.transmissionBatches")
            .legacy("P032/P027")
            .capability(Capability.SCALE_RETURNS_VIEW)
            .sql(BATCH_SQL)
            .filter(QueryFilter.required("transmissionId", "AND b.transmission_id = :transmissionId", Type.NUMBER))
            .orderBy("b.batch_id")
            .clientScope(BATCH_SCOPE)
            .maxRows(1000)
            .build(),

        // One P027 row (drill-down; Rejected link report HBS2R032).
        QueryDefinition.builder("submissions.batch")
            .legacy("P027")
            .capability(Capability.SCALE_RETURNS_VIEW)
            .sql(BATCH_SQL)
            .filter(QueryFilter.required("batchId", "AND b.batch_id = :batchId", Type.NUMBER))
            .clientScope(BATCH_SCOPE)
            .maxRows(1)
            .build(),

        // Batch-level failure reasons (DetailHbsEditError type 9 = DTL_BAT_FORMAT_ERROR).
        QueryDefinition.builder("submissions.batchErrors")
            .legacy("P027")
            .capability(Capability.SCALE_RETURNS_VIEW)
            .sql("""
                SELECT e.error_id,
                       e.hbs_edit_err_category_code AS category_code,
                       e.hbs_edit_err_message_code AS message_code,
                       m.description,
                       e.error_timestamp
                  FROM dtl_bat_format_error e
                  JOIN dtl_doc_batch b ON b.batch_id = e.batch_id
                  JOIN dtl_scl_doc_transmission t ON t.transmission_id = b.transmission_id
                  LEFT JOIN scale_site ss ON ss.scale_site_id_nmbr = b.scale_site_id_nmbr
                  LEFT JOIN hbs_edit_err_message_code m
                    ON m.hbs_edit_err_message_code = e.hbs_edit_err_message_code
                   AND m.hbs_edit_err_category_code = e.hbs_edit_err_category_code
                 WHERE 1=1""")
            .filter(QueryFilter.required("batchId", "AND e.batch_id = :batchId", Type.NUMBER))
            .orderBy("e.error_id")
            .clientScope(BATCH_SCOPE)
            .maxRows(500)
            .build(),

        // Transmission-level failure reasons (DetailHbsEditError type 8 = TRANSMISSION_FORMAT_ERROR).
        QueryDefinition.builder("submissions.transmissionErrors")
            .legacy("P032")
            .capability(Capability.SCALE_RETURNS_VIEW)
            .sql("""
                SELECT e.error_id,
                       e.hbs_edit_err_category_code AS category_code,
                       e.hbs_edit_err_message_code AS message_code,
                       m.description,
                       e.error_timestamp
                  FROM transmission_format_error e
                  JOIN dtl_scl_doc_transmission t ON t.transmission_id = e.transmission_id
                  LEFT JOIN hbs_edit_err_message_code m
                    ON m.hbs_edit_err_message_code = e.hbs_edit_err_message_code
                   AND m.hbs_edit_err_category_code = e.hbs_edit_err_category_code
                 WHERE 1=1""")
            .filter(QueryFilter.required("transmissionId", "AND e.transmission_id = :transmissionId", Type.NUMBER))
            .orderBy("e.error_id")
            .clientScope(TRANSMISSION_SCOPE)
            .maxRows(500)
            .build(),

        CodesCatalog.codeTable("codes.submissions.detailDocTypes", "DETAIL_SCALE_DOC_TYPE_CODE")
    );
  }
}
