package ca.bc.gov.nrs.hbs.api.detailreturns.validation.form;

import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.FormFixtures.arrivalLedger;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.FormFixtures.departureLedger;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.FormFixtures.fields;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.FormFixtures.logTally;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.FormFixtures.map;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.FormFixtures.messages;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.FormFixtures.sampleTally;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.FormFixtures.sfpTally;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.FormFixtures.weighSlip;
import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException.Problem;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** {@link DetailFormValidator}: the header rules of the legacy detail entry forms. */
class DetailFormValidatorTest {

  private final PermissiveFormLookups lookups = new PermissiveFormLookups();
  private final DetailFormValidator validator = new DetailFormValidator(lookups);

  private List<Problem> add(DocType type, Map<String, Object> fields) {
    return validator.validate(type, true, fields, List.of());
  }

  private List<String> addMessages(DocType type, Map<String, Object> fields) {
    return messages(add(type, fields));
  }

  private List<Problem> save(DocType type, Map<String, Object> fields) {
    return validator.validate(type, false, fields, List.of());
  }

  // ── clean documents ──────────────────────────────────────────────

  @Test
  void cleanDocumentsPass() {
    assertThat(add(DocType.LOG_TALLY, logTally())).isEmpty();
    assertThat(add(DocType.WEIGH_SLIP, weighSlip())).isEmpty();
    assertThat(add(DocType.SAMPLE_LOG_TALLY, sampleTally())).isEmpty();
    assertThat(add(DocType.ARRIVAL_LEDGER, arrivalLedger())).isEmpty();
    assertThat(add(DocType.DEPARTURE_LEDGER, departureLedger())).isEmpty();
    assertThat(add(DocType.SFP_TALLY, sfpTally())).isEmpty();
  }

  @Test
  void nullFieldsAndLogsAreTolerated() {
    assertThat(validator.validate(DocType.LOG_TALLY, false, null, null)).isEmpty();
  }

  // ── P552Form (log tally) ─────────────────────────────────────────

  @Test
  void scenarioIsRequired() {
    Map<String, Object> f = logTally();
    f.put("scaleEventTypeCode", " ");
    assertThat(add(DocType.LOG_TALLY, f)).containsExactly(new Problem("scaleEventTypeCode", "Scenario is required."));
  }

  @ParameterizedTest
  @CsvSource({"parcelCount,x,Parcel Count must be numeric.", "parcelIdentifier,1.5,Parcel ID must be numeric.",
      "parcelCount,2,", "parcelIdentifier,,"})
  void parcelsMustBeIntegers(String key, String value, String message) {
    Map<String, Object> f = logTally();
    f.put(key, value);
    assertThat(addMessages(DocType.LOG_TALLY, f)).isEqualTo(message == null ? List.of() : List.of(message));
  }

  @ParameterizedTest
  @CsvSource({",Return Number is required.", "abc,Return Number must be numeric.", "12,"})
  void returnNumberRequiredAndNumeric(String value, String message) {
    Map<String, Object> f = logTally();
    f.put("returnNumber", value);
    assertThat(addMessages(DocType.LOG_TALLY, f)).isEqualTo(message == null ? List.of() : List.of(message));
  }

  @ParameterizedTest
  @CsvSource({
      "timberMark,Timber Mark is required.", "incomingLdsNumber,LDS No is required.",
      "cutBlockId,Cut Block is required.", "transportIdentifier,Transport ID is required.",
      "loadArrivalNumber,Load Arrival is required.", "logCount,Log Count is required."})
  void primaryScaleRequiresItsFields(String key, String message) {
    Map<String, Object> f = logTally();
    f.put(key, null);
    assertThat(add(DocType.LOG_TALLY, f)).containsExactly(new Problem(key, message));
  }

  @Test
  void companyUseDoesNotRequireMarkOrLds() {
    Map<String, Object> f = logTally();
    f.put("scaleEventTypeCode", "CU");
    for (String k : List.of("timberMark", "logCount", "incomingLdsNumber", "cutBlockId", "transportIdentifier",
        "loadArrivalNumber")) {
      f.put(k, null);
    }
    assertThat(add(DocType.LOG_TALLY, f)).isEmpty();
  }

