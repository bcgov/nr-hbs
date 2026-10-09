package ca.bc.gov.nrs.hbs.api.catalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared SQL for the Detail Scale Returns area (legacy {@code dac/detail}).
 *
 * <p>The legacy app searched each detail document type with its own REF-CURSOR
 * proc ({@code HBS2R551} log tally / SFP, {@code HBS2R601} weigh slip,
 * {@code HBS2R651} sample tally, {@code HBS2R701} arrival / departure ledgers),
 * each building the same shape of dynamic SELECT over one version table joined
 * to DETAIL_SCALE_DOCUMENT, SCALE_SITE, ORG_UNIT and MARK_BILLED_CLI. Here the six
 * version tables are normalised once into a UNION ALL inline view with common
 * column aliases (typed NULLs where a type has no such column), so one
 * whitelisted query with bound filters serves every return type — including the
 * legacy combined "Log and Sample Tallies" pseudo type X.
 *
 * <p>Every column below exists in nr-mof-db {@code TABLES/V2.*__<TABLE>.sql}
 * (LOG_TALLY, WEIGH_SLIP, SAMPLE_LOG_TALLY, SPECIAL_FOREST_PRODUCT_TALLY,
 * SCL_SITE_ARRIVAL_LEDGER, SCL_SITE_DEPARTURE_LEDGER).
 */
final class DetailReturnsSql {

  private DetailReturnsSql() {}

  /** Common column → null type (S string, N number, D date). Order = SELECT order. */
  private static final Map<String, Character> COLUMNS = new LinkedHashMap<>();

  static {
    String[] cols = {
        "ddn:S", "version:N", "doc_type:S", "status:S", "active_ind:S", "original_version_ind:S",
        "valid_signature_ind:S", "event_type:S", "scale_site:S", "input_scale_site:S", "scale_date:D",
        "event_date:D", "timber_mark:S", "timber_brand:S", "cut_block_id:S", "primary_licence:S",
        "input_primary_licence:S", "secondary_licence:S", "signing_licence:S", "return_number:N",
        "log_count:N", "net_volume:N", "load_arrival_number:S", "lds_in:S", "lds_out:S", "transport_id:S",
        "outgoing_transport_id:S", "weigh_slip_number:S", "gross_weight:N", "tare_weight:N",
        "sample_deduction_weight:N", "population_number:S", "stratum_number:S", "sampling_year:N",
        "company_use_stratum:S", "input_psy:S", "sampled_weigh_slip:S", "sample_weight:N", "sample_type:S",
        "arrival_date:D", "departure_date:D", "load_departure_number:S", "destination:S",
        "field_scale_ind:S", "field_scale_deck_id:S", "beachcomb_ind:S", "check_replaces_original_ind:S",
        "original_ddn:S", "original_ddv:N", "orig_licence:S", "orig_return_number:S", "orig_scale_site:S",
        "parcel_identifier:N", "parcel_count:N", "volume_calc_method:S", "sfp_scale_type:S",
        "sample_piece_count:N", "hash_total:S", "signing_date_time:D", "version_comment:S", "batdoc_id:N",
        "input_source:S", "entry_userid:S", "entry_timestamp:D", "update_userid:S", "update_timestamp:D"};
    for (String c : cols) {
      String[] p = c.split(":");
      COLUMNS.put(p[0], p[1].charAt(0));
    }
  }

  /** Columns every version table has under the same name. */
  private static final List<String> SAME_NAME = List.of(
      "version", "active_version_ind:active_ind", "original_version_ind", "valid_signature_ind",
      "scl_rtn_version_state_code:status", "scale_event_type_code:event_type",
      "scale_site_id_nmbr:scale_site", "input_scale_site", "detail_document_number:ddn",
      "batdoc_id", "entry_userid", "entry_timestamp", "update_userid", "update_timestamp");

  private static Map<String, String> base(String docType) {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("doc_type", "'" + docType + "'");
    for (String s : SAME_NAME) {
      String[] p = s.split(":");
      m.put(p.length == 2 ? p[1] : p[0], "t." + p[0]);
    }
    return m;
  }

  /** Columns shared by the three tally tables + weigh slip (licences, mark, cut block, signature). */
  private static void tallyCommon(Map<String, String> m) {
    for (String c : List.of("timber_mark", "timber_brand", "transport_id:transport_identifier",
        "lds_in:incoming_lds_number", "primary_licence:primary_license_number",
        "input_primary_licence:input_primary_scaler_lic", "secondary_licence:secondary_license_number",
        "signing_licence:signing_license_number", "hash_total", "signing_date_time", "input_source",
        "field_scale_deck_id", "scale_date", "event_date:scale_date")) {
      String[] p = c.split(":");
      m.put(p[0], "t." + (p.length == 2 ? p[1] : p[0]));
    }
  }

