package ca.bc.gov.nrs.hbs.api.scaledata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Which forest clients each submission account may submit for and read
 * (docs/scale-data-api.md open question 2: the settings ConfigMap). One key of
 * the backend's settings ConfigMap, {@code SCALE_DATA_API_ACCOUNTS}, mounted as
 * a file like the P038 settings, one account per line:
 *
 * <pre>
 * # CSS client id of the service account = its client numbers
 * hbs-scale-acme-4521   = 00012345
 * hbs-scale-vendor-7781 = 00012345, 00067890
 * </pre>
 *
 * <p>The file is re-read when it changes, so onboarding an account or removing
 * one takes effect when OpenShift syncs the mount (about a minute), without a
 * restart. A token whose {@code azp} is not listed is refused (401), even when
 * it carries the role and scopes. An unreadable line is skipped with a warning;
 * a missing file means no accounts.
 */
@Component
@Slf4j
public class ApiAccountRegistry {

  static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
  static final Pattern CLIENT_NUMBER = Pattern.compile("\\d{8}");

  private final Path file;
  private volatile Snapshot snapshot = new Snapshot(null, -1, Map.of());

  private record Snapshot(FileTime modified, long size, Map<String, ApiAccount> accounts) {}

  public ApiAccountRegistry(ScaleDataApiProperties properties) {
    this.file = Path.of(properties.accountsFile());
  }

  /** The account with this CSS client id, if it is mapped. */
  public Optional<ApiAccount> find(String clientId) {
    if (clientId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(current().get(clientId));
  }

  /** Every mapped account (for logging and tests). */
  public Map<String, ApiAccount> all() {
    return current();
  }

  private Map<String, ApiAccount> current() {
    FileTime modified;
    long size;
    try {
      modified = Files.getLastModifiedTime(file);
      size = Files.size(file);
    } catch (NoSuchFileException ex) {
      return reload(null, -1, Map.of());
    } catch (IOException ex) {
      log.warn("Scale-data API accounts file {} could not be read; keeping the last mapping", file, ex);
      return snapshot.accounts();
    }
    Snapshot s = snapshot;
    if (modified.equals(s.modified()) && size == s.size()) {
      return s.accounts();
    }
    try {
      return reload(modified, size, parse(Files.readString(file, StandardCharsets.UTF_8)));
    } catch (IOException ex) {
      log.warn("Scale-data API accounts file {} could not be read; keeping the last mapping", file, ex);
      return s.accounts();
    }
  }

  private synchronized Map<String, ApiAccount> reload(FileTime modified, long size, Map<String, ApiAccount> accounts) {
    Snapshot s = snapshot;
    if (!accounts.equals(s.accounts())) {
      log.info("Scale-data API accounts: {} mapped ({})", accounts.size(), accounts.keySet());
    }
    snapshot = new Snapshot(modified, size, accounts);
    return accounts;
  }

  /** Parses the mapping; bad lines are skipped and logged, never fatal. */
  static Map<String, ApiAccount> parse(String text) {
    Map<String, ApiAccount> out = new LinkedHashMap<>();
    String[] lines = text.split("\\R");
    for (int n = 0; n < lines.length; n++) {
      String line = lines[n];
      int hash = line.indexOf('#');
      if (hash >= 0) {
        line = line.substring(0, hash);
      }
      line = line.strip();
      if (line.isEmpty()) {
        continue;
      }
      int eq = line.indexOf('=');
      if (eq <= 0) {
        log.warn("Scale-data API accounts line {} skipped: expected '<client id> = <client numbers>'", n + 1);
        continue;
      }
      String clientId = line.substring(0, eq).strip();
      if (!CLIENT_ID.matcher(clientId).matches()) {
        log.warn("Scale-data API accounts line {} skipped: invalid client id", n + 1);
        continue;
      }
      LinkedHashSet<String> clients = new LinkedHashSet<>();
      boolean bad = false;
      for (String part : line.substring(eq + 1).split("[,\\s]+")) {
        if (part.isEmpty()) {
          continue;
        }
        if (!CLIENT_NUMBER.matcher(part).matches()) {
          bad = true;
          break;
        }
        clients.add(part);
      }
      if (bad || clients.isEmpty()) {
        log.warn("Scale-data API accounts line {} skipped: client numbers must be 8 digits, comma-separated", n + 1);
        continue;
      }
      if (out.containsKey(clientId)) {
        log.warn("Scale-data API accounts line {}: client id {} listed twice; the later line wins", n + 1, clientId);
      }
      out.put(clientId, new ApiAccount(clientId, new ArrayList<>(clients)));
    }
    return Map.copyOf(out);
  }

  /** For tests and diagnostics: the client ids. */
  List<String> clientIds() {
    return List.copyOf(current().keySet());
  }
}
