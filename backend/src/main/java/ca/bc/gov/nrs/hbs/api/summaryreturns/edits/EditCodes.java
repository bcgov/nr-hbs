package ca.bc.gov.nrs.hbs.api.summaryreturns.edits;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The edit-error message codes the summary release edits raise
 * ({@code HBS_EDIT_ERR_MESSAGE_CODE}). Values are those of {@code $E/dac/HbsEditError},
 * {@code CrossReferenceEdit.LogTallyCommon.Log} (SPG / grade-schedule codes) and
 * {@code CrossReferenceEdit.LogTally.FIELD_DECK_CASE}; categories are on
 * {@link ca.bc.gov.nrs.hbs.api.summaryreturns.EditError}.
 */
public final class EditCodes {

  private EditCodes() {
  }

  /** {@code Arrays.asList} as legacy used it: {@code contains(null)} is false (List.of would throw). */
  private static List<String> list(String... values) {
    return Collections.unmodifiableList(Arrays.asList(values));
  }

  // ── Clients (SummaryScaleReturnValidator) ────────────────────────────────
  public static final String BT01 = "BT01";
  public static final String BT02 = "BT02";
  public static final String BT03 = "BT03";
  public static final String BT04 = "BT04";
  public static final String BT05 = "BT05";
  public static final String BT06 = "BT06";
  public static final String CT01 = "CT01";
  public static final String CT02 = "CT02";
  public static final String CT03 = "CT03";
  public static final String CT04 = "CT04";
  public static final String CT05 = "CT05";
  public static final String CT06 = "CT06";

  // ── Dates ────────────────────────────────────────────────────────────────
  public static final String SD02 = "SD02";
  public static final String SD03 = "SD03";
  public static final String SD04 = "SD04";
  public static final String SD05 = "SD05";
  public static final String SD13 = "SD13";

  // ── Rates (SummaryBillableScaleReturnValidator / PieceScaleValidator) ────
  public static final String SD08 = "SD08";
  public static final String SD10 = "SD10";
  public static final String SD11 = "SD11";
  public static final String SD12 = "SD12";
  public static final String SD15 = "SD15";
  public static final String SD17 = "SD17";

  // ── Timber mark / licence / stratum ──────────────────────────────────────
  public static final String TM01 = "TM01";
  public static final String TM02 = "TM02";
  public static final String TM04 = "TM04";
  public static final String TM06 = "TM06";
  public static final String TM07 = "TM07";
  public static final String TM08 = "TM08";
  public static final String TM09 = "TM09";
  public static final String TM10 = "TM10";
  public static final String TM11 = "TM11";
  public static final String TM12 = "TM12";
  public static final String RC01 = "RC01";
  public static final String RC02 = "RC02";
  public static final String SL01 = "SL01";
  public static final String SL02 = "SL02";
  public static final String ST01 = "ST01";
  public static final String FB01 = "FB01";
  public static final String SRT02 = "SRT02";

  // ── Return contents ──────────────────────────────────────────────────────
  public static final String RT01 = "RT01";
  public static final String RT02 = "RT02";
  public static final String LC01 = "LC01";
  public static final String NP01 = "NP01";
  public static final String NW01 = "NW01";
  public static final String LA00 = "LA00";
  public static final String QV00 = "QV00";
  public static final String SV00 = "SV00";
  public static final String SG03 = "SG03";
  public static final String SG04 = "SG04";
  public static final String SP01 = "SP01";
  public static final String NM02 = "NM02";
  public static final String NM03 = "NM03";
  public static final String NM04 = "NM04";
  public static final String SS04 = "SS04";
  public static final String SS05 = "SS05";
  public static final String DK00 = "DK00";
  public static final String CTB01 = "CTB01";
  public static final String CTB02 = "CTB02";

