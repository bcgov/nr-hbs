package ca.bc.gov.nrs.hbs.api.query;

import java.util.List;
import java.util.Map;

/**
 * A whitelisted, read-only query backing one legacy screen (or one panel of
 * it). The SQL is ported from the legacy {@code *Query} classes / JSP-backing
 * DAOs and reviewed once here; callers can only choose which declared filters
 * to fill — every value is a bound parameter, never concatenated.
 *
 * @param id              stable id the SPA calls, e.g. {@code invoices.search}
 * @param legacyScreen    legacy screen id(s), e.g. {@code P449/P450}
 * @param capability      who may run it
 * @param sql             SELECT … FROM … WHERE 1=1 (aliases become JSON keys)
 * @param filters         optional/required criteria, AND-ed onto {@code sql}
 * @param sortColumns     sortable JSON key → SQL expression whitelist
 * @param defaultOrderBy  ORDER BY used when the caller doesn't sort
 * @param clientScope     SQL fragment applied for industry users, bound to
 *                        {@code :scopeClientNumber}, e.g.
 *                        {@code AND inv.client_number = :scopeClientNumber};
 *                        {@code null} when the query isn't client-owned data
 * @param maxRows         hard cap on rows a single request may page through
 */
public record QueryDefinition(
    String id,
    String legacyScreen,
    Capability capability,
    String sql,
    List<QueryFilter> filters,
    Map<String, String> sortColumns,
    String defaultOrderBy,
    String clientScope,
    int maxRows) {

  public static final int DEFAULT_MAX_ROWS = 5000;

  public static Builder builder(String id) {
    return new Builder(id);
  }

  /** Fluent builder so catalogs read like the legacy screen spec. */
  public static final class Builder {
    private final String id;
    private String legacyScreen = "";
    private Capability capability = Capability.ANY_USER;
    private String sql;
    private final java.util.ArrayList<QueryFilter> filters = new java.util.ArrayList<>();
    private final java.util.LinkedHashMap<String, String> sortColumns = new java.util.LinkedHashMap<>();
    private String defaultOrderBy = "1";
    private String clientScope;
    private int maxRows = DEFAULT_MAX_ROWS;

    private Builder(String id) {
      this.id = id;
    }

    public Builder legacy(String screens) { this.legacyScreen = screens; return this; }
    public Builder capability(Capability c) { this.capability = c; return this; }
    public Builder sql(String s) { this.sql = s; return this; }
    public Builder filter(QueryFilter f) { this.filters.add(f); return this; }
    public Builder sort(String key, String expression) { this.sortColumns.put(key, expression); return this; }
    public Builder orderBy(String o) { this.defaultOrderBy = o; return this; }
    public Builder clientScope(String fragment) { this.clientScope = fragment; return this; }
    public Builder maxRows(int n) { this.maxRows = n; return this; }

    public QueryDefinition build() {
      if (sql == null || !sql.stripLeading().toUpperCase().startsWith("SELECT")) {
        throw new IllegalStateException("Query " + id + " must be a SELECT");
      }
      return new QueryDefinition(id, legacyScreen, capability, sql, List.copyOf(filters),
          Map.copyOf(sortColumns), defaultOrderBy, clientScope, maxRows);
    }
  }
}
