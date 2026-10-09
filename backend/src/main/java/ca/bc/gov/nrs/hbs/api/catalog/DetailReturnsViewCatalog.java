package ca.bc.gov.nrs.hbs.api.catalog;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.query.QueryCatalog;
import ca.bc.gov.nrs.hbs.api.query.QueryDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter.Type;
import org.springframework.stereotype.Component;

import java.util.List;

import static ca.bc.gov.nrs.hbs.api.catalog.DetailReturnsSql.ALL_ERRORS;
import static ca.bc.gov.nrs.hbs.api.catalog.DetailReturnsSql.CLIENT_FENCE;
import static ca.bc.gov.nrs.hbs.api.catalog.DetailReturnsSql.DOCUMENT_VERSIONS;

/**
 * Scale Returns — Detail: read-only views of one detail scale return version
 * (legacy P552 log tally, P602 weigh slip, P652 sample tally, P752 SFP tally,
 * P702 arrival/departure ledger) and their child lists: log details P556/P656/P756
 * (and the read side of P558/P658/P758), log defects P557/P657 (P559/P659),
 * detail segregations, Transaction History ({@code DetailScaleTransactionsTag}),
 * HBS edit errors ({@code hbs2:hbsDetailEditErrors}) and version history
 * ("Show History", {@code hbs2:versionExplorer}).
 *
 * <p>Legacy reads went through BMP entity beans over the same tables
 * ({@code dac/detail/*EntityBean}, {@code DetailManagerBean.findDetailScaleDocumentAndVersionByPrimaryKey});
 * the SELECTs below read exactly those tables/columns. Every child query is
 * fenced for industry users through its DDN (the legacy view actions threw
 * SecurityException via {@code P046Action.isIndustryUserInterestedDocument}).
 */
@Component
public class DetailReturnsViewCatalog implements QueryCatalog {

  /** Industry fence for a child row identified by {@code ddnExpr}. */
  private static String childFence(String ddnExpr) {
    // Correlate at the EXISTS level only (Oracle does not see outer aliases inside nested inline views).
    return "AND EXISTS (SELECT 1 FROM (" + DOCUMENT_VERSIONS + "\n " + CLIENT_FENCE + ") dv"
        + " WHERE dv.ddn = " + ddnExpr + ")";
  }

  private static QueryFilter ddn(String expr) {
    return QueryFilter.required("ddn", "AND " + expr + " = :ddn", Type.UPPER);
  }

  private static QueryFilter version(String expr) {
    return QueryFilter.required("version", "AND " + expr + " = :version", Type.NUMBER);
  }

  /** P552/P602/P652/P752/P702 header — one version of any detail document type. */
  static QueryDefinition version() {
    return QueryDefinition.builder("detailReturns.version")
        .legacy("P552/P602/P652/P752/P702")
        .capability(Capability.SCALE_RETURNS_VIEW)
        .sql(DOCUMENT_VERSIONS)
        .filter(ddn("d.ddn"))
        .filter(version("d.version"))
        .clientScope(CLIENT_FENCE)
        .orderBy("d.version")
        .maxRows(1)
        .build();
  }

  /** "Show History" / version explorer — every version of the DDN. */
  static QueryDefinition versions() {
    return QueryDefinition.builder("detailReturns.versions")
        .legacy("P551 Show History / hbs2:versionExplorer")
        .capability(Capability.SCALE_RETURNS_VIEW)
        .sql("SELECT d.ddn, d.version, d.status, d.status_desc, d.active_ind, d.event_type_desc,"
            + " d.event_date, d.entry_userid, d.entry_timestamp, d.update_userid, d.update_timestamp"
            + " FROM (" + DOCUMENT_VERSIONS + ") d WHERE 1=1")
        .filter(ddn("d.ddn"))
        .clientScope(CLIENT_FENCE)
        .orderBy("d.version")
        .maxRows(100)
        .build();
  }

