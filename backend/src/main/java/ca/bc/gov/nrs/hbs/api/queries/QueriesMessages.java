package ca.bc.gov.nrs.hbs.api.queries;

/**
 * Legacy message texts of the Queries tab, verbatim from
 * {@code ear/hbs-view/src/main/resources/ca/bc/gov/mof/hbs/presentation/ApplicationResources.properties}
 * (key in each comment; the generic Struts ones are in
 * {@link ca.bc.gov.nrs.hbs.api.legacyrules.LegacyMessages}).
 */
public final class QueriesMessages {

  private QueriesMessages() {
  }

  // ---- P431 / P441 harvest history (MarkMonthlyBillingAction.validateForm) -------------------

  /** error.billingdaterange.required */
  public static final String BILLING_RANGE_REQUIRED = "You must specify billing date range for your search.";
  /** error.billingdate.istoold={0} = the limit date (yyyy-MMM-dd) */
  public static String billingDateTooOld(String limit) {
    return "From Date must be on or after " + limit + ".";
  }
  /** error.billingdate.futuredateisused */
  public static final String BILLING_DATE_FUTURE = "Billing date may not be a future date.";
  /** error.daterange.invalidtodate */
  public static final String BILLING_FROM_AFTER_TO = "Billing From date may not be a future date of Billing To date.";
  /** error.daterange.daterangetoobig */
  public static final String RANGE_OVER_ONE_YEAR = "You may not specify a date range over one year.";
  /** error.clientorlocation.invalid */
  public static final String CLIENT_OR_LOCATION_INVALID = "Client or client location is invalid.";
  /** error.mark.invalid */
  public static final String MARK_INVALID = "The mark entered is invalid.";
  /** error.licence.invalid (also validator timberLicenseCrossRef) */
  public static final String LICENCE_INVALID = "The licence entered is invalid.";
  /** error.specie.product.grade.invalid */
  public static final String SPG_INVALID = "Species, product and grade combination is invalid.";
  /** error.criteriaselectionbilling.invalid */
  public static final String BILLING_CRITERIA_INSUFFICIENT = "Billing History criteria selection insufficient.";
  /** error.criteriaselectionscaling.invalid */
  public static final String SCALING_CRITERIA_INSUFFICIENT = "Scaling History criteria selection insufficient.";
  /**
   * New: the legacy form had ten mark / licence boxes, so it could not send
   * more; the screen now takes a comma list.
   */
  public static final String MAX_TEN = "Enter up to 10 timber marks or forest file IDs.";

  // ---- P450 issued documents (validation.xml P450Form, P450Action) ---------------------------

  /** errors.dates.seven.years.older={0} = the date label */
  public static String olderThanSevenYears(String label) {
    return "Statement cannot be retrieved as " + label + " is older than seven years";
  }
  /** error.billingorscalerange.required with {0}=issue.date, {1}=scale.date */
  public static final String ISSUE_OR_SCALE_RANGE_REQUIRED =
      "You must specify either issue or Scale Date date range for your search.";
  /** error.billingorscalerange.required with {0}=billing.date, {1}=scale.date (P421Form) */
  public static final String BILLING_OR_SCALE_RANGE_REQUIRED =
      "You must specify either billing or Scale Date date range for your search.";
  /** errors.date.from-before-todate={0} should be before {1}. */
  public static String fromBeforeTo(String from, String to) {
    return from + " should be before " + to + ".";
  }
  /** errors.error-searchfield-allowed={0} is not permitted for Ratio Statement searches. */
  public static String notForRatioStatements(String label) {
    return label + " is not permitted for Ratio Statement searches.";
  }
  /** errors.pop-stratum-year-allowed */
  public static final String PSY_NOT_FOR_PIECE =
      "Population / stratum / year criteria is not applicable to Piece Scale or Ledger searches.";
  /** error.pop.stratum.year.scale.invalid */
  public static final String PSY_NOT_FOR_PIECE_INVOICES =
      "The population/stratum/year criteria can not be applied to the piece scale invoice selection.";
  /** error.population.invalid */
  public static final String POPULATION_INVALID = "The population must be a four digit number.";
  /** error.popyear.invalid */
  public static final String POP_YEAR_INVALID = "The population year field must be a four digit number.";
  /** error.stratum.invalid */
  public static final String STRATUM_INVALID = "The stratum field must be a two digit number.";
  /** error.pop.stratum.year.invalid */
  public static final String PSY_COMBINATION_INVALID =
      "The population/stratum/year field entered is not a valid input combination.";
  /** errors.limit.samplingyear={0} = current year − 7 */
  public static String samplingYearLimit(int year) {
    return "Sampling year cannot be less than " + year;
  }
  /** error.clientnumberwhenLocation.required */
  public static final String CLIENT_NUMBER_WITH_LOCATION =
      "Client number must be provided when client location is provided";
  /** errors.client-number-location */
  public static final String CLIENT_NUMBER_LOCATION_INVALID = "Client Number and Location is invalid.";
  /** error.clientnumber.invalid */
  public static final String CLIENT_NUMBER_INVALID = "Client Number is invalid.";
  /** errors.timbermark-ref */
  public static final String TIMBER_MARK_REF =
      "Timber mark must be in Timber Mark table and in Mark Billed Client table.";
  /** error.print.client.register.not.releasable (BT17247) */
  public static final String REPORT_NOT_RELEASABLE =
      "The report contains personal information, and is temporarily unavailable for public viewing";

