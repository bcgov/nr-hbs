package ca.bc.gov.nrs.hbs.api.query;

import java.util.List;

/**
 * Implemented once per functional area (Queries, Scale Returns, Billing …).
 * Each implementation is a Spring bean; {@link QueryRegistry} collects them.
 */
public interface QueryCatalog {

  List<QueryDefinition> queries();

  default List<CommandDefinition> commands() {
    return List.of();
  }
}
