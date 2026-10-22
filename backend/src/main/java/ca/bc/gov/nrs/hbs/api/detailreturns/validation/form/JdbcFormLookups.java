package ca.bc.gov.nrs.hbs.api.detailreturns.validation.form;

import ca.bc.gov.nrs.hbs.api.detailreturns.DetailAuthority;
import ca.bc.gov.nrs.hbs.api.detailreturns.DetailAuthority.Caller;
import ca.bc.gov.nrs.hbs.api.detailreturns.UserDomains;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.DetailValidator;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.TallyLookups;
import java.time.LocalDate;
import java.util.Date;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link FormLookups} over Oracle. Marks, sites, strata and species/product/grade
 * reuse the detail-return lookups ({@link ReferenceData}, {@link TallyLookups});
 * the caller's domains reuse {@link DetailAuthority} / {@link UserDomains}. Only
 * the lookups the form layer does differently have their own SQL here (bound
 * parameters only).
 */
@Component
public class JdbcFormLookups implements FormLookups {

  /**
   * {@code DacHelper.isValidLicence(licence)} = {@code isValidLicence(licence, "S") || (licence, "R")}
   * ({@code QuantLicenseManager.isValidLicense}: QUANT_LICENSE by primary key).
   */
  static final String SCALER_LICENCE_EXISTS = """
      SELECT COUNT(*) FROM quant_license
       WHERE license_number = :licence
         AND quant_lic_type_cd IN ('S', 'R')
      """;

  /** {@code CodeReader.getMessageCode(code, category)} (HBS_EDIT_ERR_MESSAGE_CODE by primary key). */
  static final String EDIT_ERROR_DESCRIPTION = """
      SELECT description FROM hbs_edit_err_message_code
       WHERE hbs_edit_err_message_code = :code
         AND hbs_edit_err_category_code = :category
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final ReferenceData ref;
  private final TallyLookups tally;
  private final UserDomains domains;
  private final DetailAuthority authority;

  public JdbcFormLookups(JdbcTemplate jdbc, ReferenceData ref, TallyLookups tally, UserDomains domains,
                         DetailAuthority authority) {
    this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    this.ref = ref;
    this.tally = tally;
    this.domains = domains;
    this.authority = authority;
  }

  @Override
  public boolean timberMarkExists(String timberMark) {
    return ref.timberMark(upper(timberMark)).isPresent();
  }

  @Override
  public boolean scaleSiteExists(String scaleSiteId) {
    return ref.scaleSite(upper(scaleSiteId)).isPresent();
  }

  @Override
  public boolean scalerLicenceExists(String licenseNumber) {
    Integer n = jdbc.queryForObject(SCALER_LICENCE_EXISTS,
        new MapSqlParameterSource("licence", upper(licenseNumber)), Integer.class);
    return n != null && n > 0;
  }

  @Override
  public boolean stratumExists(String population, String stratum, Long samplingYear) {
    if (population == null || stratum == null || samplingYear == null) {
      return false; // StratumNotFoundException / NumberFormatException → invalid
    }
    return ref.stratum(population, stratum, samplingYear).isPresent();
  }

  @Override
  public boolean isBillableMark(String timberMark) {
    return ref.timberMark(timberMark)
        .map(m -> !m.isCashSale() && !m.isCruiseBased() && !m.isAreaBased()).orElse(false);
  }

  @Override
  public boolean speciesProductGradeExists(String species, String product, String grade) {
    return tally.speciesProductGrade(product, grade, species).isPresent();
  }

  /**
   * {@code DetailManagerBean.findActiveVersion(ddn, type)} ({@code LogTallyQuery.selectActiveLogTally}
   * and its siblings: {@code detail_document_number = ? AND active_version_ind = 'Y'},
   * a single row expected). The table name comes from the {@link DocType} constant.
   */
  @Override
  public boolean activeDocumentExists(String ddn, DocType type) {
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + type.table()
            + " WHERE detail_document_number = :ddn AND active_version_ind = 'Y'",
        new MapSqlParameterSource("ddn", upper(ddn)), Integer.class);
    return n != null && n == 1;
  }

  @Override
  public boolean hasDetailSiteProfile(String scaleSiteId, DocType type, Date scaleDate) {
    return domains.hasDetailSiteProfile(upper(scaleSiteId), type.code(), scaleDate);
  }

  @Override
  public String editErrorDescription(String code, String category) {
    return jdbc.queryForList(EDIT_ERROR_DESCRIPTION,
            new MapSqlParameterSource("code", code).addValue("category", category), String.class)
        .stream().findFirst().orElse(null);
  }

  @Override
  public Caller caller() {
    return Caller.current();
  }

  @Override
  public boolean isScaleSiteAssociatedWithOrgUnit(String scaleSiteId, Caller caller) {
    return authority.isScaleSiteAssociatedWithOrgUnit(scaleSiteId, caller);
  }

  @Override
  public boolean hasScaleSiteAuthorization(String scaleSiteId, Caller caller) {
    return authority.hasScaleSiteAuthorization(scaleSiteId, caller);
  }

  @Override
  public boolean hasScalerAuthorization(Caller caller, String... licences) {
    return authority.hasScalerAuthorization(caller, licences);
  }

  @Override
  public LocalDate today() {
    return DetailValidator.today();
  }

  private static String upper(String s) {
    return s == null ? null : s.toUpperCase(Locale.ROOT);
  }
}