  /** HBS Edit Error box (VER + DETAIL errors) with message descriptions and responsibility. */
  static QueryDefinition errors() {
    return QueryDefinition.builder("detailReturns.errors")
        .legacy("hbs2:hbsDetailEditErrors (LogTallyTableHeader.jsp)")
        .capability(Capability.SCALE_RETURNS_VIEW)
        .sql("SELECT e.ddn, e.version, e.code, heemc.description, e.category, e.error_level,"
            + " heemc.hbs_edit_err_responsiblty_code AS responsibility, e.error_timestamp"
            + " FROM (" + ALL_ERRORS + ") e"
            + " LEFT JOIN hbs_edit_err_message_code heemc ON heemc.hbs_edit_err_message_code = e.code"
            + " AND heemc.hbs_edit_err_category_code = e.category WHERE 1=1")
        .filter(ddn("e.ddn"))
        .filter(version("e.version"))
        .clientScope(childFence("e.ddn"))
        .orderBy("e.error_level DESC, e.code")
        .maxRows(500)
        .build();
  }

  private static final String LOGS = """
      SELECT 'P' AS log_type, l.detail_document_number AS ddn, l.version, l.logdtl_id,
             l.log_number, l.input_bcomb_timber_mark AS input_mark, l.timber_mark AS billed_mark,
             l.nmv_ind, NVL(sp.description, NVL2(l.input_species, '(' || l.input_species || ')', NULL)) AS species,
             l.scale_product_code AS product,
             NVL(gr.description, NVL2(l.input_grade, '(' || l.input_grade || ')', NULL)) AS grade,
             l.length, l.top, l.butt, l.net_volume, l.deduction_count AS defect_count,
             TO_NUMBER(NULL) AS stack_length, TO_NUMBER(NULL) AS stack_height, TO_NUMBER(NULL) AS stack_width,
             TO_NUMBER(NULL) AS conversion_factor, TO_NUMBER(NULL) AS piece_count
        FROM log_tally_detail l
        LEFT JOIN scale_species_code sp ON sp.scale_species_code = l.scale_species_code
        LEFT JOIN scale_grade_code gr ON gr.scale_grade_code = l.scale_grade_code
      UNION ALL
      SELECT 'S', l.detail_document_number, l.version, l.logdtl_id,
             l.log_number, TO_CHAR(NULL), TO_CHAR(NULL),
             l.nmv_ind, NVL(sp.description, NVL2(l.input_species, '(' || l.input_species || ')', NULL)),
             l.scale_product_code,
             NVL(gr.description, NVL2(l.input_grade, '(' || l.input_grade || ')', NULL)),
             l.length, l.top, l.butt, l.net_volume, l.deduction_count,
             TO_NUMBER(NULL), TO_NUMBER(NULL), TO_NUMBER(NULL), TO_NUMBER(NULL), TO_NUMBER(NULL)
        FROM sample_tally_detail l
        LEFT JOIN scale_species_code sp ON sp.scale_species_code = l.scale_species_code
        LEFT JOIN scale_grade_code gr ON gr.scale_grade_code = l.scale_grade_code
      UNION ALL
      SELECT 'F', l.detail_document_number, l.version, l.logdtl_id,
             l.sfp_number, TO_CHAR(NULL), TO_CHAR(NULL),
             TO_CHAR(NULL), NVL(sp.description, NVL2(l.input_species, '(' || l.input_species || ')', NULL)),
             NVL(l.scale_product_code, l.input_product),
             NVL(gr.description, NVL2(l.input_grade, '(' || l.input_grade || ')', NULL)),
             l.prod_log_length, l.prod_log_top, l.prod_log_butt, l.net_volume, TO_NUMBER(NULL),
             l.stack_length, l.stack_height, l.stack_width, l.conversion_factor, l.piece_count
        FROM sfp_log_detail l
        LEFT JOIN scale_species_code sp ON sp.scale_species_code = l.scale_species_code
        LEFT JOIN scale_grade_code gr ON gr.scale_grade_code = l.scale_grade_code""";

