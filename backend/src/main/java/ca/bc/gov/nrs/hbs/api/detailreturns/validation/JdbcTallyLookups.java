package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** {@link TallyLookups} over Oracle (bound parameters only). */
@Component
public class JdbcTallyLookups implements TallyLookups {

  /** {@code SpeciesProductGradeQuery.selectByPK}. */
  static final String SPG_BY_PK = """
      SELECT scale_product_code, scale_grade_code, scale_species_code, effective_date, expiry_date
        FROM species_product_grade_xref
       WHERE scale_product_code = :product AND scale_grade_code = :grade AND scale_species_code = :species""";

  /** {@code ScaleGradeDAO.getCostalGradeQuery}. */
  static final String COAST_GRADE = """
      SELECT coast_scale_grade_code AS scale_grade_code, effective_date, expiry_date
        FROM coast_scale_grade_code
       WHERE coast_scale_grade_code = :grade""";

  /** {@code ScaleGradeDAO.getInteriorGradeQuery}. */
  static final String INTERIOR_GRADE = """
      SELECT interior_scale_grade_code AS scale_grade_code, effective_date, expiry_date
        FROM interior_scale_grade_code
       WHERE interior_scale_grade_code = :grade""";

  /** {@code SummaryPieceScaleReturnVersionQuery.selectFieldDeckId} (BT 8253). */
  static final String FIELD_DECK = """
      SELECT COUNT(*)
        FROM piece_scale_summary
       WHERE field_scale_deck_id = :deck
         AND timber_mark = :mark
         AND pssmry_type = 'FLDSCLEST'
         AND scl_rtn_category_code = 'FI'
         AND scl_rtn_version_state_code IN ('ISS','RDY','ERR','HLD','LCK','CAN')
         AND active_version_ind = 'Y'""";

  /** {@code LogTallyQuery.selectByScalerAndReturnNumber} (the check-scale date is not used). */
  static final String LOG_TALLY_BY_SCALER = """
      SELECT detail_document_number
        FROM log_tally
       WHERE primary_license_number = :licence
         AND return_number = :returnNumber
         AND scl_rtn_version_state_code IN ('ISS','RDY','NTI','ERR','HLD','LCK','DSF','AWP')
         AND active_version_ind = 'Y'""";

  /** {@code SampleLogTallyQuery.selectByScalerAndReturnNumber} (the check-scale date is not used). */
  static final String SAMPLE_TALLY_BY_SCALER = """
      SELECT detail_document_number
        FROM sample_log_tally
       WHERE primary_license_number = :licence
         AND return_number = :returnNumber
         AND scl_rtn_version_state_code IN ('ISS','RDY','NTI','ERR','HLD','LCK','DSF','AWP')
         AND active_version_ind = 'Y'""";

  /**
   * {@code SpecialForestProductTallyQuery.selectByScalerAndReturnNumber}: no state
   * filter, but a scale date within the 30 days up to the check scale (legacy
   * formatted both bounds as DD-MON-YYYY, i.e. truncated to the day).
   */
  static final String SFP_TALLY_BY_SCALER = """
      SELECT detail_document_number
        FROM special_forest_product_tally
       WHERE primary_license_number = :licence
         AND return_number = :returnNumber
         AND active_version_ind = 'Y'
         AND scale_date >= TRUNC(:checkScaleDate) - 30
         AND scale_date <= TRUNC(:checkScaleDate)""";

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcTallyLookups(JdbcTemplate jdbc) {
    this.jdbc = new NamedParameterJdbcTemplate(jdbc);
  }

  @Override
  public Optional<SpeciesProductGradeRef> speciesProductGrade(String scaleProductCode, String scaleGradeCode,
                                                              String scaleSpeciesCode) {
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("product", scaleProductCode).addValue("grade", scaleGradeCode)
        .addValue("species", scaleSpeciesCode);
    return jdbc.query(SPG_BY_PK, p, (rs, i) -> new SpeciesProductGradeRef(rs.getString("scale_product_code"),
        rs.getString("scale_grade_code"), rs.getString("scale_species_code"),
        rs.getTimestamp("effective_date"), rs.getTimestamp("expiry_date"))).stream().findFirst();
  }

  @Override
  public Optional<ScaleGradeRef> scheduleGrade(String scaleGradeCode, String gradeSchedule) {
    String sql;
    if (DetailValidator.COASTAL_SCHEDULE.equalsIgnoreCase(gradeSchedule == null ? null : gradeSchedule.trim())) {
      sql = COAST_GRADE;
    } else if (DetailValidator.INTERIOR_SCHEDULE.equalsIgnoreCase(gradeSchedule == null ? null : gradeSchedule.trim())) {
      sql = INTERIOR_GRADE;
    } else {
      return Optional.empty();
    }
    List<ScaleGradeRef> rows = jdbc.query(sql, Map.of("grade", nvl(scaleGradeCode)), (rs, i) ->
        new ScaleGradeRef(rs.getString("scale_grade_code"), rs.getTimestamp("effective_date"),
            rs.getTimestamp("expiry_date")));
    // ScaleGradeDAO kept the last row read
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(rows.size() - 1));
  }

  @Override
  public int fieldDeckCount(String fieldScaleDeckId, String timberMark) {
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("deck", fieldScaleDeckId).addValue("mark", timberMark);
    Integer n = jdbc.queryForObject(FIELD_DECK, p, Integer.class);
    return n == null ? 0 : n;
  }

  @Override
  public List<String> activeOriginalsByScalerAndReturnNumber(DocType type, String scalerLicense, Long returnNumber,
                                                             Date checkScaleDate) {
    String sql = switch (type) {
      case LOG_TALLY -> LOG_TALLY_BY_SCALER;
      case SAMPLE_LOG_TALLY -> SAMPLE_TALLY_BY_SCALER;
      case SFP_TALLY -> SFP_TALLY_BY_SCALER;
      default -> throw new IllegalArgumentException("Search by scaler license and return number cannot "
          + "be performed for document type:" + type.code() + ".");
    };
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("licence", scalerLicense).addValue("returnNumber", returnNumber);
    if (type == DocType.SFP_TALLY) {
      p.addValue("checkScaleDate", checkScaleDate == null ? null : new java.sql.Timestamp(checkScaleDate.getTime()),
          java.sql.Types.TIMESTAMP);
    }
    return jdbc.queryForList(sql, p, String.class);
  }

  private static String nvl(String s) {
    return s == null ? "" : s;
  }
}
