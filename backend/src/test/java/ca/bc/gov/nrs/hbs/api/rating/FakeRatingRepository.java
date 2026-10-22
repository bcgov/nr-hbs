package ca.bc.gov.nrs.hbs.api.rating;

import ca.bc.gov.nrs.hbs.api.exception.EntityNotFoundException;
import ca.bc.gov.nrs.hbs.api.rating.RatingRules.Interval;
import ca.bc.gov.nrs.hbs.api.rating.RatingRules.RateInterval;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** In-memory {@link RatingRepository} for the workflow tests: rows and lookup answers set per test. */
class FakeRatingRepository extends RatingRepository {

  static final Timestamp UPDATED = Timestamp.valueOf("2026-10-01 10:15:30");

  final Map<Long, Map<String, Object>> rules = new HashMap<>();
  final Map<Long, Map<String, Object>> rates = new HashMap<>();
  final Map<Long, Map<String, Object>> districtRates = new HashMap<>();
  final Map<Long, Map<String, Object>> wasteRates = new HashMap<>();

  final Set<String> timberMarks = new HashSet<>(Set.of("ABC123", "BCH001", "PRV001"));
  final Map<String, String> billedFileTypes = new HashMap<>(Map.of("ABC123", "A01", "BCH001", "B17", "PRV001", "B08"));
  final Map<String, Interval> spg = new HashMap<>();
  final Map<String, Interval> grades = new HashMap<>();
  final Map<String, List<String>> cutBlocks = new HashMap<>();
  final List<RateInterval> activeDistrictRates = new ArrayList<>();
  final List<Map<String, Object>> activeWasteRates = new ArrayList<>();

  long overlappingRules;
  long overrideRateCount = 1;
  /** Arguments of the last overlap count, for assertions. */
  List<Object> lastOverlapArgs;

  static Map<String, Object> row(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put(((String) kv[i]).toUpperCase(), kv[i + 1]);
    }
    return m;
  }

  static Timestamp ts(String day) {
    return Timestamp.valueOf(LocalDate.parse(day).atStartOfDay());
  }

  @Override
  public Map<String, Object> overrideRule(long id) {
    return found(rules, id);
  }

  @Override
  public Map<String, Object> overrideRate(long id) {
    return found(rates, id);
  }

  @Override
  public Map<String, Object> districtRate(long id) {
    return found(districtRates, id);
  }

  @Override
  public Map<String, Object> wasteRate(long id) {
    return found(wasteRates, id);
  }

  private static Map<String, Object> found(Map<Long, Map<String, Object>> rows, long id) {
    Map<String, Object> r = rows.get(id);
    if (r == null) throw new EntityNotFoundException(Map.class, "id", String.valueOf(id));
    return r;
  }

  @Override
  public long countOverlappingActiveRules(String timberMark, String deck, LocalDate processFrom, LocalDate processTo,
      LocalDate scaleFrom, LocalDate scaleTo) {
    lastOverlapArgs = java.util.Arrays.asList(timberMark, deck, processFrom, processTo, scaleFrom, scaleTo);
    return overlappingRules;
  }

  @Override
  public long countOverrideRates(long ruleId) {
    return overrideRateCount;
  }

  @Override
  public boolean timberMarkExists(String timberMark) {
    return timberMarks.contains(timberMark);
  }

  @Override
  public Optional<String> billedMarkFileType(String timberMark) {
    return Optional.ofNullable(billedFileTypes.get(timberMark));
  }

  static String spgKey(String species, String product, String grade) {
    return species + "|" + product + "|" + grade;
  }

  @Override
  public Optional<Interval> speciesProductGrade(String species, String product, String grade) {
    return Optional.ofNullable(spg.get(spgKey(species, product, grade)));
  }

  @Override
  public Optional<Interval> scaleGrade(String grade, String schedule) {
    return Optional.ofNullable(grades.get(schedule + "|" + grade));
  }

  @Override
  public List<RateInterval> activeDistrictRates(long orgUnitNo, String method, String species, String product,
      String grade) {
    return activeDistrictRates;
  }

  @Override
  public long countCutBlocks(String timberMark) {
    return cutBlocks.getOrDefault(timberMark, List.of()).size();
  }

  @Override
  public boolean cutBlockExists(String timberMark, String cutBlock) {
    return cutBlocks.getOrDefault(timberMark, List.of()).contains(cutBlock);
  }

  @Override
  public List<Map<String, Object>> activeWasteRates(String timberMark, String cutBlock, String species, String grade,
      LocalDate day, String rateSource) {
    return activeWasteRates;
  }
}
