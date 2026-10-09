package ca.bc.gov.nrs.hbs.api.catalog;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.query.QueryCatalog;
import ca.bc.gov.nrs.hbs.api.query.QueryDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter.Type;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scale Control (legacy tab P900) — Scale Return Change Log (P961/P962) and
 * Scale Site Software Use (P991/P992/P993).
 *
 * <p>Change log SQL is ported from {@code AuditDetailQuery.selectByAuditCriteria}
 * / {@code AuditManager2Impl.findAuditDetails} (hbs-ejb infrastructure/audit) and
 * the P962.jsp row rendering; HBS2R962 is the matching report. Software use SQL
 * is ported from the HBS3R992 / HBS3R993 REF-CURSOR procs (their dynamic SELECTs
 * rebuilt with bound filters).
 */
@Component
public class ScaleControlAuditCatalog implements QueryCatalog {

  // ---------------------------------------------------------------- change log

  /**
   * One row per HBS_SCALE_RETURN_AUDIT field change (or one row per HBS_AUDIT
   * event without field detail — outer join, as legacy BT11741). The
   * document number / version are unrolled from OBJECT_OLD_ID ("doc,version")
   * exactly as P962.jsp did; the Field column reproduces the JSP's
   * "Insert Segregation \"Field\"" decoration for child-object changes.
   */
  private static final String CHANGE_LOG_SELECT = """
      SELECT a.audit_id,
             CASE WHEN INSTR(a.object_old_id, ',') > 0
                  THEN SUBSTR(a.object_old_id, 1, INSTR(a.object_old_id, ',') - 1)
                  ELSE 'n/a' END AS document_number,
             CASE WHEN INSTR(a.object_old_id, ',') > 0
                  THEN SUBSTR(a.object_old_id, INSTR(a.object_old_id, ',') + 1)
                  ELSE 'n/a' END AS version_number,
             a.audit_date AS change_date,
             a.user_id,
             a.user_id_recording_actions_for,
             a.hbs_object_type_code AS return_format,
             NVL(atc.description, a.action_type_code) AS action,
             CASE WHEN b.field_name IS NULL THEN NULL
                  WHEN b.hbs_object_type_code <> a.hbs_object_type_code
                  THEN TRIM(CASE WHEN b.action_type_code IN ('INS', 'DEL')
                                 THEN NVL(batc.description, b.action_type_code) END
                            || ' ' || NVL(otc.description, b.hbs_object_type_code))
                       || ' "' || b.field_name || '"'
                  ELSE b.field_name END AS field,
             b.old_value AS previous_value,
             b.new_value AS current_value
        FROM hbs_audit a
        LEFT JOIN hbs_scale_return_audit b ON b.audit_id = a.audit_id
        LEFT JOIN action_type_code atc ON atc.action_type_code = a.action_type_code
        LEFT JOIN action_type_code batc ON batc.action_type_code = b.action_type_code
        LEFT JOIN hbs_object_type_code otc ON otc.hbs_object_type_code = b.hbs_object_type_code
       WHERE a.hbs_object_type_code IN ('SSR', 'DSR')""";

  /** Legacy "Pop/Strat/Year" radio expanded to three audited fields (ScaleReturnAuditsTag). */
  private static final String FIELD_NAME_FILTER = """
      AND (b.field_name = :fieldName
           OR (:fieldName = 'Pop_Strat_Year'
               AND b.field_name IN ('Population_Number', 'Sampling_Year', 'Stratum_Number')))""";

  private static QueryDefinition.Builder changeLogSorts(QueryDefinition.Builder b) {
    return b.sort("changeDate", "a.audit_date")
        .sort("userId", "a.user_id")
        .sort("documentNumber", "a.object_old_id")
        .sort("field", "b.field_name")
        .orderBy("a.audit_id DESC, b.field_name");
  }

  // ------------------------------------------------------------- software use

