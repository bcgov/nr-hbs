package ca.bc.gov.nrs.hbs.api.summaryreturns.edits;

import static ca.bc.gov.nrs.hbs.api.summaryreturns.EditError.CATEGORY_FORMAT;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.EditError.CATEGORY_LOOKUP;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.EditError.CATEGORY_RELATED;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.EditError.CATEGORY_SCALING;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.EditError.CATEGORY_VALUE;

import ca.bc.gov.nrs.hbs.api.summaryreturns.EditError;
import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.ClientLocation;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.ScaleSite;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.TimberMark;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.VolumeEstimate;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.Worksheet;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** {@code $E/dac/summary/control/PieceScaleValidator}. */
class PieceEdits extends BillableEdits {

  PieceEdits(SummaryReturn ret, Lookups lookups, Rates rates, Clock clock) {
    super(ret, lookups, rates, clock);
  }

  // ── Version predicates (SummaryPieceScaleReturnVersion / PieceScaleSegregation) ──

  private String category() {
    return str("scl_rtn_category_code");
  }

  private String pssmryType() {
    return str("pssmry_type");
  }

  private boolean isCruise() {
    return SummaryReturnRules.CATEGORY_CRUISE.equals(category());
  }

  private boolean isBeachComb() {
    return SummaryReturnRules.CATEGORY_BEACHCOMB.equals(category());
  }

  private boolean isWasteReturn() {
    return SummaryReturnRules.PSSMRY_WASTE_TYPE.equals(pssmryType());
  }

  private boolean isAreaBased() {
    return SummaryReturnRules.PSSMRY_AREA_TYPE.equals(pssmryType());
  }

  /** {@code new Double(0.0).equals(getAreaCut())} (BT15212): an area-based return with no area cut is NTI. */
  private boolean areaBasedWithNoAreaCut() {
    BigDecimal areaCut = version.num("area_cut");
    return isAreaBased() && areaCut != null && areaCut.signum() == 0;
  }

  private boolean hasUnregulatedBeachCombMark() {
    return EditCodes.UNREGULATED_BEACHCOMB_MARKS.contains(str("timber_mark"));
  }

  private static boolean noMarkVisible(Row seg) {
    return seg.flag("nmv_ind");
  }

  private static Boolean indicator(Row seg, String column) {
    String v = seg.str(column);
    return v == null ? null : "Y".equals(v);
  }

  // ── Input fields ─────────────────────────────────────────────────────────

  /** {@code validateAndMoveInputFields}: header fields, segregation input fields, species/product/grade. */
  @Override
  protected void validateAndMoveInputFields() {
    validateFieldScaleDeckCase();
    validateAndMoveInputTimberMark();
    validateAndMoveInputScalersLicense();
    for (Row seg : ret.segregations()) {
      validateAndMoveSegregationTimberMark(seg);
      validateAndMoveSegregationInputOtherDescription(seg);
    }
    validateSpeciesProductGrade();
  }

  /** {@code validateFieldScaleDeckCase} (BT 8253): FSX03 when a field-scale deck id is not upper-case alphanumeric. */
  private void validateFieldScaleDeckCase() {
    String deck = str("field_scale_deck_id");
    if (SummaryReturnRules.CATEGORY_FIELD_SCALE.equals(category()) && notEmpty(deck)) {
      boolean alphaNumeric = deck.chars().allMatch(Character::isLetterOrDigit);
      if (!alphaNumeric || !deck.equals(deck.toUpperCase(Locale.ROOT))) {
        versionError(CATEGORY_SCALING, EditCodes.FIELD_DECK_CASE);
      }
    }
  }

  /** {@code validateAndMoveInputTimberMark}: TM01. */
  private void validateAndMoveInputTimberMark() {
    if (empty(str("timber_mark"))) {
      if (onFileTimberMark(str("input_timber_mark"))) {
        version.set("timber_mark", str("input_timber_mark"));
      } else {
        versionError(CATEGORY_LOOKUP, EditCodes.TM01);
      }
    }
  }

