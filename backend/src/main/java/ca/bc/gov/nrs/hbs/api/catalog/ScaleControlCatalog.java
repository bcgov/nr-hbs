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
 * Scale Control tab (legacy P900): Late Submissions (P901/P902), Scale Event
 * Anomalies (P911–P955) and Anomaly Assessment Windows (P908/P909).
 *
 * <p>Timber transport / LDS registry screens live in
 * {@link ScaleControlTransportCatalog}; the change log and software use
 * screens in {@link ScaleControlAuditCatalog}. See docs/areas/scale-control.md.
 */
@Component
public class ScaleControlCatalog implements QueryCatalog {

  // ---------------------------------------------------------------------------
  // P901/P902 — Late Submissions. Ported from HBS3R902
  // (nr-mof-db PROCEDURES/V7.01600__HBS3R902.sql): active versions of log
  // tallies (P), weigh slips (W) and sample tallies (S) whose
  // DETAIL_SCALE_DOCUMENT.ENTRY_TIMESTAMP (date received) is more than
  // N days after the scale date. The proc's dynamic SQL is rebuilt as one
  // UNION ALL with bound filters; :daysLateLimit is a required filter and so
  // is always bound (it is also used by the DUE_DATE / DAYS_LATE columns).
  // ---------------------------------------------------------------------------
  static final String LATE_SUBMISSIONS_SQL = """
      SELECT x.detail_document_number,
             x.doc_version,
             x.return_type,
             dtc.description AS doc_type,
             x.version_status,
             x.event_type,
             x.scale_site,
             x.weigh_slip_number,
             x.pop_str_yr,
             x.scaler_licence,
             x.return_number,
             x.timber_mark,
             x.scale_date,
             x.scale_date + :daysLateLimit AS due_date,
             x.date_received,
             FLOOR(x.date_received - (x.scale_date + :daysLateLimit)) AS days_late
        FROM (
              SELECT lt.detail_document_number, lt.version AS doc_version, 'P' AS return_type,
                     dsd.detail_scale_doc_type_code AS doc_type_code,
                     rsc.description AS version_status, etc.description AS event_type,
                     lt.scale_site_id_nmbr AS scale_site,
                     lt.red_tag_weigh_slip AS weigh_slip_number,
                     NULL AS pop_str_yr,
                     lt.primary_license_number AS scaler_licence, lt.return_number,
                     lt.timber_mark,
                     TRUNC(lt.scale_date) AS scale_date, TRUNC(dsd.entry_timestamp) AS date_received
                FROM log_tally lt
                JOIN detail_scale_document dsd ON dsd.detail_document_number = lt.detail_document_number
                JOIN scale_event_type_code etc ON etc.scale_event_type_code = lt.scale_event_type_code
                JOIN scl_rtn_version_state_code rsc ON rsc.scl_rtn_version_state_code = lt.scl_rtn_version_state_code
               WHERE lt.active_version_ind = 'Y'
              UNION ALL
              SELECT ws.detail_document_number, ws.version, 'W',
                     dsd.detail_scale_doc_type_code,
                     rsc.description, etc.description,
                     ws.scale_site_id_nmbr,
                     ws.weigh_slip_number,
                     CASE
                       WHEN ws.scale_event_type_code IN ('RT','RR','4R','DP') THEN ws.company_use_stratum
                       WHEN ws.population_number IS NOT NULL
                         THEN ws.population_number || '/' || ws.stratum_number || '/' || ws.sampling_year
                       WHEN ws.company_use_stratum IS NOT NULL THEN ws.company_use_stratum
                       WHEN ws.input_psy IS NOT NULL THEN '*' || ws.input_psy
                     END,
                     ws.primary_license_number, NULL,
                     ws.timber_mark,
                     TRUNC(ws.scale_date), TRUNC(dsd.entry_timestamp)
                FROM weigh_slip ws
                JOIN detail_scale_document dsd ON dsd.detail_document_number = ws.detail_document_number
                JOIN scale_event_type_code etc ON etc.scale_event_type_code = ws.scale_event_type_code
                JOIN scl_rtn_version_state_code rsc ON rsc.scl_rtn_version_state_code = ws.scl_rtn_version_state_code
               WHERE ws.active_version_ind = 'Y'
              UNION ALL
              SELECT slt.detail_document_number, slt.version, 'S',
                     dsd.detail_scale_doc_type_code,
                     rsc.description, etc.description,
                     slt.scale_site_id_nmbr,
                     slt.sampled_weigh_slip,
                     CASE
                       WHEN slt.population_number IS NOT NULL
                         THEN slt.population_number || '/' || slt.stratum_number || '/' || slt.sampling_year
                       WHEN slt.input_psy IS NOT NULL THEN '*' || slt.input_psy
                     END,
                     slt.primary_license_number, slt.return_number,
                     slt.timber_mark,
                     TRUNC(slt.scale_date), TRUNC(dsd.entry_timestamp)
                FROM sample_log_tally slt
                JOIN detail_scale_document dsd ON dsd.detail_document_number = slt.detail_document_number
                JOIN scale_event_type_code etc ON etc.scale_event_type_code = slt.scale_event_type_code
                JOIN scl_rtn_version_state_code rsc ON rsc.scl_rtn_version_state_code = slt.scl_rtn_version_state_code
               WHERE slt.active_version_ind = 'Y'
             ) x
        JOIN scale_site ss ON ss.scale_site_id_nmbr = x.scale_site
        LEFT JOIN org_unit ou ON ou.org_unit_no = ss.org_unit_no
        LEFT JOIN detail_scale_doc_type_code dtc ON dtc.detail_scale_doc_type_code = x.doc_type_code
       WHERE 1=1""";

