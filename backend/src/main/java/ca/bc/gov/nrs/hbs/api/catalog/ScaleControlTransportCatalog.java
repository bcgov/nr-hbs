package ca.bc.gov.nrs.hbs.api.catalog;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.query.CommandDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryCatalog;
import ca.bc.gov.nrs.hbs.api.query.QueryDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter.Type;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scale Control (legacy tab P900): Timber Transport Events (P971/P972) and the
 * Load Description Slip registry (P975–P979, P981/P982).
 *
 * <p>The legacy screens called the REF-CURSOR report procs HBS3R972/973/974/979,
 * HBS3R976 and HBS3R982, which only build dynamic SQL (by concatenating the
 * criteria). Their SELECTs are ported here as bound queries:
 * <ul>
 *   <li>Timber transport: the four procs' UNION branches are combined into one
 *       inline view tagged with {@code transport_type}. Each branch exposes the
 *       hidden filter columns {@code load_no}/{@code ws_no}/{@code orig_site_*}
 *       as NULL where the legacy proc dropped that branch (or skipped that
 *       predicate), so the outer bound filters reproduce the procs' branch
 *       selection exactly (see the per-branch comments).</li>
 *   <li>Writes go through HBS_CREATE/STORE/REMOVE_LOAD_DESC_SLIP_REG, as
 *       {@code P977Action} did through the LoadDescSlipRegistry entity bean.</li>
 * </ul>
 */
@Component
public class ScaleControlTransportCatalog implements QueryCatalog {

  /** HBS_CONSTANTS.P971_VERSION_STATES + active version — every transport branch. */
  private static final String ACTIVE =
      " AND x.scl_rtn_version_state_code IN ('RDY','ERR','LCK','ISS','HLD','NTI','AWP')"
          + " AND x.active_version_ind = 'Y'";

  /** HBS_CONSTANTS.VOLUME_FORMAT. */
  private static final String VOL = "'FM99,999,990.000'";

  /** decode(hbs_is_numeric(c),'Y',to_number(c),-1) — the procs' number-range test. */
  private static String num(String column) {
    return "DECODE(hbs_is_numeric(" + column + "),'Y',TO_NUMBER(" + column + "),-1)";
  }

  /**
   * One UNION branch. Column order: transport_type, detail_document_number,
   * doc_version, doc_type, scale_event_type_code, version_state, timber_mark,
   * cut_block_id, from_scale_site, transport_id, lds_number, scale_site,
   * destination_site, event_date, lan_or_wsn, lan_or_wsn_sort,
   * scaler_return_or_psy, kg_or_m3, load_no, ws_no, orig_site_a, orig_site_b.
   */
  private static String branch(String type, String docType, String timberMark, String cutBlock,
      String fromSite, String transportId, String ldsNumber, String destination, String eventDate,
      String lanOrWsn, String lanOrWsnSort, String scalerReturnOrPsy, String kgOrM3,
      String loadNo, String wsNo, String origA, String origB, String from, String eventTypes) {
    return "SELECT '" + type + "' AS transport_type, x.detail_document_number, x.version AS doc_version, '"
        + docType + "' AS doc_type, x.scale_event_type_code, x.scl_rtn_version_state_code AS version_state, "
        + timberMark + " AS timber_mark, " + cutBlock + " AS cut_block_id, " + fromSite + " AS from_scale_site, "
        + transportId + " AS transport_id, " + ldsNumber + " AS lds_number, x.scale_site_id_nmbr AS scale_site, "
        + destination + " AS destination_site, " + eventDate + " AS event_date, "
        + lanOrWsn + " AS lan_or_wsn, " + lanOrWsnSort + " AS lan_or_wsn_sort, "
        + scalerReturnOrPsy + " AS scaler_return_or_psy, " + kgOrM3 + " AS kg_or_m3, "
        + loadNo + " AS load_no, " + wsNo + " AS ws_no, " + origA + " AS orig_site_a, " + origB + " AS orig_site_b"
        + " FROM " + from + " WHERE x.scale_event_type_code IN (" + eventTypes + ")" + ACTIVE;
  }

