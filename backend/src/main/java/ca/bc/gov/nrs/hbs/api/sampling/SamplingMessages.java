package ca.bc.gov.nrs.hbs.api.sampling;

/**
 * Legacy message texts (hbs-view {@code ApplicationResources.properties}),
 * keyed by their legacy keys in the comments. {0}/{1} placeholders are
 * filled by the helpers below exactly as the Struts {@code ActionError}
 * arguments were.
 */
final class SamplingMessages {

  private SamplingMessages() {
  }

  // errors.required / errors.integer / errors.minlength / errors.float
  static String required(String label) {
    return label + " is required.";
  }

  static String numeric(String label) {
    return label + " must be numeric.";
  }

  static String minLength(String label, int n) {
    return label + " cannot be less than " + n + " characters.";
  }

  static String number(String label) {
    return label + " must be a number.";
  }

  // error.value-is.less-than={0} cannot be less than {2}.
  static String lessThan(String label, String min) {
    return label + " cannot be less than " + min + ".";
  }

  // error.value-is.greater-than={0} cannot be greater than {1}.
  static String greaterThan(String label, String max) {
    return label + " cannot be greater than " + max + ".";
  }

  // error.decimal.invalid={0} must have {3} or less decimal places.
  static String decimals(String label, int n) {
    return label + " must have " + n + " or less decimal places.";
  }

  // error.decimal.invalid2={0} must be a number and have {3} or less decimal places.
  static String numberDecimals(String label, int n) {
    return label + " must be a number and have " + n + " or less decimal places.";
  }

  /** Legacy field labels (samplePlan.*, population.*, clientno, district, …). */
  static final String L_ABBREVIATION = "Plan Abbreviation";
  static final String L_PLAN_NAME = "Plan-Name";
  static final String L_PRECISION_LIMIT = "Precision Limit";
  static final String L_SAMPLE_FLOOR = "Sample Floor";
  static final String L_POPULATION = "Population";
  static final String L_YEAR = "Year";
  static final String L_CLIENT = "Client No / Loc";
  static final String L_DISTRICT = "District";
  static final String L_EFFECTIVE = "Effective Date";
  static final String L_EXPIRY = "Expiry Date";
  static final String L_STRATUM_NUMBER = "Stratum Number";
  static final String L_STRATUM_NAME = "Stratum Name";

  static final String PLAN_ABBREVIATION_DUPLICATE = "Plan abbreviation already exists."; // error.planabbreviation.duplicate
  static final String STRATUM_NUMBER_DUPLICATE = "Stratum Number already exists in plan."; // error.stratumnumber.duplicate
  static final String RATIO_TYPE_CHANGE =
      "A population's ratio type cannot be changed once a plan has been approved."; // error.ratiotype.change
  static final String SAMPLING_BEFORE_EFFECTIVE = "The sampling year must be greater than or equal to the year of"
      + " the population effective date."; // errors.date.sampling-before-effective
  static final String SAMPLING_AFTER_EXPIRY = "The sampling year must be less than or equal to the year of the"
      + " population expiry date."; // errors.date.sampling-after-effective
  static final String DISTRICT_USER = "User not authorized to add a population for this district."; // error.district-user.access
  static final String REGION_DISTRICT = "Region / District is invalid."; // errors.region-district-ref
  static final String CLIENT_LOCATION_INVALID = "Client Number and Location is invalid."; // errors.client-number-location
  static final String CLIENT_LOCATION_MISSING = "Client Location is missing."; // errors.client-location-missing
  static final String POP_EFFECT_LESS_EXP =
      "Population Effective Date must be less than Population Expiry Date"; // error.stratum.popEffectLessExp
  static final String POP_EFFECT_FIRST_DAY =
      "Population Effective Date must be the first day of a month"; // error.stratum.popEffectFirstDay
  static final String POP_EXPIRY_LAST_DAY =
      "Population Expiry Date must be the last day of a month"; // error.stratum.popExpiryLastDay
  static final String PLAN_POPULATION_REQUIRED =
      "Can not create plan without target population."; // error.plan_population.required (P892 "No")

  /** P892 "Confirm Add Population" page text. */
  static String confirmPopulation(String pop, Long year) {
    return "Please confirm you wish to create a new population: " + pop + " and year: " + year;
  }

  // error.stratum.strata* / popStrata* ({0} = stratum number)
  static String strataEffectLessExp(String s) {
    return "Stratum " + s + " Effective Date must be less than Stratum Expiry Date";
  }

