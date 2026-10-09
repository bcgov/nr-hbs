package ca.bc.gov.nrs.hbs.api.submission;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates submitted detail scale data against {@code HBS_Schema_V6_1b.xsd}
 * (namespace {@code gov.bc.ca/forests/hbs/v3}) — the only schema the legacy
 * {@code Resolver.properties} mapped, so the only one P505 actually accepted.
 * The legacy v1→v2 namespace rewrite is not carried over: v1/v2 documents
 * already failed validation in the legacy app (no schema mapped for them).
 *
 * <p>Hardened against XXE: no DTDs, no external entities or schemas.
 */
@Component
public class ScaleDataXmlValidator {

  public static final String NAMESPACE = "gov.bc.ca/forests/hbs/v3";
  private static final int MAX_ERRORS = 50;

  private final Schema schema;

  public ScaleDataXmlValidator() throws SAXException, IOException {
    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    try (var in = new ClassPathResource("schemas/scale/HBS_Schema_V6_1b.xsd").getInputStream()) {
      this.schema = factory.newSchema(new StreamSource(in));
    }
  }

  /** @return validation messages (empty = valid). */
  public List<String> validate(byte[] xml) {
    List<String> errors = new ArrayList<>();
    String head = new String(xml, 0, Math.min(xml.length, 4096), StandardCharsets.UTF_8);
    if (head.contains("<!DOCTYPE")) {
      return List.of("DOCTYPE declarations are not allowed.");
    }
    if (!head.contains(NAMESPACE)) {
      return List.of("The file must use the HBS scale data namespace " + NAMESPACE
          + " (HBS_Schema_V6_1b). Older schema versions are no longer accepted.");
    }
    try {
      Validator validator = schema.newValidator();
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      validator.setErrorHandler(new org.xml.sax.ErrorHandler() {
        @Override public void warning(SAXParseException e) { /* ignore */ }
        @Override public void error(SAXParseException e) { add(e); }
        @Override public void fatalError(SAXParseException e) throws SAXParseException { add(e); throw e; }
        private void add(SAXParseException e) {
          if (errors.size() < MAX_ERRORS) {
            errors.add("Line " + e.getLineNumber() + ", column " + e.getColumnNumber() + ": " + e.getMessage());
          }
        }
      });
      validator.validate(new StreamSource(new ByteArrayInputStream(xml)));
    } catch (SAXException e) {
      if (errors.isEmpty()) errors.add(e.getMessage());
    } catch (IOException e) {
      errors.add("The file could not be read.");
    }
    return errors;
  }
}
