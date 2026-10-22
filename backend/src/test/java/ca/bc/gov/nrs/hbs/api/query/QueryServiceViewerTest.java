package ca.bc.gov.nrs.hbs.api.query;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The viewer binds a query runs with — the anonymous public viewer makes every FOI CASE sever. */
class QueryServiceViewerTest {

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final QueryService service = new QueryService(jdbc, new QueryRegistry(List.of()));

  private static final QueryDefinition SEVERED = QueryDefinition.builder("t.search")
      .capability(Capability.QUERIES_VIEW)
      .sql("SELECT CASE WHEN :hbsIsMinistry = 'Y' OR fc.client_number = :hbsViewerClient"
          + " THEN fc.client_name ELSE 'Not Releasable' END AS client_name FROM v_client_public fc WHERE 1=1")
      .build();

  @SuppressWarnings("unchecked")
  private MapSqlParameterSource binds() {
    ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
    verify(jdbc).query(anyString(), params.capture(), any(RowMapper.class));
    return (MapSqlParameterSource) params.getValue();
  }

  @Test
  @SuppressWarnings("unchecked")
  void anonymousViewerBindsSeverEverything() {
    when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
    service.list(SEVERED, Viewer.ANONYMOUS, Map.of());
    MapSqlParameterSource p = binds();
    assertThat(p.getValue("hbsIsMinistry")).isEqualTo("N");
    assertThat(p.getValue("hbsViewerClient")).isEqualTo("");
    assertThat(p.getValue("hbsUserType")).isEqualTo("PUB");
    assertThat(p.getValue("hbsUserId")).isEqualTo("");
  }

  @Test
  @SuppressWarnings("unchecked")
  void ministryViewerSeesAll() {
    when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
    service.list(SEVERED, new Viewer(true, "00012345", "MOF", "IDIR\\JSMITH"), Map.of());
    MapSqlParameterSource p = binds();
    assertThat(p.getValue("hbsIsMinistry")).isEqualTo("Y");
    assertThat(p.getValue("hbsViewerClient")).isEqualTo("");
    assertThat(p.getValue("hbsUserId")).isEqualTo("IDIR\\JSMITH");
  }

  @Test
  void clientFencedQueryRefusesTheAnonymousViewer() {
    QueryDefinition fenced = QueryDefinition.builder("t.fenced").capability(Capability.QUERIES_VIEW)
        .sql("SELECT 1 FROM dual WHERE 1=1").clientScope("AND x = :scopeClientNumber").build();
    assertThatThrownBy(() -> service.list(fenced, Viewer.ANONYMOUS, Map.of()))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void anonymousIsRecognised() {
    assertThat(Viewer.ANONYMOUS.anonymous()).isTrue();
    assertThat(new Viewer(false, "00012345", "CLI", "BCEID\\X").anonymous()).isFalse();
  }
}
