package ca.bc.gov.nrs.hbs.api.summaryreturns;

import java.util.List;

/**
 * Codes the summary-return workflows switch on ({@code $E/dac/DacConstants},
 * {@code infrastructure/audit/Constants}).
 */
public final class SummaryReturnRules {

  private SummaryReturnRules() {
  }

  // ── PIECE_SCALE_SUMMARY.PSSMRY_TYPE ──────────────────────────────────────
  public static final String PSSMRY_DEFAULT_TYPE = "SMRYDOC";
  public static final String PSSMRY_CRUISE_TYPE = "CRSBSDEST";
  public static final String PSSMRY_FIELD_TYPE = "FLDSCLEST";
  public static final String PSSMRY_WASTE_TYPE = "WSTRESEST";
  public static final String PSSMRY_OTHER_TYPE = "OTVOLEST";
  public static final String PSSMRY_SFP_TYPE = "SFPSMRY";
  public static final String PSSMRY_AREA_TYPE = "AREAEST";

  // ── SCL_RTN_CATEGORY_CODE ────────────────────────────────────────────────
  public static final String CATEGORY_WASTE = "WA";
  public static final String CATEGORY_BEACHCOMB = "BC";
  public static final String CATEGORY_FIELD_SCALE = "FI";
  public static final String CATEGORY_PRIMARY = "PR";
  public static final String CATEGORY_CHECK_SCALE = "CH";
  public static final String CATEGORY_FINAL_BILL = "FB";
  public static final String CATEGORY_NIL_BILL = "NB";
  public static final String CATEGORY_CRUISE = "CR";
  public static final String CATEGORY_OTHER = "OT";

  /** {@code SummaryScaleReturnValidator} (view) return-category drop-downs. */
  public static final List<String> PIECE_CATEGORIES =
      List.of(CATEGORY_BEACHCOMB, CATEGORY_PRIMARY, CATEGORY_WASTE, CATEGORY_CRUISE, CATEGORY_FIELD_SCALE, CATEGORY_OTHER);
  public static final List<String> WEIGHT_CATEGORIES = List.of(CATEGORY_FINAL_BILL, CATEGORY_NIL_BILL, CATEGORY_PRIMARY);
  public static final List<String> SAMPLE_CATEGORIES = List.of(CATEGORY_CHECK_SCALE, CATEGORY_PRIMARY);

  // ── PIECE_SCL_SEGREGATION.PSSEG_TYPE (PieceScaleSegregation.TYPE_*) ──────
  public static final String SEG_TYPE_PRIMARY = "PRIMARY";
  public static final String SEG_TYPE_WASTE = "WASTE";
  public static final String SEG_TYPE_BEACH_COMB = "BCOMB";

  // ── HBS_AUDIT.ACTION_TYPE_CODE / HBS_OBJECT_TYPE_CODE ────────────────────
  public static final String ACTION_INSERT = "INS";
  public static final String ACTION_UPDATE = "UPD";
  public static final String ACTION_DELETE = "DEL";
  public static final String ACTION_REQUEST = "REQ";
  public static final String ACTION_RELEASE = "REL";
  public static final String ACTION_HOLD = "HLD";
  public static final String ACTION_CANCEL = "CAN";
  public static final String ACTION_REPLACE = "RRD";
  public static final String OBJECT_SUMMARY_SCALE = "SSR";
  public static final String OBJECT_SEGREGATION = "SEG";
  public static final String OBJECT_DAILY_LOAD = "DLS";
  public static final String OBJECT_NOTATION = "NOT";

  /** BT15212 notation number of the generated area-based notation ({@code AREA_BASED_NOTATION_NUMBER}). */
  public static final long AREA_BASED_NOTATION_NUMBER = 50;

  /**
   * The notation number {@code SummaryScaleReturnStateHelper.hold} looks for when it clears an
   * area-based return (BT15212). Legacy defect: it is 98, not 50, and when no 98 exists the
   * loop leaves the last notation selected and deletes that one. Ported as is (docs/questions.md Q12).
   */
  public static final long AREA_BASED_HOLD_NOTATION_NUMBER = 98;
}
