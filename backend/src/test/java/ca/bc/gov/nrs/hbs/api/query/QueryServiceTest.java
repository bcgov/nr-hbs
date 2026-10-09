package ca.bc.gov.nrs.hbs.api.query;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryServiceTest {

  @Test
  void camel_convertsOracleLabels() {
    assertThat(QueryService.camel("TIMBER_MARK")).isEqualTo("timberMark");
    assertThat(QueryService.camel("document_control_number")).isEqualTo("documentControlNumber");
    assertThat(QueryService.camel("CODE")).isEqualTo("code");
    assertThat(QueryService.camel("alreadyCamel")).isEqualTo("alreadyCamel");
  }

  @Test
  void convert_appliesFilterTypes() {
    assertThat(QueryService.convert("m", " ab12 ".trim(), QueryFilter.Type.UPPER)).isEqualTo("AB12");
    assertThat(QueryService.convert("m", "ab*c", QueryFilter.Type.LIKE)).isEqualTo("%AB%C%");
    assertThat(QueryService.convert("m", "ab", QueryFilter.Type.PREFIX)).isEqualTo("AB%");
    assertThat(QueryService.convert("n", "12.5", QueryFilter.Type.NUMBER)).isEqualTo(new BigDecimal("12.5"));
    assertThat(QueryService.convert("d", "2024-02-29", QueryFilter.Type.DATE))
        .isEqualTo(java.sql.Date.valueOf("2024-02-29"));
    // DATE_TO is exclusive next day so "to" dates are inclusive for users.
    assertThat(QueryService.convert("d", "2024-02-29", QueryFilter.Type.DATE_TO))
        .isEqualTo(java.sql.Date.valueOf("2024-03-01"));
    assertThat(QueryService.convert("l", "p, w ,", QueryFilter.Type.LIST)).isEqualTo(List.of("P", "W"));
  }

  @Test
  void convert_rejectsBadValuesAs400() {
    assertThatThrownBy(() -> QueryService.convert("from", "2024-13-01", QueryFilter.Type.DATE))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("from");
    assertThatThrownBy(() -> QueryService.convert("n", "abc", QueryFilter.Type.NUMBER))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void builder_rejectsNonSelect() {
    assertThatThrownBy(() -> QueryDefinition.builder("x").sql("DELETE FROM t").build())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void commandBuilder_rejectsInjectedProcedureName() {
    assertThatThrownBy(() -> CommandDefinition.builder("x").capability(Capability.MINISTRY)
        .procedure("HBS_X; DROP TABLE Y").build())
        .isInstanceOf(IllegalStateException.class);
  }
}
