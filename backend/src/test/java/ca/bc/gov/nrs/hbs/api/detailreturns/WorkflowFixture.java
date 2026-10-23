package ca.bc.gov.nrs.hbs.api.detailreturns;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.EditError;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.detailreturns.store.DetailStore;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.DetailValidator;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.DetailValidators;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ValidationContext;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A detail workflow over an in-memory "database" and a recording table API,
 * with a scripted validator (the real validators have their own tests): the
 * edit errors and billable flag the validator reports are set by the test.
 */
public class WorkflowFixture {

  public final InMemoryDetailRows rows = new InMemoryDetailRows();
  public final RecordingTableApi api = new SyncingApi();
  public final DetailStore store = new DetailStore(rows, api, new Audit("HBSBATCH"));
  public final List<String> errorCodes = new ArrayList<>();
  public boolean billable = true;
  public int validations;

  public final DetailValidators validators = new DetailValidators(new StubReferenceData(), null, null, null) {
    @Override
    public DetailValidator of(DetailDocument document, DetailStore s, LocalDate today) {
      return new Scripted(document, new ValidationContext(new StubReferenceData(), s, today));
    }
  };

  public final DetailWorkflow workflow = new DetailWorkflow(store, validators, () -> LocalDate.of(2026, 10, 21));

  /** Records calls and applies document/version writes to the in-memory rows, as Oracle would. */
  class SyncingApi extends RecordingTableApi {
    @Override
    protected java.util.Map<String, Object> execute(ca.bc.gov.nrs.hbs.api.workflow.ProcSignatures.Signature sig,
                                                    List<Object> values, Integer returnSqlType) {
      var out = super.execute(sig, values, returnSqlType);
      var call = calls.get(calls.size() - 1);
      Row r = new Row();
      call.args().forEach((k, v) -> r.set(k.startsWith("i_") ? k.substring(2) : k, v));
      String name = sig.name();
      if (name.equals("HBS_STORE_DTL_SCL_DOCUMENT") || name.equals("HBS_CREATE_DTL_SCL_DOCUMENT")) {
        rows.documents.put(r.str("detail_document_number"), r);
      } else if (name.equals("HBS_STORE_LOG_TALLY") || name.equals("HBS_CREATE_LOG_TALLY")) {
        rows.versions.removeIf(x -> x.str("detail_document_number").equals(r.str("detail_document_number"))
            && x.lng("version").equals(r.lng("version")));
        rows.versions.add(r);
      }
      return out;
    }
  }

  class Scripted extends DetailValidator {
    Scripted(DetailDocument d, ValidationContext ctx) {
      super(d, ctx);
    }

    @Override
    protected void validate() {
      validations++;
      errorCodes.forEach(c -> errors.add(EditError.version("SCL", c)));
    }

    @Override
    protected void validatFieldScaleDeckId() {
    }

    @Override
    protected void validateAndCopyInputFields() {
    }

    @Override
    public boolean isBillableScaleReturn() {
      return billable;
    }
  }

  /** A log tally document + one version in the given states, with an optional log line. */
  public Row logTally(String ddn, String docState, long version, String status, boolean active) {
    rows.addDocument(ddn, DocType.LOG_TALLY, docState);
    Row v = rows.addVersion(ddn, version, status, active)
        .set("scale_event_type_code", "PS").set("scale_date", java.sql.Timestamp.valueOf("2026-10-01 00:00:00"))
        .set("scale_site_id_nmbr", "S001").set("timber_mark", "ABC123").set("primary_license_number", "1234")
        .set("return_number", 56).set("log_count", 1);
    return v;
  }

  public void setDocumentState(String ddn, String state) {
    rows.documents.get(ddn).set("scale_return_state_code", state);
  }

  public List<RecordingTableApi.Call> calls(String proc) {
    return api.callsTo(proc);
  }

  public RecordingTableApi.Call last(String proc) {
    List<RecordingTableApi.Call> c = api.callsTo(proc);
    return c.get(c.size() - 1);
  }
}
