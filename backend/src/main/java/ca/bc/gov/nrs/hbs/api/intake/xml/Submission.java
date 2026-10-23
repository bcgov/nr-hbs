package ca.bc.gov.nrs.hbs.api.intake.xml;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * An unmarshalled scale-data submission ({@code <HBS>}):
 * {@code dac.detail.xml.Submission} with its transmission header and the
 * document / ledger batches in the order B1012 processes them.
 */
public class Submission {

  /** Which mapping read the file ({@code B1012.getMappingName}). */
  public String schemaVersion;
  public XmlTransmission transmission;
  public final List<DocumentBatch> weighSlipBillables = new ArrayList<>();
  public final List<DocumentBatch> weighSlipRedTags = new ArrayList<>();
  public final List<DocumentBatch> weighSlipOthers = new ArrayList<>();
  public final List<DocumentBatch> pieceScales = new ArrayList<>();
  public final List<DocumentBatch> sampleScales = new ArrayList<>();
  public final List<DocumentBatch> otherTallies = new ArrayList<>();
  public final List<DocumentBatch> specialForestProducts = new ArrayList<>();
  public final List<LedgerBatch> arrivalLedgers = new ArrayList<>();
  public final List<LedgerBatch> departureLedgers = new ArrayList<>();

  /** Return-type codes (DacConstants.HBS_RETURN_TYPE_CODE_*). */
  static final String RETURN_TYPE_WEIGHT = "W";
  static final String RETURN_TYPE_PIECE = "P";
  static final String RETURN_TYPE_SAMPLE = "S";

  public int batchCount() {
    return weighSlipBillables.size() + weighSlipRedTags.size() + weighSlipOthers.size() + pieceScales.size()
        + sampleScales.size() + otherTallies.size() + arrivalLedgers.size() + departureLedgers.size()
        + specialForestProducts.size();
  }

  /** {@code getRecordCount}: documents plus ledger entries. */
  public long recordCount() {
    long count = 0;
    for (List<DocumentBatch> list : documentBatchLists()) {
      for (DocumentBatch b : list) {
        count += b.documents.size();
      }
    }
    for (LedgerBatch b : arrivalLedgers) {
      count += b.entries.size();
    }
    for (LedgerBatch b : departureLedgers) {
      count += b.entries.size();
    }
    return count;
  }

  private List<List<DocumentBatch>> documentBatchLists() {
    return List.of(weighSlipBillables, weighSlipRedTags, weighSlipOthers, pieceScales, sampleScales, otherTallies,
        specialForestProducts);
  }

  /**
   * {@code Submission.validate}: the creator (FXA03), the batch count (FXA01)
   * and an active detail trading-partner submitter for every scale site and
   * return type in the file (FXA02).
   */
  public void validate(ReferenceLookups lookups) throws FormatEditException {
    transmission.validate(lookups);
    Edits.assertEquals(FormatEdit.Transmission.BATCH_COUNT, transmission.batchCount, batchCount());
    Set<List<String>> sites = new LinkedHashSet<>();
    weighSlipBillables.forEach(b -> sites.add(List.of(b.header.scaleSite, RETURN_TYPE_WEIGHT)));
    weighSlipRedTags.forEach(b -> sites.add(List.of(b.header.scaleSite, RETURN_TYPE_WEIGHT)));
    weighSlipOthers.forEach(b -> sites.add(List.of(b.header.scaleSite, RETURN_TYPE_WEIGHT)));
    pieceScales.forEach(b -> sites.add(List.of(b.header.scaleSite, RETURN_TYPE_PIECE)));
    sampleScales.forEach(b -> sites.add(List.of(b.header.scaleSite, RETURN_TYPE_SAMPLE)));
    otherTallies.forEach(b -> sites.add(List.of(b.header.scaleSite, RETURN_TYPE_PIECE)));
    specialForestProducts.forEach(b -> sites.add(List.of(b.header.scaleSite, RETURN_TYPE_PIECE)));
    for (List<String> site : sites) {
      if (!lookups.activeDetailSubmitterExists(site.get(0), site.get(1))) {
        Edits.fail(FormatEdit.Transmission.TYPE);
      }
    }
  }

  /** {@code <transmission>}. */
  public static class XmlTransmission {
    public int batchCount;
    public char type;
    public String creator;
    public Parts.Software software;

    public boolean isProduction() {
      return type == 'P';
    }

    public boolean isTest() {
      return type == 'T';
    }

    /** First eight (or fewer) characters of the creator. */
    public String clientLocationNumber() {
      return creator.substring(0, creator.length() - 2);
    }

    /** Last two characters of the creator. */
    public String clientLocationCode() {
      return creator.substring(creator.length() - 2);
    }

