package ca.bc.gov.nrs.hbs.api.intake;

import ca.bc.gov.nrs.hbs.api.intake.VersionComposer.ComposedVersion;
import ca.bc.gov.nrs.hbs.api.intake.xml.Document;
import ca.bc.gov.nrs.hbs.api.intake.xml.FormatEdit;
import ca.bc.gov.nrs.hbs.api.intake.xml.FormatEditException;
import ca.bc.gov.nrs.hbs.api.intake.xml.LedgerEntry;
import ca.bc.gov.nrs.hbs.api.intake.xml.LegacyDates;
import ca.bc.gov.nrs.hbs.api.intake.xml.LogTallyDocument;
import ca.bc.gov.nrs.hbs.api.intake.xml.Parts;
import ca.bc.gov.nrs.hbs.api.intake.xml.Submission;
import ca.bc.gov.nrs.hbs.api.intake.xml.SubmissionUnmarshaller;
import ca.bc.gov.nrs.hbs.api.intake.xml.WeighSlipDocument;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * The per-file processing of legacy B1012 ({@code B1012.process} and
 * everything it calls), for one transmission received by this app's P505
 * upload. It leaves the transmission exactly where legacy B1012 left it —
 * UNPACKING/SUCCESS (CSA) with its batches, batch documents, scale
 * documents and versions, or UNPACKING/FAILED (CUS) with a transmission
 * format error — so legacy B1021 and the rest of the batch carry on.
 *
 * <p>The caller ({@link UnpackService}) runs it in ONE transaction: the
 * intermediate UNPACKING/PROCESSING state is never committed, so legacy
 * B1012's {@code removeAbnormalTerminations} (which resets every UNP/PRC
 * transmission and deletes its batches) can never see a half-unpacked
 * transmission of ours.
 *
 * <p>Format edits are recorded and processing carries on, as in legacy; any
 * other exception rolls the whole transmission back (it stays RCD/CSA and
 * is retried on the next run).
 */
@Slf4j
public class TransmissionUnpacker {

  static final String STEP_RECEIVING = "RCD";
  static final String STEP_UNPACKING = "UNP";
  static final String STATUS_PROCESSING = "PRC";
  static final String STATUS_SUCCESS = "CSA";
  static final String STATUS_FAILED = "CUS";

  /** {@code DetailHbsEditError} format error tables. */
  enum ErrorLevel {
    TRANSMISSION("HBS_CREATE_TX_FMT_ERR", "i_transmission_id"),
    BATCH("HBS_CREATE_BATCH_FMT_ERR", "i_batch_id"),
    BATCH_DOCUMENT("HBS_CREATE_BATCH_DOC_FMT_ERR_2", "i_batdoc_id");

    final String proc;
    final String parentParam;

    ErrorLevel(String proc, String parentParam) {
      this.proc = proc;
      this.parentParam = parentParam;
    }
  }

  /** Outcome of one transmission, for the job summary and the tests. */
  public record Outcome(long transmissionId, String status, long documents, String formatError) {}

  /** The P505 submitter, for edit FXA04 (legacy read the user's WebADE roles at unpack time). */
  public record Submitter(boolean webUpload, boolean softwareVendor) {}

  private final UnpackContext ctx;
  private final SubmissionUnmarshaller unmarshaller = new SubmissionUnmarshaller();
  private final VersionComposer composer;
  private final DocumentWriter writer;

  TransmissionUnpacker(UnpackContext ctx) {
    this.ctx = ctx;
    this.composer = new VersionComposer(ctx);
    this.writer = new DocumentWriter(ctx);
  }

