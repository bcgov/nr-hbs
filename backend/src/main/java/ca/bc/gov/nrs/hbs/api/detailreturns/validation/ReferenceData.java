package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The reference lookups the detail-return validators make, one method per
 * legacy reader call ({@code TimberMarkReader.getTimberMarkByMark},
 * {@code ScaleSiteReader.getScaleSite} / {@code findScaleSiteByTimberBrand},
 * {@code CutBlockManager.findCutBlockByPrimaryKey},
 * {@code QuantLicenseManager.findQuantLicenseByPrimaryKey},
 * {@code SrtsManager.findStratumByPrimaryKey}, {@code ClientReader.findClientLocation},
 * {@code RateManager.hasRate} / {@code findWorksheetsByMarkAndDateAndStatus},
 * {@code SamplePlanner.findPopulationByPrimaryKey},
 * {@code ScaledLogXrefValidator.getGradeScheduleType}). Production:
 * {@link JdbcReferenceData}; unit tests use an in-memory stub.
 *
 * <p>Type-specific lookups live in {@link TallyLookups},
 * {@link WeighSampleLookups} and {@link LedgerLookups}.
 */
public interface ReferenceData {

  /** {@code legacy.ftas.TimberMark} (TIMBER_MARK + MARK_BILLED_CLI), with its legacy predicates. */
  record TimberMarkRef(String timberMark, String fileTypeCode, String cruiseBasedInd, String paymentMethodCode,
                       String clientNumber, String clientLocnCode, Long forestDistrict, Long forestRegion,
                       String forestRegionCode, String quotaTypeCode, String markStatusSt,
                       Date markIssueDate, Date markExpiryDate, Date markExtendDate) {

    public static final String UNREGULATED_BEACHCOMB2 = "BCOMB2";
    public static final String UNREGULATED_BEACHCOMB3 = "BCOMB3";
    public static final String BEACHCOMB_FILETYPE = "B17";
    public static final String OIL_GAS = "A11";
    public static final String PAYMENT_METHOD_CODE_CASH = "C";

    /** {@code TimberMark.Type}. */
    public static final int TYPE_INVALID = -1;
    public static final int TYPE_UNDEFINED = 0;
    public static final int TYPE_CASH_SCALE = 1;
    public static final int TYPE_CRUISE_BASED = 2;
    public static final int TYPE_AREA_BASED = 3;
    public static final int TYPE_PRIVATE = 4;

    public boolean isCashSale() {
      return paymentMethodCode != null && PAYMENT_METHOD_CODE_CASH.equals(paymentMethodCode.trim());
    }

    /**
     * BT11514: cruise-based unless an oil-and-gas (A11) file. Legacy
     * {@code DbMap.flagToBoolean}: exactly "Y" (TIMBER_MARK.CRUISE_BASED_IND is
     * NOT NULL, so the legacy NPE on an empty flag cannot occur).
     */
    public boolean isCruiseBased() {
      return !OIL_GAS.equals(fileTypeCode) && "Y".equals(cruiseBasedInd);
    }

    /** BT5884: an A11 file flagged cruise-based is area-based. */
    public boolean isAreaBased() {
      return OIL_GAS.equals(fileTypeCode) && "Y".equals(cruiseBasedInd);
    }

    public boolean isBeachComb() {
      return BEACHCOMB_FILETYPE.equals(fileTypeCode);
    }

    public boolean isPrivateMark() {
      String t = trim(fileTypeCode);
      return Set.of("B08", "B09", "B13", "B14", "B15", "B16").contains(t);
    }

    public int timberMarkType() {
      if (isCashSale()) return TYPE_CASH_SCALE;
      if (isCruiseBased()) return TYPE_CRUISE_BASED;
      if (isAreaBased()) return TYPE_AREA_BASED;
      if (isPrivateMark()) return TYPE_PRIVATE;
      return TYPE_UNDEFINED;
    }

    private static String trim(String s) {
      return s == null ? "" : s.trim();
    }
  }

  /** {@code legacy.scs.ScaleSite} (SCALE_SITE). */
  record ScaleSiteRef(String scaleSiteIdNmbr, Long orgUnitNo, String ownerCliNumber, String ownerCliLocnCd,
                      String siteName, String timberBrand) {

    /** {@code ScaleSite.SCALE_SITE_WASTE}. */
    public static final String SCALE_SITE_WASTE = "ZZZ";

    /** {@code ScaleSite.isDedicatedCruiseBasedSite}: characters 5-21 of the site name. */
    public boolean isDedicatedCruiseBasedSite() {
      if (siteName == null || siteName.isBlank()) return false;
      try {
        return "Cruise Based Site".equalsIgnoreCase(siteName.substring(4, 21));
      } catch (RuntimeException e) {
        return false;
      }
    }
  }

