package ca.bc.gov.nrs.hbs.api.query;

import ca.bc.gov.nrs.hbs.api.exception.PreConditionFailedException;
import ca.bc.gov.nrs.hbs.api.query.HarvestHistoryXml.Header;
import ca.bc.gov.nrs.hbs.api.query.HarvestHistoryXml.Kind;
import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.xml.sax.SAXException;

/**
 * "Download XML" for Mark Monthly Billing / Scaling History — the legacy "Send XML"
 * (MarkMonthlyBillingAction.performSendXML), downloaded instead of e-mailed.
 * Criteria are the screen's; the header repeats them in legacy display text.
 */
@Slf4j
@Service
public class HarvestHistoryXmlService {

  /** Legacy Constants.HARVEST_REPORTS_MAX_RECORDS: 5,000 or more rows is refused. */
  static final int MAX_ROWS = 5000;
  static final String BILLING_QUERY = "harvestHistory.billing";
  static final String SCALING_QUERY = "harvestHistory.scaling";
  private static final ZoneId PACIFIC = ZoneId.of("America/Vancouver");

  private final QueryService queryService;
  private final NamedParameterJdbcTemplate jdbc;

  public HarvestHistoryXmlService(QueryService queryService, NamedParameterJdbcTemplate jdbc) {
    this.queryService = queryService;
    this.jdbc = jdbc;
  }

  public static boolean supports(String queryId) {
    return BILLING_QUERY.equals(queryId) || SCALING_QUERY.equals(queryId);
  }

  public String export(String queryId, Map<String, String> params) {
    if (!supports(queryId)) {
      throw new IllegalArgumentException("XML download isn't available for " + queryId);
    }
    Kind kind = BILLING_QUERY.equals(queryId) ? Kind.BILLING : Kind.SCALING;
    long total = queryService.count(queryId, params);
    if (total >= MAX_ROWS) {
      throw new IllegalArgumentException("The selection criteria provided will select "
          + NumberFormat.getIntegerInstance(Locale.CANADA).format(total) + " records. The maximum that can be "
          + "downloaded as XML is 5,000. Please refine your search criteria.");
    }
    List<Map<String, Object>> rows = queryService.list(queryId, params);
    String xml = HarvestHistoryXml.write(kind, header(kind, params), rows);
    try {
      HarvestHistoryXml.validate(kind, xml);
    } catch (SAXException ex) {
      log.warn("History XML for [{}] failed schema validation: {}", queryId, ex.getMessage());
      throw new PreConditionFailedException("Some of the selected records are missing information the XML "
          + "format requires, so the file couldn't be created. Please advise the HBS Help Desk. ("
          + ex.getMessage() + ")");
    }
    return xml;
  }

  Header header(Kind kind, Map<String, String> p) {
    boolean billing = kind == Kind.BILLING;
    boolean ministry = RequestUtil.isMinistryUser();
    String client = ministry ? null : blankToNull(RequestUtil.getCurrentClientNumber());
    return new Header(
        System.currentTimeMillis() / 1000,
        LocalDateTime.now(PACIFIC),
        blankToNull(email()),
        ministry ? "Ministry" : "Industry",
        client,
        month(p.get(billing ? "billingFrom" : "scalingFrom")),
        month(p.get(billing ? "billingTo" : "scalingTo")),
        regionText(p.get("regionDistrict"), "All"),
        blankToNull(p.get("clientNumber")),
        blankToNull(p.get("clientLocation")),
        fileTypeText(p.get("fileType")),
        mgmtUnitTypeText(p.get("mgmtUnitType")),
        upper(p.get("mgmtUnitNo")),
        fitOrNull(upper(p.get("forestFileId")), 10),
        fitOrNull(list(p.get("timberMarks")), 90),
        billing ? null : regionText(p.get("regionScaled"), null),
        billing ? null : upper(p.get("scaleSite")),
        cut(describe(p.get("species"), "scale_species_code", "scale_species_code", Map.of(
            "CNFRS", "Coniferous Species", "DCDS", "Deciduous Species")), 20),
        cut(describe(p.get("product"), "scale_product_code", "scale_product_code", Map.of(
            "NOT_LOG", "Special Forest Products", "NOT_XM", "All except Christmas Trees")), 30),
        cut(describe(p.get("grade"), "scale_grade_code", "scale_grade_code", Map.of(
            "LUM_REJ", "Lumber Rejects", "UTL_BTR", "Utility and Better",
            "FIRMWOOD_REJ", "Exclude Firmwood Rejects")), 50),
        StringUtils.hasText(p.get("billingType")) ? p.get("billingType").trim() : "Normal,Cruise,Waste,Beachcomb");
  }