  @Test
  void sb4RescaleRequiresTimberBrand() {
    Map<String, Object> f = logTally();
    f.put("scaleEventTypeCode", "4R");
    assertThat(add(DocType.LOG_TALLY, f)).containsExactly(new Problem("timberBrand", "Timber Brand is required."));
  }

  @Test
  void checkScaleRequiresOriginalScalerAndReturn() {
    Map<String, Object> f = logTally();
    f.put("scaleEventTypeCode", "CS");
    assertThat(add(DocType.LOG_TALLY, f)).containsExactly(
        new Problem("origChkSclLicenseNumber", "Original Scaler is required."),
        new Problem("origChkSclReturnNumber", "Original Scale Return is required."));
  }

  @Test
  void unknownTimberMarkIsRefused() {
    lookups.unknownMarks.add("ABC123");
    Map<String, Object> f = logTally();
    f.put("timberMark", "abc123");
    assertThat(add(DocType.LOG_TALLY, f)).containsExactly(new Problem("timberMark",
        "Timber mark must be in Timber Mark table and in Mark Billed Client table."));
  }

  @ParameterizedTest
  @CsvSource({"0,Log Count should not = 0.", "-1,Log Count should not = 0.", "x,Log Count should not = 0.", "3,"})
  void logCountMustBePositive(String value, String message) {
    Map<String, Object> f = logTally();
    f.put("logCount", value);
    assertThat(addMessages(DocType.LOG_TALLY, f)).isEqualTo(message == null ? List.of() : List.of(message));
  }

  @ParameterizedTest
  @CsvSource({
      ",Scale Date is required.", "2026-02-30,Scale Date is not a valid date.",
      "2018-12-31,Year of scale date is more than seven years prior to current year", "2019-01-01,"})
  void scaleDate(String value, String message) {
    Map<String, Object> f = logTally();
    f.put("scaleDate", value);
    assertThat(addMessages(DocType.LOG_TALLY, f)).isEqualTo(message == null ? List.of() : List.of(message));
  }

  @Test
  void scaleSiteRequiredThenOnFile() {
    Map<String, Object> f = logTally();
    f.put("scaleSiteIdNmbr", "");
    assertThat(addMessages(DocType.LOG_TALLY, f)).containsExactly("Scale Site is required.");
    lookups.unknownSites.add("S009");
    f.put("scaleSiteIdNmbr", "s009");
    assertThat(add(DocType.LOG_TALLY, f)).containsExactly(new Problem("scaleSiteIdNmbr", "Scale Site does not exist."));
  }

  @Test
  void scaleSiteNeedsADetailSubmitterProfile() {
    lookups.siteProfile = false;
    assertThat(addMessages(DocType.LOG_TALLY, logTally())).containsExactly(
        "No scale site profile exists to permit submission of detail scale returns for this site.");
    // not checked without a valid scale date (scaleDateReqdandValid)
    Map<String, Object> f = logTally();
    f.put("scaleDate", "bad");
    assertThat(addMessages(DocType.LOG_TALLY, f)).containsExactly("Scale Date is not a valid date.");
  }

  @ParameterizedTest
  @CsvSource({"Y,,Field Scale Deck Id cannot be null", "Y,A,", "true,,Field Scale Deck Id cannot be null",
      "N,,"})
  void fieldScaleNeedsDeck(String ind, String deck, String message) {
    Map<String, Object> f = logTally();
    f.put("fieldScaleInd", ind);
    f.put("fieldScaleDeckId", deck);
    assertThat(addMessages(DocType.LOG_TALLY, f)).isEqualTo(message == null ? List.of() : List.of(message));
  }

  @Test
  void scalerLicences() {
    Map<String, Object> f = logTally();
    f.put("primaryLicenseNumber", null);
    assertThat(addMessages(DocType.LOG_TALLY, f)).containsExactly("Primary Scaler is required.");
    lookups.unknownLicences.add("9999");
    f = logTally();
    f.put("primaryLicenseNumber", "9999");
    f.put("secondaryLicenseNumber", "9999");
    f.put("signingLicenseNumber", "9999");
    assertThat(fields(add(DocType.LOG_TALLY, f))).containsExactly("primaryLicenseNumber", "secondaryLicenseNumber",
        "signingLicenseNumber");
    assertThat(addMessages(DocType.LOG_TALLY, f)).containsOnly("Scaler Licence does not exist.");
  }

