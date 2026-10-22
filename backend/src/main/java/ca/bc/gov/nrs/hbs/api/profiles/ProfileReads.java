package ca.bc.gov.nrs.hbs.api.profiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * The reads the ported profile rules make (the legacy finders and
 * {@code *Query} classes they called), behind an interface so the rules are
 * unit-tested without a database. Rows are {@code SELECT *} maps keyed by
 * lower-case column name; DATE columns come back as {@link java.sql.Timestamp}.
 * {@link JdbcProfileReads} is the Oracle implementation.
 */
public interface ProfileReads {

  /** An existing profile's effective / expiry range. */
  record Range(LocalDateTime start, LocalDateTime end) {}

  /** The first profile at or after a date ({@code getNext*Profile}). */
  record Next(Long id, LocalDateTime effective) {}

  // ---------------------------------------------------------------- scale sites (P322/P323)

  /** SCALE_SITE row (legacy {@code ScaleSiteReader.getScaleSite}), or null. */
  Map<String, Object> scaleSite(String scaleSiteId);

  /** HBS_USER_SITE_DATA_DOMAIN has (user, site) — {@code UssHelper2.findSiteDataDomainByPrimaryKey}. */
  boolean siteDataDomain(String userId, String scaleSiteId);

  /** HBS_USER_DIST_DATA_DOMAIN org units of the user — {@code DacHelper.getDistricts}. */
  List<Long> districtDataDomains(String userId);

  /** TRADING_PARTNER (client, location) exists — {@code findTradingPartnerByPrimaryKey}. */
  boolean tradingPartnerExists(String clientNumber, String clientLocnCode);

  /**
   * Ranges of the SCALE_SITE_PROFILE (detail) and TRADING_PARTNER_SUBMITTER
   * rows for the site / return type / doc type, narrowed by client and
   * location when given — {@code TradingPartnerSubmitterQuery.selectByCriteria}.
   */
  List<Range> scaleSiteProfileRanges(String scaleSiteId, String returnType, String docType,
      String clientNumber, String clientLocnCode);

  /** Effective date of the next profile after {@code effective} — {@code TradingPartnerSubmitterQuery.getNextEffectiveDate}. */
  LocalDateTime nextScaleSiteProfile(String scaleSiteId, String returnType, String docType,
      String clientNumber, String clientLocnCode, LocalDateTime effective);

  /** TRADING_PARTNER_SUBMITTER row, or null. */
  Map<String, Object> tradingPartnerSubmitter(long tpsId);

  // ---------------------------------------------------------------- population (P332/P333)

  /** POPULATION row ({@code SmpHelper.findPopulationByPK}), or null. */
  Map<String, Object> population(String populationNumber, long samplingYear);

  /** POPULATION_PROFILE row plus the population's client_number, or null. */
  Map<String, Object> populationProfile(long popprofId);

  /**
   * Profiles of the population/year whose range touches [effective, expiry]
   * (day precision), other than {@code excludeId} — {@code PopulationProfileQuery.countedByCriteria}.
   */
  int overlappingPopulationProfiles(String populationNumber, long samplingYear, LocalDate effective,
      LocalDate expiry, Long excludeId);

  // ---------------------------------------------------------------- mark holder (P342–P344)

  /** CLIENT_LOCATION exists — {@code ClientReader.isValidClientLocation}. */
  boolean clientLocationExists(String clientNumber, String clientLocnCode);

  /** HBS_MARK_HOLDER_PROFILE ranges for client / location / return type. */
  List<Range> markHolderRanges(String clientNumber, String clientLocnCode, String returnType);

  /** Effective date of the next mark holder profile after {@code effective}. */
  LocalDateTime nextMarkHolderProfile(String clientNumber, String clientLocnCode, String returnType,
      LocalDateTime effective);

  Map<String, Object> markHolderProfile(long mhprofId);

  // ---------------------------------------------------------------- statement delivery (P372/P373)

  List<Range> deliveryRanges(String clientNumber, String clientLocnCode);

  /** The first delivery profile whose effective date is the earliest one on or after {@code effective}. */
  Next nextDeliveryProfile(String clientNumber, String clientLocnCode, LocalDateTime effective);

  /** Users with the location in their client-location data domain. */
  int documentReceivers(String clientNumber, String clientLocnCode);

  /** Of those, the ones that are HBS users with status ACT. */
  int activeDocumentReceivers(String clientNumber, String clientLocnCode);

  Map<String, Object> deliveryProfile(long dlvprofId);

  // ---------------------------------------------------------------- cruise based (P383)

  /** APP_WORKSHEET row of the appraisal (appraisal_mthd_cd, int_deciduous_vol …), or null. */
  Map<String, Object> appraisal(String timberMark, LocalDate effectiveDate);

  /** APP_SPECIES.HDBS_TREE_SPECIES of the appraisal. */
  List<String> appraisedSpecies(String timberMark, LocalDate effectiveDate);

  /** SPECIES_PRODUCT_GRADE_XREF has the species / grade (any product) — {@code isValidSPGCombination}. */
  boolean validSpeciesGrade(String speciesCode, String gradeCode);

  /** CRUISE_BASED_SP_GRD_RATIO row, or null. */
  Map<String, Object> ratio(String timberMark, LocalDate effectiveDate, String speciesCode, String gradeCode);
}