  private static String email() {
    try {
      return RequestUtil.getCurrentJwt().getClaimAsString("email");
    } catch (RuntimeException ex) {
      return null;
    }
  }

  private static YearMonth month(String isoDate) {
    if (!StringUtils.hasText(isoDate)) {
      throw new IllegalArgumentException("The month interval is required");
    }
    return YearMonth.from(LocalDate.parse(isoDate.trim().substring(0, 10)));
  }

  /** Legacy region wording: 0 / 10001–10003 are the roll-up areas, else the org unit's name. */
  private String regionText(String orgUnitNo, String blank) {
    if (!StringUtils.hasText(orgUnitNo)) return blank;
    String n = orgUnitNo.trim();
    String area = switch (n) {
      case "0" -> "Interior Forest Regions";
      case "10001" -> "Coast Area";
      case "10002" -> "North Area";
      case "10003" -> "South Area";
      default -> null;
    };
    if (area != null) return area;
    List<String> names = jdbc.queryForList("SELECT org_unit_name FROM org_unit WHERE org_unit_no = :n",
        Map.of("n", Long.parseLong(n)), String.class);
    return cut(names.isEmpty() ? n : names.get(0), 50);
  }

  private String fileTypeText(String codes) {
    List<String> list = split(codes);
    if (list.isEmpty()) return "All Land";
    List<String> names = jdbc.queryForList("""
        SELECT expanded_result FROM code_list_table
         WHERE column_name = 'FILE_TYPE_CODE' AND code_argument IN (:codes)
         ORDER BY expanded_result""", Map.of("codes", list), String.class);
    return cut(names.isEmpty() ? String.join(", ", list) : String.join(", ", names), 40);
  }

  private static String mgmtUnitTypeText(String code) {
    if (!StringUtils.hasText(code)) return null;
    return switch (code.trim().toUpperCase()) {
      case "F" -> "Woodlot";
      case "T" -> "Tree Farm Licence";
      case "U" -> "Timber Supply Area";
      case "V" -> "Timber Supply Block";
      case "U,V" -> "Timber Supply Area or Block";
      default -> "Other";
    };
  }

  /** "All" when blank, a legacy group label, else the code table's description. */
  private String describe(String code, String table, String column, Map<String, String> groups) {
    if (!StringUtils.hasText(code) || "ALL".equalsIgnoreCase(code.trim())) return "All";
    String c = code.trim().toUpperCase();
    if (groups.containsKey(c)) return groups.get(c);
    List<String> names = jdbc.queryForList(
        "SELECT description FROM " + table + " WHERE " + column + " = :c", Map.of("c", c), String.class);
    return names.isEmpty() || !StringUtils.hasText(names.get(0)) ? c : names.get(0);
  }

  private static List<String> split(String csv) {
    if (!StringUtils.hasText(csv)) return List.of();
    return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  private static String list(String csv) {
    String joined = split(csv).stream().map(String::toUpperCase).collect(Collectors.joining(", "));
    return joined.isEmpty() ? null : joined;
  }

  private static String upper(String s) {
    return StringUtils.hasText(s) ? s.trim().toUpperCase() : null;
  }

  private static String blankToNull(String s) {
    return StringUtils.hasText(s) ? s.trim() : null;
  }

  private static String cut(String s, int max) {
    return s == null || s.length() <= max ? s : s.substring(0, max);
  }

  /** Optional header values the schema caps (one forest file id, ≤ 10 marks): left out if too long. */
  private static String fitOrNull(String s, int max) {
    return s == null || s.length() > max ? null : s;
  }
}
