package ca.bc.gov.nrs.hbs.api.detailreturns;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.LogLine;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.workflow.ProcSignatures;
import ca.bc.gov.nrs.hbs.api.workflow.ProcSignatures.Param;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The data-entry payload of the SPA applied to a document — what the legacy
 * Struts forms did in {@code copyFormToDocument} / the {@code P5xx/P6xx/P7xx}
 * actions and the log-detail parsers (P558/P658/P758) before the workflow ran.
 *
 * <p>Contract (body of {@code detailReturns.add/save/release/reEdit}):
 * <pre>
 * { "fields": { camelCase of a version-table column: value, … },
 *   "logs":   [ { "logdtlId": n|null,
 *                 "fields":  { camelCase of a line column: value, … },
 *                 "defects": [ { "logDefectId": n|null, "fields": { defectNumber, defectType, volume, descriptor } } ] } ] }
 * </pre>
 * Only data columns are accepted (never state, version, signature, input_* or
 * audit columns); codes are upper-cased like the legacy setters; dates are ISO
 * ({@code yyyy-MM-dd}, or {@code yyyy-MM-ddTHH:mm} for a weigh slip scale date).
 * Text is taken as posted, like the legacy Struts forms and parsers: only ""
 * becomes null, and whitespace is kept (a single space is a real code — the
 * log product and ungraded grade — and {@code StringUtil.empty} does not count
 * it as empty) except in the fields a legacy form setter trimmed
 * ({@link #trimmed}).
 * When {@code logs} is present it replaces the version's lines (lines not sent
 * are deleted, as the legacy "delete" check box did). Each log/sample line's
 * NET_VOLUME and DEDUCTION_COUNT are recomputed ({@code P552Form.validate},
 * {@code LogDefectHelper.calculateVolume}).
 */
public final class DetailPayload {

  /** Columns the SPA may never set. */
  private static final Set<String> SYSTEM = Set.of("detail_document_number", "version", "batdoc_id",
      "scl_rtn_version_state_code", "active_version_ind", "original_version_ind", "valid_signature_ind",
      "dtl_doc_seq", "net_volume", "hash_total", "decrypted_signature", "encrypted_signature", "hbs_encr_key_id",
      "hbs_rsa_padding_id", "hbs_crypt_method_id", "signing_date_time", "entry_userid", "entry_timestamp",
      "update_userid", "update_timestamp", "primary_quant_lic_type_cd", "secondary_quant_lic_type_cd",
      "signing_quant_lic_type_cd", "orig_chk_scl_quant_lic_type_cd", "quant_lic_type_cd");

  private static final Set<String> LINE_SYSTEM = Set.of("logdtl_id", "detail_document_number", "version",
      "dtlseg_id", "deduction_count", "entry_userid", "entry_timestamp", "update_userid", "update_timestamp");

  private static final Set<String> DATES = Set.of("scale_date", "arrival_date", "departure_date");

  /** {@code LogTallyCommon.Product.LOG} / {@code GRADE_VALUE}: the parsers' one-space defaults. */
  static final String SPACE = " ";

  /**
   * Text fields a legacy form setter trimmed ({@code s.toUpperCase().trim()}):
   * P552Form original red-tag site and original DDN; P600Form cut block, mark,
   * scale site and PSY; P650Form original red-tag site, mark and scale site.
   * The field scale deck is trimmed by the entity setter of every type
   * ({@code DetailScaleBillableDocumentVersion} / {@code SclSiteArrivalLedger}),
   * and P700Form (ledgers, BT 14035) trimmed every field.
   */
  private static final Map<DocType, Set<String>> TRIMMED = Map.of(
      DocType.LOG_TALLY, Set.of("original_ddn", "orig_redtag_scale_site_id_nmbr"),
      DocType.WEIGH_SLIP, Set.of("cut_block_id", "timber_mark", "scale_site_id_nmbr", "population_number",
          "stratum_number", "company_use_stratum"),
      DocType.SAMPLE_LOG_TALLY, Set.of("orig_redtag_scale_site_id_nmbr", "timber_mark", "scale_site_id_nmbr"));

  /** Upper-cased like the legacy form setters (sites, licences, marks, brands, codes, DDNs). */
  private static final Set<String> UPPER = Set.of("scale_site_id_nmbr", "primary_license_number",
      "secondary_license_number", "signing_license_number", "orig_chk_scl_license_number", "license_number",
      "timber_mark", "timber_brand", "original_ddn", "orig_redtag_scale_site_id_nmbr", "orig_scale_site",
      "destination", "scale_event_type_code", "field_scale_deck_id", "population_number", "stratum_number",
      "company_use_stratum", "hbs_sample_type_code", "scale_species_code", "scale_grade_code",
      "scale_product_code", "input_bcomb_timber_mark", "defect_type", "volume_calc_method_type",
      "hbs_sfp_scale_type_code", "cut_block_id");

  private DetailPayload() {
  }

  /** The version-table columns (create-proc parameters, aliases resolved). */
  public static Set<String> versionColumns(DocType type) {
    Set<String> cols = new LinkedHashSet<>();
    Map<String, String> aliases = type.createAliases();
    for (Param p : ProcSignatures.load().get(type.createProc()).params()) {
      cols.add(aliases.getOrDefault(p.name(), p.name().substring(2)));
    }
    return cols;
  }

  /** The columns of the type's log line table. */
  public static Set<String> lineColumns(DocType type) {
    Set<String> cols = new LinkedHashSet<>();
    for (Param p : ProcSignatures.load().get(type.details().create()).params()) {
      cols.add(p.name().substring(2));
    }
    return cols;
  }

  public static boolean editable(String column) {
    return !SYSTEM.contains(column) && !column.startsWith("input_");
  }

  /** A blank version row of the type (every column present, null). */
  public static Row blankVersion(DocType type) {
    Row r = new Row();
    versionColumns(type).forEach(c -> r.set(c, null));
    return r;
  }

  /**
   * Applies {@code fields} to the version row; unknown or protected keys are a 400
   * (they would otherwise be silently ignored).
   */
  public static void applyFields(DetailDocument doc, Map<String, ?> fields) {
    if (fields == null) return;
    Set<String> columns = versionColumns(doc.type());
    List<WorkflowValidationException.Problem> problems = new ArrayList<>();
    Row v = doc.version().row();
    fields.forEach((key, value) -> {
      String col = snake(key);
      if (!columns.contains(col) || !editable(col)) {
        problems.add(new WorkflowValidationException.Problem(key, key + " cannot be set"));
        return;
      }
      try {
        v.set(col, convert(doc.type(), col, value));
      } catch (RuntimeException ex) {
        problems.add(new WorkflowValidationException.Problem(key, key + " is not valid: " + value));
      }
    });
    if (!problems.isEmpty()) {
      throw new WorkflowValidationException(problems);
    }
    licenceTypeCodes(doc);
  }

  /**
   * {@code P600Action.addDetailDocument} / {@code P650Action}: a weigh slip or
   * sample tally scaler licence keyed online is a scale licence (type S).
   */
  static void licenceTypeCodes(DetailDocument doc) {
    if (doc.type() != DocType.WEIGH_SLIP && doc.type() != DocType.SAMPLE_LOG_TALLY) return;
    Row v = doc.version().row();
    for (String role : List.of("primary", "secondary", "signing")) {
      if (!v.empty(role + "_license_number")) {
        v.set(role + "_quant_lic_type_cd", "S");
      }
    }
  }

  /**
   * {@code DetailScaleDocumentHelper.getCheckboxValue}: an unticked check box
   * the event type shows is stored as N (an unshown one stays null).
   */
  public static void defaultCheckboxes(DetailDocument doc) {
    Row v = doc.version().row();
    String event = v.str("scale_event_type_code");
    switch (doc.type()) {
      case LOG_TALLY -> {
        checkbox(v, "LOG_TALLY", event, "BEACHCOMB", "beachcomb_ind");
        checkbox(v, "LOG_TALLY", event, "FIELD_SCALE", "field_scale_ind");
        checkbox(v, "LOG_TALLY", event, "REPLACEMENT", "check_replaces_original_ind");
      }
      case WEIGH_SLIP -> checkbox(v, "WEIGH_SLIP", event, "FIELD_SCALE", "field_scale_ind");
      case SAMPLE_LOG_TALLY -> checkbox(v, "SAMPLE_LOG_TALLY", event, "REPLACEMENT", "check_replaces_original_ind");
      default -> { }
    }
  }

  private static void checkbox(Row v, String matrix, String event, String field, String column) {
    if (event != null && FieldAvailability.isAvailable(matrix, event, field) && v.get(column) == null) {
      v.setYes(column, false);
    }
  }

  /** Replaces the version's log lines with the payload's (see the class comment). */
  @SuppressWarnings("unchecked")
  public static void applyLogs(DetailDocument doc, List<Map<String, Object>> logs) {
    if (logs == null || !doc.isTally()) return;
    DocType type = doc.type();
    Set<String> columns = lineColumns(type);
    Map<Long, LogLine> existing = new HashMap<>();
    doc.version().logs().forEach(l -> { if (l.id() != null) existing.put(l.id(), l); });
    List<LogLine> result = new ArrayList<>();
    List<WorkflowValidationException.Problem> problems = new ArrayList<>();
    for (int i = 0; i < logs.size(); i++) {
      Map<String, Object> in = logs.get(i);
      Long id = toLong(in.get("logdtlId"));
      LogLine line = id != null && existing.containsKey(id) ? existing.get(id) : new LogLine(new Row());
      if (line.id() == null) {
        columns.forEach(c -> line.row().set(c, null));
      }
      Map<String, Object> fields = (Map<String, Object>) in.getOrDefault("fields", Map.of());
      for (Map.Entry<String, Object> e : fields.entrySet()) {
        String col = snake(e.getKey());
        boolean computed = col.equals("net_volume") && type != DocType.SFP_TALLY;
        if (!columns.contains(col) || LINE_SYSTEM.contains(col) || computed) {
          problems.add(new WorkflowValidationException.Problem("logs[" + i + "]." + e.getKey(),
              e.getKey() + " cannot be set"));
          continue;
        }
        line.row().set(col, convert(type, col, e.getValue()));
      }
      if (type.details().defects() != null && in.get("defects") instanceof List<?> defects) {
        applyDefects(line, (List<Map<String, Object>>) defects);
      }
      lineCodeDefaults(type, columns, line.row());
      if (line.row().get("nmv_ind") == null && columns.contains("nmv_ind")) {
        line.row().set("nmv_ind", "N");
      }
      if (type != DocType.SFP_TALLY) {
        line.row().set("deduction_count", line.defects().size());
        line.row().set("net_volume", LogVolume.decimal(LogVolume.netVolume(line)));
      }
      result.add(line);
    }
    if (!problems.isEmpty()) {
      throw new WorkflowValidationException(problems);
    }
    doc.version().logs().clear();
    doc.version().logs().addAll(result);
  }

  /**
   * The legacy line parsers' code defaults: {@code LogDetailParser} /
   * {@code SampleDetailParser} always set the product to LOG (one space);
   * {@code SFPDetailParser.getScaleProductCode} and every parser's
   * {@code getScaleGradeCode} turned an empty value ({@code StringUtil.empty},
   * no trim) into one space.
   */
  static void lineCodeDefaults(DocType type, Set<String> columns, Row line) {
    if (columns.contains("scale_product_code")) {
      if (type == DocType.LOG_TALLY || type == DocType.SAMPLE_LOG_TALLY) {
        line.set("scale_product_code", SPACE);
      } else if (line.str("scale_product_code") == null) {
        line.set("scale_product_code", SPACE);
      }
    }
    if (columns.contains("scale_grade_code") && line.str("scale_grade_code") == null) {
      line.set("scale_grade_code", SPACE);
    }
  }

  /** {@code LogDefectHelper.calculateVolume}: an all-blank defect row is dropped. */
  private static void applyDefects(LogLine line, List<Map<String, Object>> defects) {
    Map<Long, Row> existing = new HashMap<>();
    line.defects().forEach(d -> { if (d.lng("log_defect_id") != null) existing.put(d.lng("log_defect_id"), d); });
    List<Row> out = new ArrayList<>();
    for (Map<String, Object> in : defects) {
      @SuppressWarnings("unchecked")
      Map<String, Object> f = (Map<String, Object>) in.getOrDefault("fields", Map.of());
      Long id = toLong(in.get("logDefectId"));
      Row d = id != null && existing.containsKey(id) ? existing.get(id) : new Row();
      d.set("defect_number", f.get("defectNumber"));
      d.set("defect_type", upper(f.get("defectType")));
      d.set("volume", f.get("volume") == null || f.get("volume").toString().isBlank() ? null
          : new BigDecimal(f.get("volume").toString().trim()));
      d.set("descriptor", f.get("descriptor") == null ? null : f.get("descriptor").toString().trim()); // P559Action
      boolean blank = d.get("defect_number") == null && d.get("volume") == null && d.empty("defect_type")
          && d.empty("descriptor");
      if (!blank) out.add(d);
    }
    line.defects().clear();
    line.defects().addAll(out);
  }

  /**
   * A posted value as the legacy form/parser handed it on: "" is null
   * ({@code AbstractDetailParser.getValue}; Oracle stores "" as NULL anyway),
   * text is upper-cased where the setters did and trimmed only where they did
   * ({@link #TRIMMED}); whitespace-only text is kept. Dates and numbers are
   * parsed from the trimmed text (blank is null).
   */
  static Object convert(DocType type, String col, Object value) {
    if (value == null) return null;
    if (value instanceof String s && s.isEmpty()) return null;
    if (value instanceof String s && s.isBlank() && (DATES.contains(col) || !textColumn(type, col))) return null;
    if (DATES.contains(col)) {
      String s = value.toString().trim();
      try {
        if (s.length() > 10) {
          return Timestamp.valueOf(LocalDateTime.parse(s.length() == 16 ? s + ":00" : s.replace(' ', 'T')));
        }
        return Timestamp.valueOf(LocalDate.parse(s).atStartOfDay());
      } catch (DateTimeParseException ex) {
        throw new IllegalArgumentException("bad date " + s);
      }
    }
    if (value instanceof Boolean b) return b ? "Y" : "N";
    if (!(value instanceof String s)) return value;
    if (trimmed(type, col)) {
      s = s.trim();
      if (s.isEmpty()) return null;
    }
    return UPPER.contains(col) ? s.toUpperCase(Locale.ROOT) : s;
  }

  /** True for the fields the legacy form setters trimmed ({@link #TRIMMED}). */
  static boolean trimmed(DocType type, String col) {
    return col.equals("field_scale_deck_id") || type == DocType.ARRIVAL_LEDGER || type == DocType.DEPARTURE_LEDGER
        || TRIMMED.getOrDefault(type, Set.of()).contains(col);
  }

  /** A VARCHAR2/CHAR column of the version or line table (anything else is parsed). */
  private static boolean textColumn(DocType type, String col) {
    return TEXT_COLUMNS.computeIfAbsent(type, DetailPayload::textColumns).contains(col);
  }

  private static final Map<DocType, Set<String>> TEXT_COLUMNS = new java.util.concurrent.ConcurrentHashMap<>();

  private static Set<String> textColumns(DocType type) {
    Set<String> out = new java.util.HashSet<>();
    Map<String, String> aliases = type.createAliases();
    for (Param p : ProcSignatures.load().get(type.createProc()).params()) {
      if (p.type() != null && p.type().contains("CHAR")) out.add(aliases.getOrDefault(p.name(), p.name().substring(2)));
    }
    if (type.details() != null && type.details().create() != null) {
      for (Param p : ProcSignatures.load().get(type.details().create()).params()) {
        if (p.type() != null && p.type().contains("CHAR")) out.add(p.name().substring(2));
      }
    }
    return out;
  }

  private static String upper(Object v) {
    return v == null ? null : v.toString().trim().toUpperCase(Locale.ROOT);
  }

  private static Long toLong(Object v) {
    if (v == null || v.toString().isBlank()) return null;
    return new BigDecimal(v.toString()).longValue();
  }

  /** {@code scaleSiteIdNmbr} → {@code scale_site_id_nmbr}. */
  public static String snake(String camel) {
    StringBuilder sb = new StringBuilder();
    for (char c : camel.toCharArray()) {
      if (Character.isUpperCase(c)) sb.append('_').append(Character.toLowerCase(c));
      else sb.append(c);
    }
    return sb.toString();
  }

  /** {@code scale_site_id_nmbr} → {@code scaleSiteIdNmbr}. */
  public static String camel(String snake) {
    StringBuilder sb = new StringBuilder();
    boolean up = false;
    for (char c : snake.toLowerCase(Locale.ROOT).toCharArray()) {
      if (c == '_') {
        up = true;
      } else {
        sb.append(up ? Character.toUpperCase(c) : c);
        up = false;
      }
    }
    return sb.toString();
  }

  /** A row as camelCase JSON (dates ISO). */
  public static Map<String, Object> json(Row row) {
    Map<String, Object> out = new LinkedHashMap<>();
    row.asMap().forEach((k, v) -> {
      if (v instanceof byte[]) return;
      if (v instanceof java.util.Date d) v = new Timestamp(d.getTime()).toLocalDateTime().toString();
      out.put(camel(k), v);
    });
    return out;
  }
}
