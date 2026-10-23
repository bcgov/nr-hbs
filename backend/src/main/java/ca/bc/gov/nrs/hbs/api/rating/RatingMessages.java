package ca.bc.gov.nrs.hbs.api.rating;

/**
 * Legacy message texts used by the Rating workflows, verbatim from
 * {@code hbs-view/.../presentation/ApplicationResources.properties} (key in
 * each comment). Struts validator messages ({@code validation.xml} +
 * {@code validator-rules.xml}) are resolved with their argument labels.
 */
final class RatingMessages {

  private RatingMessages() {
  }

  // ── Override rules (P203 / P204 / P207) ──
  /** error.scaledatefrom.invalid */
  static final String SCALE_FROM_INVALID = "Invalid Scale From date.";
  /** error.scaledateto.invalid */
  static final String SCALE_TO_INVALID = "Invalid Scale To date.";
  /** error.processdatefrom.invalid */
  static final String PROCESS_FROM_INVALID = "Invalid process from date.";
  /** error.processdateto.invalid */
  static final String PROCESS_TO_INVALID = "Invalid process to date.";
  /** error.overrideratedate.invalid */
  static final String OVERRIDE_RATE_DATE_INVALID = "The override rate date is invalid.";
  /** error.overrideratedate.pastdate */
  static final String OVERRIDE_RATE_DATE_PAST =
      "Override rate date must be greater than or equal to the current date less 7 years.";
  /** error.daterange.invalidscalingdate */
  static final String SCALE_RANGE_INVALID = "Invalid scaling date range.";
  /** error.scaledate.pastdate */
  static final String SCALE_DATE_PAST = "Scale date must be greater than or equal to the current date less 7 years.";
  /** error.daterange.invalidprocessingdate */
  static final String PROCESS_RANGE_INVALID = "Invalid processing date range.";
  /** error.daterange.invalidprocessingdatefrom */
  static final String PROCESS_FROM_PAST = "Processing date must be a current or future date.";
  /** errors.required with Constants.FS_DECK_ID */
  static final String DECK_REQUIRED = "Field Scale Deck ID is required.";
  /** error.singleDeck.invalid */
  static final String DECK_INVALID = "Field Deck ID must be an upper case letter from A to Z or a number from 0 to 9.";
  /** error.overriderule.creationoverlaperror (the later of the two duplicate keys wins) */
  static final String RULE_OVERLAP = "Rule cannot be activated because its scaling interval and its processing "
      + "interval overlap with another rule for this timber mark.";
  /** error.overriderule.creationoverlaperrorwithFSDeck */
  static final String RULE_OVERLAP_DECK = "Rule cannot be activated because scaling interval and processing "
      + "interval overlap with another rule for this timber mark and field scale deck ID.";
  /** error.overriderate.required */
  static final String RATE_REQUIRED = "At least one override rate is required.";
  /** error.overriderule.updateerror */
  static final String RULE_UPDATE_ERROR = "Error updating override rule, please try again.";
  /** error.overrideraterow.invalid */
  static final String RATE_ROW_INVALID = "All fields of an override rate detail row must be filled in.";
  /** error.overrideratefield.invalid */
  static final String RATE_ALL_ONLY_LOGS = "The species and/or grade can only be all when the product type is logs.";
  /** error.speciesproductgrade.invalid */
  static final String SPG_INVALID = "Species, product and grade combination is invalid.";
  /** error.timbermark.required (P200, which gated P203/P204) */
  static final String TIMBER_MARK_REQUIRED = "A timber mark is required.";
  /** error.timbermark.invalid (P200 / RuleHelper.isValidTimberMark) */
  static final String TIMBER_MARK_INVALID =
      "Timber mark must be in Timber Mark table and in Mark Billed Client table.";
  /** errors.timbermark-ref (validator timbermarkCrossRef) */
  static final String TIMBER_MARK_REF = "Timber mark must be in Timber Mark table and in Mark Billed Client table.";

