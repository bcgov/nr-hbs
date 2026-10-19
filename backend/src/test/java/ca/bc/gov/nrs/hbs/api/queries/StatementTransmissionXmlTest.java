package ca.bc.gov.nrs.hbs.api.queries;

import ca.bc.gov.nrs.hbs.api.queries.StatementTransmissionXml.Item;
import ca.bc.gov.nrs.hbs.api.queries.StatementTransmissionXml.Requestor;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Issued Documents "Generate XML": stored statements merged one batch per type (legacy mergeItemIntoProperBatch). */
class StatementTransmissionXmlTest {

  private static final Requestor WHO =
      new Requestor(1760600000000L, LocalDateTime.of(2026, 10, 16, 14, 5, 9), "a@gov.bc.ca", "MOF", null);

  private static String stored(String wrapper, String item, String number) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <hbs-transmission xmlns="gov.bc.ca/forests/hbs/statement/v1" transmission-id="9" batch-count="1"
            date-created="2025-01-01" time-created="10:00:00">
          <hbs-batch batch-type-code="X" document-count="1">
            <%1$s><%2$s><header><statement-number>%3$s</statement-number></header></%2$s></%1$s>
          </hbs-batch>
        </hbs-transmission>""".formatted(wrapper, item, number).strip();
  }

  @Test
  void mergesOneBatchPerTypeWithCounts() {
    String xml = StatementTransmissionXml.merge(WHO, List.of(
        new Item("PSI", stored("piece-scale-invoices", "piece-scale-invoice", "P1")),
        new Item("WSI", stored("weight-scale-invoices", "weight-scale-invoice", "W1")),
        new Item("PSI", stored("piece-scale-invoices", "piece-scale-invoice", "P2"))));
    assertThat(xml)
        .contains("transmission-id=\"1760600000000\"")
        .contains("batch-count=\"2\"")
        .contains("date-created=\"2026-10-16\"")
        .contains("time-created=\"14:05:09\"")
        .contains("<requestor-relationship>MOF</requestor-relationship>")
        .contains(StatementTransmissionXml.FINE_PRINT)
        .doesNotContain("requestor-client>")
        .containsPattern("batch-type-code=\"PSI\"[^>]*document-count=\"2\"|document-count=\"2\"[^>]*batch-type-code=\"PSI\"")
        .containsPattern("batch-type-code=\"WSI\"[^>]*document-count=\"1\"|document-count=\"1\"[^>]*batch-type-code=\"WSI\"")
        .contains("<statement-number>P1</statement-number>")
        .contains("<statement-number>P2</statement-number>")
        .contains("<statement-number>W1</statement-number>");
    // P1 and P2 share the PSI batch's single piece-scale-invoices wrapper.
    assertThat(xml.split("<piece-scale-invoices", -1)).hasSize(2);
    assertThat(xml).doesNotContain("xmlns=\"\"");
  }

  @Test
  void nothingRetrievableIsRefused() {
    assertThatThrownBy(() -> StatementTransmissionXml.merge(WHO, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