  @Test
  void originalDdnMustBeActive() {
    lookups.unknownDdns.add("DDN9");
    Map<String, Object> f = logTally();
    f.put("originalDdn", "DDN9");
    assertThat(add(DocType.LOG_TALLY, f)).containsExactly(
        new Problem("originalDdn", "There is no active tally with the specified Original DDN."));
  }

  @Test
  void remoteRedTagOriginatingSite() {
    Map<String, Object> f = logTally();
    f.put("scaleEventTypeCode", "RR");
    assertThat(addMessages(DocType.LOG_TALLY, f)).containsExactly("Originating Scale Site is required.");
    lookups.unknownSites.add("S009");
    f.put("origRedtagScaleSiteIdNmbr", "S009");
    assertThat(addMessages(DocType.LOG_TALLY, f))
        .containsExactly("Originating scale site not in the scale site table.");
    f.put("origRedtagScaleSiteIdNmbr", "s001");
    assertThat(addMessages(DocType.LOG_TALLY, f))
        .containsExactly("Originating scale site cannot be the same as the Scale Site.");
  }

  // ── scaleSiteAndUser / licenceAndUser (Add only) ─────────────────

  @Test
  void siteAdminMustHaveTheSiteOrBeAScalerOnTheReturn() {
    lookups.roles = Set.of("HBS_SCALE_ADMIN");
    lookups.siteInOrgUnit = false;
    assertThat(add(DocType.LOG_TALLY, logTally())).containsExactly(new Problem("scaleSiteIdNmbr",
        "User not authorized to add a scale return for this scale site."));
    lookups.roles = Set.of("HBS_SCALE_ADMIN", "HBS_MOF_SCALER");
    assertThat(add(DocType.LOG_TALLY, logTally())).isEmpty();
    lookups.scalerAuthority = false;
    assertThat(fields(add(DocType.LOG_TALLY, logTally()))).contains("scaleSiteIdNmbr");
    lookups.siteInOrgUnit = true;
    assertThat(fields(add(DocType.LOG_TALLY, logTally()))).doesNotContain("scaleSiteIdNmbr");
  }

  @Test
  void scalerMustHoldALicenceOrAdministerTheSite() {
    lookups.roles = Set.of("HBS_CLI_SCALER");
    lookups.scalerAuthority = false;
    assertThat(add(DocType.LOG_TALLY, logTally())).containsExactly(new Problem("primaryLicenseNumber",
        "User not authorized to add a scale return for this scaler licence."));
    lookups.roles = Set.of("HBS_CLI_SCALER", "HBS_CLI_SITE_ADMIN");
    lookups.siteInOrgUnit = true;
    assertThat(add(DocType.LOG_TALLY, logTally())).isEmpty();
    lookups.siteAuthority = false;
    assertThat(fields(add(DocType.LOG_TALLY, logTally()))).containsExactly("primaryLicenseNumber");
  }

  @Test
  void userChecksAreAddOnlyAndOtherRolesPass() {
    lookups.roles = Set.of("HBS_DTL_DATA_ENT");
    lookups.siteInOrgUnit = false;
    lookups.scalerAuthority = false;
    assertThat(add(DocType.LOG_TALLY, logTally())).isEmpty();
    lookups.roles = Set.of("HBS_SCALE_ADMIN", "HBS_MOF_SCALER");
    assertThat(save(DocType.LOG_TALLY, logTally())).isEmpty();
  }

  // ── partial saves ────────────────────────────────────────────────

