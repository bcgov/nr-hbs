package ca.bc.gov.nrs.hbs.api.summaryreturns.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryCaller;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryFixtures;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException.Problem;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * The entry-command test bed: the shared {@link SummaryFixtures} (in-memory repository, access,
 * release edits, the real writer on a {@link RecordingTableApi}) plus {@link FakeLookups}, and the
 * commands built with a fixed caller.
 */
class EntryFixture {

  final SummaryFixtures f = new SummaryFixtures();
  final RecordingTableApi api = f.api;
  final FakeLookups lookups = new FakeLookups();
  final HeaderValidator validator = new HeaderValidator(lookups, f.access, f.repo);
  final EntryCommand.Deps deps = new EntryCommand.Deps(f.repo, f.writer, f.access, f.audit, lookups, validator,
      SummaryFixtures.provider(f.edits));
  SummaryCaller caller = SummaryFixtures.caller(ca.bc.gov.nrs.hbs.api.security.HbsRoles.SUMM_DATA_CTL);

  // ── Commands with the fixture's caller ───────────────────────────────────

  AddReturnCommands.AddPiece addPiece() {
    return new AddReturnCommands.AddPiece(deps) {
      @Override
      protected SummaryCaller caller() {
        return caller;
      }
    };
  }

  AddReturnCommands.AddWeight addWeight() {
    return new AddReturnCommands.AddWeight(deps) {
      @Override
      protected SummaryCaller caller() {
        return caller;
      }
    };
  }

  AddReturnCommands.AddSample addSample() {
    return new AddReturnCommands.AddSample(deps) {
      @Override
      protected SummaryCaller caller() {
        return caller;
      }
    };
  }

  UpdateReturnCommand update() {
    return new UpdateReturnCommand(deps) {
      @Override
      protected SummaryCaller caller() {
        return caller;
      }
    };
  }

  SaveSegregationsCommand segregations() {
    return new SaveSegregationsCommand(deps) {
      @Override
      protected SummaryCaller caller() {
        return caller;
      }
    };
  }

  SaveNotationsCommand notations() {
    return new SaveNotationsCommand(deps) {
      @Override
      protected SummaryCaller caller() {
        return caller;
      }
    };
  }

  SaveDailyLoadsCommand dailyLoads() {
    return new SaveDailyLoadsCommand(deps) {
      @Override
      protected SummaryCaller caller() {
        return caller;
      }
    };
  }

  // ── Bodies ───────────────────────────────────────────────────────────────

  /** A valid P050 SMRYDOC add body (the fake lookups know every value). */
  static Map<String, Object> pieceBody() {
    Map<String, Object> b = new LinkedHashMap<>();
    b.put("pssmryType", "SMRYDOC");
    b.put("category", "PR");
    b.put("scalerLicence", "K123");
    b.put("timberMark", "ABC123");
    b.put("returnNumber", "12");
    b.put("scaleSite", "S123");
    b.put("scaleDate", "2026-09-15");
    b.put("logCount", "10");
    b.put("placeOfScale", "here");
    return b;
  }

  static Map<String, Object> weightBody() {
    Map<String, Object> b = new LinkedHashMap<>();
    b.put("category", "PR");
    b.put("populationNumber", "1001");
    b.put("stratumNumber", "01");
    b.put("samplingYear", "2026");
    b.put("timberMark", "ABC123");
    b.put("scaleSite", "S123");
    b.put("scaleDate", "2026-09-15");
    return b;
  }

  static Map<String, Object> sampleBody() {
    Map<String, Object> b = new LinkedHashMap<>();
    b.put("category", "PR");
    b.put("populationNumber", "1001");
    b.put("stratumNumber", "01");
    b.put("samplingYear", "2026");
    b.put("timberMark", "ABC123");
    b.put("scalerLicence", "K123");
    b.put("loadArrivalNumber", "3");
    b.put("netWeight", "30");
    b.put("returnNumber", "7");
    b.put("logCount", "4");
    b.put("scaleSite", "S123");
    b.put("weighSlipNumber", "w1");
    b.put("scaleDate", "2026-09-15");
    return b;
  }

  /** The keys of an existing return as the SPA loaded it. */
  static Map<String, Object> keys(long dcn, long version) {
    Map<String, Object> b = new HashMap<>();
    b.put("documentControlNumber", dcn);
    b.put("version", version);
    b.put("updateTimestamp", "2026-10-01T09:30:00");
    return b;
  }

  /** An existing return: header + active version (state doc/version), as SummaryFixtures builds them. */
  void existing(SummaryReturnType type, long dcn, String doc, String ver) {
    f.header(dcn, type, doc, false);
    f.version(type, dcn, 1, ver, true);
  }

  // ── Assertions ───────────────────────────────────────────────────────────

