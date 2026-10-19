package ca.bc.gov.nrs.hbs.api.query;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.springframework.core.io.ClassPathResource;
import org.xml.sax.SAXException;

/**
 * The legacy "Send XML" document for Mark Monthly Billing / Scaling History
 * (P431 / P441): {@code HBS_Billing_History_Request_Schema_v3} and
 * {@code HBS_Scaling_History_Request_Schema_v3}, namespace {@code gov.bc.ca/forests/hbs/v2}
 * (legacy {@code DocumentDeliveryManagerBean.generateHistoryReport} + {@code HbsRequestMapping.mapping}).
 *
 * <p>Element order follows the schemas. Amounts are written without trailing zeros, as
 * legacy did ({@code BigDecimal} from Oracle). FOI-severed names use the legacy XML
 * wording ({@code CLIENT NAME NOT RELEASABLE}). Deliberate differences from legacy:
 * {@code datetime-created} uses a 24-hour clock (legacy wrote 12-hour with no AM/PM),
 * rows are in the screen's sort order (legacy had none), and the document is
 * downloaded rather than e-mailed.
 */
final class HarvestHistoryXml {

  static final String NAMESPACE = "gov.bc.ca/forests/hbs/v2";
  static final String FINE_PRINT = "Note:  While every effort has been made to ensure that the information "
      + "presented in this file is accurate and complete, it cannot be guaranteed.";
  private static final String NOT_RELEASABLE = "Not Releasable";
  private static final DateTimeFormatter CREATED = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

  enum Kind {
    BILLING("mbh", "billing", "mark-monthly-billing-history", "HBS_Billing_History_Request_Schema_v3.xsd"),
    SCALING("msh", "scaling", "mark-monthly-scaling-history", "HBS_Scaling_History_Request_Schema_v3.xsd");

    final String prefix;
    final String period;
    final String rowElement;
    final String schema;

    Kind(String prefix, String period, String rowElement, String schema) {
      this.prefix = prefix;
      this.period = period;
      this.rowElement = rowElement;
      this.schema = schema;
    }
  }

  /** Header values, already turned into the legacy display text ("All", "All Land", …). */
  record Header(
      long requestId, LocalDateTime created, String requestorEmail, String relationship, String requestorClient,
      YearMonth from, YearMonth to, String regionDistrictHarvested, String markHolderClient,
      String markHolderLocation, String fileType, String mgmtUnitType, String mgmtUnitId, String forestFileId,
      String timberMarks, String regionDistrictScaled, String scaleSite, String species, String product,
      String grade, String billingType) {
  }

  private HarvestHistoryXml() {
  }

  static String write(Kind kind, Header h, List<Map<String, Object>> rows) {
    StringWriter out = new StringWriter();
    try {
      XMLStreamWriter w = XMLOutputFactory.newFactory().createXMLStreamWriter(out);
      w.writeStartDocument("UTF-8", "1.0");
      w.writeStartElement("hbs-request");
      w.writeDefaultNamespace(NAMESPACE);
      header(w, kind, h, rows.size());
      for (Map<String, Object> row : rows) {
        row(w, kind, h.requestId(), row);
      }
      w.writeEndElement();
      w.writeEndDocument();
      w.close();
    } catch (XMLStreamException ex) {
      throw new IllegalStateException("Could not write the history XML", ex);
    }
    return out.toString();
  }

  /** Validates against the vendored legacy schema; the message names the first problem. */
  static void validate(Kind kind, String xml) throws SAXException {
    try {
      SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      Schema schema = factory.newSchema(new StreamSource(
          new ClassPathResource("xml/history/" + kind.schema).getInputStream()));
      schema.newValidator().validate(new StreamSource(new StringReader(xml)));
    } catch (IOException ex) {
      throw new IllegalStateException("History XML schema missing: " + kind.schema, ex);
    }
  }

  private static void header(XMLStreamWriter w, Kind kind, Header h, int count) throws XMLStreamException {
    String p = kind.period;
    w.writeStartElement("xml-" + kind.prefix + "-request-header");
    el(w, kind.prefix + "-request-id", Long.toString(h.requestId()));
    el(w, "datetime-created", CREATED.format(h.created()));
    el(w, "doc-count", Integer.toString(count));
    el(w, "requestor-email", h.requestorEmail());
    el(w, "requestor-relationship", h.relationship());
    el(w, "requestor-client", h.requestorClient());
    el(w, "requested-from-" + p + "-year", Integer.toString(h.from().getYear()));
    el(w, "requested-from-" + p + "-month", Integer.toString(h.from().getMonthValue()));
    el(w, "requested-to-" + p + "-year", Integer.toString(h.to().getYear()));
    el(w, "requested-to-" + p + "-month", Integer.toString(h.to().getMonthValue()));
    el(w, "requested-region-district-harvested", h.regionDistrictHarvested());
    el(w, "requested-mark-holder-client-number", h.markHolderClient());
    el(w, "requested-mark-holder-client-location", h.markHolderLocation());
    el(w, "requested-file-type", h.fileType());
    el(w, "requested-mgmt-unit-type", h.mgmtUnitType());
    el(w, "requested-mgmt-unit-id", h.mgmtUnitId());
    el(w, "requested-forest-file-id", h.forestFileId());
    el(w, "requested-timber-mark", h.timberMarks());
    if (kind == Kind.SCALING) {
      el(w, "requested-region-district-scaled", h.regionDistrictScaled());
      el(w, "requested-scale-site", h.scaleSite());
    }
    el(w, "requested-species", h.species());
    el(w, "requested-product", h.product());
    el(w, "requested-grade", h.grade());
    el(w, "requested-billing-type", h.billingType());
    el(w, "fine-print", FINE_PRINT);
    w.writeEndElement();
  }

