package ca.bc.gov.nrs.hbs.api.catalog;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.query.QueryCatalog;
import ca.bc.gov.nrs.hbs.api.query.QueryDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter.Type;
import org.springframework.stereotype.Component;

import java.util.List;

import static ca.bc.gov.nrs.hbs.api.catalog.DetailReturnsSql.ALL_ERRORS;
import static ca.bc.gov.nrs.hbs.api.catalog.DetailReturnsSql.CLIENT_FENCE;
import static ca.bc.gov.nrs.hbs.api.catalog.DetailReturnsSql.DOCUMENT_VERSIONS;

/**
 * Scale Returns — Detail: the searches (legacy P046 general, P047 submitted
 * batches, P048 single return, Home "Detail Scale Return Status" work queues and
 * "Error Categories") and their shared result list (P551/P581/P601/P651/P751/P701).
 *
 * <p>All of them were one Struts action ({@code dac.detail.P046Action}) feeding
 * {@code DetailManagerQuery} / {@code fastlane.DetailReaderBean}, which called the
 * REF-CURSOR procs HBS2R551 (P, F), HBS2R601 (W), HBS2R651 (S) and HBS2R701 (A, D)
 * (nr-mof-db {@code PROCEDURES/V7.01532__HBS2R551.sql}, {@code V7.01536__HBS2R601.sql},
 * {@code V7.01540__HBS2R651.sql}, {@code V7.01544__HBS2R701.sql}). Their dynamic WHERE
 * clauses are rebuilt here as bound filters over {@link DetailReturnsSql#DOCUMENT_VERSIONS};
 * the procs are still offered as the legacy Jasper list reports.
 *
 * <p>Not ported (no request-independent bind for the legacy user id): the
 * "Associated Sites" / "Sites in Associated Districts" / "Marks in Associated
 * Districts" data-domain scopes (HBS_USER_SITE_DATA_DOMAIN / HBS_USER_DIST_DATA_DOMAIN
 * / HBS_SCALER_AUTH_KEY joins on PSR_UserId) — see docs/areas/detail-returns.md.
 */
@Component
public class DetailReturnsCatalog implements QueryCatalog {

  /** Legacy LimitYearInd (BT6020): nothing older than 7 calendar years is searchable online. */
  private static final String RETENTION =
      "\n   AND d.event_date >= ADD_MONTHS(TRUNC(SYSDATE, 'YYYY'), -84)";

  /** Correlated EXISTS over every edit error of the row's version. */
  private static String errorExists(String condition, boolean negate) {
    return "AND " + (negate ? "NOT " : "") + "EXISTS (SELECT 1 FROM (" + ALL_ERRORS + ") e"
        + " WHERE e.ddn = d.ddn AND e.version = d.version AND " + condition + ")";
  }

  private static final String BATCH_TRANSMISSION = """
      AND EXISTS (SELECT 1 FROM dtl_doc_batch_document b
                    JOIN dtl_doc_batch ddb ON ddb.batch_id = b.batch_id
                    JOIN dtl_scl_doc_transmission t ON t.transmission_id = ddb.transmission_id
                   WHERE b.detail_document_number = d.ddn AND %s)""";

