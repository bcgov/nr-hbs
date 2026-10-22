package ca.bc.gov.nrs.hbs.api.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Server-side binds reach only the queries that select them, and a request can't override them. */
class QueryServiceServerBindsTest {

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final QueryBinds settings = new QueryBinds() {
    @Override
    public String prefix() {
      return "hbsSetting";
    }

    @Override
    public Map<String, Object> values() {
      return Map.of("hbsSetting_ALERT_TEXT", "Down Saturday");
    }
  };
  private final QueryService service = new QueryService(jdbc, new QueryRegistry(List.of()), List.of(settings));

  @SuppressWarnings("unchecked")
  private MapSqlParameterSource binds(QueryDefinition q, Map<String, String> request) {
    service.list(q, Viewer.ANONYMOUS, request);
    ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(jdbc).query(anyString(), params.capture(), any(RowMapper.class));
    return params.getValue();
  }

  @Test
  void boundForAQueryThatSelectsThem() {
    QueryDefinition q = QueryDefinition.builder("t.alert")
        .sql("SELECT :hbsSetting_ALERT_TEXT AS alert_text FROM dual WHERE 1=1").build();
    assertThat(binds(q, Map.of("hbsSetting_ALERT_TEXT", "spoofed")).getValue("hbsSetting_ALERT_TEXT"))
        .isEqualTo("Down Saturday");
  }

  @Test
  void notBoundForOtherQueries() {
    QueryDefinition q = QueryDefinition.builder("t.other").sql("SELECT 1 AS n FROM dual WHERE 1=1").build();
    assertThat(binds(q, Map.of()).hasValue("hbsSetting_ALERT_TEXT")).isFalse();
  }
}