  static String strataEffectFirstDay(String s) {
    return "Stratum " + s + " Effective Date must be the first day of a month";
  }

  static String strataExpiryLastDay(String s) {
    return "Stratum " + s + " Expiry Date must be the last day of a month";
  }

  static String strataPopEffect(String s) {
    return "Stratum " + s + " Effective Date cannot be less than Population Effective Date";
  }

  static String strataPopExpiry(String s) {
    return "Stratum " + s + " Expiry Date cannot be greater than Population Expiry Date";
  }

  static String popStrataEffect(String s) {
    return "Population Effective Date cannot be greater than Stratum " + s + " Effective Date";
  }

  static String popStrataExpiry(String s) {
    return "Population Expiry Date cannot be less than Stratum " + s + " Expiry Date";
  }

  // error.strand.* ({0} stratum, {1} count)
  static String strandSampleScale(String s, long n) {
    return "The proposed effective period for stratum " + s + " will strand " + n + " sample scale summaries";
  }

  static String strandSampleLog(String s, long n) {
    return "The proposed effective period for stratum " + s + " will strand " + n + " sample log tallies";
  }

  static String strandWeighScale(String s, long n) {
    return "The proposed effective period for stratum " + s + " will strand " + n + " weight scale summaries";
  }

  static String strandWeighSlips(String s, long n) {
    return "The proposed effective period for stratum " + s + " will strand " + n + " weigh slips";
  }

  // error.deleteStrata.* ({0} stratum, {1} count)
  static String deleteSampleLog(String s, long n) {
    return n + " Sample Log Tallies prevent the deletion of Stratum " + s;
  }

  static String deleteSampleScale(String s, long n) {
    return n + " Sample Scale Summaries prevent the deletion of Stratum " + s;
  }

  static String deleteWeighScale(String s, long n) {
    return n + " Weight Scale Summaries prevent the deletion of Stratum " + s;
  }

  static String deleteWeighSlips(String s, long n) {
    return n + " Weigh Slips prevent the deletion of Stratum " + s;
  }

  // error.scale.activity.associated
  static String activityAssociated(String action, String stratum) {
    return "Cannot " + action + " the Plan. One or more documents exists, that reference stratum " + stratum
        + ", which this plan is attempting to remove";
  }

  static final String PRECISION_ZERO =
      "Stratum Plan computation variables must be recalculated before proceeding."; // errors.plan_precision1.zero
  static final String STRATUM_PLAN_ERRORS =
      "Stratum Plan errors must be corrected before proceeding."; // errors.stratum-plan.validation

  // error.strata.effectiveDate.*Span
  static String firstSpan(String s) {
    return "Stratum " + s + " - Interior strata and strata in interior populations cannot span 2006-04-01"
        + " (the Interior Grade conversion date)";
  }

  static String secondSpan(String s) {
    return "Stratum " + s + " - Coast strata and strata in coast populations cannot span 2006-01-01 and"
        + " 2006-04-01 (the Interior Grade conversion date)";
  }

  static String thirdSpan(String s) {
    return "Stratum " + s + " - Coast strata and strata in coast populations cannot span 2006-04-01"
        + " (the Interior Grade conversion date) and 2007-01-01.";
  }

  static final String PRODUCT_SCHEDULE_CHANGE =
      "A stratum's product schedule cannot be changed once a plan has been approved."; // error.productschedule.change
  static final String GRADE_SCHEDULE_CHANGE =
      "A stratum's grade schedule cannot be changed once a plan has been approved."; // error.gradeschedule.change