  /** {@code B1012.process(clientNumber, xmlDocument)}. */
  public Outcome process(long transmissionId, byte[] xml, Submitter submitter) {
    Map<String, Object> row = ctx.repository().transmission(transmissionId);
    if (row == null) {
      throw new IllegalStateException("No transmission found for id " + transmissionId);
    }
    ProcArgs tx = ctx.tableApi().argsFromRow("HBS_STORE_DTL_SCL_DOC_TRANS", row);
    if (!STEP_RECEIVING.equals(tx.get("i_hbs_xml_trans_step_code"))
        || !STATUS_SUCCESS.equals(tx.get("i_hbs_processing_status_code"))) {
      // Transmission.unpacking(): only from RECEIVING/SUCCESS.
      throw new IllegalStateException("Illegal transition from " + tx.get("i_hbs_xml_trans_step_code") + "/"
          + tx.get("i_hbs_processing_status_code") + " to UNP/PRC for transmission " + transmissionId);
    }
    // BT 427
    tx.put("i_update_userid", ctx.userId());
    tx.put("i_update_timestamp", ctx.now());
    setState(tx, STEP_UNPACKING, STATUS_PROCESSING);

    long documents = 0;
    try {
      Submission submission = unmarshaller.unmarshal(xml);
      submission.validate(ctx.repository()); // FXA03, FXA01, FXA02
      Submission.XmlTransmission t = submission.transmission;
      tx.put("i_batch_count", (long) t.batchCount);
      tx.put("i_client_locn_code", t.clientLocationCode());
      tx.put("i_client_number", t.clientLocationNumber());
      tx.put("i_record_count", submission.recordCount());
      tx.put("i_test_file_ind", t.isTest() ? "Y" : "N");
      if (t.software != null) { // MR28_SECURITY_CD
        tx.put("i_software_product", t.software.product);
        tx.put("i_software_version", t.software.version);
        tx.put("i_software_revision", t.software.revision);
      }
      ctx.call("HBS_STORE_DTL_SCL_DOC_TRANS", tx);
      boolean production = t.isProduction();

      // FXA04: a production file uploaded on the web by a software vendor (legacy: RECD_DIRECTORY
      // null for web uploads; here the storage key marks them).
      if (submitter.webUpload() && !t.isTest() && tx.get("i_input_userid") != null && submitter.softwareVendor()) {
        throw new FormatEditException(FormatEdit.Transmission.SOFTWARE_VENDOR);
      }

      documents += processBatches(submission.weighSlipBillables, transmissionId, production);
      documents += processBatches(submission.weighSlipRedTags, transmissionId, production);
      documents += processBatches(submission.weighSlipOthers, transmissionId, production);
      documents += processBatches(submission.pieceScales, transmissionId, production);
      documents += processBatches(submission.sampleScales, transmissionId, production);
      documents += processBatches(submission.otherTallies, transmissionId, production);
      documents += processBatches(submission.specialForestProducts, transmissionId, production);
      documents += processLedgerBatches(submission.arrivalLedgers, transmissionId);
      documents += processLedgerBatches(submission.departureLedgers, transmissionId);

      setState(tx, STEP_UNPACKING, STATUS_SUCCESS); // unpackComplete()
      return new Outcome(transmissionId, STATUS_SUCCESS, documents, null);
    } catch (FormatEditException fe) {
      error(ErrorLevel.TRANSMISSION, transmissionId, fe.editNumber(), null);
      setState(tx, STEP_UNPACKING, STATUS_FAILED); // unpackFailed()
      return new Outcome(transmissionId, STATUS_FAILED, documents, fe.editNumber());
    }
  }

  private void setState(ProcArgs tx, String step, String status) {
    tx.put("i_hbs_xml_trans_step_code", step);
    tx.put("i_hbs_processing_status_code", status);
    ctx.call("HBS_STORE_DTL_SCL_DOC_TRANS", tx);
  }

  // ── batches (processBatches / processBatch / matchPaperBatch) ──────────

  private long processBatches(List<Submission.DocumentBatch> batches, long transmissionId, boolean production) {
    long processed = 0;
    for (Submission.DocumentBatch batch : batches) {
      ProcArgs stored = createBatch(batch.header, transmissionId, batch.kind.docTypeCode);
      long batchId = ((Number) stored.get("i_batch_id")).longValue();
      try {
        batch.validate(ctx.repository());
        stored.put("i_scale_site_id_nmbr", batch.header.scaleSite);
        ctx.call("HBS_STORE_DTL_DOC_BATCH", stored);
        matchPaperBatch(stored);
        processed += processDocuments(batch.documents, batchId, batch.header.scaleSite, production);
      } catch (FormatEditException ve) {
        error(ErrorLevel.BATCH, batchId, ve.editNumber(), null);
      }
    }
    return processed;
  }

  private long processLedgerBatches(List<Submission.LedgerBatch> batches, long transmissionId) {
    long processed = 0;
    for (Submission.LedgerBatch batch : batches) {
      ProcArgs stored = createBatch(batch.header, transmissionId, batch.docTypeCode);
      long batchId = ((Number) stored.get("i_batch_id")).longValue();
      try {
        batch.validate(ctx.repository());
        stored.put("i_scale_site_id_nmbr", batch.header.scaleSite);
        ctx.call("HBS_STORE_DTL_DOC_BATCH", stored);
        matchPaperBatch(stored);
        processed += processEntries(batch.entries, batch.header.scaleSite, batchId);
      } catch (FormatEditException ve) {
        error(ErrorLevel.BATCH, batchId, ve.editNumber(), null);
      }
    }
    return processed;
  }

