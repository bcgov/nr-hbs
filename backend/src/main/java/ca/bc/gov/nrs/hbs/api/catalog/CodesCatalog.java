package ca.bc.gov.nrs.hbs.api.catalog;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.query.QueryCatalog;
import ca.bc.gov.nrs.hbs.api.query.QueryDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Shared code lists (dropdown options) used across every area — the
 * replacement for the legacy {@code hbs2:codelist} tag and the per-JVM
 * code-table cache. Each query returns {@code code} + {@code description}.
 * Area-specific lists live in each area's catalog as {@code codes.<area>.*}.
 *
 * <p>Only currently-effective codes are returned
 * ({@code SYSDATE BETWEEN effective_date AND expiry_date}), matching the
 * legacy lookups.
 */
@Component
public class CodesCatalog implements QueryCatalog {

  /** Standard THE code-table select: {@code <TABLE>.<TABLE> AS code, description}. */
  static QueryDefinition codeTable(String id, String table) {
    return QueryDefinition.builder(id)
        .capability(Capability.ANY_USER)
        .sql("SELECT " + table + " AS code, description FROM " + table
            + " WHERE SYSDATE BETWEEN effective_date AND expiry_date")
        .orderBy("description")
        .maxRows(1000)
        .build();
  }

  /**
   * A code set from the shared {@code CODE_LIST_TABLE}
   * ({@code column_name} → {@code code_argument} / {@code expanded_result}).
   * For the THE code tables the HBS proxy role has no grant on (FILE_TYPE_CODE,
   * CLIENT_TYPE_CODE …) — the same codes, read the way the legacy app did.
   */
  static QueryDefinition codeList(String id, String columnName) {
    return QueryDefinition.builder(id)
        .capability(Capability.ANY_USER)
        .sql("SELECT code_argument AS code, expanded_result AS description FROM code_list_table"
            + " WHERE column_name = '" + columnName + "' AND SYSDATE BETWEEN effective_date AND expiry_date")
        .orderBy("description")
        .maxRows(1000)
        .build();
  }

  @Override
  public List<QueryDefinition> queries() {
    return List.of(
        // Regions + districts — the legacy "Region / District" selects
        // (chcDistrictScaled / chcDistrictHarvested), keyed by ORG_UNIT_NO.
        QueryDefinition.builder("codes.orgUnits")
            .legacy("hbs2:orgUnitList")
            .capability(Capability.ANY_USER)
            .sql("SELECT TO_CHAR(org_unit_no) AS code, org_unit_code || ' - ' || org_unit_name AS description,"
                + " org_level_code AS org_level FROM org_unit"
                + " WHERE org_level_code IN ('R','D') AND SYSDATE BETWEEN effective_date AND expiry_date")
            .orderBy("org_level_code DESC, org_unit_name")
            .maxRows(500)
            .build(),
        codeTable("codes.returnTypes", "HBS_RETURN_TYPE_CODE"),
        codeTable("codes.scaleReturnStates", "SCALE_RETURN_STATE_CODE"),
        codeTable("codes.versionStates", "SCL_RTN_VERSION_STATE_CODE"),
        codeTable("codes.txnTypes", "HBS_TXN_TYPE_CODE"),
        codeTable("codes.txnStatuses", "HBS_TXN_STATUS_CODE"),
        codeTable("codes.processingStatuses", "HBS_PROCESSING_STATUS_CODE"),
        codeTable("codes.xmlTransSteps", "HBS_XML_TRANS_STEP_CODE"),
        codeTable("codes.deliveryMethods", "HBS_DELIVERY_METHOD_CODE"),
        codeTable("codes.frequencyTypes", "HBS_FREQUENCY_TYPE_CODE"),
        codeTable("codes.sampleTypes", "HBS_SAMPLE_TYPE_CODE"),
        codeTable("codes.anomalyTypes", "SCALE_ANOMALY_TYPE_CODE"),
        codeTable("codes.anomalyStatuses", "SCALE_ANOMALY_STATUS_CODE"),
        codeList("codes.editErrorResponsibility", "HBS_ERR_USER_CD")
    );
  }
}