  /** P556 / P656 / P756 Log Details (read-only list of the logs / SFP lines of a version). */
  static QueryDefinition logs() {
    return QueryDefinition.builder("detailReturns.logs")
        .legacy("P556/P656/P756 (read side of P558/P658/P758)")
        .capability(Capability.SCALE_RETURNS_VIEW)
        .sql("SELECT x.* FROM (" + LOGS + ") x WHERE 1=1")
        .filter(ddn("x.ddn"))
        .filter(version("x.version"))
        .clientScope(childFence("x.ddn"))
        .orderBy("x.log_number, x.logdtl_id")
        .maxRows(10000)
        .build();
  }

  /** P557 / P657 log row header for the defects view. */
  static QueryDefinition log() {
    return QueryDefinition.builder("detailReturns.log")
        .legacy("P557/P657 (hbs2:retrieveLogTallyDetail)")
        .capability(Capability.SCALE_RETURNS_VIEW)
        .sql("SELECT x.* FROM (" + LOGS + ") x WHERE x.log_type IN ('P', 'S')")
        .filter(QueryFilter.required("logType", "AND x.log_type = :logType", Type.UPPER))
        .filter(QueryFilter.required("logDtlId", "AND x.logdtl_id = :logDtlId", Type.NUMBER))
        .clientScope(childFence("x.ddn"))
        .orderBy("x.logdtl_id")
        .maxRows(1)
        .build();
  }

  /** P557 / P657 Log Defects (LOG_TALLY_DETAIL_DEFECT / SAMPLE_TALLY_DETAIL_DEFECT). */
  static QueryDefinition logDefects() {
    return QueryDefinition.builder("detailReturns.logDefects")
        .legacy("P557/P657 (read side of P559/P659)")
        .capability(Capability.SCALE_RETURNS_VIEW)
        .sql("""
            SELECT x.* FROM (
              SELECT 'P' AS log_type, l.detail_document_number AS ddn, f.logdtl_id, f.log_defect_id,
                     f.defect_number, f.defect_type, f.volume, f.descriptor
                FROM log_tally_detail_defect f JOIN log_tally_detail l ON l.logdtl_id = f.logdtl_id
              UNION ALL
              SELECT 'S', l.detail_document_number, f.logdtl_id, f.log_defect_id,
                     f.defect_number, f.defect_type, f.volume, f.descriptor
                FROM sample_tally_detail_defect f JOIN sample_tally_detail l ON l.logdtl_id = f.logdtl_id
            ) x WHERE 1=1""")
        .filter(QueryFilter.required("logType", "AND x.log_type = :logType", Type.UPPER))
        .filter(QueryFilter.required("logDtlId", "AND x.logdtl_id = :logDtlId", Type.NUMBER))
        .clientScope(childFence("x.ddn"))
        .orderBy("x.defect_number")
        .maxRows(100)
        .build();
  }

  /** Detail segregation roll-up (LOG_TALLY / SAMPLE_TALLY _DTL_SEGREGATION, SFP_TALLY_SEGREGATION). */
  static QueryDefinition segregations() {
    return QueryDefinition.builder("detailReturns.segregations")
        .legacy("P555 (mock-up) / *_DTL_SEGREGATION")
        .capability(Capability.SCALE_RETURNS_VIEW)
        .sql("""
            SELECT x.ddn, x.version, x.scale_species_code AS species, sp.description AS species_desc,
                   x.scale_product_code AS product, x.scale_grade_code AS grade, x.log_count, x.volume
              FROM (
                SELECT detail_document_number AS ddn, version, scale_species_code, scale_product_code,
                       scale_grade_code, log_count, volume FROM log_tally_dtl_segregation
                UNION ALL
                SELECT detail_document_number, version, scale_species_code, scale_product_code,
                       scale_grade_code, log_count, volume FROM sample_tally_dtl_segregation
                UNION ALL
                SELECT detail_document_number, version, scale_species_code, scale_product_code,
                       scale_grade_code, TO_NUMBER(NULL), volume FROM sfp_tally_segregation
              ) x
              LEFT JOIN scale_species_code sp ON sp.scale_species_code = x.scale_species_code
             WHERE 1=1""")
        .filter(ddn("x.ddn"))
        .filter(version("x.version"))
        .clientScope(childFence("x.ddn"))
        .orderBy("x.scale_species_code, x.scale_product_code, x.scale_grade_code")
        .maxRows(500)
        .build();
  }