  private static final String LAN = "NVL2(x.parcel_identifier, x.load_arrival_number || '-' || x.parcel_identifier, x.load_arrival_number)";
  private static final String LAN_SORT = "NVL2(x.parcel_identifier, x.load_arrival_number || x.parcel_identifier, x.load_arrival_number)";
  private static final String PSY = "NVL2(x.population_number, x.population_number || '/' || x.stratum_number || '/' || x.sampling_year,"
      + " NVL2(x.company_use_stratum, x.company_use_stratum, '*' || x.input_psy))";
  private static final String WS_KG = "TO_CHAR(NVL(x.gross_weight - x.tare_weight, 0))";
  private static final String M3 = "TO_CHAR(NVL(x.net_volume, 0), " + VOL + ")";

  private static final String TRANSPORT_UNION = String.join("\n UNION \n", List.of(
      // ---- HBS3R972: Cut Block Departures (CBD) ----
      // Arrival ledger: load range applies; weigh-slip range drops the branch.
      branch("CBD", "AL", "x.timber_mark", "x.cut_block_id", "NULL", "x.transport_identifier", "x.incoming_lds_number",
          "NULL", "x.arrival_date", LAN, LAN_SORT, "x.license_number || ' ' || x.return_number", "NULL",
          num("x.load_arrival_number"), "NULL", "NULL", "NULL",
          "scl_site_arrival_ledger x", "'PS','TD'"),
      branch("CBD", "LT", "x.timber_mark", "x.cut_block_id", "NULL", "x.transport_identifier", "x.incoming_lds_number",
          "NULL", "x.scale_date", LAN, LAN_SORT, "x.primary_license_number || ' ' || x.return_number", M3,
          num("x.load_arrival_number"), "NULL", "NULL", "NULL",
          "log_tally x", "'PS'"),
      // Weigh slip: weigh-slip range applies; load range drops the branch.
      branch("CBD", "WS", "x.timber_mark", "x.cut_block_id", "NULL", "x.transport_identifier", "x.incoming_lds_number",
          "NULL", "x.scale_date", "x.weigh_slip_number", "x.weigh_slip_number", PSY, WS_KG,
          "NULL", num("x.weigh_slip_number"), "NULL", "NULL",
          "weigh_slip x", "'PD','PS','RR','RS','RT','SS'"),

      // ---- HBS3R973: Arrivals From Other Sites (ARR) ----
      branch("ARR", "AL", "x.timber_mark", "x.cut_block_id", "NVL(x.orig_scale_site, ss.scale_site_id_nmbr)",
          "x.transport_identifier", "x.incoming_lds_number", "NULL", "x.arrival_date", LAN, LAN_SORT,
          "x.license_number || ' ' || x.return_number", "NULL",
          num("x.load_arrival_number"), num("x.arrival_ledger_weigh_slip"), "x.orig_scale_site", "ss.scale_site_id_nmbr",
          "scl_site_arrival_ledger x LEFT JOIN scale_site ss ON x.timber_brand = ss.timber_brand", "'RR','RS','4R','B4'"),
      branch("ARR", "LT", "x.timber_mark", "x.cut_block_id", "NVL(x.orig_redtag_scale_site_id_nmbr, ss.scale_site_id_nmbr)",
          "x.transport_identifier", "x.incoming_lds_number", "NULL", "x.scale_date", LAN, LAN_SORT,
          "x.primary_license_number || ' ' || x.return_number",
          "DECODE(x.scale_event_type_code, 'RR', TO_CHAR(NVL(x.net_volume, 0), " + VOL + "), '4R', NULL)",
          num("x.load_arrival_number"), num("x.red_tag_weigh_slip"), "x.orig_redtag_scale_site_id_nmbr", "ss.scale_site_id_nmbr",
          "log_tally x LEFT JOIN scale_site ss ON x.timber_brand = ss.timber_brand", "'RR','4R'"),
      // Sample tally has no cut block (a cut-block search drops it, as in the proc).
      branch("ARR", "ST", "x.timber_mark", "NULL", "x.orig_redtag_scale_site_id_nmbr",
          "x.transport_identifier", "x.incoming_lds_number", "NULL", "x.scale_date",
          "x.load_arrival_number", "x.load_arrival_number",
          "x.primary_license_number || ' ' || x.return_number", M3,
          num("x.load_arrival_number"), num("x.sampled_weigh_slip"), "x.orig_redtag_scale_site_id_nmbr", "NULL",
          "sample_log_tally x", "'RS'"),
      // Weigh slip has no timber mark / load number (a mark or load search drops it, as in the proc).
      branch("ARR", "WS", "NULL", "NULL", "ss.scale_site_id_nmbr",
          "x.transport_identifier", "x.incoming_lds_number", "NULL", "x.scale_date",
          "x.weigh_slip_number", "x.weigh_slip_number",
          "NVL2(x.company_use_stratum, x.company_use_stratum, '*' || x.input_psy)", "NULL",
          "NULL", num("x.weigh_slip_number"), "NULL", "ss.scale_site_id_nmbr",
          "weigh_slip x LEFT JOIN scale_site ss ON x.timber_brand = ss.timber_brand", "'4R'"),

      // ---- HBS3R974: Departures To Other Sites (DEP) ----
      // Departure ledger: no timber mark (a mark search drops it); load range only.
      branch("DEP", "DL", "NULL", "NULL", "NULL", "x.outgoing_transport_id", "x.outgoing_lds_number",
          "x.destination", "x.departure_date", "x.load_departure_number", "x.load_departure_number", "NULL", "NULL",
          num("x.load_departure_number"), "NULL", "NULL", "NULL",
          "scl_site_departure_ledger x", "'DP'"),
      branch("DEP", "WS", "x.timber_mark", "x.cut_block_id", "NULL", "x.transport_identifier", "x.outgoing_lds_number",
          "x.destination", "x.scale_date", "x.weigh_slip_number", "x.weigh_slip_number", PSY,
          "DECODE(x.scale_event_type_code, 'DP', NULL, " + WS_KG + ")",
          "NULL", num("x.weigh_slip_number"), "NULL", "NULL",
          "weigh_slip x", "'DP','PD','RR','RS'"),

      // ---- HBS3R979: Intra-Site Events (INT) — no transport id / LDS ----
      branch("INT", "AL", "x.timber_mark", "NULL", "NULL", "NULL", "NULL", "NULL", "x.scale_date",
          "x.load_arrival_number", "x.load_arrival_number", "x.license_number || ' ' || x.return_number", "NULL",
          num("x.load_arrival_number"), "NULL", "NULL", "NULL",
          "scl_site_arrival_ledger x", "'FD'"),
      branch("INT", "LT", "x.timber_mark", "x.cut_block_id", "NULL", "NULL", "NULL", "NULL", "x.scale_date",
          "DECODE(x.scale_event_type_code, 'FD', x.load_arrival_number, 'RT', x.red_tag_weigh_slip)",
          "DECODE(x.scale_event_type_code, 'FD', x.load_arrival_number, 'RT', x.red_tag_weigh_slip)",
          "x.primary_license_number || ' ' || x.return_number",
          "DECODE(x.scale_event_type_code, 'CU', NULL, TO_CHAR(NVL(x.net_volume, 0), " + VOL + "))",
          num("x.load_arrival_number"), num("x.red_tag_weigh_slip"), "NULL", "NULL",
          "log_tally x", "'CS','CU','FD','RT'"),
      branch("INT", "ST", "x.timber_mark", "NULL", "NULL", "NULL", "NULL", "NULL", "x.scale_date",
          "x.sampled_weigh_slip", "x.sampled_weigh_slip", "x.primary_license_number || ' ' || x.return_number", M3,
          "NULL", num("x.sampled_weigh_slip"), "NULL", "NULL",
          "sample_log_tally x", "'CS','SS'"),
      branch("INT", "WS", "NULL", "NULL", "NULL", "NULL", "NULL", "NULL", "x.scale_date",
          "x.weigh_slip_number", "x.weigh_slip_number", "NULL", "NULL",
          "NULL", num("x.weigh_slip_number"), "NULL", "NULL",
          "weigh_slip x", "'CU'")));

