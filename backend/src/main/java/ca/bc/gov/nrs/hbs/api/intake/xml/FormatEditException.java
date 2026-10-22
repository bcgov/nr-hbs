package ca.bc.gov.nrs.hbs.api.intake.xml;

/**
 * A failed format edit: the legacy {@code dac.detail.xml.ValidationException}.
 * The unpack records {@link #editNumber()} as a format error on the
 * transmission, batch or batch document being processed and carries on.
 */
public class FormatEditException extends Exception {

  private final String editNumber;

  public FormatEditException(String editNumber) {
    super("Edit error code " + editNumber);
    this.editNumber = editNumber;
  }

  public String editNumber() {
    return editNumber;
  }
}
