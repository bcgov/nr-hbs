package ca.bc.gov.nrs.hbs.api.workflow;

import ca.bc.gov.nrs.hbs.api.exception.ConflictFoundException;

/**
 * The record changed after the user loaded it (another user, or a legacy
 * batch job running against the same rows). 409 with the legacy wording
 * ({@code StaleStateException}).
 */
public class StaleUpdateException extends ConflictFoundException {

  public static final String MESSAGE =
      "Your changes were not saved because the record has been updated by another process. "
          + "Reload it and try again.";

  public StaleUpdateException() {
    super(MESSAGE);
  }

  public StaleUpdateException(String message) {
    super(message);
  }
}