    /** {@code validateCreator} (FXA03): 10 characters naming an existing client location. */
    void validate(ReferenceLookups lookups) throws FormatEditException {
      if (creator == null || creator.length() != 10) {
        Edits.fail(FormatEdit.Transmission.CREATOR);
      }
      if (!lookups.clientLocationExists(clientLocationNumber(), clientLocationCode())) {
        Edits.fail(FormatEdit.Transmission.CREATOR);
      }
    }
  }

  /** {@code <BatchHeader>}. */
  public static class BatchHeader {
    public String scaleSite;
    /** {@link LegacyDates#errorDate()} when the date did not parse (stored like that, then FBA02). */
    public Date fromScaleDate;
    public Date toScaleDate;
    public String submitterBatchId;
    public int documentCount;
    public int batchControlTotal;

    /** FBA01 scale site exists, FBA02 / FBA03 dates parsed. */
    void validate(ReferenceLookups lookups) throws FormatEditException {
      if (!lookups.scaleSiteExists(scaleSite)) {
        Edits.fail(FormatEdit.Batch.SCALE_SITE);
      }
      if (LegacyDates.errorDate().equals(fromScaleDate)) {
        throw new FormatEditException(FormatEdit.Batch.FROM_SCALE_DATE_VALID);
      }
      if (LegacyDates.errorDate().equals(toScaleDate)) {
        throw new FormatEditException(FormatEdit.Batch.TO_SCALE_DATE_VALID);
      }
    }
  }

  /** The kinds of document batch ({@code WSB}, {@code WSR}, ... ) and their batch control totals. */
  public enum BatchKind {
    WSB("W"), WSR("W"), WSO("W"), PPS("P"), PSS("S"), POT("P"), SFP("F");

    /** DTL_DOC_BATCH.DETAIL_SCALE_DOC_TYPE_CODE as B1012.processBatches set it. */
    public final String docTypeCode;

    BatchKind(String docTypeCode) {
      this.docTypeCode = docTypeCode;
    }
  }

  /** A batch of documents ({@code dac.detail.xml.Batch} subclasses). */
  public static class DocumentBatch {
    public final BatchKind kind;
    public BatchHeader header;
    public final List<Document> documents = new ArrayList<>();

    public DocumentBatch(BatchKind kind) {
      this.kind = kind;
    }

    /** {@code Batch.validate}: header, document count (FBA04), control total (FBA05/FBB05/FBC05). */
    public void validate(ReferenceLookups lookups) throws FormatEditException {
      header.validate(lookups);
      Edits.assertEquals(FormatEdit.Batch.DOCUMENT_COUNT, header.documentCount, documents.size());
      switch (kind) {
        case WSB, WSR -> {
          int gross = 0;
          for (Document d : documents) {
            gross += d instanceof WeighSlipDocument.Billable b ? b.weights.gross
                : ((WeighSlipDocument.RedTag) d).weights.gross;
          }
          Edits.assertEquals(FormatEdit.Batch.BATCH_CONTROL_TOTAL_GROSS_WEIGHT, header.batchControlTotal, gross);
        }
        case WSO, POT -> Edits.assertEquals(FormatEdit.Batch.BATCH_CONTROL_TOTAL_DOCUMENT_COUNT,
            header.batchControlTotal, header.documentCount);
        case PPS, PSS -> {
          int logs = 0;
          for (Document d : documents) {
            logs += d instanceof LogTallyDocument.PieceScale p ? p.pieceScaleMeasure.logCount
                : ((LogTallyDocument.SampleScale) d).pieceScaleMeasure.logCount;
          }
          Edits.assertEquals(FormatEdit.Batch.BATCH_CONTROL_TOTAL_LOG_COUNT, header.batchControlTotal, logs);
        }
        case SFP -> {
          int records = 0;
          for (Document d : documents) {
            records += ((LogTallyDocument.SpecialForestProduct) d).measure.detailRecordCount;
          }
          Edits.assertEquals(FormatEdit.Batch.BATCH_CONTROL_TOTAL_LOG_COUNT, header.batchControlTotal, records);
        }
        default -> throw new IllegalStateException(kind.name());
      }
    }
  }

  /** An arrival ({@code ARL}) or departure ({@code DPL}) ledger batch. */
  public static class LedgerBatch {
    /** "A" or "D" (B1012.ARRIVAL_/DEPARTURE_LEDGER_DOC_TYPE_CODE). */
    public final String docTypeCode;
    public BatchHeader header;
    public final List<LedgerEntry> entries = new ArrayList<>();

    public LedgerBatch(String docTypeCode) {
      this.docTypeCode = docTypeCode;
    }

    /** {@code LedgerBatch.validate}: header, entry count (FBA04), control total = count (FBC05). */
    public void validate(ReferenceLookups lookups) throws FormatEditException {
      header.validate(lookups);
      Edits.assertEquals(FormatEdit.Batch.DOCUMENT_COUNT, header.documentCount, entries.size());
      Edits.assertEquals(FormatEdit.Batch.BATCH_CONTROL_TOTAL_DOCUMENT_COUNT, header.batchControlTotal,
          header.documentCount);
    }
  }
}