  /**
   * Active-version detail returns (log tally, weigh slip, sample tally) with the
   * software that submitted them — the shared FROM of HBS3R992 / HBS3R993
   * (DETAIL_SCALE_DOCUMENT + DTL_DOC_BATCH_DOCUMENT + DTL_DOC_BATCH + the
   * LOG_TALLY / WEIGH_SLIP / SAMPLE_LOG_TALLY union "subSelB"/"subData").
   */
  private static final String SOFTWARE_USAGE = """
      SELECT DISTINCT dtlDocBatch.scale_site_id_nmbr AS site_id,
             subSelB.scale_date,
             dtlBatDoc.software_product AS product,
             dtlBatDoc.software_version AS version,
             dtlBatDoc.software_revision AS revision,
             typeCode.description,
             subSelB.ddn,
             subSelB.ddn_ver
        FROM detail_scale_document dtlSclDoc
        JOIN detail_scale_doc_type_code typeCode
          ON typeCode.detail_scale_doc_type_code = dtlSclDoc.detail_scale_doc_type_code
        JOIN dtl_doc_batch_document dtlBatDoc
          ON dtlBatDoc.detail_document_number = dtlSclDoc.detail_document_number
        JOIN dtl_doc_batch dtlDocBatch
          ON dtlDocBatch.batch_id = dtlBatDoc.batch_id
        JOIN (SELECT lt.scale_date, lt.detail_document_number AS ddn, lt.version AS ddn_ver
                FROM log_tally lt WHERE lt.active_version_ind = 'Y'
              UNION
              SELECT ws.scale_date, ws.detail_document_number, ws.version
                FROM weigh_slip ws WHERE ws.active_version_ind = 'Y'
              UNION
              SELECT slt.scale_date, slt.detail_document_number, slt.version
                FROM sample_log_tally slt WHERE slt.active_version_ind = 'Y') subSelB
          ON subSelB.ddn = dtlBatDoc.detail_document_number""";

  /**
   * SPC_SFTWR_VNDR users only see their own products (legacy SoftwareProductsTag
   * → ManagerBean.getSoftwareProductByClient: SCALING_PROGRAM by client number +
   * location). Enforced server-side here; the legacy app only filtered the
   * dropdown. Location code is not on the token, so the fence is by client number.
   */
  private static final String VENDOR_FENCE = """
      AND (:hbsUserType <> 'SPC'
           OR UPPER(%s) IN (SELECT UPPER(sp.scl_program_name) FROM scaling_program sp
                             WHERE sp.client_number = :hbsViewerClient))""";

  private static final String VENDOR_NAME = """
      CASE WHEN :hbsIsMinistry = 'Y' OR fc.client_type_code <> 'I'
                OR fc.client_number = :hbsViewerClient
           THEN fc.client_name ELSE 'Not Releasable' END""";

