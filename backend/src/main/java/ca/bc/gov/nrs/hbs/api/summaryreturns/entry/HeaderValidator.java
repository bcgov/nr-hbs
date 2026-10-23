package ca.bc.gov.nrs.hbs.api.summaryreturns.entry;

import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.CLI_SCALER;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.CLI_SITE_ADMIN;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.CLI_USER;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.SCALE_ADMIN;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.SPC_SUBM_AGNT;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.SUMM_DATA_CORR;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.AREA_CUT;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.BATCH_SLIP_ID;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.BILL_TO_CLIENT;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.BILL_TO_LOCN;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.CATEGORY;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.COPY_TO_CLIENT;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.COPY_TO_LOCN;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.CUT_BLOCK_ID;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.FIELD_DECK_ID;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.LOAD_ARRIVAL;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.LOG_COUNT;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.NET_WEIGHT;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.NUMBER_OF_SECTIONS;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.POPULATION;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.PSSMRY_TYPE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.RETURN_NUMBER;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.SAMPLING_YEAR;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.SCALER_LICENCE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.SCALE_DATE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.SCALE_SITE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.STRATUM;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.TIMBER_MARK;

import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryCaller;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnAccess;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRepository;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException.Problem;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The header validations of the add/update pages: {@code $V/dac/summary/SummaryScaleReturnValidator}
 * {@code validateP050FormDates / validateP050Form / validateDocumentEligibleForUpdate},
 * {@code validateP100FormDates / validateP100Form}, {@code validateP150FormDates / validateP150Form},
 * {@code validateDate}, {@code validatePopStratYear}, and the extra checks {@code P100Action} ran on
 * Save/Add ({@code validateReturnCategory}, BT14020; {@code updateYTDCyclicBillingInd}, BT7327).
 *
 * <p>Like the legacy validators these also adjust the form: Bill To / Copy To are cleared where the
 * billing derives them (Bug 404 / BT 6258) and a waste return without cut blocks gets "None".
 * Problems use the JSON field names of {@link HeaderForm}; a global legacy error has no field.
 */
@Component
public class HeaderValidator {

  /** {@code SummaryScaleReturn.isReplace}: the document states of a replacement. */
  private static final Set<String> REPLACE_STATES = Set.of(ScaleReturnState.CANCEL_WITH_REPLACE_NO_CHANGES,
      ScaleReturnState.CANCEL_WITH_REPLACE_WITH_CHANGES, ScaleReturnState.REPLACE_NO_CHANGES_COMPLETE,
      ScaleReturnState.REPLACE_NO_CHANGES_IN_PROGRESS, ScaleReturnState.REPLACE_WITH_CHANGES_COMPLETE,
      ScaleReturnState.REPLACE_WITH_CHANGES_IN_PROGRESS);

  private final EntryLookups lookups;
  private final SummaryReturnAccess access;
  private final SummaryReturnRepository repo;

  public HeaderValidator(EntryLookups lookups, SummaryReturnAccess access, SummaryReturnRepository repo) {
    this.lookups = lookups;
    this.access = access;
    this.repo = repo;
  }

  /** The outcome of a header validation. */
  public record Outcome(List<Problem> problems, boolean custodyTransfer) {
    public boolean ok() {
      return problems.isEmpty();
    }
  }

  // ── Piece (P050 / P053) ──────────────────────────────────────────────────