  /*
   * Status gating. The legacy JSPs only rendered the buttons for the right
   * status (P201/P202: P207 link and "Activate Rule" for Pending rules,
   * "De-activate Rule" for Active ones), so there is no legacy text; these
   * are new wordings for the server-side check that replaces the hidden button.
   */
  static final String ONLY_PENDING_ACTIVATE = "Only a Pending rule can be activated.";
  static final String ONLY_ACTIVE_DEACTIVATE = "Only an Active rule can be de-activated.";
  static final String ONLY_PENDING_EDIT = "Only a Pending rate rule can be updated.";

  // ── District default rates (P212 / P213) ──
  /** error.defaultdistricteffectivedate.invalid.old */
  static final String DDR_EFF_OLD = "Effective Date entered by user can not predate current date by more than 7 years.";
  /** error.defaultdistrictexpirydate.invalid.old */
  static final String DDR_EXP_OLD = "Expiry Date entered by user can not predate current date by more than 7 years.";
  /** error.defaultdistricteffectivedate.too.old */
  static final String DDR_EFF_ON_FILE_OLD = "Effective Date on file is older than 7 years - cannot be updated.";
  /** error.defaultdistrictexpirydate.too.old */
  static final String DDR_EXP_ON_FILE_OLD = "Expiry Date on file is older than 7 years - cannot be updated.";
  /** errors.date.effective-onorbefore-expiry */
  static final String EFF_ON_OR_BEFORE_EXP = "Effective Date must be on or before Expiry Date.";
  /** error.hbsdistrictdefaultrate.effdtexistint */
  static final String DDR_EFF_IN_INTERVAL = "Effective date for proposed entry is within an existing interval.";
  /** error.hbsdistrictdefaultrate.expdtexistint */
  static final String DDR_EXP_IN_INTERVAL = "Expiry date for proposed entry is within an existing interval.";
  /** error.hbsdistrictdefaultrate.propentexstint */
  static final String DDR_INCLUDES_INTERVAL = "Interval for proposed entry includes an existing interval.";
  /** error.rateusecoastalgrade.notexist */
  static final String COAST_GRADE_NOT_EXIST = "Rates with Coast Appraisal Method must use Coast grade codes.";
  /** error.rateuseinteriorgrade.notexist */
  static final String INTERIOR_GRADE_NOT_EXIST = "Rates with Interior Appraisal Method must use Interior grade codes.";
  /** error.logsratecoastalgradefromdate.noteffective */
  static final String COAST_GRADE_FROM = "Rates for logs with Coast Appraisal Method must use Coast grade codes in "
      + "effect on the Effective Date.";
  /** error.logsrateinteriorgradefromdate.noteffective */
  static final String INTERIOR_GRADE_FROM = "Rates for logs with Interior Appraisal Method must use Interior grade "
      + "codes in effect on the Effective Date.";
  /** error.logsratecoastalgradetodate.noteffective */
  static final String COAST_GRADE_TO = "Rates for logs with Coast Appraisal Method must use Coast grade codes in "
      + "effect on the Expiry Date.";
  /** error.logsrateinteriorgradetodate.noteffective */
  static final String INTERIOR_GRADE_TO = "Rates for logs with Interior Appraisal Method must use Interior grade "
      + "codes in effect on the Expiry Date.";

  // ── Waste & residue rates (P222 / P223) ──
  /** error.wasteresidueeffectivedate.invalid.old */
  static final String WRR_EFF_OLD = "From Scale Date entered by user can not predate current date by more than 7 years.";
  /** error.wasteresidueeffectivedate.too.old */
  static final String WRR_EFF_ON_FILE_OLD = "From Scale Date on file older than 7 years - can not update.";
  /** error.cutblock_ctb01.invalid */
  static final String CUT_BLOCK_NOT_ON_MARK =
      "Cut block on waste return must be one of the cut blocks on the cut block table for this mark";
  /** error.cutblock_ctb02.invalid (the property file escapes the characters as &lt; &gt;) */
  static final String CUT_BLOCK_CHARS = "Each character in the cut block on waste return must be alphabetic, numeric "
      + "or one of 7 special characters </'(-:>.";
  /** error.Record.exists */
  static final String RECORD_EXISTS = "Record already exists. Either de-activate the existing record, or update it.";
  /** error.wasteresiduerate.invalid */
  static final String WRR_RATE_REQUIRED = "A rate is required.";
  /** error.wasteresiduerate.inactive.update (the legacy "Return" link markup is dropped) */
  static final String WRR_INACTIVE = "Inactive waste residue rates cannot be updated.";