  /**
   * HBS3R992's four UNION branches (Product / Version / Revision Not on File,
   * Version not Valid), rewritten as NOT EXISTS checks. The legacy proc's
   * cartesian "NOT IN" branches flagged every use of a product that had more
   * than one version/revision, and its certification-date test
   * ({@code >= eff OR <= exp}) was always true; this port implements the stated
   * intent instead (see docs/areas/scale-control.md).
   */
  private static final String SOFTWARE_ANOMALIES = """
      SELECT x.site_id AS scale_site, x.scale_date, x.vendor AS software_vendor,
             x.product AS software_product, x.version AS software_version,
             x.revision AS software_revision, x.reason, x.description AS return_type,
             x.ddn AS document_control_no, x.ddn_ver AS document_version
        FROM (
          WITH usage AS (""" + SOFTWARE_USAGE + """
          )
          SELECT u.*, 'Product Not on File' AS reason, CAST(NULL AS VARCHAR2(60)) AS vendor
            FROM usage u
           WHERE u.product IS NOT NULL
             AND NOT EXISTS (SELECT 1 FROM scaling_program prog
                              WHERE UPPER(prog.scl_program_name) = UPPER(u.product))
          UNION
          SELECT u.*, 'Version Not on File', """ + VENDOR_NAME + """

            FROM usage u
            JOIN scaling_program prog ON UPPER(prog.scl_program_name) = UPPER(u.product)
            JOIN v_client_public fc ON fc.client_number = prog.client_number
           WHERE u.version IS NOT NULL
             AND NOT EXISTS (SELECT 1 FROM scl_prg_version ver
                              WHERE ver.scaling_program_no = prog.scaling_program_no
                                AND LTRIM(ver.version, '0') = LTRIM(u.version, '0'))
          UNION
          SELECT u.*, 'Revision Not on File', """ + VENDOR_NAME + """

            FROM usage u
            JOIN scaling_program prog ON UPPER(prog.scl_program_name) = UPPER(u.product)
            JOIN v_client_public fc ON fc.client_number = prog.client_number
            JOIN scl_prg_version ver ON ver.scaling_program_no = prog.scaling_program_no
                                    AND LTRIM(ver.version, '0') = LTRIM(u.version, '0')
           WHERE u.revision IS NOT NULL
             AND NOT EXISTS (SELECT 1 FROM scl_pg_revision rev
                              WHERE rev.scaling_program_no = ver.scaling_program_no
                                AND rev.version = ver.version
                                AND LTRIM(rev.revision_number, '0') = LTRIM(u.revision, '0'))
          UNION
          SELECT u.*, 'Version not Valid', """ + VENDOR_NAME + """

            FROM usage u
            JOIN scaling_program prog ON UPPER(prog.scl_program_name) = UPPER(u.product)
            JOIN v_client_public fc ON fc.client_number = prog.client_number
            JOIN scl_prg_version ver ON ver.scaling_program_no = prog.scaling_program_no
                                    AND LTRIM(ver.version, '0') = LTRIM(u.version, '0')
           WHERE EXISTS (SELECT 1 FROM scl_pg_revision rev
                          WHERE rev.scaling_program_no = ver.scaling_program_no
                            AND rev.version = ver.version
                            AND LTRIM(rev.revision_number, '0') = LTRIM(u.revision, '0'))
             AND (u.scale_date < ver.cert_effective_dt OR u.scale_date > ver.cert_expiry_date)
        ) x
       WHERE 1=1""";

  /**
   * HBS3R993: return count per return type / site / product / version /
   * revision. GROUP BY is expressed as DISTINCT + COUNT(*) OVER so the
   * registry's bound filters (appended after WHERE) apply before counting.
   */
  private static final String SOFTWARE_USE = """
      SELECT DISTINCT u.description AS return_type,
             u.site_id AS scale_site,
             u.product AS software_product,
             u.version AS software_version,
             u.revision AS software_revision,
             COUNT(*) OVER (PARTITION BY u.description, u.site_id, u.product, u.revision, u.version)
               AS return_count
        FROM (""" + SOFTWARE_USAGE.replace("SELECT DISTINCT", "SELECT") + """
        ) u
       WHERE 1=1""";

  private static List<QueryFilter> softwareFilters(String alias) {
    return List.of(
        QueryFilter.required("fromDate", "AND " + alias + ".scale_date >= :fromDate", Type.DATE),
        QueryFilter.required("toDate", "AND " + alias + ".scale_date < :toDate", Type.DATE_TO),
        QueryFilter.upper("scaleSite", "AND " + alias + ".site_id = :scaleSite"),
        QueryFilter.eq("product", "AND UPPER(" + alias + ".product) = UPPER(:product)"),
        QueryFilter.eq("version", "AND " + alias + ".version = :version"),
        QueryFilter.eq("revision", "AND " + alias + ".revision = :revision"));
  }