  /** Bound replacement for the procs' REGEXP_LIKE(col, '^'||HBS_ESCAPE_FOR_REGEXP(v)||'$', 'i') ('*' wildcard). */
  private static String wildcard(String column, String param) {
    return "AND REGEXP_LIKE(" + column + ", '^' || HBS_ESCAPE_FOR_REGEXP(:" + param + ") || '$', 'i')";
  }

  @Override
  public List<QueryDefinition> queries() {
    return List.of(
        // P971 / P972 — Search for Timber Transport Events / List of <type>.
        QueryDefinition.builder("scaleControl.transport.search")
            .legacy("P971/P972")
            .capability(Capability.SCALE_CONTROL_MINISTRY_VIEW)
            .sql("SELECT t.transport_type, t.detail_document_number, t.doc_version, t.doc_type,"
                + " t.scale_event_type_code, t.version_state, t.timber_mark, t.cut_block_id, t.from_scale_site,"
                + " t.transport_id, t.lds_number, t.scale_site, t.destination_site, t.event_date,"
                + " t.lan_or_wsn, t.scaler_return_or_psy, t.kg_or_m3"
                + " FROM (\n" + TRANSPORT_UNION + "\n) t WHERE 1=1")
            .filter(QueryFilter.required("transportType", "AND t.transport_type = :transportType", Type.UPPER))
            .filter(QueryFilter.upper("timberMark", "AND t.timber_mark = :timberMark"))
            .filter(QueryFilter.eq("cutBlock", wildcard("t.cut_block_id", "cutBlock")))
            .filter(QueryFilter.eq("scaleSite", "AND t.scale_site = :scaleSite"))
            .filter(QueryFilter.eq("originatingSite",
                "AND (t.orig_site_a = :originatingSite OR t.orig_site_b = :originatingSite)"))
            .filter(QueryFilter.eq("destinationSite", "AND t.destination_site = :destinationSite"))
            .filter(QueryFilter.required("fromDate", "AND t.event_date >= :fromDate", Type.DATE))
            .filter(QueryFilter.required("toDate", "AND t.event_date < :toDate", Type.DATE_TO))
            .filter(QueryFilter.number("loadNumberFrom", "AND t.load_no >= :loadNumberFrom"))
            .filter(QueryFilter.number("loadNumberTo", "AND t.load_no <= :loadNumberTo"))
            .filter(QueryFilter.number("weighSlipFrom", "AND t.ws_no >= :weighSlipFrom"))
            .filter(QueryFilter.number("weighSlipTo", "AND t.ws_no <= :weighSlipTo"))
            .filter(QueryFilter.eq("transportId", wildcard("t.transport_id", "transportId")))
            .filter(QueryFilter.eq("ldsNumber", wildcard("t.lds_number", "ldsNumber")))
            .sort("scaleSite", "t.scale_site")
            .sort("eventDate", "t.event_date")
            .sort("timberMark", "t.timber_mark")
            .sort("detailDocumentNumber", "t.detail_document_number")
            .sort("lanOrWsn", "t.lan_or_wsn_sort")
            .orderBy("t.scale_site, t.event_date, t.lan_or_wsn_sort")
            .build(),

        // P975 / P976 — Search for LDS Registry Entries / List of Pre-Registered LDS Numbers (HBS3R976).
        QueryDefinition.builder("scaleControl.lds.search")
            .legacy("P975/P976")
            .capability(Capability.SCALE_CONTROL_MINISTRY_VIEW)
            .sql("""
                SELECT lds.ldsreg_id, lds.timber_mark, lds.cut_block_id, lds.start_number, lds.end_number,
                       lds.entry_timestamp AS date_registered, ou.org_unit_name AS district,
                       mbc.client_number, mbc.client_locn_code, mbc.forest_file_id AS licence,
                       mbc.cutting_permit_id AS permit
                  FROM load_desc_slip_registry lds
                  JOIN mark_billed_cli mbc ON lds.timber_mark = mbc.timber_mark
                  JOIN org_unit ou ON mbc.forest_district = ou.org_unit_no
                 WHERE 1=1""")
            .filter(QueryFilter.upper("timberMark", "AND lds.timber_mark = :timberMark"))
            .filter(QueryFilter.eq("cutBlock", "AND lds.cut_block_id = :cutBlock"))
            .filter(QueryFilter.eq("clientNumber", "AND mbc.client_number = :clientNumber"))
            .filter(QueryFilter.upper("licence", "AND mbc.forest_file_id = :licence"))
            // HBS_isRegion(d) ? OU.ROLLUP_REGION_NO = d : OU.ORG_UNIT_NO = d
            .filter(QueryFilter.number("district", "AND (ou.org_unit_no = :district OR ou.rollup_region_no = :district)"))
            .sort("timberMark", "lds.timber_mark")
            .sort("cutBlockId", "lds.cut_block_id")
            .sort("startNumber", "lds.start_number")
            .sort("dateRegistered", "lds.entry_timestamp")
            .orderBy("lds.timber_mark, lds.cut_block_id, lds.start_number")
            .build(),

        // P978 / P979 — one registry entry (read-only row + edit pre-fill). Outer joins so an
        // entry whose mark has no MARK_BILLED_CLI row can still be maintained.
        QueryDefinition.builder("scaleControl.lds.entry")
            .legacy("P978/P979")
            .capability(Capability.SCALE_CONTROL_MINISTRY_VIEW)
            .sql("""
                SELECT lds.ldsreg_id, lds.timber_mark, lds.cut_block_id, lds.start_number, lds.end_number,
                       lds.entry_timestamp AS date_registered, lds.entry_userid, lds.entry_timestamp,
                       lds.update_userid, lds.update_timestamp,
                       ou.org_unit_name AS district, mbc.client_number, mbc.client_locn_code,
                       mbc.forest_file_id AS licence, mbc.cutting_permit_id AS permit
                  FROM load_desc_slip_registry lds
                  LEFT JOIN mark_billed_cli mbc ON lds.timber_mark = mbc.timber_mark
                  LEFT JOIN org_unit ou ON mbc.forest_district = ou.org_unit_no
                 WHERE 1=1""")
            .filter(QueryFilter.required("ldsregId", "AND lds.ldsreg_id = :ldsregId", Type.NUMBER))
            .orderBy("lds.ldsreg_id")
            .maxRows(1)
            .build(),

        // P981 / P982 — Search for / List of LDS Registry Violations (HBS3R982 + HBS_LDS_REG_VIOLATION_TYPE).
        // "Arrival Date" is the return's ENTRY_TIMESTAMP (received date), as in the proc.
        QueryDefinition.builder("scaleControl.lds.violations")
            .legacy("P981/P982")
            .capability(Capability.SCALE_CONTROL_MINISTRY_VIEW)
            .sql("""
                SELECT u.timber_mark, u.cut_block, u.transport_identifier, u.incoming_lds_number AS lds_number,
                       u.entry_timestamp AS arrival_date, u.scale_site_id_nmbr AS scale_site,
                       u.load_arrival_number, dsdtc.description AS return_type, u.scale_date,
                       u.detail_document_number, setc.description AS event_type,
                       CASE WHEN INSTR(:cutBlock, CHR(39)) = 0 AND INSTR(:timberMark, CHR(39)) = 0
                            THEN HBS_LDS_REG_VIOLATION_TYPE(u.incoming_lds_number, :timberMark, :cutBlock,
                                   TO_CHAR(:arrivalDateFrom, 'yyyy-mm-dd-hh24-mi-ss'),
                                   TO_CHAR(:arrivalDateTo, 'yyyy-mm-dd-hh24-mi-ss')) END AS anomaly,
                       (SELECT LISTAGG(r.start_number || ' - ' || r.end_number, ', ')
                                 WITHIN GROUP (ORDER BY r.start_number)
                          FROM load_desc_slip_registry r
                         WHERE r.timber_mark = u.timber_mark AND r.cut_block_id = u.cut_block) AS assigned_lds_range
                  FROM (SELECT dsd.detail_scale_doc_type_code, dsd.detail_document_number, lt.timber_mark,
                               lt.cut_block_id AS cut_block, lt.transport_identifier, lt.incoming_lds_number,
                               lt.entry_timestamp, lt.scale_site_id_nmbr, lt.load_arrival_number, lt.scale_date,
                               lt.scale_event_type_code
                          FROM detail_scale_document dsd
                          JOIN log_tally lt ON dsd.detail_document_number = lt.detail_document_number
                         WHERE lt.timber_mark = :timberMark AND lt.cut_block_id = :cutBlock
                           AND lt.entry_timestamp >= :arrivalDateFrom AND lt.entry_timestamp < :arrivalDateTo
                        UNION
                        SELECT dsd.detail_scale_doc_type_code, dsd.detail_document_number, ws.timber_mark,
                               ws.cut_block_id, ws.transport_identifier, ws.incoming_lds_number,
                               ws.entry_timestamp, ws.scale_site_id_nmbr, TO_CHAR(NULL), ws.scale_date,
                               ws.scale_event_type_code
                          FROM detail_scale_document dsd
                          JOIN weigh_slip ws ON dsd.detail_document_number = ws.detail_document_number
                         WHERE ws.timber_mark = :timberMark AND ws.cut_block_id = :cutBlock
                           AND ws.entry_timestamp >= :arrivalDateFrom AND ws.entry_timestamp < :arrivalDateTo) u
                  LEFT JOIN detail_scale_doc_type_code dsdtc
                         ON u.detail_scale_doc_type_code = dsdtc.detail_scale_doc_type_code
                  LEFT JOIN scale_event_type_code setc ON u.scale_event_type_code = setc.scale_event_type_code
                 WHERE 1=1""")
            .filter(QueryFilter.required("timberMark", "AND u.timber_mark = :timberMark", Type.UPPER))
            .filter(QueryFilter.required("cutBlock", "AND u.cut_block = :cutBlock", Type.STRING))
            .filter(QueryFilter.required("arrivalDateFrom", "AND u.entry_timestamp >= :arrivalDateFrom", Type.DATE))
            .filter(QueryFilter.required("arrivalDateTo", "AND u.entry_timestamp < :arrivalDateTo", Type.DATE_TO))
            .sort("ldsNumber", "u.incoming_lds_number")
            .sort("arrivalDate", "u.entry_timestamp")
            .sort("scaleSite", "u.scale_site_id_nmbr")
            .sort("detailDocumentNumber", "u.detail_document_number")
            .orderBy("u.incoming_lds_number")
            .build()
    );
  }

