package ca.bc.gov.nrs.hbs.api.detailreturns.validation.form;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException.Problem;
import java.util.List;
import java.util.Map;

/**
 * A {@link DetailFormValidator} that accepts every request, for tests of the
 * commands whose minimal payloads the legacy forms would refuse (the form rules
 * themselves are covered by {@link DetailFormValidatorTest}).
 */
public class FormChecksOff extends DetailFormValidator {

  public FormChecksOff() {
    super(new PermissiveFormLookups());
  }

  @Override
  public List<Problem> validate(DocType type, boolean adding, Map<String, Object> fields,
                                List<Map<String, Object>> logs) {
    return List.of();
  }

  @Override
  public List<Problem> validate(DocType type, boolean adding, Map<String, Object> fields,
                                List<Map<String, Object>> logs, Context context) {
    return List.of();
  }
}
