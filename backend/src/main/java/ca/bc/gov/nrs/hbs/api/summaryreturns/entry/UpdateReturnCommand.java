package ca.bc.gov.nrs.hbs.api.summaryreturns.entry;

import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.AREA_CUT;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.BATCH_SLIP_ID;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.CATEGORY;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.CONFIRM_CUSTODY_TRANSFER;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.LOG_COUNT;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.RETURN_NUMBER;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.SCALER_LICENCE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.SCALE_SITE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.TIMBER_MARK;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.summaryreturns.AuditTrail;
import ca.bc.gov.nrs.hbs.api.summaryreturns.EditError;
import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryCaller;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * P053 / P103 / P153 Save: {@code P050Action / P100Action / P150Action.updateScaleReturn} and
 * {@code updateVersion}, then {@code SummaryScaleReturnHelper.updateSummaryScaleReturnAndChildren}
 * (remove errors, save the return and its children, run the release edits, save their errors).
 *
 * <p>Legacy saved only when the page changed something ({@code getScaleReturnEdits().length > 0}),
 * or always for an area-based return, whose edits must run anyway (24.09.2011); so does this.
 *
 * <p>P054A: when a volume-estimate user saves a held OIP version with a scale site or licence
 * outside their authorization ({@code validateDocumentEligibleForUpdate} → {@code authorizationFailure}),
 * the legacy page asked to confirm the custody transfer. Here the command refuses with
 * {@link EntryMessages#confirmCustodyTransfer} on field {@code confirmCustodyTransfer} unless the body
 * carries {@code confirmCustodyTransfer: "Y"}.
 *
 * <p>Body: {@code documentControlNumber}, {@code version}, {@code updateTimestamp} and the header
 * fields of {@link HeaderForm}; a field left out keeps its stored value. Response: the version plus
 * {@code updateTimestamp}, {@code saved} and the {@code editErrors} the release edits found.
 */
@Component
public class UpdateReturnCommand extends EntryCommand {

  public UpdateReturnCommand(Deps deps) {
    super(deps);
  }

  @Override
  public String id() {
    return "summaryReturns.update";
  }

  @Override
  public String legacy() {
    return "P053/P103/P153 Save — P050/P100/P150Action.updateScaleReturn + updateVersion, "
        + "SummaryScaleReturnStateHelper.updateSummaryScaleReturnAndChildren";
  }

  @Override
  public Capability capability() {
    return Capability.SUMMARY_RETURN_EDIT;
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    SummaryCaller caller = caller();
    SummaryReturn ret = loadEditable(body, caller);
    HeaderForm f = HeaderForm.forUpdate(ret, body);
    boolean changed;
    switch (ret.type()) {
      case PIECE -> changed = updatePiece(ret, f, caller);
      case WEIGHT -> changed = updateWeight(ret, f);
      default -> changed = updateSample(ret, f);
    }
    boolean save = changed || ret.areaBased();
    List<EditError> errors = save ? saveAndValidate(ret, changed) : List.of();
    return saved(ret, save, errors);
  }

  /** {@code P050Action.updateScaleReturn(scaleRtn, form, …)} + {@code updateVersion}. */
  private boolean updatePiece(SummaryReturn ret, HeaderForm f, SummaryCaller caller) {
    String pssmry = ret.pssmryType();
    boolean areaBased = ret.areaBased();
    if (!SummaryReturnRules.PSSMRY_DEFAULT_TYPE.equals(pssmry)) {
      // only a SMRYDOC return shows the category drop-down (PR, BC) on P053
      f.set(CATEGORY, ret.version().str("scl_rtn_category_code"));
    }
    if (SummaryReturnRules.PSSMRY_WASTE_TYPE.equals(pssmry)) {
      f.set(SCALE_SITE, ret.version().str("scale_site_id_nmbr")); // not editable on a waste return
    }
    HeaderValidator.Outcome outcome = validator.validatePiece(f, caller, ret);
    throwIfAny(outcome.problems());
    if (outcome.custodyTransfer() && !f.confirmCustodyTransfer()) {
      Row v = ret.version();
      throw new WorkflowValidationException(CONFIRM_CUSTODY_TRANSFER, EntryMessages.confirmCustodyTransfer(
          v.str("scale_site_id_nmbr"), f.get(SCALE_SITE), v.str("license_number"), f.get(SCALER_LICENCE)));
    }

    Row v = ret.version();
    List<Row> segsBefore = ret.segregations().stream().map(Row::copy).toList();
    String typeBefore = v.str("quant_lic_type_cd");

    ret.header().set("volest_batch_id", f.lng(BATCH_SLIP_ID));
    Set<String> skip = new java.util.HashSet<>();
    if (areaBased) {
      // BT 15212: the derived licence, return number and log count are not updated; BT18110: the
      // scale site follows the timber mark
      skip.addAll(Set.of(SCALER_LICENCE, RETURN_NUMBER, LOG_COUNT));
      f.set(SCALE_SITE, lookups.areaBasedScaleSite(f.get(TIMBER_MARK)));
    }
    String licenceType = validator.licenceType(f.get(SCALER_LICENCE));
    f.copyTo(v, skip);
    v.set("quant_lic_type_cd", licenceType);
    if (f.empty(AREA_CUT)) v.set("area_cut", null);

    // the segregations follow the return category
    String category = f.get(CATEGORY) == null ? "" : f.get(CATEGORY);
    for (Row seg : ret.segregations()) {
      if (SummaryReturnRules.CATEGORY_WASTE.equals(category)) {
        seg.set("psseg_type", SummaryReturnRules.SEG_TYPE_WASTE).set("nmv_ind", "N").set("sb4_ind", "N")
            .set("timber_mark", null);
      } else if (SummaryReturnRules.CATEGORY_BEACHCOMB.equals(category)) {
        seg.set("psseg_type", SummaryReturnRules.SEG_TYPE_BEACH_COMB).set("avoidable_ind", "N").set("sb4_ind", "N");
      } else {
        seg.set("psseg_type", SummaryReturnRules.SEG_TYPE_PRIMARY).set("timber_mark", null).set("avoidable_ind", "N");
      }
    }
    return !ret.versionEdits().isEmpty() || segregationsChanged(ret.type(), segsBefore, ret.segregations())
        || !java.util.Objects.equals(typeBefore, licenceType);
  }

  /** {@code P100Action.updateScaleReturn} + {@code updateVersion}. */
  private boolean updateWeight(SummaryReturn ret, HeaderForm f) {
    throwIfAny(validator.validateWeight(f, ret).problems());
    f.copyTo(ret.version(), Set.of());
    return !ret.versionEdits().isEmpty();
  }

  /** {@code P150Action.updateScaleReturn} + {@code updateVersion}. */
  private boolean updateSample(SummaryReturn ret, HeaderForm f) {
    throwIfAny(validator.validateSample(f).problems());
    String typeBefore = ret.version().str("quant_lic_type_cd");
    String licenceType = validator.licenceType(f.get(SCALER_LICENCE));
    f.copyTo(ret.version(), Set.of());
    ret.version().set("quant_lic_type_cd", licenceType);
    return !ret.versionEdits().isEmpty() || !java.util.Objects.equals(typeBefore, licenceType);
  }

  static boolean segregationsChanged(SummaryReturnType type, List<Row> before, List<Row> after) {
    List<AuditTrail.Detail> details = new ArrayList<>();
    for (int i = 0; i < before.size(); i++) {
      details.addAll(AuditTrail.segregationEdits(type, before.get(i), after.get(i)));
    }
    return !details.isEmpty();
  }
}