  /**
   * {@code validateAndMoveInputScalersLicense}: SL01; for an accepted or in-error waste return
   * (BT 5705) the licence type becomes S when a scaler licence exists, else R, and a missing
   * licence is SL02.
   */
  private void validateAndMoveInputScalersLicense() {
    String input = str("input_scaler_license_num");
    if (!empty(str("license_number")) || empty(input)) return;
    String state = str("scl_rtn_version_state_code");
    if (SummaryReturnRules.CATEGORY_WASTE.equals(category())
        && (ScaleReturnState.ACCEPTED.equals(state) || ScaleReturnState.ERROR.equals(state))) {
      String type = lookups.quantLicenseExists(input, EditCodes.SCALE_LICENSE)
          ? EditCodes.SCALE_LICENSE : EditCodes.WASTE_RESIDUE_LICENSE;
      version.set("quant_lic_type_cd", type);
      if (lookups.quantLicenseExists(input, type)) {
        version.set("license_number", input);
      } else {
        versionError(CATEGORY_FORMAT, EditCodes.SL02);
      }
    } else if (lookups.quantLicenseExists(input, str("quant_lic_type_cd"))) {
      version.set("license_number", input);
    } else {
      versionError(CATEGORY_LOOKUP, EditCodes.SL01);
    }
  }

  /** {@code validateAndMoveSegregationTimberMark}: a beachcomb segregation takes its on-file input mark (BM01 removed, issue 6133). */
  private void validateAndMoveSegregationTimberMark(Row seg) {
    if (isBeachComb() && empty(seg.str("timber_mark")) && onFileTimberMark(seg.str("input_bcomb_timber_mark"))) {
      seg.set("timber_mark", seg.str("input_bcomb_timber_mark"));
    }
  }

  /** {@code validateAndMoveSegregationInputOtherDescription}: SP01 (segregation, LKP). */
  private void validateAndMoveSegregationInputOtherDescription(Row seg) {
    String input = seg.str("input_other_desc");
    if (empty(seg.str("other_description_type_code")) && notEmpty(input)) {
      if (lookups.otherDescriptionTypeExists(input)) {
        seg.set("other_description_type_code", input);
      } else {
        segregationError(seg, CATEGORY_LOOKUP, EditCodes.SP01);
      }
    }
  }

  // ── validate() ───────────────────────────────────────────────────────────

  /**
   * {@code validate}: header; an area-based return with area cut 0 stops there (NTI, BT15212);
   * rates; area-based mark and area edits; waste rate; segregations (not for area-based returns,
   * whose segregations are generated after the release); cut block of a waste return.
   */
  @Override
  protected void validate() {
    validateHeader();
    if (areaBasedWithNoAreaCut()) return;
    validateRate();
    if (isAreaBased()) {
      validateAreaBasedTimberMark();
      validateAreaCut();
    }
    validateWasteRate();
    if (!isAreaBased()) {
      validateSegregations();
    }
    if (notEmpty(str("cut_block_id")) && isWasteReturn()) {
      validateCutBlockId();
    }
  }

  private void validateHeader() {
    validateHeaderTimberMark();
    validateClients();
    validateScaleDate();
    validateHarvestDate();
    if (areaBasedWithNoAreaCut()) return;
    validateWasteScaleSite();
    validateDuplicateVolumeEstimates();
    validateCruiseBasedScaleSite();
  }

  /**
   * {@code validateHeaderTimberMark}: RC02 (beachcomb mark / category mismatch), else TM04 (cash
   * sale), else TM10 + TM12 (area-based mark, with a confirmed appraisal), else TM09 (cruise-based
   * mark on a non-cruise return) / TM06 (cruise return on a scale-based mark); then the stand-rate
   * eligibility codes of the first confirmed worksheet (BT5884, BT 16523).
   */
  private void validateHeaderTimberMark() {
    String markCode = str("timber_mark");
    if (empty(markCode)) return;
    List<Worksheet> appraisals = lookups.confirmedWorksheets(markCode, scaleDate().toLocalDate());
    TimberMark mark = requiredTimberMark(markCode, "Previously validated mark not found!");
    if ((mark.isBeachComb() && !isBeachComb()) || (!mark.isBeachComb() && isBeachComb())) {
      versionError(CATEGORY_RELATED, EditCodes.RC02);
    } else if (mark.isCashSale()) {
      versionError(CATEGORY_RELATED, EditCodes.TM04);
    } else if (mark.isAreaBased()) {
      versionError(CATEGORY_RELATED, EditCodes.TM10);
      if (!appraisals.isEmpty()) {
        versionError(CATEGORY_RELATED, EditCodes.TM12);
      }
    } else if (mark.isCruiseBased()) {
      if (!isCruise()) versionError(CATEGORY_RELATED, EditCodes.TM09);
    } else if (isCruise()) {
      versionError(CATEGORY_RELATED, EditCodes.TM06);
    }
    if (mark.isPrivateMark() || appraisals.isEmpty()) return;
    Worksheet ws = appraisals.get(0);
    if (EditCodes.INTERIOR_SCHEDULE.equals(ws.appraisalMthdCd())) {
      if (isCruise() && mark.isCruiseBased()) {
        Optional<LocalDateTime> policy = lookups.interiorMpbPolicyDate();
        if (scaleDate() != null && policy.isPresent()) {
          if (scaleDate().isBefore(policy.get())) {
            validateScaleBasedSreCodes(ws, EditCodes.ICB4C, EditCodes.ICB4D);
          } else {
            validateCruiseBasedSreCodes(ws);
          }
        }
      } else if (!isCruise() && !mark.isCruiseBased()) {
        validateScaleBasedSreCodes(ws, EditCodes.ISB1C, EditCodes.ISB1D);
      }
    } else if (EditCodes.COASTAL_SCHEDULE.equals(ws.appraisalMthdCd())) {
      validateScaleBasedSreCodes(ws, EditCodes.CSB1C, EditCodes.CSB1D);
    }
  }

