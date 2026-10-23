package ca.bc.gov.nrs.hbs.api.summaryreturns.edits;

/**
 * A release edit hit reference data the legacy validator assumed (a "previously validated" timber
 * mark, the scale site, the stratum / population owner …) and threw a runtime exception instead of
 * raising an edit error ({@code HbsGenericRuntimeException}, {@code HBSEJBException}, a
 * NullPointerException). Ported as an exception so the workflow's transaction rolls back, as the
 * legacy release did; the message names the legacy check.
 */
public class ReleaseEditException extends IllegalStateException {

  public ReleaseEditException(String message) {
    super(message);
  }
}
