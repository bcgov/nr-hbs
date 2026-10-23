package ca.bc.gov.nrs.hbs.api.queries;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Search for Issued Documents "Generate XML" (legacy {@code DataDeliveryAction} →
 * {@code DocumentDeliveryManagerBean.generateStatementReport}): every listed statement's
 * stored XML (already FOI-severed) merged into one {@code hbs-transmission}
 * ({@code HBS_Transmission_Schema_V1_0h}, namespace {@code gov.bc.ca/forests/hbs/statement/v1})
 * with one {@code hbs-batch} per document type ({@code mergeItemIntoProperBatch}) and a
 * request header.
 */
final class StatementTransmissionXml {

  static final String NAMESPACE = "gov.bc.ca/forests/hbs/statement/v1";
  static final String SCHEMA = "xml/statement/HBS_Transmission_Schema_V1_0h.xsd";
  /** Legacy HbsRequestHeader.finePrint. */
  static final String FINE_PRINT = "Data items severed because of FOIPP concerns have been "
      + "replaced with the value ***** or omitted.";

  /** One statement: its type (PSI, PSV, WSI, WSV, CSS, RS) and stored, severed XML. */
  record Item(String type, String xml) {}

  /** Request header values; relationship is MOF / Industry, client blank for ministry. */
  record Requestor(long requestId, LocalDateTime created, String email, String relationship, String client) {}

  private StatementTransmissionXml() {
  }

  static String merge(Requestor who, List<Item> items) {
    if (items.isEmpty()) {
      throw new IllegalArgumentException("None of the listed documents can be retrieved, so there is nothing to download");
    }
    try {
      Document out = builder().newDocument();
      Element root = out.createElementNS(NAMESPACE, "hbs-transmission");
      out.appendChild(root);
      String date = who.created().format(DateTimeFormatter.ISO_LOCAL_DATE);
      String time = who.created().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
      root.setAttribute("transmission-id", Long.toString(who.requestId()));
      root.setAttribute("date-created", date);
      root.setAttribute("time-created", time);

      Element header = child(out, root, "hbs-request-header", null);
      child(out, header, "request-id", Long.toString(who.requestId()));
      child(out, header, "date-created", date);
      child(out, header, "time-created", time);
      child(out, header, "requestor-email", who.email());
      child(out, header, "requestor-relationship", who.relationship());
      child(out, header, "requestor-client", who.client());
      child(out, header, "fine-print", FINE_PRINT);

      // Legacy collected the batches in a HashMap keyed by type and wrote values():
      // the same HashMap gives the same batch order (see DeliveryTransmissionXml).
      Map<String, Element> wrappers = new HashMap<>();
      Map<String, Integer> counts = new HashMap<>();
      for (Item item : items) {
        Element source = firstWrapper(builder().parse(new InputSource(new StringReader(item.xml()))));
        if (source == null) {
          continue;
        }
        Element wrapper = wrappers.get(item.type());
        if (wrapper == null) { // get/put keeps legacy's bucket order (see DeliveryTransmissionXml)
          Element batch = out.createElementNS(NAMESPACE, "hbs-batch");
          batch.setAttribute("batch-type-code", item.type());
          wrapper = child(out, batch, source.getLocalName(), null);
          wrappers.put(item.type(), wrapper);
        }
        for (Node n = source.getFirstChild(); n != null; n = n.getNextSibling()) {
          if (n instanceof Element statement) {
            wrapper.appendChild(out.importNode(statement, true));
            counts.merge(item.type(), 1, Integer::sum);
          }
        }
      }
      wrappers.forEach((type, wrapper) -> {
        Element batch = (Element) wrapper.getParentNode();
        batch.setAttribute("document-count", Integer.toString(counts.getOrDefault(type, 0)));
        root.appendChild(batch);
      });
      root.setAttribute("batch-count", Integer.toString(wrappers.size()));
      return write(out);
    } catch (ParserConfigurationException | SAXException | IOException | TransformerException ex) {
      throw new IllegalStateException("Could not combine the statement XML", ex);
    }
  }

  static void validate(String xml) throws SAXException {
    try {
      SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      factory.newSchema(new StreamSource(new ClassPathResource(SCHEMA).getInputStream()))
          .newValidator().validate(new StreamSource(new StringReader(xml)));
    } catch (IOException ex) {
      throw new IllegalStateException("Statement schema missing: " + SCHEMA, ex);
    }
  }

  /** The {@code piece-scale-invoices} / … element inside the stored file's (single) batch. */
  private static Element firstWrapper(Document doc) {
    var batches = doc.getElementsByTagNameNS("*", "hbs-batch");
    if (batches.getLength() == 0) return null;
    for (Node n = batches.item(0).getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n instanceof Element e) return e;
    }
    return null;
  }

  private static Element child(Document doc, Element parent, String name, String text) {
    if (text != null && text.isBlank()) return null;
    Element e = doc.createElementNS(NAMESPACE, name);
    if (text != null) e.setTextContent(text);
    parent.appendChild(e);
    return e;
  }

  private static javax.xml.parsers.DocumentBuilder builder() throws ParserConfigurationException {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    dbf.setExpandEntityReferences(false);
    return dbf.newDocumentBuilder();
  }

  private static String write(Document doc) throws TransformerException {
    var tf = TransformerFactory.newInstance();
    tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
    var transformer = tf.newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    StringWriter out = new StringWriter();
    transformer.transform(new DOMSource(doc), new StreamResult(out));
    return out.toString();
  }
}