  /** {@code processBatch}: DTL_DOC_BATCH before validation, scale site unset, counts zero. */
  private ProcArgs createBatch(Submission.BatchHeader header, long transmissionId, String typeCode) {
    ProcArgs b = ProcArgs.of()
        .put("i_batch_id", ctx.nextId(UnpackContext.SEQ_BATCH))
        .put("i_dtldocbat_type", "DTLBAT")
        .put("i_transmission_id", transmissionId)
        .put("i_hbs_return_type_code", null)
        .put("i_scale_site_id_nmbr", null)
        .put("i_detail_scale_doc_type_code", typeCode)
        .put("i_batch_control_total", (long) header.batchControlTotal)
        .put("i_ready_count", 0L)
        .put("i_in_error_count", 0L)
        .put("i_from_scale_date", UnpackContext.ts(header.fromScaleDate))
        .put("i_to_scale_date", UnpackContext.ts(header.toScaleDate))
        .put("i_other_count", 0L)
        .put("i_return_count", (long) header.documentCount)
        .put("i_accepted_ind", "N")
        .put("i_submitter_batch_id", header.submitterBatchId)
        .put("i_entry_userid", ctx.userId())
        .put("i_entry_timestamp", ctx.now());
    ctx.call("HBS_CREATE_DTL_DOC_BATCH", b);
    return b;
  }

  /** {@code matchPaperBatch}: a pending paper batch slip with the same site, dates and count becomes MAT. */
  private void matchPaperBatch(ProcArgs batch) {
    java.util.Date from = (java.util.Date) batch.get("i_from_scale_date");
    java.util.Date to = (java.util.Date) batch.get("i_to_scale_date");
    Map<String, Object> paper = ctx.repository().pendingPaperBatch((String) batch.get("i_scale_site_id_nmbr"),
        from, to, ((Number) batch.get("i_return_count")).longValue());
    if (paper == null) {
      return;
    }
    ProcArgs args = ctx.tableApi().argsFromRow("HBS_STORE_HBS_PAPER_BATCH", paper)
        .put("i_dtl_doc_batch_id", batch.get("i_batch_id"))
        .put("i_paper_batch_status_code", "MAT")
        .put("i_update_timestamp", ctx.now())
        .put("i_update_userid", ctx.userId());
    ctx.call("HBS_STORE_HBS_PAPER_BATCH", args);
  }

  // ── documents (processDocuments / processBatchDocument / processDocument) ─

  private long processDocuments(List<Document> documents, long batchId, String scaleSite, boolean production) {
    long processed = 0;
    for (Document document : documents) {
      processed++;
      Parts.Software software = document instanceof WeighSlipDocument ws ? ws.common.software
          : ((LogTallyDocument) document).common.software;
      long batdocId = ctx.nextId(UnpackContext.SEQ_BATCH_DOCUMENT);
      ProcArgs batdoc = ProcArgs.of()
          .put("i_batdoc_id", batdocId)
          .put("i_batch_id", batchId)
          .put("i_accepted_ind", "N")
          .put("i_software_product", software.product)
          .put("i_software_version", software.version)
          .put("i_software_revision", software.revision)
          .put("i_detail_document_number", null) // the scale document does not exist yet
          .put("i_input_ddn", document.detailDocumentNumber.toUpperCase())
          .put("i_input_dv", Integer.toString(document.documentVersion))
          .put("i_entry_userid", ctx.userId())
          .put("i_entry_timestamp", ctx.now());
      ctx.call("HBS_CREATE_DTL_DOC_BATCH_DOC", batdoc);
      try {
        document.validate(scaleSite, ctx.repository());
        if (production) {
          ComposedVersion version = composer.compose(document);
          writer.writeDocumentVersion(version, batdocId);
          batdoc.put("i_accepted_ind", "Y");
          batdoc.put("i_detail_document_number", document.detailDocumentNumber.toUpperCase());
        }
        ctx.call("HBS_STORE_DTL_DOC_BATCH_DOC", batdoc);
      } catch (FormatEditException ve) {
        error(ErrorLevel.BATCH_DOCUMENT, batdocId, ve.editNumber(), documentInputs(document));
      } catch (ParseException pe) {
        // composeWS: "Scale date should have already been validated."
        throw new IllegalStateException("Scale date should have already been validated. " + pe.getMessage(), pe);
      }
    }
    return processed;
  }

  // ── ledger entries (processEntries) ───────────────────────────────────