  @Test
  void saveChecksOnlyTheKeysSent() {
    assertThat(save(DocType.LOG_TALLY, map("returnNumber", ""))).containsExactly(
        new Problem("returnNumber", "Return Number is required."));
    // without the event type the event rules cannot apply
    assertThat(save(DocType.LOG_TALLY, map("timberMark", ""))).isEmpty();
    assertThat(save(DocType.LOG_TALLY, map("timberMark", "", "scaleEventTypeCode", "PS"))).containsExactly(
        new Problem("timberMark", "Timber Mark is required."));
    // the field scale deck check needs the deck
    assertThat(save(DocType.LOG_TALLY, map("fieldScaleInd", "Y"))).isEmpty();
    // the profile check needs the scale date
    lookups.siteProfile = false;
    assertThat(save(DocType.LOG_TALLY, map("scaleSiteIdNmbr", "S001"))).isEmpty();
  }

  // ── P600Form (weigh slip) ────────────────────────────────────────

  @ParameterizedTest
  @CsvSource({
      "sampleDeductionWeight,x,Wt less Subsample must be numeric.",
      "grossWeight,,Gross Weight is required.", "grossWeight,1.5,Gross Weight must be numeric.",
      "tareWeight,,Tare Weight is required.", "tareWeight,x,Tare Weight must be numeric.",
      "incomingLdsNumber,,LDS In is required.", "cutBlockId,,Cut Block is required.",
      "transportIdentifier,,Transport ID is required.", "timberMark,,Timber Mark is required.",
      "scaleDate,2026-06-01T25:00,Scale Date is not a valid date.",
      "scaleDate,2018-06-01T10:00,Year of scale date is more than seven years prior to current year"})
  void weighSlipFields(String key, String value, String message) {
    Map<String, Object> f = weighSlip();
    f.put(key, value);
    assertThat(add(DocType.WEIGH_SLIP, f)).containsExactly(new Problem(key, message));
  }

  @Test
  void primaryDepartureNeedsDestinationOnFile() {
    Map<String, Object> f = weighSlip();
    f.put("scaleEventTypeCode", "PD");
    f.put("outgoingLdsNumber", "LDS2");
    assertThat(add(DocType.WEIGH_SLIP, f)).containsExactly(new Problem("destination", "Destination is required."));
    lookups.unknownSites.add("S009");
    f.put("destination", "S009");
    assertThat(add(DocType.WEIGH_SLIP, f)).containsExactly(new Problem("destination", "Scale Site does not exist."));
  }

  @Test
  void weighSlipPsyRequiredWithTheLabelOfThePage() {
    Map<String, Object> f = weighSlip();
    f.put("populationNumber", null);
    f.put("stratumNumber", null);
    f.put("samplingYear", null);
    assertThat(add(DocType.WEIGH_SLIP, f)).containsExactly(new Problem("populationNumber",
        "Population / Stratum / Year combination is required."));
    lookups.nonBillableMarks.add("ABC123");
    assertThat(addMessages(DocType.WEIGH_SLIP, f)).containsExactly("Company Stratum is required.");
  }

  @ParameterizedTest
  @CsvSource({
      "PS,ABC,The PSY must contain 8 or 10 characters.",
      "PS,ABCD01XX,The sampling year must be numeric.",
      "PS,ZZZZ0126,A weigh slip with a billable event type and a billable mark requires a billable stratum.",
      "PS,ABCD0126,",
      "RT,ABCD0126,A weigh slip with a non-billable event type requires a company-use stratum",
      "RT,CO,"})
  void billableEventAndMarkNeedABillableStratum(String event, String psy, String message) {
    lookups.strata = Set.of("ABCD/01/2026");
    Map<String, Object> f = weighSlip();
    f.put("scaleEventTypeCode", event);
    f.put("companyUseStratum", psy);
    assertThat(addMessages(DocType.WEIGH_SLIP, f)).isEqualTo(message == null ? List.of() : List.of(message));
  }

  @Test
  void billableEventWithNonBillableMarkNeedsACompanyStratum() {
    lookups.nonBillableMarks.add("ABC123");
    assertThat(addMessages(DocType.WEIGH_SLIP, weighSlip())).containsExactly(
        " A weigh slip with a billable event type and a non-billable mark requires a company-use stratum");
    Map<String, Object> f = weighSlip();
    f.put("populationNumber", null);
    f.put("stratumNumber", null);
    f.put("samplingYear", null);
    f.put("companyUseStratum", "MYCO");
    assertThat(add(DocType.WEIGH_SLIP, f)).isEmpty();
  }