  // ---------------------------------------------------------------------------
  // P911–P955 — Scale Event Anomalies. One normalised row per anomaly /
  // linked document, ported from the eight list procs HBS2R912 (scaling),
  // HBS2R914 (weighing), HBS2R922 (arrival), HBS2R932 (arrivals w/o
  // departures), HBS2R933 (departures w/o arrivals), HBS2R942 (weigh slips
  // w/o samples), HBS2R952 (weigh slips w/o red tags), HBS2R953 (red tags w/o
  // weigh slips) and AnomalyManagerBean.findAnomalyDetails (P954/P955). Each
  // UNION ALL branch joins SCALE_ANOMALY_LOG to one document table through
  // its FK pair, exactly as the procs did; the outer query adds the shared
  // site / batch / code-table joins and the bound filters.
  //   MISSING_RANGE reproduces the JSP arithmetic of P912/P914/P922
  //   ("n-gap … n-1" on the return / weigh slip / arrival number).
  // ---------------------------------------------------------------------------
  static final String ANOMALY_SQL = """
      SELECT a.anomaly_id,
             a.scale_control_type_code,
             sctc.description AS scale_control_type,
             a.scale_anomaly_type_code,
             satc.description AS anomaly_type,
             a.scale_anomaly_status_code,
             sasc.description AS anomaly_status,
             a.scale_site_id_nmbr AS scale_site,
             ss.site_name,
             a.detail_document_number,
             a.doc_version,
             dtc.description AS doc_type,
             setc.description AS event_type,
             a.event_date,
             a.timber_mark,
             a.timber_brand,
             a.primary_license_number AS primary_licence,
             a.return_number,
             a.weigh_slip_number,
             a.load_arrival_number,
             a.load_departure_number,
             a.lds_number,
             a.transport_id,
             a.other_site,
             a.pop_str_yr,
             a.log_count,
             a.sample_weight,
             a.anomaly_gap_count AS gap_count,
             CASE
               WHEN a.anomaly_gap_count = 1 AND a.base_number IS NOT NULL
                 THEN TO_CHAR(a.base_number - 1)
               WHEN a.anomaly_gap_count > 1 AND a.base_number IS NOT NULL
                 THEN TO_CHAR(a.base_number - a.anomaly_gap_count) || ' - ' || TO_CHAR(a.base_number - 1)
             END AS missing_range,
             be.anomaly_batch_datetime AS original_assessment,
             aasc.description AS assessment_status,
             CASE WHEN aac.anomaly_assessment_status_code IN ('RCM','WCM')
                   AND a.scale_anomaly_status_code = 'O' THEN 'Y' ELSE 'N' END AS clearable,
             a.user_id AS cleared_by,
             a.cleared_datetime AS date_cleared,
             a.user_cleared_comment AS cleared_comment
        FROM (
              SELECT sa.anomaly_id, sa.scale_control_type_code, sa.scale_anomaly_type_code,
                     sa.scale_anomaly_status_code, sa.original_anomaly_batch_id, sa.anomaly_gap_count,
                     sa.user_id, sa.cleared_datetime, sa.user_cleared_comment,
                     lt.detail_document_number, lt.version AS doc_version,
                     lt.scale_site_id_nmbr, lt.scale_event_type_code, lt.scale_date AS event_date,
                     lt.timber_mark, lt.timber_brand, lt.primary_license_number, lt.return_number,
                     lt.red_tag_weigh_slip AS weigh_slip_number,
                     lt.load_arrival_number, CAST(NULL AS VARCHAR2(10)) AS load_departure_number,
                     lt.incoming_lds_number AS lds_number, lt.transport_identifier AS transport_id,
                     CASE WHEN sa.scale_control_type_code = 'X'
                          THEN NVL(lt.orig_redtag_scale_site_id_nmbr,
                                   (SELECT MIN(s2.scale_site_id_nmbr) FROM scale_site s2
                                     WHERE s2.timber_brand = lt.timber_brand))
                          ELSE lt.orig_redtag_scale_site_id_nmbr END AS other_site,
                     CAST(NULL AS VARCHAR2(40)) AS pop_str_yr,
                     lt.log_count, CAST(NULL AS NUMBER) AS sample_weight,
                     CASE sa.scale_control_type_code
                       WHEN 'S' THEN lt.return_number
                       WHEN 'A' THEN hbs_extract_arrival_number(lt.load_arrival_number) END AS base_number
                FROM scale_anomaly_log sa
                JOIN log_tally lt ON lt.detail_document_number = sa.log_tly_dtl_doc_no
                                 AND lt.version = sa.log_tly_version
               WHERE (sa.scale_control_type_code <> 'A' OR lt.scale_event_type_code IN ('PS','RR','4R','FD'))
              UNION ALL
              SELECT sa.anomaly_id, sa.scale_control_type_code, sa.scale_anomaly_type_code,
                     sa.scale_anomaly_status_code, sa.original_anomaly_batch_id, sa.anomaly_gap_count,
                     sa.user_id, sa.cleared_datetime, sa.user_cleared_comment,
                     slt.detail_document_number, slt.version,
                     slt.scale_site_id_nmbr, slt.scale_event_type_code, slt.scale_date,
                     slt.timber_mark, slt.timber_brand, slt.primary_license_number, slt.return_number,
                     slt.sampled_weigh_slip,
                     slt.load_arrival_number, NULL,
                     slt.incoming_lds_number, slt.transport_identifier,
                     slt.orig_redtag_scale_site_id_nmbr,
                     CASE WHEN slt.population_number IS NOT NULL
                          THEN slt.population_number || '/' || slt.stratum_number || '/' || slt.sampling_year END,
                     slt.log_count, slt.sample_weight,
                     CASE sa.scale_control_type_code
                       WHEN 'S' THEN slt.return_number
                       WHEN 'A' THEN hbs_extract_arrival_number(slt.load_arrival_number) END
                FROM scale_anomaly_log sa
                JOIN sample_log_tally slt ON slt.detail_document_number = sa.smpl_tly_dtl_doc_no
                                         AND slt.version = sa.smpl_tly_version
               WHERE (sa.scale_control_type_code <> 'A' OR slt.scale_event_type_code = 'RS')
              UNION ALL
              SELECT sa.anomaly_id, sa.scale_control_type_code, sa.scale_anomaly_type_code,
                     sa.scale_anomaly_status_code, sa.original_anomaly_batch_id, sa.anomaly_gap_count,
                     sa.user_id, sa.cleared_datetime, sa.user_cleared_comment,
                     ws.detail_document_number, ws.version,
                     ws.scale_site_id_nmbr, ws.scale_event_type_code, ws.scale_date,
                     ws.timber_mark, ws.timber_brand, ws.primary_license_number, NULL,
                     ws.weigh_slip_number,
                     NULL, NULL,
                     CASE WHEN sa.scale_control_type_code = 'X' THEN ws.incoming_lds_number
                          ELSE NVL(ws.outgoing_lds_number, ws.incoming_lds_number) END,
                     ws.transport_identifier,
                     CASE WHEN sa.scale_control_type_code = 'X'
                          THEN (SELECT MIN(s2.scale_site_id_nmbr) FROM scale_site s2
                                 WHERE s2.timber_brand = ws.timber_brand)
                          ELSE ws.destination END,
                     CASE WHEN ws.population_number IS NOT NULL
                          THEN ws.population_number || '/' || ws.stratum_number || '/' || ws.sampling_year
                          ELSE ws.company_use_stratum END,
                     NULL, ws.gross_weight - ws.tare_weight,
                     CASE WHEN sa.scale_control_type_code = 'W'
                           AND REGEXP_LIKE(ws.weigh_slip_number, '^[0-9]+$')
                          THEN TO_NUMBER(ws.weigh_slip_number) END
                FROM scale_anomaly_log sa
                JOIN weigh_slip ws ON ws.detail_document_number = sa.ws_dtl_doc_no
                                  AND ws.version = sa.ws_version
              UNION ALL
              SELECT sa.anomaly_id, sa.scale_control_type_code, sa.scale_anomaly_type_code,
                     sa.scale_anomaly_status_code, sa.original_anomaly_batch_id, sa.anomaly_gap_count,
                     sa.user_id, sa.cleared_datetime, sa.user_cleared_comment,
                     sl.detail_document_number, sl.version,
                     sl.scale_site_id_nmbr, sl.scale_event_type_code,
                     DECODE(sl.scale_event_type_code, 'B4', sl.arrival_date, 'TD', sl.arrival_date, sl.scale_date),
                     sl.timber_mark, sl.timber_brand, sl.license_number, sl.return_number,
                     sl.arrival_ledger_weigh_slip,
                     sl.load_arrival_number, NULL,
                     sl.incoming_lds_number, sl.transport_identifier,
                     NVL(sl.orig_scale_site,
                         (SELECT MIN(s2.scale_site_id_nmbr) FROM scale_site s2
                           WHERE s2.timber_brand = sl.timber_brand)),
                     NULL,
                     NULL, NULL,
                     CASE WHEN sa.scale_control_type_code = 'A'
                          THEN hbs_extract_arrival_number(sl.load_arrival_number) END
                FROM scale_anomaly_log sa
                JOIN scl_site_arrival_ledger sl ON sl.detail_document_number = sa.arrvl_ldgr_dtl_doc_no
                                               AND sl.version = sa.arrvl_ldgr_version
              UNION ALL
              SELECT sa.anomaly_id, sa.scale_control_type_code, sa.scale_anomaly_type_code,
                     sa.scale_anomaly_status_code, sa.original_anomaly_batch_id, sa.anomaly_gap_count,
                     sa.user_id, sa.cleared_datetime, sa.user_cleared_comment,
                     dl.detail_document_number, dl.version,
                     dl.scale_site_id_nmbr, dl.scale_event_type_code, dl.departure_date,
                     dl.timber_mark, dl.timber_brand, NULL, NULL,
                     NULL,
                     NULL, dl.load_departure_number,
                     dl.outgoing_lds_number, dl.outgoing_transport_id,
                     dl.destination,
                     NULL,
                     NULL, NULL,
                     NULL
                FROM scale_anomaly_log sa
                JOIN scl_site_departure_ledger dl ON dl.detail_document_number = sa.deprt_ldgr_dtl_doc_no
                                                 AND dl.version = sa.deprt_ldgr_version
             ) a
        JOIN hbs_anomaly_batch_event be ON be.anomaly_batch_id = a.original_anomaly_batch_id
        LEFT JOIN scale_site ss ON ss.scale_site_id_nmbr = a.scale_site_id_nmbr
        LEFT JOIN org_unit ou ON ou.org_unit_no = ss.org_unit_no
        LEFT JOIN detail_scale_document ds ON ds.detail_document_number = a.detail_document_number
        LEFT JOIN detail_scale_doc_type_code dtc ON dtc.detail_scale_doc_type_code = ds.detail_scale_doc_type_code
        LEFT JOIN scale_event_type_code setc ON setc.scale_event_type_code = a.scale_event_type_code
        LEFT JOIN scale_control_type_code sctc ON sctc.scale_control_type_code = a.scale_control_type_code
        LEFT JOIN scale_anomaly_type_code satc ON satc.scale_anomaly_type_code = a.scale_anomaly_type_code
        LEFT JOIN scale_anomaly_status_code sasc ON sasc.scale_anomaly_status_code = a.scale_anomaly_status_code
        LEFT JOIN anomaly_assessment_control aac ON aac.scale_control_type_code = a.scale_control_type_code
        LEFT JOIN anomaly_assessment_status_code aasc
               ON aasc.anomaly_assessment_status_code = aac.anomaly_assessment_status_code
       WHERE 1=1""";

