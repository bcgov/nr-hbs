package ca.bc.gov.nrs.hbs.api.summaryreturns.edits;

import static ca.bc.gov.nrs.hbs.api.summaryreturns.EditError.CATEGORY_DATE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.EditError.CATEGORY_FORMAT;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.EditError.CATEGORY_RELATED;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.EditError.CATEGORY_SCALING;

import ca.bc.gov.nrs.hbs.api.summaryreturns.EditError;
import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.Client;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.ClientLocation;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.DateWindow;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.ScaleSite;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.Stratum;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.TimberMark;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code $E/dac/summary/control/SummaryScaleReturnValidator}: the edits every summary return
 * type shares. One instance validates one return, as the legacy validator did; it collects the
 * errors and moves input fields into the business columns of the loaded rows.
 */
abstract class ReturnEdits {

  protected final SummaryReturn ret;
  protected final Row version;
  protected final Lookups lookups;
  protected final Clock clock;
  protected final List<EditError> errors = new ArrayList<>();

  private final Map<String, Optional<TimberMark>> marks = new HashMap<>();
  private final Map<String, Optional<ClientLocation>> clientLocations = new HashMap<>();
  private final Map<String, Optional<Client>> clients = new HashMap<>();
  private final Map<String, Optional<Stratum>> strata = new HashMap<>();

  protected ReturnEdits(SummaryReturn ret, Lookups lookups, Clock clock) {
    this.ret = ret;
    this.version = ret.version();
    this.lookups = lookups;
    this.clock = clock;
  }

  /** {@code applyEditsAndValidate}: {@code validateAndMoveInputFields}, {@code validateEventDate}, {@code validate}. */
  final List<EditError> applyEditsAndValidate() {
    validateAndMoveInputFields();
    validateEventDate();
    validate();
    return errors;
  }

  /** {@code applyEditsForClient} (BT-9202). */
  final List<EditError> applyEditsForClient() {
    validateClients();
    return errors;
  }

  protected abstract void validateAndMoveInputFields();

  protected abstract void validate();

  protected abstract void validateClients();

  // ── Errors (createError / createSegregationError of the subclasses) ──────

  protected void versionError(String category, String code) {
    errors.add(EditError.version(category, code));
  }

  /** A segregation error; a generated segregation not yet saved has no id (null). */
  protected void segregationError(Row seg, String category, String code) {
    errors.add(new EditError(EditError.Target.SEGREGATION, category, code, seg.lng("segregation_id"), null));
  }

  /** {@code HbsEditErrors.contains(category, message)}. */
  protected boolean hasError(String category, String code) {
    return errors.stream().anyMatch(e -> category.equals(e.category()) && code.equals(e.message()));
  }

  /** A NullPointerException / runtime exception the legacy edit ran into (see {@link ReleaseEditException}). */
  protected static ReleaseEditException legacyFailure(String message) {
    return new ReleaseEditException(message);
  }

  // ── Small helpers ────────────────────────────────────────────────────────

  /** {@code StringUtil.empty}: null or "" (blanks are not empty). */
  protected static boolean empty(String s) {
    return s == null || s.isEmpty();
  }

  protected static boolean notEmpty(String s) {
    return !empty(s);
  }

  protected String str(String column) {
    return version.str(column);
  }

  protected LocalDateTime scaleDate() {
    return Lookups.dateTime(version, "scale_date");
  }

  protected LocalDateTime now() {
    return LocalDateTime.now(clock);
  }

  protected static LocalDateTime startDay(LocalDateTime d) {
    return d == null ? null : d.toLocalDate().atStartOfDay();
  }

  /** {@code DateUtil.endDay}: 23:59:59.999 of the day. */
  protected static LocalDateTime endDay(LocalDateTime d) {
    return d == null ? null : d.toLocalDate().atTime(LocalTime.of(23, 59, 59, 999_000_000));
  }