  /** {@code validateCruiseBasedSRECodes}: ICB1C, ICB1D, ICB2MM. */
  private void validateCruiseBasedSreCodes(Worksheet ws) {
    String conif = ws.conifStandRateEligibilityCode();
    String decid = ws.decidStandRateEligibilityCode();
    if (!EditCodes.CRUISE_BASED_CONIFEROUS_SRE_CODES.contains(conif)) {
      versionError(CATEGORY_SCALING, EditCodes.ICB1C);
    }
    if (!EditCodes.CRUISE_BASED_DECIDUOUS_SRE_CODES.contains(decid)) {
      versionError(CATEGORY_SCALING, EditCodes.ICB1D);
    }
    if (!"N".equals(decid)) {
      if (decid == null) {
        throw legacyFailure("validateCruiseBasedSRECodes: null DECID_STAND_RATE_ELIG_CODE for " + ws.timberMark());
      }
      if (!decid.equals(conif)) versionError(CATEGORY_SCALING, EditCodes.ICB2MM);
    }
  }

  /** {@code validateClients}: bill-to and (unless an unregulated beachcomb mark without copy-to) copy-to. */
  @Override
  protected void validateClients() {
    validateBillToClientAndLocation(deriveBillToClientLocation());
    if (!hasUnregulatedBeachCombMark() || notEmpty(str("client_number_copied_to"))) {
      validateCopyToClientAndLocation(deriveCopyToClientLocation());
    }
  }

  /** {@code deriveBillToClientLocation} (BT10941): paid-by, else the scale site owner (unregulated beachcomb) or the mark's client. */
  private ClientLocation deriveBillToClientLocation() {
    if (empty(str("client_number_paid_by"))) {
      return hasUnregulatedBeachCombMark() ? scaleSiteOwner() : deriveBillToClientAndLocationFromTimberMark();
    }
    return fetchClientLocation(str("client_number_paid_by"), str("client_locn_code_paid_by"));
  }

  /** {@code deriveCopyToClientLocation}: copied-to, else the scale site owner. */
  private ClientLocation deriveCopyToClientLocation() {
    if (empty(str("client_number_copied_to"))) {
      return scaleSiteOwner();
    }
    return fetchClientLocation(str("client_number_copied_to"), str("client_locn_code_copied_to"));
  }

  /** {@code getScaleSiteOwner}: null when the site is not found. */
  private ClientLocation scaleSiteOwner() {
    return scaleSite().map(s -> fetchClientLocation(s.ownerCliNumber(), s.ownerCliLocnCd())).orElse(null);
  }

  /** {@code validateScaleDate}: SD03 when the scale date is after today. */
  private void validateScaleDate() {
    if (!scaleDateNotInFuture()) {
      versionError(CATEGORY_RELATED, EditCodes.SD03);
    }
  }

  /**
   * {@code validateHarvestDate} (BT15212, area-based without SD03): CBSD01 when the scale date is
   * neither a month end nor the expiry of the covering confirmed worksheet; CBSD02 when another
   * active return of the mark has the same scale date.
   */
  private void validateHarvestDate() {
    if (!isAreaBased() || hasError(CATEGORY_RELATED, EditCodes.SD03)) return;
    if (!lookups.areaBasedHarvestDateValid(ret.dcn(), ret.versionNo())) {
      versionError(CATEGORY_SCALING, EditCodes.CBSD01);
    }
    if (lookups.areaBasedSameScaleDateCount(ret.dcn(), ret.versionNo()) > 1) {
      versionError(CATEGORY_SCALING, EditCodes.CBSD02);
    }
  }

