package ca.bc.gov.nrs.hbs.api.publicaccess;

import ca.bc.gov.nrs.hbs.api.exception.EntityNotFoundException;
import ca.bc.gov.nrs.hbs.api.query.QueryDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter;
import ca.bc.gov.nrs.hbs.api.query.QueryRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import ca.bc.gov.nrs.hbs.api.queries.HarvestHistoryRules;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The whitelist of registry queries the public (anonymous) read-only area may
 * run — the functions the legacy app showed to its {@code Public_User}
 * ({@code include/mainmenu_public.jsp}; see docs/public-access.md).
 *
 * <p>Each public query is <b>derived from the registered query of the same id</b>
 * (same reviewed SQL, same FOI {@code CASE} expressions), then narrowed:
 * <ul>
 *   <li>only the listed criteria are accepted (anything else is ignored);</li>
 *   <li>{@code maxRows} is capped at {@code hbs.public.max-rows};</li>
 *   <li>a query with a client fence ({@code clientScope}) is refused at start-up
 *       unless the entry says the fence is deliberately dropped because the
 *       legacy public site listed those rows to anyone (issued documents —
 *       client numbers only, documents severed on download);</li>
 *   <li>legacy form rules that bound a public search are checked server-side
 *       ({@link #validate}).</li>
 * </ul>
 * Rows are always produced for {@link ca.bc.gov.nrs.hbs.api.query.Viewer#ANONYMOUS},
 * so every FOI {@code CASE} severs individual clients' names and addresses.
 *
 * <p>Adding a public query: add an entry here, check its SQL severs every
 * individual client's name / location name / address through the viewer binds,
 * and add the public screen in {@code frontend/src/pages/public/publicScreens.ts}.
 */
@Component
public class PublicQueries {

  private static final java.time.ZoneId PACIFIC = java.time.ZoneId.of("America/Vancouver");

  /** One whitelisted query. {@code criteria} null = every filter of the base query. */
  private record Entry(String id, Set<String> criteria, List<String> requireOneOf,
      boolean dropClientScope, Consumer<Map<String, String>> rule) {}

  private static Entry all(String id) {
    return new Entry(id, null, List.of(), false, p -> { });
  }

  /**
   * Issued documents (P449/P450): legacy listed them to anyone (no client fence;
   * client numbers only, no names). The Transmission ID criterion came only from
   * the logged-in P453 links, so it is not public. Legacy P450 required a date
   * range (validation.xml {@code dateRangeRequired}); P449 needed only the document number.
   */
  private static final Entry ISSUED_DOCUMENTS = new Entry("issuedDocuments.search",
      Set.of("documentType", "statementNumber", "scaleDateFrom", "scaleDateTo", "issueDateFrom", "issueDateTo",
          "timberMark", "scaleSite", "population", "stratum", "samplingYear", "regionScaled", "regionHarvested",
          "clientNumber", "sendToClientNumber", "copyToClientNumber", "clientLocation"),
      List.of("statementNumber", "scaleDateFrom", "scaleDateTo", "issueDateFrom", "issueDateTo"),
      true, p -> { });

  private static final List<Entry> ENTRIES = List.of(
      // Code lists the public criteria use (reference data).
      all("codes.orgUnits"),
      all("codes.queries.fileTypes"),
      all("codes.queries.species"),
      all("codes.queries.products"),
      all("codes.queries.grades"),
      all("codes.sampling.planStatuses"),

      // Harvest Reports — By Date of Invoice / By Date of Scale (P431, P441).
      new Entry("harvestHistory.billing", null, List.of(), false,
          p -> historyRange(p.get("billingFrom"), p.get("billingTo"))),
      new Entry("harvestHistory.scaling", null, List.of(), false,
          p -> historyRange(p.get("scalingFrom"), p.get("scalingTo"))),

      // Invoice / Ratio, Sample and Volume Statement copies (P449, P450 → P451…P476).
      ISSUED_DOCUMENTS,

      // Timber Mark Information (P480 / P481).
      all("timberMarks.search"),
      all("timberMarks.detail"),
      all("timberMarks.appraisals"),
      all("timberMarks.species"),
      all("timberMarks.stumpageRates"),
      all("timberMarks.spgRates"),

      // Stratum Advisor (P850): sampling plans, populations, strata — Active plans only
      // for anonymous viewers (HBS3R880 PLAN_VISIBLE with no viewer client).
      all("sampling.plans.search"),
      all("sampling.plans.detail"),
      all("sampling.stratumPlans.list"),
      all("sampling.stratumPlans.detail"),
      all("sampling.stratumComposition.list"),
      all("sampling.planComparison.list"),
      all("sampling.populations.search"),
      all("sampling.strata.search"),
      all("sampling.populationStats.search"),
      all("sampling.stratumStats.search"));

  private final Map<String, QueryDefinition> definitions = new LinkedHashMap<>();
  private final Map<String, Entry> entries = new LinkedHashMap<>();

  public PublicQueries(QueryRegistry registry, @Value("${hbs.public.max-rows:1000}") int maxRows) {
    for (Entry e : ENTRIES) {
      QueryDefinition base = registry.query(e.id());
      if (base.clientScope() != null && !e.dropClientScope()) {
        throw new IllegalStateException("Public query " + e.id() + " is client-fenced; it cannot be public "
            + "unless its entry deliberately drops the fence (docs/public-access.md)");
      }
      List<QueryFilter> filters = base.filters().stream()
          .filter(f -> e.criteria() == null || e.criteria().contains(f.param()))
          .toList();
      definitions.put(e.id(), new QueryDefinition(base.id(), base.legacyScreen(), base.capability(), base.sql(),
          filters, base.sortColumns(), base.defaultOrderBy(), null, Math.min(base.maxRows(), maxRows)));
      entries.put(e.id(), e);
    }
  }

  /** The public form of a query; 404 for anything not whitelisted. */
  public QueryDefinition query(String id) {
    QueryDefinition q = definitions.get(id);
    if (q == null) {
      throw new EntityNotFoundException(QueryDefinition.class, "id", id);
    }
    return q;
  }

  public Collection<QueryDefinition> all() {
    return definitions.values();
  }

  /** Server-side form rules for a public search (400 with the legacy wording). */
  public void validate(String id, Map<String, String> params) {
    Entry e = entries.get(id);
    if (e == null) {
      throw new EntityNotFoundException(QueryDefinition.class, "id", id);
    }
    if (!e.requireOneOf().isEmpty() && e.requireOneOf().stream().noneMatch(n -> StringUtils.hasText(params.get(n)))) {
      // validation.xml P450Form "dateRangeRequired" → error.billingorscalerange.required
      // (P449, the single-document search, needs only the statement number).
      throw new IllegalArgumentException("You must specify either issue or scale date range for your search.");
    }
    e.rule().accept(params);
  }

  /**
   * The month-interval rules of {@code MarkMonthlyBillingAction.validateForm}
   * ({@code getLimitDate}, {@code isBillingDateValid}, {@code isValidDateRange}),
   * shared with the signed-in screens ({@link HarvestHistoryRules}); the first
   * problem is the 400 message. The signed-in screens also check the marks,
   * licences, client and species/product/grade ({@code queries.QueriesCriteriaGuard}).
   */
  static void historyRange(String fromText, String toText) {
    List<WorkflowValidationException.Problem> problems =
        HarvestHistoryRules.interval("from", fromText, "to", toText, LocalDate.now(PACIFIC));
    if (!problems.isEmpty()) {
      throw new IllegalArgumentException(problems.get(0).message());
    }
  }
}
