package ca.bc.gov.nrs.hbs.api.billing;

import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import org.springframework.stereotype.Component;

/** P829/P830 "Generate and Release": HBS3R830 with version state RDY (DacConstants.VersionStatus.READY). */
@Component
public class GenerateFinalBillReleaseCommand extends GenerateFinalBillCommand {

  public GenerateFinalBillReleaseCommand(BillingReads reads, TableApi tableApi, Audit audit) {
    super(reads, tableApi, audit, "RDY");
  }

  @Override
  public String id() {
    return "billing.finalBill.generateRelease";
  }

  @Override
  public String legacy() {
    return "P829/P830 Generate and Release (P828Action, HBS3R830)";
  }
}