  /** {@code DateRange.insideRange}: inclusive; false when the range is not valid (null end, start after end). */
  protected static boolean insideRange(LocalDateTime start, LocalDateTime end, LocalDateTime maybe) {
    if (start == null || end == null || start.isAfter(end) || maybe == null) return false;
    return !maybe.isBefore(start) && !maybe.isAfter(end);
  }

  // ── Timber marks and clients (fetchTimberMark, fetchClient, fetchClientLocation) ──

  /** {@code fetchTimberMark}: empty when the mark is empty or not on file. Cached per return. */
  protected Optional<TimberMark> timberMark(String mark) {
    if (empty(mark)) return Optional.empty();
    return marks.computeIfAbsent(mark, lookups::timberMark);
  }

  /** {@code fetchTimberMark} where the legacy code treated a missing mark as a programming error. */
  protected TimberMark requiredTimberMark(String mark, String legacyMessage) {
    return timberMark(mark).orElseThrow(() -> legacyFailure(legacyMessage + " timberMark=" + mark));
  }

  /** {@code onFileTimbermark}. */
  protected boolean onFileTimberMark(String mark) {
    return timberMark(mark).isPresent();
  }

  protected Client fetchClient(String clientNumber) {
    if (empty(clientNumber)) return null;
    return clients.computeIfAbsent(clientNumber, lookups::client).orElse(null);
  }

  protected ClientLocation fetchClientLocation(String clientNumber, String clientLocnCode) {
    if (empty(clientNumber) || empty(clientLocnCode)) return null;
    return clientLocations.computeIfAbsent(clientNumber + ", " + clientLocnCode,
        k -> lookups.clientLocation(clientNumber, clientLocnCode)).orElse(null);
  }

  /** {@code deriveBillToClientFromTimberMark}. */
  protected Client deriveBillToClientFromTimberMark() {
    String mark = str("timber_mark");
    if (empty(mark)) return null;
    TimberMark tm = requiredTimberMark(mark, "Couldn't find timbermark that should have been validated previously.");
    return fetchClient(tm.clientNumber());
  }

  /** {@code deriveBillToClientAndLocationFromTimberMark}. */
  protected ClientLocation deriveBillToClientAndLocationFromTimberMark() {
    String mark = str("timber_mark");
    if (empty(mark)) return null;
    TimberMark tm = requiredTimberMark(mark, "validateBillToClientAndLocationFromTimberMark");
    return fetchClientLocation(tm.clientNumber(), tm.clientLocnCode());
  }

  /** {@code validateBillToClient}: BT01. */
  protected void validateBillToClient(Client billTo) {
    if (billTo == null) versionError(CATEGORY_FORMAT, EditCodes.BT01);
  }

  /** {@code validateCopyToClient}: CT01. */
  protected void validateCopyToClient(Client copyTo) {
    if (copyTo == null) versionError(CATEGORY_FORMAT, EditCodes.CT01);
  }

  /** {@code validateBillToClientAndLocation}: BT03, BT02 / BT04, BT05 (BT 4837, 7264, 9202, 11743), BT06. */
  protected void validateBillToClientAndLocation(ClientLocation loc) {
    if (loc == null) {
      versionError(CATEGORY_FORMAT, EditCodes.BT03);
      return;
    }
    Client client = loc.client();
    if (client.isDeactivated()) {
      versionError(CATEGORY_RELATED, EditCodes.BT02);
    } else if (loc.isExpired()) {
      versionError(CATEGORY_RELATED, EditCodes.BT04);
    }
    if (client.isIndividual() && individualName(client).length() > EditCodes.NAME_MAX_LEN) {
      versionError(CATEGORY_FORMAT, EditCodes.BT05);
    }
    if (blank(loc.city()) || blank(loc.province()) || blank(loc.postalCode())) {
      versionError(CATEGORY_FORMAT, EditCodes.BT06);
    }
  }

