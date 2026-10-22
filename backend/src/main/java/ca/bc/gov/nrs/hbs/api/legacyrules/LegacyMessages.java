package ca.bc.gov.nrs.hbs.api.legacyrules;

/**
 * Legacy message texts used by the work-package-7 ports, verbatim from
 * {@code ear/hbs-view/src/main/resources/ca/bc/gov/mof/hbs/presentation/ApplicationResources.properties}
 * (key in each comment). Parameterised messages are methods with the legacy
 * {@code {0}}/{@code {1}} filled in.
 */
public final class LegacyMessages {

  private LegacyMessages() {
  }

  // ---- generic Struts validator messages ------------------------------------------------------

  /** errors.required={0} is required. */
  public static String required(String label) {
    return label + " is required.";
  }

  /** errors.long={0} must be a number. */
  public static String notANumber(String label) {
    return label + " must be a number.";
  }

  /** errors.integer={0} must be numeric. */
  public static String notNumeric(String label) {
    return label + " must be numeric.";
  }

  /** errors.invalid={0} is invalid. */
  public static String invalid(String label) {
    return label + " is invalid.";
  }

  /** errors.date={0} is not a valid date. */
  public static String invalidDate(String label) {
    return label + " is not a valid date.";
  }

  /** errors.date.after-current={0} must be before the current date. */
  public static String beforeCurrent(String label) {
    return label + " must be before the current date.";
  }

  /** errors.maxlength={0} cannot be greater than {1} characters. */
  public static String maxLength(String label, int max) {
    return label + " cannot be greater than " + max + " characters.";
  }

  /** error.value-is.less-than={0} cannot be less than {2}. */
  public static String lessThan(String label, long min) {
    return label + " cannot be less than " + min + ".";
  }

  /** error.value-is.greater-than={0} cannot be greater than {1}. */
  public static String greaterThan(String label, long max) {
    return label + " cannot be greater than " + max + ".";
  }

  /** errors.limitingdate={0} must be on or after {1} (date as yyyy-MMM-dd). */
  public static String limitingDate(String label, String formattedLimit) {
    return label + " must be on or after " + formattedLimit;
  }

  /** error.date.future={0} cannot be a future date. */
  public static String futureDate(String label) {
    return label + " cannot be a future date.";
  }

  /** errors.date.from-before-to */
  public static final String FROM_BEFORE_TO = "From date should be before to date.";
  /** errors.scale-site-ref */
  public static final String SCALE_SITE_REF = "Scale Site does not exist.";
  /** errors.region-district-ref */
  public static final String REGION_DISTRICT_REF = "Region / District is invalid.";
  /** error.useriddomain.required */
  public static final String USER_ID_DOMAIN_REQUIRED = "You must enter a User Id and domain.";

  // ---- P811 / P813 / P823 force dates --------------------------------------------------------

  /** error.forceyear.invalid */
  public static final String FORCE_YEAR_INVALID = "Year is invalid.";
  /** error.forcedatem.invalid */
  public static final String FORCE_DATE_MONTHLY =
      "Force date must be within 28 days from today, if frequency is Monthly.";
  /** error.forcedateb.invalid */
  public static final String FORCE_DATE_BIWEEKLY =
      "Force date must be within 14 days from today, if frequency is Bi-Weekly.";
  /** error.forcedated.invalid */
  public static final String FORCE_DATE_DAILY =
      "Force date must be within 1 day from today, if frequency is Daily.";
  /** error.nextDate.invalid */
  public static final String FORCE_BEFORE_PERIOD_START =
      "Force Date cannot be less than Ratio Computation Period Start";
  /** error.lastDate.invalid */
  public static final String FORCE_AFTER_PERIOD_END =
      "Force Date cannot be greater than Ratio Computation Period End";
  /** error.profile.notfound */
  public static final String PROFILE_NOT_FOUND =
      "Profile not found - record with specified Population,Sampling Year,and Effective Date does not exist";
  /** all.noresults.header (the HTML markup dropped) */
  public static final String NO_RECORDS = "No Records";

  // ---- P828-P830 final bills -----------------------------------------------------------------

  /** process.success (markup dropped) */
  public static final String PROCESSED = "Processed.";
  /** process.failure (markup dropped) */
  public static final String NOT_PROCESSED = "Not Processed.";

  // ---- P835 / P836 non-renewable tenures -----------------------------------------------------

