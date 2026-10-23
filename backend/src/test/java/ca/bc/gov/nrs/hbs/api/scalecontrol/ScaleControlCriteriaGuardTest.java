package ca.bc.gov.nrs.hbs.api.scalecontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.hbs.api.legacyrules.NullMocks;
import ca.bc.gov.nrs.hbs.api.queries.QueryCriteriaReads;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** P911 scaler restriction, P971 / P975 / P981 search rules and the P977 entry rules. */
class ScaleControlCriteriaGuardTest {

  private final QueryCriteriaReads reads = NullMocks.of(QueryCriteriaReads.class);
  private final AtomicBoolean scalerOnly = new AtomicBoolean(false);
  private final ScaleControlCriteriaGuard guard =
      new ScaleControlCriteriaGuard(reads, () -> LocalDate.of(2026, 10, 21), scalerOnly::get);

  private Map<String, Object> params(Object... kv) {
    Map<String, Object> p = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) p.put((String) kv[i], kv[i + 1]);
    return p;
  }

  private List<String> messages(String id, Map<String, Object> p) {
    try {
      guard.validateQuery(id, p);
      return List.of();
    } catch (WorkflowValidationException e) {
      return e.problems().stream().map(WorkflowValidationException.Problem::message).toList();
    }
  }

  private List<String> entry(Map<String, Object> body) {
    try {
      guard.validateLdsEntry(body);
      return List.of();
    } catch (WorkflowValidationException e) {
      return e.problems().stream().map(WorkflowValidationException.Problem::message).toList();
    }
  }

  @Test
  void p911_scalersAreNotOfferedArrivalTypes() {
    assertThat(messages("scaleControl.anomalies.search", params("anomalyType", "ARR_DEP"))).isEmpty();
    scalerOnly.set(true);
    assertThat(messages("scaleControl.anomalies.search", params("anomalyType", "arrival_gap")))
        .containsExactly(ScaleControlCriteriaGuard.SCALER_TYPE_NOT_OFFERED);
    assertThat(messages("scaleControl.anomalies.search", params("anomalyType", "SCALING"))).isEmpty();
  }

  @Test
  void p971_fieldCombinationsAndDates() {
    when(reads.scaleSiteExists(anyString())).thenReturn(true);
    assertThat(messages("scaleControl.transport.search", params("transportType", "CBD", "fromDate", "2026-10-01",
        "toDate", "2026-10-20"))).containsExactly("Timber Mark or Scale Site required for Cut Block Departures option.");
    assertThat(messages("scaleControl.transport.search", params("transportType", "ARR", "fromDate", "2026-10-01")))
        .containsExactly("Scale Site or Originating Site required for Arrivals From Other Sites option.");
    assertThat(messages("scaleControl.transport.search", params("transportType", "INT", "scaleSite", "A12",
        "destinationSite", "B1", "transportId", "X1", "fromDate", "2026-10-01"))).containsExactly(
        "Destination Site only permitted with Departures To Other Sites option.",
        "Transport ID option not permitted with Intra-Site Events selection.");
    assertThat(messages("scaleControl.transport.search", params("transportType", "DEP", "scaleSite", "A12",
        "fromDate", "2024-01-01", "toDate", "2025-06-01"))).containsExactly("Date ranges are restricted to one year.");
    assertThat(messages("scaleControl.transport.search", params("transportType", "DEP", "scaleSite", "A12",
        "fromDate", "2018-01-01", "toDate", "2018-06-01"))).containsExactly("Event From Date must be on or after 2019-Jan-01");
    assertThat(messages("scaleControl.transport.search", params("transportType", "DEP", "scaleSite", "A12",
        "fromDate", "2026-10-22", "toDate", "2026-10-01"))).containsExactly(
        "From Date cannot be a future date.", "To Date cannot be before From Date.");
    assertThat(messages("scaleControl.transport.search", params("transportType", "DEP", "scaleSite", "A12")))
        .containsExactly("From Date is required.");
  }

  @Test
  void p971_markCutBlockAndRanges() {
    when(reads.scaleSiteExists("A12")).thenReturn(true);
    assertThat(messages("scaleControl.transport.search", params("transportType", "DEP", "scaleSite", "A12",
        "fromDate", "2026-10-01", "cutBlock", "A B"))).containsExactly(
        "Invalid Cut Block format (embedded blank or asterisk).");
    assertThat(messages("scaleControl.transport.search", params("transportType", "DEP", "scaleSite", "A12",
        "fromDate", "2026-10-01", "cutBlock", "A*"))).containsExactly("Cut Block not permitted without Timber Mark.");
    assertThat(messages("scaleControl.transport.search", params("transportType", "ARR", "scaleSite", "A12",
        "originatingSite", "ZZ", "fromDate", "2026-10-01"))).containsExactly(
        "Originating Scale Site not in Scale Site table.");
    assertThat(messages("scaleControl.transport.search", params("transportType", "DEP", "scaleSite", "A12",
        "fromDate", "2026-10-01", "loadNumberTo", "5"))).containsExactly("From Load Number is required.");
    assertThat(messages("scaleControl.transport.search", params("transportType", "DEP", "scaleSite", "A12",
        "fromDate", "2026-10-01", "weighSlipFrom", "9", "weighSlipTo", "3"))).containsExactly(
        "To Weigh Slip Number cannot be less than From Weigh Slip Number.");
    // LdsHelper.setCriteria: a blank To number is the From number.
    Map<String, Object> p = params("transportType", "DEP", "scaleSite", "A12", "fromDate", "2026-10-01",
        "loadNumberFrom", "42");
    assertThat(messages("scaleControl.transport.search", p)).isEmpty();
    assertThat(p).containsEntry("loadNumberTo", "42");
  }

  @Test
  void p975_and_p981() {
    assertThat(messages("scaleControl.lds.search", params())).containsExactly("Please enter the Selection Criteria");
    assertThat(messages("scaleControl.lds.search", params("timberMark", "zz", "clientNumber", "12x", "district", "77")))
        .containsExactly("Timber mark must be in Timber Mark table and in Mark Billed Client table.",
            "Client must be numeric.", "Region / District is invalid.");
    assertThat(messages("scaleControl.lds.violations", params())).containsExactly("Timber Mark is required.",
        "Cut Block is required.", "From Date is required.", "To Date is required.");
    when(reads.billedMarkExists("AB123")).thenReturn(true);
    assertThat(messages("scaleControl.lds.violations", params("timberMark", "ab123", "cutBlock", "1",
        "arrivalDateFrom", "2026-10-10", "arrivalDateTo", "2026-10-01"))).containsExactly(
        "From date should be before to date.");
    assertThat(messages("scaleControl.lds.violations", params("timberMark", "ab123", "cutBlock", "1",
        "arrivalDateFrom", "2018-10-10", "arrivalDateTo", "2026-10-22"))).containsExactly(
        "Arrival From Date must be on or after 2019-Jan-01", "To Date must be before the current date.");
  }

  @Test
  void p977_entry() {
    assertThat(entry(params())).containsExactly("Timber Mark is required.", "Cut Block is required.",
        "Starting No is required.", "Ending No is required.");
    when(reads.billedMarkExists("AB123")).thenReturn(true);
    assertThat(entry(params("timberMark", "ab123", "cutBlockId", "1", "startNumber", "1x", "endNumber", 20)))
        .containsExactly("Starting No must be numeric.");
    assertThat(entry(params("timberMark", "ab123", "cutBlockId", "1", "startNumber", "1", "endNumber", "20"))).isEmpty();
  }
}
