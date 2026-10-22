package ca.bc.gov.nrs.hbs.api.summaryreturns;

import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;

/**
 * In-memory summary returns for the workflow tests: a repository serving rows from maps, an access
 * helper with fixed data-domain answers, fake release edits and generated-summary details, and the
 * real writer on a {@link RecordingTableApi}.
 */
public class SummaryFixtures {

  public final RecordingTableApi api = new RecordingTableApi();
  public final Audit audit = new Audit("HBSBATCH");
  public final FakeRepository repo = new FakeRepository();
  public final FakeAccess access = new FakeAccess();
  public final FakeEdits edits = new FakeEdits();
  public final FakeDetails details = new FakeDetails();
  public final SummaryReturnWriter writer = new SummaryReturnWriter(api, repo, audit);
  public final SummaryReturnWorkflows workflows =
      new SummaryReturnWorkflows(repo, writer, audit, provider(edits), provider(details));
  public final SummaryButtons buttons = new SummaryButtons(access);

  public static final Timestamp LOADED = Timestamp.valueOf("2026-10-01 09:30:00");

  public static <T> ObjectProvider<T> provider(T bean) {
    return new ObjectProvider<>() {
      @Override
      public T getObject(Object... args) {
        return bean;
      }

      @Override
      public T getIfAvailable() {
        return bean;
      }

      @Override
      public T getIfUnique() {
        return bean;
      }

      @Override
      public T getObject() {
        return bean;
      }
    };
  }

  // ── Rows ─────────────────────────────────────────────────────────────────

  public Row header(long dcn, SummaryReturnType type, String state, boolean generated) {
    Row h = new Row()
        .set("document_control_number", dcn).set("edi_doc_id", null).set("edi_batch_id", null)
        .set("volest_batch_id", null).set("hbs_return_type_code", type.code())
        .set("scale_return_state_code", state).set("generated_summary_ind", generated ? "Y" : "N")
        .set("entry_userid", "IDIR\\ORIG").set("entry_timestamp", LOADED)
        .set("update_userid", "IDIR\\ORIG").set("update_timestamp", LOADED);
    repo.headers.put(dcn, h);
    return h;
  }

  /** A version row with every column of the type's table. */
  public Row version(SummaryReturnType type, long dcn, long version, String status, boolean active) {
    Row v = new Row().set("document_control_number", dcn).set("version", version)
        .set("scl_rtn_version_state_code", status).set("scl_rtn_category_code", "PR")
        .set("scale_date", Timestamp.valueOf("2026-09-15 00:00:00")).set("scale_site_id_nmbr", "S123")
        .set("license_number", "K123").set("quant_lic_type_cd", "S").set("timber_mark", "ABC123")
        .set("client_number_paid_by", "00012345").set("client_locn_code_paid_by", "00")
        .set("client_number_copied_to", null).set("client_locn_code_copied_to", null)
        .set("input_timber_mark", "ABC123")
        .set("active_version_ind", active ? "Y" : "N").set("queued_for_print_ind", "N").set("visibility_ind", "Y")
        .set("pending_txn_comment", null)
        .set("entry_userid", "IDIR\\ORIG").set("entry_timestamp", LOADED)
        .set("update_userid", "IDIR\\ORIG").set("update_timestamp", LOADED);
    switch (type) {
      case PIECE -> v.set("pssmry_type", SummaryReturnRules.PSSMRY_DEFAULT_TYPE).set("return_number", "12")
          .set("log_count", 10).set("input_scaler_license_num", "K123").set("place_of_scale", "Here")
          .set("number_of_chains", null).set("camp_boom_number", null).set("area_cut", null)
          .set("number_of_sections", null).set("period_start_date", null).set("field_scale_deck_id", null)
          .set("cut_block_id", null).set("version_comment", null);
      case WEIGHT -> v.set("population_number", "1001").set("stratum_number", "01").set("sampling_year", 2026)
          .set("input_population", null).set("input_stratum", null).set("input_sampling_year", null)
          .set("period_start_date", null).set("field_scale_deck_id", null);
      case SAMPLE -> v.set("return_number", "7").set("load_arrival_number", "L1").set("weigh_slip_number", "W1")
          .set("population_number", "1001").set("stratum_number", "01").set("sampling_year", 2026)
          .set("log_count", 4).set("net_weight", 30).set("check_scale_ind", "N")
          .set("input_scaler_license_num", null).set("input_population", null).set("input_stratum", null)
          .set("input_sampling_year", null);
    }
    repo.versions.put(key(type, dcn, version), v);
    return v;
  }

