package ca.bc.gov.nrs.hbs.api.summaryreturns.entry;

import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.AREA_CUT;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.BATCH_SLIP_ID;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.CATEGORY;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.LOG_COUNT;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.PSSMRY_TYPE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.RETURN_NUMBER;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.SCALER_LICENCE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.SCALE_SITE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.TIMBER_MARK;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.entry.HeaderForm.WEIGH_SLIP;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryCaller;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnWriter;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * The add pages: P050 (piece, volume estimate and BT15212 area-based estimate), P100 (weight) and
 * P150 (sample). Each validates the header as the page's validator did, takes a new DCN from
 * {@code HBS_SCALE_RETURN_SEQ} ({@code SummaryScaleReturnHelper.getDocumentControlNumber}) and stores
 * the document, version 1 and the "Scale_Return" INS audit
 * ({@code SummaryScaleReturnManagerBean.createAndStoreSummaryScaleReturn}). New returns are
 * OIP / INC, active, not generated.
 *
 * <p>Body: the header fields of {@link HeaderForm} (blank = null). Response: the new return
 * ({@code documentControlNumber}, {@code version}, {@code returnType}, {@code returnStateCode},
 * {@code status}, {@code updateTimestamp}).
 */
public final class AddReturnCommands {

  private AddReturnCommands() {
  }

  /** The common create path. */
  abstract static class AddCommand extends EntryCommand {

    protected AddCommand(Deps deps) {
      super(deps);
    }

    /** HBS_SCALE_RETURN of a new submitted return ({@code SummaryScaleReturnFactory.createObject}). */
    protected Row newHeader(long dcn, SummaryReturnType type, String user, Timestamp now) {
      return new Row()
          .set("document_control_number", dcn).set("edi_doc_id", null).set("edi_batch_id", null)
          .set("hbs_return_type_code", type.code())
          .set("scale_return_state_code", ScaleReturnState.ORIGINAL_IN_PROGRESS)
          .set("generated_summary_ind", "N").set("volest_batch_id", null)
          .set("entry_userid", user).set("entry_timestamp", now)
          .set("update_userid", null).set("update_timestamp", null);
    }

    /** The version columns every type shares, from the form. */
    protected Row newVersion(long dcn, HeaderForm f, String user, Timestamp now, boolean visible) {
      Row v = new Row().set("document_control_number", dcn).set("version", 1L)
          .set("scl_rtn_version_state_code", ScaleReturnState.INCOMPLETE);
      f.copyTo(v, Set.of());
      return v.set("input_timber_mark", null)
          .set("entry_userid", user).set("entry_timestamp", now)
          .set("update_userid", user).set("update_timestamp", now)
          .set("queued_for_print_ind", "N").set("active_version_ind", "Y")
          .set("visibility_ind", visible ? "Y" : "N");
    }

    protected Map<String, Object> create(Row header, Row version, SummaryReturnType type) {
      SummaryReturn ret = new SummaryReturn(type, header, version, List.of(), List.of(), List.of());
      writer.createReturn(ret, audit.user());
      Map<String, Object> out = result(ret);
      out.put("updateTimestamp", version.get("update_timestamp"));
      return out;
    }
  }

  /** P050 Add: {@code P050Action.addScaleReturn}. */
  @Component
  public static class AddPiece extends AddCommand {

    public AddPiece(Deps deps) {
      super(deps);
    }

    @Override
    public String id() {
      return "summaryReturns.addPiece";
    }

    @Override
    public String legacy() {
      return "P050 Add — P050Action.addScaleReturn, SummaryScaleReturnValidator.validateP050Form";
    }

    @Override
    public Capability capability() {
      return Capability.SUMMARY_RETURN_EDIT;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      SummaryCaller caller = caller();
      HeaderForm f = HeaderForm.forAdd(SummaryReturnType.PIECE, body);
      if (f.empty(PSSMRY_TYPE)) f.set(PSSMRY_TYPE, SummaryReturnRules.PSSMRY_DEFAULT_TYPE); // P050Form default
      String pssmry = f.get(PSSMRY_TYPE);
      if (!EntryRules.ADDABLE_PIECE_TYPES.contains(pssmry)) {
        throw new ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException(PSSMRY_TYPE,
            "Unknown piece summary type '" + pssmry + "'.");
      }
      if (!EntryRules.mayAddPiece(caller, pssmry)) {
        throw new AccessDeniedException(EntryMessages.ACCESS_DISALLOWED);
      }
      // add/P050.jsp hidden fields: an estimate's category, the waste return's scale site
      String forced = EntryRules.ESTIMATE_CATEGORIES.get(pssmry);
      if (forced != null) f.set(CATEGORY, forced);
      if (SummaryReturnRules.PSSMRY_WASTE_TYPE.equals(pssmry)) f.set(SCALE_SITE, EntryRules.WASTE_SCALE_SITE);

      HeaderValidator.Outcome outcome = validator.validatePiece(f, caller, null);
      throwIfAny(outcome.problems());

      boolean areaBased = SummaryReturnRules.PSSMRY_AREA_TYPE.equals(pssmry);
      String user = audit.user();
      Timestamp now = audit.now();
      long dcn = writer.nextId(SummaryReturnWriter.DCN_SEQ);
      Row header = newHeader(dcn, SummaryReturnType.PIECE, user, now)
          .set("volest_batch_id", f.lng(BATCH_SLIP_ID));
      // the licence type comes from the licence entered, even when BT15212 derives the licence
      String licenceType = validator.licenceType(f.get(SCALER_LICENCE));
      if (areaBased) {
        String mark = f.get(TIMBER_MARK);
        f.set(LOG_COUNT, "0"); // BT 15212: compatibility with existing functionality
        f.set(SCALE_SITE, lookups.areaBasedScaleSite(mark));
        f.set(SCALER_LICENCE, lookups.areaBasedLicence(mark));
        f.set(RETURN_NUMBER, areaBasedReturnNumber(mark));
      } else {
        f.set(AREA_CUT, null);
      }
      Row version = newVersion(dcn, f, user, now, true)
          .set("pssmry_type", pssmry).set("quant_lic_type_cd", licenceType)
          .set("input_scaler_license_num", null).set("period_start_date", null);
      return create(header, version, SummaryReturnType.PIECE);
    }

