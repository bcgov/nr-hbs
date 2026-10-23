package ca.bc.gov.nrs.hbs.api.submissions;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads behind the P280 / P283 batch-slip workflows and the P041 / P024 /
 * P901 / P911 criteria checks (reference cross-references of the legacy
 * Struts validators). Bound parameters only.
 */
@Component
public class BatchSlipReads {

  private final NamedParameterJdbcTemplate jdbc;

  public BatchSlipReads(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private boolean exists(String sql, MapSqlParameterSource p) {
    Integer n = jdbc.queryForObject(sql, p, Integer.class);
    return n != null && n > 0;
  }

  /** PaperBatchQuery.selectByPK (all columns, for HBS_STORE_HBS_PAPER_BATCH). */
  public Map<String, Object> paperBatch(long batchId) {
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM hbs_paper_batch WHERE batch_id = :id",
        new MapSqlParameterSource("id", batchId));
    return rows.isEmpty() ? null : rows.get(0);
  }

  /** P283 "Documents Received": SummaryScaleReturnQuery.selectCountByVolumeEstBatchId. */
  public long documentsReceived(long batchId) {
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM hbs_scale_return WHERE volest_batch_id = :id",
        new MapSqlParameterSource("id", batchId), Long.class);
    return n == null ? 0 : n;
  }

  /** HBSValidator.validateScaleSiteNoCrossRef (InvoiceHelper.findScaleSite). */
  public boolean scaleSiteExists(String scaleSite) {
    return exists("SELECT COUNT(*) FROM scale_site WHERE scale_site_id_nmbr = :site",
        new MapSqlParameterSource("site", scaleSite));
  }

  /** The scale site belongs to the client (industry fence of the scale-control searches). */
  public boolean scaleSiteOwnedBy(String scaleSite, String clientNumber) {
    return exists("SELECT COUNT(*) FROM scale_site WHERE scale_site_id_nmbr = :site AND owner_cli_number = :client",
        new MapSqlParameterSource("site", scaleSite).addValue("client", clientNumber));
  }

  /** HBSValidator.validateRegionDistrict (OrgUnitHelper.findOrgUnit). */
  public boolean orgUnitExists(long orgUnitNo) {
    return exists("SELECT COUNT(*) FROM org_unit WHERE org_unit_no = :ou",
        new MapSqlParameterSource("ou", orgUnitNo));
  }

  /** AnomalyManagerBean.findPrimaryLicense: {@code select license_number from quant_license where license_number=?}. */
  public boolean licenceExists(String licence) {
    return exists("SELECT COUNT(*) FROM quant_license WHERE license_number = :lic",
        new MapSqlParameterSource("lic", licence));
  }
}
