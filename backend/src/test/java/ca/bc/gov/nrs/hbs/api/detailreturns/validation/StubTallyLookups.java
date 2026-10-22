package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory {@link TallyLookups} for the tally validator tests: everything unknown unless registered. */
class StubTallyLookups implements TallyLookups {

  /** product/grade/species → row. */
  final Map<String, SpeciesProductGradeRef> spg = new HashMap<>();
  /** schedule/grade → row. */
  final Map<String, ScaleGradeRef> grades = new HashMap<>();
  /** deck/mark → count. */
  final Map<String, Integer> decks = new HashMap<>();
  /** DDNs returned for the scaler + return number search. */
  final List<String> originals = new ArrayList<>();
  DocType searchedType;

  StubTallyLookups addSpg(String species, String product, String grade, Date effective, Date expiry) {
    spg.put(product + "/" + grade + "/" + species, new SpeciesProductGradeRef(product, grade, species, effective,
        expiry));
    return this;
  }

  StubTallyLookups addGrade(String schedule, String grade, Date effective, Date expiry) {
    grades.put(schedule + "/" + grade, new ScaleGradeRef(grade, effective, expiry));
    return this;
  }

  @Override
  public Optional<SpeciesProductGradeRef> speciesProductGrade(String scaleProductCode, String scaleGradeCode,
                                                              String scaleSpeciesCode) {
    return Optional.ofNullable(spg.get(scaleProductCode + "/" + scaleGradeCode + "/" + scaleSpeciesCode));
  }

  @Override
  public Optional<ScaleGradeRef> scheduleGrade(String scaleGradeCode, String gradeSchedule) {
    return Optional.ofNullable(grades.get(gradeSchedule + "/" + scaleGradeCode));
  }

  @Override
  public int fieldDeckCount(String fieldScaleDeckId, String timberMark) {
    return decks.getOrDefault(fieldScaleDeckId + "/" + timberMark, 0);
  }

  @Override
  public List<String> activeOriginalsByScalerAndReturnNumber(DocType type, String scalerLicense, Long returnNumber,
                                                             Date checkScaleDate) {
    searchedType = type;
    return originals;
  }
}