  static QueryDefinition search() {
    return QueryDefinition.builder("detailReturns.search")
        .legacy("P046/P047/P048/P551/P581/P601/P651/P751/P701")
        .capability(Capability.SCALE_RETURNS_VIEW)
        .sql(DOCUMENT_VERSIONS + RETENTION)
        // General criteria — Return Type (radScaleType; X = Log and Sample Tallies, BT12638)
        .filter(QueryFilter.upper("returnType",
            "AND (d.doc_type = :returnType OR (:returnType = 'X' AND d.doc_type IN ('P', 'S')))"))
        .filter(QueryFilter.of("status", "AND d.status IN (:status)", Type.LIST))
        .filter(QueryFilter.upper("activeOnly", "AND (:activeOnly = 'N' OR d.active_ind = 'Y')"))
        .filter(QueryFilter.upper("ddn", "AND d.ddn = :ddn"))
        .filter(QueryFilter.of("eventType", "AND d.event_type IN (:eventType)", Type.LIST))
        // Error Code / Invert Selection (BT1993, BT10645)
        .filter(QueryFilter.upper("errorCode", errorExists("e.code = :errorCode", false)))
        .filter(QueryFilter.upper("notErrorCode", errorExists("e.code = :notErrorCode", true)))
        // Error Categories — Industry (I) / Ministry (M) responsibility; Joint (J) counts for both (BT13643)
        .filter(QueryFilter.upper("responsibility", errorExists("EXISTS (SELECT 1 FROM hbs_edit_err_message_code heemc"
            + " WHERE heemc.hbs_edit_err_message_code = e.code AND heemc.hbs_edit_err_category_code = e.category"
            + " AND heemc.hbs_edit_err_responsiblty_code IN (:responsibility, 'J'))", false)))
        // Home "Replaced By Check Scale" / "Check Scale Replacements" (BT6131, PSR_CROInd)
        .filter(QueryFilter.upper("mode", """
            AND d.check_replaces_original_ind = 'Y'
            AND ((:mode = 'BY_CHECK_SCALE' AND d.event_type <> 'CS')
              OR (:mode = 'CS_REPLACEMENT' AND d.event_type = 'CS'))"""))
        // Range of Dates
        .filter(QueryFilter.date("scaleDateFrom", "AND d.event_date >= :scaleDateFrom"))
        .filter(QueryFilter.dateTo("scaleDateTo", "AND d.event_date < :scaleDateTo"))
        .filter(QueryFilter.date("receivedDateFrom", "AND d.received_timestamp >= :receivedDateFrom"))
        .filter(QueryFilter.dateTo("receivedDateTo", "AND d.received_timestamp < :receivedDateTo"))
        // Home workbench default window (DacHelper.setDetailDateRange: today - 30 days)
        .filter(QueryFilter.number("days", "AND d.event_date >= TRUNC(SYSDATE) - :days"))
        // Scale Site
        .filter(QueryFilter.upper("scaleSite", "AND d.scale_site = :scaleSite"))
        .filter(QueryFilter.number("siteOrgUnit",
            "AND (d.site_org_unit_no = :siteOrgUnit OR d.site_region_no = :siteOrgUnit)"))
        // Pop/Strat/Year
        .filter(QueryFilter.upper("population", "AND d.population_number = :population"))
        .filter(QueryFilter.upper("stratum", "AND d.stratum_number = :stratum"))
        .filter(QueryFilter.number("samplingYear", "AND d.sampling_year = :samplingYear"))
        // Timber Mark (Scale-Based / Cruise-Based = PSR_TimberMarkFilter, BT15896)
        .filter(QueryFilter.upper("markBasis", "AND d.cruise_based_ind = :markBasis"))
        .filter(QueryFilter.upper("timberMark", "AND d.timber_mark = :timberMark"))
        .filter(QueryFilter.upper("cutBlock", "AND UPPER(d.cut_block_id) LIKE REPLACE(:cutBlock, '*', '%')"))
        .filter(QueryFilter.number("markOrgUnit",
            "AND (d.mark_district_no = :markOrgUnit OR d.mark_region_no = :markOrgUnit)"))
        // Primary Scaler Licence
        .filter(QueryFilter.upper("scalerLicence", "AND d.primary_licence = :scalerLicence"))
        // Range of Numbers (BT7857)
        .filter(QueryFilter.number("returnNumber", "AND d.return_number = :returnNumber"))
        .filter(QueryFilter.number("returnNumberFrom", "AND d.return_number >= :returnNumberFrom"))
        .filter(QueryFilter.number("returnNumberTo", "AND d.return_number <= :returnNumberTo"))
        .filter(QueryFilter.upper("loadNumberFrom", "AND d.load_arrival_number >= :loadNumberFrom"))
        .filter(QueryFilter.upper("loadNumberTo", "AND d.load_arrival_number <= :loadNumberTo"))
        .filter(QueryFilter.upper("weighSlipNumber", "AND d.weigh_slip_number = :weighSlipNumber"))
        .filter(QueryFilter.upper("weighSlipFrom", "AND d.weigh_slip_number >= :weighSlipFrom"))
        .filter(QueryFilter.upper("weighSlipTo", "AND d.weigh_slip_number <= :weighSlipTo"))
        .filter(QueryFilter.upper("transportId",
            "AND UPPER(NVL(d.transport_id, d.outgoing_transport_id)) LIKE REPLACE(:transportId, '*', '%')"))
        .filter(QueryFilter.upper("ldsNumber",
            "AND UPPER(NVL(d.lds_in, d.lds_out)) LIKE REPLACE(:ldsNumber, '*', '%')"))
        // Client Association (Mark Holder / Site Owner / Stratum Owner)
        .filter(QueryFilter.eq("markHolderClient", "AND d.mark_holder_client = :markHolderClient"))
        .filter(QueryFilter.eq("siteOwnerClient", "AND d.site_owner_client = :siteOwnerClient"))
        .filter(QueryFilter.eq("stratumOwnerClient", "AND d.stratum_owner_client = :stratumOwnerClient"))
        // P047 — submitted batches
        .filter(QueryFilter.date("batchReceivedFrom",
            "AND d.batdoc_id IS NOT NULL AND d.entry_timestamp >= :batchReceivedFrom"))
        .filter(QueryFilter.dateTo("batchReceivedTo",
            "AND d.batdoc_id IS NOT NULL AND d.entry_timestamp < :batchReceivedTo"))
        .filter(QueryFilter.number("batchId", """
            AND EXISTS (SELECT 1 FROM dtl_doc_batch_document b
                         WHERE b.detail_document_number = d.ddn AND b.batch_id = :batchId)"""))
        .filter(QueryFilter.eq("submitterClient", BATCH_TRANSMISSION.formatted(
            "(t.client_number = :submitterClient OR t.recd_client_number = :submitterClient)")))
        .filter(QueryFilter.upper("inputUserId", BATCH_TRANSMISSION.formatted(
            "UPPER(t.input_userid) = :inputUserId")))
        .sort("ddn", "d.ddn")
        .sort("version", "d.version")
        .sort("statusDesc", "d.status_desc")
        .sort("eventTypeDesc", "d.event_type_desc")
        .sort("eventDate", "d.event_date")
        .sort("scaleSiteDisplay", "d.scale_site")
        .sort("timberMark", "d.timber_mark")
        .sort("primaryLicenceDisplay", "d.primary_licence")
        .sort("returnNumber", "d.return_number")
        .sort("loadArrivalNumber", "d.load_arrival_number")
        .sort("weighSlipNumber", "d.weigh_slip_number")
        .sort("popStratYear", "d.pop_strat_year")
        .orderBy("d.event_date, d.status, d.ddn, d.version")
        .clientScope(CLIENT_FENCE)
        .maxRows(2000)
        .build();
  }

  private static QueryDefinition codes(String id, String table) {
    return QueryDefinition.builder(id)
        .legacy("hbs2:codelist")
        .capability(Capability.ANY_USER)
        .sql("SELECT " + table + " AS code, description FROM " + table
            + " WHERE SYSDATE BETWEEN effective_date AND expiry_date")
        .orderBy("description")
        .maxRows(1000)
        .build();
  }

  @Override
  public List<QueryDefinition> queries() {
    return List.of(
        search(),
        codes("codes.detailReturns.eventTypes", "SCALE_EVENT_TYPE_CODE"),
        codes("codes.detailReturns.docTypes", "DETAIL_SCALE_DOC_TYPE_CODE"),
        codes("codes.detailReturns.sfpScaleTypes", "HBS_SFP_SCALE_TYPE_CODE"),
        QueryDefinition.builder("codes.detailReturns.editErrors")
            .legacy("DetailHbsEditErrorManagerBean")
            .capability(Capability.ANY_USER)
            .sql("SELECT DISTINCT hbs_edit_err_message_code AS code, description FROM hbs_edit_err_message_code"
                + " WHERE SYSDATE BETWEEN effective_date AND expiry_date")
            .orderBy("code")
            .maxRows(1000)
            .build());
  }
}