  // ---- P421 billing summary ---------------------------------------------------------------------

  /** error.daterange.one_month_limit */
  public static final String ONE_MONTH_LIMIT = "Date ranges are restricted to one month if All is chosen for "
      + "Region / District Harvested or File Type and for Region / District Scaled.";
  /** error.daterange.one_year_limit */
  public static final String ONE_YEAR_LIMIT = "Date ranges are restricted to one year.";
  /** error.popstratyear.invalid (P421Form msg override of populationStratumYearAllowed) */
  public static final String PSY_WEIGHT_ONLY = "Population/stratum/year can only be specified when Weight Scale is chosen.";

  // ---- P401 scale site summary (P401Action.validateForm) -------------------------------------

  /** error.scaledatefrom.required */
  public static final String SCALE_FROM_REQUIRED = "A Scaling From date is required.";
  /** error.scaledatefrom.invalid */
  public static final String SCALE_FROM_INVALID = "Invalid Scale From date.";
  /** error.scaledateto.required */
  public static final String SCALE_TO_REQUIRED = "A Scaling To date is required.";
  /** error.scaledateto.invalid */
  public static final String SCALE_TO_INVALID = "Invalid Scale To date.";
  /** error.grouping.required */
  public static final String GROUPING_REQUIRED = "A report grouping must be specified.";
  /** error.type.required */
  public static final String TYPE_REQUIRED = "A return type is required.";
  /** error.grouping.notallowed */
  public static final String GROUPING_NOT_FOR_PIECE = "This report grouping is not available for Piece Scale.";
  /** error.scales.invalid */
  public static final String SCALE_SITE_INVALID = "The scale site entered is invalid.";
  /** error.client.association.required (legacy text "Association Required.", reworded to say what to do) */
  public static final String ASSOCIATION_REQUIRED =
      "Choose a Client Association (Mark Holder, Site Owner or Stratum Owner) to search by client.";
  /** error.client.required */
  public static final String CLIENT_REQUIRED = "Client number Required.";
  /** error.clientinfo.required */
  public static final String CLIENT_INFO_REQUIRED = "Client information Required.";
  /** error.scalerlicence.scale.invalid */
  public static final String SCALER_LICENCE_NOT_FOR_WEIGHT = "A scaler licence cannot be specified for weight scale.";

  // ---- P403 aged unbilled scale (P403Action.validateForm) -------------------------------------

  /** error.scaledpriortodate.required */
  public static final String SCALED_PRIOR_TO_REQUIRED = "Scaled Prior To date is required.";
  /** error.scaledpriortodate.invalid */
  public static final String SCALED_PRIOR_TO_INVALID = "Invalid Scaled Prior To date.";
  /** error.estimatedaveragerate.required */
  public static final String RATE_REQUIRED = "Estimated Average Rate is required.";
  /** error.estimatedaveragerate.notnumeric */
  public static final String RATE_NOT_NUMERIC = "Estimated Average Rate must be numeric currency value.";

  // ---- P490 cut to cruise (validation.xml P490Form) --------------------------------------------

  /** error.expirationdate.future */
  public static final String EXPIRATION_FUTURE = "Expiration Date may not be a future date.";
  /** error.expirationdate.notEmpty (leading blank of the property value dropped) */
  public static final String EXPIRATION_WITH_MARK =
      "Expiration Date Exclusion can only be used with Forest File Id or Client/Loc.";
}
