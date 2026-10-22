package ca.bc.gov.nrs.hbs.api.intake.xml;

import java.util.Date;

/**
 * Arrival ({@code ARLEntry}) and departure ({@code DPLEntry}) ledger
 * entries: {@code dac.detail.xml.LedgerEntry} and subclasses. Ledger entries
 * have no hash total.
 */
public abstract sealed class LedgerEntry permits LedgerEntry.ArrivalEntry, LedgerEntry.DepartureEntry {

  public String eventType;
  public String scaleSite;

  /** {@code LedgerEntry.validate}: FL01A field inclusion, FLA02 scale site, then the dates. */
  public final void validate(String batchScaleSite) throws FormatEditException {
    try {
      checkFields();
    } catch (IllegalStateException e) {
      Edits.fail(FormatEdit.Transmission.VALIDATION);
    }
    Edits.assertEquals(FormatEdit.Ledger.SCALE_SITE, batchScaleSite, scaleSite);
    validateDates();
  }

  protected abstract void checkFields() throws FormatEditException;

  protected abstract void validateDates() throws FormatEditException;

  /** {@code ARLEntry}: PS, B4, 4R, TD, FD, RS, RR. */
  public static final class ArrivalEntry extends LedgerEntry {
    public Parts.Arrival arrival;
    public String source;
    public Parts.Scale scale;
    public Character fieldScaleFlag;
    public Character fieldScaleDeckId;
    public String cutblockId;
    public String weighSlipNumber;
    public String originatingScaleSiteNumber;

    @Override
    protected void validateDates() throws FormatEditException {
      if (arrival != null) {
        arrival.validate();
      }
      if (scale != null) {
        scale.validate();
      }
    }

    /**
     * The legacy FSD01 check compared the Character flag with the String "Y"
     * ({@code getFieldScaleFlag().equals("Y")}), which is never true, so FSD01
     * was never raised. Kept as is (docs/questions.md Q12: port known defects).
     */
    private boolean flagIsYesAsLegacyCompared() {
      Object legacyComparand = "Y";
      return fieldScaleFlag != null && legacyComparand.equals(fieldScaleFlag); // always false, as in legacy
    }