  /**
   * {@code validateP050FormDates} + {@code validateP050Form}. {@code existing} is the active version
   * on update, null on add.
   */
  public Outcome validatePiece(HeaderForm f, SummaryCaller caller, SummaryReturn existing) {
    List<Problem> p = new ArrayList<>();
    String pssmry = f.get(PSSMRY_TYPE);
    boolean areaBased = SummaryReturnRules.PSSMRY_AREA_TYPE.equals(pssmry);

    validateDate(p, f.get(SCALE_DATE), areaBased);

    boolean validCategory = validateCategory(p, f.get(CATEGORY),
        SummaryReturnRules.PSSMRY_DEFAULT_TYPE.equals(pssmry) ? EntryRules.PIECE_DEFAULT_CATEGORIES : null);
    String category = f.get(CATEGORY) == null ? "" : f.get(CATEGORY);

    boolean validLicence = false;
    if (!f.empty(SCALER_LICENCE)) {
      validLicence = isValidLicence(f.get(SCALER_LICENCE));
      if (!validLicence) p.add(new Problem(SCALER_LICENCE, EntryMessages.LICENCE_INVALID));
    } else if (!areaBased) {
      p.add(new Problem(SCALER_LICENCE, EntryMessages.LICENCE_REQUIRED)); // not shown for AREAEST (BT 15212)
    }

    if (f.empty(RETURN_NUMBER)) {
      if (!areaBased) p.add(new Problem(RETURN_NUMBER, EntryMessages.RETURN_NO_REQUIRED));
    } else if (!EntryRules.isNumber(f.get(RETURN_NUMBER))) {
      p.add(new Problem(RETURN_NUMBER, EntryMessages.RETURN_NO_NUMERIC));
    }

    boolean validSite = false;
    if (!f.empty(SCALE_SITE)) {
      validSite = lookups.scaleSiteExists(f.get(SCALE_SITE));
      if (!validSite) p.add(new Problem(SCALE_SITE, EntryMessages.SCALE_SITE_INVALID));
    } else if (!areaBased) {
      p.add(new Problem(SCALE_SITE, EntryMessages.SCALE_SITE_REQUIRED));
    }

    boolean custodyTransfer = false;
    if (validLicence && validSite && !areaBased) {
      custodyTransfer = validateDocumentEligibleForUpdate(p, f, caller, existing);
    }

    boolean validMark = false;
    boolean privateMark = false;
    if (!f.empty(TIMBER_MARK)) {
      Optional<EntryLookups.Mark> mark = lookups.timberMark(f.get(TIMBER_MARK));
      if (mark.isEmpty()) {
        p.add(new Problem(TIMBER_MARK, EntryMessages.MARK_INVALID));
      } else {
        if (areaBased) {
          // BT15212: the mark must be cruise based and, for an industry user, owned by the user's client
          if (!mark.get().isCruiseBased()) {
            p.add(new Problem(TIMBER_MARK, EntryMessages.MARK_NOT_CRUISE_BASED));
          } else if (caller.has(CLI_USER)) {
            if (caller.clientNumber() != null && !caller.clientNumber().isBlank()
                && !caller.clientNumber().equals(mark.get().clientNumber())) {
              p.add(new Problem(TIMBER_MARK, EntryMessages.MARK_NOT_CLIENTS));
            } else {
              validMark = true;
            }
          }
          // Legacy defect kept: a ministry user's cruise-based mark is never flagged valid here, so the
          // broken-link check, the Bill To derivation and the private-mark check are skipped for it.
          if (validMark && EntryRules.WASTE_SCALE_SITE.equalsIgnoreCase(lookups.areaBasedScaleSite(f.get(TIMBER_MARK)))) {
            p.add(new Problem(TIMBER_MARK, EntryMessages.MARK_BROKEN_LINK));
          }
        } else {
          validMark = true;
        }
        privateMark = mark.get().isPrivateMark(); // MR02 bug 2574
      }
    } else {
      p.add(new Problem(TIMBER_MARK, EntryMessages.MARK_REQUIRED_PIECE));
    }

    validateClients(p, f);

    if (!f.empty(NUMBER_OF_SECTIONS)) {
      try {
        if (Double.parseDouble(f.get(NUMBER_OF_SECTIONS)) >= 100.0) {
          p.add(new Problem(NUMBER_OF_SECTIONS, EntryMessages.SECTIONS_INVALID));
        }
      } catch (NumberFormatException ex) {
        p.add(new Problem(NUMBER_OF_SECTIONS, EntryMessages.SECTIONS_INVALID));
      }
    }

    if (!f.empty(LOG_COUNT)) {
      if (!EntryRules.allNumbers(f.get(LOG_COUNT))) p.add(new Problem(LOG_COUNT, EntryMessages.LOG_COUNT_INVALID));
    } else if (!areaBased) {
      p.add(new Problem(LOG_COUNT, EntryMessages.LOG_COUNT_REQUIRED));
    }

    // Bug 404 / BT 6258: Bill To and Copy To are left blank where the billing derives them
    boolean waste = SummaryReturnRules.CATEGORY_WASTE.equals(category);
    if (existing == null) {
      if (validSite && !waste && (f.empty(COPY_TO_CLIENT) || f.empty(COPY_TO_LOCN))) {
        clearCopyTo(f);
      }
      if (validMark && (f.empty(BILL_TO_CLIENT) || f.empty(BILL_TO_LOCN))) {
        clearBillToIfDerived(f, validSite);
      }
    } else {
      Row v = existing.version();
      if (validSite && !waste) {
        if (!f.get(SCALE_SITE).equals(v.str("scale_site_id_nmbr"))) clearCopyTo(f);
      } else if (REPLACE_STATES.contains(existing.docState())
          && (v.get("client_locn_code_copied_to") != null || v.get("client_number_copied_to") != null)) {
        clearCopyTo(f);
      }
      if (validMark && !f.get(TIMBER_MARK).equals(v.str("timber_mark"))) {
        clearBillToIfDerived(f, validSite);
      }
    }

    if (SummaryReturnRules.PSSMRY_FIELD_TYPE.equals(pssmry) && f.empty(FIELD_DECK_ID)) {
      p.add(new Problem(FIELD_DECK_ID, EntryMessages.DECK_ID_REQUIRED));
    }

    if (SummaryReturnRules.PSSMRY_WASTE_TYPE.equals(pssmry) && validMark) {
      validateWasteCutBlock(p, f);
    }

    validateBatchSlip(p, f);

    if (validMark && privateMark && validCategory && SummaryReturnRules.CATEGORY_CRUISE.equals(category)) {
      p.add(new Problem(CATEGORY, EntryMessages.PRIVATE_MARK_CRUISE));
    }

    if (areaBased) {
      validateAreaCut(p, f.get(AREA_CUT));
    }
    return new Outcome(p, custodyTransfer);
  }

