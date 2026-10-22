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
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Rebuilds the XML of a statement-delivery transmission exactly as legacy batch
 * B2065 wrote {@code <transmissionId>.xml} into the client's statement drop
 * directory ({@code batch/dad/main/B2065.createDeliveryFiles}), so the links in
 * the statement e-mails ({@code EmailTranmissionDelivery}) can be served from
 * Oracle instead of the file share.
 *
 * <ul>
 *   <li>Root {@code hbs-transmission} with the transmission row's id, client number,
 *       location and recipient type ({@code HbsTransmission} Castor mapping:
 *       {@code transmission-id}, {@code batch-count}, {@code date-created},
 *       {@code time-created}, {@code client-number}, {@code client-location-code},
 *       {@code recipient-type}); no request header.</li>
 *   <li>Each item's stored file ({@code HBS_FILE.FILE_OBJECT}) contributes the first
 *       statement of its batch, merged into one {@code hbs-batch} per statement type
 *       ({@code mergeItemIntoProperBatch}), with {@code batch-type-code} and
 *       {@code document-count}.</li>
 *   <li>Batch order: B2065 collected the batches in a {@code java.util.HashMap}
 *       keyed by statement type and wrote {@code batchCollection.values()}; the same
 *       HashMap on Java 8+ yields the same order (RS, PSV/CSS, PSI, WSV, WSI).</li>
 *   <li>{@code date-created}/{@code time-created}: B2065 stamped the run time when it
 *       wrote the file, right after creating the row; the row's {@code CREATION_DATE}
 *       is used. Time is {@code hh:mm:ss} (12-hour, no AM/PM) as
 *       {@code Query.ORACLE_TIME_FORMAT} wrote it.</li>
 *   <li>Not reproduced: Castor's re-serialisation of each statement (the stored
 *       statement elements are copied as stored) and the {@code xsi:schemaLocation}
 *       pointing at the legacy web server.</li>
 * </ul>
 * No FOI severing: the transmission is the recipient client's own documents and
 * legacy served the stored file as written.
 */
final class DeliveryTransmissionXml {

  static final String NAMESPACE = StatementTransmissionXml.NAMESPACE;

  /** Statement type (HBS_DETERMINE_STATEMENT_TYPE) → collection element in hbs-batch. */
  static final Map<String, String> WRAPPERS = Map.of(
      "PSI", "piece-scale-invoices",
      "PSV", "piece-scale-volume-statements",
      "WSI", "weight-scale-invoices",
      "WSV", "weight-scale-volume-statements",
      "CSS", "compiled-sample-statements",
      "RS", "ratio-statements");

  /** One DOCUMENT_DELIVERY_TRANSMISSION row. */
  record Transmission(long transmissionId, String clientNumber, String clientLocnCode,
      String recipientType, LocalDateTime created) {}

  /** One DOCUMENT_DELIVERY_BATCH_ITEM: its statement type and the stored file. */
  record Item(long hbsFileId, String type, String xml) {}

  private DeliveryTransmissionXml() {
  }

  static String build(Transmission t, List<Item> items) {
    if (items.isEmpty()) {
      throw new IllegalStateException("Transmission " + t.transmissionId() + " has no statements");
    }
    try {
      DocumentBuilder builder = builder();
      Document out = builder.newDocument();
      Element root = out.createElementNS(NAMESPACE, "hbs-transmission");
      out.appendChild(root);

      // B2065: HashMap batchCollection, keyed by the trimmed statement type.
      Map<String, Element> batches = new HashMap<>();
      Map<String, Integer> counts = new HashMap<>();
      for (Item item : items) {
        String type = item.type() == null ? "" : item.type().trim();
        String wrapperName = WRAPPERS.get(type);
        if (wrapperName == null) {
          throw new IllegalStateException("Unknown statement type '" + type + "' for file " + item.hbsFileId());
        }
        if (item.xml() == null || item.xml().isBlank()) {
          throw new IllegalStateException("Can't find the file " + item.hbsFileId());
        }
        Element statement = firstStatement(builder.parse(new InputSource(new StringReader(item.xml()))), wrapperName);
        if (statement == null) {
          throw new IllegalStateException("File " + item.hbsFileId() + " has no " + wrapperName + " statement");
        }
        // get/put, not computeIfAbsent: put appends to a hash bucket as B2065's put did
        // (computeIfAbsent inserts at the head, which reverses colliding types).
        Element wrapper = batches.get(type);
        if (wrapper == null) {
          Element batch = out.createElementNS(NAMESPACE, "hbs-batch");
          wrapper = out.createElementNS(NAMESPACE, wrapperName);
          batch.appendChild(wrapper);
          batches.put(type, wrapper);
        }
        wrapper.appendChild(out.importNode(statement, true));
        counts.merge(type, 1, Integer::sum);
      }

      root.setAttribute("transmission-id", Long.toString(t.transmissionId()));
      root.setAttribute("batch-count", Integer.toString(batches.size()));
      root.setAttribute("date-created", t.created().format(DateTimeFormatter.ISO_LOCAL_DATE));
      root.setAttribute("time-created", t.created().format(DateTimeFormatter.ofPattern("hh:mm:ss")));
      setIfPresent(root, "client-number", t.clientNumber());
      setIfPresent(root, "client-location-code", t.clientLocnCode());
      setIfPresent(root, "recipient-type", t.recipientType());
      batches.forEach((type, wrapper) -> {
        Element batch = (Element) wrapper.getParentNode();
        batch.setAttribute("batch-type-code", type);
        batch.setAttribute("document-count", Integer.toString(counts.get(type)));
        root.appendChild(batch);
      });
      return write(out);
    } catch (ParserConfigurationException | SAXException | IOException | TransformerException ex) {
      throw new IllegalStateException("Error while creating xml for transmission " + t.transmissionId(), ex);
    }
  }

  /** {@code batch.get<Type>s().get<Type>s().iterator().next()}: the first statement of the file's batch. */
  private static Element firstStatement(Document doc, String wrapperName) {
    var batches = doc.getElementsByTagNameNS("*", "hbs-batch");
    if (batches.getLength() == 0) return null;
    for (Node n = batches.item(0).getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n instanceof Element w && wrapperName.equals(w.getLocalName())) {
        for (Node s = w.getFirstChild(); s != null; s = s.getNextSibling()) {
          if (s instanceof Element statement) return statement;
        }
      }
    }
    return null;
  }

  private static void setIfPresent(Element e, String name, String value) {
    if (value != null && !value.isBlank()) {
      e.setAttribute(name, value.trim());
    }
  }

  private static DocumentBuilder builder() throws ParserConfigurationException {
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
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    StringWriter out = new StringWriter();
    transformer.transform(new DOMSource(doc), new StreamResult(out));
    return out.toString();
  }
}