  // ── Stumpage rate (P230) ──
  /** error.returnType.invalid */
  static final String RETURN_TYPE_BEACHCOMB = "Return Type must be Beachcomb for Beachcomb marks.";
  /** error.cutblockblank.invalid */
  static final String CUT_BLOCK_BLANK = "Cutblock must be empty when Rate Return Type is not Waste.";
  /** error.eachCombTimberMarkblank.invalid */
  static final String BEACHCOMB_MARK_BLANK = "Beachcomb Timber Mark must be blank.";
  /** error.beachcomtimbermark.invalid */
  static final String NOT_BEACHCOMB_MARK = "The timber mark is invalid for Rate Return Type Beachcomb.";
  /** error.beachcometimbermark.required */
  static final String BEACHCOMB_MARK_REQUIRED = "A Segregation timber mark is required.";
  /** error.beachcometimbermark.invalid */
  static final String BEACHCOMB_MARK_INVALID = "Segregation timber mark is not valid.";
  /** error.wastecutblockid.required */
  static final String WASTE_CUT_BLOCK_REQUIRED = "A cut block id is required when Rate Return Type is Waste.";
  /** error.cutblockidtimbermark.invalid */
  static final String CUT_BLOCK_INVALID_FOR_MARK = "Invalid cutblock for the timber mark.";
  /** error.stumpagespecode.invalid */
  static final String SPECIES_REQUIRED = "A species is required.";
  /** error.stumpageprodcode.invalid */
  static final String PRODUCT_REQUIRED = "A product is required.";
  /** error.stumpagegradcode.invalid */
  static final String GRADE_REQUIRED = "A grade is required.";
  /** error.speciesproductgrade.scaledate.invalid */
  static final String SPG_NOT_ON_SCALE_DATE = "Species, product and grade combination is not in effect on the scale date.";
  /** error.grade.coast.invalid */
  static final String GRADE_COAST = "A Coast grade must be used with this mark.";
  /** error.grade.interior.invalid */
  static final String GRADE_INTERIOR = "An Interior grade must be used with this mark.";
  /** error.grade.coast.scaledate.invalid */
  static final String GRADE_COAST_DATE = "A Coast grade in effect on the scale date must be used with this mark.";
  /** error.grade.interior.scaledate.invalid */
  static final String GRADE_INTERIOR_DATE = "An Interior grade in effect on the scale date must be used with this mark.";

  // ── Struts validator messages (validator-rules.xml) ──
  /** errors.required */
  static String required(String label) {
    return label + " is required.";
  }

  /** errors.date (hbsDate) */
  static String notADate(String label) {
    return label + " is not a valid date.";
  }

  /** errors.float */
  static String notANumber(String label) {
    return label + " must be a number.";
  }

  /** error.value-is.greater-than (greaterThan / maxValue) */
  static String greaterThan(String label, String max) {
    return label + " cannot be greater than " + max + ".";
  }

  /** error.value-is.not-less-than / error.value-is.less-than (lessThan / minValue) */
  static String lessThan(String label, String min) {
    return label + " cannot be less than " + min + ".";
  }

  /** error.decimal.invalid (precision) */
  static String decimals(String label, int places) {
    return label + " must have " + places + " or less decimal places.";
  }

  /** errors.date.onorafter-current (onOrbeforeCurrent) */
  static String onOrBeforeToday(String label) {
    return label + " must be on or before the current date.";
  }
}