  /** {@code validateWasteScaleSite}: SS04 (waste return not at ZZZ) / SS05 (ZZZ for a non-waste return). */
  private void validateWasteScaleSite() {
    boolean wasteSite = EditCodes.SCALE_SITE_WASTE.equals(str("scale_site_id_nmbr"));
    if (isWasteReturn() && !wasteSite) {
      versionError(CATEGORY_RELATED, EditCodes.SS04);
    } else if (!isWasteReturn() && wasteSite) {
      versionError(CATEGORY_RELATED, EditCodes.SS05);
    }
  }

  /**
   * {@code validateDuplicateVolumeEstimates}: DK00 when a volume estimate (not SMRYDOC / AREAEST, not
   * waste category, BT 5705) is entered after another active estimate of another document with the
   * same type, scale day, licence and return number.
   */
  private void validateDuplicateVolumeEstimates() {
    if (SummaryReturnRules.PSSMRY_DEFAULT_TYPE.equals(pssmryType()) || isAreaBased()
        || str("license_number") == null || scaleDate() == null || str("return_number") == null) {
      return;
    }
    List<VolumeEstimate> estimates = lookups.activeVolumeEstimates(pssmryType(), scaleDate().toLocalDate(),
        str("license_number"), str("return_number"));
    if (SummaryReturnRules.CATEGORY_WASTE.equals(category()) || estimates.size() <= 1) return;
    LocalDateTime entered = Lookups.dateTime(version, "entry_timestamp");
    for (VolumeEstimate estimate : estimates) {
      if (ret.dcn() != estimate.documentControlNumber() && entered.isAfter(estimate.entryTimestamp())) {
        versionError(CATEGORY_FORMAT, EditCodes.DK00);
        break;
      }
    }
  }

  /**
   * {@code validateCruiseBasedScaleSite}: a cruise return needs a dedicated cruise-based site (CBSS01)
   * in the mark's district (CBSS02); any other return must not use one (CBSS00).
   */
  private void validateCruiseBasedScaleSite() {
    ScaleSite site = requiredScaleSite();
    boolean dedicated = site.isDedicatedCruiseBasedSite();
    if (isCruise()) {
      if (!dedicated) {
        versionError(CATEGORY_SCALING, EditCodes.CBSS01);
      } else {
        TimberMark mark = requiredTimberMark(str("timber_mark"), "Previously validated mark not found!");
        if (site.orgUnitNo() == null || mark.forestDistrict() == null) {
          throw legacyFailure("validateCruiseBasedScaleSite: null ORG_UNIT_NO / FOREST_DISTRICT");
        }
        if (site.orgUnitNo().intValue() != mark.forestDistrict()) {
          versionError(CATEGORY_SCALING, EditCodes.CBSS02);
        }
      }
    } else if (dedicated) {
      versionError(CATEGORY_SCALING, EditCodes.CBSS00);
    }
  }

  // ── Rates ────────────────────────────────────────────────────────────────

  /** {@code getScaleDateRange}: the scale day. */
  @Override
  protected LocalDateTime[] scaleDateRange() {
    return new LocalDateTime[] {startDay(scaleDate()), endDay(scaleDate())};
  }

  @Override
  protected boolean ratesExist() throws Rates.RateException {
    return rates.pieceScaleReturnHasRates(ret);
  }

  /** {@code validateWasteRate}: SD15 when a waste return has an avoidable segregation and no waste rate. */
  private void validateWasteRate() {
    if (!isWasteReturn()) return;
    boolean avoidable = ret.segregations().stream().anyMatch(s -> s.flag("avoidable_ind"));
    if (avoidable && !wasteRateExists()) {
      versionError(CATEGORY_RELATED, EditCodes.SD15);
    }
  }

  /** {@code wasteRateExists}: false without a cut block or mark. */
  private boolean wasteRateExists() {
    String cutBlock = str("cut_block_id");
    String mark = str("timber_mark");
    if (cutBlock == null || mark == null || scaleDate() == null) return false;
    LocalDateTime[] range = scaleDateRange();
    return lookups.hasWasteRate(cutBlock, mark, range[0], range[1]);
  }

  // ── Area based (BT15212) ─────────────────────────────────────────────────

