package ca.bc.gov.nrs.hbs.api.summaryreturns.entry;

/**
 * Legacy message texts of the summary-return data-entry screens
 * ({@code $V/ApplicationResources.properties}; key in each javadoc).
 *
 * <p>A few legacy entries repeat their own key in the value
 * ({@code error.timbermark.cb = error.timbermark.cb = Timber Mark …}), so the old
 * screen showed "error.timbermark.cb = Timber Mark …". The key prefix is dropped here.
 * Two entries are edit-error references ({@code MSGCODE:NW00,FMT}, {@code MSGCODE:TM01,SCL})
 * that the legacy {@code HBSErrors2Tag} rendered as "code description" from
 * {@code HBS_EDIT_ERR_MESSAGE_CODE}; {@link EntryLookups#editErrorMessage} does the same.
 */
public final class EntryMessages {

  private EntryMessages() {
  }

  /** error.access.disallowed */
  public static final String ACCESS_DISALLOWED = "You are not authorized to perform this action.";

  // ── Header (SummaryScaleReturnValidator.validateP050Form / P100 / P150) ──
  /** error.type.required */
  public static final String TYPE_REQUIRED = "A return type is required.";
  /** error.type.invalid */
  public static final String TYPE_INVALID = "The return type entered is invalid.";
  /** error.scalerlicence.invalid */
  public static final String LICENCE_INVALID = "Scaler licence is invalid.";
  /** error.scalerlicence.required */
  public static final String LICENCE_REQUIRED = "A scaler licence is required.";
  /** error.returnno.required */
  public static final String RETURN_NO_REQUIRED = "A return number is required.";
  /** error.returnno.numeric */
  public static final String RETURN_NO_NUMERIC = "Return number must be numeric.";
  /** error.scales.invalid */
  public static final String SCALE_SITE_INVALID = "The scale site entered is invalid.";
  /** error.scalesiteno.required */
  public static final String SCALE_SITE_REQUIRED = "A scaling site number is required.";
  /** errors.scalar-licence-user-access */
  public static final String LICENCE_USER_ACCESS = "User not authorized to add a scale return for this scaler licence.";
  /** errors.scalesite-user-access3 */
  public static final String SITE_USER_ACCESS = "User not authorized to add a scale return for this scale site.";
  /** error.timbermark.invalid */
  public static final String MARK_INVALID = "Timber mark must be in Timber Mark table and in Mark Billed Client table.";
  /** error.timbermark.cb */
  public static final String MARK_NOT_CRUISE_BASED = "Timber Mark must be designated as Cruise Based.";
  /** error.timbermark.client */
  public static final String MARK_NOT_CLIENTS = "Timber mark is not owned by the company you are associated with.";
  /** error.timbermark.brokenlink */
  public static final String MARK_BROKEN_LINK =
      "Timber mark to District to Cruise Based Scale link is broken. Document can not be saved.";
  /** error.timbermark.required2 (piece) */
  public static final String MARK_REQUIRED_PIECE = "Timber Mark is required.";
  /** error.timbermark.required (weight, sample) */
  public static final String MARK_REQUIRED = "A timber mark is required.";
  /** error.invoicedToClient.invalid */
  public static final String BILL_TO_INVALID = "Payable by Client and/or Location is invalid.";
  /** error.copiedToClient.invalid */
  public static final String COPY_TO_INVALID = "Copied to Client and/or Location is invalid.";
  /** error.sections.invalid */
  public static final String SECTIONS_INVALID = "Sections must be a valid number less than 100.0.";
  /** error.logcount.invalid */
  public static final String LOG_COUNT_INVALID = "Log count must be a valid number.";
  /** error.logcount.required */
  public static final String LOG_COUNT_REQUIRED = "A log count is required.";
  /** error.fielddeckid.required */
  public static final String DECK_ID_REQUIRED = "A field deck id is required.";
  /** error.cutblock_ctb01.invalid */
  public static final String CUT_BLOCK_CTB01 =
      "Cut block on waste return must be one of the cut blocks on the cut block table for this mark";
  /** error.cutblock_ctb02.invalid (the properties file HTML-escapes the brackets) */
  public static final String CUT_BLOCK_CTB02 =
      "Each character in the cut block on waste return must be alphabetic, numeric or one of 7 special characters </'(-:>.";
  /** error.cutblock_ctb04.invalid */
  public static final String CUT_BLOCK_CTB04 = "This mark has no cut blocks";
  /** error.batchslipid.number */
  public static final String BATCH_SLIP_NUMBER = "Batch slip id must be a valid number.";
  /** error.batchslipid.invalid */
  public static final String BATCH_SLIP_INVALID = "Batch slip id does not exist.";
  /** error.batchslipid2.invalid */
  public static final String BATCH_SLIP_CLEARED =
      "Cannot add or update volume estimates to a batch slip that has a cleared status.";
  /** error.timbermark.invalidSrCategoryCruise */
  public static final String PRIVATE_MARK_CRUISE = "Return Category cannot be Cruise for private marks.";
  /** error.areacut.required */
  public static final String AREA_CUT_REQUIRED = "Area Cut is required.";
  /** error.areacut.numeric */
  public static final String AREA_CUT_NUMERIC = "Area Cut must be numeric and positive.";
  /** error.areacut.exceeds ({0} = 9999.9) */
  public static final String AREA_CUT_EXCEEDS = "Area Cut cannot exceed 9999.9.";
  /** error.decimal.invalid with "Area Cut", "", "", "1" */
  public static final String AREA_CUT_DECIMALS = "Area Cut must have 1 or less decimal places.";
  /** error.daterange.invalidscaledate */
  public static final String SCALE_DATE_INVALID = "Invalid scaling date.";
  /** error.daterange.invalidharvestdate */
  public static final String HARVEST_DATE_INVALID = "Invalid Harvest Month End Date";
  /** errors.limit.scaledate */
  public static final String SCALE_DATE_LIMIT = "Year of scale date is more than seven years prior to current year";
  /** errors.limit.harvestdate */
  public static final String HARVEST_DATE_LIMIT =
      "Year of harvest month end date is more than seven years prior to current year";
  /** error.scaledate.required */
  public static final String SCALE_DATE_REQUIRED = "A scaling date is required.";
  /** error.harvestdate.required */
  public static final String HARVEST_DATE_REQUIRED = "Harvest month end date is required.";
  /** error.population.invalid */
  public static final String POPULATION_INVALID = "The population must be a four digit number.";
  /** error.stratum.invalid */
  public static final String STRATUM_INVALID = "The stratum field must be a two digit number.";
  /** error.popyear.invalid */
  public static final String POP_YEAR_INVALID = "The population year field must be a four digit number.";
  /** error.pop.stratum.year.invalid */
  public static final String PSY_INVALID = "The population/stratum/year field entered is not a valid input combination.";
  /** error.popstratyear.required */
  public static final String PSY_REQUIRED = "A population, stratum and year are required.";
  /** errors.returncategory.defaultpop */
  public static final String DEFAULT_POP_CATEGORY =
      "Return Type must be Primary Scale for populations where Ratio Type is Default.";
  /** error.psym.required */
  public static final String PSYM_REQUIRED =
      "Cannot create a Final Bill for a Stratum/Mark that has had no billing activity to date";
  /** error.sampleweight.invalid = MSGCODE:NW00,FMT (rendered from the code table) */
  public static final String SAMPLE_WEIGHT_INVALID_CODE = "NW00";
  public static final String SAMPLE_WEIGHT_INVALID_CATEGORY = "FMT";
  /** error.sampleweight.required */
  public static final String SAMPLE_WEIGHT_REQUIRED = "A sample weight is required.";
  /** error.loadarrival.invalid */
  public static final String LOAD_ARRIVAL_INVALID = "Load arrival number must be numeric and greater than zero.";
  /** error.unable.tosave.doc (the 409 of {@code StaleUpdateException} carries the same text) */
  public static final String UNABLE_TO_SAVE =
      "Your changes were not saved because the scale return has been updated by another process.";

