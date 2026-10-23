package ca.bc.gov.nrs.hbs.api.summaryreturns.generated;

import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnWriter;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code PieceSegregationManager} with its two flavours: {@code PrimaryPieceSegregationManager}
 * (segregations keyed by species / product / grade, type PRIMARY) and
 * {@code BeachCombSegregationManager} (keyed by species / product / grade / NMV / timber mark, type
 * BCOMB). Summarizes log tally / SFP details into the piece version's segregations and log count,
 * and finds the segregation a cancelling txn segregation belongs to.
 *
 * <p>New segregations get their id at once ({@code segHelper.getId}, {@code PIECE_SCL_SEGREGATION_SEQ})
 * because the cancelling txn segregations reference it; {@link SummaryReturnWriter#saveReturnAndChildren}
 * inserts them.
 */
final class PieceSegregations {

  private final SummaryReturn summary;
  private final String user;
  private final Timestamp now;
  private final boolean beachComb;
  /** BT 5193: segregation id → segregation of every version of the summary (beachcomb only). */
  private final Map<Long, Row> previousSegs;
  private final Map<String, Row> byKey = new HashMap<>();
  private final IdSource ids;

  interface IdSource {
    long next();
  }

  PieceSegregations(SummaryReturn summary, String user, Timestamp now, boolean beachComb, List<Row> previous,
      IdSource ids) {
    this.summary = summary;
    this.user = user;
    this.now = now;
    this.beachComb = beachComb;
    this.ids = ids;
    if (previous != null) {
      previousSegs = new HashMap<>();
      previous.forEach(s -> previousSegs.put(s.lng("segregation_id"), s));
    } else {
      previousSegs = null;
    }
    // populateSegregationHashTable
    for (Row seg : summary.segregations()) {
      byKey.put(segKey(seg), seg);
    }
  }

  /** {@code PrimaryPieceDetailExtensionSegKey} / {@code BeachCombPieceDetailExtensionSegKey} (null → ""). */
  private String key(Object species, Object product, Object grade, boolean nmv, Object timberMark) {
    String k = text(species) + "|" + text(product) + "|" + text(grade);
    return beachComb ? k + "|" + (nmv ? "Y" : "N") + "|" + text(timberMark) : k;
  }

  private static String text(Object v) {
    return v == null ? "" : v.toString();
  }

  private String segKey(Row seg) {
    return key(seg.get("scale_species_code"), seg.get("scale_product_code"), seg.get("scale_grade_code"),
        seg.flag("nmv_ind"), seg.get("timber_mark"));
  }

  /**
   * {@code addDetail(LogTallyDetail)}: one piece and the log's net volume; {@code addDetail(SFPLogDetail)}:
   * the detail's piece count and its net volume (when given). Either way the version's log count goes up by
   * one ({@code incrementLogCount}).
   */
  void addDetail(DetailFamily f, Row log) {
    if (beachComb && f == DetailFamily.SFP_TALLY) {
      throw new UnsupportedOperationException("Special forest product details cannot be beachcomb.");
    }
    String k = key(log.get("scale_species_code"), log.get("scale_product_code"), log.get("scale_grade_code"),
        log.flag("nmv_ind"), log.get("timber_mark"));
    Row seg = byKey.get(k);
    if (seg == null) {
      seg = createMatchingSegregation(log);
      byKey.put(k, seg);
      summary.segregations().add(seg);
    } else {
      seg.set("update_userid", user).set("update_timestamp", now); // findMatchingSegregationAndUpdateAuditFields
    }
    long pieces;
    if (f == DetailFamily.SFP_TALLY) {
      Long count = log.lng("piece_count");
      if (count == null) {
        throw new IllegalStateException("SFP log detail " + log.get("logdtl_id") + " has no piece count");
      }
      pieces = count;
    } else {
      pieces = 1; // DETAIL_PIECE_COUNT
    }
    seg.set("number_of_pieces", nz(seg.num("number_of_pieces")).add(BigDecimal.valueOf(pieces)));
    Long logCount = summary.version().lng("log_count");
    summary.version().set("log_count", (logCount == null ? 0 : logCount) + 1);
    BigDecimal volume = log.num("net_volume");
    if (volume != null) {
      seg.set("volume", nz(seg.num("volume")).add(volume));
    }
  }

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }

  /** {@code createMatchingSegregation} + {@code setCategorySpecificSegFields}. */
  private Row createMatchingSegregation(Row log) {
    Row seg = new Row()
        .set("segregation_id", ids.next())
        .set("document_control_number", summary.dcn())
        .set("version", summary.versionNo())
        .set("psseg_type", SummaryReturnRules.SEG_TYPE_PRIMARY)
        .set("other_description_type_code", null) // legacy "" = NULL
        .set("scale_species_code", log.get("scale_species_code"))
        .set("scale_product_code", log.get("scale_product_code"))
        .set("scale_grade_code", log.get("scale_grade_code"))
        .set("timber_mark", null)
        .set("number_of_pieces", BigDecimal.ZERO)
        .set("volume", BigDecimal.ZERO)
        .set("nmv_ind", "N")
        .set("sb4_ind", "N")
        .set("avoidable_ind", null)
        .set("input_species", null).set("input_product", null).set("input_grade", null)
        .set("input_bcomb_timber_mark", null).set("archive_timber_mark", null).set("input_other_desc", null)
        .set("entry_userid", user).set("entry_timestamp", now)
        .set("update_userid", null).set("update_timestamp", null);
    if (beachComb) {
      seg.set("psseg_type", SummaryReturnRules.SEG_TYPE_BEACH_COMB)
          .set("nmv_ind", log.flag("nmv_ind") ? "Y" : "N")
          .set("timber_mark", log.get("timber_mark"));
    }
    return seg;
  }

  /**
   * {@code getSegregationIdForTransactionSegregation}: the segregation matching a txn segregation's
   * species / product / grade, or — beachcomb — the key of the summary segregation the txn segregation
   * pointed at.
   */
  long segregationIdFor(DetailFamily f, Row txnSeg) {
    String k;
    if (beachComb) {
      if (previousSegs == null) {
        throw new IllegalStateException("BeachCombSegregationManager.getKeyForHash  no previous scale return for txnSeg="
            + txnSeg);
      }
      Row prev = previousSegs.get(txnSeg.lng("segregation_id"));
      if (prev == null) {
        throw new IllegalStateException("No summary segregation " + txnSeg.get("segregation_id")
            + " for txnSeg=" + txnSeg);
      }
      k = segKey(prev);
    } else {
      k = key(txnSeg.get("scale_species_code"), txnSeg.get("scale_product_code"), txnSeg.get("scale_grade_code"),
          false, null);
    }
    Row seg = byKey.get(k);
    if (seg == null) {
      throw new IllegalStateException("PieceSegregationManager.getSegregationForTransactionSegregation "
          + " Unable to find segregation for txnSeg='" + txnSeg + "' key='" + k + "'");
    }
    return seg.lng("segregation_id");
  }
}