  /** {@code validateCopyToClientAndLocation}: CT03, CT02 / CT04, CT05, CT06. */
  protected void validateCopyToClientAndLocation(ClientLocation loc) {
    if (loc == null) {
      versionError(CATEGORY_FORMAT, EditCodes.CT03);
      return;
    }
    Client client = loc.client();
    if (client.isDeactivated()) {
      versionError(CATEGORY_RELATED, EditCodes.CT02);
    } else if (loc.isExpired()) {
      versionError(CATEGORY_RELATED, EditCodes.CT04);
    }
    if (loc.clientNumber() != null && client.isIndividual()
        && individualName(client).length() > EditCodes.NAME_MAX_LEN) {
      versionError(CATEGORY_FORMAT, EditCodes.CT05);
    }
    if (loc.clientNumber() != null && loc.clientLocnCode() != null
        && (blank(loc.city()) || blank(loc.province()) || blank(loc.postalCode()))) {
      versionError(CATEGORY_FORMAT, EditCodes.CT06);
    }
  }

  /** "name, first middle" as the BT05/CT05 edits measured it (BT11743). */
  private static String individualName(Client client) {
    StringBuilder name = new StringBuilder(String.valueOf(client.clientName()));
    if (client.legalFirstName() != null) name.append(", ").append(client.legalFirstName());
    if (client.legalMiddleName() != null) name.append(" ").append(client.legalMiddleName());
    return name.toString();
  }

  /** {@code StringUtil.empty(s, false)}: null or only blanks. */
  private static boolean blank(String s) {
    return s == null || s.trim().isEmpty();
  }

  // ── Dates ────────────────────────────────────────────────────────────────

  /** {@code validateEventDate} (BT 10747): SD02 when the scale date is before 1 January seven years ago. */
  protected void validateEventDate() {
    LocalDateTime scaleDate = scaleDate();
    LocalDateTime limit = LocalDate.of(now().getYear() - 7, 1, 1).atStartOfDay();
    if (scaleDate != null && scaleDate.isBefore(limit)) {
      versionError(CATEGORY_DATE, EditCodes.SD02);
    }
  }

  /** {@code DacValidator.isScaleDateBeforeCurrentDate}: not after today, time ignored. */
  protected boolean scaleDateNotInFuture() {
    LocalDateTime scaleDate = scaleDate();
    if (scaleDate == null) throw legacyFailure("isScaleDateBeforeCurrentDate: null scale date");
    return !scaleDate.toLocalDate().isAfter(now().toLocalDate());
  }

  // ── Scale site ───────────────────────────────────────────────────────────

  /** {@code getScaleSite}. */
  protected Optional<ScaleSite> scaleSite() {
    return lookups.scaleSite(str("scale_site_id_nmbr"));
  }

  /** {@code getScaleSite} where a missing site was "Unexpected Scale Site removal". */
  protected ScaleSite requiredScaleSite() {
    return scaleSite().orElseThrow(
        () -> legacyFailure("Unexpected Scale Site removal. Scale Site Id: " + str("scale_site_id_nmbr")));
  }

  // ── Sampling (fetchPopStratumYear, HdbsUtilities.convertYearToLong) ──────

  protected Optional<Stratum> stratum(String population, String stratum, Long year) {
    if (empty(population) || empty(stratum) || year == null) return Optional.empty();
    return strata.computeIfAbsent(population + "/" + stratum + "/" + year,
        k -> lookups.stratum(population, stratum, year));
  }

  /** {@code fetchPopStratumYear} where a missing stratum was an {@code HBSEJBException}. */
  protected Stratum requiredStratum(String population, String stratum, Long year, String legacyMethod) {
    return stratum(population, stratum, year).orElseThrow(() -> legacyFailure(legacyMethod
        + ": Stratum not found for population=" + population + " stratum=" + stratum + " year=" + year));
  }

  /** {@code emptyPopStratumYear}. */
  protected boolean emptyPopStratumYear() {
    return empty(str("population_number")) || empty(str("stratum_number")) || version.lng("sampling_year") == null;
  }

  /** {@code InvalidYearException}. */
  protected static final class InvalidYearException extends Exception {
    InvalidYearException(String message) {
      super(message);
    }
  }

