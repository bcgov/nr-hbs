package ca.bc.gov.nrs.hbs.api.query;

import ca.bc.gov.nrs.hbs.api.exception.EntityNotFoundException;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Collects every {@link QueryCatalog} bean and every {@link WorkflowCommand}
 * bean and indexes queries/commands by id (one id space for both kinds of
 * command).
 */
@Component
@Slf4j
public class QueryRegistry {

  private final Map<String, QueryDefinition> queries = new TreeMap<>();
  private final Map<String, CommandDefinition> commands = new TreeMap<>();
  private final Map<String, WorkflowCommand> workflows = new TreeMap<>();

  public QueryRegistry(List<QueryCatalog> catalogs) {
    this(catalogs, List.of());
  }

  @org.springframework.beans.factory.annotation.Autowired
  public QueryRegistry(List<QueryCatalog> catalogs, List<WorkflowCommand> workflowCommands) {
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
    for (WorkflowCommand w : workflowCommands) {
      if (commands.containsKey(w.id()) || workflows.put(w.id(), w) != null) {
        throw new IllegalStateException("Duplicate command id " + w.id());
      }
    }
    log.info("Registered {} queries, {} commands and {} workflow commands from {} catalogs",
        queries.size(), commands.size(), workflows.size(), catalogs.size());
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

  /** The workflow command with this id, or null when the id is a plain command (or unknown). */
  public WorkflowCommand workflow(String id) {
    return workflows.get(id);
  }

  public Collection<WorkflowCommand> allWorkflows() {
    return workflows.values();
  }

  public Collection<QueryDefinition> allQueries() {
    return queries.values();
  }

  public Collection<CommandDefinition> allCommands() {
    return commands.values();
  }
}