  public Row pieceSegregation(long dcn, long version, long id, String species, int pieces, String volume) {
    Row s = new Row().set("segregation_id", id).set("document_control_number", dcn).set("version", version)
        .set("psseg_type", SummaryReturnRules.SEG_TYPE_PRIMARY).set("other_description_type_code", null)
        .set("scale_species_code", species).set("scale_product_code", "LO").set("scale_grade_code", "U")
        .set("timber_mark", null).set("number_of_pieces", pieces).set("volume", new BigDecimal(volume))
        .set("nmv_ind", "N").set("sb4_ind", "N").set("avoidable_ind", "N")
        .set("input_species", species).set("input_product", "LO").set("input_grade", "U")
        .set("input_bcomb_timber_mark", null).set("archive_timber_mark", null).set("input_other_desc", null)
        .set("entry_userid", "IDIR\\ORIG").set("entry_timestamp", LOADED)
        .set("update_userid", "IDIR\\ORIG").set("update_timestamp", LOADED);
    repo.segs.computeIfAbsent(key(SummaryReturnType.PIECE, dcn, version), k -> new ArrayList<>()).add(s);
    return s;
  }

  public Row notation(SummaryReturnType type, long dcn, long version, long ntnId, long number, String text) {
    Row n = new Row().set("ntn_id", ntnId).set("notation_type_code", "NOT").set("version", version)
        .set("document_control_number", dcn).set("notation_number", number).set("text", text)
        .set("entry_userid", "IDIR\\ORIG").set("entry_timestamp", LOADED)
        .set("update_userid", "IDIR\\ORIG").set("update_timestamp", LOADED);
    repo.notations.computeIfAbsent(key(type, dcn, version), k -> new ArrayList<>()).add(n);
    return n;
  }

  public Row dailyLoad(long dcn, long version, long day, int loads, int weight) {
    Row d = new Row().set("document_control_number", dcn).set("version", version).set("day", day)
        .set("number_of_loads", loads).set("weight", weight)
        .set("entry_userid", "IDIR\\ORIG").set("entry_timestamp", LOADED)
        .set("update_userid", "IDIR\\ORIG").set("update_timestamp", LOADED);
    repo.loads.computeIfAbsent(key(SummaryReturnType.WEIGHT, dcn, version), k -> new ArrayList<>()).add(d);
    return d;
  }

  static String key(SummaryReturnType type, long dcn, long version) {
    return type.code() + ":" + dcn + ":" + version;
  }

  /** The caller of a test. */
  public static SummaryCaller caller(String... roles) {
    boolean ministry = java.util.Arrays.stream(roles).noneMatch(r -> r.startsWith("HBS_CLI") || r.startsWith("HBS_SPC"));
    return new SummaryCaller("IDIR\\TESTER", Set.of(roles), ministry, ministry ? "" : "00012345");
  }

  // ── Fakes ────────────────────────────────────────────────────────────────

  public static class FakeRepository extends SummaryReturnRepository {
    public final Map<Long, Row> headers = new HashMap<>();
    public final Map<String, Row> versions = new HashMap<>();
    public final Map<String, List<Row>> segs = new HashMap<>();
    public final Map<String, List<Row>> notations = new HashMap<>();
    public final Map<String, List<Row>> loads = new HashMap<>();
    public final Map<String, Row> latestTxns = new HashMap<>();
    public Row cyclicBilling;
    public List<Long> versionErrors = new ArrayList<>();
    public List<Long> segErrors = new ArrayList<>();

    public FakeRepository() {
      super(null);
    }

    private List<Row> versionsOf(SummaryReturnType type, long dcn) {
      return versions.entrySet().stream().filter(e -> e.getKey().startsWith(type.code() + ":" + dcn + ":"))
          .map(Map.Entry::getValue).sorted(java.util.Comparator.comparing((Row r) -> r.lng("version"))).toList();
    }

    @Override
    public Optional<Row> header(long dcn) {
      return Optional.ofNullable(headers.get(dcn)).map(Row::copy);
    }

    @Override
    public Optional<Row> version(SummaryReturnType type, long dcn, long version) {
      return Optional.ofNullable(versions.get(key(type, dcn, version))).map(Row::copy);
    }

    @Override
    public Optional<Row> activeVersion(SummaryReturnType type, long dcn) {
      return versionsOf(type, dcn).stream().filter(r -> r.flag("active_version_ind"))
          .reduce((a, b) -> b).map(Row::copy);
    }

    @Override
    public long maxVersion(SummaryReturnType type, long dcn) {
      return versionsOf(type, dcn).stream().mapToLong(r -> r.lng("version")).max().orElse(0);
    }

    @Override
    public Optional<Long> previousInvoicedVersion(SummaryReturnType type, long dcn, long version) {
      return versionsOf(type, dcn).stream()
          .filter(r -> Set.of("ISS", "CAN").contains(r.str("scl_rtn_version_state_code")) && r.lng("version") < version)
          .map(r -> r.lng("version")).reduce((a, b) -> b);
    }

