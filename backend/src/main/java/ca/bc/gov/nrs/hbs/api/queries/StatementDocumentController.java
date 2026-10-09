package ca.bc.gov.nrs.hbs.api.queries;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.security.HbsAuthorities;
import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Clob;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Replacement for the legacy {@code /StatementDelivery/statements/<type>/<number>.pdf|xml}
 * servlet ({@code presentation.dad.StatementServlet}). Serves one issued
 * invoice/statement from the stored statement XML ({@code HBS_FILE.FILE_OBJECT},
 * located through the statement table's {@code HBS_FILE_ID}, as
 * {@code HbsFileManagerBean.findHbsFileByTransactionStatement} did).
 *
 * <ul>
 *   <li>FOI: {@code TransactionStatementManagerBean.freedomOfInformationCheck} —
 *       ministry users and the send-to / copy-to client see everything; for
 *       anyone else the send-to block ({@code invoice-to}, {@code population-owner})
 *       and/or copy-to block ({@code copy-to}, {@code stratum-owner}) of an
 *       Individual ({@code client_type_code = 'I'}) client is replaced as the
 *       legacy {@code xsl/<ver>/freedom-of-info-{send,copy}-to.xsl} did
 *       (client-number/code kept, name "NOT RELEASABLE", address blanked).
 *       The legacy {@code ?foi} debug parameter is intentionally not ported.</li>
 *   <li>7-year rule (P449 {@code LimitYear.SEVEN} + 2003-11-01 cut-over): older
 *       documents are 410 Gone.</li>
 *   <li>PDF: the legacy app rendered the XML through XSL-FO
 *       ({@code HbsStatementPdfFactory}, Apache FOP 0.20 + {@code xsl/<ver>/hbs-transmission.xsl}).
 *       Neither FOP nor the stylesheets are in this backend yet, so {@code .pdf}
 *       answers 501 until they are added (see docs/areas/queries.md).</li>
 * </ul>
 *
 * <p>The SPA must fetch these with the bearer token (apiFetch / getBlob), not
 * a plain link.
 */
@RestController
@RequestMapping("/api/v1/hbs/statements")
@Tag(name = "Queries", description = "Issued invoice / statement documents (legacy StatementDelivery servlet)")
@Slf4j
public class StatementDocumentController {

  private static final Pattern NUMBER = Pattern.compile("[A-Z0-9]{1,15}");
  private static final LocalDate HBS_CUTOVER = LocalDate.of(2003, 11, 1);
  private static final Set<String> SEND_TO_BLOCKS = Set.of("invoice-to", "population-owner");
  private static final Set<String> COPY_TO_BLOCKS = Set.of("copy-to", "stratum-owner");
  private static final List<String> BLANKED = List.of(
      "name2", "address1", "address2", "address3", "city", "province", "country", "postal-code");

  /** Statement type → whitelisted lookup SQL (TransactionStatementQuery.getTableName). */
  private enum StatementType {
    PSI("hbs_ps_statement", "AND s.psstmt_type = 'PSI'"),
    PSV("hbs_ps_statement", "AND s.psstmt_type = 'PSV'"),
    WSI("hbs_ws_statement", "AND s.wsstmt_type = 'WSI'"),
    WSV("hbs_ws_statement", "AND s.wsstmt_type = 'WSV'"),
    CSS("hbs_compiled_sample_stmnt", ""),
    RS("hbs_ratio_statement", "");

    final String sql;

    StatementType(String table, String typeFilter) {
      this.sql = """
          SELECT s.client_number_send_to, s.client_number_copy_to, s.issue_date,
                 st.client_type_code AS send_to_type, ct.client_type_code AS copy_to_type,
                 hf.file_object
            FROM %s s
            JOIN hbs_file hf ON hf.hbs_file_id = s.hbs_file_id
            LEFT JOIN v_client_public st ON st.client_number = s.client_number_send_to
            LEFT JOIN v_client_public ct ON ct.client_number = s.client_number_copy_to
           WHERE s.statement_number = :statementNumber %s""".formatted(table, typeFilter);
    }
  }

  private record Stored(String sendTo, String copyTo, LocalDate issueDate,
      String sendToType, String copyToType, String xml) {}

  private final NamedParameterJdbcTemplate jdbc;

