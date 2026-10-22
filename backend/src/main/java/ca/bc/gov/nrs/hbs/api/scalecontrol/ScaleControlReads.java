package ca.bc.gov.nrs.hbs.api.scalecontrol;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads behind the scale-control workflows (P908 / P954). Bound parameters only. */
@Component
public class ScaleControlReads {

  private final NamedParameterJdbcTemplate jdbc;

  public ScaleControlReads(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private Map<String, Object> one(String sql, MapSqlParameterSource p) {
    List<Map<String, Object>> rows = jdbc.queryForList(sql, p);
    return rows.isEmpty() ? null : rows.get(0);
  }

  /** ScaleAnomalyLogEntity by PK (all columns, for HBS_STORE_SCALE_ANOMALY_LOG). */
  public Map<String, Object> anomaly(long anomalyId) {
    return one("SELECT * FROM scale_anomaly_log WHERE anomaly_id = :id", new MapSqlParameterSource("id", anomalyId));
  }

  /**
   * The scale site of the anomaly's document ({@code Doc.version.scaleSiteIdNmbr}
   * on P912–P953), through the same FK pairs as ScaleControlCatalog.ANOMALY_SQL.
   */
  public String anomalyScaleSite(long anomalyId) {
    List<String> rows = jdbc.queryForList("""
        SELECT COALESCE(
                 (SELECT lt.scale_site_id_nmbr FROM log_tally lt
                   WHERE lt.detail_document_number = sa.log_tly_dtl_doc_no AND lt.version = sa.log_tly_version),
                 (SELECT slt.scale_site_id_nmbr FROM sample_log_tally slt
                   WHERE slt.detail_document_number = sa.smpl_tly_dtl_doc_no AND slt.version = sa.smpl_tly_version),
                 (SELECT ws.scale_site_id_nmbr FROM weigh_slip ws
                   WHERE ws.detail_document_number = sa.ws_dtl_doc_no AND ws.version = sa.ws_version),
                 (SELECT sl.scale_site_id_nmbr FROM scl_site_arrival_ledger sl
                   WHERE sl.detail_document_number = sa.arrvl_ldgr_dtl_doc_no AND sl.version = sa.arrvl_ldgr_version),
                 (SELECT dl.scale_site_id_nmbr FROM scl_site_departure_ledger dl
                   WHERE dl.detail_document_number = sa.deprt_ldgr_dtl_doc_no AND dl.version = sa.deprt_ldgr_version))
          FROM scale_anomaly_log sa
         WHERE sa.anomaly_id = :id""", new MapSqlParameterSource("id", anomalyId), String.class);
    return rows.isEmpty() ? null : rows.get(0);
  }

  /**
   * DacHelper.isScaleSiteAssociatedWithOrgUnit for HBS_SCALE_ADMIN: the
   * site's ORG_UNIT_NO is one of the user's HBS_USER_DIST_DATA_DOMAIN districts.
   */
  public boolean siteInUserDistricts(String scaleSite, String userId) {
    Integer n = jdbc.queryForObject("""
        SELECT COUNT(*)
          FROM scale_site s, hbs_user_dist_data_domain d
         WHERE s.scale_site_id_nmbr = :site
           AND d.org_unit_no = s.org_unit_no
           AND d.user_id = :userId""",
        new MapSqlParameterSource("site", scaleSite).addValue("userId", userId), Integer.class);
    return n != null && n > 0;
  }

  /** AnomalyAssessmentControlEntity by PK (all columns). */
  public Map<String, Object> assessmentControl(String scaleControlTypeCode) {
    return one("SELECT * FROM anomaly_assessment_control WHERE scale_control_type_code = :type",
        new MapSqlParameterSource("type", scaleControlTypeCode));
  }
}
