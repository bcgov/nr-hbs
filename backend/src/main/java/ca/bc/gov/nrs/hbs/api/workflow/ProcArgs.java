package ca.bc.gov.nrs.hbs.api.workflow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Named arguments for one {@link TableApi} call. Every IN parameter of the
 * proc must be put (null included): leaving one out is a programming error,
 * because the generated STORE procs overwrite every column they are given.
 */
public final class ProcArgs {

  private final Map<String, Object> values = new LinkedHashMap<>();

  private ProcArgs() {
  }

  public static ProcArgs of() {
    return new ProcArgs();
  }

  /** Copy of the given name → value pairs. */
  public static ProcArgs from(Map<String, ?> source) {
    ProcArgs args = new ProcArgs();
    source.forEach(args::put);
    return args;
  }

  public ProcArgs put(String name, Object value) {
    values.put(name.toLowerCase(Locale.ROOT), value);
    return this;
  }

  public boolean has(String name) {
    return values.containsKey(name.toLowerCase(Locale.ROOT));
  }

  public Object get(String name) {
    return values.get(name.toLowerCase(Locale.ROOT));
  }

  public Map<String, Object> asMap() {
    return Collections.unmodifiableMap(values);
  }

  @Override
  public String toString() {
    return values.toString();
  }
}