  /**
   * {@code validateDocumentEligibleForUpdate}: on a volume estimate, client scalers, client site
   * admins and scale admins may only use a licence / scale site they are authorized for. On a held
   * OIP version the failure becomes the P054A custody-transfer confirmation instead of an error.
   */
  private boolean validateDocumentEligibleForUpdate(List<Problem> p, HeaderForm f, SummaryCaller caller,
      SummaryReturn existing) {
    String pssmry = f.get(PSSMRY_TYPE);
    String docState = existing == null ? null : existing.docState();
    String versionState = existing == null ? null : existing.versionState();
    if (SummaryReturnRules.PSSMRY_DEFAULT_TYPE.equals(pssmry) || SummaryReturnRules.PSSMRY_SFP_TYPE.equals(pssmry)
        || caller.has(SUMM_DATA_CORR) || caller.has(SPC_SUBM_AGNT)
        || !(docState == null || ScaleReturnState.ORIGINAL_IN_PROGRESS.equals(docState))) {
      return false;
    }
    boolean licenceOk = access.hasScalerAuthorization(caller, f.get(SCALER_LICENCE));
    boolean siteOk = access.hasScaleSiteAuthorization(caller, f.get(SCALE_SITE));
    boolean dualRole = caller.has(CLI_SCALER) && caller.has(CLI_SITE_ADMIN);
    boolean held = ScaleReturnState.HELD.equals(versionState);
    List<Problem> failures = new ArrayList<>();
    if (dualRole && !(licenceOk || siteOk)) {
      failures.add(new Problem(SCALER_LICENCE, EntryMessages.LICENCE_USER_ACCESS));
      failures.add(new Problem(SCALE_SITE, EntryMessages.SITE_USER_ACCESS));
    } else if (!dualRole && caller.has(CLI_SITE_ADMIN) && !siteOk) {
      failures.add(new Problem(SCALE_SITE, EntryMessages.SITE_USER_ACCESS));
    } else if (!dualRole && caller.has(CLI_SCALER) && !licenceOk) {
      failures.add(new Problem(SCALER_LICENCE, EntryMessages.LICENCE_USER_ACCESS));
    } else if (caller.has(SCALE_ADMIN) && !siteOk) {
      failures.add(new Problem(SCALE_SITE, EntryMessages.SITE_USER_ACCESS));
    }
    if (failures.isEmpty()) return false;
    if (held) return true;
    p.addAll(failures);
    return false;
  }

