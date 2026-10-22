package ca.bc.gov.nrs.hbs.api.sampling;

import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException.Problem;
import java.util.ArrayList;
import java.util.List;

/** The legacy {@code ActionErrors}: messages collected in order, thrown together. */
final class Problems {

  private final List<Problem> list = new ArrayList<>();

  Problems add(String field, String message) {
    list.add(new Problem(field, message));
    return this;
  }

  boolean isEmpty() {
    return list.isEmpty();
  }

  int size() {
    return list.size();
  }

  void clear() {
    list.clear();
  }

  List<Problem> list() {
    return List.copyOf(list);
  }

  void throwIfAny() {
    if (!list.isEmpty()) {
      throw new WorkflowValidationException(list);
    }
  }
}
