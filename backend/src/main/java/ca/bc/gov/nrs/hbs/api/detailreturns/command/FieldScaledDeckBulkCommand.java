package ca.bc.gov.nrs.hbs.api.detailreturns.command;

import ca.bc.gov.nrs.hbs.api.detailreturns.DetailAuthority.Caller;
import ca.bc.gov.nrs.hbs.api.detailreturns.command.FieldScaledDeckReturns.Found;
import ca.bc.gov.nrs.hbs.api.detailreturns.command.FieldScaledDeckReturns.Selection;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.VersionState;
import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowCommand;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * P842 / P843 / P844 bulk actions on the returns of a field scaled deck
 * ({@code $V/ivs/config/P840Action.bulkActionForSampleTally / ForWeighSlip /
 * ForLogTally} → {@code bulkRequestCommonAction}):
 * <ul>
 *   <li>P842 "Cancel Issued Sample Tallies in Selected Strata"
 *       ({@code doCancelReturn}): each issued sample tally of the selected strata
 *       is cancelled when {@code isCancelAllowed && isActive};</li>
 *   <li>P843 "Clear Field Scale Flag for Weigh Slips in Selected Strata" and
 *       P844 "Clear Field Scale Flag for Selected Log Tallies"
 *       ({@code doClearFiledScaleFlag}): a holdable (or not-to-be-billed) active
 *       version is held, its FIELD_SCALE_IND set to N and the held version
 *       released; an active held version is just set to N and released.</li>
 * </ul>
 * The returns of each selected row are found as legacy did
 * ({@link FieldScaledDeckReturns}), and each return runs in its own savepoint
 * (legacy autocommitted each workflow call) and is reported PROCESSED /
 * NOTPROCESSED / ERROR ({@code DetailScaleDocumentProcessStatus}).
 *
 * <p>Who: the button was rendered only when the timber mark's district is one
 * of the user's associated districts ({@code IsTimberMarkAssociatedWithUserTag} →
 * {@code DacHelper.isTimberMarkAssocitedWithUser}), and each row's check box only
 * then too (the HBS3R842..844 {@code slect} column); the same check is applied
 * here before anything runs.
 *
 * <p>Body: {@code {docType: S|W|P, timberMark, fieldScaleDeckId ('*' = all decks),
 * rows: [{psy: "POPU-ST-YYYY" | "Unknown"} (S, W) | {eventCode} (P)]}};
 * answer: {@code {results: [{ddn, version, label, status, message}]}}.
 */
@Component
@Slf4j
public class FieldScaledDeckBulkCommand implements WorkflowCommand {

  static final String PROCESSED = "PROCESSED";
  static final String NOT_PROCESSED = "NOTPROCESSED";
  static final String ERROR = "ERROR";

  /** HBS3R842/843 PSY of the rows without a population (legacy {@code UNKNOWN}). */
  static final String UNKNOWN = "Unknown";

  private final DetailCommandSupport s;
  private final Function<Selection, List<Found>> finder;
  private final TransactionTemplate nested;

  @Autowired
  public FieldScaledDeckBulkCommand(DetailCommandSupport s, FieldScaledDeckReturns returns,
                                    ObjectProvider<PlatformTransactionManager> tx) {
    this(s, returns::find, tx);
  }

  FieldScaledDeckBulkCommand(DetailCommandSupport s, Function<Selection, List<Found>> finder,
                             ObjectProvider<PlatformTransactionManager> tx) {
    this.s = s;
    this.finder = finder;
    PlatformTransactionManager tm = tx == null ? null : tx.getIfAvailable();
    if (tm == null) {
      this.nested = null;
    } else {
      this.nested = new TransactionTemplate(tm);
      this.nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    }
  }

  @Override
  public String id() {
    return "billing.fieldScaledDecks.bulk";
  }

  @Override
  public String legacy() {
    return "P842/P843/P844 bulk actions — P840Action.bulkRequestCommonAction (doCancelReturn / doClearFiledScaleFlag)";
  }

