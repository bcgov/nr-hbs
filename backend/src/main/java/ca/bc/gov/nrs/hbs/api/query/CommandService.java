package ca.bc.gov.nrs.hbs.api.query;

import ca.bc.gov.nrs.hbs.api.dao.v1.StoredProcedureException;
import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Executes registry {@link CommandDefinition}s — one positional call to a
 * legacy table-API proc. Returns the generated surrogate keys (sequence
 * arguments) so the SPA can navigate to the new record.
 *
 * <p>Each command is its own transaction, matching the legacy app (which ran
 * every proc call in autocommit; see docs/database.md, "Transactions").
 */
@Service
@Slf4j
public class CommandService {

  private final JdbcTemplate jdbc;
  private final QueryRegistry registry;
  private final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate named;

  public CommandService(JdbcTemplate jdbc, QueryRegistry registry) {
    this.jdbc = jdbc;
    this.registry = registry;
    this.named = new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc);
  }

  /** The record's current row (camelCase keys, raw JDBC values) for EXISTING args. */
  private Map<String, Object> existingRow(CommandDefinition cmd, Map<String, Object> in) {
    var params = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource();
    in.forEach((k, v) -> params.addValue(k, v == null ? null : v.toString()));
    var rows = named.query(cmd.existingRowSql(), params, (rs, n) -> {
      Map<String, Object> row = new LinkedHashMap<>();
      var md = rs.getMetaData();
      for (int c = 1; c <= md.getColumnCount(); c++) {
        row.put(QueryService.camel(md.getColumnLabel(c)), rs.getObject(c));
      }
      return row;
    });
    if (rows.isEmpty()) {
      throw new ca.bc.gov.nrs.hbs.api.exception.EntityNotFoundException(CommandDefinition.class, "command", cmd.id());
    }
    return rows.get(0);
  }

  @Transactional
  public Map<String, Object> execute(String commandId, Map<String, Object> body) {
    CommandDefinition cmd = registry.command(commandId);
    cmd.capability().require();
    Map<String, Object> in = body == null ? Map.of() : body;

    Map<String, Object> current = cmd.existingRowSql() == null ? Map.of() : existingRow(cmd, in);
    Object[] values = new Object[cmd.args().size()];
    int[] sqlTypes = new int[cmd.args().size()];
    Map<String, Object> generated = new LinkedHashMap<>();
    for (int i = 0; i < cmd.args().size(); i++) {
      CommandDefinition.Arg arg = cmd.args().get(i);
      Object v = switch (arg.source()) {
        case BODY -> bodyValue(arg, in);
        case SEQUENCE -> {
          if (!arg.name().matches("[A-Z0-9_$.]+")) throw new IllegalStateException("bad sequence");
          Long id = jdbc.queryForObject("SELECT " + arg.name() + ".NEXTVAL FROM DUAL", Long.class);
          generated.put(sequenceKey(arg.name()), id);
          yield id == null ? null : BigDecimal.valueOf(id);
        }
        case AUDIT_USER -> RequestUtil.getCurrentAuditUserId();
        case NOW -> Timestamp.valueOf(LocalDateTime.now());
        case CONSTANT -> arg.name();
        case EXISTING -> {
          if (!current.containsKey(arg.name())) {
            throw new IllegalStateException("Command " + cmd.id() + " existingRow lacks column " + arg.name());
          }
          yield current.get(arg.name());
        }
        case CLIENT_NUMBER -> RequestUtil.isMinistryUser()
            ? stringOrNull(in.get(arg.name()))
            : RequestUtil.getCurrentClientNumber();
      };
      values[i] = v;
      sqlTypes[i] = sqlType(arg, v);
    }

    String sql = "{call " + cmd.procedure() + "(" + "?,".repeat(values.length).replaceAll(",$", "") + ")}";
    try {
      jdbc.execute(sql, (CallableStatementCallback<Void>) cs -> {
        for (int i = 0; i < values.length; i++) {
          if (values[i] == null) cs.setNull(i + 1, sqlTypes[i]);
          else cs.setObject(i + 1, values[i], sqlTypes[i]);
        }
        cs.execute();
        return null;
      });
    } catch (org.springframework.dao.DataAccessException ex) {
      Throwable root = ex.getMostSpecificCause();
      String message = root instanceof SQLException sql2 ? sql2.getMessage() : String.valueOf(root);
      throw new StoredProcedureException(cmd.procedure(), cmd.id(), message);
    }
    log.info("Command {} ({}) executed by {}", cmd.id(), cmd.procedure(), RequestUtil.getCurrentAuditUserId());
    return generated;
  }

  private static Object bodyValue(CommandDefinition.Arg arg, Map<String, Object> in) {
    Object raw = in.get(arg.name());
    String s = stringOrNull(raw);
    if (!StringUtils.hasText(s)) {
      if (arg.required()) throw new IllegalArgumentException("'" + arg.name() + "' is required");
      return null;
    }
    return switch (arg.type()) {
      case DATE, DATE_TO -> java.sql.Date.valueOf(java.time.LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s));
      case NUMBER -> new BigDecimal(s);
      case UPPER -> s.toUpperCase();
      default -> s;
    };
  }

  private static int sqlType(CommandDefinition.Arg arg, Object v) {
    if (v instanceof Timestamp) return Types.TIMESTAMP;
    if (v instanceof java.time.LocalDateTime) return Types.TIMESTAMP;
    if (v instanceof java.sql.Date) return Types.DATE;
    if (v instanceof BigDecimal) return Types.NUMERIC;
    return switch (arg.type()) {
      case NUMBER -> Types.NUMERIC;
      case DATE, DATE_TO -> Types.DATE;
      default -> Types.VARCHAR;
    };
  }

  private static String stringOrNull(Object o) {
    return o == null ? null : o.toString().trim();
  }

  /** {@code HBS_MARK_HOLDER_PROFILE_SEQ} → {@code hbsMarkHolderProfileId}. */
  private static String sequenceKey(String sequence) {
    String base = sequence.replaceFirst("^THE\\.", "").replaceFirst("_SEQ$", "");
    return QueryService.camel(base) + "Id";
  }
}
