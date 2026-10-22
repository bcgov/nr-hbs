package ca.bc.gov.nrs.hbs.api.detailreturns.store;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailVersion;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.VersionState;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * The audit rows every legacy version write produced
 * ({@code DetailManagerBean.createAudit} → {@code AuditManager2Impl.createAudit}):
 * one HBS_AUDIT row (object type DSR, old/new id "DDN,version", the action
 * type) and one HBS_SCALE_RETURN_AUDIT row per edit — the version status and
 * active flag when they changed ({@code getStateChangeEdits}) plus each
 * audited field the user changed ({@code recordFieldEdit} in the version
 * setters, field names from {@code DacConstants.ScaleReturnAudit}).
 */
public final class DetailAuditTrail {

  /** {@code infrastructure.audit.Constants} action types. */
  public static final String INSERT = "INS";
  public static final String UPDATE = "UPD";
  public static final String DELETE = "DEL";
  public static final String REQUEST = "REQ";
  public static final String APPROVE = "APP";
  public static final String REJECT = "REJ";
  public static final String RELEASE = "REL";
  public static final String HOLD = "HLD";
  public static final String CANCEL = "CAN";

  static final String OBJECT_TYPE = "DSR";
  static final String AUDIT_SEQUENCE = "HBS_AUDIT_SEQ";

  private record Field(String name, String format) {}

  /** Audited columns → legacy field names (and value format: S string, D date, T date-time, B Y/N). */
  private static final Map<String, Field> COMMON = new LinkedHashMap<>();
  private static final Map<DocType, Map<String, Field>> BY_TYPE = new LinkedHashMap<>();

  static {
    // DetailScaleDocumentVersion setters
    COMMON.put("incoming_lds_number", new Field("Incoming_LDS", "S"));
    COMMON.put("scale_event_type_code", new Field("Event_Type", "S"));
    COMMON.put("primary_license_number", new Field("Scaler_License", "S"));
    COMMON.put("scale_site_id_nmbr", new Field("Scale_Site_Id", "S"));
    COMMON.put("scale_date", new Field("Scale_Date", "D"));
    COMMON.put("secondary_license_number", new Field("Second_Scaler", "S"));
    COMMON.put("signing_license_number", new Field("Signing_Scaler", "S"));
    COMMON.put("timber_brand", new Field("Timber_Brand", "S"));
    COMMON.put("timber_mark", new Field("Timber_Mark", "S"));
    COMMON.put("transport_identifier", new Field("Transport_ID", "S"));
    // DetailScaleBillableDocumentVersion setters
    COMMON.put("cut_block_id", new Field("Cutblock_ID", "S"));
    COMMON.put("field_scale_deck_id", new Field("Deck_Id", "S"));
    COMMON.put("field_scale_ind", new Field("Field_Scale", "B"));

    Map<String, Field> lt = new LinkedHashMap<>();
    lt.put("beachcomb_ind", new Field("Beachcomb", "B"));
    lt.put("check_replaces_original_ind", new Field("Replaces_Orig", "B"));
    lt.put("log_tally_comment", new Field("Comment", "S"));
    lt.put("load_arrival_number", new Field("Load_Arrival_Number", "S"));
    lt.put("original_ddn", new Field("Original_DDN", "S"));
    lt.put("original_ddv", new Field("Original_DDV", "S"));
    lt.put("orig_chk_scl_license_number", new Field("Original_Scaler", "S"));
    lt.put("orig_redtag_scale_site_id_nmbr", new Field("Redtag_Site", "S"));
    lt.put("volume_calc_method_type", new Field("Volume_Calc", "S"));
    lt.put("red_tag_weigh_slip", new Field("Weight_Slip_Number", "S"));
    BY_TYPE.put(DocType.LOG_TALLY, lt);

    Map<String, Field> ws = new LinkedHashMap<>();
    ws.put("weigh_slip_comment", new Field("Comment", "S"));
    ws.put("company_use_stratum", new Field("Company_Stratum", "S"));
    ws.put("destination", new Field("Destination", "S"));
    ws.put("gross_weight", new Field("Gross_Weight", "S"));
    ws.put("outgoing_lds_number", new Field("Outgoing_LDS", "S"));
    ws.put("population_number", new Field("Population_Number", "S"));
    ws.put("hbs_sample_type_code", new Field("Sample_Type", "S"));
    ws.put("sample_deduction_weight", new Field("Sample_Weight", "S"));
    ws.put("sampling_year", new Field("Sampling_Year", "S"));
    ws.put("stratum_number", new Field("Stratum_Number", "S"));
    ws.put("tare_weight", new Field("Tare_Weight", "S"));
    ws.put("weigh_slip_number", new Field("Weight_Slip_Number", "S"));
    ws.put("scale_date", new Field("Scale_Date", "T")); // WeighSlip.setScaleDate: DateUtil.getDateTimeString
    BY_TYPE.put(DocType.WEIGH_SLIP, ws);

    Map<String, Field> st = new LinkedHashMap<>();
    st.put("check_replaces_original_ind", new Field("Replaces_Orig", "B"));
    st.put("sample_log_tally_comment", new Field("Comment", "S"));
    st.put("field_scale_deck_id", new Field("field_deck", "S"));
    st.put("load_arrival_number", new Field("Load_Arrival_Number", "S"));
    st.put("log_count", new Field("Log_Count", "S"));
    st.put("original_ddn", new Field("Original_DDN", "S"));
    st.put("original_ddv", new Field("Original_DDV", "S"));
    st.put("orig_chk_scl_return_number", new Field("Orig_Return_No", "S"));
    st.put("orig_chk_scl_license_number", new Field("Original_Scaler", "S"));
    st.put("population_number", new Field("Population_Number", "S"));
    st.put("orig_redtag_scale_site_id_nmbr", new Field("Redtag_Site", "S"));
    st.put("return_number", new Field("Return_No", "S"));
    st.put("sample_weight", new Field("Sample_Weight", "S"));
    st.put("sampling_year", new Field("Sampling_Year", "S"));
    st.put("stratum_number", new Field("Stratum_Number", "S"));
    st.put("volume_calc_method_type", new Field("Volume_Calc", "S"));
    st.put("sampled_weigh_slip", new Field("Weight_Slip_Number", "S"));
    BY_TYPE.put(DocType.SAMPLE_LOG_TALLY, st);
  }