  @Test
  void weighSlipScenarioRequired() {
    Map<String, Object> f = weighSlip();
    f.put("scaleEventTypeCode", null);
    assertThat(add(DocType.WEIGH_SLIP, f)).containsExactly(new Problem("scaleEventTypeCode", "Scenario is required."));
  }

  // ── P650Form (sample tally) ──────────────────────────────────────

  @ParameterizedTest
  @CsvSource({
      "logCount,,Log Count is required.", "logCount,x,Log Count must be numeric.",
      "logCount,10000,Log Count cannot be greater than 9999.", "logCount,0,Log Count cannot be less than 1.",
      "logCount,9999,", "returnNumber,,Return Number is required.", "sampleWeight,,Sample Weight is required.",
      "sampleWeight,x,Sample Weight must be numeric.", "timberMark,,Timber Mark is required.",
      "scaleSiteIdNmbr,,Scale Site is required.", "primaryLicenseNumber,,Primary Scaler is required."})
  void sampleTallyFields(String key, String value, String message) {
    Map<String, Object> f = sampleTally();
    f.put(key, value);
    assertThat(add(DocType.SAMPLE_LOG_TALLY, f))
        .isEqualTo(message == null ? List.of() : List.of(new Problem(key, message)));
  }

  @Test
  void remoteSampleAndCheckScaleOriginals() {
    Map<String, Object> f = sampleTally();
    f.put("scaleEventTypeCode", "RS");
    f.put("incomingLdsNumber", "L");
    f.put("transportIdentifier", "T");
    f.put("loadArrivalNumber", "A");
    assertThat(add(DocType.SAMPLE_LOG_TALLY, f)).containsExactly(
        new Problem("origRedtagScaleSiteIdNmbr", "Originating Scale Site is required."));
    f = sampleTally();
    f.put("scaleEventTypeCode", "CS");
    f.put("origChkSclReturnNumber", "x");
    lookups.unknownLicences.add("9999");
    f.put("origChkSclLicenseNumber", "9999");
    assertThat(add(DocType.SAMPLE_LOG_TALLY, f)).containsExactly(
        new Problem("origChkSclReturnNumber", "Original Scaler Return must be numeric."),
        new Problem("origChkSclLicenseNumber", "Scaler Licence does not exist."));
  }

  @Test
  void samplePopStratYear() {
    Map<String, Object> f = sampleTally();
    f.put("populationNumber", null);
    f.put("stratumNumber", null);
    f.put("samplingYear", null);
    assertThat(add(DocType.SAMPLE_LOG_TALLY, f)).containsExactly(new Problem("populationNumber",
        "Population / Stratum / Year combination is required."));
    lookups.strata = Set.of("ABCD/01/2026");
    f = sampleTally();
    f.put("stratumNumber", "02");
    assertThat(addMessages(DocType.SAMPLE_LOG_TALLY, f))
        .containsExactly("Population / Stratum / Year combination is invalid.");
    f.put("stratumNumber", "01");
    f.put("samplingYear", "20x6");
    assertThat(addMessages(DocType.SAMPLE_LOG_TALLY, f))
        .containsExactly("Population / Stratum / Year combination is invalid.");
  }

  // ── P700Form (ledgers) ───────────────────────────────────────────

  @ParameterizedTest
  @CsvSource({
      "scaleSiteIdNmbr,Scale Site is required.", "licenseNumber,Primary Scaler is required.",
      "returnNumber,Return Number is required.", "arrivalDate,Arrival Date is required.",
      "scaleDate,Scale Date is required.", "loadArrivalNumber,Load Arrival Number is required.",
      "incomingLdsNumber,LDS In is required.", "timberMark,Timber Mark is required.",
      "transportIdentifier,Transport In is required.", "cutBlockId,Cutblock is required."})
  void arrivalPrimaryScaleRequires(String key, String message) {
    Map<String, Object> f = arrivalLedger();
    f.put(key, "");
    assertThat(add(DocType.ARRIVAL_LEDGER, f)).containsExactly(new Problem(key, message));
  }

