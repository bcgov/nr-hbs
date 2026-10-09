package ca.bc.gov.nrs.hbs.api.service.v1.report;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Per-report prompt rules for the legacy report procs that build their SQL by
 * pasting prompt values in <b>unquoted</b> — {@code ' WHERE x = ' || prompt},
 * {@code ' IN (' || prompt || ')'}, {@code ' ORDER BY ' || prompt}.
 *
 * <p>{@code HbsReportService.SAFE_PROMPT} already bars quotes, semicolons and
 * comment markers, which is enough where a proc quotes the value. Unquoted, a
 * value such as {@code 1 OR 1 IN (1)} would still rewrite the WHERE clause, so
 * each such prompt is held to the characters its column can take. And a
 * blank required prompt leaves {@code x = } dangling (ORA-00936), so the
 * prompts a proc can't run without are required up front.
 *
 * <p>Derived from the nr-mof-db sources of every catalogued report proc (every
 * {@code || PROMPT} concatenation not wrapped in {@code '''} / {@code qt});
 * see docs/reports.md "Prompt rules".
 */
final class ReportPromptRules {

  /** What an unquoted prompt may contain. Blank always passes this check. */
  enum Kind {
    /** Numbers: org units, document / version numbers, ids, row ranges, years. */
    DIGITS("\\d{1,20}", "digits only"),
    /** A comma-separated list of numbers, pasted into IN (…). */
    DIGIT_LIST("[0-9, ]+", "numbers separated by commas"),
    /** A comma-separated code list (file types), pasted into IN (…). */
    CODE_LIST("[A-Za-z0-9,]+", "codes separated by commas"),
    /** An ORDER BY column list. */
    ORDER_BY("[A-Za-z0-9_., ]{1,100}", "column names separated by commas");

    final Pattern pattern;
    final String description;

    Kind(String regex, String description) {
      this.pattern = Pattern.compile(regex);
      this.description = description;
    }
  }

  record Rules(Map<String, Kind> kinds, Set<String> required, Map<String, String> requiredWith) {
    static final Rules NONE = new Rules(Map.of(), Set.of(), Map.of());
  }

  private static final Map<String, Rules> BY_REPORT = new HashMap<>();

  /**
   * Code lists a proc pastes as {@code IN (' || p1 || p2 || p3 || ')'}: legacy
   * screens sent them pre-quoted ({@code 'A01','A02'}), which SAFE_PROMPT can't
   * allow, so the codes are validated bare and quoted here.
   */
  private static final Map<String, List<String>> QUOTED_LISTS = new HashMap<>();

  private static void rule(List<String> reports, Kind kind, String... prompts) {
    for (String r : reports) {
      Rules cur = BY_REPORT.getOrDefault(r, Rules.NONE);
      Map<String, Kind> kinds = new HashMap<>(cur.kinds());
      for (String p : prompts) kinds.put(p, kind);
      BY_REPORT.put(r, new Rules(Map.copyOf(kinds), cur.required(), cur.requiredWith()));
    }
  }

  private static void required(String report, String... prompts) {
    Rules cur = BY_REPORT.getOrDefault(report, Rules.NONE);
    Set<String> req = new java.util.HashSet<>(cur.required());
    req.addAll(List.of(prompts));
    BY_REPORT.put(report, new Rules(cur.kinds(), Set.copyOf(req), cur.requiredWith()));
  }

  /** {@code then} is required whenever {@code when} is filled (row ranges). */
  private static void requiredWith(List<String> reports, String when, String then) {
    for (String r : reports) {
      Rules cur = BY_REPORT.getOrDefault(r, Rules.NONE);
      Map<String, String> with = new HashMap<>(cur.requiredWith());
      with.put(when, then);
      BY_REPORT.put(r, new Rules(cur.kinds(), cur.required(), Map.copyOf(with)));
    }
  }

  static {
    List<String> summaryLists = List.of("HBS2R051", "HBS2R101", "HBS2R151", "HBS2R551", "HBS2R601", "HBS2R651");
    rule(summaryLists, Kind.DIGITS, "PSR_DISTRICTSCALED", "PSR_DISTRICTHARVESTED");
    rule(List.of("HBS2R551", "HBS2R651", "HBS2R701"), Kind.DIGITS, "PSR_NUMBERRANGEFROM", "PSR_NUMBERRANGETO");
    rule(List.of("HBS2R701"), Kind.DIGITS, "PSR_DISTRICTSCALED");

    // Single summary returns (piece / weight / sample): document + version.
    rule(List.of("HBS2R052"), Kind.DIGITS, "PSRETURN_DOCUMENTNUMBER", "PSRETURN_VERSIONNUMBER");
    rule(List.of("HBS2R102"), Kind.DIGITS, "WSRETURN_DOCUMENTNUMBER", "WSRETURN_VERSIONNUMBER");
    rule(List.of("HBS2R152"), Kind.DIGITS, "SSRETURN_DOCUMENTNUMBER", "SSRETURN_VERSIONNUMBER");
    required("HBS2R052", "PSRETURN_DOCUMENTNUMBER", "PSRETURN_VERSIONNUMBER");
    required("HBS2R102", "WSRETURN_DOCUMENTNUMBER", "WSRETURN_VERSIONNUMBER");
    required("HBS2R152", "SSRETURN_DOCUMENTNUMBER", "SSRETURN_VERSIONNUMBER");

    // Detail returns (log tally / weigh slip / sample tally), single + "docd" list variants.
    List<String> ps = List.of("HBS2R552", "HBS2R552DOCD");
    List<String> ws = List.of("HBS2R602", "HBS2R602DOCD");
    List<String> ss = List.of("HBS2R652", "HBS2R652DOCD");
    rule(ps, Kind.DIGITS, "PSRETURN_VERSIONNUMBER", "PSDISTRICTSCALED", "PSDISTRICTHARVESTED", "PSMINREC", "PSMAXREC");
    rule(ws, Kind.DIGITS, "WSRETURN_VERSIONNUMBER", "WSRETURN_DISTRICTSCALED", "WSRETURN_DISTRICTHARVESTED",
        "WSRETURN_MINREC", "WSRETURN_MAXREC");
    rule(ss, Kind.DIGITS, "SSRETURN_VERSIONNUMBER", "SSRETURN_DISTRICTSCALED", "SSRETURN_DISTRICTHARVESTED",
        "SSRETURN_MINREC", "SSRETURN_MAXREC");
    requiredWith(ps, "PSMINREC", "PSMAXREC");
    requiredWith(ws, "WSRETURN_MINREC", "WSRETURN_MAXREC");
    requiredWith(ss, "SSRETURN_MINREC", "SSRETURN_MAXREC");
    rule(List.of("HBS2R702"), Kind.DIGITS, "LEDGERDTL_VERSIONNUMBER");
    required("HBS2R702", "LEDGERDTL_VERSIONNUMBER");

    // Transmissions / batches.
    rule(List.of("HBS2R027"), Kind.DIGITS, "RB_REGDIST", "RB_TRANSMISSION_ID");
    rule(List.of("HBS2R032"), Kind.DIGITS, "RB_TRANSMISSION_ID", "RB_BATCH_ID");
    rule(List.of("HBS2R033"), Kind.DIGITS, "RB_TRANSMISSION_ID");

    // Scale site summaries (P401–P403).
    List<String> siteSummaries = List.of(
        "HBS2R401GROUPDISTSCALE", "HBS2R401GROUPSCALETM",
        "HBS2R402GROUPDISTSCALE", "HBS2R402GROUPSCALEPOP", "HBS2R402GROUPSCALETM",
        "HBS2R403GROUPDISTSCALE", "HBS2R403GROUPSCALEPOP", "HBS2R403GROUPSCALETM");
    rule(siteSummaries, Kind.DIGITS, "RB_DISTRICT_SCALED", "RB_DISTRICT_HARVESTED");

    // Invoices: lists of invoice numbers pasted into IN (…) — at least one is needed.
    String[] wsLists = new String[10];
    String[] invLists = new String[10];
    for (int i = 1; i <= 10; i++) {
      wsLists[i - 1] = String.format("WSINVOICE_LIST%02d", i);
      invLists[i - 1] = String.format("INVOICE_LIST%02d", i);
    }
    rule(List.of("HBS3R416"), Kind.DIGIT_LIST, wsLists);
    rule(List.of("HBS3R417"), Kind.DIGIT_LIST, invLists);
    required("HBS3R416", "WSINVOICE_LIST01");
    required("HBS3R417", "INVOICE_LIST01");

    // Billing / harvest summaries: file-type lists (quoted by quoteLists) and regions.
    // HBS3R421/422 build their WHERE in HBS_BILLINGSMRYRPT_WHERECLAUSE.
    rule(List.of("HBS3R421", "HBS3R422"), Kind.CODE_LIST, "RB_FILE_TYPE1", "RB_FILE_TYPE2", "RB_FILE_TYPE3");
    rule(List.of("HBS3R421", "HBS3R422"), Kind.DIGITS, "RB_REGION_HARVESTED", "RB_REGION_SCALED");
    rule(List.of("HBS3R431", "HBS3R441"), Kind.CODE_LIST, "INVSMRY_FILETYPE1", "INVSMRY_FILETYPE2", "INVSMRY_FILETYPE3");
    rule(List.of("HBS3R431"), Kind.DIGITS, "INVSMRY_REGDIST");
    quotedList(List.of("HBS3R421", "HBS3R422"), "RB_FILE_TYPE1", "RB_FILE_TYPE2", "RB_FILE_TYPE3");
    quotedList(List.of("HBS3R431", "HBS3R441"), "INVSMRY_FILETYPE1", "INVSMRY_FILETYPE2", "INVSMRY_FILETYPE3");

    // Issued-document registers: HBS_REGISTRYRPT_WHERECLAUSE.
    rule(List.of("HBS3R451", "HBS3R461", "HBS3R471"), Kind.DIGITS,
        "RB_REGION_SCALED", "RB_REGION_HARVESTED", "RB_TRANSMISSION_ID");
    rule(List.of("HBS3R476"), Kind.DIGITS, "RB_TRANSMISSION_ID");

    // Rating, final bills, sampling, audits.
    rule(List.of("HBS3R211"), Kind.DIGITS, "PDDR_DISTRICT");
    rule(List.of("HBS3R211"), Kind.ORDER_BY, "PDDR_ORDER_BY");
    rule(List.of("HBS3R829"), Kind.DIGITS, "FB_ORG_UNIT_NO");
    rule(List.of("HBS3R852"), Kind.DIGITS, "PARAMYEAR");
    rule(List.of("HBS3R902"), Kind.DIGITS, "PLLS_DISTRICTSCALED");
    rule(List.of("HBS3R976"), Kind.DIGITS, "RB_DISTRICT");
  }

  private static void quotedList(List<String> reports, String... prompts) {
    for (String r : reports) QUOTED_LISTS.put(r, List.of(prompts));
  }

  private ReportPromptRules() {}

  /**
   * Rewrites a report's split code list ({@code A01,A02} across up to three
   * prompts) into the quoted form its proc pastes into IN (…), all in the
   * first prompt. {@code ALL} — the procs' "no filter" value — is left as is.
   * Call after {@link #check}, which has already limited the codes to
   * letters and digits.
   */
  static void quoteLists(String reportId, Map<String, Object> params) {
    List<String> group = QUOTED_LISTS.get(reportId.toUpperCase(Locale.ROOT));
    if (group == null) return;
    String joined = group.stream()
        .map(p -> params.get(p) instanceof String v ? v.trim() : "")
        .filter(v -> !v.isEmpty())
        .collect(java.util.stream.Collectors.joining(","));
    if (joined.isEmpty() || joined.equalsIgnoreCase("ALL")) return;
    String quoted = java.util.Arrays.stream(joined.split(","))
        .map(String::trim)
        .filter(c -> !c.isEmpty())
        .map(c -> "'" + c.toUpperCase(Locale.ROOT) + "'")
        .collect(java.util.stream.Collectors.joining(","));
    params.put(group.get(0), quoted);
    group.stream().skip(1).forEach(p -> params.put(p, ""));
  }

  static Rules forReport(String reportId) {
    return BY_REPORT.getOrDefault(reportId.toUpperCase(Locale.ROOT), Rules.NONE);
  }

  /**
   * Checks one report's prompts (already trimmed). Throws
   * IllegalArgumentException — a 400 with this message — on the first problem.
   */
  static void check(String reportId, Map<String, String> prompts) {
    Rules rules = forReport(reportId);
    for (String name : rules.required()) {
      if (blank(prompts.get(name))) {
        throw new IllegalArgumentException("Report " + reportId + " needs " + label(name) + ".");
      }
    }
    rules.requiredWith().forEach((when, then) -> {
      if (!blank(prompts.get(when)) && blank(prompts.get(then))) {
        throw new IllegalArgumentException(
            "Report " + reportId + " needs " + label(then) + " when " + label(when) + " is filled in.");
      }
    });
    rules.kinds().forEach((name, kind) -> {
      String v = prompts.get(name);
      if (!blank(v) && !kind.pattern.matcher(v).matches()) {
        throw new IllegalArgumentException(
            "Report prompt " + label(name) + " takes " + kind.description + ".");
      }
    });
  }

  private static boolean blank(String v) {
    return v == null || v.isBlank();
  }

  /** "SSRETURN_DOCUMENTNUMBER" → "DOCUMENTNUMBER", as the Reports page labels prompts. */
  static String label(String prompt) {
    int i = prompt.indexOf('_');
    return i >= 0 && i < prompt.length() - 1 ? prompt.substring(i + 1) : prompt;
  }
}
