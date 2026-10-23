package ca.bc.gov.nrs.hbs.api.detailreturns;

import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * In-memory {@link ReferenceData} for unit tests: everything unknown unless
 * registered. {@link #valid()} registers a usable mark (ABC123), site (S001),
 * licences (1234, 5678), owner client, stratum and rate.
 */
public class StubReferenceData implements ReferenceData {

  public final Map<String, TimberMarkRef> marks = new HashMap<>();
  public final Map<String, ScaleSiteRef> sites = new HashMap<>();
  public final Set<String> cutBlocks = new HashSet<>();
  public final Set<String> licences = new HashSet<>();
  public final Map<String, StratumRef> strata = new HashMap<>();
  public final Map<String, PopulationRef> populations = new HashMap<>();
  public final Map<String, ClientLocationRef> clients = new HashMap<>();
  public final Map<String, RateCheck> rates = new HashMap<>();
  public final Map<String, List<WorksheetRef>> worksheets = new HashMap<>();
  public final Map<String, String> gradeSchedules = new HashMap<>();

  public static StubReferenceData valid() {
    StubReferenceData r = new StubReferenceData();
    r.marks.put("ABC123", new TimberMarkRef("ABC123", "A01", "N", "B", "00012345", "00", 1L, 1L, "RCO", "A",
        "HI", null, null, null));
    r.sites.put("S001", new ScaleSiteRef("S001", 1L, "00012345", "00", "Main Site", "BRAND1"));
    r.licences.add("1234");
    r.licences.add("5678");
    r.clients.put("00012345/00", new ClientLocationRef("00012345", "00", "ACT", "N"));
    r.rates.put("ABC123", RateCheck.HAS_RATE);
    r.gradeSchedules.put("ABC123", "C");
    return r;
  }

  @Override
  public Optional<TimberMarkRef> timberMark(String timberMark) {
    return Optional.ofNullable(timberMark == null ? null : marks.get(timberMark));
  }

  @Override
  public Optional<ScaleSiteRef> scaleSite(String scaleSiteId) {
    return Optional.ofNullable(scaleSiteId == null ? null : sites.get(scaleSiteId));
  }

  @Override
  public Optional<ScaleSiteRef> scaleSiteByTimberBrand(String timberBrand) {
    return sites.values().stream().filter(s -> timberBrand != null && timberBrand.equals(s.timberBrand()))
        .findFirst();
  }

  @Override
  public boolean cutBlockExists(String cutBlockId, String timberMark) {
    return cutBlocks.contains(timberMark + "/" + cutBlockId);
  }

  @Override
  public boolean scalerLicenceExists(String licenseNumber) {
    return licences.contains(licenseNumber);
  }

  @Override
  public Optional<StratumRef> stratum(String population, String stratum, Long samplingYear) {
    return Optional.ofNullable(strata.get(population + "/" + stratum + "/" + samplingYear));
  }

  @Override
  public Optional<PopulationRef> population(String population, Long samplingYear) {
    return Optional.ofNullable(populations.get(population + "/" + samplingYear));
  }

  @Override
  public Optional<ClientLocationRef> clientLocation(String clientNumber, String clientLocnCode) {
    return Optional.ofNullable(clients.get(clientNumber + "/" + clientLocnCode));
  }

  @Override
  public RateCheck rateCheck(String timberMark, Date scaleDate, String fieldScaleDeckId) {
    return rates.getOrDefault(timberMark, RateCheck.NO_RATE);
  }

  @Override
  public List<WorksheetRef> confirmedWorksheets(String timberMark, Date scaleDate) {
    return worksheets.getOrDefault(timberMark, new ArrayList<>());
  }

  @Override
  public String gradeScheduleType(String timberMark, Date scaleDate) {
    return gradeSchedules.get(timberMark);
  }
}