  private static String branch(String table, Map<String, String> m) {
    String select = COLUMNS.entrySet().stream()
        .map(e -> {
          String expr = m.get(e.getKey());
          if (expr == null) {
            expr = switch (e.getValue()) {
              case 'N' -> "TO_NUMBER(NULL)";
              case 'D' -> "TO_DATE(NULL)";
              default -> "TO_CHAR(NULL)";
            };
          }
          return expr + " AS " + e.getKey();
        })
        .collect(Collectors.joining(", "));
    return "SELECT " + select + " FROM " + table + " t";
  }

  private static String logTally() {
    Map<String, String> m = base("P");
    tallyCommon(m);
    m.put("cut_block_id", "t.cut_block_id");
    m.put("return_number", "t.return_number");
    m.put("log_count", "t.log_count");
    m.put("net_volume", "t.net_volume");
    m.put("load_arrival_number", "t.load_arrival_number");
    m.put("weigh_slip_number", "t.red_tag_weigh_slip");
    m.put("field_scale_ind", "t.field_scale_ind");
    m.put("beachcomb_ind", "t.beachcomb_ind");
    m.put("check_replaces_original_ind", "t.check_replaces_original_ind");
    m.put("original_ddn", "t.original_ddn");
    m.put("original_ddv", "t.original_ddv");
    m.put("orig_licence", "t.orig_chk_scl_license_number");
    m.put("orig_return_number", "TO_CHAR(t.orig_chk_scl_return_number)");
    m.put("orig_scale_site", "t.orig_redtag_scale_site_id_nmbr");
    m.put("parcel_identifier", "t.parcel_identifier");
    m.put("parcel_count", "t.parcel_count");
    m.put("volume_calc_method", "t.volume_calc_method_type");
    m.put("version_comment", "t.log_tally_comment");
    return branch("log_tally", m);
  }

  private static String weighSlip() {
    Map<String, String> m = base("W");
    tallyCommon(m);
    m.put("cut_block_id", "t.cut_block_id");
    m.put("weigh_slip_number", "t.weigh_slip_number");
    m.put("gross_weight", "t.gross_weight");
    m.put("tare_weight", "t.tare_weight");
    m.put("sample_deduction_weight", "t.sample_deduction_weight");
    m.put("population_number", "t.population_number");
    m.put("stratum_number", "t.stratum_number");
    m.put("sampling_year", "t.sampling_year");
    m.put("company_use_stratum", "t.company_use_stratum");
    m.put("input_psy", "t.input_psy");
    m.put("sample_type", "t.hbs_sample_type_code");
    m.put("lds_out", "t.outgoing_lds_number");
    m.put("destination", "t.destination");
    m.put("field_scale_ind", "t.field_scale_ind");
    m.put("version_comment", "t.weigh_slip_comment");
    return branch("weigh_slip", m);
  }

  private static String sampleTally() {
    Map<String, String> m = base("S");
    tallyCommon(m);
    m.put("return_number", "t.return_number");
    m.put("log_count", "t.log_count");
    m.put("net_volume", "t.net_volume");
    m.put("load_arrival_number", "t.load_arrival_number");
    m.put("population_number", "t.population_number");
    m.put("stratum_number", "t.stratum_number");
    m.put("sampling_year", "t.sampling_year");
    m.put("input_psy", "t.input_psy");
    m.put("sampled_weigh_slip", "t.sampled_weigh_slip");
    m.put("sample_weight", "t.sample_weight");
    m.put("check_replaces_original_ind", "t.check_replaces_original_ind");
    m.put("original_ddn", "t.original_ddn");
    m.put("original_ddv", "t.original_ddv");
    m.put("orig_licence", "t.orig_chk_scl_license_number");
    m.put("orig_return_number", "TO_CHAR(t.orig_chk_scl_return_number)");
    m.put("orig_scale_site", "t.orig_redtag_scale_site_id_nmbr");
    m.put("volume_calc_method", "t.volume_calc_method_type");
    m.put("version_comment", "t.sample_log_tally_comment");
    return branch("sample_log_tally", m);
  }

  private static String sfpTally() {
    Map<String, String> m = base("F");
    tallyCommon(m);
    m.put("cut_block_id", "t.cut_block_id");
    m.put("return_number", "t.return_number");
    m.put("log_count", "t.log_count");
    m.put("net_volume", "t.net_volume");
    m.put("load_arrival_number", "t.load_arrival_number");
    m.put("field_scale_ind", "t.field_scale_ind");
    m.put("check_replaces_original_ind", "t.check_replaces_original_ind");
    m.put("orig_licence", "t.orig_chk_scl_license_number");
    m.put("orig_return_number", "t.orig_chk_scl_return_number");
    m.put("parcel_identifier", "t.parcel_identifier");
    m.put("parcel_count", "t.parcel_count");
    m.put("volume_calc_method", "t.volume_calc_method_type");
    m.put("sfp_scale_type", "t.hbs_sfp_scale_type_code");
    m.put("sample_piece_count", "t.sample_piece_count");
    m.put("version_comment", "t.sfp_tally_comment");
    return branch("special_forest_product_tally", m);
  }

