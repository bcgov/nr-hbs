package ca.bc.gov.nrs.hbs.api.publicaccess;

import ca.bc.gov.nrs.hbs.api.exception.EntityNotFoundException;
import ca.bc.gov.nrs.hbs.api.queries.StatementDocumentController;
import ca.bc.gov.nrs.hbs.api.queries.StatementDocumentService;
import ca.bc.gov.nrs.hbs.api.query.QueryDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryService;
import ca.bc.gov.nrs.hbs.api.query.Viewer;
import ca.bc.gov.nrs.hbs.api.struct.v1.PageableResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The public (anonymous) read-only area — the replacement for what the legacy
 * app showed to a visitor who had not logged in ({@code Public_User},
 * {@code include/mainmenu_public.jsp}). Served without a token under
 * {@code /api/v1/hbs/public/**} only ({@code PublicAccessSecurityConfig}),
 * rate-limited per client IP ({@link PublicRateLimitFilter}).
 *
 * <p>Every query and document here is produced for {@link Viewer#ANONYMOUS}
 * regardless of what the request carries (an {@code Authorization} header is
 * ignored on these paths): FOI severing always applies, no client fence can
 * apply, and only {@link PublicQueries} entries can run.
 */
@RestController
@RequestMapping("/api/v1/hbs/public")
@Tag(name = "Public", description = "Anonymous read-only queries and statement copies (legacy public site)")
public class PublicController {

  private static final Set<String> PAGING = Set.of("page", "size", "sortBy", "sortDir");
  private static final String AUTH_STATEMENTS = "/api/v1/hbs/statements/";
  private static final String PUBLIC_STATEMENTS = "/api/v1/hbs/public/statements/";

  private final PublicQueries publicQueries;
  private final QueryService queryService;
  private final StatementDocumentService documents;

  public PublicController(PublicQueries publicQueries, QueryService queryService,
      StatementDocumentService documents) {
    this.publicQueries = publicQueries;
    this.queryService = queryService;
    this.documents = documents;
  }

  @GetMapping("/queries/{queryId}")
  @Operation(summary = "Paged public search (anonymous viewer, FOI-severed)")
  public PageableResponse<Map<String, Object>> search(@PathVariable String queryId,
      @RequestParam Map<String, String> params,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sortBy,
      @RequestParam(required = false) String sortDir) {
    Map<String, String> criteria = criteria(params);
    QueryDefinition q = prepare(queryId, criteria);
    PageableResponse<Map<String, Object>> result =
        queryService.search(q, Viewer.ANONYMOUS, criteria, page, size, sortBy, sortDir);
    result.setContent(result.getContent().stream().map(PublicController::publicLinks).toList());
    return result;
  }

  @GetMapping("/queries/{queryId}/list")
  @Operation(summary = "All rows (capped) of a public query — lookups and child tables")
  public List<Map<String, Object>> list(@PathVariable String queryId, @RequestParam Map<String, String> params) {
    Map<String, String> criteria = criteria(params);
    QueryDefinition q = prepare(queryId, criteria);
    return queryService.list(q, Viewer.ANONYMOUS, criteria).stream().map(PublicController::publicLinks).toList();
  }

  @GetMapping("/queries/{queryId}/one")
  @Operation(summary = "A single record of a public detail query")
  public Map<String, Object> one(@PathVariable String queryId, @RequestParam Map<String, String> params) {
    List<Map<String, Object>> rows = list(queryId, params);
    if (rows.isEmpty()) {
      throw new EntityNotFoundException(QueryDefinition.class, "query", queryId);
    }
    return rows.get(0);
  }

  @GetMapping("/queries/{queryId}/export")
  @Operation(summary = "CSV export of a public query (capped at hbs.public.max-rows)")
  public ResponseEntity<byte[]> export(@PathVariable String queryId, @RequestParam Map<String, String> params) {
    byte[] csv = QueryService.toCsv(list(queryId, params)).getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(queryId + ".csv").build().toString())
        .contentType(new MediaType("text", "csv"))
        .body(csv);
  }

  /** P451/P456 "Generate XML" for the public site: the listed statements, severed, as one transmission. */
  @GetMapping("/statements/export.xml")
  @Operation(summary = "All issued documents of a public search as one statement transmission XML")
  public ResponseEntity<byte[]> combined(@RequestParam Map<String, String> params) {
    String xml = combinedXml(params, "XML");
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_XML)
        .header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("issued-documents.xml").build().toString())
        .body(xml.getBytes(StandardCharsets.UTF_8));
  }

  /** P451/P456 "Generate PDF" for the public site: the same transmission, rendered. */
  @GetMapping("/statements/export.pdf")
  @Operation(summary = "All issued documents of a public search as one PDF")
  public ResponseEntity<byte[]> combinedPdf(@RequestParam Map<String, String> params) {
    byte[] pdf = documents.combinedPdf(combinedXml(params, "PDF"));
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("issued-documents.pdf").build().toString())
        .body(pdf);
  }

  private String combinedXml(Map<String, String> params, String format) {
    String query = "issuedDocuments.search";
    Map<String, String> criteria = criteria(params);
    QueryDefinition q = prepare(query, criteria);
    long total = queryService.count(q, Viewer.ANONYMOUS, criteria);
    if (total > StatementDocumentService.COMBINED_LIMIT) {
      throw new IllegalArgumentException("The search lists " + total + " documents; the " + format
          + " download is limited to " + StatementDocumentService.COMBINED_LIMIT + ". Please refine your search criteria.");
    }
    return documents.combinedXml(queryService.list(q, Viewer.ANONYMOUS, criteria), Viewer.ANONYMOUS, null);
  }

  /** Invoice / statement copy (legacy {@code /StatementDelivery/statements/…} for the public site). */
  @GetMapping("/statements/{type}/{number}.{format}")
  @Operation(summary = "One issued invoice/statement as XML or PDF, FOI-severed for the public")
  public ResponseEntity<byte[]> statement(@PathVariable String type, @PathVariable String number,
      @PathVariable String format) {
    return StatementDocumentController.render(documents, type, number, format, Viewer.ANONYMOUS);
  }

  private QueryDefinition prepare(String queryId, Map<String, String> criteria) {
    QueryDefinition q = publicQueries.query(queryId);
    publicQueries.validate(queryId, criteria);
    return q;
  }

  private static Map<String, String> criteria(Map<String, String> params) {
    Map<String, String> out = new HashMap<>(params);
    PAGING.forEach(out::remove);
    return out;
  }

  /** Statement links in a row point at the public statement endpoint, never the authenticated one. */
  static Map<String, Object> publicLinks(Map<String, Object> row) {
    Map<String, Object> out = new LinkedHashMap<>(row);
    out.replaceAll((k, v) -> v instanceof String s && s.startsWith(AUTH_STATEMENTS)
        ? PUBLIC_STATEMENTS + s.substring(AUTH_STATEMENTS.length()) : v);
    return out;
  }
}
