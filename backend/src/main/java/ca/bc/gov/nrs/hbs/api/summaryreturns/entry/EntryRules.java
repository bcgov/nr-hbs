package ca.bc.gov.nrs.hbs.api.summaryreturns.entry;

import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.CLI_CRUISE_ADMIN;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.CLI_SCALER;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.CLI_SITE_ADMIN;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.SCALE_ADMIN;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.SPC_SUBM_AGNT;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.SUMM_DATA_CORR;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.SUMM_DATA_CTL;

import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryCaller;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Constants and pure checks of the summary-return data-entry screens
 * ({@code SummaryScaleReturnValidator} (view), {@code HBSValidatorHelper}, {@code StringUtil},
 * {@code DacValidator}, the add/update JSPs). No I/O; table-driven tests cover each one.
 */
public final class EntryRules {

  private EntryRules() {
  }

  /** {@code ScaleSite.SCALE_SITE_WASTE}: the hidden scale site of a waste (WSTRESEST) return. */
  public static final String WASTE_SCALE_SITE = "ZZZ";
  /** {@code TimberMark.UNREGULATED_BEACHCOMB2/3}: their Bill To comes from the scale site. */
  public static final List<String> BCOMB_SITE_BILLED_MARKS = List.of("BCOMB2", "BCOMB3");
  /** {@code PaperBatch.STATUS_CLEAR}. */
  public static final String PAPER_BATCH_CLEARED = "CLR";
  /** {@code SummaryScaleReturnValidator.DEFAULT_RATIO_TYPE} (BT14020). */
  public static final String DEFAULT_RATIO_TYPE = "D";
  /** {@code P050Form.MAX_AREA_CUT} (BT15212). */
  public static final BigDecimal MAX_AREA_CUT = new BigDecimal("9999.9");
  /** {@code HBSValidatorHelper.MAX_VOLUME} / {@code VOLUME_SCALE}. */
  public static final BigDecimal MAX_VOLUME = new BigDecimal("1000000.00");
  public static final int VOLUME_SCALE = 3;
  /** {@code SpeciesProductGrade.Product.LOG / XMAS_TREES}, {@code Grade.UNGRADED}. */
  public static final String PRODUCT_LOG = "LO";
  public static final String PRODUCT_XMAS_TREES = "XM";
  public static final String GRADE_UNGRADED = "U";
  /** The cut block id a waste return gets when the mark has none ({@code validateP050Form}, BT 5705). */
  public static final String NO_CUT_BLOCK = "None";
  /** {@code DailyLoadSummariesTag.numDays}. */
  public static final int DAYS_IN_PERIOD = 31;
  /** Notation type of the notations the screens add ({@code P058Action.addNotation}). */
  public static final String NOTATION_TYPE = "NOT";

  /** Return categories the add/update pages offered ({@code codefield values=…}). */
  public static final List<String> PIECE_DEFAULT_CATEGORIES =
      List.of(SummaryReturnRules.CATEGORY_PRIMARY, SummaryReturnRules.CATEGORY_BEACHCOMB);

  /**
   * The category the P050 page sent as a hidden field for each estimate type
   * ({@code add/P050.jsp}); a SMRYDOC return chooses PR or BC.
   */
  public static final Map<String, String> ESTIMATE_CATEGORIES = Map.of(
      SummaryReturnRules.PSSMRY_AREA_TYPE, SummaryReturnRules.CATEGORY_CRUISE,
      SummaryReturnRules.PSSMRY_CRUISE_TYPE, SummaryReturnRules.CATEGORY_CRUISE,
      SummaryReturnRules.PSSMRY_FIELD_TYPE, SummaryReturnRules.CATEGORY_FIELD_SCALE,
      SummaryReturnRules.PSSMRY_WASTE_TYPE, SummaryReturnRules.CATEGORY_WASTE,
      SummaryReturnRules.PSSMRY_OTHER_TYPE, SummaryReturnRules.CATEGORY_OTHER);

  /** Piece summary types the P002 menu could add (SFPSMRY returns come from XML submissions). */
  public static final List<String> ADDABLE_PIECE_TYPES = List.of(SummaryReturnRules.PSSMRY_DEFAULT_TYPE,
      SummaryReturnRules.PSSMRY_CRUISE_TYPE, SummaryReturnRules.PSSMRY_FIELD_TYPE,
      SummaryReturnRules.PSSMRY_WASTE_TYPE, SummaryReturnRules.PSSMRY_OTHER_TYPE,
      SummaryReturnRules.PSSMRY_AREA_TYPE);

  // ── Who may add (hbs_action_lnk.sql grants + the P002 menu) ──────────────