  private long processEntries(List<LedgerEntry> entries, String scaleSite, long batchId) {
    long processed = 0;
    for (LedgerEntry entry : entries) {
      processed++;
      long batdocId = ctx.nextId(UnpackContext.SEQ_BATCH_DOCUMENT);
      // Legacy creates ledger entries for test files too (processEntries ignores "production").
      ProcArgs batdoc = ProcArgs.of()
          .put("i_batdoc_id", batdocId)
          .put("i_batch_id", batchId)
          .put("i_accepted_ind", "Y")
          .put("i_software_product", null)
          .put("i_software_version", null)
          .put("i_software_revision", null)
          .put("i_detail_document_number", null)
          .put("i_input_ddn", null)
          .put("i_input_dv", null)
          .put("i_entry_userid", ctx.userId())
          .put("i_entry_timestamp", ctx.now());
      ctx.call("HBS_CREATE_DTL_DOC_BATCH_DOC", batdoc);
      try {
        entry.validate(scaleSite);
        ComposedVersion version = switch (entry) {
          case LedgerEntry.ArrivalEntry a -> composer.arrival(a, batdocId);
          case LedgerEntry.DepartureEntry d -> composer.departure(d, batdocId);
        };
        writer.writeLedgerVersion(version);
        batdoc.put("i_detail_document_number", version.ddn());
        batdoc.put("i_entry_timestamp", ctx.now());
        batdoc.put("i_entry_userid", ctx.userId());
        ctx.call("HBS_STORE_DTL_DOC_BATCH_DOC", batdoc);
      } catch (FormatEditException ve) {
        error(ErrorLevel.BATCH_DOCUMENT, batdocId, ve.editNumber(), ledgerInputs(entry));
      }
    }
    return processed;
  }

  // ── format errors (validationError) ───────────────────────────────────

  /** What the batch-document error row shows of the rejected input (BT 7260). */
  record ErrorInputs(String scaleSite, String date, String primaryScalerLic, String weighSlipNumber,
      String source, String eventType, String returnNumber) {}

  private void error(ErrorLevel level, long parentId, String messageCode, ErrorInputs in) {
    ProcArgs a = ctx.nulls(level.proc)
        .put("i_error_id", ctx.nextId(UnpackContext.SEQ_EDIT_ERROR))
        .put("i_hbs_edit_err_category_code", FormatEdit.CATEGORY)
        .put("i_hbs_edit_err_message_code", messageCode)
        .put(level.parentParam, parentId)
        .put("i_error_timestamp", ctx.now());
    if (in != null) {
      a.put("i_input_scale_site", in.scaleSite())
          .put("i_input_date", in.date())
          .put("i_input_primary_scaler_lic", in.primaryScalerLic())
          .put("i_input_weigh_slip_number", in.weighSlipNumber())
          .put("i_input_source", in.source())
          .put("i_input_scale_event_type", in.eventType())
          .put("i_input_return_number", in.returnNumber());
    }
    ctx.call(level.proc, a);
  }

  static ErrorInputs documentInputs(Document document) {
    if (document instanceof WeighSlipDocument ws) {
      return new ErrorInputs(ws.common.scaleSite, dateString(ws.common.scaleDate), ws.common.primaryScalerLicence,
          Integer.toString(ws.common.weighSlipNumber), ws.sourceCode(), ws.eventType, null);
    }
    LogTallyDocument lt = (LogTallyDocument) document;
    return new ErrorInputs(lt.common.scaleSite, dateString(lt.common.scaleDate), lt.common.primaryScalerLicence,
        null, lt.source, lt.eventType, Integer.toString(lt.common.primaryScalerReturnNumber));
  }

  static ErrorInputs ledgerInputs(LedgerEntry entry) {
    if (entry instanceof LedgerEntry.ArrivalEntry a) {
      String lic = a.scale == null ? null : a.scale.primaryScalerLicence;
      String ret = a.scale == null ? null : Long.toString(a.scale.primaryScalerReturnNumber);
      String date = a.arrival == null ? null : a.arrival.arrivalDateString;
      return new ErrorInputs(a.scaleSite, date, lic, a.weighSlipNumber, a.source, a.eventType, ret);
    }
    LedgerEntry.DepartureEntry d = (LedgerEntry.DepartureEntry) entry;
    return new ErrorInputs(d.scaleSite, d.departureDateString, null, null, null, d.eventType, null);
  }

  /** {@code getScaleDateString()} (yyyyMMdd), null when the date did not parse. */
  private static String dateString(java.util.Date date) {
    return date == null ? null : LegacyDates.format(LegacyDates.DATE_FORMAT_NO_TIME, date);
  }
}