  static List<Problem> problems(Supplier<?> action) {
    WorkflowValidationException ex = catchThrowableOfType(WorkflowValidationException.class, action::get);
    assertThat(ex).as("expected a validation failure").isNotNull();
    return ex.problems();
  }

  static void assertProblem(Supplier<?> action, String field, String message) {
    assertThat(problems(action)).contains(new Problem(field, message));
  }

  // ── Fake lookups ─────────────────────────────────────────────────────────

  static class FakeLookups extends EntryLookups {
    final Map<String, Mark> marks = new HashMap<>();
    final Set<String> sites = new HashSet<>(Set.of("S123", "S456", "ZZZ"));
    final Set<String> sLicences = new HashSet<>(Set.of("K123"));
    final Set<String> rLicences = new HashSet<>(Set.of("R900"));
    final Set<String> clientLocations = new HashSet<>(Set.of("00012345/00", "00099999/00"));
    final Set<String> categories = new HashSet<>(Set.of("PR", "BC", "WA", "CR", "FI", "OT", "FB", "NB", "CH"));
    final Set<String> strata = new HashSet<>(Set.of("1001/01/2026", "1002/02/2026"));
    final Map<String, String> strataOwners = new HashMap<>(Map.of("1001/01/2026", "00099999/00"));
    final Map<String, String> ratioTypes = new HashMap<>();
    final Map<Long, String> paperBatches = new HashMap<>(Map.of(77L, "OPN", 78L, "CLR"));
    final Map<String, Set<String>> cutBlocks = new HashMap<>();
    final Set<String> spg = new HashSet<>(Set.of("F/LO/U", "H/LO/U", "C/LO/2", "F/XM/U"));
    final Map<String, String> maxReturn = new HashMap<>();
    String areaSite = "S456";
    String areaLicence = "A999";

    FakeLookups() {
      super(null);
      marks.put("ABC123", new Mark("ABC123", "A01", "N", "00012345", "00"));
      marks.put("CRS001", new Mark("CRS001", "A01", "Y", "00012345", "00"));
      marks.put("PRV001", new Mark("PRV001", "B08", "N", "00012345", "00"));
      marks.put("BCOMB2", new Mark("BCOMB2", "B17", "N", "00055555", "00"));
      marks.put("NEW999", new Mark("NEW999", "A01", "N", "00099999", "00"));
    }

    @Override
    public Optional<Mark> timberMark(String timberMark) {
      return Optional.ofNullable(marks.get(timberMark));
    }

    @Override
    public boolean scaleSiteExists(String scaleSite) {
      return sites.contains(scaleSite);
    }

    @Override
    public boolean licenceExists(String licence, String type) {
      return ("S".equals(type) ? sLicences : rLicences).contains(licence);
    }

    @Override
    public boolean clientLocationExists(String clientNumber, String clientLocnCode) {
      return clientLocations.contains(clientNumber + "/" + clientLocnCode);
    }

    @Override
    public boolean billToDerivable(String timberMark) {
      return timberMark(timberMark).map(m -> clientLocationExists(m.clientNumber(), m.clientLocnCode())).orElse(false);
    }

    @Override
    public boolean copyToDerivable(String population, String stratum, Long year) {
      String owner = strataOwners.get(population + "/" + stratum + "/" + year);
      return owner != null && clientLocations.contains(owner);
    }

    @Override
    public boolean categoryCodeExists(String code) {
      return categories.contains(code);
    }

    @Override
    public boolean speciesProductGradeExists(String species, String product, String grade) {
      return spg.contains(species + "/" + product + "/" + grade);
    }

    @Override
    public String editErrorMessage(String code, String category) {
      return code + " description of " + category;
    }

    @Override
    public boolean stratumExists(String population, String stratum, long year) {
      return strata.contains(population + "/" + stratum + "/" + year);
    }

    @Override
    public Optional<String> populationRatioType(String population, long year) {
      return Optional.ofNullable(ratioTypes.get(population + "/" + year));
    }

    @Override
    public Optional<String> paperBatchStatus(long batchId) {
      return Optional.ofNullable(paperBatches.get(batchId));
    }

    @Override
    public long cutBlockCount(String timberMark) {
      return cutBlocks.getOrDefault(timberMark, Set.of()).size();
    }

    @Override
    public boolean cutBlockExists(String cutBlockId, String timberMark) {
      return cutBlocks.getOrDefault(timberMark, Set.of()).contains(cutBlockId);
    }

    @Override
    public String areaBasedLicence(String timberMark) {
      return areaLicence;
    }

    @Override
    public String maxPieceReturnNumber(String timberMark) {
      return maxReturn.get(timberMark);
    }

    @Override
    public String areaBasedScaleSite(String timberMark) {
      return areaSite;
    }

    @Override
    protected long count(String sql, MapSqlParameterSource params) {
      throw new IllegalStateException("unexpected SQL in a test: " + sql);
    }
  }
}