    /**
     * BT15212 {@code SummaryScaleReturnManagerBean.findAreaBasedReturnNo}: MAX(RETURN_NUMBER) + 1 of
     * the mark's piece returns, zero-padded to 4. Legacy defect kept: with no earlier return the
     * MAX is null, the code falls back to "1", and the first area-based return gets 0002.
     */
    String areaBasedReturnNumber(String mark) {
      String max = lookups.maxPieceReturnNumber(mark);
      int next = Integer.parseInt(max == null ? "1" : max.trim()) + 1;
      StringBuilder s = new StringBuilder(String.valueOf(next));
      while (s.length() < 4) s.insert(0, '0');
      return s.toString();
    }
  }

  /** P100 Add: {@code P100Action.addScaleReturn}. */
  @Component
  public static class AddWeight extends AddCommand {

    public AddWeight(Deps deps) {
      super(deps);
    }

    @Override
    public String id() {
      return "summaryReturns.addWeight";
    }

    @Override
    public String legacy() {
      return "P100 Add — P100Action.addScaleReturn, SummaryScaleReturnValidator.validateP100Form";
    }

    @Override
    public Capability capability() {
      return Capability.SUMMARY_RETURN_CONTROL;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      SummaryCaller caller = caller();
      if (!EntryRules.mayAddWeightOrSample(caller)) {
        throw new AccessDeniedException(EntryMessages.ACCESS_DISALLOWED);
      }
      HeaderForm f = HeaderForm.forAdd(SummaryReturnType.WEIGHT, body);
      throwIfAny(validator.validateWeight(f, null).problems());
      String user = audit.user();
      Timestamp now = audit.now();
      long dcn = writer.nextId(SummaryReturnWriter.DCN_SEQ);
      Row version = newVersion(dcn, f, user, now, false)
          .set("license_number", null).set("quant_lic_type_cd", null)
          .set("input_population", null).set("input_stratum", null).set("input_sampling_year", null)
          .set("period_start_date", null);
      return create(newHeader(dcn, SummaryReturnType.WEIGHT, user, now), version, SummaryReturnType.WEIGHT);
    }
  }

  /** P150 Add: {@code P150Action.addScaleReturn}. */
  @Component
  public static class AddSample extends AddCommand {

    public AddSample(Deps deps) {
      super(deps);
    }

    @Override
    public String id() {
      return "summaryReturns.addSample";
    }

    @Override
    public String legacy() {
      return "P150 Add — P150Action.addScaleReturn, SummaryScaleReturnValidator.validateP150Form";
    }

    @Override
    public Capability capability() {
      return Capability.SUMMARY_RETURN_CONTROL;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      SummaryCaller caller = caller();
      if (!EntryRules.mayAddWeightOrSample(caller)) {
        throw new AccessDeniedException(EntryMessages.ACCESS_DISALLOWED);
      }
      HeaderForm f = HeaderForm.forAdd(SummaryReturnType.SAMPLE, body);
      throwIfAny(validator.validateSample(f).problems());
      String user = audit.user();
      Timestamp now = audit.now();
      long dcn = writer.nextId(SummaryReturnWriter.DCN_SEQ);
      Row version = newVersion(dcn, f, user, now, false)
          .set("weigh_slip_number", f.get(WEIGH_SLIP))
          .set("quant_lic_type_cd", validator.licenceType(f.get(SCALER_LICENCE)))
          .set("check_scale_ind", null).set("input_scaler_license_num", null)
          .set("input_population", null).set("input_stratum", null).set("input_sampling_year", null)
          .set("pending_txn_comment", null);
      return create(newHeader(dcn, SummaryReturnType.SAMPLE, user, now), version, SummaryReturnType.SAMPLE);
    }
  }
}