  /** {@code legacy.cli.ClientLocation}: client status and location expiry. */
  record ClientLocationRef(String clientNumber, String clientLocnCode, String clientStatusCode,
                           String locationExpiredInd) {

    /** {@code Client.isDeactivated}: client status DAC. */
    public boolean isDeactivated() {
      return "DAC".equals(clientStatusCode);
    }

    /** {@code ClientLocation.isExpired}: LOCN_EXPIRED_IND = Y. */
    public boolean isExpired() {
      return "Y".equals(locationExpiredInd);
    }
  }

  /**
   * {@code smp.Stratum} (STRATUM); {@code gradeConstraint} is GRADE_SCHEDULE_CODE
   * ({@code Stratum.getGradeConstraint}). The effective/expiry dates are used by
   * {@code SampleLogTallyValidator} (scale date inside the stratum).
   */
  record StratumRef(String populationNumber, String stratumNumber, Long samplingYear, String clientNumber,
                    String clientLocnCode, String gradeConstraint, Date effectiveDate, Date expiryDate) {

    /** Without dates (tests). */
    public StratumRef(String populationNumber, String stratumNumber, Long samplingYear, String clientNumber,
                      String clientLocnCode, String gradeConstraint) {
      this(populationNumber, stratumNumber, samplingYear, clientNumber, clientLocnCode, gradeConstraint, null, null);
    }
  }

  /**
   * {@code smp.Population} (POPULATION). {@code ratioTypeCode} drives the
   * default-ratio edits ({@code RatioHistories.TypeCode.DEFAULT} = "D"). POPULATION
   * has no status or grade-constraint column: {@code populationStatusCode} and
   * {@code gradeConstraint} are always null from the database.
   */
  record PopulationRef(String populationNumber, Long samplingYear, String clientNumber, String clientLocnCode,
                       String populationStatusCode, String gradeConstraint, String ratioTypeCode) {

    /** {@code RatioHistories.TypeCode.DEFAULT}. */
    public static final String RATIO_TYPE_DEFAULT = "D";

    /** Without ratio type (tests). */
    public PopulationRef(String populationNumber, Long samplingYear, String clientNumber, String clientLocnCode,
                         String populationStatusCode, String gradeConstraint) {
      this(populationNumber, samplingYear, clientNumber, clientLocnCode, populationStatusCode, gradeConstraint, null);
    }

    /** {@code RatioHistories.TypeCode.DEFAULT.equals(population.getRatioTypeCode())}. */
    public boolean isDefaultRatio() {
      return RATIO_TYPE_DEFAULT.equals(ratioTypeCode);
    }
  }

  /** A confirmed appraisal worksheet ({@code plu.AppraisalWorksheet}). */
  record WorksheetRef(String appraisalMthdCd, String conifStandRateEligibilityCode,
                      String decidStandRateEligibilityCode) {
  }

  /** Outcome of {@code RateManager.hasRate} (its exceptions become values). */
  enum RateCheck { HAS_RATE, NO_RATE, OVERLAPPING_RULES, NO_STUMPAGE }

  Optional<TimberMarkRef> timberMark(String timberMark);

  Optional<ScaleSiteRef> scaleSite(String scaleSiteId);

  Optional<ScaleSiteRef> scaleSiteByTimberBrand(String timberBrand);

  /** {@code CutBlockManager.findCutBlockByPrimaryKey(cutBlockId, timberMark)} found a row. */
  boolean cutBlockExists(String cutBlockId, String timberMark);

  /** {@code QuantLicenseManager.findQuantLicenseByPrimaryKey(licence, "S")} found a scaler licence. */
  boolean scalerLicenceExists(String licenseNumber);

  Optional<StratumRef> stratum(String population, String stratum, Long samplingYear);

  Optional<PopulationRef> population(String population, Long samplingYear);

  Optional<ClientLocationRef> clientLocation(String clientNumber, String clientLocnCode);

  /** {@code RateManager.hasRate(mark, startDayToEndDay(scaleDate), fieldScaleDeckId)}. */
  RateCheck rateCheck(String timberMark, Date scaleDate, String fieldScaleDeckId);

  /** {@code RateManager.findWorksheetsByMarkAndDateAndStatus(mark, day(scaleDate), CONFIRMED)}. */
  List<WorksheetRef> confirmedWorksheets(String timberMark, Date scaleDate);

  /**
   * {@code ScaledLogXrefValidator.getGradeScheduleType(mark, scaleDate)}: "C"
   * (coastal) or "I" (interior) grade schedule of the mark on the scale date.
   */
  String gradeScheduleType(String timberMark, Date scaleDate);
}