  @Override
  public List<CommandDefinition> commands() {
    return List.of(
        // P977 Save — LoadDescSlipRegistryManager.createAndStoreLoadDescSlipRegistry.
        CommandDefinition.builder("scaleControl.lds.create")
            .legacy("P977")
            .capability(Capability.SCALE_CONTROL_ADMIN)
            .procedure("HBS_CREATE_LOAD_DESC_SLIP_REG")
            .sequence("LOAD_DESC_SLIP_REGISTRY_SEQ")   // i_ldsreg_id
            .requiredBody("timberMark", Type.UPPER)    // i_timber_mark
            .requiredBody("cutBlockId", Type.STRING)   // i_cut_block_id
            .requiredBody("startNumber", Type.NUMBER)  // i_start_number
            .requiredBody("endNumber", Type.NUMBER)    // i_end_number
            .auditUser()                               // i_entry_userid
            .now()                                     // i_entry_timestamp
            .auditUser()                               // i_update_userid
            .now()                                     // i_update_timestamp
            .build(),

        // P978 Save — updateLoadDescSlipRegistry. The proc rewrites the entry columns too, so the
        // original entry user/timestamp are echoed back from the loaded record.
        CommandDefinition.builder("scaleControl.lds.update")
            .legacy("P978")
            .capability(Capability.SCALE_CONTROL_ADMIN)
            .procedure("HBS_STORE_LOAD_DESC_SLIP_REG")
            .existingRow("SELECT entry_userid, entry_timestamp FROM load_desc_slip_registry WHERE ldsreg_id = :ldsregId")
            .requiredBody("ldsregId", Type.NUMBER)     // i_ldsreg_id
            .requiredBody("timberMark", Type.UPPER)    // i_timber_mark
            .requiredBody("cutBlockId", Type.STRING)   // i_cut_block_id
            .requiredBody("startNumber", Type.NUMBER)  // i_start_number
            .requiredBody("endNumber", Type.NUMBER)    // i_end_number
            .existing("entryUserid")                       // i_entry_userid
            .existing("entryTimestamp")                    // i_entry_timestamp
            .auditUser()                               // i_update_userid
            .now()                                     // i_update_timestamp
            .build(),

        // P979 Yes — removeLoadDescSlipRegistry.
        CommandDefinition.builder("scaleControl.lds.delete")
            .legacy("P979")
            .capability(Capability.SCALE_CONTROL_ADMIN)
            .procedure("HBS_REMOVE_LOAD_DESC_SLIP_REG")
            .requiredBody("ldsregId", Type.NUMBER)     // i_ldsreg_id
            .build()
    );
  }
}