  @ParameterizedTest
  @CsvSource({
      "scaleSiteIdNmbr,Scale Site is required.", "departureDate,Departure Date is required.",
      "destination,Destination is required.", "outgoingLdsNumber,LDS Out is required.",
      "loadDepartureNumber,Load Departure Number is required.", "outgoingTransportId,Transport Out is required."})
  void departureRequires(String key, String message) {
    Map<String, Object> f = departureLedger();
    f.put(key, null);
    assertThat(add(DocType.DEPARTURE_LEDGER, f)).containsExactly(new Problem(key, message));
  }

  @Test
  void scaledBeforeArrivalNeedsBrandNotMark() {
    Map<String, Object> f = map("scaleEventTypeCode", "B4", "scaleSiteIdNmbr", "S001", "arrivalDate", "2026-06-01",
        "incomingLdsNumber", "L", "loadArrivalNumber", "A", "transportIdentifier", "T");
    assertThat(add(DocType.ARRIVAL_LEDGER, f)).containsExactly(new Problem("timberBrand", "Timber Brand is required."));
  }

  @Test
  void ledgerDates() {
    Map<String, Object> f = arrivalLedger();
    f.put("arrivalDate", "2026-13-01");
    assertThat(add(DocType.ARRIVAL_LEDGER, f)).containsExactly(
        new Problem("arrivalDate", "Arrival Date is not a valid date."));
    f.put("arrivalDate", "2018-06-01");
    f.put("scaleDate", "2018-06-02");
    assertThat(add(DocType.ARRIVAL_LEDGER, f)).containsExactly(
        new Problem("scaleDate", "Year of scale date is more than seven years prior to current year"),
        new Problem("arrivalDate", "Year of arrival date is more than seven years prior to current year"));
    Map<String, Object> d = departureLedger();
    d.put("departureDate", "2018-06-01");
    assertThat(add(DocType.DEPARTURE_LEDGER, d)).containsExactly(
        new Problem("departureDate", "Year of departure date is more than seven years prior to current year"));
  }

  @Test
  void ledgerSitesOnFile() {
    lookups.unknownSites.add("S002");
    assertThat(add(DocType.DEPARTURE_LEDGER, departureLedger())).containsExactly(
        new Problem("destination", "Destination scale site not in the scale site table.  "));
    Map<String, Object> f = arrivalLedger();
    f.put("scaleEventTypeCode", "RR");
    f.put("origScaleSite", "S002");
    assertThat(add(DocType.ARRIVAL_LEDGER, f)).containsExactly(
        new Problem("origScaleSite", "Originating scale site not in the scale site table."));
    f.put("origScaleSite", null);
    assertThat(add(DocType.ARRIVAL_LEDGER, f)).containsExactly(
        new Problem("origScaleSite", "Originating Scale Site is required."));
  }

  @Test
  void ledgerUserChecksUseTheLedgerScaler() {
    lookups.roles = Set.of("HBS_CLI_SCALER");
    lookups.scalerAuthority = false;
    assertThat(add(DocType.ARRIVAL_LEDGER, arrivalLedger())).containsExactly(new Problem("licenseNumber",
        "User not authorized to add a scale return for this scaler licence."));
  }

  // ── P752Form (SFP tally) ─────────────────────────────────────────

  @ParameterizedTest
  @CsvSource({
      "samplePieceCount,,Prorated Piece Count is required.",
      "samplePieceCount,x,Prorated Piece Count must be a number.",
      "logCount,x,Log Count must be numeric.", "logCount,,Log Count is required.",
      "cutBlockId,,Cut Block is required.", "timberMark,,Timber Mark is required.",
      "scaleEventTypeCode,,Scenario is required.", "volumeCalcMethodType,,Scale method is required."})
  void sfpFields(String key, String value, String message) {
    Map<String, Object> f = sfpTally();
    f.put(key, value);
    assertThat(add(DocType.SFP_TALLY, f)).containsExactly(new Problem(key, message));
  }

  @Test
  void sfpLogCountZeroIsNotRefused() {
    // valueNonZeroBasedOnEventType never matched the SFP field matrix (legacy quirk)
    Map<String, Object> f = sfpTally();
    f.put("logCount", 0);
    assertThat(add(DocType.SFP_TALLY, f)).isEmpty();
  }
}