  private static String arrivalLedger() {
    Map<String, String> m = base("A");
    m.put("scale_date", "t.scale_date");
    // HBS2R701 strWhereLimitYearAL: scale date for scaled events, arrival date for B4/TD.
    m.put("event_date", "NVL(t.scale_date, t.arrival_date)");
    m.put("arrival_date", "t.arrival_date");
    m.put("timber_mark", "t.timber_mark");
    m.put("timber_brand", "t.timber_brand");
    m.put("cut_block_id", "t.cut_block_id");
    m.put("primary_licence", "t.license_number");
    m.put("input_primary_licence", "t.input_primary_scaler_lic");
    m.put("return_number", "t.return_number");
    m.put("weigh_slip_number", "t.arrival_ledger_weigh_slip");
    m.put("parcel_identifier", "t.parcel_identifier");
    m.put("parcel_count", "t.parcel_count");
    m.put("field_scale_ind", "t.field_scale_ind");
    m.put("field_scale_deck_id", "t.field_scale_deck_id");
    m.put("orig_scale_site", "t.orig_scale_site");
    m.put("transport_id", "t.transport_identifier");
    m.put("lds_in", "t.incoming_lds_number");
    m.put("load_arrival_number", "t.load_arrival_number");
    m.put("version_comment", "t.arrival_ledger_comment");
    m.put("input_source", "t.input_source");
    return branch("scl_site_arrival_ledger", m);
  }

  private static String departureLedger() {
    Map<String, String> m = base("D");
    m.put("event_date", "t.departure_date");
    m.put("departure_date", "t.departure_date");
    m.put("timber_mark", "t.timber_mark");
    m.put("timber_brand", "t.timber_brand");
    m.put("outgoing_transport_id", "t.outgoing_transport_id");
    m.put("lds_out", "t.outgoing_lds_number");
    m.put("load_departure_number", "t.load_departure_number");
    m.put("destination", "t.destination");
    return branch("scl_site_departure_ledger", m);
  }

  /** UNION ALL of every detail version table, normalised to {@link #COLUMNS}. */
  static final String VERSIONS = String.join("\n UNION ALL ",
      logTally(), weighSlip(), sampleTally(), sfpTally(), arrivalLedger(), departureLedger());

  /**
   * One row per detail document version with resolved descriptions and the
   * ownership columns the legacy procs joined (scale site owner + org unit,
   * MARK_BILLED_CLI mark holder / harvest district / cruise-based, STRATUM owner,
   * QUANT_LICENSE client of the primary scaler licence). Scalar subqueries on
   * MARK_BILLED_CLI because that table has no unique key on TIMBER_MARK.
   * Wrapped so filters can address every alias as {@code d.<col>}.
   */
  static final String DOCUMENT_VERSIONS = """
      SELECT * FROM (
        SELECT v.*,
               dsd.scale_return_state_code AS return_state,
               dsd.entry_timestamp AS received_timestamp,
               srsc.description AS return_state_desc,
               svsc.description AS status_desc,
               setc.description AS event_type_desc,
               dtc.description AS doc_type_desc,
               ss.org_unit_no AS site_org_unit_no,
               ou.rollup_region_no AS site_region_no,
               ss.owner_cli_number AS site_owner_client,
               ss.owner_cli_locn_cd AS site_owner_location,
               (SELECT MAX(m.client_number) FROM mark_billed_cli m WHERE m.timber_mark = v.timber_mark) AS mark_holder_client,
               (SELECT MAX(m.client_locn_code) FROM mark_billed_cli m WHERE m.timber_mark = v.timber_mark) AS mark_holder_location,
               (SELECT MAX(m.forest_district) FROM mark_billed_cli m WHERE m.timber_mark = v.timber_mark) AS mark_district_no,
               (SELECT MAX(m.forest_region) FROM mark_billed_cli m WHERE m.timber_mark = v.timber_mark) AS mark_region_no,
               (SELECT MAX(m.cruise_based_ind) FROM mark_billed_cli m WHERE m.timber_mark = v.timber_mark) AS cruise_based_ind,
               st.client_number AS stratum_owner_client,
               st.client_locn_code AS stratum_owner_location,
               ql.client_number AS scaler_client,
               CASE WHEN v.population_number IS NOT NULL
                    THEN v.population_number || '/' || v.stratum_number || '/' || v.sampling_year
                    WHEN v.company_use_stratum IS NOT NULL THEN v.company_use_stratum
                    WHEN v.input_psy IS NOT NULL THEN '*' || v.input_psy END AS pop_strat_year,
               NVL(v.scale_site, NVL2(v.input_scale_site, '*' || v.input_scale_site, NULL)) AS scale_site_display,
               NVL(v.primary_licence, NVL2(v.input_primary_licence, '*' || v.input_primary_licence, NULL)) AS primary_licence_display,
               v.gross_weight - v.tare_weight AS net_weight
          FROM (%s) v
          JOIN detail_scale_document dsd ON dsd.detail_document_number = v.ddn
          LEFT JOIN scale_return_state_code srsc ON srsc.scale_return_state_code = dsd.scale_return_state_code
          LEFT JOIN scl_rtn_version_state_code svsc ON svsc.scl_rtn_version_state_code = v.status
          LEFT JOIN scale_event_type_code setc ON setc.scale_event_type_code = v.event_type
          LEFT JOIN detail_scale_doc_type_code dtc ON dtc.detail_scale_doc_type_code = dsd.detail_scale_doc_type_code
          LEFT JOIN scale_site ss ON ss.scale_site_id_nmbr = v.scale_site
          LEFT JOIN org_unit ou ON ou.org_unit_no = ss.org_unit_no
          LEFT JOIN stratum st ON st.population_number = v.population_number
                              AND st.stratum_number = v.stratum_number
                              AND st.sampling_year = v.sampling_year
          LEFT JOIN quant_license ql ON ql.license_number = v.primary_licence
      ) d
       WHERE 1=1""".formatted(VERSIONS);

