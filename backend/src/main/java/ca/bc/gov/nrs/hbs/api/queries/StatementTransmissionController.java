package ca.bc.gov.nrs.hbs.api.queries;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.security.HbsAuthorities;
import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.sql.Clob;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.xml.sax.SAXException;

/**
 * The statement-delivery transmission files that legacy batch B2065 wrote to the
 * client's statement drop directory ({@code <transmissionId>.xml} / {@code .pdf})
 * and that the statement e-mails link to
 * ({@code <webAppBaseUrl>/StatementDelivery/transmissions/<id>.pdf|.xml},
 * {@code EmailTranmissionDelivery}). Legacy served them from the share
 * ({@code presentation/dad/StatementServlet.handleTransmissionCommand}); this app
 * can't read the share, so it rebuilds them from Oracle:
 * DOCUMENT_DELIVERY_TRANSMISSION + DOCUMENT_DELIVERY_BATCH_ITEM + each item's
 * HBS_FILE.FILE_OBJECT ({@link DeliveryTransmissionXml}), and the PDF from that
 * XML with the current stylesheets (B2065: {@code XmlConstants.CURRENT_XSL_VERSION}).
 *
 * <p>Access (StatementServlet): a logged-in user; a ministry (MOF_USER) user may
 * open any client's transmission, optionally naming the client with
 * {@code ?clientNumber=} as legacy required; anyone else only their own client's
 * transmissions. Anything else is 404, as legacy's "file not found in your
 * folder". No FOI severing: legacy served the client's own files as written.
 *
 * <p>A transmission is only available once B2065 has written its files
 * ({@code FILE_NAME} / {@code PDF_FILE_NAME} set when the row moved to COM).
 */
@RestController
@RequestMapping("/api/v1/hbs/statements/transmissions")
@Tag(name = "Queries", description = "Statement delivery transmissions (legacy StatementDelivery servlet)")
@Slf4j
public class StatementTransmissionController {

  private static final Pattern FILE = Pattern.compile("\\d{1,32}");

  private static final String TRANSMISSION_SQL = """
      SELECT t.transmission_id, t.client_number, t.client_locn_code, t.recipient_type,
             t.file_name, t.pdf_file_name, t.creation_date
        FROM document_delivery_transmission t
       WHERE t.transmission_id = :transmissionId""";

  /** DocumentDeliveryTransmissionQuery.selectTransmissionItems (+ the file), in insertion order. */
  private static final String ITEMS_SQL = """
      SELECT item.hbs_file_id, hbs_determine_statement_type(item.hbs_file_id) AS stmt_type,
             hf.file_object
        FROM document_delivery_batch_item item
        JOIN hbs_file hf ON hf.hbs_file_id = item.hbs_file_id
       WHERE item.transmission_id = :transmissionId
       ORDER BY item.batch_id""";

  private record Row(DeliveryTransmissionXml.Transmission transmission, String xmlFile, String pdfFile) {}

  private final NamedParameterJdbcTemplate jdbc;
  private final StatementPdfRenderer pdfRenderer;
  private final StatementXslVersion xslVersion;

  public StatementTransmissionController(NamedParameterJdbcTemplate jdbc, StatementPdfRenderer pdfRenderer,
      StatementXslVersion xslVersion) {
    this.jdbc = jdbc;
    this.pdfRenderer = pdfRenderer;
    this.xslVersion = xslVersion;
  }

  @GetMapping("/{id}.{format}")
  @PreAuthorize(HbsAuthorities.ANY_USER)
  @Operation(summary = "A statement-delivery transmission (the e-mailed link) as XML or PDF")
  public ResponseEntity<byte[]> transmission(@PathVariable String id, @PathVariable String format,
      @RequestParam(name = "clientNumber", required = false) String clientNumber) {
    Capability.ANY_USER.require();
    boolean pdf = "pdf".equalsIgnoreCase(format);
    if ((!pdf && !"xml".equalsIgnoreCase(format)) || id == null || !FILE.matcher(id).matches()) {
      throw notFound();
    }
    Row row = load(Long.parseLong(id));
    if (row == null || !mayRead(RequestUtil.isMinistryUser(), RequestUtil.getCurrentClientNumber(), clientNumber,
        row.transmission().clientNumber())) {
      throw notFound();
    }
    if (!StringUtils.hasText(pdf ? row.pdfFile() : row.xmlFile())) {
      // B2065 has not created this transmission's files yet.
      throw notFound();
    }
    String xml = DeliveryTransmissionXml.build(row.transmission(), items(row.transmission().transmissionId()));
    try {
      StatementTransmissionXml.validate(xml);
    } catch (SAXException ex) {
      // B2065 verified against the then-current schema; stored files may follow an older one.
      log.warn("Transmission {} XML doesn't validate against V1_0h: {}", id, ex.getMessage());
    }
    String filename = row.transmission().transmissionId() + (pdf ? ".pdf" : ".xml");
    byte[] body = pdf ? pdfRenderer.render(xml, xslVersion.current()) : xml.getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.TEXT_XML)
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
        .body(body);
  }

  /**
   * StatementServlet.handleTransmissionCommand: ministry users read the folder of the
   * {@code clientNumber} they name (any client when they name none); everyone else
   * reads their own client's folder only.
   */
  static boolean mayRead(boolean ministry, String viewerClient, String requestedClient, String owner) {
    if (!StringUtils.hasText(owner)) {
      return false;
    }
    String transmissionClient = owner.trim();
    if (ministry) {
      return !StringUtils.hasText(requestedClient) || requestedClient.trim().equals(transmissionClient);
    }
    return StringUtils.hasText(viewerClient) && viewerClient.trim().equals(transmissionClient);
  }

  private Row load(long transmissionId) {
    List<Row> rows = jdbc.query(TRANSMISSION_SQL, new MapSqlParameterSource("transmissionId", transmissionId),
        (rs, n) -> {
          Timestamp created = rs.getTimestamp("creation_date");
          return new Row(new DeliveryTransmissionXml.Transmission(rs.getLong("transmission_id"),
              trim(rs.getString("client_number")), trim(rs.getString("client_locn_code")),
              trim(rs.getString("recipient_type")),
              created == null ? java.time.LocalDateTime.now() : created.toLocalDateTime()),
              rs.getString("file_name"), rs.getString("pdf_file_name"));
        });
    return rows.isEmpty() ? null : rows.get(0);
  }

  private List<DeliveryTransmissionXml.Item> items(long transmissionId) {
    return jdbc.query(ITEMS_SQL, new MapSqlParameterSource("transmissionId", transmissionId),
        (rs, n) -> new DeliveryTransmissionXml.Item(rs.getLong("hbs_file_id"), rs.getString("stmt_type"),
            clob(rs.getClob("file_object"))));
  }

  private static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Could not find the transmission file");
  }

  private static String trim(String s) {
    return s == null ? null : s.trim();
  }

  private static String clob(Clob clob) throws SQLException {
    if (clob == null) return null;
    return clob.getSubString(1, (int) Math.min(clob.length(), Integer.MAX_VALUE));
  }
}
