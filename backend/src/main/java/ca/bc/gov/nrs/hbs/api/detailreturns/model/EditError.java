package ca.bc.gov.nrs.hbs.api.detailreturns.model;

/**
 * One HBS edit error found by the validators, stored in the version's error
 * table ({@code logdtlId} null) or its log-line error table — legacy
 * {@code DetailHbsEditError} created through {@code createError} /
 * {@code createLogError} of each {@code *Validator}.
 *
 * @param category {@code HBS_EDIT_ERR_CATEGORY_CODE} (CrossReferenceEdit.CategoryCode)
 * @param code     {@code HBS_EDIT_ERR_MESSAGE_CODE}
 * @param line     the log line the error belongs to, or null for a version error
 */
public record EditError(String category, String code, LogLine line) {

  public static EditError version(String category, String code) {
    return new EditError(category, code, null);
  }

  public static EditError line(String category, String code, LogLine line) {
    return new EditError(category, code, line);
  }

  public boolean isLineError() {
    return line != null;
  }
}