  /**
   * Industry fence = legacy {@code P046Action.isIndustryUserInterestedDocument} /
   * {@code DetailScaleDocumentHelper.isDetailDocViewableByClient}: the client is
   * the stratum owner, the scale-site owner, the mark holder, or holds the primary
   * scaler licence (independent scaler).
   */
  static final String CLIENT_FENCE = """
      AND (d.site_owner_client = :scopeClientNumber
           OR d.mark_holder_client = :scopeClientNumber
           OR d.stratum_owner_client = :scopeClientNumber
           OR d.scaler_client = :scopeClientNumber)""";

  /**
   * Every edit error of a version: version-level error tables plus the log /
   * line-level detail error tables (legacy BT10645 joins in HBS2R551/601/651/701
   * via HBS_GET_VERSION_ERR_TBL_NAME / HBS_GET_DETAIL_ERROR_TABLE).
   */
  static final String ALL_ERRORS = """
      SELECT e.detail_document_number AS ddn, e.version, e.hbs_edit_err_message_code AS code,
             e.hbs_edit_err_category_code AS category, 'VER' AS error_level, e.error_timestamp
        FROM log_tally_error e
      UNION ALL
      SELECT e.detail_document_number, e.version, e.hbs_edit_err_message_code,
             e.hbs_edit_err_category_code, 'VER', e.error_timestamp FROM weigh_slip_error e
      UNION ALL
      SELECT e.detail_document_number, e.version, e.hbs_edit_err_message_code,
             e.hbs_edit_err_category_code, 'VER', e.error_timestamp FROM sample_log_tally_error e
      UNION ALL
      SELECT e.detail_document_number, e.version, e.hbs_edit_err_message_code,
             e.hbs_edit_err_category_code, 'VER', e.error_timestamp FROM sfp_tally_error e
      UNION ALL
      SELECT e.detail_document_number, e.version, e.hbs_edit_err_message_code,
             e.hbs_edit_err_category_code, 'VER', e.error_timestamp FROM scale_site_arvl_ldgr_error e
      UNION ALL
      SELECT e.detail_document_number, e.version, e.hbs_edit_err_message_code,
             e.hbs_edit_err_category_code, 'VER', e.error_timestamp FROM scale_site_dep_ldgr_error e
      UNION ALL
      SELECT l.detail_document_number, l.version, e.hbs_edit_err_message_code,
             e.hbs_edit_err_category_code, 'DETAIL', e.error_timestamp
        FROM log_tally_detail l JOIN log_tally_detail_error e ON e.logdtl_id = l.logdtl_id
      UNION ALL
      SELECT l.detail_document_number, l.version, e.hbs_edit_err_message_code,
             e.hbs_edit_err_category_code, 'DETAIL', e.error_timestamp
        FROM sample_tally_detail l JOIN sample_tally_detail_error e ON e.logdtl_id = l.logdtl_id
      UNION ALL
      SELECT l.detail_document_number, l.version, e.hbs_edit_err_message_code,
             e.hbs_edit_err_category_code, 'DETAIL', e.error_timestamp
        FROM sfp_log_detail l JOIN sfp_log_tally_detail_error e ON e.logdtl_id = l.logdtl_id""";
}
