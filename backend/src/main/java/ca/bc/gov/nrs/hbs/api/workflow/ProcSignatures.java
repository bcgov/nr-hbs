package ca.bc.gov.nrs.hbs.api.workflow;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;

/**
 * The declared parameter lists of the {@code HBS_*} procedures and functions,
 * extracted from nr-mof-db by {@code scripts/gen-proc-signatures.py} into
 * {@code db/hbs-proc-signatures.json}. {@link TableApi} binds call arguments by
 * these names, so a ported workflow names each argument instead of counting
 * positions, and a wrong name fails in unit tests rather than in TEST.
 */
public final class ProcSignatures {

  public static final String RESOURCE = "db/hbs-proc-signatures.json";

  public record Param(String name, String mode, String type) {
    public boolean isIn() {
      return mode.startsWith("IN");
    }

    public boolean isOut() {
      return mode.endsWith("OUT");
    }
  }

  /**
   * @param proposed true for a proc this app has asked the DBA for (docs/db/*.sql)
   *                 that nr-mof-db doesn't have yet — calls fail until it is applied
   */
  public record Signature(String name, String kind, String source, List<Param> params, boolean proposed) {
    public boolean isFunction() {
      return "function".equals(kind);
    }

    public Set<String> inNames() {
      return params.stream().filter(Param::isIn).map(Param::name).collect(Collectors.toUnmodifiableSet());
    }
  }

  private static volatile ProcSignatures instance;

  private final Map<String, Signature> byName;

  private ProcSignatures(Map<String, Signature> byName) {
    this.byName = byName;
  }

  /** The signatures on the classpath (loaded once). */
  public static ProcSignatures load() {
    ProcSignatures local = instance;
    if (local == null) {
      synchronized (ProcSignatures.class) {
        local = instance;
        if (local == null) {
          local = read();
          instance = local;
        }
      }
    }
    return local;
  }

  private static ProcSignatures read() {
    // `proposed` is absent on most entries (Jackson 3 won't map a missing value to a primitive).
    record Raw(String kind, String source, List<Param> params, Boolean proposed) {}
    try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
      Map<String, Raw> raw = new ObjectMapper().readValue(in, new TypeReference<>() {});
      return new ProcSignatures(raw.entrySet().stream().collect(Collectors.toUnmodifiableMap(
          Map.Entry::getKey,
          e -> new Signature(e.getKey(), e.getValue().kind(), e.getValue().source(), e.getValue().params(),
              Boolean.TRUE.equals(e.getValue().proposed())))));
    } catch (IOException ex) {
      throw new UncheckedIOException("Cannot read " + RESOURCE, ex);
    }
  }

  public boolean exists(String name) {
    return byName.containsKey(normalize(name));
  }

  public Signature get(String name) {
    Signature s = byName.get(normalize(name));
    if (s == null) {
      throw new IllegalStateException(name + " is not an HBS procedure/function in nr-mof-db ("
          + RESOURCE + "); add it to nr-mof-db and re-run scripts/gen-proc-signatures.py");
    }
    return s;
  }

  /** Names of the procs that only exist as proposals in docs/db. */
  public List<String> proposed() {
    return byName.values().stream().filter(Signature::proposed).map(Signature::name).sorted().toList();
  }

  public int size() {
    return byName.size();
  }

  static String normalize(String name) {
    return name.replaceFirst("(?i)^THE\\.", "").toUpperCase(Locale.ROOT);
  }
}
