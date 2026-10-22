package ca.bc.gov.nrs.hbs.api.intake;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.util.LinkedCaseInsensitiveMap;

/** In-memory {@link UnpackRepository}: every reference exists unless a test says otherwise. */
class StubUnpackRepository implements UnpackRepository {

  final Map<Long, Map<String, Object>> transmissions = new HashMap<>();
  final Map<String, Map<String, Object>> documents = new HashMap<>();
  final Map<String, List<Map<String, Object>>> versions = new HashMap<>();
  final Map<String, Map<String, Object>> versionRows = new HashMap<>();
  final Set<String> missingScaleSites = new HashSet<>();
  final Set<String> sitesWithoutSubmitter = new HashSet<>();
  final Set<String> existingVersions = new HashSet<>();
  boolean clientLocationExists = true;
  Map<String, Object> paperBatch;

  /** A P505 transmission at RCD/CSA, as SubmissionService leaves it. */
  Map<String, Object> receivedTransmission(long id) {
    Map<String, Object> row = new LinkedCaseInsensitiveMap<>();
    row.put("TRANSMISSION_ID", BigDecimal.valueOf(id));
    row.put("HBS_XML_TRANS_STEP_CODE", "RCD");
    row.put("HBS_PROCESSING_STATUS_CODE", "CSA");
    row.put("ACCEPTED_IND", "N");
    row.put("CLIENT_NUMBER", null);
    row.put("INPUT_USERID", "IDIR\\JSCALER");
    row.put("USER_ID", null);
    row.put("CLIENT_LOCN_CODE", null);
    row.put("FILE_NAME", "upload.xml");
    row.put("FILE_DATETIME", Timestamp.valueOf("2026-10-05 10:00:00"));
    row.put("BATCH_COUNT", BigDecimal.ZERO);
    row.put("RECORD_COUNT", BigDecimal.ZERO);
    row.put("TEST_FILE_IND", null);
    row.put("ENTRY_USERID", "IDIR\\JSCALER");
    row.put("ENTRY_TIMESTAMP", Timestamp.valueOf("2026-10-05 10:00:00"));
    row.put("RECD_DIRECTORY", "input/00012345/" + id + ".xml");
    row.put("RECD_CLIENT_NUMBER", "00012345");
    row.put("UPDATE_USERID", "IDIR\\JSCALER");
    row.put("UPDATE_TIMESTAMP", Timestamp.valueOf("2026-10-05 10:00:00"));
    row.put("HBS_XML_PURGE_STATUS_CODE", null);
    row.put("HBS_XML_PURGE_TIMESTAMP", null);
    row.put("SOFTWARE_PRODUCT", null);
    row.put("SOFTWARE_VERSION", null);
    row.put("SOFTWARE_REVISION", null);
    transmissions.put(id, row);
    return row;
  }

  @Override
  public List<Pending> pendingTransmissions() {
    List<Pending> list = new ArrayList<>();
    transmissions.forEach((id, row) -> list.add(new Pending(id, (String) row.get("RECD_DIRECTORY"),
        (String) row.get("RECD_CLIENT_NUMBER"))));
    return list;
  }

  @Override
  public Map<String, Object> transmission(long transmissionId) {
    return transmissions.get(transmissionId);
  }

  @Override
  public Map<String, Object> detailScaleDocument(String ddn) {
    return documents.get(ddn);
  }

  @Override
  public List<Map<String, Object>> versions(String docTypeCode, String ddn) {
    return versions.getOrDefault(ddn, List.of());
  }

  @Override
  public Map<String, Object> versionRow(String docTypeCode, String ddn, long version) {
    return versionRows.get(ddn + "/" + version);
  }

  @Override
  public Map<String, Object> pendingPaperBatch(String scaleSite, Date from, Date to, long documentCount) {
    return paperBatch;
  }

  @Override
  public Long rsaPaddingId(String description) {
    return "RSA_15".equals(description) ? 1L : "RSA_OAEP".equals(description) ? 2L : null;
  }

  @Override
  public boolean clientLocationExists(String clientNumber, String clientLocnCode) {
    return clientLocationExists;
  }

  @Override
  public boolean activeDetailSubmitterExists(String scaleSite, String returnTypeCode) {
    return !sitesWithoutSubmitter.contains(scaleSite + "/" + returnTypeCode);
  }

  @Override
  public boolean scaleSiteExists(String scaleSite) {
    return !missingScaleSites.contains(scaleSite);
  }

  @Override
  public String existingDocumentType(String ddn) {
    Map<String, Object> doc = documents.get(ddn);
    return doc == null ? null : (String) doc.get("DETAIL_SCALE_DOC_TYPE_CODE");
  }

  @Override
  public boolean versionExists(char documentType, String ddn, long version) {
    return existingVersions.contains(ddn + "/" + version);
  }
}
