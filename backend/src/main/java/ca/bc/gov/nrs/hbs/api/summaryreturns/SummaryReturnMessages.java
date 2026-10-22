package ca.bc.gov.nrs.hbs.api.summaryreturns;

/**
 * Legacy message texts ({@code $V/ApplicationResources.properties}) the state workflows return.
 */
public final class SummaryReturnMessages {

  private SummaryReturnMessages() {
  }

  /** error.ratecorrection.nodetails */
  public static final String RATE_CORRECTION_NO_DETAILS = "No detail scale returns available for bulk rate correction.";
  /** error.ratecorrection.confirm.nodetails */
  public static final String RATE_CORRECTION_CONFIRM_NO_DETAILS =
      "Detail scale returns available at request time were unavailable at confirmation time.";
  /** error.ratecorrection.approval.nodetails */
  public static final String RATE_CORRECTION_APPROVAL_NO_DETAILS =
      "Detail scale returns available at request time were unavailable at approval time.";
  /** error.psym.required */
  public static final String PSYM_REQUIRED =
      "Cannot create a Final Bill for a Stratum/Mark that has had no billing activity to date";
}