  /**
   * Transaction History (DetailScaleTransactionsTag): Seq No., Sign, Type, Date
   * Summarized (HBS_SCALE_RETURN.ENTRY_TIMESTAMP of the SDN), SDN, Volume / Value
   * summed from the *_TXN_SEG rows (sample tallies carry volume only).
   */
  static QueryDefinition transactions() {
    return QueryDefinition.builder("detailReturns.transactions")
        .legacy("P552/P553/P602/P603/P652/P653/P752/P753 Transaction History")
        .capability(Capability.SCALE_RETURNS_VIEW)
        .sql("""
            SELECT x.ddn, x.version, x.dtl_txn_id, x.event_sequence, x.debit_credit_type,
                   x.hbs_txn_type_code AS txn_type, ttc.description AS txn_type_desc,
                   x.hbs_txn_status_code AS txn_status, sr.entry_timestamp AS date_summarized,
                   x.document_control_number AS sdn, x.summary_document_version AS sdn_version,
                   x.volume, x.value
              FROM (
                SELECT t.detail_document_number AS ddn, t.detail_document_version AS version, t.dtl_txn_id,
                       t.event_sequence, t.debit_credit_type, t.hbs_txn_type_code, t.hbs_txn_status_code,
                       t.document_control_number, t.summary_document_version,
                       (SELECT SUM(s.prorated_volume) FROM log_tally_txn_seg s WHERE s.dtl_txn_id = t.dtl_txn_id) AS volume,
                       (SELECT SUM(s.prorated_value) FROM log_tally_txn_seg s WHERE s.dtl_txn_id = t.dtl_txn_id) AS value
                  FROM log_tally_txn t
                UNION ALL
                SELECT t.detail_document_number, t.detail_document_version, t.dtl_txn_id,
                       t.event_sequence, t.debit_credit_type, t.hbs_txn_type_code, t.hbs_txn_status_code,
                       t.document_control_number, t.summary_document_version,
                       (SELECT SUM(s.prorated_volume) FROM weigh_slip_txn_seg s WHERE s.dtl_txn_id = t.dtl_txn_id),
                       (SELECT SUM(s.prorated_value) FROM weigh_slip_txn_seg s WHERE s.dtl_txn_id = t.dtl_txn_id)
                  FROM weigh_slip_txn t
                UNION ALL
                SELECT t.detail_document_number, t.detail_document_version, t.dtl_txn_id,
                       t.event_sequence, t.debit_credit_type, t.hbs_txn_type_code, t.hbs_txn_status_code,
                       t.document_control_number, t.summary_document_version,
                       (SELECT SUM(s.prorated_volume) FROM sample_tally_txn_seg s WHERE s.dtl_txn_id = t.dtl_txn_id),
                       TO_NUMBER(NULL)
                  FROM sample_tally_txn t
                UNION ALL
                SELECT t.detail_document_number, t.detail_document_version, t.dtl_txn_id,
                       t.event_sequence, t.debit_credit_type, t.hbs_txn_type_code, t.hbs_txn_status_code,
                       t.document_control_number, t.summary_document_version,
                       (SELECT SUM(s.prorated_volume) FROM sfp_tally_txn_seg s WHERE s.dtl_txn_id = t.dtl_txn_id),
                       (SELECT SUM(s.prorated_value) FROM sfp_tally_txn_seg s WHERE s.dtl_txn_id = t.dtl_txn_id)
                  FROM sfp_tally_txn t
              ) x
              LEFT JOIN hbs_txn_type_code ttc ON ttc.hbs_txn_type_code = x.hbs_txn_type_code
              LEFT JOIN hbs_scale_return sr ON sr.document_control_number = x.document_control_number
             WHERE 1=1""")
        .filter(ddn("x.ddn"))
        .filter(version("x.version"))
        .clientScope(childFence("x.ddn"))
        .orderBy("x.event_sequence, x.dtl_txn_id")
        .maxRows(500)
        .build();
  }

  @Override
  public List<QueryDefinition> queries() {
    return List.of(version(), versions(), errors(), logs(), log(), logDefects(), segregations(),
        transactions());
  }
}