  public StatementDocumentController(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/{type}/{number}.{format}")
  @PreAuthorize(HbsAuthorities.QUERIES_VIEW)
  @Operation(summary = "One issued invoice/statement as XML (FOI-severed) or PDF")
  public ResponseEntity<byte[]> statement(@PathVariable String type, @PathVariable String number,
      @PathVariable String format) {
    Capability.QUERIES_VIEW.require();
    StatementType statementType = parseType(type);
    String statementNumber = number == null ? "" : number.trim().toUpperCase();
    if (!NUMBER.matcher(statementNumber).matches()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid statement number was provided");
    }
    boolean pdf = "pdf".equalsIgnoreCase(format);
    if (!pdf && !"xml".equalsIgnoreCase(format)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unsupported format " + format);
    }

    Stored stored = load(statementType, statementNumber);
    LocalDate limit = LocalDate.now().minusYears(7);
    if (stored.issueDate() == null || stored.issueDate().isBefore(limit)
        || stored.issueDate().isBefore(HBS_CUTOVER)) {
      throw new ResponseStatusException(HttpStatus.GONE,
          "Statement cannot be retrieved as it is older than seven years");
    }

    String xml = applyFreedomOfInformation(stored);
    if (pdf) {
      // Legacy: HbsStatementPdfFactory (XSL-FO via FOP) — not yet ported.
      throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
          "PDF rendering of statements is not available yet; download the XML version");
    }
    String filename = statementNumber + ".xml";
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_XML)
        .header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
        .body(xml.getBytes(StandardCharsets.UTF_8));
  }

  private static StatementType parseType(String type) {
    try {
      return StatementType.valueOf(type == null ? "" : type.toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid Statement Type " + type);
    }
  }

  private Stored load(StatementType type, String statementNumber) {
    List<Stored> rows = jdbc.query(type.sql,
        new MapSqlParameterSource("statementNumber", statementNumber),
        (rs, n) -> {
          java.sql.Date issue = rs.getDate("issue_date");
          return new Stored(rs.getString("client_number_send_to"), rs.getString("client_number_copy_to"),
              issue == null ? null : issue.toLocalDate(), rs.getString("send_to_type"),
              rs.getString("copy_to_type"), clob(rs.getClob("file_object")));
        });
    if (rows.isEmpty() || !StringUtils.hasText(rows.get(0).xml())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Statement Does Not Exist");
    }
    return rows.get(0);
  }

  private static String clob(Clob clob) throws SQLException {
    if (clob == null) return null;
    return clob.getSubString(1, (int) Math.min(clob.length(), Integer.MAX_VALUE));
  }

  /**
   * {@code freedomOfInformationCheck}: false (no severing) for ministry users
   * and for the send-to / copy-to client; otherwise sever a block only when
   * that block's client is an Individual.
   */
  private String applyFreedomOfInformation(Stored s) {
    if (RequestUtil.isMinistryUser()) {
      return s.xml();
    }
    String viewer = RequestUtil.getCurrentClientNumber();
    if (StringUtils.hasText(viewer) && (viewer.equals(s.sendTo()) || viewer.equals(s.copyTo()))) {
      return s.xml();
    }
    boolean severSendTo = "I".equals(s.sendToType());
    boolean severCopyTo = StringUtils.hasText(s.copyTo()) && "I".equals(s.copyToType());
    if (!severSendTo && !severCopyTo) {
      return s.xml();
    }
    return sever(s.xml(), severSendTo, severCopyTo);
  }

  static String sever(String xml, boolean sendTo, boolean copyTo) {
    try {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true);
      dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      dbf.setExpandEntityReferences(false);
      Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
      List<Element> targets = new ArrayList<>();
      NodeList all = doc.getElementsByTagNameNS("*", "*");
      for (int i = 0; i < all.getLength(); i++) {
        Element e = (Element) all.item(i);
        String local = e.getLocalName() == null ? e.getNodeName() : e.getLocalName();
        if ((sendTo && SEND_TO_BLOCKS.contains(local)) || (copyTo && COPY_TO_BLOCKS.contains(local))) {
          targets.add(e);
        }
      }
      for (Element block : targets) {
        severBlock(doc, block);
      }
      var tf = TransformerFactory.newInstance();
      tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
      var transformer = tf.newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      StringWriter out = new StringWriter();
      transformer.transform(new DOMSource(doc), new StreamResult(out));
      return out.toString();
    } catch (Exception ex) {
      log.error("FOI severing failed", ex);
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
          "Error filtering documents for freedom of information");
    }
  }

  /** Mirrors the XSL template: keep client-number + client-code, NOT RELEASABLE name, empty address. */
  private static void severBlock(Document doc, Element block) {
    String ns = block.getNamespaceURI();
    Map<String, String> kept = new java.util.HashMap<>();
    NodeList children = block.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node c = children.item(i);
      if (c instanceof Element ce) {
        String local = ce.getLocalName() == null ? ce.getNodeName() : ce.getLocalName();
        if ("client-number".equals(local) || "client-code".equals(local)) {
          kept.put(local, ce.getTextContent());
        }
      }
    }
    while (block.getFirstChild() != null) {
      block.removeChild(block.getFirstChild());
    }
    for (Node attr : attributes(block)) {
      block.removeAttributeNode((org.w3c.dom.Attr) attr);
    }
    block.appendChild(text(doc, ns, "client-number", kept.getOrDefault("client-number", "")));
    block.appendChild(text(doc, ns, "client-code", kept.getOrDefault("client-code", "")));
    block.appendChild(text(doc, ns, "name", "NOT RELEASABLE"));
    for (String name : BLANKED) {
      block.appendChild(text(doc, ns, name, ""));
    }
  }

  private static List<Node> attributes(Element e) {
    List<Node> out = new ArrayList<>();
    for (int i = 0; i < e.getAttributes().getLength(); i++) {
      Node a = e.getAttributes().item(i);
      if (!"xmlns".equals(a.getPrefix()) && !"xmlns".equals(a.getNodeName())) {
        out.add(a);
      }
    }
    return out;
  }

  private static Element text(Document doc, String ns, String name, String value) {
    Element el = ns == null ? doc.createElement(name) : doc.createElementNS(ns, name);
    el.setTextContent(value);
    return el;
  }
}