  /**
   * {@code HdbsUtilities.convertYearToLong}: century (19 for 95–99, else 20) prefixed to the text.
   * Legacy defects kept: a one-digit year gives e.g. 205, a four-digit one 202026; null or "" ran
   * into {@code Integer.parseInt} (NumberFormatException).
   */
  protected static Long convertYearToLong(String twoDigitYear) throws InvalidYearException {
    if (twoDigitYear != null && !twoDigitYear.chars().allMatch(Character::isDigit)) {
      throw new InvalidYearException(twoDigitYear + " is not numeric.");
    }
    if (twoDigitYear == null || twoDigitYear.isEmpty()) {
      throw legacyFailure("HdbsUtilities.convertYear: NumberFormatException for input sampling year [" + twoDigitYear + "]");
    }
    int year = Integer.parseInt(twoDigitYear);
    int century = year >= 95 && year <= 99 ? 19 : 20;
    return Long.valueOf(century + twoDigitYear);
  }

  /** {@code setPopStratumYearFromInput}. */
  protected void setPopStratumYearFromInput() throws InvalidYearException {
    version.set("population_number", str("input_population"));
    version.set("sampling_year", convertYearToLong(str("input_sampling_year")));
    version.set("stratum_number", str("input_stratum"));
  }

  // ── Species / product / grade (7012) and grade constraint ────────────────

  /** {@code ScaledLogXrefValidator.getGradeScheduleType}: "" without a mark or scale date; may be null. */
  protected String gradeScheduleType(String mark) {
    LocalDateTime scaleDate = scaleDate();
    if (empty(mark) || scaleDate == null) return "";
    return lookups.gradeScheduleType(mark, scaleDate.toLocalDate());
  }

  /**
   * {@code validateInputSpeciesProductGrade}: the business species/product/grade, or the input ones
   * when any business code is null; when all three are given they must be on the cross reference
   * (SP01, then moved into the business columns), effective on the scale date (SP02) and, for logs,
   * a grade of the mark's coastal / interior schedule (CG01 / IG01) effective on the scale date
   * (CG02 / IG02). Each code is raised once per return ({@code logSPGError}).
   *
   * @return the code raised, or null
   */
  protected String validateInputSpeciesProductGrade(Row seg, String timberMark, List<String> logged,
      String gradeScheduleType) {
    String species;
    String product;
    String grade;
    if (seg.str("scale_species_code") == null || seg.str("scale_product_code") == null
        || seg.str("scale_grade_code") == null) {
      species = seg.str("input_species");
      grade = seg.str("input_grade");
      product = seg.str("input_product");
    } else {
      species = seg.str("scale_species_code");
      grade = seg.str("scale_grade_code");
      product = seg.str("scale_product_code");
    }
    if (empty(species) || empty(product) || empty(grade)) {
      return null;
    }
    Optional<DateWindow> spg = lookups.speciesProductGrade(species, product, grade);
    if (spg.isEmpty()) {
      return logSpgError(seg, logged, EditCodes.SPG_NOT_FOUND);
    }
    seg.set("scale_species_code", species).set("scale_product_code", product).set("scale_grade_code", grade);
    LocalDateTime scaleDate = scaleDate();
    if (!insideRange(startDay(spg.get().effectiveDate()), endDay(spg.get().expiryDate()), scaleDate)) {
      return logSpgError(seg, logged, EditCodes.SPG_NOT_EFFECTIVE);
    }
    if (!product.equalsIgnoreCase(EditCodes.PRODUCT_LOG) || timberMark == null) {
      return null;
    }
    boolean coastal = EditCodes.COASTAL_SCHEDULE.equalsIgnoreCase(gradeScheduleType);
    boolean interior = EditCodes.INTERIOR_SCHEDULE.equalsIgnoreCase(gradeScheduleType);
    Optional<DateWindow> gradeInfo = coastal || interior
        ? lookups.scaleGrade(seg.str("scale_grade_code"), coastal ? EditCodes.COASTAL_SCHEDULE : EditCodes.INTERIOR_SCHEDULE)
        : Optional.empty();
    if (gradeInfo.isEmpty()) {
      if (coastal) return logSpgError(seg, logged, EditCodes.COASTAL_GRADE);
      if (interior) return logSpgError(seg, logged, EditCodes.INTERIOR_GRADE);
      return null;
    }
    if (!insideRange(startDay(gradeInfo.get().effectiveDate()), endDay(gradeInfo.get().expiryDate()), scaleDate)) {
      if (coastal) return logSpgError(seg, logged, EditCodes.COASTAL_GRADE_NOT_EFFECTIVE);
      return logSpgError(seg, logged, EditCodes.INTERIOR_GRADE_NOT_EFFECTIVE);
    }
    return null;
  }

