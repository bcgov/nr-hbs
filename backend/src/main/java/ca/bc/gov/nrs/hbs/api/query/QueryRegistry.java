package ca.bc.gov.nrs.hbs.api.query;

import ca.bc.gov.nrs.hbs.api.exception.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Collects every {@link QueryCatalog} bean and indexes queries/commands by id. */
@Component
@Slf4j
public class QueryRegistry {

  private final Map<String, QueryDefinition> queries = new TreeMap<>();
  private final Map<String, CommandDefinition> commands = new TreeMap<>();

  public QueryRegistry(List<QueryCatalog> catalogs) {
    for (QueryCatalog catalog : catalogs) {
      for (QueryDefinition q : catalog.queries()) {
        if (queries.put(q.id(), q) != null) {
          throw new IllegalStateException("Duplicate query id " + q.id());
        }
      }
      for (CommandDefinition c : catalog.commands()) {
        if (commands.put(c.id(), c) != null) {
          throw new IllegalStateException("Duplicate command id " + c.id());
        }
      }
    }
    log.info("Registered {} queries and {} commands from {} catalogs",
        queries.size(), commands.size(), catalogs.size());
  }

  public QueryDefinition query(String id) {
    QueryDefinition q = queries.get(id);
    if (q == null) throw new EntityNotFoundException(QueryDefinition.class, "id", id);
    return q;
  }

  public CommandDefinition command(String id) {
    CommandDefinition c = commands.get(id);
    if (c == null) throw new EntityNotFoundException(CommandDefinition.class, "id", id);
    return c;
  }

  public Collection<QueryDefinition> allQueries() {
    return queries.values();
  }

  public Collection<CommandDefinition> allCommands() {
    return commands.values();
  }
}
