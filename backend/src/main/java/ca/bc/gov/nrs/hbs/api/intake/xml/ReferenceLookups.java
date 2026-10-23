package ca.bc.gov.nrs.hbs.api.intake.xml;

/**
 * The database look-ups the legacy format edits made through EJB finders
 * while validating a submission. Implemented with plain SELECTs by
 * {@code intake.JdbcReferenceLookups}; tests use a stub.
 */
public interface ReferenceLookups {

  /** {@code Transmission.validateCreator}: {@code ClientReader.findClientLocation} found a row (FXA03). */
  boolean clientLocationExists(String clientNumber, String clientLocnCode);

  /**
   * {@code Submission.validateTradingPartnerSubmitter}: {@code
   * ClientProfileManager.findActiveDetailSubmittersByScaleSiteAndReturnCode}
   * returned at least one row (FXA02).
   */
  boolean activeDetailSubmitterExists(String scaleSite, String returnTypeCode);

  /** {@code BatchHeader.validateScaleSite}: the legacy scale-site entity exists (FBA01). */
  boolean scaleSiteExists(String scaleSite);

  /**
   * {@code Document.getExistingDocumentType}: DETAIL_SCALE_DOCUMENT_TYPE_CODE of
   * the document with this number, or null when there is none.
   */
  String existingDocumentType(String detailDocumentNumber);

  /**
   * {@code validateUniqueness}: a version row already exists for this
   * document number (upper case) and version, in the table of the document
   * type ({@code W} weigh slip, {@code S} sample tally, {@code F} SFP tally,
   * otherwise log tally).
   */
  boolean versionExists(char documentType, String detailDocumentNumber, long version);
}
