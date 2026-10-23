package ca.bc.gov.nrs.hbs.api.workflow;

import java.util.List;

/**
 * One or more business-rule failures, each with the legacy message text (the
 * {@code ApplicationResources} / {@code ErrorMessages} wording), returned as a
 * 400 with one sub-error per message. Nothing has been written when it is
 * thrown: the workflow's transaction rolls back.
 */
public class WorkflowValidationException extends IllegalArgumentException {

  public record Problem(String field, String message) {}

  private final transient List<Problem> problems;

  public WorkflowValidationException(List<Problem> problems) {
    super(problems.isEmpty() ? "Validation failed" : problems.get(0).message());
    this.problems = List.copyOf(problems);
  }

  public WorkflowValidationException(String field, String message) {
    this(List.of(new Problem(field, message)));
  }

  public List<Problem> problems() {
    return problems;
  }
}