  /**
   * Legacy radAnomalyType (P911 select / home links) → URL value → control
   * type (SCALE_CONTROL_TYPE_CODE) and, for the single gap / duplicate
   * options, SCALE_ANOMALY_TYPE_CODE:
   * scalinganomaly=SCALING(S), weighinganomaly=WEIGHING(W),
   * arrivalanomaly=ARRIVAL(A), arrdepanomaly=ARR_DEP(X),
   * deparranomaly=DEP_ARR(D), weighsampleanomaly=WEIGH_SAMPLE(Y),
   * weighredanomaly=WEIGH_RED(Z), redweighanomaly=RED_WEIGH(R);
   * *_GAP → G, *_DUP → D.
   */
  static final String ANOMALY_TYPE_FILTER = """
      AND a.scale_control_type_code = DECODE(:anomalyType,
            'SCALING','S', 'SCALING_GAP','S', 'SCALING_DUP','S',
            'WEIGHING','W', 'WEIGHING_GAP','W', 'WEIGHING_DUP','W',
            'ARRIVAL','A', 'ARRIVAL_GAP','A', 'ARRIVAL_DUP','A',
            'ARR_DEP','X', 'DEP_ARR','D', 'WEIGH_SAMPLE','Y', 'WEIGH_RED','Z', 'RED_WEIGH','R', '?')
      AND a.scale_anomaly_type_code LIKE DECODE(:anomalyType,
            'SCALING_GAP','G', 'WEIGHING_GAP','G', 'ARRIVAL_GAP','G',
            'SCALING_DUP','D', 'WEIGHING_DUP','D', 'ARRIVAL_DUP','D',
            'ARR_DEP','M', 'DEP_ARR','M', 'WEIGH_SAMPLE','M', 'WEIGH_RED','M', 'RED_WEIGH','M', '%')""";