  /**
   * {@code validateAreaBasedTimberMark} (unless SD08): CBTP01 without a cruise profile; CBCV01 when
   * the worksheets' volume per hectare does not match the profile (the maximum when there are no
   * changed circumstances, else the covering worksheet's).
   */
  private void validateAreaBasedTimberMark() {
    if (hasError(CATEGORY_RELATED, EditCodes.SD08)) return;
    double cbpM3Ha = lookups.areaBasedCruiseProfile(ret.dcn(), ret.versionNo());
    if (cbpM3Ha == 0) {
      versionError(CATEGORY_SCALING, EditCodes.CBTP01);
      return;
    }
    Optional<Lookups.WorksheetVals> vals = lookups.areaBasedWorksheetVals(ret.dcn(), ret.versionNo());
    if (vals.isEmpty()) return;
    if (vals.get().diffM3Ha() == 0) {
      if (vals.get().maxM3Ha() != cbpM3Ha) {
        versionError(CATEGORY_SCALING, EditCodes.CBCV01);
      }
    } else {
      double volHa = lookups.areaBasedActiveWorksheetVolumePerHectare(ret.dcn(), ret.versionNo());
      if (Math.abs(volHa) != cbpM3Ha) {
        versionError(CATEGORY_SCALING, EditCodes.CBCV01);
      }
    }
  }

  /** {@code validateAreaCut}: CBAF01 when the area cut to date exceeds the appraised merchantable area. */
  private void validateAreaCut() {
    double totalAreaCut = lookups.areaBasedTotalAreaCut(ret.dcn(), ret.versionNo());
    double merchantable = lookups.areaBasedTotalMerchantableArea(ret.dcn(), ret.versionNo());
    if (totalAreaCut > merchantable) {
      versionError(CATEGORY_SCALING, EditCodes.CBAF01);
    }
  }

  // ── Segregations ─────────────────────────────────────────────────────────

  /** {@code validateGeneratedSegregations} (BT15212). */
  List<EditError> validateGeneratedSegregations() {
    validateSegregations();
    return errors;
  }

  /**
   * {@code validateSegregations}: per segregation NM03, ICB789 / ISB789, the beachcomb mark edits
   * (NM02, NM04, TM02) or RC01, NP01, QV00, SG03 (duplicate key); then LC01 (piece count ≠ log
   * count) or SG04 (no segregations). BM04 / BM05 are commented out in legacy (issue 569) and are
   * not ported.
   */
  private void validateSegregations() {
    TimberMark mark = requiredTimberMark(str("timber_mark"), "Previously validated mark not found!");
    String gradeSchedule = gradeScheduleType(str("timber_mark"));
    Optional<LocalDateTime> policy = lookups.interiorMpbPolicyDate();
    long totalPieces = 0;
    Set<List<Object>> keys = new HashSet<>();
    for (Row seg : ret.segregations()) {
      Long pieces = seg.lng("number_of_pieces");
      if (pieces != null) totalPieces += pieces;
      if (noMarkVisible(seg) && !EditCodes.PRODUCT_LOG.equals(seg.str("scale_product_code"))) {
        segregationError(seg, CATEGORY_RELATED, EditCodes.NM03);
      }
      validateCruiseBasedGrades(seg, mark, gradeSchedule, policy.orElse(null));
      if (isBeachComb()) {
        validateBeachCombSegregationTimberMark(seg);
      } else if (notEmpty(seg.str("timber_mark"))) {
        segregationError(seg, CATEGORY_RELATED, EditCodes.RC01);
      }
      if (invalidNumberOfPieces(pieces)) {
        segregationError(seg, CATEGORY_VALUE, EditCodes.NP01);
      }
      if (seg.get("volume") == null) {
        segregationError(seg, CATEGORY_FORMAT, EditCodes.QV00);
      }
      if (seg.str("scale_species_code") != null || seg.str("scale_product_code") != null
          || seg.str("scale_grade_code") != null) {
        if (!keys.add(segKey(seg))) {
          segregationError(seg, CATEGORY_FORMAT, EditCodes.SG03);
        }
      }
    }
    Long logCount = version.lng("log_count");
    if ((logCount == null && totalPieces != 0) || (logCount != null && totalPieces != logCount)) {
      versionError(CATEGORY_RELATED, EditCodes.LC01);
    } else if (ret.segregations().isEmpty()) {
      versionError(CATEGORY_FORMAT, EditCodes.SG04);
    }
  }

