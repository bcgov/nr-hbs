package ca.bc.gov.nrs.hbs.api.queries;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Statement PDFs (legacy HbsStatementPdfFactory): every statement type renders
 * through the ported XSL-FO stylesheets on FOP 2.x. The fixtures in
 * src/test/resources/statements are schema-valid (V1_0h) statements with every
 * element and attribute filled.
 */
class StatementPdfRendererTest {

  private static StatementPdfRenderer renderer;

  @BeforeAll
  static void setUp() {
    System.setProperty("java.awt.headless", "true");
    renderer = new StatementPdfRenderer();
  }

  static String fixture(String type) {
    try (InputStream in = StatementPdfRendererTest.class.getResourceAsStream("/statements/" + type + ".xml")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new IllegalStateException(ex);
    }
  }

  static Stream<Arguments> statementTypes() {
    return Stream.of(
        Arguments.of("PSI", List.of("MinistryofForests", "PieceScale", "Invoice:", "1000001")),
        Arguments.of("PSV", List.of("MinistryofForests", "PieceScale", "VolumeStatement", "Statement:")),
        Arguments.of("WSI", List.of("MinistryofForests", "WeightScale", "Invoice:")),
        Arguments.of("WSV", List.of("MinistryofForests", "WeightScale", "VolumeStatement")),
        Arguments.of("CSS", List.of("MinistryofForests", "CompiledSample", "Statement:")),
        Arguments.of("RS", List.of("MinistryofForests", "RatioStatement")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("statementTypes")
  void rendersEveryStatementType(String type, List<String> expected) throws Exception {
    String xml = fixture(type);
    StatementTransmissionXml.validate(xml);

    byte[] pdf = renderer.render(xml, "V1_0h");

    assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    assertThat(pdf.length).isGreaterThan(5_000);
    String text = PdfText.of(pdf);
    assertThat(text).contains(expected);
    assertThat(text).contains("WESTERNFORESTPRODUCTSINC");
  }

  /** piece-scale-invoice.xsl lays the segregation section out per return category. */
  @ParameterizedTest
  @ValueSource(strings = {"Field Scale", "Primary Scale", "Cruise Based", "Other", "Beachcomb", "Waste"})
  void pieceScaleInvoiceRendersEveryReturnCategoryLayout(String category) {
    String xml = fixture("PSI").replace("description=\"Primary Scale\"", "description=\"" + category + "\"");
    String text = PdfText.of(renderer.render(xml, "V1_0h"));
    assertThat(text).contains("Invoice:", "1000001", "Notations", "Ratesasperappraisal");
  }

  @Test
  void severedStatementShowsNotReleasable() {
    String severed = StatementDocumentService.sever(fixture("PSI"), true, true);
    String text = PdfText.of(renderer.render(severed, "V1_0h"));
    assertThat(text).contains("NOTRELEASABLE").doesNotContain("1500TIMBERWAY");
  }

  /** Older statements are rendered with the stylesheets of their own version. */
  @ParameterizedTest
  @ValueSource(strings = {"V1_0", "V1_0a", "V1_0b", "V1_0c", "V1_0d", "V1_0e", "V1_0f", "V1_0g", "V1_0h", "V999_0"})
  void everyShippedVersionCompilesAndRenders(String version) {
    assertThat(renderer.supports(version)).isTrue();
    byte[] pdf = renderer.render(fixture("PSV"), version);
    assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
  }

  @Test
  void unknownOrUnsafeVersionsAreRefused() {
    assertThat(renderer.supports("V9_9z")).isFalse();
    assertThat(renderer.supports("../conf")).isFalse();
    assertThat(renderer.supports(null)).isFalse();
    assertThatThrownBy(() -> renderer.render(fixture("PSI"), "../../etc"))
        .isInstanceOf(IllegalStateException.class);
  }
}
