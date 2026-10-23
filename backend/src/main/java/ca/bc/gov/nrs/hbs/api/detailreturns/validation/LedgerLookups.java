package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

/**
 * Lookups only the arrival / departure ledger edits ({@link ArrivalLedgerValidator}, {@link DepartureLedgerValidator}) need. Production: {@link JdbcLedgerLookups}.
 */
public interface LedgerLookups {

  /**
   * FSX04: a field-scale estimate (summary return of type FLDSCLEST, category
   * FI, active, in ISS/RDY/ERR/HLD/LCK/CAN) exists for the field deck and
   * timber mark. Legacy {@code SummaryPieceScaleReturnVersionEntityHome.findFieldDeckId}
   * ({@code SummaryPieceScaleReturnVersionQuery.selectFieldDeckId}), non-empty.
   */
  boolean fieldScaleEstimateExists(String fieldScaleDeckId, String timberMark);
}
