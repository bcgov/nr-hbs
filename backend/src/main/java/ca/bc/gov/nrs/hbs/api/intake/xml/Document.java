package ca.bc.gov.nrs.hbs.api.intake.xml;

/**
 * One scale document of a batch ({@code dac.detail.xml.Document}): weigh slip
 * (W), piece/other tally (P), sample tally (S) or SFP tally (F).
 *
 * <p>Each subclass ports the legacy per-event-type {@code Type} objects as
 * {@link #checkFields()} (which fields are required / not permitted) and
 * {@link #hashString()} (the string the submitter hashed into EC_Digest).
 */
public abstract sealed class Document permits WeighSlipDocument, LogTallyDocument {

  /** Event types ({@code DetailScaleDocumentVersion}). */
  public static final String PRIMARY_SCALE = "PS";
  public static final String FROM_SITE_DECK = "FD";
  public static final String TO_SITE_DECK = "TD";
  public static final String RED_TAG = "RT";
  public static final String REMOTE_RED_TAG = "RR";
  public static final String CHECK_SCALE = "CS";
  public static final String SAMPLE_SCALE = "SS";
  public static final String REMOTE_SAMPLE_SCALE = "RS";
  public static final String SB4_RESCALE = "4R";
  public static final String COMPANY_USE = "CU";
  public static final String PRIMARY_DEPARTURE = "PD";
  public static final String DEPARTURE = "DP";
  public static final String SCALED_BEFORE = "B4";

  /** {@code DDN} attribute. */
  public String detailDocumentNumber;
  /** {@code DV} attribute. */
  public int documentVersion;
  public String eventType;

  /** W, P, S or F ({@code getDocumentType}). */
  public abstract char documentType();

  /** {@code Type.validate}: throws {@link IllegalStateException} for an unknown event type. */
  protected abstract void checkFields() throws FormatEditException;

  /** {@code Type.getHashString}. */
  protected abstract String hashString();

  /** The digest method of the document's common block. */
  protected abstract Parts.Digest digest();

  public abstract String scaleSite();

  /** {@code Document.validate(scaleSite)}. */
  public abstract void validate(String batchScaleSite, ReferenceLookups lookups) throws FormatEditException;

  /** {@code getHashValue}: the hash total recomputed from the document's fields. */
  public String hashValue() {
    return HashValues.hashValue(digest().method, hashString());
  }

  /** {@code validateFieldInclusion}: an unknown event type is FXA00. */
  protected void validateFieldInclusion() throws FormatEditException {
    try {
      checkFields();
    } catch (IllegalStateException e) {
      Edits.fail(FormatEdit.Transmission.VALIDATION);
    }
  }

  /** {@code validateDocumentType}: an existing document of another type. */
  protected void validateDocumentType(String formatEdit, ReferenceLookups lookups) throws FormatEditException {
    String existing = lookups.existingDocumentType(detailDocumentNumber);
    if (existing == null) {
      return;
    }
    if (!existing.equals("W") && !existing.equals("P") && !existing.equals("S") && !existing.equals("F")) {
      // Legacy: IllegalStateException("Invald document type") stopped the run.
      throw new IllegalStateException("Invalid document type: " + existing);
    }
    if (existing.charAt(0) != documentType()) {
      Edits.fail(formatEdit);
    }
  }

  /** {@code validateUniqueness}: the same document number and version must not exist yet. */
  protected void validateUniqueness(String formatEdit, ReferenceLookups lookups) throws FormatEditException {
    if (lookups.versionExists(documentType(), detailDocumentNumber.toUpperCase(), documentVersion)) {
      Edits.fail(formatEdit);
    }
  }

  protected static IllegalStateException unknown(String eventType) {
    return new IllegalStateException(eventType);
  }
}
