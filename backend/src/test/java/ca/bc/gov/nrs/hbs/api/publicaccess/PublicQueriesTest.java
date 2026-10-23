package ca.bc.gov.nrs.hbs.api.publicaccess;

import ca.bc.gov.nrs.hbs.api.catalog.CodesCatalog;
import ca.bc.gov.nrs.hbs.api.catalog.QueriesCatalog;
import ca.bc.gov.nrs.hbs.api.catalog.QueriesReportsCatalog;
import ca.bc.gov.nrs.hbs.api.catalog.SamplingCatalog;
import ca.bc.gov.nrs.hbs.api.exception.EntityNotFoundException;
import ca.bc.gov.nrs.hbs.api.query.QueryDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter;
import ca.bc.gov.nrs.hbs.api.query.QueryRegistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The public whitelist: derived from the registry, never client-fenced, capped, FOI-severing, legacy form rules. */
class PublicQueriesTest {

  private final PublicQueries publicQueries = new PublicQueries(new QueryRegistry(List.of(
      new QueriesCatalog(), new QueriesReportsCatalog(), new SamplingCatalog(), new CodesCatalog())), 1000);

  @Test
  void everyPublicQueryIsUnfencedAndCapped() {
    assertThat(publicQueries.all()).isNotEmpty();
    for (QueryDefinition q : publicQueries.all()) {
      assertThat(q.clientScope()).as(q.id()).isNull();
      assertThat(q.maxRows()).as(q.id()).isLessThanOrEqualTo(1000);
    }
  }

  /**
   * FOI guard: any public query that selects a client's name, location name or
   * address must sever it through the viewer binds (QueriesReportsCatalog.sever).
   */
  @Test
  void everyPersonalColumnIsSevered() {
    Pattern personal = Pattern.compile("(?i)\\b\\w+\\.(client_name|client_locn_name|address_\\d|postal_code|city)\\b");
    for (QueryDefinition q : publicQueries.all()) {
      if (personal.matcher(q.sql()).find()) {
        assertThat(q.sql()).as(q.id()).contains("'Not Releasable'").contains(":hbsIsMinistry");
        // Every SELECT-list occurrence sits inside a CASE … 'Not Releasable' (count the two the same).
        long cases = Pattern.compile("ELSE 'Not Releasable' END").matcher(q.sql()).results().count();
        long exposed = personal.matcher(q.sql()).results()
            .filter(m -> !m.group().toLowerCase().startsWith("fc.client_number")).count();
        assertThat(cases).as(q.id() + " severed columns").isGreaterThanOrEqualTo(exposed);
      }
    }
  }

  @Test
  void issuedDocumentsDropTheFenceAndTheTransmissionCriterion() {
    QueryDefinition q = publicQueries.query("issuedDocuments.search");
    assertThat(q.filters()).extracting(QueryFilter::param)
        .contains("statementNumber", "documentType", "issueDateFrom")
        .doesNotContain("transmissionId");
  }

  @Test
  void issuedDocumentsNeedADateRangeOrANumber() {
    assertThatThrownBy(() -> publicQueries.validate("issuedDocuments.search", Map.of("documentType", "PSI")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("You must specify either issue or scale date range for your search.");
    assertThatCode(() -> publicQueries.validate("issuedDocuments.search", Map.of("statementNumber", "A123")))
        .doesNotThrowAnyException();
    assertThatCode(() -> publicQueries.validate("issuedDocuments.search", Map.of("issueDateFrom", "2025-01-01")))
        .doesNotThrowAnyException();
  }

  @Test
  void nonPublicQueriesAre404() {
    assertThatThrownBy(() -> publicQueries.query("agedUnbilled.search")).isInstanceOf(EntityNotFoundException.class);
    assertThatThrownBy(() -> publicQueries.query("statementTransmissions.search"))
        .isInstanceOf(EntityNotFoundException.class);
    assertThatThrownBy(() -> publicQueries.validate("billingSummary.search", Map.of()))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void historyRangeFollowsMarkMonthlyBillingAction() {
    LocalDate month = LocalDate.now().withDayOfMonth(1).minusMonths(2);
    // At most one year between the dates as entered.
    assertThatCode(() -> PublicQueries.historyRange(month.minusMonths(12).toString(), month.toString()))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> PublicQueries.historyRange(month.minusMonths(12).minusDays(1).toString(), month.toString()))
        .hasMessage("You may not specify a date range over one year.");
    assertThatThrownBy(() -> PublicQueries.historyRange(month.toString(), month.minusMonths(1).toString()))
        .hasMessage("Billing From date may not be a future date of Billing To date.");
    assertThatThrownBy(() -> PublicQueries.historyRange(month.toString(), LocalDate.now().plusMonths(1).toString()))
        .hasMessage("Billing date may not be a future date.");
    assertThatThrownBy(() -> PublicQueries.historyRange("1990-01-01", "1990-06-01"))
        .hasMessageStartingWith("From Date must be on or after ");
    assertThatThrownBy(() -> PublicQueries.historyRange(null, month.toString()))
        .hasMessage("You must specify billing date range for your search.");
  }

  @Test
  void historyQueriesApplyTheRangeRule() {
    assertThatThrownBy(() -> publicQueries.validate("harvestHistory.scaling",
        Map.of("scalingFrom", "2020-01-01", "scalingTo", "2022-01-01")))
        .hasMessage("You may not specify a date range over one year.");
  }
}
