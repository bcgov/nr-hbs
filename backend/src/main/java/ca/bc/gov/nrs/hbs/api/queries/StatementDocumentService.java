package ca.bc.gov.nrs.hbs.api.queries;

import ca.bc.gov.nrs.hbs.api.query.Viewer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
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
import java.sql.Clob;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One issued invoice/statement, rebuilt from the stored statement XML
 * ({@code HBS_FILE.FILE_OBJECT}, located through the statement table's
 * {@code HBS_FILE_ID}, as {@code HbsFileManagerBean.findHbsFileByTransactionStatement}
 * did) and FOI-severed for a given {@link Viewer}. Shared by the authenticated
 * {@link StatementDocumentController} and the public read-only area
 * ({@code publicaccess.PublicController}), so both serve exactly the same
 * document with exactly the same severing rules.
 *
 * <ul>
 *   <li>FOI: {@code TransactionStatementManagerBean.freedomOfInformationCheck} —
 *       ministry users and the send-to / copy-to client see everything; anyone
 *       else (including the anonymous public viewer, legacy {@code Public_User},
 *       BT17247 "the FOI filter is applied indiscriminately" on the public site)
 *       gets the send-to block ({@code invoice-to}, {@code population-owner})
 *       and/or copy-to block ({@code copy-to}, {@code stratum-owner}) of an
 *       Individual ({@code client_type_code = 'I'}) client replaced as the
 *       legacy {@code xsl/<ver>/freedom-of-info-{send,copy}-to.xsl} did
 *       (client-number/code kept, name "NOT RELEASABLE", address blanked).</li>
 *   <li>7-year rule (P449 {@code LimitYear.SEVEN} + 2003-11-01 cut-over): older
 *       documents are 410 Gone.</li>
 *   <li>PDF: {@link #pdf} is the single rendering seam: the severed XML through the
 *       legacy XSL-FO stylesheets of the file's own version
 *       ({@code HBS_FILE.HBS_TS_VERSION_ID → HBS_TS_VERSION.TRANS_VERSION},
 *       {@code HbsStatementPdfFactory}), see {@link StatementPdfRenderer}.</li>
 * </ul>
 */
@Service
@Slf4j
public class StatementDocumentService {

  static final Pattern NUMBER = Pattern.compile("[A-Z0-9]{1,15}");
  static final LocalDate HBS_CUTOVER = LocalDate.of(2003, 11, 1);
  private static final Set<String> SEND_TO_BLOCKS = Set.of("invoice-to", "population-owner");
  private static final Set<String> COPY_TO_BLOCKS = Set.of("copy-to", "stratum-owner");
  private static final List<String> BLANKED = List.of(
      "name2", "address1", "address2", "address3", "city", "province", "country", "postal-code");

  /** Legacy P451/P456 offered "Generate XML" / "Generate PDF" only up to Constants.SUPPRESS_LIMIT rows. */
  public static final int COMBINED_LIMIT = 200;

  /** Statement type → whitelisted lookup SQL (TransactionStatementQuery.getTableName). */
  enum StatementType {
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
                 htv.trans_version, hf.file_object
            FROM %s s
            JOIN hbs_file hf ON hf.hbs_file_id = s.hbs_file_id
            LEFT JOIN hbs_ts_version htv ON htv.hbs_ts_version_id = hf.hbs_ts_version_id
            LEFT JOIN v_client_public st ON st.client_number = s.client_number_send_to
            LEFT JOIN v_client_public ct ON ct.client_number = s.client_number_copy_to
           WHERE s.statement_number = :statementNumber %s""".formatted(table, typeFilter);
    }
  }

  /** A stored statement and the parties FOI is decided on. */
  record Stored(String sendTo, String copyTo, LocalDate issueDate,
      String sendToType, String copyToType, String transVersion, String xml) {

    Stored(String sendTo, String copyTo, LocalDate issueDate, String sendToType, String copyToType, String xml) {
      this(sendTo, copyTo, issueDate, sendToType, copyToType, null, xml);
    }
  }

  /**
   * A document ready to send: its file name, (severed) content and the
   * stylesheet version it renders with (null = the current one).
   */
  public record StatementDocument(String filename, String xml, String transVersion) {

    public StatementDocument(String filename, String xml) {
      this(filename, xml, null);
    }
  }

  private final NamedParameterJdbcTemplate jdbc;
  private final StatementPdfRenderer pdfRenderer;
  private final StatementXslVersion xslVersion;

  public StatementDocumentService(NamedParameterJdbcTemplate jdbc, StatementPdfRenderer pdfRenderer,
      StatementXslVersion xslVersion) {
    this.jdbc = jdbc;
    this.pdfRenderer = pdfRenderer;
    this.xslVersion = xslVersion;
  }

  /**
   * The FOI-severed statement XML for {@code viewer}: 404 for an unknown type or
   * number, 410 when older than seven years / before the 2003-11-01 cut-over.
   */
  public StatementDocument xml(String type, String number, Viewer viewer) {
    StatementType statementType = parseType(type);
    String statementNumber = number == null ? "" : number.trim().toUpperCase();
    if (!NUMBER.matcher(statementNumber).matches()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid statement number was provided");
    }
    Stored stored = load(statementType, statementNumber);
    LocalDate limit = LocalDate.now().minusYears(7);
    if (stored.issueDate() == null || stored.issueDate().isBefore(limit)
        || stored.issueDate().isBefore(HBS_CUTOVER)) {
      throw new ResponseStatusException(HttpStatus.GONE,
          "Statement cannot be retrieved as it is older than seven years");
    }
    return new StatementDocument(statementNumber + ".xml", applyFreedomOfInformation(stored, viewer),
        stored.transVersion());
  }

  /**
   * PDF rendering seam (legacy {@code HbsStatementPdfFactory}). Takes the
   * already-severed XML from {@link #xml}, so the authenticated and the public
   * paths get the same FOI handling.
   */
  public byte[] pdf(StatementDocument severed) {
    String version = severed.transVersion() == null ? xslVersion.current() : severed.transVersion();
    return pdfRenderer.render(severed.xml(), version);
  }

  /**
   * Search for Issued Documents "Generate PDF" ({@code DataDeliveryAction} ACTION_PDF_REPORT): the
   * {@link #combinedXml} transmission rendered with the current stylesheets
   * ({@code HbsStatementPdfFactory.getPDFTransmission(out, reader)} → {@code CURRENT_XSL_VERSION}).
   */
  public byte[] combinedPdf(String combinedXml) {
    return pdfRenderer.render(combinedXml, xslVersion.current());
  }

  /**
   * Search for Issued Documents "Generate XML": every retrievable statement the
   * search listed, FOI-severed for {@code viewer}, merged into one transmission
   * ({@link StatementTransmissionXml}). Rows are the {@code issuedDocuments.search}
   * rows the caller already fetched (and capped at {@link #COMBINED_LIMIT}).
   */
  public String combinedXml(List<Map<String, Object>> rows, Viewer viewer, String requestorEmail) {
    List<StatementTransmissionXml.Item> items = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      if (row.get("documentXmlUrl") == null) {
        continue; // older than seven years / no stored file
      }
      String type = String.valueOf(row.get("documentType")).trim();
      String number = String.valueOf(row.get("statementNumber")).trim();
      items.add(new StatementTransmissionXml.Item(type,
          applyFreedomOfInformation(load(parseType(type), number), viewer)));
    }
    // Legacy DocumentDeliveryManagerBean.createRequestHeader: MOF / Industry, "Public" when anonymous.
    String relationship = viewer.ministry() ? "MOF" : viewer.anonymous() ? "Public" : "Industry";
    var who = new StatementTransmissionXml.Requestor(System.currentTimeMillis(),
        LocalDateTime.now(ZoneId.of("America/Vancouver")), requestorEmail, relationship,
        viewer.ministry() || viewer.anonymous() ? null : viewer.clientNumber());
    String xml = StatementTransmissionXml.merge(who, items);
    try {
      StatementTransmissionXml.validate(xml);
    } catch (org.xml.sax.SAXException ex) {
      // Stored files were valid against the schema version current when issued; deliver anyway.
      log.warn("Combined statement XML doesn't validate against V1_0h: {}", ex.getMessage());
    }
    return xml;
  }

  static StatementType parseType(String type) {
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
              rs.getString("copy_to_type"), trim(rs.getString("trans_version")), clob(rs.getClob("file_object")));
        });
    if (rows.isEmpty() || !StringUtils.hasText(rows.get(0).xml())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Statement Does Not Exist");
    }
    return rows.get(0);
  }

  private static String trim(String s) {
    return s == null ? null : s.trim();
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
  static String applyFreedomOfInformation(Stored s, Viewer viewer) {
    if (viewer.ministry()) {
      return s.xml();
    }
    String client = viewer.clientNumber();
    if (StringUtils.hasText(client) && (client.equals(s.sendTo()) || client.equals(s.copyTo()))) {
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
    Map<String, String> kept = new HashMap<>();
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
