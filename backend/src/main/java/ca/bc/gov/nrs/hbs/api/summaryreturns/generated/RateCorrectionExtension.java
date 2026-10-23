package ca.bc.gov.nrs.hbs.api.summaryreturns.generated;

import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnWriter;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * {@code summary.control.workflow.Release.extendRateCorrectionDetails} →
 * {@code SummarizationManager.extendRateCorrection} → {@code {WeighSlip,LogTally,SFPTally}Summarizer
 * .summarizeRateCorrection} → {@code {WeighSlip,LogTally,SFPTally}Extender.extendRateCorrection}: when a
 * generated CWR/AWP summary version is approved, every correctable detail document of the summary's
 * version 1 ({@code findCorrectable*(dcn, DacConstants.INITIAL_VERSION)}) is
 *
 * <ol>
 *   <li>moved ISS → AWP → RDY and its document to CWR ({@code DocumentExtender.releaseRateCorrection}),
 *       then RDY → LCK ({@code VersionState.extend});</li>
 *   <li>summarized onto the approved version ({@code applyWeighSlipToSummary}: a daily load per scale
 *       day; {@code applyLogTallyToSummary / applySFPTallyToSummary}: a piece segregation per
 *       species/product/grade, or per species/product/grade/NMV/mark for beachcomb tallies, and the
 *       version's log count);</li>
 *   <li>given a cancelling txn (CRT, CAN, CR) against the approved version, cancelling its latest
 *       invoiced original / replace txn ({@code getTxnToCancel}) with every segregation's volume,
 *       value and amounts negated, event sequence = MAX + 1 over the document
 *       ({@code getNextEventSequence}).</li>
 * </ol>
 *
 * <p>Then {@code DetailManagerBean.saveDetailExtensions} saves the summary version with its
 * segregations / daily loads and notations without audit ({@code updateGeneratedSummaryScaleReturnAndChildren}
 * = {@link SummaryReturnWriter#saveReturnAndChildren}(ret, user, false)), each detail document with
 * audit action LCK, and the cancelling txns with their segregations.
 *
 * <p>Legacy released the summary in memory before that save ({@code DocumentExtender.releaseScaleReturn}:
 * {@code clearEdits}, {@code release()}, VISIBILITY_IND 'Y'); in this port the caller reloads the version
 * and runs the release after this method, so the summary rows written here keep the CWR/AWP state.
 */
@Component
public class RateCorrectionExtension {

  /** {@code DacConstants.INITIAL_VERSION}: legacy always looked the correctable details up on version 1. */
  static final long INITIAL_VERSION = 1;
  /** {@code Constants.ACTION_TYPE_LOCK}: {@code saveDetailExtensions → updateDetailDocuments(.., LCK)}. */
  static final String ACTION_LOCK = "LCK";
  /** {@code TransactionCodes.Type.ORIGINAL / REPLACE_WITH_CHANGES / REPLACE_WITH_NO_CHANGES}. */
  private static final Set<String> DEBIT_TYPES = Set.of("ORG", "RWC", "RWN");

  private final DetailDocumentRepository repo;
  private final DetailDocumentWriter writer;
  private final SummaryReturnWriter summaryWriter;
  private final Audit audit;

  public RateCorrectionExtension(DetailDocumentRepository repo, DetailDocumentWriter writer,
      SummaryReturnWriter summaryWriter, Audit audit) {
    this.repo = repo;
    this.writer = writer;
    this.summaryWriter = summaryWriter;
    this.audit = audit;
  }

  /** {@code Release.extendRateCorrectionDetails}: weigh slips, log tallies or SFP tallies by the summary's type. */
  static DetailFamily family(SummaryReturn ret) {
    return switch (ret.type()) {
      case WEIGHT -> DetailFamily.WEIGH_SLIP;
      case PIECE -> ret.specialForestProduct() ? DetailFamily.SFP_TALLY : DetailFamily.LOG_TALLY;
      case SAMPLE -> throw new WorkflowValidationException(null,
          "Unknown scale return document type=[" + ret.type().code() + "]");
    };
  }

  public void extend(SummaryReturn awaitingApproval, String user) {
    DetailFamily f = family(awaitingApproval);
    List<DetailDocument> details = repo.correctableKeys(f, awaitingApproval.dcn(), INITIAL_VERSION).stream()
        .map(k -> repo.load(f, k)).toList();
    if (details.isEmpty()) {
      return; // legacy: if (details.hasNext()) { ... }
    }
    Timestamp now = audit.now();
    PieceSegregations segManager = null;
    if (f != DetailFamily.WEIGH_SLIP) {
      DetailDocument first = details.get(0);
      Row firstTxnToCancel = txnToCancel(first);
      if (f == DetailFamily.SFP_TALLY && repo.txnSegregations(f, firstTxnToCancel.lng("dtl_txn_id")).isEmpty()) {
        throw new IllegalStateException("Cancelled Transaction has no segregations (" + firstTxnToCancel + ")");
      }
      // getSegregationManager: chosen by the FIRST tally only (legacy), beachcomb with BT 5193's previous segs
      boolean beachComb = f == DetailFamily.LOG_TALLY && first.version.flag("beachcomb_ind");
      segManager = new PieceSegregations(awaitingApproval, user, now, beachComb,
          beachComb ? repo.pieceSegregationsOfDocument(firstTxnToCancel.lng("document_control_number")) : null,
          () -> summaryWriter.nextId(SummaryReturnWriter.SEGREGATION_SEQ));
    }

    List<Row> cancellingTxns = new ArrayList<>();
    List<List<Row>> cancellingSegs = new ArrayList<>();
    for (DetailDocument d : details) {
      Row toCancel = txnToCancel(d);
      // DocumentExtender.releaseRateCorrection
      DetailDocumentReset.transition(d, d.state::versionCancelAndReplaceWithNoChangesRequested);
      DetailDocumentReset.transition(d, d.state::documentCancelAndReplaceWithNoChangesRequested);
      DetailDocumentReset.transition(d, d.state::versionCancelAndReplaceWithNoChangesApproved);
      long eventSequence = repo.nextEventSequence(f, d.ddn());
      DetailDocumentReset.transition(d, d.state::extend);

      if (f == DetailFamily.WEIGH_SLIP) {
        applyWeighSlipToSummary(awaitingApproval, d, user, now);
      } else {
        for (Row log : repo.logs(f, d.ddn(), d.versionNo())) {
          segManager.addDetail(f, log);
        }
      }
      Row txn = cancellingTxn(f, awaitingApproval, d, toCancel, eventSequence, user, now);
      List<Row> segs = new ArrayList<>();
      for (Row old : repo.txnSegregations(f, toCancel.lng("dtl_txn_id"))) {
        segs.add(cancellingSegregation(f, old, txn, segManager, user, now));
      }
      cancellingTxns.add(txn);
      cancellingSegs.add(segs);
    }

    // DetailManagerBean.saveDetailExtensions
    summaryWriter.saveReturnAndChildren(awaitingApproval, user, false);
    for (DetailDocument d : details) {
      writer.update(d, user, ACTION_LOCK);
    }
    for (int i = 0; i < cancellingTxns.size(); i++) {
      writer.createTxn(f, cancellingTxns.get(i), cancellingSegs.get(i));
    }
  }

  /**
   * {@code DocumentExtender.getTxnToCancel}: the invoiced (ISS) original / replace txn with the highest
   * event sequence (BT10789).
   */
  static Row txnToCancel(DetailDocument d) {
    Row found = null;
    long highest = 0;
    for (Row t : d.txns) {
      if ("ISS".equals(t.str("hbs_txn_status_code")) && DEBIT_TYPES.contains(t.str("hbs_txn_type_code"))
          && highest < t.lng("event_sequence")) {
        highest = t.lng("event_sequence");
        found = t;
      }
    }
    if (found == null) {
      // legacy returned null here and failed further on (NullPointerException)
      throw new IllegalStateException("DocumentExtender.getTxnToCancel no txn found DetailScaleDocument=[" + d + "]");
    }
    return found;
  }

  /**
   * {@code {WeighSlip,LogTally,SFPTally}Extender.createCancellingTxn}. The interest reversal indicator
   * comes from {@code InterestReversalIndHelper(version)}, which re-read the document from the database:
   * its saved state is OCM / RNC / RCC (the correctable finder's condition), an "original or replace" →
   * {@code INTEREST_REVERSAL_OFF}.
   */
  private Row cancellingTxn(DetailFamily f, SummaryReturn summary, DetailDocument d, Row toCancel,
      long eventSequence, String user, Timestamp now) {
    return new Row()
        .set("dtl_txn_id", writer.nextId(f.txnSequence()))
        .set("hbs_txn_status_code", "CRT") // TransactionCodes.Status.CREATED
        .set("hbs_txn_type_code", "CAN") // TransactionCodes.Type.CANCEL
        .set("document_control_number", summary.dcn())
        .set("summary_document_version", summary.versionNo())
        .set("detail_document_number", d.ddn())
        .set("detail_document_version", d.versionNo())
        .set("dtl_txn_id_cancelled_by", toCancel.get("dtl_txn_id")) // sic: the cancelled txn's id
        .set("event_sequence", eventSequence)
        .set("debit_credit_type", "CR") // TransactionCodes.CREDIT
        .set("entry_userid", user).set("entry_timestamp", now)
        .set("update_userid", null).set("update_timestamp", null) // legacy "" = NULL in Oracle
        .set("interest_reversal_ind", "N");
  }

  /** {@code createCancellingTxnSegregations}: the old segregation with its quantities negated. */
  private Row cancellingSegregation(DetailFamily f, Row old, Row txn, PieceSegregations segManager, String user,
      Timestamp now) {
    Row seg = new Row()
        .set("dtl_txn_seg_id", writer.nextId(f.txnSegSequence()))
        .set("scale_species_code", old.get("scale_species_code"))
        .set("scale_product_code", old.get("scale_product_code"))
        .set("scale_grade_code", old.get("scale_grade_code"))
        .set("dtl_txn_id", txn.get("dtl_txn_id"))
        .set("txn_seg_id", null)
        .set("prorated_volume", negate(old.num("prorated_volume")))
        .set("prorated_value", negate(old.num("prorated_value")))
        .set("entry_userid", user).set("entry_timestamp", now)
        .set("update_userid", null).set("update_timestamp", null);
    if (f != DetailFamily.WEIGH_SLIP) {
      seg.set("dtlseg_id", null).set("segregation_id", segManager.segregationIdFor(f, old));
    }
    if (f == DetailFamily.WEIGH_SLIP || f == DetailFamily.LOG_TALLY) {
      seg.set("rate", old.get("rate")).set("rate_source", old.get("rate_source"))
          .set("reserve_stmpg_rate_component", old.get("reserve_stmpg_rate_component"))
          .set("bonus_bid_rate_component", old.get("bonus_bid_rate_component"))
          .set("dev_levy_rate_component", old.get("dev_levy_rate_component"))
          .set("silv_levy_rate_component", old.get("silv_levy_rate_component"))
          .set("reserve_stmpg_amount_component", negate(old.num("reserve_stmpg_amount_component")))
          .set("bonus_bid_amount_component", negate(old.num("bonus_bid_amount_component")))
          .set("dev_levy_amount_component", negate(old.num("dev_levy_amount_component")))
          .set("silv_levy_amount_component", negate(old.num("silv_levy_amount_component")));
    }
    if (f == DetailFamily.WEIGH_SLIP) {
      seg.set("weight_x_rate", negate(old.num("weight_x_rate")));
    }
    return seg;
  }

  private static BigDecimal negate(BigDecimal v) {
    return v == null ? null : v.negate();
  }

  /**
   * {@code WeighSlipExtender.applyWeighSlipToSummary}: the daily load of the slip's scale day (day of
   * month) gets one more load and the slip's net weight (gross − tare, 0 when either is missing).
   */
  private static void applyWeighSlipToSummary(SummaryReturn summary, DetailDocument slip, String user,
      Timestamp now) {
    LocalDateTime scaleDate = slip.version.date("scale_date").toLocalDateTime();
    long day = scaleDate.getDayOfMonth();
    Row load = summary.dailyLoads().stream().filter(l -> Long.valueOf(day).equals(l.lng("day"))).findFirst()
        .orElse(null);
    if (load == null) {
      // createDailyLoadRecordAndAddToSummary (Bug 823: no update timestamp)
      load = new Row().set("document_control_number", summary.dcn()).set("version", summary.versionNo())
          .set("day", day).set("number_of_loads", 0L).set("weight", 0L)
          .set("entry_userid", user).set("entry_timestamp", now).set("update_userid", null)
          .set("update_timestamp", null);
      summary.dailyLoads().add(load);
    } else {
      load.set("update_userid", user).set("update_timestamp", now); // findDailyLoadRecordAndUpdateAudits
    }
    Long loads = load.lng("number_of_loads");
    load.set("number_of_loads", loads == null ? 0L : loads + 1); // WeightScaleDailyLoadSummary.incrementLoads
    Long gross = slip.version.lng("gross_weight");
    Long tare = slip.version.lng("tare_weight");
    long net = gross != null && tare != null ? gross - tare : 0; // WeighSlip.getNetWeight
    Long weight = load.lng("weight");
    load.set("weight", weight == null ? net : weight + net);
    summary.version().set("update_timestamp", now).set("update_userid", user);
  }
}