  /** Industry fence: anomalies at scale sites owned by the active client. */
  static final String ANOMALY_CLIENT_SCOPE = "AND ss.owner_cli_number = :scopeClientNumber";

  // ---------------------------------------------------------------------------
  // P908 — Anomaly Assessment Windows: ANOMALY_ASSESSMENT_CONTROL with the
  // last HBS_ANOMALY_BATCH_EVENT per control type (AnomalyBatchEventQuery
  // .selectAllByLastEvent: MAX(anomaly_batch_id) per type).
  // ---------------------------------------------------------------------------
  static final String WINDOWS_SQL = """
      SELECT aac.scale_control_type_code,
             sctc.description AS scale_control_type,
             aac.presentation_sequence,
             aac.assessment_begin,
             aac.assessment_end,
             aac.anomaly_assessment_status_code,
             aasc.description AS assessment_status,
             CASE WHEN aac.anomaly_assessment_status_code IN ('RCM','WCM') THEN 'Y' ELSE 'N' END AS updatable,
             aac.previous_anomaly_batch_id,
             aac.current_anomaly_batch_id,
             be.anomaly_batch_datetime AS last_run,
             be.from_scale_date AS from_date,
             be.to_scale_date AS to_date,
             aac.update_userid,
             aac.update_timestamp
        FROM anomaly_assessment_control aac
        LEFT JOIN scale_control_type_code sctc ON sctc.scale_control_type_code = aac.scale_control_type_code
        LEFT JOIN anomaly_assessment_status_code aasc
               ON aasc.anomaly_assessment_status_code = aac.anomaly_assessment_status_code
        LEFT JOIN hbs_anomaly_batch_event be
               ON be.anomaly_batch_id = (SELECT MAX(t.anomaly_batch_id) FROM hbs_anomaly_batch_event t
                                          WHERE t.scale_control_type_code = aac.scale_control_type_code)
       WHERE 1=1""";