  /** error.population.required */
  public static final String POPULATION_REQUIRED = "A population is required.";
  /** error.populationlenght.required */
  public static final String POPULATION_DIGITS = "A population must be a 4 digit number.";
  /** error.finalbillpopulation.invalid */
  public static final String FB_POPULATION_INVALID = "Population is invalid.";
  /** error.finalbillyear.required */
  public static final String FB_YEAR_REQUIRED = "A sample year is required.";
  /** error.finalbillyear.invalid */
  public static final String FB_YEAR_INVALID = "Sample year is invalid.";
  /** error.finalbillpopulation.defaultratio */
  public static final String FB_DEFAULT_RATIO =
      "An election for final billing cannot be made for a population where Ratio Type is Default.";
  /** error.timbermark.required */
  public static final String TIMBER_MARK_REQUIRED = "A timber mark is required.";
  /** error.timbermark.invalid */
  public static final String TIMBER_MARK_INVALID =
      "Timber mark must be in Timber Mark table and in Mark Billed Client table.";
  /** error.timbermark.renewable */
  public static final String TIMBER_MARK_RENEWABLE =
      "Cannot include this Mark for final billing, because the Timber Mark must be exempt from final bills "
          + "(must be non-renewable).";
  /** error.finalbillabledropoutmark.inserterror */
  public static final String FB_INSERT_ERROR =
      "Cannot include this Mark for final billing for this Pop/Yr, because invoices or statements exist for "
          + "this Pop/Yr/Mark.";
  /** error.finalbillabledropoutmark.deleteerror */
  public static final String FB_DELETE_ERROR =
      "Cannot delete this Mark from final billing for this Pop/Yr, because invoices or statements exist for "
          + "this Pop/Yr/Mark.";
  /** error.finalbillindb.dataindb */
  public static final String FB_ALREADY_ELECTED =
      "The population, year and timber mark combination has already elected to receive final bills.";
  /** error.finalbillpopulationdb.dataindb */
  public static final String FB_POP_YEAR_INVALID = "Population / Sampling Year combination is invalid.";

  // ---- P908 / P911 / P954 scale control ------------------------------------------------------

  /** error.fromdays.greater */
  public static final String FROM_DAYS_GREATER = "From Days must be greater than To Days.";
  /** error.newfromandoldto.greater */
  public static final String NEW_FROM_OLD_TO =
      "The new value for From Days must be greater than the old value for To Days.";
  /** error.newtoandoldfrom.lesser */
  public static final String NEW_TO_OLD_FROM =
      "The new value for To Days must be less than the old value for From Days.";
  /** error.values.unchanged */
  public static final String VALUES_UNCHANGED = "No values have been changed and no updates made.";
  /** error.status.changed */
  public static final String STATUS_CHANGED =
      "Your request was denied, because the status of a scale control type has changed.";
  /** errors.scaleanomaly-user-access */
  public static final String ANOMALY_USER_ACCESS =
      "User not authorized to view Scale Event Anomalies for other clients scale sites.";
  /** error.eventdatefrom.future */
  public static final String EVENT_FROM_FUTURE = "Event From Date may not be a future date.";
  /** errors.eventdatefrom.before-to */
  public static final String EVENT_FROM_BEFORE_TO = "Event From Date should be before Event To Date.";
  /** error.fromdateandtodate.lesser */
  public static final String ASSESSMENT_FROM_TO =
      "Assessment From Date must be less than or equal to Assessment To Date";
  /** error.scales.invalid */
  public static final String SCALE_SITE_INVALID = "The scale site entered is invalid.";
  /** error.eventgapandanomalies.notrequired */
  public static final String LICENCE_WITH_ARRIVAL =
      "Primary Scaler Licence option may not be selected in conjunction with Arrival Event Gaps and Duplicates.";
  /** error.arrivalnodepartures.notrequired */
  public static final String LICENCE_WITH_ARR_DEP =
      "Primary Scaler Licence option may not be selected in conjunction with Arrivals without matching Departures.";
  /** error.departurenoarrival.notrequired */
  public static final String LICENCE_WITH_DEP_ARR =
      "Primary Scaler Licence option may not be selected in conjunction with Departures without matching Arrivals.";
  /** error.licenseNumber.required */
  public static final String LICENCE_NOT_FOUND = "Primary Scaler Licence does not exist in the Quant License table.";

  // ---- P280 / P283 batch slips ---------------------------------------------------------------

  /** error.doc-count-received.invalid */
  public static final String DOC_COUNT_RECEIVED = "Document count does not match documents received.";
}
