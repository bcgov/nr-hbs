package ca.bc.gov.nrs.hbs.api.detailreturns;

import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The user's data domains and scaler keys, as the legacy authority checks read
 * them by HBS user id ({@code IDIR\NAME} / {@code BCEID\NAME}):
 * HBS_USER_SITE_DATA_DOMAIN ({@code UssHelper2.findSiteDataDomainByPrimaryKey}),
 * HBS_USER_DIST_DATA_DOMAIN ({@code DistrictDataDomainEntityHome.findByCriteria}),
 * HBS_SCALER_AUTH_KEY joined to QUANT_LICENSE by client
 * ({@code HbsScalerAuthKeyQuery.selectByCriteria} with a licence and no date),
 * SCALE_SITE.ORG_UNIT_NO and the mark's forest district (MARK_BILLED_CLI).
 */
public interface UserDomains {

  boolean hasSiteDomain(String userId, String scaleSiteId);

  Set<Long> districtDomains(String userId);

  /** At least one scaler auth key of the user whose client holds the licence. */
  boolean hasScalerKeyForLicence(String userId, String licenseNumber);

  Long scaleSiteOrgUnit(String scaleSiteId);

  /** {@code InvoiceHelper.findMark(...).getForestDistrict()} (null when the mark is unknown). */
  Long markDistrict(String timberMark);

  /**
   * The industry fence of the detail views ({@code DetailReturnsSql.CLIENT_FENCE},
   * legacy {@code DetailScaleDocumentHelper.isDetailDocViewableByClient}): the
   * client owns the scale site, holds the mark, owns the stratum or holds the
   * primary scaler licence.
   */
  /**
   * {@code ClientProfileManagerBean.findDetailSubmittersByScaleSiteAndReturnCodeAndScaleDate}
   * (TradingPartnerSubmitterQuery.selectUnexpiredDetailSubmittersSql): an
   * unexpired detail SCALE_SITE_PROFILE for the site and return type on the date
   * (an SFP tally uses the log tally profile).
   */
  boolean hasDetailSiteProfile(String scaleSite, String returnTypeCode, java.util.Date date);

  boolean visibleToClient(String client, String scaleSite, String timberMark, String population, String stratum,
                          Long samplingYear, String primaryLicence);

  @Component
  class Jdbc implements UserDomains {

    private final NamedParameterJdbcTemplate jdbc;

    public Jdbc(JdbcTemplate jdbc) {
      this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public boolean hasSiteDomain(String userId, String scaleSiteId) {
      Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM hbs_user_site_data_domain"
              + " WHERE user_id = :userId AND scale_site_id_nmbr = :site",
          new MapSqlParameterSource("userId", userId).addValue("site", scaleSiteId), Integer.class);
      return n != null && n > 0;
    }

    @Override
    public Set<Long> districtDomains(String userId) {
      return Set.copyOf(jdbc.queryForList("SELECT org_unit_no FROM hbs_user_dist_data_domain WHERE user_id = :userId",
          new MapSqlParameterSource("userId", userId), Long.class));
    }

    @Override
    public boolean hasScalerKeyForLicence(String userId, String licenseNumber) {
      Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM hbs_scaler_auth_key"
              + " WHERE user_id = :userId"
              + " AND client_number IN (SELECT client_number FROM quant_license WHERE license_number = :licence)",
          new MapSqlParameterSource("userId", userId).addValue("licence", licenseNumber), Integer.class);
      return n != null && n > 0;
    }

    @Override
    public Long scaleSiteOrgUnit(String scaleSiteId) {
      return jdbc.queryForList("SELECT org_unit_no FROM scale_site WHERE scale_site_id_nmbr = :site",
          new MapSqlParameterSource("site", scaleSiteId), Long.class).stream().findFirst().orElse(null);
    }

    @Override
    public Long markDistrict(String timberMark) {
      return jdbc.queryForList("SELECT MAX(forest_district) FROM mark_billed_cli WHERE timber_mark = :mark",
          new MapSqlParameterSource("mark", timberMark), Long.class).stream().findFirst().orElse(null);
    }

    @Override
    public boolean hasDetailSiteProfile(String scaleSite, String returnTypeCode, java.util.Date date) {
      String type = "F".equals(returnTypeCode) ? "P" : returnTypeCode;
      Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM scale_site_profile"
              + " WHERE scale_site_id_nmbr = :site AND hbs_return_type_code = :type AND doc_type = 'D'"
              + " AND (CAST(:day AS DATE) IS NULL OR (:day >= effective_date AND :day <= expiry_date))",
          new MapSqlParameterSource("site", scaleSite).addValue("type", type)
              .addValue("day", date == null ? null : new java.sql.Timestamp(date.getTime()), java.sql.Types.TIMESTAMP),
          Integer.class);
      return n != null && n > 0;
    }

    @Override
    public boolean visibleToClient(String client, String scaleSite, String timberMark, String population,
                                   String stratum, Long samplingYear, String primaryLicence) {
      Integer n = jdbc.queryForObject("""
          SELECT COUNT(*) FROM dual
           WHERE :client IN (
                 (SELECT ss.owner_cli_number FROM scale_site ss WHERE ss.scale_site_id_nmbr = :site),
                 (SELECT MAX(m.client_number) FROM mark_billed_cli m WHERE m.timber_mark = :mark),
                 (SELECT MAX(st.client_number) FROM stratum st WHERE st.population_number = :pop
                     AND st.stratum_number = :stratum AND st.sampling_year = :year),
                 (SELECT MAX(ql.client_number) FROM quant_license ql WHERE ql.license_number = :licence))""",
          new MapSqlParameterSource("client", client).addValue("site", scaleSite).addValue("mark", timberMark)
              .addValue("pop", population).addValue("stratum", stratum).addValue("year", samplingYear)
              .addValue("licence", primaryLicence), Integer.class);
      return n != null && n > 0;
    }
  }
}
