package ca.bc.gov.nrs.hbs.api.scaledata;

import ca.bc.gov.nrs.hbs.api.intake.xml.FormatEdit;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The text of the format edits ({@code HBS_EDIT_ERR_MESSAGE_CODE}, category
 * FMT), for the dry run, which finds the codes without writing error rows.
 * Cached for an hour; a code with no row reads "Format edit XXX failed."
 */
@Component
@Slf4j
public class EditMessages {

  private static final Duration TTL = Duration.ofHours(1);

  private final NamedParameterJdbcTemplate jdbc;
  private volatile Map<String, String> messages = Map.of();
  private volatile Instant loadedAt = Instant.EPOCH;

  public EditMessages(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public String formatEdit(String code) {
    if (code == null) {
      return "The file failed a format edit.";
    }
    String text = all().get(code);
    return text == null ? "Format edit " + code + " failed." : text;
  }

  private Map<String, String> all() {
    if (Instant.now().isAfter(loadedAt.plus(TTL))) {
      try {
        Map<String, String> loaded = new HashMap<>();
        jdbc.query("""
            SELECT hbs_edit_err_message_code AS code, description
              FROM hbs_edit_err_message_code
             WHERE hbs_edit_err_category_code = :category""",
            new MapSqlParameterSource("category", FormatEdit.CATEGORY),
            (java.sql.ResultSet rs) -> {
              String d = rs.getString("description");
              loaded.put(rs.getString("code"), d == null ? null : d.trim());
            });
        messages = Map.copyOf(loaded);
        loadedAt = Instant.now();
      } catch (DataAccessException ex) {
        log.warn("Format edit messages could not be loaded; answering with codes only", ex);
        loadedAt = Instant.now().minus(TTL).plus(Duration.ofMinutes(1));
      }
    }
    return messages;
  }
}
