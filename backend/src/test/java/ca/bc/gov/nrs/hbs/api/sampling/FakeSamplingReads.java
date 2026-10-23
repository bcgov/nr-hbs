package ca.bc.gov.nrs.hbs.api.sampling;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** In-memory {@link SamplingReads} for the workflow tests (rows are what the tests put in). */
class FakeSamplingReads implements SamplingReads {

  final Map<Long, SamplePlanRow> plans = new LinkedHashMap<>();
  final Map<Long, StratumPlanRow> stratumPlans = new LinkedHashMap<>();
  final Map<Long, List<Map<String, Object>>> compositions = new HashMap<>();
  final Map<String, Map<String, Object>> populations = new HashMap<>();
  final Map<String, List<Map<String, Object>>> strata = new HashMap<>();
  final Set<String> userDistricts = new HashSet<>();
  final Map<Long, OrgUnit> orgUnits = new HashMap<>();
  final Set<String> clientLocations = new HashSet<>();
  final Map<String, Dates> spg = new HashMap<>();
  final Map<String, Dates> grades = new HashMap<>();
  final Map<String, DocumentCounts> documents = new HashMap<>();
  final Map<String, DocumentCounts> strandedSingle = new HashMap<>();
  final List<Stranded> strandedMultiple = new ArrayList<>();
  final Map<String, List<Map<String, Object>>> ratios = new HashMap<>();

  static String key(Object... parts) {
    StringBuilder sb = new StringBuilder();
    for (Object p : parts) sb.append(p).append('|');
    return sb.toString();
  }

  @Override
  public SamplePlanRow plan(long planId) {
    SamplePlanRow p = plans.get(planId);
    return p == null ? null : p.copy();
  }

  @Override
  public List<StratumPlanRow> stratumPlans(long planId) {
    return stratumPlans.values().stream().filter(s -> s.planId == planId)
        .sorted(Comparator.comparing(s -> s.stratumNumber)).map(StratumPlanRow::copy).toList();
  }

  @Override
  public StratumPlanRow stratumPlan(long stratumPlanId) {
    StratumPlanRow s = stratumPlans.get(stratumPlanId);
    return s == null ? null : s.copy();
  }

  @Override
  public List<Map<String, Object>> compositions(long stratumPlanId) {
    return new ArrayList<>(compositions.getOrDefault(stratumPlanId, List.of()));
  }

  @Override
  public Map<String, Object> population(String populationNumber, long samplingYear) {
    return populations.get(key(populationNumber, samplingYear));
  }

  @Override
  public List<SamplePlanRow> plans(String status, String populationNumber, long samplingYear,
      String planAbbreviation, Boolean acceptedPlan) {
    return plans.values().stream()
        .filter(p -> p.populationNumber.equals(populationNumber) && p.samplingYear == samplingYear)
        .filter(p -> status == null || status.equals(p.samplePlanStatusCode))
        .filter(p -> planAbbreviation == null || planAbbreviation.equals(p.planAbreviation))
        .filter(p -> acceptedPlan == null || (acceptedPlan ? "Y" : "N").equals(p.acceptedPlanInd))
        .map(SamplePlanRow::copy).toList();
  }

  @Override
  public List<Map<String, Object>> strata(String populationNumber, long samplingYear) {
    return strata.getOrDefault(key(populationNumber, samplingYear), List.of());
  }

  @Override
  public boolean userHasDistrict(String userId, long orgUnitNo) {
    return userDistricts.contains(key(userId, orgUnitNo));
  }

  @Override
  public OrgUnit orgUnit(long orgUnitNo) {
    return orgUnits.get(orgUnitNo);
  }

  @Override
  public boolean clientLocationExists(String clientNumber, String clientLocnCode) {
    return clientLocations.contains(key(clientNumber, clientLocnCode));
  }

  @Override
  public Dates speciesProductGrade(String species, String product, String grade) {
    return spg.get(key(species, product, grade));
  }

  @Override
  public Dates scheduleGrade(String grade, String gradeSchedule) {
    return grades.get(key(grade, gradeSchedule));
  }

  @Override
  public DocumentCounts documentCounts(String populationNumber, String stratumNumber, long samplingYear) {
    return documents.getOrDefault(key(populationNumber, stratumNumber, samplingYear),
        new DocumentCounts(0, 0, 0, 0));
  }

  @Override
  public DocumentCounts strandedSingle(String populationNumber, String stratumNumber, long samplingYear,
      LocalDate effective, LocalDate expiry) {
    return strandedSingle.getOrDefault(key(populationNumber, stratumNumber, samplingYear),
        new DocumentCounts(0, 0, 0, 0));
  }

  @Override
  public List<Stranded> strandedMultiple(String planAbbreviation, String populationNumber, long samplingYear) {
    return strandedMultiple;
  }

  @Override
  public List<Map<String, Object>> latestRatios(String populationNumber, String stratumNumber, long samplingYear) {
    return ratios.getOrDefault(key(populationNumber, stratumNumber, samplingYear), List.of());
  }
}