  private void clearCopyTo(HeaderForm f) {
    f.set(COPY_TO_CLIENT, null);
    f.set(COPY_TO_LOCN, null);
  }

  /** BCOMB2/3 marks bill the scale site owner (when the site is valid); other marks their holder. */
  private void clearBillToIfDerived(HeaderForm f, boolean validSite) {
    String mark = f.get(TIMBER_MARK);
    boolean derived = EntryRules.BCOMB_SITE_BILLED_MARKS.contains(mark) ? validSite : lookups.billToDerivable(mark);
    if (derived) {
      f.set(BILL_TO_CLIENT, null);
      f.set(BILL_TO_LOCN, null);
    }
  }

  /**
   * BT 5705 (WSTRESEST): blank becomes "None"; a mark without cut blocks only takes "None"
   * ({@code error.cutblock_ctb04}); otherwise the cut block must be one of the mark's
   * ({@code error.cutblock_ctb01}). The character check ({@code error.cutblock_ctb02}) sits in a
   * branch the earlier ones always pre-empt, so it never fired; it is not ported.
   */
  private void validateWasteCutBlock(List<Problem> p, HeaderForm f) {
    String mark = f.get(TIMBER_MARK);
    String cutBlock = f.get(CUT_BLOCK_ID);
    long blocks = lookups.cutBlockCount(mark);
    if (cutBlock == null) {
      f.set(CUT_BLOCK_ID, EntryRules.NO_CUT_BLOCK);
    } else if (blocks == 0 && EntryRules.NO_CUT_BLOCK.equalsIgnoreCase(cutBlock)) {
      f.set(CUT_BLOCK_ID, EntryRules.NO_CUT_BLOCK);
    } else if (blocks == 0) {
      p.add(new Problem(CUT_BLOCK_ID, EntryMessages.CUT_BLOCK_CTB04));
    } else if (!lookups.cutBlockExists(cutBlock, mark)) {
      p.add(new Problem(CUT_BLOCK_ID, EntryMessages.CUT_BLOCK_CTB01));
    }
  }

  /** Issue 702: the batch slip must exist and not be cleared. */
  private void validateBatchSlip(List<Problem> p, HeaderForm f) {
    String id = f.get(BATCH_SLIP_ID);
    if (id == null) return;
    if (!EntryRules.allNumbers(id)) {
      p.add(new Problem(BATCH_SLIP_ID, EntryMessages.BATCH_SLIP_NUMBER));
      return;
    }
    Optional<String> status = lookups.paperBatchStatus(Long.parseLong(id));
    if (status.isEmpty()) {
      p.add(new Problem(BATCH_SLIP_ID, EntryMessages.BATCH_SLIP_INVALID));
    } else if (EntryRules.PAPER_BATCH_CLEARED.equals(status.get())) {
      p.add(new Problem(BATCH_SLIP_ID, EntryMessages.BATCH_SLIP_CLEARED));
    }
  }

  /** BT 15212 Area Cut: required, numeric and ≥ 0, at most 9999.9, one decimal place. */
  private void validateAreaCut(List<Problem> p, String areaCut) {
    if (areaCut == null) {
      p.add(new Problem(AREA_CUT, EntryMessages.AREA_CUT_REQUIRED));
    } else if (!EntryRules.isNumeric(areaCut)) {
      p.add(new Problem(AREA_CUT, EntryMessages.AREA_CUT_NUMERIC));
    } else if (Double.parseDouble(areaCut) > EntryRules.MAX_AREA_CUT.doubleValue()) {
      p.add(new Problem(AREA_CUT, EntryMessages.AREA_CUT_EXCEEDS));
    } else if (!EntryRules.isValidPrecision(areaCut, 1)) {
      p.add(new Problem(AREA_CUT, EntryMessages.AREA_CUT_DECIMALS));
    }
  }