    @Override
    protected void checkFields() throws FormatEditException {
      switch (String.valueOf(eventType)) {
        case Document.PRIMARY_SCALE -> {
          Edits.assertNotNull(FormatEdit.Ledger.ARRIVAL_REQUIRED, arrival);
          Edits.assertNotNull(FormatEdit.Ledger.CUTBLOCK_ID_REQUIRED, cutblockId);
          Edits.assertNotNull(FormatEdit.Ledger.FIELD_SCALE_FLAG_REQUIRED, fieldScaleFlag);
          Edits.assertNull(FormatEdit.Ledger.WEIGH_SLIP_NUMBER_NOT_PERMITTED, weighSlipNumber);
          Edits.assertNotNull(FormatEdit.Ledger.SCALE_REQUIRED, scale);
          if (flagIsYesAsLegacyCompared()) {
            Edits.assertNotNull(FormatEdit.Ledger.FIELD_SCALE_DECK_ID_PRESENT_WHEN_FLAG_Y, fieldScaleDeckId);
          }
          Edits.assertNull(FormatEdit.Ledger.ORIGINATING_SCALE_SITE_NUMBER_NOT_PERMITTED, originatingScaleSiteNumber);
        }
        case Document.SCALED_BEFORE -> {
          Edits.assertNotNull(FormatEdit.Ledger.ARRIVAL_REQUIRED, arrival);
          Edits.assertNull(FormatEdit.Ledger.CUTBLOCK_ID_NOT_PERMITTED, cutblockId);
          Edits.assertNull(FormatEdit.Ledger.FIELD_SCALE_FLAG_NOT_PERMITTED, fieldScaleFlag);
          Edits.assertNull(FormatEdit.Ledger.WEIGH_SLIP_NUMBER_NOT_PERMITTED, weighSlipNumber);
          Edits.assertNull(FormatEdit.Ledger.SCALE_NOT_PERMITTED, scale);
          Edits.assertNull(FormatEdit.Ledger.FIELD_SCALE_DECK_ID_NOT_PERMITTED, fieldScaleDeckId);
          Edits.assertNull(FormatEdit.Ledger.ORIGINATING_SCALE_SITE_NUMBER_NOT_PERMITTED, originatingScaleSiteNumber);
        }
        case Document.SB4_RESCALE -> {
          Edits.assertNotNull(FormatEdit.Ledger.ARRIVAL_REQUIRED, arrival);
          Edits.assertNull(FormatEdit.Ledger.CUTBLOCK_ID_NOT_PERMITTED, cutblockId);
          Edits.assertNull(FormatEdit.Ledger.FIELD_SCALE_FLAG_NOT_PERMITTED, fieldScaleFlag);
          Edits.assertNull(FormatEdit.Ledger.WEIGH_SLIP_NUMBER_NOT_PERMITTED, weighSlipNumber);
          Edits.assertNotNull(FormatEdit.Ledger.SCALE_REQUIRED, scale);
          Edits.assertNull(FormatEdit.Ledger.FIELD_SCALE_DECK_ID_NOT_PERMITTED, fieldScaleDeckId);
          Edits.assertNull(FormatEdit.Ledger.ORIGINATING_SCALE_SITE_NUMBER_NOT_PERMITTED, originatingScaleSiteNumber);
        }
        case Document.TO_SITE_DECK -> {
          Edits.assertNotNull(FormatEdit.Ledger.ARRIVAL_REQUIRED, arrival);
          Edits.assertNotNull(FormatEdit.Ledger.CUTBLOCK_ID_REQUIRED, cutblockId);
          Edits.assertNotNull(FormatEdit.Ledger.FIELD_SCALE_FLAG_REQUIRED, fieldScaleFlag);
          Edits.assertNull(FormatEdit.Ledger.WEIGH_SLIP_NUMBER_NOT_PERMITTED, weighSlipNumber);
          Edits.assertNull(FormatEdit.Ledger.SCALE_NOT_PERMITTED, scale);
          if (flagIsYesAsLegacyCompared()) {
            Edits.assertNotNull(FormatEdit.Ledger.FIELD_SCALE_DECK_ID_PRESENT_WHEN_FLAG_Y, fieldScaleDeckId);
          }
          Edits.assertNull(FormatEdit.Ledger.ORIGINATING_SCALE_SITE_NUMBER_NOT_PERMITTED, originatingScaleSiteNumber);
        }
        case Document.FROM_SITE_DECK -> {
          Edits.assertNotNull(FormatEdit.Ledger.ARRIVAL_REQUIRED, arrival);
          Edits.assertNull(FormatEdit.Ledger.CUTBLOCK_ID_NOT_PERMITTED, cutblockId);
          Edits.assertNotNull(FormatEdit.Ledger.FIELD_SCALE_FLAG_REQUIRED, fieldScaleFlag);
          Edits.assertNull(FormatEdit.Ledger.WEIGH_SLIP_NUMBER_NOT_PERMITTED, weighSlipNumber);
          Edits.assertNotNull(FormatEdit.Ledger.SCALE_REQUIRED, scale);
          Edits.assertNull(FormatEdit.Ledger.SPLIT_LOAD_NOT_PERMITTED, scale.splitLoad);
          if (flagIsYesAsLegacyCompared()) {
            Edits.assertNotNull(FormatEdit.Ledger.FIELD_SCALE_DECK_ID_PRESENT_WHEN_FLAG_Y, fieldScaleDeckId);
          }
          Edits.assertNull(FormatEdit.Ledger.ORIGINATING_SCALE_SITE_NUMBER_NOT_PERMITTED, originatingScaleSiteNumber);
        }
        case Document.REMOTE_SAMPLE_SCALE -> {
          Edits.assertNotNull(FormatEdit.Ledger.ARRIVAL_REQUIRED, arrival);
          Edits.assertNull(FormatEdit.Ledger.CUTBLOCK_ID_NOT_PERMITTED, cutblockId);
          Edits.assertNull(FormatEdit.Ledger.FIELD_SCALE_FLAG_NOT_PERMITTED, fieldScaleFlag);
          Edits.assertNotNull(FormatEdit.Ledger.WEIGH_SLIP_NUMBER_REQUIRED, weighSlipNumber);
          Edits.assertNotNull(FormatEdit.Ledger.ORIGINATING_SCALE_SITE_NUMBER_REQUIRED, originatingScaleSiteNumber);
          Edits.assertNotNull(FormatEdit.Ledger.SCALE_REQUIRED, scale);
          Edits.assertNull(FormatEdit.Ledger.SPLIT_LOAD_NOT_PERMITTED, scale.splitLoad);
          Edits.assertNull(FormatEdit.Ledger.FIELD_SCALE_DECK_ID_NOT_PERMITTED, fieldScaleDeckId);
        }
        case Document.REMOTE_RED_TAG -> {
          Edits.assertNotNull(FormatEdit.Ledger.ARRIVAL_REQUIRED, arrival);
          Edits.assertNotNull(FormatEdit.Ledger.CUTBLOCK_ID_REQUIRED, cutblockId);
          Edits.assertNotNull(FormatEdit.Ledger.FIELD_SCALE_FLAG_REQUIRED, fieldScaleFlag);
          Edits.assertNotNull(FormatEdit.Ledger.WEIGH_SLIP_NUMBER_REQUIRED, weighSlipNumber);
          Edits.assertNotNull(FormatEdit.Ledger.ORIGINATING_SCALE_SITE_NUMBER_REQUIRED, originatingScaleSiteNumber);
          Edits.assertNotNull(FormatEdit.Ledger.SCALE_REQUIRED, scale);
          if (flagIsYesAsLegacyCompared()) {
            Edits.assertNotNull(FormatEdit.Ledger.FIELD_SCALE_DECK_ID_PRESENT_WHEN_FLAG_Y, fieldScaleDeckId);
          }
        }
        default -> throw new IllegalStateException(eventType);
      }
    }
  }

  /** {@code DPLEntry}: DP. */
  public static final class DepartureEntry extends LedgerEntry {
    public String departureDateString;
    public String outgoingLdsNumber;
    public String destination;
    public String departureNumber;
    public String outgoingTransportIdentifier;

    /** {@code getDepartureDate}: lenient yyyyMMdd (validated strictly first). */
    public Date departureDate() {
      return LegacyDates.parseLenient(LegacyDates.DATE_FORMAT_NO_TIME, departureDateString);
    }

    @Override
    protected void checkFields() {
      if (!Document.DEPARTURE.equals(eventType)) {
        throw new IllegalStateException(eventType);
      }
      // the legacy DEPARTURE type had no field rules ("TODO: implement this")
    }

    @Override
    protected void validateDates() throws FormatEditException {
      try {
        LegacyDates.parseStrict(LegacyDates.DATE_FORMAT_NO_TIME, departureDateString);
      } catch (Exception e) {
        throw new FormatEditException(FormatEdit.Ledger.DEPARTURE_DATE_VALID);
      }
    }
  }
}
