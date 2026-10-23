package ca.bc.gov.nrs.hbs.api.summaryreturns;

import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.AWAITING_APPROVAL;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.CANCEL_IN_PROGRESS;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.CANCEL_WITH_REPLACE_NO_CHANGES;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.CANCEL_WITH_REPLACE_WITH_CHANGES;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.NOT_TO_BE_BILLED;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.READY;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.REPLACE_NO_CHANGES_IN_PROGRESS;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.REPLACE_WITH_CHANGES_IN_PROGRESS;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryButtons.APPROVE_RATE_CORRECTION;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryButtons.CANCEL;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryButtons.CANCEL_REPLACE_WITHOUT_CHANGES;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryButtons.CANCEL_REPLACE_WITH_CHANGES;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryButtons.DISCARD;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryButtons.REJECT_RATE_CORRECTION;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryButtons.RELEASE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryButtons.REPLACE_WITHOUT_CHANGES;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryButtons.REPLACE_WITH_CHANGES;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryButtons.REQUEST_RATE_CORRECTION;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * {@code summaryReturns.preview} — read only, writes nothing. Two legacy pieces:
 * <ul>
 *   <li>{@code availableActions}: the buttons the view page would show the caller
 *       ({@link SummaryButtons}); the SPA shows only these.</li>
 *   <li>{@code returns}: the P054/P104/P154 confirmation page ({@code FetchConfirmationDataTag}) for
 *       {@code action}: the versions involved and the transactions the action will produce — the
 *       latest real transaction plus the "faux" cancel / replace transactions the batch will create.</li>
 * </ul>
 * Body: {@code documentControlNumber}, {@code version} (the version on screen; non-active versions
 * get no actions), optional {@code action} (a button id, e.g. {@code cancel}).
 */
@Component
public class SummaryPreviewCommand extends SummaryReturnCommand {

  private static final String CREDIT = "CR";
  private static final String DEBIT = "DR";
  private static final String TXN_CANCEL = "CAN";
  private static final String TXN_ORIGINAL = "ORG";
  private static final String TXN_REPLACE_WITH_CHANGES = "RWC";
  private static final String TXN_REPLACE_WITH_NO_CHANGES = "RWN";

  private final SummaryButtons buttons;
  private final ObjectProvider<GeneratedSummaryDetails> details;

  public SummaryPreviewCommand(SummaryReturnRepository repo, SummaryReturnWriter writer, SummaryReturnAccess access,
      Audit audit, SummaryButtons buttons, ObjectProvider<GeneratedSummaryDetails> details) {
    super(repo, writer, access, audit);
    this.buttons = buttons;
    this.details = details;
  }

  @Override
  public String id() {
    return "summaryReturns.preview";
  }

  @Override
  public String legacy() {
    return "P054/P104/P154 FetchConfirmationDataTag; SummaryReturnFieldsTag";
  }

  @Override
  public Capability capability() {
    return Capability.SCALE_RETURNS_VIEW;
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    SummaryCaller caller = caller();
    long dcn = requiredLong(body, "documentControlNumber");
    Long version = optionalLong(body, "version");
    SummaryReturn ret = version == null ? repo.loadActive(dcn) : repo.load(dcn, version);
    access.requireVisible(caller, ret);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("documentControlNumber", ret.dcn());
    out.put("version", ret.versionNo());
    out.put("availableActions", List.copyOf(buttons.available(caller, ret)));
    String action = text(body, "action");
    if (action != null) {
      out.put("returns", preview(ret, action));
    }
    return out;
  }

