package ca.bc.gov.nrs.hbs.api.queries;

import java.nio.charset.StandardCharsets;
import java.sql.Clob;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.xml.sax.SAXException;

/**
 * The files of a statement-delivery transmission (what legacy B2065 wrote to the
 * client's statement drop directory as {@code <transmissionId>.xml} / {@code .pdf}),
 * rebuilt from Oracle: DOCUMENT_DELIVERY_TRANSMISSION + DOCUMENT_DELIVERY_BATCH_ITEM
 * + each item's HBS_FILE.FILE_OBJECT ({@link DeliveryTransmissionXml}), and the PDF
 * from that XML with the current stylesheets (B2065:
 * {@code XmlConstants.CURRENT_XSL_VERSION}).
 *
 * <p>Shared by the e-mailed links ({@link StatementTransmissionController}) and the
 * scale data API's statement feed, which only differ in who may read what.
 */
@Service
@Slf4j
public class StatementTransmissionFiles {

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

  /** A transmission row: who it was for, and which files B2065 has written. */
  public record Found(DeliveryTransmissionXml.Transmission transmission, String xmlFile, String pdfFile) {

    public String clientNumber() {
      return transmission.clientNumber();
    }

    /** When B2065 created the transmission (Pacific time). */
    public java.time.LocalDateTime created() {
      return transmission.created();
    }

    /** B2065 has created this format's file (FILE_NAME / PDF_FILE_NAME set when the row moved to COM). */
    public boolean has(boolean pdf) {
      return StringUtils.hasText(pdf ? pdfFile : xmlFile);
    }
  }

  private final NamedParameterJdbcTemplate jdbc;
  private final StatementPdfRenderer pdfRenderer;
  private final StatementXslVersion xslVersion;

  public StatementTransmissionFiles(NamedParameterJdbcTemplate jdbc, StatementPdfRenderer pdfRenderer,
      StatementXslVersion xslVersion) {
    this.jdbc = jdbc;
    this.pdfRenderer = pdfRenderer;
    this.xslVersion = xslVersion;
  }

  public Optional<Found> find(long transmissionId) {
    List<Found> rows = jdbc.query(TRANSMISSION_SQL, new MapSqlParameterSource("transmissionId", transmissionId),
        (rs, n) -> {
          Timestamp created = rs.getTimestamp("creation_date");
          return new Found(new DeliveryTransmissionXml.Transmission(rs.getLong("transmission_id"),
              trim(rs.getString("client_number")), trim(rs.getString("client_locn_code")),
              trim(rs.getString("recipient_type")),
              created == null ? java.time.LocalDateTime.now() : created.toLocalDateTime()),
              rs.getString("file_name"), rs.getString("pdf_file_name"));
        });
    return rows.stream().findFirst();
  }

  /** The file as a download ({@code <transmissionId>.pdf|.xml}); the caller has checked access and {@link Found#has}. */
  public ResponseEntity<byte[]> download(Found found, boolean pdf) {
    long id = found.transmission().transmissionId();
    String xml = DeliveryTransmissionXml.build(found.transmission(), items(id));
    try {
      StatementTransmissionXml.validate(xml);
    } catch (SAXException ex) {
      // B2065 verified against the then-current schema; stored files may follow an older one.
      log.warn("Transmission {} XML doesn't validate against V1_0h: {}", id, ex.getMessage());
    }
    String filename = id + (pdf ? ".pdf" : ".xml");
    byte[] body = pdf ? pdfRenderer.render(xml, xslVersion.current()) : xml.getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.TEXT_XML)
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
        .body(body);
  }

  private List<DeliveryTransmissionXml.Item> items(long transmissionId) {
    return jdbc.query(ITEMS_SQL, new MapSqlParameterSource("transmissionId", transmissionId),
        (rs, n) -> new DeliveryTransmissionXml.Item(rs.getLong("hbs_file_id"), rs.getString("stmt_type"),
            clob(rs.getClob("file_object"))));
  }

  private static String trim(String s) {
    return s == null ? null : s.trim();
  }

  private static String clob(Clob clob) throws SQLException {
    if (clob == null) return null;
    return clob.getSubString(1, (int) Math.min(clob.length(), Integer.MAX_VALUE));
  }
}