  // Save & Calculate (P891Action.doCalculations / SamplePlanValidator)
  static final String CALC_VOLUME_TOO_BIG = "Computed Volume cannot be greater than 9,999,999.999";
  static final String CALC_VOLUME_TOO_SMALL = "Computed Volume cannot be less than 10 M3";
  static final String CALC_LOADS_LT_FLOOR = "Computed Loads cannot be less than Sample Floor";
  static final String CALC_LOADS_LT_CONSTRAINT = "Constrained Samples cannot be greater than Computed Loads";
  static final String CALC_SIZE_TOO_SMALL = "Computed Size cannot be less than 1.000 M3";
  static final String CALC_SIZE_LT_STDDEV = "Estimated Standard Deviation cannot be greater than Computed Size";
  static final String EST_VOLUME_INVALID = "Estimated Volume cannot be greater than 9,999,999.999. ";
  static final String EST_LOADS_INVALID = "Estimated Loads must be an integer. ";
  static final String EST_STDDEV_INVALID = "Estimated Standard deviation cannot be less than 0.10 M3.";
  static final String EST_STDDEV_GT_SIZE = "Estimated Standard Deviation cannot be greater than Estimated Size.";
  static final String SAMPLE_CONSTRAINT_INVALID = "Constrained Samples must be an integer.";
  static final String EFFECT_DATE_NULL = "The Population effective date is null.";
  static final String LEN_VOLUME = "(Estimated Loads)*(Estimated Size) must be a valid number greater than 0.0 and"
      + " less than 1000000000000.0.";
  static final String LEN_LOADS = "(Estimated Volume)/(Estimated Size) must be a valid number greater than 0 and"
      + " less than 1000000.";
  static final String LEN_SIZE = "(Estimated Volume)/(Estimated Loads) must be a valid number greater than 0.0 and"
      + " less than 10000.00.";
  static final String LEN_OPT_SAMPLE = "Optimum sample calculation error.  One or more of the following fields is"
      + " too large or too small: estimated loads, estimated volume, estimated size, standard deviation, sample"
      + " floor or precision limit.";
  static final String LEN_OPT_PRECISION = "Optimum precision calculation error.  One or more of the following"
      + " fields is too large or too small: estimated loads, estimated volume, estimated size, standard deviation,"
      + " sample floor or precision limit.";
  static final String LEN_OPT_FREQUENCY = "Optimum frequency calculation error.  One or more of the following"
      + " fields is too large or too small: estimated loads, estimated volume, estimated size, standard deviation,"
      + " sample floor or precision limit.";
  static final String LEN_TARGET_SAMPLE = "Target sample calculation error. One or more of the following fields is"
      + " too large or too small: estimated loads, estimated volume, estimated size, standard deviation, sample"
      + " floor or precision limit.";
  static final String LEN_TARGET_PRECISION = "Target precision calculation error. One or more of the following"
      + " fields is too large or too small: estimated volume, estimated loads, standard deviation or sample"
      + " constraint.";
  static final String LEN_TARGET_FREQUENCY = "Target frequency calculation error. One or more of the following"
      + " fields is too large or too small: estimated loads or target sample.";
  static final String LEN_SAMPLE_CONSTRAINT = "Sample constraint calculation error. One or more of the following"
      + " fields is too large or too small: estimated volume, estimated loads, standard deviation, precision"
      + " constraint or sample floor.";
  static final String LEN_PRECISION_CONSTRAINT = "Precision constraint calculation error. One or more of the"
      + " following fields is too large or too small: estimated volume, estimated loads, standard deviation or"
      + " sample constraint.";

  // Composition (P885 / P886 validateForm)
  static final String SPG_EFFECTIVE = "Timber in this stratum must use species product grade combinations in"
      + " effect on the Stratum effective date.";
  static final String SPG_EXPIRY = "Timber in this stratum must use species product grade combinations in effect"
      + " on the Stratum expiry date.";
  static final String USE_COAST_GRADE = "Timber in this stratum must use Coast grade codes.";
  static final String USE_INTERIOR_GRADE = "Timber in this stratum must use Interior grade codes.";
  static final String COAST_FROM = "Timber in this stratum must use Coast grade codes in effect on the Stratum"
      + " effective date.";
  static final String INTERIOR_FROM = "Timber in this stratum must use Interior grade codes in effect on the"
      + " Stratum effective date.";
  static final String COAST_TO = "Timber in this stratum must use Coast grade codes in effect on the Stratum"
      + " expiry date.";
  static final String INTERIOR_TO = "Timber in this stratum must use Interior grade codes in effect on the Stratum"
      + " expiry date.";
  static final String SPECIES_GRADE_INVALID = "Species and grade combination is invalid.";
  static final String SPG_INVALID = "Species, product, and grade combination is invalid.";
  static final String SPG_DUPLICATE =
      "The stratum plan composition you are trying to insert/update already exists.";
  static final String SPECIES_REQUIRED = "Species Code is required.";
  static final String RATIO_REQUIRED = "Default Ratio is required.";
  static final String RATIO_GREATER = "Composition Ratio must be greater than zero m3/tonne. ";
  static final String RATIO_LESSER = "Composition Ratio must be less than 10 m3/tonne.";
  static final String TOTAL_RATIO_LESSER = "Total Ratio must be less than 10 m3/tonne.";
  static final String RATIO_NUMERIC = "Default Ratio must be a numeric value between -10 and 10";
}
