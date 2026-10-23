package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import ca.bc.gov.nrs.hbs.api.detailreturns.InMemoryDetailRows;
import ca.bc.gov.nrs.hbs.api.detailreturns.StubReferenceData;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailVersion;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.EditError;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.DocumentState;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.VersionState;
import ca.bc.gov.nrs.hbs.api.detailreturns.store.DetailStore;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.PopulationRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.StratumRef;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared test data for {@link WeighSlipValidator} / {@link SampleLogTallyValidator}
 * tests: documents, a reference set with stratum ABCD/01/2026 and in-memory
 * stubs of {@link WeighSampleLookups} and {@link TallyLookups}.
 */
final class WeighSampleFixtures {

  static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

  private WeighSampleFixtures() {
  }

  static Timestamp ts(String isoDate) {
    return Timestamp.valueOf(LocalDate.parse(isoDate).atStartOfDay());
  }

  /** {@link StubReferenceData#valid()} plus stratum/population ABCD 01 2026 (2026-01-01..2026-12-31). */
  static StubReferenceData reference() {
    StubReferenceData r = StubReferenceData.valid();
    r.strata.put("ABCD/01/2026", new StratumRef("ABCD", "01", 2026L, "00012345", "00", "C", ts("2026-01-01"),
        ts("2026-12-31")));
    r.populations.put("ABCD/2026", new PopulationRef("ABCD", 2026L, "00012345", "00", null, null, "R"));
    return r;
  }

  static DetailDocument document(DocType type, Row versionRow) {
    versionRow.set("detail_document_number", "DDN1").set("version", 1L).set("scl_rtn_version_state_code", "INC")
        .set("active_version_ind", "Y").set("original_version_ind", "Y");
    VersionState vs = new VersionState("INC", true);
    DetailVersion ver = new DetailVersion(versionRow, vs, true);
    return new DetailDocument(type, new Row().set("detail_document_number", "DDN1")
        .set("scale_return_state_code", "OIP").set("detail_scale_doc_type_code", type.code()),
        new DocumentState("OIP", vs), ver, true);
  }

  static ValidationContext context(ReferenceData ref) {
    DetailStore store = new DetailStore(new InMemoryDetailRows(), new RecordingTableApi(), new Audit("HBSBATCH"));
    return new ValidationContext(ref, store, TODAY);
  }

  static List<String> codes(List<EditError> errors) {
    return errors.stream().map(EditError::code).toList();
  }

  /** In-memory {@link WeighSampleLookups}; records the arguments of each call. */
  static final class StubWeighSample implements WeighSampleLookups {
    long sampleTallyConflict;
    long redTagLogTallies;
    long fieldScaleEstimates = 1;
    long sampledWeighSlips = 1;
    Row sampledWeighSlip;
    List<Row> weighSlipsByCriteria = new ArrayList<>();
    final Map<String, String> deckByEvent = new HashMap<>();
    final List<List<Object>> calls = new ArrayList<>();

    @Override
    public long sampleTallyConflict(String scaleSite, String origRedtagScaleSite, String weighSlipNumber,
                                    String eventType, String timberMark, String population, String stratum,
                                    Long samplingYear, Long sampleWeight) {
      calls.add(java.util.Arrays.asList("sampleTallyConflict", scaleSite, origRedtagScaleSite, weighSlipNumber,
          eventType, timberMark, population, stratum, samplingYear, sampleWeight));
      return sampleTallyConflict;
    }

    @Override
    public long redTagLogTallyCount(String scaleSite, String origRedtagScaleSite, String weighSlipNumber,
                                    String eventType) {
      calls.add(java.util.Arrays.asList("redTagLogTallyCount", scaleSite, origRedtagScaleSite, weighSlipNumber,
          eventType));
      return redTagLogTallies;
    }

    @Override
    public long fieldScaleEstimateCount(String fieldScaleDeckId, String timberMark) {
      calls.add(java.util.Arrays.asList("fieldScaleEstimateCount", fieldScaleDeckId, timberMark));
      return fieldScaleEstimates;
    }

    @Override
    public long sampledWeighSlipCount(String scaleSite, String originatingScaleSite, String weighSlipNumber,
                                      String eventType, boolean checkReplaceFlag) {
      calls.add(java.util.Arrays.asList("sampledWeighSlipCount", scaleSite, originatingScaleSite, weighSlipNumber,
          eventType, checkReplaceFlag));
      return sampledWeighSlips;
    }

    @Override
    public Optional<Row> sampledWeighSlip(String weighSlipNumber, String scaleSite) {
      calls.add(java.util.Arrays.asList("sampledWeighSlip", weighSlipNumber, scaleSite));
      return Optional.ofNullable(sampledWeighSlip);
    }

    @Override
    public List<Row> weighSlipsByCriteria(String scaleSite, String populationNumber, String stratumNumber,
                                          Long samplingYear, String weighSlipNumber) {
      calls.add(java.util.Arrays.asList("weighSlipsByCriteria", scaleSite, populationNumber, stratumNumber,
          samplingYear, weighSlipNumber));
      return weighSlipsByCriteria;
    }

    @Override
    public String sampledWeighSlipFieldScaleDeckId(String scaleSite, String weighSlipNumber, String eventType) {
      calls.add(java.util.Arrays.asList("sampledWeighSlipFieldScaleDeckId", scaleSite, weighSlipNumber, eventType));
      return deckByEvent.get(eventType);
    }
  }

  /** {@link TallyLookups} where every species/product/grade and schedule grade is on file and always effective. */
  static final class StubTally implements TallyLookups {
    @Override
    public Optional<SpeciesProductGradeRef> speciesProductGrade(String scaleProductCode, String scaleGradeCode,
                                                                String scaleSpeciesCode) {
      return Optional.of(new SpeciesProductGradeRef(scaleProductCode, scaleGradeCode, scaleSpeciesCode,
          ts("1990-01-01"), ts("9999-12-31")));
    }

    @Override
    public Optional<ScaleGradeRef> scheduleGrade(String scaleGradeCode, String gradeSchedule) {
      return Optional.of(new ScaleGradeRef(scaleGradeCode, ts("1990-01-01"), ts("9999-12-31")));
    }

    @Override
    public int fieldDeckCount(String fieldScaleDeckId, String timberMark) {
      return 1;
    }

    @Override
    public List<String> activeOriginalsByScalerAndReturnNumber(DocType type, String scalerLicense,
                                                               Long returnNumber, Date checkScaleDate) {
      return List.of();
    }
  }
}
