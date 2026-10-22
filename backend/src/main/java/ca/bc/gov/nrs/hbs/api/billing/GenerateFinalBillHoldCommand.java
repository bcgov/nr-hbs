package ca.bc.gov.nrs.hbs.api.billing;

import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import org.springframework.stereotype.Component;

/** P829/P830 "Generate and Hold": HBS3R830 with version state HLD (DacConstants.VersionStatus.HELD). */
@Component
public class GenerateFinalBillHoldCommand extends GenerateFinalBillCommand {

  public GenerateFinalBillHoldCommand(BillingReads reads, TableApi tableApi, Audit audit) {
    super(reads, tableApi, audit, "HLD");
  }

  @Override
  public String id() {
    return "billing.finalBill.generateHold";
  }

  @Override
  public String legacy() {
    return "P829/P830 Generate and Hold (P828Action, HBS3R830)";
  }
}