  /** {@code FetchConfirmationDataTag.createTxnList}. */
  List<Map<String, Object>> preview(SummaryReturn current, String action) {
    String doc = current.docState();
    String ver = current.versionState();
    SummaryReturn last;
    SummaryReturn previous = null;
    Map<String, Object> lastTxn = null;
    List<Map<String, Object>> lastTxns = new ArrayList<>();
    List<Map<String, Object>> previousTxns = new ArrayList<>();
    boolean generated = current.generated();

    if (CANCEL_WITH_REPLACE_WITH_CHANGES.equals(doc)
        && (READY.equals(ver) || (current.areaBased() && NOT_TO_BE_BILLED.equals(ver))) && !generated) {
      last = repo.loadNextPending(current).orElse(current);
      previous = current;
      lastTxn = latest(current).orElse(null);
      add(previousTxns, lastTxn);
    } else if ((REPLACE_WITH_CHANGES_IN_PROGRESS.equals(doc) || CANCEL_WITH_REPLACE_WITH_CHANGES.equals(doc)) && !generated) {
      last = current;
      previous = repo.loadPreviousInvoiced(current).orElse(null);
      lastTxn = previous == null ? null : latest(previous).orElse(null);
      add(previousTxns, lastTxn);
    } else if (CANCEL_WITH_REPLACE_NO_CHANGES.equals(doc) && AWAITING_APPROVAL.equals(ver)) {
      last = current;
      previous = repo.loadPreviousInvoiced(current).orElse(null);
      lastTxn = previous == null ? null : latest(previous).orElse(null);
      add(previousTxns, lastTxn);
    } else {
      last = current;
      lastTxn = latest(current).orElse(null);
      add(lastTxns, lastTxn);
    }

    String effective = DISCARD.equals(action) ? actionFromStatus(current) : action;
    long seq = lastTxn == null ? 0 : ((Number) lastTxn.get("eventSequence")).longValue();
    Object invoice = lastTxn == null ? null : lastTxn.get("invoiceNumber");

    if (CANCEL.equals(effective) || CANCEL_REPLACE_WITH_CHANGES.equals(effective)
        || CANCEL_REPLACE_WITHOUT_CHANGES.equals(effective)) {
      lastTxns.add(faux(seq + 1, CREDIT, TXN_CANCEL, invoice, amount(lastTxn, "volume"), amount(lastTxn, "value"),
          amount(lastTxn, "weight")));
    }
    if (REQUEST_RATE_CORRECTION.equals(effective) || APPROVE_RATE_CORRECTION.equals(effective)
        || REJECT_RATE_CORRECTION.equals(effective)) {
      SummaryReturn source = REQUEST_RATE_CORRECTION.equals(effective) ? last : previous;
      GeneratedSummaryDetails.Amounts amounts = source == null ? null : details.getObject().correctableAmounts(source);
      lastTxns.add(faux(seq + 1, CREDIT, TXN_CANCEL, invoice, amounts == null ? null : amounts.volume(),
          amounts == null ? null : amounts.value(), null));
    }
    if (CANCEL_REPLACE_WITH_CHANGES.equals(effective) || REPLACE_WITH_CHANGES.equals(effective)
        || CANCEL_REPLACE_WITHOUT_CHANGES.equals(effective) || REPLACE_WITHOUT_CHANGES.equals(effective)) {
      long event = seq + 1;
      if (CANCEL_REPLACE_WITH_CHANGES.equals(effective) || CANCEL_REPLACE_WITHOUT_CHANGES.equals(effective)) {
        event++;
      }
      // legacy shows "Replace" (RWC) for both with and without changes
      lastTxns.add(faux(event, DEBIT, TXN_REPLACE_WITH_CHANGES, null, null, null, null));
    }
    if (REQUEST_RATE_CORRECTION.equals(effective) || APPROVE_RATE_CORRECTION.equals(effective)
        || REJECT_RATE_CORRECTION.equals(effective)) {
      lastTxns.add(faux(seq + 2, DEBIT, TXN_REPLACE_WITH_NO_CHANGES, null, null, null, null));
    }
    if (RELEASE.equals(effective)) {
      if (REPLACE_WITH_CHANGES_IN_PROGRESS.equals(doc)) {
        lastTxns.add(faux(seq + 1, DEBIT, TXN_REPLACE_WITH_CHANGES, null, null, null, null));
      } else if (CANCEL_WITH_REPLACE_WITH_CHANGES.equals(doc)) {
        previousTxns.add(faux(seq + 1, CREDIT, TXN_CANCEL, invoice, amount(lastTxn, "volume"),
            amount(lastTxn, "value"), amount(lastTxn, "weight")));
        lastTxns.add(faux(seq + 2, DEBIT, TXN_REPLACE_WITH_CHANGES, null, null, null, null));
      }
    }

    List<Map<String, Object>> result = new ArrayList<>();
    result.add(version(last, lastTxns));
    if (CANCEL_WITH_REPLACE_WITH_CHANGES.equals(doc) && previous != null
        && ((DISCARD.equals(action) && !generated) || RELEASE.equals(action))) {
      result.add(version(previous, previousTxns));
    }
    return result;
  }

  /** {@code FetchConfirmationDataTag.getActionTypeFromStatus} (discard previews what the discard undoes). */
  static String actionFromStatus(SummaryReturn ret) {
    if (ret.generated()) return "";
    return switch (ret.docState()) {
      case CANCEL_IN_PROGRESS -> CANCEL;
      case CANCEL_WITH_REPLACE_NO_CHANGES -> CANCEL_REPLACE_WITHOUT_CHANGES;
      case REPLACE_NO_CHANGES_IN_PROGRESS -> REPLACE_WITHOUT_CHANGES;
      case CANCEL_WITH_REPLACE_WITH_CHANGES, REPLACE_WITH_CHANGES_IN_PROGRESS -> RELEASE;
      default -> "";
    };
  }

  private Optional<Map<String, Object>> latest(SummaryReturn ret) {
    return repo.latestTransaction(ret.type(), ret.dcn(), ret.versionNo()).map(t -> {
      return txn(t.lng("event_sequence"), t.str("debit_credit_type"), t.str("hbs_txn_type_code"),
          t.get("invoice_number"), t.num("volume"), t.num("value"), t.num("weight"));
    });
  }

  private static void add(List<Map<String, Object>> list, Map<String, Object> txn) {
    if (txn != null) list.add(txn);
  }

  private static BigDecimal amount(Map<String, Object> txn, String key) {
    return txn == null ? null : (BigDecimal) txn.get(key);
  }

  private static Map<String, Object> faux(long seq, String drCr, String type, Object invoice, BigDecimal volume,
      BigDecimal value, BigDecimal weight) {
    return txn(seq, drCr, type, invoice, volume, value, weight);
  }

  private static Map<String, Object> txn(Long seq, String drCr, String type, Object invoice, BigDecimal volume,
      BigDecimal value, BigDecimal weight) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("eventSequence", seq);
    m.put("drCr", drCr);
    m.put("txnType", type);
    m.put("txnTypeDesc", description(type));
    m.put("invoiceNumber", invoice);
    m.put("volume", volume);
    m.put("value", value);
    m.put("weight", weight);
    return m;
  }

  /** {@code FetchConfirmationDataTag.getTxnTypeDescription}. */
  static String description(String type) {
    if (TXN_CANCEL.equals(type)) return "Cancel";
    if (TXN_ORIGINAL.equals(type)) return "Original";
    if (TXN_REPLACE_WITH_CHANGES.equals(type) || TXN_REPLACE_WITH_NO_CHANGES.equals(type)) return "Replace";
    return type;
  }

  private static Map<String, Object> version(SummaryReturn ret, List<Map<String, Object>> txns) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("documentControlNumber", ret.dcn());
    m.put("version", ret.versionNo());
    m.put("returnStateCode", ret.docState());
    m.put("status", ret.versionState());
    m.put("transactions", txns);
    return m;
  }
}