  @Override
  public Capability capability() {
    return Capability.FIELD_SCALED_DECKS;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> execute(Map<String, Object> body) {
    String docType = DetailCommandSupport.required(body, "docType").toUpperCase(Locale.ROOT);
    if (!List.of("S", "W", "P").contains(docType)) {
      throw new WorkflowValidationException("docType", "docType must be S, W or P");
    }
    String mark = DetailCommandSupport.required(body, "timberMark").toUpperCase(Locale.ROOT);
    Object rawDeck = body.get("fieldScaleDeckId");
    String deck = rawDeck == null || rawDeck.toString().isBlank() ? "*" : rawDeck.toString().trim()
        .toUpperCase(Locale.ROOT);
    Object raw = body.get("rows");
    if (!(raw instanceof List<?> selected) || selected.isEmpty()) {
      throw new WorkflowValidationException("rows", "Select at least one row.");
    }
    Caller caller = s.caller();
    if (!s.authority().hasDistrictAuthorization(mark, caller)) {
      throw new AccessDeniedException("hbs.no_access_right: timber mark " + mark
          + " is not in one of your associated districts");
    }
    String user = s.user();
    List<Map<String, Object>> results = new ArrayList<>();
    for (Object o : selected) {
      Map<String, Object> row = o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
      Selection selection = selection(docType, mark, deck, row);
      String group = "P".equals(docType) ? "event " + selection.eventCode() : "Pop/Str/Year " + row.get("psy");
      List<Found> found = finder.apply(selection);
      if (found.isEmpty()) {
        results.add(result(null, null, group, NOT_PROCESSED, "No returns found."));
      }
      for (Found f : found) {
        try {
          String status = inRowTransaction(() -> processReturn(docType, f, user));
          results.add(result(f.ddn(), f.version(), group, status, null));
        } catch (RuntimeException ex) {
          log.info("Field scaled deck bulk {} on {} failed: {}", docType, f, ex.getMessage());
          results.add(result(f.ddn(), f.version(), group, ERROR, ex.getMessage()));
        }
      }
    }
    return Map.of("results", results);
  }

  private static Map<String, Object> result(String ddn, Long version, String group, String status, String message) {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("ddn", ddn);
    r.put("version", version);
    r.put("label", ddn == null ? group : ddn + " v" + version + " (" + group + ")");
    r.put("status", status);
    if (message != null) r.put("message", message);
    return r;
  }

  /** {@code setCriteria}: the row's PSY (S, W) or event type (P). */
  static Selection selection(String docType, String mark, String deck, Map<String, Object> row) {
    if ("P".equals(docType)) {
      Object code = row.get("eventCode");
      if (code == null || code.toString().isBlank()) {
        throw new WorkflowValidationException("rows", "eventCode is required for log tally rows");
      }
      return new Selection(docType, mark, deck, false, null, null, null, code.toString().trim()
          .toUpperCase(Locale.ROOT));
    }
    Object rawPsy = row.get("psy");
    String psy = rawPsy == null ? "" : rawPsy.toString().trim();
    if (UNKNOWN.equalsIgnoreCase(psy)) {
      return new Selection(docType, mark, deck, true, null, null, null, null);
    }
    // P840Action.setCriteria: psy.substring(0,4) / (5,7) / (8,12) of "POPU-ST-YYYY".
    if (psy.length() < 12) {
      throw new WorkflowValidationException("rows", "Not a Pop/Stratum/Year: " + psy);
    }
    long year;
    try {
      year = Long.parseLong(psy.substring(8, 12));
    } catch (NumberFormatException e) {
      throw new WorkflowValidationException("rows", "Not a Pop/Stratum/Year: " + psy);
    }
    return new Selection(docType, mark, deck, true, psy.substring(0, 4), psy.substring(5, 7), year, null);
  }

  private String inRowTransaction(Supplier<String> work) {
    return nested == null ? work.get() : nested.execute(st -> work.get());
  }

  String processReturn(String docType, Found f, String user) {
    DetailDocument doc = s.store().find(f.ddn(), f.version())
        .orElseThrow(() -> new IllegalStateException("Detail return " + f.ddn() + " v" + f.version() + " not found"));
    s.fence(doc);
    VersionState v = doc.version().state();
    if ("S".equals(docType)) {
      // doCancelReturn
      if (!(v.isCancelAllowed() && v.isActive())) return NOT_PROCESSED;
      s.run(() -> s.workflow().cancel(doc, user));
      return PROCESSED;
    }
    // doClearFiledScaleFlag
    if ((v.isHoldAllowed() || v.isNotToBeBilled()) && v.isActive()) {
      s.run(() -> {
        DetailDocument held = s.workflow().hold(doc, user);
        clearFlag(held);
        s.workflow().release(held, user);
      });
      return PROCESSED;
    }
    if (v.isHeld() && v.isActive()) {
      s.run(() -> {
        clearFlag(doc);
        s.workflow().release(doc, user);
      });
      return PROCESSED;
    }
    return NOT_PROCESSED;
  }

  /** {@code setFieldScaleInd(false)} on the weigh slip / log tally version. */
  private static void clearFlag(DetailDocument doc) {
    if (doc.type() == DocType.WEIGH_SLIP || doc.type() == DocType.LOG_TALLY) {
      doc.version().row().setYes("field_scale_ind", false);
    }
  }
}