  /**
   * {@code PrimaryPieceSummarySegKey.createSegKey(seg, category)}: species, product, grade, other
   * description and, by category, the mark (BC), NMV (PR, OT, BC, CR), SB4 (PR, OT) or avoidable (WA).
   */
  private List<Object> segKey(Row seg) {
    String c = category();
    boolean primary = SummaryReturnRules.CATEGORY_PRIMARY.equals(c) || SummaryReturnRules.CATEGORY_OTHER.equals(c);
    boolean beachComb = SummaryReturnRules.CATEGORY_BEACHCOMB.equals(c);
    boolean cruise = SummaryReturnRules.CATEGORY_CRUISE.equals(c);
    boolean waste = SummaryReturnRules.CATEGORY_WASTE.equals(c);
    if (!primary && !beachComb && !cruise && !waste && !SummaryReturnRules.CATEGORY_FIELD_SCALE.equals(c)) {
      throw legacyFailure("Unable to create PrimaryPieceSummarySegKey for category code [" + c + "]");
    }
    return Arrays.asList(
        nz(seg.str("scale_species_code")), nz(seg.str("scale_product_code")), nz(seg.str("scale_grade_code")),
        beachComb ? nz(seg.str("timber_mark")) : "", nz(seg.str("other_description_type_code")),
        primary || beachComb || cruise ? indicator(seg, "nmv_ind") : null,
        primary ? indicator(seg, "sb4_ind") : null,
        waste ? indicator(seg, "avoidable_ind") : null);
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }

  /**
   * {@code validateCruiseBasedGrades} (BT 16523): on a cruise return, a log segregation of a
   * non-private cruise-based interior mark scaled on or after the MPB policy date must be grade 7
   * or 8 (ICB789); on any other return grades 7 and 8 are not allowed (ISB789).
   */
  private void validateCruiseBasedGrades(Row seg, TimberMark mark, String gradeSchedule, LocalDateTime policy) {
    String grade = seg.str("scale_grade_code");
    if (isCruise() && EditCodes.PRODUCT_LOG.equalsIgnoreCase(seg.str("scale_product_code"))
        && scaleDate() != null && policy != null
        && !scaleDate().isBefore(policy) && mark.isCruiseBased()
        && !mark.isPrivateMark() && EditCodes.INTERIOR_SCHEDULE.equals(gradeSchedule)
        && !EditCodes.INTERIOR_CB_GRADES.contains(grade)) {
      segregationError(seg, CATEGORY_SCALING, EditCodes.ICB789);
    }
    if (!isCruise() && EditCodes.INTERIOR_CB_GRADES.contains(grade)) {
      segregationError(seg, CATEGORY_SCALING, EditCodes.ISB789);
    }
  }

  /** {@code validateBeachCombSegregationTimberMark}: NM02, NM04, TM02. */
  private void validateBeachCombSegregationTimberMark(Row seg) {
    boolean nmv = noMarkVisible(seg);
    String input = seg.str("input_bcomb_timber_mark");
    if (!nmv && empty(input)) {
      segregationError(seg, CATEGORY_RELATED, EditCodes.NM02);
    }
    if (nmv && !empty(input)) {
      segregationError(seg, CATEGORY_RELATED, EditCodes.NM04);
    }
    if (notEmpty(seg.str("timber_mark"))) {
      requiredTimberMark(seg.str("timber_mark"), "Previously validated mark not found!");
    } else {
      segregationError(seg, CATEGORY_FORMAT, EditCodes.TM02);
    }
  }

  /** {@code invalidNumberOfPieces}: cruise, field and other volume estimates need no piece count. */
  private boolean invalidNumberOfPieces(Long pieces) {
    String t = pssmryType();
    if (SummaryReturnRules.PSSMRY_CRUISE_TYPE.equals(t) || SummaryReturnRules.PSSMRY_FIELD_TYPE.equals(t)
        || SummaryReturnRules.PSSMRY_OTHER_TYPE.equals(t)) {
      return false;
    }
    return pieces == null || pieces <= 0;
  }

  /**
   * {@code validateCutBlockId} (BT 5705): CTB02 when the mark has no cut block (unless NONE); CTB01
   * when the cut block is not one of the mark's (unless NONE).
   */
  private void validateCutBlockId() {
    String cutBlock = str("cut_block_id");
    String mark = str("timber_mark");
    boolean none = "NONE".equals(cutBlock);
    if (!lookups.markHasCutBlock(mark) && !none) {
      versionError(CATEGORY_SCALING, EditCodes.CTB02);
    } else if (notEmpty(mark) && lookups.cutBlockExists(cutBlock, mark)) {
      return;
    } else if (!none) {
      versionError(CATEGORY_SCALING, EditCodes.CTB01);
    }
  }
}
