package ca.bc.gov.nrs.hbs.api.processparams;

import ca.bc.gov.nrs.hbs.api.query.QueryBinds;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The P038 settings, which replace the three legacy flat files of
 * {@code HBSProcessingParamHelper}. They live in the backend's settings
 * ConfigMap, mounted as one file per key under {@code hbs.settings.dir}
 * (docs/coexistence.md). Every read goes to the files, so an edit to the
 * ConfigMap shows up once OpenShift syncs the mount (about a minute), without
 * a restart. A missing key reads as null.
 */
@Component
public class AppSettings implements QueryBinds {

  /** P038Form process names (the legacy select offered exactly these two). */
  public static final List<String> PROCESSES = List.of("B2031", "B2041");
  public static final String START_LOC = ".START_LOC";
  public static final String TIME_LIMIT = ".TIME_LIMIT";
  public static final String SEND_TO = "PRINTER.SEND_TO";
  public static final String COPY_TO = "PRINTER.COPY_TO";
  public static final String ALERT_TEXT = "ALERT_TEXT";

  /** Every key, in P038 order (ConfigMap keys allow '.', so they match the old setting keys). */
  public static final List<String> KEYS = List.of(
      "B2031" + START_LOC, "B2031" + TIME_LIMIT, "B2041" + START_LOC, "B2041" + TIME_LIMIT,
      SEND_TO, COPY_TO, ALERT_TEXT);

  /** Bind prefix of the registry queries that show these values. */
  static final String BIND_PREFIX = "hbsSetting";

  private final Path dir;

  public AppSettings(@Value("${hbs.settings.dir}") String dir) {
    this.dir = Path.of(dir);
  }

  /** Every configured setting, key → value; blank and missing keys are left out. */
  public Map<String, String> all() {
    Map<String, String> out = new LinkedHashMap<>();
    for (String key : KEYS) {
      String v = value(key);
      if (v != null) {
        out.put(key, v);
      }
    }
    return out;
  }

  /** One setting, trimmed (a YAML block value ends in a newline); null when missing or blank. */
  public String value(String key) {
    if (!KEYS.contains(key)) {
      throw new IllegalArgumentException("Unknown setting " + key);
    }
    try {
      String v = Files.readString(dir.resolve(key), StandardCharsets.UTF_8).strip();
      return v.isEmpty() ? null : v;
    } catch (NoSuchFileException ex) {
      return null;
    } catch (IOException ex) {
      throw new UncheckedIOException("Setting " + key + " could not be read", ex);
    }
  }

  @Override
  public String prefix() {
    return BIND_PREFIX;
  }

  /**
   * {@code :hbsSetting_B2031_START_LOC} etc. for the registry queries. A
   * missing value binds as '' (Oracle NULL).
   */
  @Override
  public Map<String, Object> values() {
    Map<String, String> all = all();
    Map<String, Object> binds = new LinkedHashMap<>();
    for (String key : KEYS) {
      binds.put(bindName(key), all.getOrDefault(key, ""));
    }
    return binds;
  }

  static String bindName(String key) {
    return BIND_PREFIX + "_" + key.replace('.', '_');
  }
}