  private static void row(XMLStreamWriter w, Kind kind, long requestId, Map<String, Object> r)
      throws XMLStreamException {
    boolean billing = kind == Kind.BILLING;
    YearMonth period = billing
        ? yearMonth(r.get("billingYear"), r.get("billingMonth"), r.get("billingPeriod"))
        : yearMonth(null, null, r.get("scalingPeriod"));
    w.writeStartElement(kind.rowElement);
    el(w, kind.prefix + "-request-id", Long.toString(requestId));
    el(w, kind.period + "-year", period == null ? null : Integer.toString(period.getYear()));
    el(w, kind.period + "-month", period == null ? null : Integer.toString(period.getMonthValue()));
    el(w, "billing-type", text(r, "billingTypeCode"));
    el(w, "timber-mark", text(r, "timberMark"));
    if (!billing) {
      el(w, "scale-site", text(r, "scaleSite"));
    }
    el(w, "species-code", text(r, "speciesCode"));
    el(w, "product-code", text(r, "productCode"));
    el(w, "grade-code", text(r, "gradeCode"));
    el(w, "total-amount-" + (billing ? "billed" : "scaled"), number(r.get(billing ? "totalAmountBilled" : "totalAmount")));
    el(w, "total-volume-" + (billing ? "billed" : "scaled"), number(r.get(billing ? "totalVolumeBilled" : "volumeScaled")));
    el(w, "billing-type-description", or(text(r, "billingTypeDescription"), text(r, "billingTypeCode")));
    el(w, "species-description", or(text(r, "speciesDescription"), text(r, "speciesCode")));
    el(w, "product-description", or(text(r, "productDescription"), text(r, "productCode")));
    el(w, "grade-description", or(text(r, "gradeDescription"), text(r, "gradeCode")));
    el(w, "region-harvested-code", text(r, "regionHarvestedCode"));
    el(w, "region-harvested-description", text(r, "regionHarvestedName"));
    el(w, "district-harvested-code", text(r, "districtHarvestedCode"));
    el(w, "district-harvested-description", text(r, "districtHarvestedName"));
    el(w, "forest-file-id", text(r, "forestFileId"));
    el(w, "file-type-code", text(r, "fileTypeCode"));
    el(w, "file-type-description", or(text(r, "fileTypeDescription"), text(r, "fileTypeCode")));
    el(w, "mgmt-unit-type-code", text(r, "mgmtUnitType"));
    el(w, "mgmt-unit-type-description", text(r, "mgmtUnitTypeDescription"));
    el(w, "mgmt-unit-id", text(r, "mgmtUnitId"));
    el(w, "mark-holder-client-type-code", text(r, "clientTypeCode"));
    el(w, "mark-holder-client-type-description", or(text(r, "clientTypeDescription"), text(r, "clientTypeCode")));
    el(w, "mark-holder-client-number", text(r, "clientNumber"));
    el(w, "mark-holder-client-name", severedName(text(r, "clientName")));
    el(w, "mark-holder-client-location-code", text(r, "clientLocnCode"));
    el(w, "mark-holder-client-location-name", severedLocation(text(r, "clientLocnName")));
    if (!billing) {
      el(w, "region-scaled-code", text(r, "regionScaledCode"));
      el(w, "region-scaled-description", text(r, "regionScaledName"));
      el(w, "district-scaled-code", text(r, "districtScaledCode"));
      el(w, "district-scaled-description", text(r, "districtScaledName"));
      el(w, "scale-site-name", text(r, "siteName"));
      el(w, "site-owner-client-type-code", text(r, "siteOwnerClientTypeCode"));
      el(w, "site-owner-client-type-description",
          or(text(r, "siteOwnerClientTypeDescription"), text(r, "siteOwnerClientTypeCode")));
      el(w, "site-owner-client-number", text(r, "siteOwnerClientNumber"));
      el(w, "site-owner-client-name", severedName(text(r, "siteOwnerClientName")));
      el(w, "site-owner-client-location-code", text(r, "siteOwnerClientLocnCode"));
      el(w, "site-owner-client-location-name", severedLocation(text(r, "siteOwnerClientLocnName")));
    }
    w.writeEndElement();
  }

  /** Writes the element only when it has a value (legacy Castor omitted nulls). */
  private static void el(XMLStreamWriter w, String name, String value) throws XMLStreamException {
    if (value == null || value.isEmpty()) {
      return;
    }
    w.writeStartElement(name);
    w.writeCharacters(value);
    w.writeEndElement();
  }

  /** Codes are kept as stored (a log product code is two spaces); blank-only names are dropped. */
  private static String text(Map<String, Object> r, String key) {
    Object v = r.get(key);
    if (v == null) return null;
    String s = v.toString();
    return key.endsWith("Code") ? s : (s.isBlank() ? null : s.trim());
  }

  private static String or(String value, String fallback) {
    return value != null ? value : fallback;
  }

  private static String number(Object v) {
    if (v == null) return "0";
    BigDecimal d = v instanceof BigDecimal b ? b : new BigDecimal(v.toString());
    return d.signum() == 0 ? "0" : d.stripTrailingZeros().toPlainString();
  }

  private static String severedName(String name) {
    return NOT_RELEASABLE.equals(name) ? "CLIENT NAME NOT RELEASABLE" : name;
  }

  private static String severedLocation(String name) {
    return NOT_RELEASABLE.equals(name) ? "CLIENT LOCATION NAME NOT RELEASABLE" : name;
  }

  private static YearMonth yearMonth(Object year, Object month, Object period) {
    if (year != null && month != null) {
      return YearMonth.of(Integer.parseInt(year.toString().trim()), Integer.parseInt(month.toString().trim()));
    }
    if (period == null) return null;
    String s = period.toString();
    return YearMonth.from(LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s));
  }
}
