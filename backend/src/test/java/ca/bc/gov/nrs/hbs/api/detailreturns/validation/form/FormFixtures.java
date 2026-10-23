package ca.bc.gov.nrs.hbs.api.detailreturns.validation.form;

import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException.Problem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Payload builders for the form check tests (clean documents that pass every rule). */
final class FormFixtures {

  private FormFixtures() {
  }

  /** A mutable map from key/value pairs (null values allowed). */
  static Map<String, Object> map(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  /** A clean primary-scale log tally header. */
  static Map<String, Object> logTally() {
    return map("scaleEventTypeCode", "PS", "returnNumber", 56, "timberMark", "ABC123", "logCount", 1,
        "scaleDate", "2026-06-01", "scaleSiteIdNmbr", "S001", "fieldScaleInd", "N", "fieldScaleDeckId", null,
        "primaryLicenseNumber", "1234", "secondaryLicenseNumber", null, "signingLicenseNumber", null,
        "incomingLdsNumber", "LDS1", "cutBlockId", "CB1", "transportIdentifier", "TRUCK1",
        "loadArrivalNumber", "LA1", "parcelCount", 1, "parcelIdentifier", 1, "timberBrand", null,
        "origChkSclLicenseNumber", null, "origChkSclReturnNumber", null, "originalDdn", null,
        "origRedtagScaleSiteIdNmbr", null);
  }

  /** A clean primary-scale weigh slip. */
  static Map<String, Object> weighSlip() {
    return map("scaleEventTypeCode", "PS", "scaleSiteIdNmbr", "S001", "primaryLicenseNumber", "1234",
        "timberMark", "ABC123", "incomingLdsNumber", "LDS1", "cutBlockId", "CB1", "scaleDate", "2026-06-01T10:30",
        "fieldScaleInd", "N", "grossWeight", 30000, "tareWeight", 10000, "transportIdentifier", "TRUCK1",
        "populationNumber", "ABCD", "stratumNumber", "01", "samplingYear", 2026, "companyUseStratum", null,
        "sampleDeductionWeight", null, "destination", null, "outgoingLdsNumber", null, "timberBrand", null);
  }

  /** A clean sample-scale sample tally. */
  static Map<String, Object> sampleTally() {
    return map("scaleEventTypeCode", "SS", "returnNumber", 7, "logCount", 2, "scaleDate", "2026-06-01",
        "timberMark", "ABC123", "scaleSiteIdNmbr", "S001", "primaryLicenseNumber", "1234", "sampleWeight", 500,
        "populationNumber", "ABCD", "stratumNumber", "01", "samplingYear", 2026, "origChkSclReturnNumber", null,
        "origRedtagScaleSiteIdNmbr", null, "origChkSclLicenseNumber", null, "originalDdn", null);
  }

  /** A clean primary-scale arrival ledger. */
  static Map<String, Object> arrivalLedger() {
    return map("scaleEventTypeCode", "PS", "scaleSiteIdNmbr", "S001", "licenseNumber", "1234", "returnNumber", 5,
        "arrivalDate", "2026-06-01", "scaleDate", "2026-06-02", "loadArrivalNumber", "LA1",
        "incomingLdsNumber", "LDS1", "timberMark", "ABC123", "transportIdentifier", "TRUCK1", "cutBlockId", "CB1");
  }

  /** A clean departure ledger. */
  static Map<String, Object> departureLedger() {
    return map("scaleEventTypeCode", "DP", "scaleSiteIdNmbr", "S001", "departureDate", "2026-06-01",
        "destination", "S002", "outgoingLdsNumber", "LDS9", "loadDepartureNumber", "LD1",
        "outgoingTransportId", "TRUCK9");
  }

  /** A clean primary-scale SFP tally (log scale method). */
  static Map<String, Object> sfpTally() {
    return map("scaleEventTypeCode", "PS", "hbsSfpScaleTypeCode", "L", "returnNumber", 3, "timberMark", "ABC123",
        "logCount", 1, "scaleDate", "2026-06-01", "scaleSiteIdNmbr", "S001", "primaryLicenseNumber", "1234",
        "incomingLdsNumber", "LDS1", "cutBlockId", "CB1", "transportIdentifier", "TRUCK1", "loadArrivalNumber", "LA1",
        "samplePieceCount", 4);
  }

  /** A log line payload. */
  static Map<String, Object> line(Long logdtlId, Map<String, Object> fields, List<Map<String, Object>> defects) {
    Map<String, Object> m = map("logdtlId", logdtlId, "fields", fields);
    if (defects != null) m.put("defects", defects);
    return m;
  }

  static Map<String, Object> line(Map<String, Object> fields) {
    return line(null, fields, null);
  }

  /** A clean log tally / sample line. */
  static Map<String, Object> logLine() {
    return map("logNumber", 1, "scaleSpeciesCode", "FI", "scaleGradeCode", "J", "length", "5.0", "top", 20,
        "butt", 25, "nmvInd", "N");
  }

  /** A clean SFP line (log scale). */
  static Map<String, Object> sfpLine() {
    return map("sfpNumber", 1, "scaleSpeciesCode", "FI", "scaleProductCode", "XM", "scaleGradeCode", "J",
        "prodLogLength", "5.0", "prodLogTop", 20, "prodLogButt", 25);
  }

  static Map<String, Object> defect(Object number, Object type, Object volume, Object descriptor) {
    return map("logDefectId", null, "fields", map("defectNumber", number, "defectType", type, "volume", volume,
        "descriptor", descriptor));
  }

  static List<Map<String, Object>> list(Map<String, Object>... items) {
    return new ArrayList<>(List.of(items));
  }

  static List<String> messages(List<Problem> problems) {
    return problems.stream().map(Problem::message).toList();
  }

  static List<String> fields(List<Problem> problems) {
    return problems.stream().map(Problem::field).toList();
  }
}