  /** {@code web:/dac/summary/add/P050.jsp} grants. */
  public static boolean mayAddPiece(SummaryCaller caller, String pssmryType) {
    if (SummaryReturnRules.PSSMRY_DEFAULT_TYPE.equals(pssmryType)) {
      return caller.hasAny(CLI_SCALER, CLI_SITE_ADMIN, SCALE_ADMIN, SUMM_DATA_CORR, SUMM_DATA_CTL, SPC_SUBM_AGNT);
    }
    if (SummaryReturnRules.PSSMRY_AREA_TYPE.equals(pssmryType)) {
      // P002 "Cruise Based Billing: Area" — ministry: P050 grant + VOLUME_ESTIMATES_LINK roles;
      // industry: the CLI_CRUISE_ADMIN role only (BT15212)
      return caller.ministry() ? caller.hasAny(SCALE_ADMIN, SUMM_DATA_CORR) : caller.has(CLI_CRUISE_ADMIN);
    }
    // P002 "Volume Estimate Return": P050 grant + SummaryReturnFieldsTag VOLUME_ESTIMATES_LINK roles
    return caller.hasAny(CLI_SCALER, CLI_SITE_ADMIN, SCALE_ADMIN, SUMM_DATA_CORR, SPC_SUBM_AGNT);
  }

  /** {@code web:/dac/summary/add/P100.jsp} and {@code P150.jsp} grants. */
  public static boolean mayAddWeightOrSample(SummaryCaller caller) {
    return caller.has(SUMM_DATA_CTL);
  }

  /** {@code web:/dac/summary/update/P103.jsp} and {@code P153.jsp} grants (P053 is the broad capability). */
  public static boolean mayOpenWeightOrSampleUpdate(SummaryCaller caller) {
    return caller.hasAny(SUMM_DATA_CTL, SUMM_DATA_CORR);
  }

  // ── Number checks ────────────────────────────────────────────────────────

  /** {@code SummaryScaleReturnValidator.isNumber}: an int greater than zero. */
  public static boolean isNumber(String s) {
    try {
      return s != null && Integer.parseInt(s) > 0;
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  /** {@code SummaryScaleReturnValidator.isNumeric}: a double ≥ 0. */
  public static boolean isNumeric(String s) {
    try {
      return s != null && Double.parseDouble(s) >= 0;
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  /** {@code StringUtil.allNumbers}: non-empty and every character a digit. */
  public static boolean allNumbers(String s) {
    if (s == null || s.isEmpty()) return false;
    for (int i = 0; i < s.length(); i++) {
      if (!Character.isDigit(s.charAt(i))) return false;
    }
    return true;
  }

  /** {@code isValidCharField}: at most {@code length} characters once trimmed. */
  public static boolean fits(String s, int length) {
    return s.trim().length() <= length;
  }

  /**
   * {@code HBSValidatorHelper.isValidPrecision}, defect included: while more than three decimals
   * remain, a trailing "0" drops the last two characters, so e.g. "1.12340" passes a 3-place check.
   */
  public static boolean isValidPrecision(String value, int decimalPlaces) {
    int index = value.indexOf('.');
    if (index < 0) return true;
    String right = value.substring(index + 1);
    int last = right.length() - 1;
    while (last > 2) {
      if (right.endsWith("0")) {
        right = right.substring(0, last - 1);
      }
      last--;
    }
    return right.length() <= decimalPlaces;
  }

  /** {@code SampleScaleValidator.isValidLA00}: an int ≥ 1. */
  public static boolean isValidLoadArrival(String value) {
    try {
      return value != null && Integer.parseInt(value) >= 1;
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  /** {@code PresentationHelper.convertToLong}: null for blank, NumberFormatException otherwise. */
  public static Long toLong(String s) {
    if (s == null || s.isEmpty()) return null;
    return Long.valueOf(s.trim());
  }

  /**
   * {@code HBSValidatorHelper.validateVolume}: the legacy message for a segregation volume, or null.
   * Christmas trees must have no volume; other products need 0 ≤ volume < 1,000,000 with at most
   * three decimals.
   */
  public static String volumeProblem(String product, String volume) {
    BigDecimal v;
    try {
      v = new BigDecimal(volume);
    } catch (RuntimeException ex) {
      v = null;
    }
    if (PRODUCT_XMAS_TREES.equals(product)) {
      return v != null && v.signum() > 0 ? EntryMessages.VOLUME_XMAS : null;
    }
    if (v == null) return EntryMessages.VOLUME_REQUIRED;
    if (v.signum() < 0) return EntryMessages.VOLUME_NEGATIVE;
    if (v.compareTo(MAX_VOLUME) >= 0) return EntryMessages.VOLUME_TOO_LARGE;
    if (!isValidPrecision(volume, VOLUME_SCALE)) return EntryMessages.VOLUME_DECIMALS;
    return null;
  }

  // ── Dates ────────────────────────────────────────────────────────────────

  /** The scale date (yyyy-MM-dd; a longer ISO value keeps its date part), or null when unreadable. */
  public static LocalDate parseDate(String value) {
    if (value == null) return null;
    String s = value.length() > 10 ? value.substring(0, 10) : value;
    try {
      return LocalDate.parse(s);
    } catch (DateTimeParseException ex) {
      return null;
    }
  }

  /** {@code DacValidator.compareForYearLimit}: before 1 January of the year seven years ago (BT6020). */
  public static boolean beforeYearLimit(LocalDate date) {
    return date.isBefore(LocalDate.of(LocalDate.now(Audit.ZONE).getYear() - 7, 1, 1));
  }

  public static Timestamp startOfDay(LocalDate date) {
    return Timestamp.valueOf(date.atStartOfDay());
  }
}
