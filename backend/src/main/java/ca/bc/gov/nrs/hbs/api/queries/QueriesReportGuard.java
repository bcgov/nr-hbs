package ca.bc.gov.nrs.hbs.api.queries;

import ca.bc.gov.nrs.hbs.api.query.QueryService;
import ca.bc.gov.nrs.hbs.api.service.v1.report.ReportRequestGuard;
import ca.bc.gov.nrs.hbs.api.struct.v1.report.HbsReportRequestDto;
import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException.Problem;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Report-time rules of the Queries tab, run before the Jasper report:
 *
 * <ul>
 *   <li><b>P451/P456 Print and Print Client Register</b> ({@code HBS4R452/462/472/477},
 *       {@code HBS3R451/461/471/476}) — {@code opq/P450Action.perform} (BT 2637 / BT17247):
 *       the document-number criterion is dropped before the report runs (legacy
 *       {@code setDocumentNumber(null)}); for a non-ministry caller the report is
 *       fenced to the caller's client (prompts {@code RB_CLIENT_ASSOCIATION = BOTH},
 *       {@code RB_CLIENT_NUMBER} = active client, as the list is fenced by
 *       {@code issuedDocuments.search}; the report procs themselves filter only on
 *       the prompts), and refused with the legacy FOI message when
 *       {@code P450Action.canShowIndustry} would have refused it: one of the listed
 *       piece / weight / compiled-sample documents has a transaction whose
 *       parties are both other clients, one of them an Individual
 *       ({@link QueryCriteriaReads#industryNotReleasable}). Ratio statements
 *       were not checked by legacy and are not here. The public site's
 *       {@code canShowInPublic} variant does not apply: the public area offers no
 *       reports.</li>
 *   <li><b>P403 Aged Unbilled Scale</b> ({@code HBS2R411/412}) — the Estimated Average
 *       Rate rules of {@code opq/dac/P403Action.validateForm}.</li>
 * </ul>
 */
@Component
public class QueriesReportGuard implements ReportRequestGuard {

  /** Report → statement kind for the FOI check (P piece, W weight, S compiled sample, R ratio). */
  private static final Map<String, Character> ISSUED_DOC_REPORTS = Map.of(
      "HBS4R452", 'P', "HBS3R451", 'P',
      "HBS4R462", 'W', "HBS3R461", 'W',
      "HBS4R472", 'S', "HBS3R471", 'S',
      "HBS4R477", 'R', "HBS3R476", 'R');

  /** Report prompt → {@code issuedDocuments.search} criterion (the inverse of the screen's mapping). */
  private static final Map<String, String> PROMPT_TO_CRITERION = Map.ofEntries(
      Map.entry("RB_TYPE", "documentType"),
      Map.entry("RB_SCALE_DATE_FROM", "scaleDateFrom"),
      Map.entry("RB_SCALE_DATE_TO", "scaleDateTo"),
      Map.entry("RB_ISSUE_DATE_FROM", "issueDateFrom"),
      Map.entry("RB_ISSUE_DATE_TO", "issueDateTo"),
      Map.entry("RB_TIMBER_MARK", "timberMark"),
      Map.entry("RB_SCALE_SITE", "scaleSite"),
      Map.entry("RB_POPULATION", "population"),
      Map.entry("RB_STRATUM", "stratum"),
      Map.entry("RB_SAMPLINGYEAR", "samplingYear"),
      Map.entry("RB_REGION_SCALED", "regionScaled"),
      Map.entry("RB_REGION_HARVESTED", "regionHarvested"),
      Map.entry("RB_CLIENT_LOCATION_CODE", "clientLocation"),
      Map.entry("RB_TRANSMISSION_ID", "transmissionId"));

  private static final Map<String, String> RATE_PROMPTS = new LinkedHashMap<>();

  static {
    RATE_PROMPTS.put("Avg_Rate_Coast", "coastRate");
    RATE_PROMPTS.put("Avg_Rate_NInterior", "northRate");
    RATE_PROMPTS.put("Avg_Rate_SInterior", "southRate");
  }

  private final QueryService queries;
  private final QueryCriteriaReads reads;

  public QueriesReportGuard(QueryService queries, QueryCriteriaReads reads) {
    this.queries = queries;
    this.reads = reads;
  }

  @Override
  public HbsReportRequestDto apply(String reportId, HbsReportRequestDto request) {
    String id = reportId == null ? "" : reportId.toUpperCase(Locale.ROOT);
    Map<String, String> in = request.parameters() == null ? Map.of() : request.parameters();
    if (id.equals("HBS2R411") || id.equals("HBS2R412")) {
      Map<String, String> rates = new LinkedHashMap<>();
      RATE_PROMPTS.forEach((prompt, key) -> rates.put(key, in.get(prompt)));
      List<Problem> problems = QueriesCriteriaGuard.averageRates(rates);
      if (!problems.isEmpty()) {
        throw new WorkflowValidationException(problems);
      }
      return request;
    }
    Character kind = ISSUED_DOC_REPORTS.get(id);
    if (kind == null) {
      return request;
    }
    Map<String, String> prompts = new HashMap<>(in);
    // P450Action: p450form.getCriteria().setDocumentNumber(null) before every report.
    prompts.put("RB_DOC_NUMBER", "");
    if (!RequestUtil.isMinistryUser()) {
      String client = RequestUtil.getCurrentClientNumber();
      if (!StringUtils.hasText(client)) {
        throw new AccessDeniedException("hbs.no_access_right: no client scope on token");
      }
      prompts.put("RB_CLIENT_ASSOCIATION", "BOTH");
      prompts.put("RB_CLIENT_NUMBER", client);
      if (kind != 'R' && notReleasable(kind, in, client)) {
        throw new WorkflowValidationException("report", QueriesMessages.REPORT_NOT_RELEASABLE);
      }
    }
    return new HbsReportRequestDto(prompts, request.format());
  }

  /**
   * {@code P450Action.canShowIndustry}: the summary returns of the statements the
   * search lists ({@code manager.findByIssuedStatementsByCriteria(criteria)} →
   * {@code loadScaleReturn}), then the BT17247 count over their transactions.
   */
  private boolean notReleasable(char kind, Map<String, String> prompts, String client) {
    Map<String, String> criteria = new HashMap<>();
    PROMPT_TO_CRITERION.forEach((prompt, key) -> {
      String v = prompts.get(prompt);
      if (StringUtils.hasText(v)) criteria.put(key, v.trim());
    });
    if (kind == 'S') {
      criteria.put("documentType", "CSS");
    }
    // The caller's own documents only, as the screen listed them (clientScope fences too).
    criteria.put("clientNumber", client);
    criteria.put("statementNumber", prompts.getOrDefault("RB_DOC_NUMBER", ""));
    Set<Long> dcns = new LinkedHashSet<>();
    for (Map<String, Object> row : queries.list("issuedDocuments.search", criteria)) {
      Object dcn = row.get("documentControlNumber");
      if (dcn instanceof Number n) {
        dcns.add(n.longValue());
      } else if (dcn != null && StringUtils.hasText(dcn.toString())) {
        dcns.add(Long.parseLong(dcn.toString().trim()));
      }
    }
    return reads.industryNotReleasable(kind, dcns, client);
  }
}