    @Override
    public Optional<Long> nextPendingVersion(SummaryReturnType type, long dcn, long version) {
      return versionsOf(type, dcn).stream()
          .filter(r -> "PND".equals(r.str("scl_rtn_version_state_code")) && r.lng("version") > version)
          .map(r -> r.lng("version")).findFirst();
    }

    @Override
    public long versionCount(SummaryReturnType type, long dcn) {
      return versionsOf(type, dcn).size();
    }

    @Override
    public List<Row> segregations(SummaryReturnType type, long dcn, long version) {
      return segs.getOrDefault(key(type, dcn, version), List.of()).stream().map(Row::copy).toList();
    }

    @Override
    public List<Row> notations(SummaryReturnType type, long dcn, long version) {
      return notations.getOrDefault(key(type, dcn, version), List.of()).stream().map(Row::copy).toList();
    }

    @Override
    public List<Row> dailyLoads(SummaryReturnType type, long dcn, long version) {
      return loads.getOrDefault(key(type, dcn, version), List.of()).stream().map(Row::copy).toList();
    }

    @Override
    public List<Long> versionErrorIds(SummaryReturnType type, long dcn) {
      return versionErrors;
    }

    @Override
    public List<Long> segregationErrorIds(SummaryReturnType type, List<Long> segregationIds) {
      return segregationIds.isEmpty() ? List.of() : segErrors;
    }

    @Override
    public List<Long> dailyLoadErrorIds(long dcn, long version, List<Long> days) {
      return List.of();
    }

    @Override
    public Optional<Row> cyclicBilling(String population, String stratum, Long year, String timberMark) {
      return Optional.ofNullable(cyclicBilling);
    }

    @Override
    public Optional<Row> latestTransaction(SummaryReturnType type, long dcn, long version) {
      return Optional.ofNullable(latestTxns.get(key(type, dcn, version)));
    }
  }

  public static class FakeAccess extends SummaryReturnAccess {
    public Set<String> siteDomain = Set.of();
    public Set<String> districtMarks = Set.of();
    public Set<String> licences = Set.of();
    public String markHolder = "00012345";

    public FakeAccess() {
      super(null);
    }

    @Override
    public boolean siteInUserDomain(String userId, String scaleSite) {
      return siteDomain.contains(scaleSite);
    }

    @Override
    public boolean siteInUserDistricts(String userId, String scaleSite) {
      return siteDomain.contains(scaleSite);
    }

    @Override
    public boolean markInUserDistricts(String userId, String timberMark) {
      return districtMarks.contains(timberMark);
    }

    @Override
    public boolean licenceInUserAuthKeys(String userId, String licence) {
      return licences.contains(licence);
    }

    @Override
    public Optional<String> markHolder(String timberMark) {
      return Optional.ofNullable(markHolder);
    }

    @Override
    public Optional<String> siteOwner(String scaleSite) {
      return Optional.of("00099999");
    }

    @Override
    public Optional<String> stratumOwner(String population, String stratum, Long year) {
      return Optional.empty();
    }
  }

  public static class FakeEdits implements ReleaseEdits {
    public List<EditError> errors = List.of();
    public List<EditError> clientErrors = List.of();
    public int calls;
    public List<Row> generatedSegregations = List.of();

    @Override
    public List<EditError> applyEditsAndValidate(SummaryReturn ret) {
      calls++;
      return errors;
    }

    @Override
    public List<EditError> applyEditsForClient(SummaryReturn ret) {
      calls++;
      return clientErrors;
    }

    @Override
    public List<EditError> generateAreaBasedSegregations(SummaryReturn ret, String user) {
      ret.segregations().clear();
      generatedSegregations.forEach(s -> ret.segregations().add(s.copy()));
      ret.version().set("log_count", ret.segregations().size());
      return List.of();
    }
  }

  public static class FakeDetails implements GeneratedSummaryDetails {
    public boolean eligible = true;
    public final List<String> calls = new ArrayList<>();

    @Override
    public boolean isEligibleForRateCorrection(SummaryReturn generated) {
      return eligible;
    }

    @Override
    public void resetDetailDocuments(SummaryReturn generated, String user) {
      calls.add("reset " + generated.dcn());
    }

    @Override
    public void extendRateCorrection(SummaryReturn awaitingApproval, String user) {
      calls.add("extend " + awaitingApproval.dcn() + "/" + awaitingApproval.versionNo());
    }

    @Override
    public Amounts correctableAmounts(SummaryReturn generated) {
      return new Amounts(new BigDecimal("12.345"), new BigDecimal("678.90"));
    }
  }
}
