package ca.bc.gov.nrs.hbs.api.queries;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.query.QueryService;
import ca.bc.gov.nrs.hbs.api.query.Viewer;
import ca.bc.gov.nrs.hbs.api.security.HbsAuthorities;
import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Replacement for the legacy {@code /StatementDelivery/statements/<type>/<number>.pdf|xml}
 * servlet ({@code presentation.dad.StatementServlet}) for logged-in users. The
 * document itself — lookup, 7-year rule, FOI severing, PDF rendering — is
 * {@link StatementDocumentService}, which the public read-only area
 * ({@code /api/v1/hbs/public/statements/…}) calls too, as the anonymous viewer.
 * The legacy {@code ?foi} debug parameter is intentionally not ported.
 *
 * <p>The SPA must fetch these with the bearer token (apiFetch / getBlob), not
 * a plain link.
 */
@RestController
@RequestMapping("/api/v1/hbs/statements")
@Tag(name = "Queries", description = "Issued invoice / statement documents (legacy StatementDelivery servlet)")
public class StatementDocumentController {

  /** Legacy P451/P456 offered "Generate XML" / "Generate PDF" only up to Constants.SUPPRESS_LIMIT rows. */
  static final int COMBINED_LIMIT = StatementDocumentService.COMBINED_LIMIT;

  private final QueryService queryService;
  private final StatementDocumentService documents;

  public StatementDocumentController(QueryService queryService, StatementDocumentService documents) {
    this.queryService = queryService;
    this.documents = documents;
  }

  /**
   * Search for Issued Documents "Generate XML": every retrievable statement the search lists,
   * FOI-severed, merged into one transmission ({@link StatementTransmissionXml}).
   */
  @GetMapping("/export.xml")
  @PreAuthorize(HbsAuthorities.QUERIES_VIEW)
  @Operation(summary = "All issued documents of a search as one statement transmission XML")
  public ResponseEntity<byte[]> combined(@RequestParam Map<String, String> criteria) {
    Capability.QUERIES_VIEW.require();
    return xmlAttachment("issued-documents.xml", combinedXml(criteria, "XML"));
  }

  /**
   * Search for Issued Documents "Generate PDF" ({@code DataDeliveryAction} ACTION_PDF_REPORT): the
   * same merged transmission rendered with the current stylesheets.
   */
  @GetMapping("/export.pdf")
  @PreAuthorize(HbsAuthorities.QUERIES_VIEW)
  @Operation(summary = "All issued documents of a search as one PDF")
  public ResponseEntity<byte[]> combinedPdf(@RequestParam Map<String, String> criteria) {
    Capability.QUERIES_VIEW.require();
    byte[] pdf = documents.combinedPdf(combinedXml(criteria, "PDF"));
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("issued-documents.pdf").build().toString())
        .body(pdf);
  }

  /** {@code DocumentDeliveryManagerBean.generateStatementReport}: the listed statements, severed and merged. */
  private String combinedXml(Map<String, String> criteria, String format) {
    String query = "issuedDocuments.search";
    long total = queryService.count(query, criteria);
    if (total > COMBINED_LIMIT) {
      throw new IllegalArgumentException("The search lists " + total + " documents; the " + format
          + " download is limited to " + COMBINED_LIMIT + ". Please refine your search criteria.");
    }
    String email;
    try {
      email = RequestUtil.getCurrentJwt().getClaimAsString("email");
    } catch (RuntimeException ex) {
      email = null;
    }
    return documents.combinedXml(queryService.list(query, criteria), Viewer.current(), email);
  }

  @GetMapping("/{type}/{number}.{format}")
  @PreAuthorize(HbsAuthorities.QUERIES_VIEW)
  @Operation(summary = "One issued invoice/statement as XML (FOI-severed) or PDF")
  public ResponseEntity<byte[]> statement(@PathVariable String type, @PathVariable String number,
      @PathVariable String format) {
    Capability.QUERIES_VIEW.require();
    return render(documents, type, number, format, Viewer.current());
  }

  /**
   * One statement as XML or PDF for {@code viewer} — shared with the public
   * controller so both paths answer identically.
   */
  public static ResponseEntity<byte[]> render(StatementDocumentService documents, String type, String number,
      String format, Viewer viewer) {
    boolean pdf = "pdf".equalsIgnoreCase(format);
    if (!pdf && !"xml".equalsIgnoreCase(format)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unsupported format " + format);
    }
    StatementDocumentService.StatementDocument doc = documents.xml(type, number, viewer);
    if (pdf) {
      byte[] body = documents.pdf(doc);
      String filename = doc.filename().replaceFirst("\\.xml$", ".pdf");
      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_PDF)
          .header(HttpHeaders.CONTENT_DISPOSITION,
              ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
          .body(body);
    }
    return xmlAttachment(doc.filename(), doc.xml());
  }

  static ResponseEntity<byte[]> xmlAttachment(String filename, String xml) {
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_XML)
        .header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
        .body(xml.getBytes(StandardCharsets.UTF_8));
  }
}