  // ── Weight (P100 / P103) ─────────────────────────────────────────────────

  /** {@code validateP100FormDates} + {@code validateP100Form} + the P100Action Save/Add checks. */
  public Outcome validateWeight(HeaderForm f, SummaryReturn existing) {
    List<Problem> p = new ArrayList<>();
    validateDate(p, f.get(SCALE_DATE), false);
    validateCategory(p, f.get(CATEGORY), SummaryReturnRules.WEIGHT_CATEGORIES);
    boolean validPsy = validatePopStratYear(p, f);

    if (!f.empty(SCALE_SITE)) {
      if (!lookups.scaleSiteExists(f.get(SCALE_SITE))) p.add(new Problem(SCALE_SITE, EntryMessages.SCALE_SITE_INVALID));
    } else {
      p.add(new Problem(SCALE_SITE, EntryMessages.SCALE_SITE_REQUIRED));
    }
    boolean validMark = validateMark(p, f);
    validateClients(p, f);

    // Bug 404 / BT 6258
    String mark = f.get(TIMBER_MARK);
    if (existing == null) {
      if ((f.empty(COPY_TO_CLIENT) || f.empty(COPY_TO_LOCN)) && validPsy && copyToDerived(f)) clearCopyTo(f);
      if ((f.empty(BILL_TO_CLIENT) || f.empty(BILL_TO_LOCN)) && validMark && lookups.billToDerivable(mark)) {
        f.set(BILL_TO_CLIENT, null);
        f.set(BILL_TO_LOCN, null);
      }
    } else {
      Row v = existing.version();
      if (validMark && !mark.equals(v.str("timber_mark")) && lookups.billToDerivable(mark)) {
        f.set(BILL_TO_CLIENT, null);
        f.set(BILL_TO_LOCN, null);
      }
      if (validPsy && !(f.get(POPULATION).equals(v.str("population_number"))
          && f.get(STRATUM).equals(v.str("stratum_number"))
          && Objects.equals(f.lng(SAMPLING_YEAR), v.lng("sampling_year")))
          && copyToDerived(f)) {
        clearCopyTo(f);
      }
    }

    // P100Action Save/Add: BT14020 (default-ratio populations allow Primary Scale only)
    if (!f.empty(CATEGORY)) {
      validateReturnCategory(p, f.get(CATEGORY), f.get(POPULATION), f.get(SAMPLING_YEAR));
    }
    // BT7327: a Final Bill needs YTD cyclic billing for the stratum/mark (presence only; the
    // FINAL_BILL_IND update belongs to release)
    if (SummaryReturnRules.CATEGORY_FINAL_BILL.equals(f.get(CATEGORY)) && validPsy
        && repo.cyclicBilling(f.get(POPULATION), f.get(STRATUM), f.lng(SAMPLING_YEAR), mark).isEmpty()) {
      p.add(new Problem(null, EntryMessages.PSYM_REQUIRED));
    }
    return new Outcome(p, false);
  }

  private boolean copyToDerived(HeaderForm f) {
    return lookups.copyToDerivable(f.get(POPULATION), f.get(STRATUM), f.lng(SAMPLING_YEAR));
  }

  /** {@code validateReturnCategory} (BT14020). */
  private void validateReturnCategory(List<Problem> p, String category, String population, String year) {
    if (population == null || year == null) return;
    long y;
    try {
      y = Long.parseLong(year);
    } catch (NumberFormatException ex) {
      return; // legacy threw here; the PSY checks have reported the year already
    }
    lookups.populationRatioType(population, y).ifPresent(ratio -> {
      if (EntryRules.DEFAULT_RATIO_TYPE.equals(ratio) && !SummaryReturnRules.CATEGORY_PRIMARY.equals(category)) {
        p.add(new Problem(CATEGORY, EntryMessages.DEFAULT_POP_CATEGORY));
      }
    });
  }

