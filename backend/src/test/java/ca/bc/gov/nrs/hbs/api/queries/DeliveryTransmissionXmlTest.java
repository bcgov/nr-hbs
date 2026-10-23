package ca.bc.gov.nrs.hbs.api.queries;

import ca.bc.gov.nrs.hbs.api.queries.DeliveryTransmissionXml.Item;
import ca.bc.gov.nrs.hbs.api.queries.DeliveryTransmissionXml.Transmission;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static ca.bc.gov.nrs.hbs.api.queries.StatementPdfRendererTest.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** B2065.createDeliveryFiles: a delivery transmission rebuilt from its items' stored files. */
class DeliveryTransmissionXmlTest {

  private static final Transmission T = new Transmission(
      4711L, "00012345", "00", "SEND_TO", LocalDateTime.of(2026, 10, 16, 14, 5, 9));

  /** A fixture whose (single) statement number is replaced, to follow it through the merge. */
  private static String stored(String type, String number) {
    return fixture(type).replaceFirst("<statement-number>\\d{7}</statement-number>",
        "<statement-number>" + number + "</statement-number>");
  }

  private static List<String> batchOrder(String xml) {
    List<String> order = new ArrayList<>();
    Matcher m = Pattern.compile("<hbs-batch batch-type-code=\"([A-Z]+)\" document-count=\"(\\d+)\"").matcher(xml);
    while (m.find()) order.add(m.group(1) + "=" + m.group(2));
    return order;
  }

  @Test
  void mergesOneBatchPerTypeInLegacyHashMapOrder() throws Exception {
    String xml = DeliveryTransmissionXml.build(T, List.of(
        new Item(1, "PSI", stored("PSI", "P000001")),
        new Item(2, "WSI", stored("WSI", "W000001")),
        new Item(3, "PSI ", stored("PSI", "P000002")),
        new Item(4, "RS", stored("RS", "R000001")),
        new Item(5, "PSV", stored("PSV", "V000001")),
        new Item(6, "CSS", stored("CSS", "C000001")),
        new Item(7, "WSV", stored("WSV", "X000001"))));

    // java.util.HashMap order of the type keys, as B2065's batchCollection.values().
    assertThat(batchOrder(xml)).containsExactly("RS=1", "PSV=1", "CSS=1", "PSI=2", "WSV=1", "WSI=1");
    assertThat(xml)
        .contains("transmission-id=\"4711\"", "batch-count=\"6\"", "date-created=\"2026-10-16\"",
            "time-created=\"02:05:09\"", "client-number=\"00012345\"", "client-location-code=\"00\"",
            "recipient-type=\"SEND_TO\"")
        .doesNotContain("hbs-request-header");
    // Both PSIs in the one PSI batch, in item order.
    assertThat(xml.indexOf("P000001")).isPositive().isLessThan(xml.indexOf("P000002"));
    assertThat(xml.split("<piece-scale-invoices", -1)).hasSize(2);
    StatementTransmissionXml.validate(xml);
  }

  @Test
  void collidingTypesKeepItemOrderWithinTheirBucket() {
    String xml = DeliveryTransmissionXml.build(T, List.of(
        new Item(1, "CSS", stored("CSS", "C000001")),
        new Item(2, "PSV", stored("PSV", "V000001"))));
    assertThat(batchOrder(xml)).containsExactly("CSS=1", "PSV=1");
  }

  @Test
  void takesOnlyTheFirstStatementOfEachFile() {
    String two = stored("PSI", "P000001").replace("</piece-scale-invoices>",
        stored("PSI", "P000009").replaceAll("(?s).*<piece-scale-invoices>(.*)</piece-scale-invoices>.*", "$1")
            + "</piece-scale-invoices>");
    assertThat(two).contains("P000009");
    String xml = DeliveryTransmissionXml.build(T, List.of(new Item(1, "PSI", two)));
    assertThat(xml).contains("P000001").doesNotContain("P000009");
    assertThat(batchOrder(xml)).containsExactly("PSI=1");
  }

  @Test
  void copyToTransmissionWithoutLocation() {
    var copyTo = new Transmission(9L, "00099999", null, "COPY_TO", LocalDateTime.of(2026, 1, 2, 0, 30, 0));
    String xml = DeliveryTransmissionXml.build(copyTo, List.of(new Item(1, "RS", fixture("RS"))));
    assertThat(xml).contains("recipient-type=\"COPY_TO\"", "time-created=\"12:30:00\"")
        .doesNotContain("client-location-code");
  }

  @Test
  void itemOfTheWrongTypeOrWithoutFileFails() {
    assertThatThrownBy(() -> DeliveryTransmissionXml.build(T, List.of(new Item(1, "WSI", fixture("PSI")))))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("weight-scale-invoices");
    assertThatThrownBy(() -> DeliveryTransmissionXml.build(T, List.of(new Item(1, "XXX", fixture("PSI")))))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> DeliveryTransmissionXml.build(T, List.of(new Item(1, "PSI", null))))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> DeliveryTransmissionXml.build(T, List.of()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void transmissionPdfHasTheCoverSheet() {
    System.setProperty("java.awt.headless", "true");
    String xml = DeliveryTransmissionXml.build(T, List.of(
        new Item(1, "PSI", stored("PSI", "P000001")),
        new Item(2, "RS", stored("RS", "R000001"))));
    String text = PdfText.of(new StatementPdfRenderer().render(xml, "V1_0h"));
    assertThat(text).contains("HBSTransmission", "Transmission:", "4711", "TransmissionDetails",
        "ClientNumber/Location:", "00012345", "P000001", "R000001");
  }
}