  @Override
  public List<QueryDefinition> queries() {
    QueryDefinition.Builder changeLogSearch = QueryDefinition.builder("scaleControl.changeLog.search")
        .legacy("P961/P962")
        // Cross-return search: legacy menu link (P900 → P961) was granted to MOF_USER only.
        .capability(Capability.SCALE_CONTROL_MINISTRY_VIEW)
        .sql(CHANGE_LOG_SELECT)
        .filter(QueryFilter.required("returnFormat", "AND a.hbs_object_type_code = :returnFormat", Type.UPPER))
        .filter(QueryFilter.eq("fieldName", FIELD_NAME_FILTER))
        .filter(QueryFilter.required("fromDate", "AND a.audit_date >= :fromDate", Type.DATE))
        .filter(QueryFilter.required("toDate", "AND a.audit_date < :toDate", Type.DATE_TO))
        .filter(QueryFilter.upper("userId", "AND a.user_id = :userId"));

    QueryDefinition.Builder changeLogDocument = QueryDefinition.builder("scaleControl.changeLog.document")
        .legacy("P962")
        // Reached from the summary/detail return views (internal and external users).
        .capability(Capability.SCALE_CONTROL_VIEW)
        .sql(CHANGE_LOG_SELECT)
        .filter(QueryFilter.required("returnFormat", "AND a.hbs_object_type_code = :returnFormat", Type.UPPER))
        // object_new_id = '<doc>,<version>' (AuditDetailQuery.selectByAuditCriteria); each
        // filter binds only its own param.
        .filter(QueryFilter.required("documentNumber",
            "AND a.object_new_id LIKE :documentNumber || ',%'", Type.UPPER))
        .filter(QueryFilter.required("versionNumber",
            "AND SUBSTR(a.object_new_id, INSTR(a.object_new_id, ',') + 1) = :versionNumber", Type.STRING));

    QueryDefinition.Builder anomalies = QueryDefinition.builder("scaleControl.software.anomalies")
        .legacy("P991/P992")
        // "Anomaly List" was hidden from SPC_SFTWR_VNDR (P992 granted to CLI_USER + MOF_USER).
        .capability(Capability.SCALE_CONTROL_VIEW)
        .sql(SOFTWARE_ANOMALIES + "\n" + VENDOR_FENCE.formatted("x.product"))
        .sort("scaleSite", "x.site_id")
        .sort("scaleDate", "x.scale_date")
        .sort("softwareProduct", "x.product")
        .sort("reason", "x.reason")
        .orderBy("x.site_id, x.scale_date, x.product, x.version, x.revision");
    softwareFilters("x").forEach(anomalies::filter);

    QueryDefinition.Builder use = QueryDefinition.builder("scaleControl.software.use")
        .legacy("P991/P993")
        .capability(Capability.SOFTWARE_USE_VIEW)
        .sql(SOFTWARE_USE + "\n" + VENDOR_FENCE.formatted("u.product"))
        .sort("returnType", "u.description")
        .sort("scaleSite", "u.site_id")
        .sort("softwareProduct", "u.product")
        .orderBy("u.description, u.site_id, u.product, u.version, u.revision");
    softwareFilters("u").forEach(use::filter);

    return List.of(
        changeLogSorts(changeLogSearch).build(),
        changeLogSorts(changeLogDocument).build(),
        anomalies.build(),
        use.build(),
        // Software Product dropdown (SoftwareProductsTag): vendors see their own products only.
        QueryDefinition.builder("codes.scaleControl.softwareProducts")
            .legacy("P991 SoftwareProductsTag")
            .capability(Capability.SOFTWARE_USE_VIEW)
            .sql("""
                SELECT DISTINCT sp.scl_program_name AS code, CAST(NULL AS VARCHAR2(1)) AS description
                  FROM scaling_program sp
                 WHERE (:hbsUserType <> 'SPC' OR sp.client_number = :hbsViewerClient)""")
            .orderBy("sp.scl_program_name")
            .maxRows(1000)
            .build());
  }
}