  private static QueryDefinition.Builder anomalyFilters(QueryDefinition.Builder b) {
    return b
        .filter(QueryFilter.eq("status", "AND a.scale_anomaly_status_code = :status"))
        .filter(QueryFilter.date("scaleDateFrom", "AND a.event_date >= :scaleDateFrom"))
        .filter(QueryFilter.dateTo("scaleDateTo", "AND a.event_date < :scaleDateTo"))
        .filter(QueryFilter.date("assessedFrom", "AND be.anomaly_batch_datetime >= :assessedFrom"))
        .filter(QueryFilter.dateTo("assessedTo", "AND be.anomaly_batch_datetime < :assessedTo"))
        .filter(QueryFilter.upper("primaryLicence", "AND a.primary_license_number = :primaryLicence"))
        .filter(QueryFilter.upper("scaleSite", "AND a.scale_site_id_nmbr = :scaleSite"))
        .filter(QueryFilter.number("orgUnitNo",
            "AND (ss.org_unit_no = :orgUnitNo OR ou.rollup_region_no = :orgUnitNo)"));
  }

  @Override
  public List<QueryDefinition> queries() {
    return List.of(
        // ---- P901/P902 Late Submissions -------------------------------------
        QueryDefinition.builder("scaleControl.lateSubmissions.search")
            .legacy("P901/P902")
            .capability(Capability.SCALE_CONTROL_VIEW)
            .sql(LATE_SUBMISSIONS_SQL)
            .filter(QueryFilter.required("daysLateLimit",
                "AND x.date_received > x.scale_date + :daysLateLimit", Type.NUMBER))
            .filter(QueryFilter.required("scaleDateFrom", "AND x.scale_date >= :scaleDateFrom", Type.DATE))
            .filter(QueryFilter.required("scaleDateTo", "AND x.scale_date < :scaleDateTo", Type.DATE_TO))
            .filter(QueryFilter.upper("returnType", "AND (:returnType = 'ALL' OR x.return_type = :returnType)"))
            .filter(QueryFilter.upper("scaleSite", "AND x.scale_site = :scaleSite"))
            .filter(QueryFilter.number("orgUnitNo",
                "AND (ss.org_unit_no = :orgUnitNo OR ou.rollup_region_no = :orgUnitNo)"))
            .sort("detailDocumentNumber", "x.detail_document_number")
            .sort("scaleSite", "x.scale_site")
            .sort("scaleDate", "x.scale_date")
            .sort("dateReceived", "x.date_received")
            .sort("daysLate", "x.date_received - x.scale_date")
            // HBS3R902: ORDER BY DAYSLATE DESC, SCALE_DATE, SCALE_SITE, RETURN_TYPE, EVENT_TYPE, DDN
            .orderBy("x.date_received - x.scale_date DESC, x.scale_date, x.scale_site, x.return_type,"
                + " x.event_type, x.detail_document_number")
            .clientScope("AND ss.owner_cli_number = :scopeClientNumber")
            .build(),

        // ---- P911/P912–P953 Scale Event Anomalies ---------------------------
        anomalyFilters(QueryDefinition.builder("scaleControl.anomalies.search")
            .legacy("P911/P912/P914/P922/P932/P933/P942/P952/P953")
            .capability(Capability.SCALE_CONTROL_VIEW)
            .sql(ANOMALY_SQL)
            .filter(QueryFilter.required("anomalyType", ANOMALY_TYPE_FILTER, Type.UPPER)))
            .sort("scaleSite", "a.scale_site_id_nmbr")
            .sort("eventDate", "a.event_date")
            .sort("primaryLicence", "a.primary_license_number")
            .sort("returnNumber", "a.return_number")
            .sort("weighSlipNumber", "a.weigh_slip_number")
            .sort("anomalyStatus", "sasc.description")
            .sort("originalAssessment", "be.anomaly_batch_datetime")
            .orderBy("a.scale_site_id_nmbr, a.primary_license_number, a.return_number, a.weigh_slip_number,"
                + " a.load_arrival_number, a.load_departure_number, a.event_date, a.scale_anomaly_type_code")
            .clientScope(ANOMALY_CLIENT_SCOPE)
            .build(),

        // P954 / P955 — one anomaly (clear confirmation / user-cleared details)
        QueryDefinition.builder("scaleControl.anomalies.detail")
            .legacy("P954/P955")
            .capability(Capability.SCALE_CONTROL_VIEW)
            .sql(ANOMALY_SQL)
            .filter(QueryFilter.required("anomalyId", "AND a.anomaly_id = :anomalyId", Type.NUMBER))
            .orderBy("a.detail_document_number")
            .clientScope(ANOMALY_CLIENT_SCOPE)
            .maxRows(20)
            .build(),

        // ---- P908 Anomaly Assessment Windows --------------------------------
        QueryDefinition.builder("scaleControl.anomalyWindows.list")
            .legacy("P908")
            .capability(Capability.SCALE_CONTROL_VIEW)
            .sql(WINDOWS_SQL)
            .filter(QueryFilter.upper("scaleControlTypeCode", "AND aac.scale_control_type_code = :scaleControlTypeCode"))
            .orderBy("aac.presentation_sequence")
            .maxRows(50)
            .build(),

        // P908 edit — pre-fills the anomaly-windows form. With no URL param it
        // returns the first control type (presentation order).
        QueryDefinition.builder("scaleControl.anomalyWindows.detail")
            .legacy("P908/P909")
            .capability(Capability.PRODUCTION_CONTROL)
            .sql(WINDOWS_SQL)
            .filter(QueryFilter.upper("scaleControlTypeCode", "AND aac.scale_control_type_code = :scaleControlTypeCode"))
            .orderBy("aac.presentation_sequence")
            .maxRows(50)
            .build(),

        // ---- Area code lists -------------------------------------------------
        CodesCatalog.codeTable("codes.scaleControl.controlTypes", "SCALE_CONTROL_TYPE_CODE"),
        CodesCatalog.codeTable("codes.scaleControl.assessmentStatuses", "ANOMALY_ASSESSMENT_STATUS_CODE"),
        CodesCatalog.codeTable("codes.scaleControl.docTypes", "DETAIL_SCALE_DOC_TYPE_CODE")
    );
  }