  // ── Sample (P150 / P153) ─────────────────────────────────────────────────

  /** {@code validateP150FormDates} + {@code validateP150Form}. */
  public Outcome validateSample(HeaderForm f) {
    List<Problem> p = new ArrayList<>();
    validateDate(p, f.get(SCALE_DATE), false);
    validateCategory(p, f.get(CATEGORY), SummaryReturnRules.SAMPLE_CATEGORIES);
    validatePopStratYear(p, f);
    if (!f.empty(SCALE_SITE)) {
      if (!lookups.scaleSiteExists(f.get(SCALE_SITE))) p.add(new Problem(SCALE_SITE, EntryMessages.SCALE_SITE_INVALID));
    } else {
      p.add(new Problem(SCALE_SITE, EntryMessages.SCALE_SITE_REQUIRED));
    }
    validateMark(p, f);
    if (!f.empty(SCALER_LICENCE)) {
      if (!isValidLicence(f.get(SCALER_LICENCE))) p.add(new Problem(SCALER_LICENCE, EntryMessages.LICENCE_INVALID));
    } else {
      p.add(new Problem(SCALER_LICENCE, EntryMessages.LICENCE_REQUIRED));
    }
    if (f.empty(RETURN_NUMBER)) {
      p.add(new Problem(RETURN_NUMBER, EntryMessages.RETURN_NO_REQUIRED));
    } else if (!EntryRules.isNumber(f.get(RETURN_NUMBER))) {
      p.add(new Problem(RETURN_NUMBER, EntryMessages.RETURN_NO_NUMERIC));
    }
    if (!f.empty(NET_WEIGHT)) {
      if (!EntryRules.isNumber(f.get(NET_WEIGHT))) {
        p.add(new Problem(NET_WEIGHT, lookups.editErrorMessage(EntryMessages.SAMPLE_WEIGHT_INVALID_CODE,
            EntryMessages.SAMPLE_WEIGHT_INVALID_CATEGORY)));
      }
    } else {
      p.add(new Problem(NET_WEIGHT, EntryMessages.SAMPLE_WEIGHT_REQUIRED));
    }
    if (!f.empty(LOG_COUNT)) {
      if (!EntryRules.allNumbers(f.get(LOG_COUNT))) p.add(new Problem(LOG_COUNT, EntryMessages.LOG_COUNT_INVALID));
    } else {
      p.add(new Problem(LOG_COUNT, EntryMessages.LOG_COUNT_REQUIRED));
    }
    if (!EntryRules.isValidLoadArrival(f.get(LOAD_ARRIVAL))) {
      p.add(new Problem(LOAD_ARRIVAL, EntryMessages.LOAD_ARRIVAL_INVALID));
    }
    return new Outcome(p, false);
  }

  // ── Shared pieces ────────────────────────────────────────────────────────

  /**
   * {@code validateDate}: required, a real calendar date, not before 1 January seven years ago
   * (BT6020). Area-based returns call it the Harvest Month End Date.
   */
  void validateDate(List<Problem> p, String value, boolean areaBased) {
    if (value == null) {
      p.add(new Problem(SCALE_DATE, areaBased ? EntryMessages.HARVEST_DATE_REQUIRED : EntryMessages.SCALE_DATE_REQUIRED));
      return;
    }
    LocalDate date = EntryRules.parseDate(value);
    if (date == null) {
      p.add(new Problem(SCALE_DATE, areaBased ? EntryMessages.HARVEST_DATE_INVALID : EntryMessages.SCALE_DATE_INVALID));
    } else if (EntryRules.beforeYearLimit(date)) {
      p.add(new Problem(SCALE_DATE, areaBased ? EntryMessages.HARVEST_DATE_LIMIT : EntryMessages.SCALE_DATE_LIMIT));
    }
  }

