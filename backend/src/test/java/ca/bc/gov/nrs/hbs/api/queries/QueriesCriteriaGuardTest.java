package ca.bc.gov.nrs.hbs.api.queries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.hbs.api.legacyrules.NullMocks;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The legacy Queries-tab search-form rules (MarkMonthlyBillingAction, P450Form, P421Form, P401/P403/P490). */
class QueriesCriteriaGuardTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
  private final QueryCriteriaReads reads = NullMocks.of(QueryCriteriaReads.class);
  private final QueriesCriteriaGuard guard = new QueriesCriteriaGuard(reads, () -> TODAY);

  private List<String> messages(String queryId, Object... kv) {
    Map<String, String> p = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) p.put((String) kv[i], (String) kv[i + 1]);
    try {
      guard.validate(queryId, p);
      return List.of();
    } catch (WorkflowValidationException e) {
      // As the user sees them: one sentence per distinct message.
      return e.problems().stream().map(WorkflowValidationException.Problem::message).distinct().toList();
    }
  }

  /** The request fields the problems point at (what the search screen highlights). */
  private List<String> fields(String queryId, Object... kv) {
    Map<String, String> p = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) p.put((String) kv[i], (String) kv[i + 1]);
    try {
      guard.validate(queryId, p);
      return List.of();
    } catch (WorkflowValidationException e) {
      return e.problems().stream().map(WorkflowValidationException.Problem::field).distinct().toList();
    }
  }

  @Test
  void otherQueries_areNotChecked() {
    assertThat(messages("timberMarks.search", "timberMark", "??")).isEmpty();
  }

  // ── P431 / P441 ──

  @Test
  void history_interval_oneYearOnEnteredDates() {
    when(reads.clientHasLocations("00001234")).thenReturn(true);
    // At most one year between the dates as entered.
    assertThat(messages("harvestHistory.billing", "billingFrom", "2025-01-15", "billingTo", "2025-12-01",
        "clientNumber", "00001234")).isEmpty();
    assertThat(messages("harvestHistory.billing", "billingFrom", "2025-01-01", "billingTo", "2026-01-01",
        "clientNumber", "00001234")).isEmpty();
    // Under a year as entered.
    assertThat(messages("harvestHistory.billing", "billingFrom", "2025-10-25", "billingTo", "2026-10-16",
        "clientNumber", "00001234")).isEmpty();
    assertThat(messages("harvestHistory.billing", "billingFrom", "2025-01-01", "billingTo", "2026-01-02",
        "clientNumber", "00001234")).containsExactly("You may not specify a date range over one year.");
    assertThat(messages("harvestHistory.billing", "billingFrom", "2010-12-01", "billingTo", "2011-01-01",
        "clientNumber", "00001234")).containsExactly("From Date must be on or after 2011-Jan-01.");
    assertThat(messages("harvestHistory.billing", "billingFrom", "2026-10-01", "billingTo", "2026-11-01",
        "clientNumber", "00001234")).containsExactly("Billing date may not be a future date.");
    assertThat(messages("harvestHistory.billing", "billingFrom", "2026-05-01", "billingTo", "2026-04-01",
        "clientNumber", "00001234")).containsExactly("Billing From date may not be a future date of Billing To date.");
    assertThat(messages("harvestHistory.billing", "billingTo", "2026-04-01", "clientNumber", "00001234"))
        .containsExactly("You must specify billing date range for your search.");
  }

  @Test
  void history_selection_marksLicencesClientSpg() {
    when(reads.billedMarkExists("AB123")).thenReturn(true);
    when(reads.licenceHasMarks("A12345")).thenReturn(true);
    assertThat(messages("harvestHistory.billing", "billingFrom", "2026-01-01", "billingTo", "2026-03-01",
        "timberMarks", "ab123, zz999", "forestFileId", "a12345")).containsExactly("The mark entered is invalid.");
    assertThat(messages("harvestHistory.scaling", "scalingFrom", "2026-01-01", "scalingTo", "2026-03-01",
        "forestFileId", "Q1", "clientLocation", "00")).containsExactly(
        "Client or client location is invalid.", "The licence entered is invalid.");
    // Two or more of species / product / grade: the combination must exist in the interval.
    assertThat(messages("harvestHistory.billing", "billingFrom", "2026-01-01", "billingTo", "2026-03-01",
        "timberMarks", "AB123", "species", "fi", "grade", "Z")).containsExactly(
        "Species, product and grade combination is invalid.");
    when(reads.speciesProductGradeInEffect("FI", null, "Z", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)))
        .thenReturn(true);
    assertThat(messages("harvestHistory.billing", "billingFrom", "2026-01-01", "billingTo", "2026-03-01",
        "timberMarks", "AB123", "species", "fi", "grade", "Z")).isEmpty();
    // A single code needs no combination check.
    assertThat(messages("harvestHistory.billing", "billingFrom", "2026-01-01", "billingTo", "2026-03-01",
        "timberMarks", "AB123", "species", "XX")).isEmpty();
  }

  @Test
  void history_criteriaInsufficient_andMgmtUnitType() {
    assertThat(messages("harvestHistory.billing", "billingFrom", "2026-01-01", "billingTo", "2026-03-01"))
        .containsExactly("Billing History criteria selection insufficient.");
    assertThat(messages("harvestHistory.scaling", "scalingFrom", "2026-01-01", "scalingTo", "2026-03-01",
        "fileType", "ALL")).containsExactly("Scaling History criteria selection insufficient.");
    assertThat(messages("harvestHistory.scaling", "scalingFrom", "2026-01-01", "scalingTo", "2026-03-01",
        "scaleSite", "A12")).isEmpty();
    assertThat(messages("harvestHistory.billing", "billingFrom", "2026-01-01", "billingTo", "2026-03-01",
        "mgmtUnitNo", "41")).containsExactly("Mgmt Unit Type Code is required.");
  }

  // ── P449 / P450 ──

  @Test
  void issuedDocuments_dates() {
    assertThat(messages("issuedDocuments.search", "documentType", "PSI")).containsExactly(
        "You must specify either issue or Scale Date date range for your search.");
    assertThat(messages("issuedDocuments.search", "documentType", "PSI", "statementNumber", "Q1234567")).isEmpty();
    assertThat(messages("issuedDocuments.search", "documentType", "PSI", "issueDateFrom", "2026-01-01")).containsExactly(
        "You must specify either issue or Scale Date date range for your search.");
    assertThat(messages("issuedDocuments.search", "documentType", "PSI", "issueDateFrom", "2018-12-31",
        "issueDateTo", "2026-11-01")).containsExactly(
        "Statement cannot be retrieved as Issue From Date is older than seven years",
        "Issue To Date must be before the current date.");
    assertThat(messages("issuedDocuments.search", "documentType", "PSI", "scaleDateFrom", "2026-05-01",
        "scaleDateTo", "2026-04-01")).containsExactly("Scale From Date should be before Scale To Date.");
  }

  @Test
  void issuedDocuments_flagsTheMissingDateFields() {
    // Nothing given: every date field, so the user sees both ranges to choose from.
    assertThat(fields("issuedDocuments.search", "documentType", "PSI"))
        .containsExactlyInAnyOrder("issueDateFrom", "issueDateTo", "scaleDateFrom", "scaleDateTo");
    // Half a range: only its missing end.
    assertThat(fields("issuedDocuments.search", "documentType", "PSI", "issueDateFrom", "2026-01-01"))
        .containsExactly("issueDateTo");
  }

  @Test
  void issuedDocuments_ratioStatementsAndPopStratYear() {
    assertThat(messages("issuedDocuments.search", "documentType", "RS", "issueDateFrom", "2026-01-01",
        "issueDateTo", "2026-02-01", "scaleSite", "A12", "timberMark", "AB123")).containsExactly(
        "Scale Site is not permitted for Ratio Statement searches.",
        "Timber Mark is not permitted for Ratio Statement searches.");
    assertThat(messages("issuedDocuments.search", "documentType", "PSI", "issueDateFrom", "2026-01-01",
        "issueDateTo", "2026-02-01", "population", "1234")).containsExactly(
        "Population / stratum / year criteria is not applicable to Piece Scale or Ledger searches.",
        "The population/stratum/year criteria can not be applied to the piece scale invoice selection.");
    assertThat(messages("issuedDocuments.search", "documentType", "WSI", "issueDateFrom", "2026-01-01",
        "issueDateTo", "2026-02-01", "population", "123", "stratum", "1")).containsExactly(
        "The population must be a four digit number.", "The stratum field must be a two digit number.",
        "The population/stratum/year field entered is not a valid input combination.");
    assertThat(messages("issuedDocuments.search", "documentType", "CSS", "issueDateFrom", "2026-01-01",
        "issueDateTo", "2026-02-01", "population", "1234", "samplingYear", "2018")).containsExactly(
        "Sampling year cannot be less than 2019");
  }

  @Test
  void issuedDocuments_clientAndCrossReferences() {
    assertThat(messages("issuedDocuments.search", "documentType", "PSI", "statementNumber", "Q1",
        "clientLocation", "00")).containsExactly("Client number must be provided when client location is provided");
    assertThat(messages("issuedDocuments.search", "documentType", "PSI", "statementNumber", "Q1",
        "sendToClientNumber", "00001234", "clientLocation", "00")).containsExactly("Client Number and Location is invalid.");
    assertThat(messages("issuedDocuments.search", "documentType", "PSI", "statementNumber", "Q1",
        "clientNumber", "00001234")).containsExactly("Client Number is invalid.");
    assertThat(messages("issuedDocuments.search", "documentType", "PSI", "statementNumber", "Q1",
        "scaleSite", "zz", "timberMark", "zz")).containsExactly("Scale Site does not exist.",
        "Timber mark must be in Timber Mark table and in Mark Billed Client table.");
  }

  // ── P421 ──

  @Test
  void billingSummary_ranges() {
    assertThat(messages("billingSummary.search", "scaleType", "P")).containsExactly(
        "You must specify either billing or Scale Date date range for your search.");
    // All districts / file types: one month.
    assertThat(messages("billingSummary.search", "scaleType", "P", "issueDateFrom", "2026-01-01",
        "issueDateTo", "2026-03-01")).containsExactly(
        "Date ranges are restricted to one month if All is chosen for Region / District Harvested or File Type "
            + "and for Region / District Scaled.");
    assertThat(messages("billingSummary.search", "scaleType", "P", "issueDateFrom", "2025-01-01",
        "issueDateTo", "2026-03-01", "regionHarvested", "1")).containsExactly("Date ranges are restricted to one year.");
    assertThat(messages("billingSummary.search", "scaleType", "P", "scaleDateFrom", "2018-01-01",
        "scaleDateTo", "2018-01-15")).containsExactly("Scale From Date must be on or after 2019-Jan-01");
    assertThat(messages("billingSummary.search", "scaleType", "P", "issueDateFrom", "2026-01-01",
        "issueDateTo", "2026-01-20", "population", "1234")).containsExactly(
        "The population/stratum/year field entered is not a valid input combination.");
    when(reads.billedMarkExists("AB123")).thenReturn(true);
    assertThat(messages("billingSummary.search", "scaleType", "W", "issueDateFrom", "2026-01-01",
        "issueDateTo", "2026-06-20", "timberMark", "AB123", "population", "1234", "samplingYear", "2026")).isEmpty();
  }

  // ── P452, P401, P403, P490 ──

  @Test
  void transmissions_dates() {
    assertThat(messages("statementTransmissions.search", "recent", "Y")).isEmpty();
    assertThat(messages("statementTransmissions.search", "fromDate", "2026-10-22", "toDate", "2026-10-23"))
        .containsExactly("Transmission From Date must be before the current date.",
            "Transmission To Date must be before the current date.");
  }

  @Test
  void scaleSiteSummary_p401() {
    assertThat(messages("scaleSiteSummary.search", "returnType", "P", "groupBy", "SCALESITEPOPULATION",
        "scaleDateFrom", "2018-12-31", "scaleDateTo", "2026-01-01")).containsExactly(
        "Scale From Date must be on or after 2019-Jan-01", "This report grouping is not available for Piece Scale.");
    assertThat(messages("scaleSiteSummary.search", "returnType", "P", "groupBy", "DISTRICTSCALESITE",
        "scaleDateFrom", "2026-01-01", "scaleDateTo", "2026-02-01", "clientAssociation", "ALL",
        "clientNumber", "00001234")).containsExactly("Association Required.");
    assertThat(messages("scaleSiteSummary.search", "scaleDateFrom", "2026-01-01", "scaleDateTo", "2026-02-01"))
        .containsExactly("A report grouping must be specified.", "A return type is required.");
    assertThat(messages("scaleSiteSummary.search", "returnType", "W", "groupBy", "SCALESITETIMBERMARK",
        "scaleDateFrom", "2026-01-01", "scaleDateTo", "2026-02-01", "clientAssociation", "MARK HOLDER", "scalerLicence", "1234",
        "population", "1234")).containsExactly("Client information Required.",
        "A scaler licence cannot be specified for weight scale.");
    assertThat(messages("scaleSiteSummary.search", "returnType", "P", "groupBy", "DISTRICTSCALESITE",
        "scaleDateFrom", "2026-01-01", "scaleDateTo", "2026-02-01", "population", "1234")).containsExactly(
        "The population/stratum/year criteria can not be applied to the piece scale invoice selection.");
  }

  @Test
  void agedUnbilled_p403() {
    assertThat(messages("agedUnbilled.search")).containsExactly("Scaled Prior To date is required.");
    assertThat(messages("agedUnbilled.search", "scaledPriorTo", "2018-06-01")).containsExactly(
        "Scaled Prior To Date must be on or after 2019-Jan-01");
    assertThat(QueriesCriteriaGuard.averageRates(Map.of("coastRate", "12.5")).stream()
        .map(WorkflowValidationException.Problem::message)).isEmpty();
    assertThat(QueriesCriteriaGuard.averageRates(new java.util.LinkedHashMap<>(Map.of("coastRate", "0")))
        .get(0).message()).isEqualTo("Estimated Average Rate is required.");
    assertThat(QueriesCriteriaGuard.averageRates(Map.of("northRate", "1.234")).get(0).message())
        .isEqualTo("Estimated Average Rate must be numeric currency value.");
    assertThat(QueriesCriteriaGuard.averageRates(Map.of("southRate", "abc")).get(0).message())
        .isEqualTo("Estimated Average Rate must be numeric currency value.");
  }

  @Test
  void cutToCruise_p490() {
    when(reads.billedMarkExists(anyString())).thenReturn(true);
    assertThat(messages("cutToCruise.search", "timberMark", "AB123", "expiryDate", "2026-01-01")).containsExactly(
        "Expiration Date Exclusion can only be used with Forest File Id or Client/Loc.");
    when(reads.licenceHasMarks(eq("A1"))).thenReturn(true);
    assertThat(messages("cutToCruise.search", "forestFileId", "A1", "expiryDate", "2026-12-01")).containsExactly(
        "Expiration Date may not be a future date.");
    assertThat(messages("cutToCruise.search", "clientNumber", "00001234")).containsExactly("Client Number is invalid.");
    when(reads.clientHasLocations(any())).thenReturn(true);
    assertThat(messages("cutToCruise.search", "clientNumber", "00001234", "clientLocation", "99")).containsExactly(
        "Client Number and Location is invalid.");
  }

  @Test
  void helpers() {
    assertThat(QueriesCriteriaGuard.popStratYearCombination("1234", null, "2026", true)).isTrue();
    assertThat(QueriesCriteriaGuard.popStratYearCombination(null, "01", "2026", true)).isFalse();
    assertThat(QueriesCriteriaGuard.popStratYearCombination("1234", null, null, false)).isFalse();
    assertThat(QueriesCriteriaGuard.withinMonths(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), 1)).isTrue();
    assertThat(QueriesCriteriaGuard.withinMonths(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 2), 1)).isFalse();
  }
}
