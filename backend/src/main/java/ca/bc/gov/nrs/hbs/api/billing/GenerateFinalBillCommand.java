package ca.bc.gov.nrs.hbs.api.billing;

import ca.bc.gov.nrs.hbs.api.legacyrules.Inputs;
import ca.bc.gov.nrs.hbs.api.legacyrules.LegacyMessages;
import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowCommand;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * P829 / P830 "Generate and Hold" / "Generate and Release"
 * ({@code P828Action.doGenerateFinalBillStatusSummary} →
 * {@code ProfileManagerBean.generateFinalBill} → proc {@code HBS3R830}).
 *
 * <p>Legacy offered the checkbox only to HBS_BILL_ADMIN / MOF_MGR
 * ({@code hbs1:rolePresent}) and only on rows whose eligibility flag is
 * {@code '*'}; the row is re-read here ({@link BillingReads#finalBillStatus})
 * and refused otherwise. HBS3R830 creates the final-bill weight-scale summary
 * returns (version state HLD or RDY, category FB) for every eligible PSYMark
 * of the population (and stratum on P830) and sets
 * YTD_CYCLIC_BILLING.FINAL_BILL_IND. Its first argument is the OUT status:
 * 0 = processed ("Processed."), -1 = nothing to generate, otherwise the
 * SQLCODE of the last failing PSYMark ("Not Processed.").
 *
 * <p><b>Transactions:</b> HBS3R830 COMMITs after each PSYMark (and ROLLBACKs a
 * failing one) itself, exactly as legacy ran it; this workflow does nothing
 * else in the database, so it leaves the same states legacy did.
 *
 * <p>Body: {@code samplingYear}, {@code population}, optional {@code stratum}.
 */
@Slf4j
public abstract class GenerateFinalBillCommand implements WorkflowCommand {

  static final String PROC = "HBS3R830";

  private final BillingReads reads;
  private final TableApi tableApi;
  private final Audit audit;
  private final String versionState;

  protected GenerateFinalBillCommand(BillingReads reads, TableApi tableApi, Audit audit, String versionState) {
    this.reads = reads;
    this.tableApi = tableApi;
    this.audit = audit;
    this.versionState = versionState;
  }

  @Override
  public Capability capability() {
    return Capability.FINAL_BILL_GENERATE;
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    long samplingYear = Inputs.requiredLong(body, "samplingYear", "Sampling Year");
    String population = Inputs.upper(body, "population");
    if (population == null) {
      throw new WorkflowValidationException("population", LegacyMessages.POPULATION_REQUIRED);
    }
    String stratum = Inputs.upper(body, "stratum");

    Map<String, Object> row = reads.finalBillStatus(samplingYear, population, stratum);
    if (row == null || !"*".equals(Inputs.colText(row, "ELIGIBLE_PSYM_FLAG"))) {
      throw new WorkflowValidationException("population", LegacyMessages.NOT_PROCESSED);
    }

    Map<String, Object> out = tableApi.call(PROC, ProcArgs.of()
        .put("fb_sampling_year", String.valueOf(samplingYear))
        .put("fb_population_nmbr", population)
        .put("fb_stratum_nmbr", stratum)
        .put("fb_hld_rel", versionState)
        .put("fb_userid", audit.user()));
    Object raw = out.get("fb_returnvalue");
    long status = raw instanceof Number n ? n.longValue() : raw == null ? -1L : Long.parseLong(raw.toString());
    log.info("HBS3R830 {} {}/{}/{} returned {}", versionState, population, stratum, samplingYear, status);
    if (status != 0) {
      throw new WorkflowValidationException("population", LegacyMessages.NOT_PROCESSED);
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("processedStatus", status);
    result.put("message", LegacyMessages.PROCESSED);
    return result;
  }
}
