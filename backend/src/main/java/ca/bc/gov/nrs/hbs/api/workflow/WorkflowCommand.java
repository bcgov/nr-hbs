package ca.bc.gov.nrs.hbs.api.workflow;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import java.util.Map;

/**
 * A write that is more than one table-API call: a legacy EJB workflow ported
 * to Java (release, cancel/replace, approve, …). Served by the same
 * {@code POST /api/v1/hbs/commands/{id}} endpoint and listed by
 * {@code GET /commands} like a registry {@code CommandDefinition}, so the SPA
 * enables the matching button once the bean exists.
 *
 * <p>Implementations are Spring beans, run in one transaction
 * ({@code CommandService} opens it), check optimistic locks with
 * {@link RowLock}, and write only through {@link TableApi}.
 */
public interface WorkflowCommand {

  /** Command id the SPA posts to, e.g. {@code summaryReturns.release}. */
  String id();

  /** Legacy screen / EJB method it replaces, for the docs and logs. */
  String legacy();

  Capability capability();

  /**
   * Runs the workflow. The capability has already been checked. Returns values
   * the SPA may use (new ids, messages); validation failures are thrown as
   * {@link WorkflowValidationException}.
   */
  Map<String, Object> execute(Map<String, Object> body);
}
