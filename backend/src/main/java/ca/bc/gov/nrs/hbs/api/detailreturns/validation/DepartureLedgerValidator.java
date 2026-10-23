package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import java.util.Date;

/**
 * Edits of a departure ledger entry (SCL_SITE_DEPARTURE_LEDGER), ported from
 * {@code $E/dac/detail/control/DepartureLedgerValidator.java}.
 */
public class DepartureLedgerValidator extends DetailValidator {

  protected final LedgerLookups ledger;

  public DepartureLedgerValidator(DetailDocument document, ValidationContext ctx, LedgerLookups ledger) {
    super(document, ctx);
    this.ledger = ledger;
  }

  /** {@code validateAndCopyInputFields}: input scale site, destination (BT 5646), input source. */
  @Override
  protected void validateAndCopyInputFields() {
    if (v.empty("scale_site_id_nmbr")) {
      validateInputScaleSite();
    }
    // BT 5646: DS01 unknown destination site
    if (!v.empty("destination")) {
      validateInputDestinationSite(v.str("destination"));
    }
    // Legacy reads getInputSource(); SCL_SITE_DEPARTURE_LEDGER has no INPUT_SOURCE
    // column, so this copy never fires for a stored row (kept for fidelity).
    if (v.empty("timber_mark") && v.empty("timber_brand") && !v.empty("input_source")) {
      validateInputSource();
    }
  }

  /** {@code validate}. */
  @Override
  protected void validate() {
    Date departureDate = v.date("departure_date");
    if (departureDate != null) {
      validateDepartureDate(departureDate);
      validateDepartureLimitDate(departureDate); // BT 10747
    }
    if (!v.empty("timber_mark")) {
      validateTimberMark();
    }
    if (!v.empty("scale_site_id_nmbr")) {
      validateCruiseBasedScaleSite(v.str("scale_site_id_nmbr"), HbsEditError.CBSS00);
    }
    if (!v.empty("destination")) {
      validateCruiseBasedScaleSite(v.str("destination"), HbsEditError.CBDS00);
    }
  }

  /** Legacy body empty. */
  @Override
  protected void validatFieldScaleDeckId() {
  }

  /** Legacy: a ledger is never billable. */
  @Override
  public boolean isBillableScaleReturn() {
    return false;
  }
}