  // ── Cruise based / SRE codes (BT5884, BT 16523) ──────────────────────────
  public static final String CBSS00 = "CBSS00";
  public static final String CBSS01 = "CBSS01";
  public static final String CBSS02 = "CBSS02";
  public static final String ICB1C = "ICB1C";
  public static final String ICB1D = "ICB1D";
  public static final String ICB2MM = "ICB2MM";
  public static final String ICB4C = "ICB4C";
  public static final String ICB4D = "ICB4D";
  public static final String ISB1C = "ISB1C";
  public static final String ISB1D = "ISB1D";
  public static final String CSB1C = "CSB1C";
  public static final String CSB1D = "CSB1D";
  public static final String ICB789 = "ICB789";
  public static final String ISB789 = "ISB789";

  // ── Area based (BT15212) ─────────────────────────────────────────────────
  public static final String CBTP01 = "CBTP01";
  public static final String CBCV01 = "CBCV01";
  public static final String CBSD01 = "CBSD01";
  public static final String CBSD02 = "CBSD02";
  public static final String CBAF01 = "CBAF01";

  // ── CrossReferenceEdit.LogTallyCommon.Log (7012) ─────────────────────────
  /** {@code SPECIES_PRODUCT_GRADE_NOT_EXIT}. */
  public static final String SPG_NOT_FOUND = "SP01";
  /** {@code SPECIES_PRODUCT_GRADE_NOT_EFFECT}. */
  public static final String SPG_NOT_EFFECTIVE = "SP02";
  public static final String COASTAL_GRADE = "CG01";
  public static final String INTERIOR_GRADE = "IG01";
  public static final String COASTAL_GRADE_NOT_EFFECTIVE = "CG02";
  public static final String INTERIOR_GRADE_NOT_EFFECTIVE = "IG02";
  public static final String COASTAL_STRATUM = "CG03";
  public static final String INTERIOR_STRATUM = "IG03";
  public static final String WEIGH_COASTAL_STRATUM = "CG04";
  public static final String WEIGH_INTERIOR_STRATUM = "IG04";

  /** {@code CrossReferenceEdit.LogTally.FIELD_DECK_CASE} (BT 8253). */
  public static final String FIELD_DECK_CASE = "FSX03";

  // ── DacConstants (BT 16523) ──────────────────────────────────────────────
  public static final List<String> SCALE_BASED_CONIFEROUS_SRE_CODES = list("S", "A", "N");
  public static final List<String> SCALE_BASED_DECIDUOUS_SRE_CODES = list("S", "A", "N");
  public static final List<String> CRUISE_BASED_CONIFEROUS_SRE_CODES = list("B", "C", "N");
  public static final List<String> CRUISE_BASED_DECIDUOUS_SRE_CODES = list("B", "C", "N");
  public static final List<String> INTERIOR_CB_GRADES = list("7", "8");

  // ── dac/common/Constants ─────────────────────────────────────────────────
  public static final String COASTAL_SCHEDULE = "C";
  public static final String INTERIOR_SCHEDULE = "I";
  /** {@code Constants.NAME_MAX_LEN} (BT-4837 & 7264). */
  public static final int NAME_MAX_LEN = 60;
  /** {@code SpeciesProductGrade.Product.LOG}: one space. */
  public static final String PRODUCT_LOG = " ";
  /** {@code ScaleSite.SCALE_SITE_WASTE}. */
  public static final String SCALE_SITE_WASTE = "ZZZ";
  /** {@code QuantLicense.SCALE_LICENSE_CODE} / {@code WASTE_RESIDUE_LICENSE_CODE}. */
  public static final String SCALE_LICENSE = "S";
  public static final String WASTE_RESIDUE_LICENSE = "R";
  /** {@code AppraisalWorksheet.STATUS_CONFIRMED}. */
  public static final String WORKSHEET_CONFIRMED = "CNF";
  /** {@code TimberMark.UNREGULATED_BEACHCOMB2/3}. */
  public static final List<String> UNREGULATED_BEACHCOMB_MARKS = list("BCOMB2", "BCOMB3");
  /** {@code RatioHistories.TypeCode.DEFAULT}. */
  public static final String RATIO_TYPE_DEFAULT = "D";
}
