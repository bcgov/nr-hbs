package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import java.util.Date;

/**
 * Edits of an arrival ledger entry (SCL_SITE_ARRIVAL_LEDGER), ported from
 * {@code $E/dac/detail/control/ArrivalLedgerValidator.java}. The primary scaler
 * lives in LICENSE_NUMBER / QUANT_LIC_TYPE_CD (see
 * {@link DetailValidator#primaryLicenceColumn()}).
 */
public class ArrivalLedgerValidator extends DetailValidator {

  protected final LedgerLookups ledger;

  public ArrivalLedgerValidator(DetailDocument document, ValidationContext ctx, LedgerLookups ledger) {
    super(document, ctx);
    this.ledger = ledger;
  }

  /**
   * {@code validateAndCopyInputFields}: input scale site, input source (mark or
   * brand), input primary scaler (BT 5646) and input cut block (BT 5646, copied
   * without a check).
   */
  @Override
  protected void validateAndCopyInputFields() {
    // required field for all scale event type codes
    if (v.empty("scale_site_id_nmbr")) {
      validateInputScaleSite();
    }
    // not required for all event types: field inclusion was checked before
    if (v.empty("timber_mark") && v.empty("timber_brand") && !v.empty("input_source")) {
      validateInputSource();
    }
    // BT-5646: validateArrInputPrimaryScalerLicense (→ LICENSE_NUMBER, QUANT_LIC_TYPE_CD = S, else PSL01)
    if (v.empty("license_number") && !v.empty("input_primary_scaler_lic")) {
      validateInputPrimaryScalerLicense();
    }
    if (v.empty("cut_block_id") && !v.empty("input_cutblock_id")) {
      v.set("cut_block_id", v.str("input_cutblock_id"));
    }
  }

  /** {@code validate}. */
  @Override
  protected void validate() {
    Date scaleDate = v.date("scale_date");
    Date arrivalDate = v.date("arrival_date");
    if (scaleDate != null) {
      validateScaleDate();
    }
    // BT 5646
    if (arrivalDate != null) {
      validateArrivalDate(arrivalDate);
      validateArrivalLimitDate(arrivalDate); // BT 10747
      if (scaleDate != null) {
        validateArrivalDateBeforeScaleDate(arrivalDate, scaleDate); // BT 2912
      }
    }
    if (!v.empty("timber_brand")) {
      validateTimberBran(v.str("timber_brand"));
    }
    if (!v.empty("orig_scale_site")) {
      validateOrigScaleSite(v.str("orig_scale_site"));
      validateCruiseBasedScaleSite(v.str("orig_scale_site"), HbsEditError.CBOS00);
    }
    if (!v.empty("scale_site_id_nmbr")) {
      validateCruiseBasedScaleSite(v.str("scale_site_id_nmbr"), HbsEditError.CBSS00);
    }
    // BT11301: trim the deck id, then FSD01 / FSD02
    if (v.yes("field_scale_ind") != null) {
      if (v.str("field_scale_deck_id") != null) {
        v.set("field_scale_deck_id", v.str("field_scale_deck_id").trim());
      }
      validateFieldScaleDeckId(v.yes("field_scale_ind"), v.str("field_scale_deck_id"),
          v.yes("original_version_ind"));
    }
    if (!v.empty("timber_mark")) {
      validateTimberMark();
    }
    if (!v.empty("arrival_ledger_weigh_slip")) {
      validateWeighSlip(v.str("arrival_ledger_weigh_slip"));
    }
    if (v.lng("parcel_identifier") != null || v.lng("parcel_count") != null) {
      validateParcelCountAndParcelId();
    }
    // BT11301 (legacy getScaleEventTypeCode().equals(...): the event type is mandatory)
    String event = v.str("scale_event_type_code");
    if (PRIMARY_SCALE.equals(event) || TO_SITE_DECK.equals(event) || FROM_SITE_DECK.equals(event)
        || REMOTE_RED_TAG.equals(event)) {
      validateFieldScaleDeck();
    }
  }

  /** AD04 (BT 2912): arrival date after the scale date. */
  protected void validateArrivalDateBeforeScaleDate(Date arrivalDate, Date scaleDate) {
    if (arrivalDate.after(scaleDate)) {
      errors.add(scalingError(CrossReferenceEdit.Common.ARRIVAL_DATE_BEFORE_SCALE_DATE));
    }
  }

  /** {@code validateParcelCountAndParcelId}. */
  protected void validateParcelCountAndParcelId() {
    Long parcelId = v.lng("parcel_identifier");
    Long parcelCount = v.lng("parcel_count");
    validateParcelId(parcelId, parcelCount);
    validateParcelCount(parcelId, parcelCount);
  }

  /**
   * {@code validateFieldScaleDeck} (BT11301): FSX03 deck id not alphanumeric;
   * FSX04 no field-scale estimate for the deck and mark.
   */
  private void validateFieldScaleDeck() {
    String deck = v.str("field_scale_deck_id");
    if (!empty(deck) && !isAlphaNumeric(deck)) {
      errors.add(scalingError(CrossReferenceEdit.LogTally.FIELD_DECK_CASE));
    }
    if (!empty(deck) && !v.empty("timber_mark")
        && !ledger.fieldScaleEstimateExists(deck, v.str("timber_mark"))) {
      errors.add(scalingError(CrossReferenceEdit.Common.FIELD_SCALE_DECK_ID_MATCH));
    }
  }

  /** {@code StringUtil.isAlphaNumeric}: non-empty, every character a letter or digit. */
  static boolean isAlphaNumeric(String s) {
    if (empty(s)) return false;
    for (int i = 0; i < s.length(); i++) {
      if (!Character.isLetterOrDigit(s.charAt(i))) return false;
    }
    return true;
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
