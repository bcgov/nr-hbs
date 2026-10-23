package ca.bc.gov.nrs.hbs.api.intake;

import ca.bc.gov.nrs.hbs.api.intake.xml.ReferenceLookups;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * The reads the unpack needs (plain SELECTs, every value bound). Rows are
 * {@code SELECT *} maps keyed by column name, so a store proc can be started
 * from them with {@code TableApi.argsFromRow}. Implemented by
 * {@link JdbcUnpackRepository}; tests use an in-memory stub.
 */
public interface UnpackRepository extends ReferenceLookups {

  /** A transmission received by the new app, waiting at RCD/CSA. */
  record Pending(long transmissionId, String recdDirectory, String recdClientNumber) {}

  /**
   * Transmissions created by the P505 upload of this app (RECD_DIRECTORY holds
   * a {@code SubmissionStorage} key {@code input/...}; FTP and legacy web
   * uploads hold share paths or null) waiting at RCD/CSA, oldest first.
   */
  List<Pending> pendingTransmissions();

  /** DTL_SCL_DOC_TRANSMISSION row, or null. */
  Map<String, Object> transmission(long transmissionId);

  /** DETAIL_SCALE_DOCUMENT row, or null. */
  Map<String, Object> detailScaleDocument(String detailDocumentNumber);

  /** VERSION and ORIGINAL_VERSION_IND of every version of a document, in its type's version table. */
  List<Map<String, Object>> versions(String docTypeCode, String detailDocumentNumber);

  /** One version row ({@code SELECT *}) of the document type's version table. */
  Map<String, Object> versionRow(String docTypeCode, String detailDocumentNumber, long version);

  /**
   * {@code findPaperBatchsByCriteria(PND, site, ..., from, to, count)}: the
   * first pending paper batch slip ({@code SELECT *}) for the site with the
   * same from / to scale days and document count, or null.
   */
  Map<String, Object> pendingPaperBatch(String scaleSite, Date fromScaleDate, Date toScaleDate, long documentCount);

  /** HBS_RSA_PADDING_SCHEME.HBS_RSA_PADDING_ID for a description (RSA_15, RSA_OAEP), or null. */
  Long rsaPaddingId(String description);
}
