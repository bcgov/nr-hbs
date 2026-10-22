package ca.bc.gov.nrs.hbs.api.summaryreturns.edits;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** {@link Lookups} answering from memory (no database). */
class FakeLookups extends Lookups {

  final Map<String, TimberMark> marks = new HashMap<>();
  final Map<String, Client> clients = new HashMap<>();
  final Map<String, ClientLocation> locations = new HashMap<>();
  final Map<String, ScaleSite> sites = new HashMap<>();
  final Set<String> licences = new HashSet<>();
  final Set<String> otherDescriptions = new HashSet<>();
  final Map<String, DateWindow> spg = new HashMap<>();
  final Map<String, DateWindow> grades = new HashMap<>();
  LocalDateTime policyDate = LocalDateTime.of(2008, 1, 1, 0, 0);
  String gradeSchedule = "C";
  final Map<String, Stratum> strata = new HashMap<>();
  final Map<String, Population> populations = new HashMap<>();
  final Set<String> dropouts = new HashSet<>();
  final List<RatioStatistic> ratios = new ArrayList<>();
  final Set<String> cutBlocks = new HashSet<>();
  final List<VolumeEstimate> estimates = new ArrayList<>();
  final List<Worksheet> worksheets = new ArrayList<>();
  boolean splitRate;
  boolean wasteRate = true;

  boolean harvestDateValid = true;
  int sameScaleDateCount = 1;
  double cruiseProfile = 100;
  WorksheetVals worksheetVals = new WorksheetVals(0, 100, 0);
  double activeVolumePerHectare = 100;
  double totalAreaCut = 40;
  double merchantableArea = 100;
  final List<CruiseRatio> cruiseRatios = new ArrayList<>();

  final List<String> called = new ArrayList<>();

  FakeLookups() {
    super(null);
  }

  @Override
  public Optional<TimberMark> timberMark(String mark) {
    called.add("timberMark " + mark);
    return Optional.ofNullable(marks.get(mark));
  }

  @Override
  public Optional<Client> client(String clientNumber) {
    return Optional.ofNullable(clients.get(clientNumber));
  }

  @Override
  public Optional<ClientLocation> clientLocation(String clientNumber, String clientLocnCode) {
    return Optional.ofNullable(locations.get(clientNumber + "/" + clientLocnCode));
  }

  @Override
  public Optional<ScaleSite> scaleSite(String id) {
    return Optional.ofNullable(sites.get(id));
  }

  @Override
  public boolean quantLicenseExists(String licenseNumber, String type) {
    return licences.contains(licenseNumber + "/" + type);
  }

  @Override
  public boolean otherDescriptionTypeExists(String code) {
    return otherDescriptions.contains(code);
  }

  @Override
  public Optional<DateWindow> speciesProductGrade(String species, String product, String grade) {
    return Optional.ofNullable(spg.get(species + "/" + product + "/" + grade));
  }

  @Override
  public Optional<DateWindow> scaleGrade(String grade, String schedule) {
    return Optional.ofNullable(grades.get(grade + "/" + schedule));
  }

  @Override
  public Optional<LocalDateTime> interiorMpbPolicyDate() {
    return Optional.ofNullable(policyDate);
  }

  @Override
  public String gradeScheduleType(String timberMark, LocalDate scaleDate) {
    return gradeSchedule;
  }

  @Override
  public Optional<Stratum> stratum(String population, String stratum, Long year) {
    return Optional.ofNullable(strata.get(population + "/" + stratum + "/" + year));
  }

  @Override
  public Optional<Population> population(String population, Long year) {
    return Optional.ofNullable(populations.get(population + "/" + year));
  }

  @Override
  public boolean finalBillableDropoutMark(String population, Long year, String mark) {
    return dropouts.contains(population + "/" + year + "/" + mark);
  }

  @Override
  public List<RatioStatistic> latestRatios(String population, String stratum, Long year) {
    return ratios;
  }

  @Override
  public boolean cutBlockExists(String cutBlockId, String timberMark) {
    return cutBlocks.contains(cutBlockId + "/" + timberMark);
  }

  @Override
  public boolean markHasCutBlock(String timberMark) {
    return cutBlocks.stream().anyMatch(c -> c.endsWith("/" + timberMark));
  }

  @Override
  public List<VolumeEstimate> activeVolumeEstimates(String type, LocalDate scaleDate, String licence, String returnNo) {
    return estimates;
  }

  @Override
  public List<Worksheet> confirmedWorksheets(String timberMark, LocalDate scaleDate) {
    return worksheets;
  }

  @Override
  public boolean hasSplitRate(String timberMark, LocalDate start, LocalDate end) {
    called.add("hasSplitRate " + start + " " + end);
    return splitRate;
  }

  @Override
  public boolean hasWasteRate(String cutBlockId, String timberMark, LocalDateTime start, LocalDateTime end) {
    called.add("hasWasteRate " + cutBlockId + " " + start + " " + end);
    return wasteRate;
  }

  @Override
  public boolean areaBasedHarvestDateValid(long dcn, long version) {
    return harvestDateValid;
  }

  @Override
  public double areaBasedCruiseProfile(long dcn, long version) {
    return cruiseProfile;
  }

  @Override
  public Optional<WorksheetVals> areaBasedWorksheetVals(long dcn, long version) {
    return Optional.ofNullable(worksheetVals);
  }

  @Override
  public double areaBasedActiveWorksheetVolumePerHectare(long dcn, long version) {
    return activeVolumePerHectare;
  }

  @Override
  public int areaBasedSameScaleDateCount(long dcn, long version) {
    return sameScaleDateCount;
  }

  @Override
  public double areaBasedTotalAreaCut(long dcn, long version) {
    return totalAreaCut;
  }

  @Override
  public double areaBasedTotalMerchantableArea(long dcn, long version) {
    return merchantableArea;
  }

  @Override
  public List<CruiseRatio> areaBasedCruiseRatios(long dcn, long version) {
    return cruiseRatios;
  }
}
