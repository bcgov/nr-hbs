package ca.bc.gov.nrs.hbs.api.profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test doubles for the profile workflows: an in-memory {@link ProfileReads},
 * a fixed clock / user ({@link FixedAudit}) and a caller with chosen roles.
 * "Now" is 2026-10-21 10:00 Pacific unless a test moves it.
 */
abstract class ProfileFixtures {

  static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 10, 0, 0);

  final RecordingTableApi api = new RecordingTableApi();
  final FakeReads reads = new FakeReads();
  final FixedAudit audit = new FixedAudit();
  final FakeCaller caller = new FakeCaller();

  static Timestamp ts(String isoDateTime) {
    return Timestamp.valueOf(LocalDateTime.parse(isoDateTime));
  }

  static Timestamp day(String isoDate) {
    return Timestamp.valueOf(LocalDate.parse(isoDate).atStartOfDay());
  }

  static Map<String, Object> body(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  /** The legacy messages of a rejected workflow (and that nothing was written). */
  List<String> messages(Runnable run) {
    WorkflowValidationException ex = catchThrowableOfType(run::run, WorkflowValidationException.class);
    assertThat(ex).as("expected a WorkflowValidationException").isNotNull();
    assertThat(api.calls).as("nothing written").isEmpty();
    return ex.problems().stream().map(WorkflowValidationException.Problem::message).toList();
  }

  static final class FixedAudit extends Audit {
    LocalDateTime now = NOW;

    FixedAudit() {
      super("HBSBATCH");
    }

    @Override
    public String user() {
      return "IDIR\\TESTER";
    }

    @Override
    public Timestamp now() {
      return Timestamp.valueOf(now);
    }
  }

  static final class FakeCaller extends ProfileCaller {
    final Set<String> roles = new HashSet<>();
    boolean ministry = true;
    String client = "";

    @Override
    public String userId() {
      return "IDIR\\TESTER";
    }

    @Override
    public boolean hasRole(String canonicalRole) {
      return roles.contains(canonicalRole);
    }

    @Override
    public boolean isMinistry() {
      return ministry;
    }

    @Override
    public String clientNumber() {
      return client;
    }
  }

  /** In-memory ProfileReads; tests fill the fields they need. */
  static final class FakeReads implements ProfileReads {
    final Map<String, Map<String, Object>> scaleSites = new HashMap<>();
    final Set<String> siteDomains = new HashSet<>();
    final List<Long> districts = new ArrayList<>();
    final Set<String> tradingPartners = new HashSet<>();
    final List<Range> scaleSiteRanges = new ArrayList<>();
    LocalDateTime nextScaleSite;
    final Map<Long, Map<String, Object>> tps = new HashMap<>();

    final Map<String, Map<String, Object>> populations = new HashMap<>();
    final Map<Long, Map<String, Object>> populationProfiles = new HashMap<>();
    int overlaps;
    final List<Object[]> overlapCalls = new ArrayList<>();

    final Set<String> clientLocations = new HashSet<>();
    final List<Range> markHolderRanges = new ArrayList<>();
    LocalDateTime nextMarkHolder;
    final Map<Long, Map<String, Object>> markHolders = new HashMap<>();

    final List<Range> deliveryRanges = new ArrayList<>();
    Next nextDelivery;
    int receivers;
    int activeReceivers;
    final Map<Long, Map<String, Object>> deliveries = new HashMap<>();

    Map<String, Object> appraisal;
    final List<String> appraisedSpecies = new ArrayList<>();
    final Set<String> speciesGrades = new HashSet<>();
    final Map<String, Map<String, Object>> ratios = new HashMap<>();

    @Override
    public Map<String, Object> scaleSite(String scaleSiteId) {
      return scaleSites.get(scaleSiteId);
    }

    @Override
    public boolean siteDataDomain(String userId, String scaleSiteId) {
      return siteDomains.contains(userId + "|" + scaleSiteId);
    }

    @Override
    public List<Long> districtDataDomains(String userId) {
      return districts;
    }

    @Override
    public boolean tradingPartnerExists(String clientNumber, String clientLocnCode) {
      return tradingPartners.contains(clientNumber + "|" + clientLocnCode);
    }

    @Override
    public List<Range> scaleSiteProfileRanges(String scaleSiteId, String returnType, String docType,
        String clientNumber, String clientLocnCode) {
      return scaleSiteRanges;
    }

    @Override
    public LocalDateTime nextScaleSiteProfile(String scaleSiteId, String returnType, String docType,
        String clientNumber, String clientLocnCode, LocalDateTime effective) {
      return nextScaleSite;
    }

    @Override
    public Map<String, Object> tradingPartnerSubmitter(long tpsId) {
      return tps.get(tpsId);
    }

    @Override
    public Map<String, Object> population(String populationNumber, long samplingYear) {
      return populations.get(populationNumber + "|" + samplingYear);
    }

    @Override
    public Map<String, Object> populationProfile(long popprofId) {
      return populationProfiles.get(popprofId);
    }

    @Override
    public int overlappingPopulationProfiles(String populationNumber, long samplingYear, LocalDate effective,
        LocalDate expiry, Long excludeId) {
      overlapCalls.add(new Object[] {populationNumber, samplingYear, effective, expiry, excludeId});
      return overlaps;
    }

    @Override
    public boolean clientLocationExists(String clientNumber, String clientLocnCode) {
      return clientLocations.contains(clientNumber + "|" + clientLocnCode);
    }

    @Override
    public List<Range> markHolderRanges(String clientNumber, String clientLocnCode, String returnType) {
      return markHolderRanges;
    }

    @Override
    public LocalDateTime nextMarkHolderProfile(String clientNumber, String clientLocnCode, String returnType,
        LocalDateTime effective) {
      return nextMarkHolder;
    }

    @Override
    public Map<String, Object> markHolderProfile(long mhprofId) {
      return markHolders.get(mhprofId);
    }

    @Override
    public List<Range> deliveryRanges(String clientNumber, String clientLocnCode) {
      return deliveryRanges;
    }

    @Override
    public Next nextDeliveryProfile(String clientNumber, String clientLocnCode, LocalDateTime effective) {
      return nextDelivery;
    }

    @Override
    public int documentReceivers(String clientNumber, String clientLocnCode) {
      return receivers;
    }

    @Override
    public int activeDocumentReceivers(String clientNumber, String clientLocnCode) {
      return activeReceivers;
    }

    @Override
    public Map<String, Object> deliveryProfile(long dlvprofId) {
      return deliveries.get(dlvprofId);
    }

    @Override
    public Map<String, Object> appraisal(String timberMark, LocalDate effectiveDate) {
      return appraisal;
    }

    @Override
    public List<String> appraisedSpecies(String timberMark, LocalDate effectiveDate) {
      return appraisedSpecies;
    }

    @Override
    public boolean validSpeciesGrade(String speciesCode, String gradeCode) {
      return speciesGrades.contains(speciesCode + "|" + gradeCode);
    }

    @Override
    public Map<String, Object> ratio(String timberMark, LocalDate effectiveDate, String speciesCode,
        String gradeCode) {
      return ratios.get(speciesCode + "|" + gradeCode);
    }
  }
}