  @Override
  public List<CommandDefinition> commands() {
    return List.of(
        // P908/P909 "Update Window Spans" → Confirm: AnomalyManagerBean
        // .updateAssessmentControlParameter, which stored the row through the
        // HBS_STORE_ANMLY_ASSESS_CONTROL table API (args in declared order).
        // NOTE: the window-expansion follow-up (status WIP →
        // HBS_UPDATE_STALE_ANOMALIES → WCM) and the P908 validations are NOT
        // part of this single call — see docs/areas/scale-control.md.
        CommandDefinition.builder("scaleControl.anomalyWindows.update")
            .legacy("P908/P909")
            .capability(Capability.PRODUCTION_CONTROL)
            .procedure("HBS_STORE_ANMLY_ASSESS_CONTROL")
            .requiredBody("scaleControlTypeCode", Type.UPPER)       // i_Scale_Control_Type_Code
            .requiredBody("presentationSequence", Type.NUMBER)      // i_Presentation_Sequence
            .requiredBody("assessmentBegin", Type.NUMBER)           // i_Assessment_Begin (From Days)
            .requiredBody("assessmentEnd", Type.NUMBER)             // i_Assessment_End (To Days)
            .requiredBody("anomalyAssessmentStatusCode", Type.UPPER) // i_Anmly_Assess_Status_Code
            .requiredBody("previousAnomalyBatchId", Type.NUMBER)    // i_Previous_Anomaly_Batch_ID
            .requiredBody("currentAnomalyBatchId", Type.NUMBER)     // i_Current_Anomaly_Batch_ID
            .now()                                                  // i_Update_Timestamp
            .auditUser()                                            // i_Update_UserID
            .build()
    );
  }
}
