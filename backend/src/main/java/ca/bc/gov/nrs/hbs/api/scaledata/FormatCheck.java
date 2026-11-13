package ca.bc.gov.nrs.hbs.api.scaledata;

import ca.bc.gov.nrs.hbs.api.intake.xml.Document;
import ca.bc.gov.nrs.hbs.api.intake.xml.FormatEditException;
import ca.bc.gov.nrs.hbs.api.intake.xml.LedgerEntry;
import ca.bc.gov.nrs.hbs.api.intake.xml.ReferenceLookups;
import ca.bc.gov.nrs.hbs.api.intake.xml.Submission;
import ca.bc.gov.nrs.hbs.api.intake.xml.SubmissionUnmarshaller;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

/**
 * The dry run of {@code POST /submissions:validate}: the unpack's format edits
 * (the {@code validate} calls of {@code TransmissionUnpacker}, in the same
 * order and with the same stop rules) without writing anything. File-level
 * edits stop the check (the unpack rejects the whole file); a failed batch
 * skips its documents; a failed document doesn't stop the batch.
 *
 * <p>Not covered, because they need the file to be stored: the seven-year date
 * limit and the replacement-version checks done while composing versions, and
 * duplicates between documents of the same file. The detail edits (B1021) run
 * after the unpack and are not part of the dry run either.
 */
@Slf4j
final class FormatCheck {

  /** Result of a dry run. */
  record Report(boolean testFile, int batchCount, int documentCount, List<ApiErrorItem> errors) {
  }

  private final ReferenceLookups lookups;
  private final Function<String, String> messages;

  FormatCheck(ReferenceLookups lookups, Function<String, String> messages) {
    this.lookups = lookups;
    this.messages = messages;
  }

  Report check(byte[] xml) {
    List<ApiErrorItem> errors = new ArrayList<>();
    Submission submission;
    try {
      submission = new SubmissionUnmarshaller().unmarshal(xml);
      submission.validate(lookups); // FXA03, FXA01, FXA02
    } catch (FormatEditException fe) {
      errors.add(ApiErrorItem.transmission(fe.editNumber(), messages.apply(fe.editNumber())));
      return new Report(false, 0, 0, errors);
    }
    boolean test = submission.transmission != null && submission.transmission.isTest();
    int documents = 0;
    for (List<Submission.DocumentBatch> list : List.of(submission.weighSlipBillables, submission.weighSlipRedTags,
        submission.weighSlipOthers, submission.pieceScales, submission.sampleScales, submission.otherTallies,
        submission.specialForestProducts)) {
      for (Submission.DocumentBatch batch : list) {
        String type = batch.kind.docTypeCode;
        String batchNumber = batch.header == null ? null : batch.header.submitterBatchId;
        documents += batch.documents.size();
        try {
          batch.validate(lookups);
        } catch (FormatEditException fe) {
          errors.add(ApiErrorItem.batch(fe.editNumber(), messages.apply(fe.editNumber()), type, batchNumber));
          continue;
        }
        for (Document document : batch.documents) {
          try {
            document.validate(batch.header.scaleSite, lookups);
          } catch (FormatEditException fe) {
            errors.add(ApiErrorItem.document(fe.editNumber(), messages.apply(fe.editNumber()), type,
                upper(document.detailDocumentNumber), document.documentVersion));
          } catch (RuntimeException ex) {
            log.debug("Dry run: document {} could not be checked", document.detailDocumentNumber, ex);
            errors.add(ApiErrorItem.document(null, "The document could not be checked: " + ex.getMessage(), type,
                upper(document.detailDocumentNumber), document.documentVersion));
          }
        }
      }
    }
    for (List<Submission.LedgerBatch> list : List.of(submission.arrivalLedgers, submission.departureLedgers)) {
      for (Submission.LedgerBatch batch : list) {
        String batchNumber = batch.header == null ? null : batch.header.submitterBatchId;
        documents += batch.entries.size();
        try {
          batch.validate(lookups);
        } catch (FormatEditException fe) {
          errors.add(ApiErrorItem.batch(fe.editNumber(), messages.apply(fe.editNumber()), batch.docTypeCode,
              batchNumber));
          continue;
        }
        for (LedgerEntry entry : batch.entries) {
          try {
            entry.validate(batch.header.scaleSite);
          } catch (FormatEditException fe) {
            errors.add(ApiErrorItem.document(fe.editNumber(), messages.apply(fe.editNumber()), batch.docTypeCode,
                null, null));
          }
        }
      }
    }
    return new Report(test, submission.batchCount(), documents, errors);
  }

  private static String upper(String s) {
    return s == null ? null : s.toUpperCase();
  }
}