  private final TableApi api;

  DetailAuditTrail(TableApi api) {
    this.api = api;
  }

  /**
   * Writes the audit of one version write.
   *
   * @param stateOnly true for an insert (legacy passed only {@code getStateChangeEdits} for INS)
   */
  void record(DocType type, DetailVersion version, String ddn, String user, Timestamp now, String actionType,
              boolean stateOnly) {
    List<String[]> edits = new ArrayList<>();
    VersionState s = version.state();
    if (!s.originalValue().equals(s.value())) {
      edits.add(new String[] {"Version_Status", s.originalValue(), s.value()});
    }
    if (s.originalActive() != s.active()) {
      edits.add(new String[] {"Version_Active", s.originalActive() ? "Y" : "N", s.active() ? "Y" : "N"});
    }
    if (!stateOnly && version.loaded() != null) {
      edits.addAll(fieldEdits(type, version.loaded(), version.row()));
    }
    long auditId = api.nextId(AUDIT_SEQUENCE);
    String id = ddn + "," + version.number();
    api.call("HBS_CREATE_AUDIT", ProcArgs.of()
        .put("i_audit_id", auditId)
        .put("i_user_id", user)
        .put("i_user_id_record_actions_for", user)
        .put("i_audit_date", now)
        .put("i_hbs_object_type_code", OBJECT_TYPE)
        .put("i_object_old_id", id)
        .put("i_object_new_id", id)
        .put("i_action_type_code", actionType));
    for (String[] e : edits) {
      api.call("HBS_CREATE_SCL_RTN_AUDIT", ProcArgs.of()
          .put("i_audit_id", auditId)
          .put("i_field_name", e[0])
          .put("i_action_type_code", UPDATE)
          .put("i_hbs_object_type_code", OBJECT_TYPE)
          .put("i_old_value", truncate(e[1]))
          .put("i_new_value", truncate(e[2])));
    }
  }

  static List<String[]> fieldEdits(DocType type, Row before, Row after) {
    Map<String, Field> fields = new LinkedHashMap<>(COMMON);
    fields.putAll(BY_TYPE.getOrDefault(type, Map.of()));
    List<String[]> edits = new ArrayList<>();
    fields.forEach((column, field) -> {
      if (!after.has(column) && !before.has(column)) return;
      Object a = before.get(column);
      Object b = after.get(column);
      if (!Row.same(a, b)) {
        edits.add(new String[] {field.name(), format(a, field.format()), format(b, field.format())});
      }
    });
    return edits;
  }

  private static String format(Object v, String format) {
    if (v == null) return null;
    if (v instanceof java.util.Date d && ("D".equals(format) || "T".equals(format))) {
      // DateUtil.getDateString ("yyyy.MM.dd") / getDateTimeString ("yyyy.MM.dd HH.mm.ss")
      SimpleDateFormat f = new SimpleDateFormat("D".equals(format) ? "yyyy.MM.dd" : "yyyy.MM.dd HH.mm.ss",
          Locale.CANADA);
      f.setTimeZone(TimeZone.getTimeZone(ca.bc.gov.nrs.hbs.api.workflow.Audit.ZONE));
      return f.format(d);
    }
    if ("B".equals(format)) {
      String s = v.toString();
      return "Y".equalsIgnoreCase(s) ? "true" : "N".equalsIgnoreCase(s) ? "false" : s; // StringUtil.toStringOrNull(Boolean)
    }
    return v.toString();
  }

  private static String truncate(String s) {
    return s == null || s.length() <= 240 ? s : s.substring(0, 240);
  }
}