  // ── Segregations (P057Action / P157Action, HBSValidatorHelper.validateVolume) ──
  /** error.bcombmark.invalid = MSGCODE:TM01,SCL (rendered from the code table) */
  public static final String BCOMB_MARK_INVALID_CODE = "TM01";
  public static final String BCOMB_MARK_INVALID_CATEGORY = "SCL";
  /** error.sb4AndNmv.invalid */
  public static final String SB4_AND_NMV = "SB4 and NMV cannot both be selected.";
  /** error.species.required */
  public static final String SPECIES_REQUIRED = "A species is required.";
  /** error.product.required */
  public static final String PRODUCT_REQUIRED = "A product is required.";
  /** error.grade.required */
  public static final String GRADE_REQUIRED = "A grade is required.";
  /** error.specie.product.grade.invalid */
  public static final String SPG_INVALID = "Species, product and grade combination is invalid.";
  /** errors.speciesgrade.invalid */
  public static final String SPECIES_GRADE_INVALID = "Species and grade combination is invalid.";
  /** error.numberofpieces.required */
  public static final String PIECES_REQUIRED = "Pieces must be a valid number.";
  /** error.piecesequalszero.invalid */
  public static final String PIECES_NEGATIVE = "Pieces must be a value greater than or equal to zero.";
  /** error.volumeproduct.invalid */
  public static final String VOLUME_XMAS = "Volume must be blank for product type Christmas Trees.";
  /** error.volume.required */
  public static final String VOLUME_REQUIRED = "Volume must be a valid number.";
  /** error.negvolume.invalid */
  public static final String VOLUME_NEGATIVE = "Volume must be a value greater than or equal to zero.";
  /** error.volume.invalid */
  public static final String VOLUME_TOO_LARGE = "Volume must be a valid number less than 1000000.00.";
  /** error.volume.decimal.invalid */
  public static final String VOLUME_DECIMALS = "Volume must have 3 or less decimal places.";