  /**
   * Return category: required and in SCL_RTN_CATEGORY_CODE ({@code error.type.*}); where the page
   * offered a fixed drop-down ({@code codefield values=…}) the value must be one of those.
   */
  private boolean validateCategory(List<Problem> p, String category, List<String> offered) {
    if (category == null) {
      p.add(new Problem(CATEGORY, EntryMessages.TYPE_REQUIRED));
      return false;
    }
    if (!lookups.categoryCodeExists(category) || (offered != null && !offered.contains(category))) {
      p.add(new Problem(CATEGORY, EntryMessages.TYPE_INVALID));
      return false;
    }
    return true;
  }

  /** Weight / sample timber mark: required, in TIMBER_MARK + MARK_BILLED_CLI. */
  private boolean validateMark(List<Problem> p, HeaderForm f) {
    if (f.empty(TIMBER_MARK)) {
      p.add(new Problem(TIMBER_MARK, EntryMessages.MARK_REQUIRED));
      return false;
    }
    if (lookups.timberMark(f.get(TIMBER_MARK)).isEmpty()) {
      p.add(new Problem(TIMBER_MARK, EntryMessages.MARK_INVALID));
      return false;
    }
    return true;
  }

  /** Bill To / Copy To: both parts or neither, and an existing client location. */
  private void validateClients(List<Problem> p, HeaderForm f) {
    if (!f.empty(BILL_TO_CLIENT) || !f.empty(BILL_TO_LOCN)) {
      if (f.empty(BILL_TO_CLIENT) || f.empty(BILL_TO_LOCN)
          || !lookups.clientLocationExists(f.get(BILL_TO_CLIENT), f.get(BILL_TO_LOCN))) {
        p.add(new Problem(BILL_TO_CLIENT, EntryMessages.BILL_TO_INVALID));
      }
    }
    if (!f.empty(COPY_TO_CLIENT) || !f.empty(COPY_TO_LOCN)) {
      if (f.empty(COPY_TO_CLIENT) || f.empty(COPY_TO_LOCN)
          || !lookups.clientLocationExists(f.get(COPY_TO_CLIENT), f.get(COPY_TO_LOCN))) {
        p.add(new Problem(COPY_TO_CLIENT, EntryMessages.COPY_TO_INVALID));
      }
    }
  }

  /**
   * {@code validatePopStratYear}: all three required; population 4 and stratum 2 characters at most,
   * the year 4, each a whole number above zero; then the stratum must exist.
   */
  boolean validatePopStratYear(List<Problem> p, HeaderForm f) {
    String pop = f.get(POPULATION);
    String strat = f.get(STRATUM);
    String year = f.get(SAMPLING_YEAR);
    if (pop == null || strat == null || year == null) {
      p.add(new Problem(POPULATION, EntryMessages.PSY_REQUIRED));
      return false;
    }
    boolean fieldsOk = true;
    if (!EntryRules.fits(pop, 4) || !EntryRules.isNumber(pop)) {
      p.add(new Problem(POPULATION, EntryMessages.POPULATION_INVALID));
      fieldsOk = false;
    }
    if (!EntryRules.fits(strat, 2) || !EntryRules.isNumber(strat)) {
      p.add(new Problem(STRATUM, EntryMessages.STRATUM_INVALID));
      fieldsOk = false;
    }
    if (!EntryRules.fits(year, 4) || !EntryRules.isNumber(year)) {
      p.add(new Problem(SAMPLING_YEAR, EntryMessages.POP_YEAR_INVALID));
      fieldsOk = false;
    }
    if (!fieldsOk) return false;
    if (!lookups.stratumExists(pop, strat, Long.parseLong(year))) {
      p.add(new Problem(POPULATION, EntryMessages.PSY_INVALID));
      return false;
    }
    return true;
  }

  /** {@code DacHelper.isValidLicence(licence)}: a scaling (S) or receiving (R) licence. */
  boolean isValidLicence(String licence) {
    return lookups.licenceExists(licence, "S") || lookups.licenceExists(licence, "R");
  }

  /** {@code isValidLicence(licence, "S") ? "S" : "R"} — QUANT_LIC_TYPE_CD of a new or saved version. */
  String licenceType(String licence) {
    return licence != null && lookups.licenceExists(licence, "S") ? "S" : "R";
  }
}