  /** {@code logSPGError}: a segregation SCL error, once per code. */
  private String logSpgError(Row seg, List<String> logged, String code) {
    if (logged.contains(code)) return null;
    segregationError(seg, CATEGORY_SCALING, code);
    return code;
  }

  /** {@code validateInputSpeciesProductGrade} over the segregations, sharing one de-duplication list. */
  protected void validateSpeciesProductGrade() {
    List<String> logged = new ArrayList<>();
    String mark = str("timber_mark");
    String schedule = gradeScheduleType(mark);
    for (Row seg : ret.segregations()) {
      String code = validateInputSpeciesProductGrade(seg, mark, logged, schedule);
      if (code != null) logged.add(code);
    }
  }

  /**
   * {@code validateGradeConstraint} (7012): when the stratum exists, the mark's grade schedule must
   * be the stratum's grade constraint, else CG03 / IG03 (CG04 / IG04 for weight).
   */
  protected void validateGradeConstraint(String timberMark, String population, String stratumNo, Long year,
      boolean weigh) {
    if (timberMark == null) {
      throw legacyFailure("ScaledLogXrefValidator.canStratumValidationPerform: null timber mark");
    }
    Optional<Stratum> stratum = stratum(population, stratumNo, year);
    if (stratum.isEmpty()) return;
    String schedule = gradeScheduleType(timberMark);
    if (schedule == null) {
      throw legacyFailure("validateGradeConstraint: no grade schedule for timber mark " + timberMark);
    }
    if (!schedule.equalsIgnoreCase(stratum.get().gradeConstraint())) {
      if (EditCodes.COASTAL_SCHEDULE.equalsIgnoreCase(schedule)) {
        versionError(CATEGORY_SCALING, weigh ? EditCodes.WEIGH_COASTAL_STRATUM : EditCodes.COASTAL_STRATUM);
      } else {
        versionError(CATEGORY_SCALING, weigh ? EditCodes.WEIGH_INTERIOR_STRATUM : EditCodes.INTERIOR_STRATUM);
      }
    }
  }

  // ── Stand-rate eligibility codes (BT 16523) ──────────────────────────────

  /** {@code validateScaleBasedSRECodes}. */
  protected void validateScaleBasedSreCodes(Lookups.Worksheet ws, String conifCode, String decidCode) {
    if (!EditCodes.SCALE_BASED_CONIFEROUS_SRE_CODES.contains(ws.conifStandRateEligibilityCode())) {
      versionError(CATEGORY_SCALING, conifCode);
    }
    if (!EditCodes.SCALE_BASED_DECIDUOUS_SRE_CODES.contains(ws.decidStandRateEligibilityCode())) {
      versionError(CATEGORY_SCALING, decidCode);
    }
  }

  /** {@code validateCruiseBasedScaleSite} of weight and sample: CBSS00 on a dedicated cruise-based site. */
  protected void validateNotCruiseBasedScaleSite() {
    if (requiredScaleSite().isDedicatedCruiseBasedSite()) {
      versionError(CATEGORY_SCALING, EditCodes.CBSS00);
    }
  }
}
