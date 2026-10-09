package ca.bc.gov.nrs.hbs.api.submission;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ScaleDataXmlValidatorTest {

  private final ScaleDataXmlValidator validator;

  ScaleDataXmlValidatorTest() throws Exception {
    validator = new ScaleDataXmlValidator();
  }

  @Test
  void rejectsWrongNamespace() {
    var errors = validator.validate("<HBS xmlns=\"gov.bc.ca/forests/hbs/v2\"/>".getBytes(StandardCharsets.UTF_8));
    assertThat(errors).singleElement().asString().contains("HBS_Schema_V6_1b");
  }

  @Test
  void rejectsDoctype_xxeHardening() {
    var xml = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"
        + "<x xmlns=\"gov.bc.ca/forests/hbs/v3\">&e;</x>";
    assertThat(validator.validate(xml.getBytes(StandardCharsets.UTF_8))).isNotEmpty();
  }

  @Test
  void reportsSchemaErrorsWithLineNumbers() {
    var xml = "<NotARealRoot xmlns=\"gov.bc.ca/forests/hbs/v3\"/>";
    var errors = validator.validate(xml.getBytes(StandardCharsets.UTF_8));
    assertThat(errors).isNotEmpty();
    assertThat(errors.get(0)).startsWith("Line ");
  }
}