  // ── Notations (P058Action / P109Action / P158Action) ──
  /** error.notation.invalid */
  public static final String NOTATION_EMPTY = "Notation text cannot be empty.";

  // ── Daily loads (P110Action) ──
  /** error.weight.required */
  public static final String WEIGHT_REQUIRED = "Weight must be an integer greater than zero.";
  /** error.loads.required */
  public static final String LOADS_REQUIRED = "Loads must be an integer greater than zero.";

  // ── Port-only texts (no legacy key: the legacy page made these impossible) ──
  public static final String NOT_EDITABLE = "This summary scale return cannot be changed in its current state.";
  public static final String SEGREGATIONS_NOT_AVAILABLE = "Segregations are not available for this summary scale return.";
  public static final String GENERATED_NOT_EDITABLE = "Generated summary scale returns cannot be changed.";
  public static final String DAILY_LOADS_NOT_AVAILABLE =
      "Daily load summaries are only kept for weight scale summary returns.";
  public static final String DAY_INVALID = "Day must be a number from 1 to 31.";
  public static final String DAY_DUPLICATE = "Each day can only be entered once.";
  public static final String UNKNOWN_SEGREGATION = "This segregation is not part of the summary scale return.";
  public static final String UNKNOWN_NOTATION = "This notation is not part of the summary scale return.";

  /**
   * P054A "Confirm Custody Transfer" (the legacy page showed From / To Scale Site and From / To
   * Scaler Licence and asked Yes / No).
   */
  public static String confirmCustodyTransfer(String fromSite, String toSite, String fromLicence, String toLicence) {
    return "Confirm Custody Transfer: scale site " + blank(fromSite) + " to " + blank(toSite)
        + ", scaler licence " + blank(fromLicence) + " to " + blank(toLicence)
        + ". Save again with confirmCustodyTransfer = Y to confirm.";
  }

  private static String blank(String s) {
    return s == null ? "(none)" : s;
  }
}
