package ca.bc.gov.nrs.hbs.api.query;

import java.util.Map;

/**
 * Server-side values a registry query can select as bound parameters (for
 * values that live outside the database, e.g. the settings ConfigMap). Bound
 * only for queries whose SQL contains {@code :<prefix>}; a request parameter
 * can never supply them.
 */
public interface QueryBinds {

  /** Bind-name prefix, e.g. {@code hbsSetting}. */
  String prefix();

  /** Bind name → value. */
  Map<String, Object> values();
}
