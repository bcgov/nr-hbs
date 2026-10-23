package ca.bc.gov.nrs.hbs.api.summaryreturns.edits;

import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * BT15212 {@code SummaryScaleReturnManagerBean.generateAreaBasedSegregationsAndNotation},
 * {@code createAreaBasedSegregations}, {@code createAreaBasedNotationText} and
 * {@code createAreaBasedNotation}, in memory only: the caller's writer assigns the ids and saves.
 */
class AreaBasedSegregations {

  static final String NOTATION_TYPE = "NOT";

  private final Lookups lookups;
  private final Clock clock;

  AreaBasedSegregations(Lookups lookups, Clock clock) {
    this.lookups = lookups;
    this.clock = clock;
  }

  /**
   * {@code generateAreaBasedSegregationsAndNotation}: replaces the segregations with one PRIMARY log
   * segregation per cruise-based species/grade ratio of the confirmed worksheet covering the scale
   * date (one piece, volume = volume per hectare × area cut), sets the log count to their number
   * and, when there is at least one, updates the text of notation 50 or adds it.
   */
  void generate(SummaryReturn ret, String user) {
    Row v = ret.version();
    BigDecimal areaCut = v.num("area_cut");
    if (areaCut == null) {
      throw new ReleaseEditException("createAreaBasedSegregations: null AREA_CUT");
    }
    Timestamp now = Timestamp.valueOf(LocalDateTime.now(clock));
    List<Row> generated = new ArrayList<>();
    for (Lookups.CruiseRatio ratio : lookups.areaBasedCruiseRatios(ret.dcn(), ret.versionNo())) {
      if (ratio.volumePerHectare() == null) {
        throw new ReleaseEditException("createAreaBasedSegregations: null VOLUME_PER_HECTARE");
      }
      double volume = ratio.volumePerHectare().doubleValue() * areaCut.doubleValue();
      generated.add(new Row()
          .set("segregation_id", null)
          .set("psseg_type", SummaryReturnRules.SEG_TYPE_PRIMARY)
          .set("document_control_number", ret.dcn())
          .set("version", ret.versionNo())
          .set("scale_species_code", ratio.species())
          .set("scale_product_code", EditCodes.PRODUCT_LOG)
          .set("scale_grade_code", ratio.grade())
          .set("number_of_pieces", 1L)
          // VOLUME is NUMBER(9,3): Oracle rounded the legacy double on insert
          .set("volume", BigDecimal.valueOf(volume).setScale(3, RoundingMode.HALF_UP))
          .set("timber_mark", null)
          .set("input_species", null)
          .set("input_product", null)
          .set("input_grade", null)
          .set("input_bcomb_timber_mark", null)
          .set("input_other_desc", null)
          .set("archive_timber_mark", null)
          .set("sb4_ind", null)
          .set("nmv_ind", "N")
          .set("avoidable_ind", null)
          .set("other_description_type_code", null)
          .set("entry_userid", user)
          .set("entry_timestamp", now)
          .set("update_userid", user)
          .set("update_timestamp", now));
    }
    ret.segregations().clear();
    ret.segregations().addAll(generated);
    v.set("log_count", (long) generated.size());

    if (!generated.isEmpty()) {
      String text = notationText(ret);
      Row existing = ret.notations().stream()
          .filter(n -> Long.valueOf(SummaryReturnRules.AREA_BASED_NOTATION_NUMBER).equals(n.lng("notation_number")))
          .findFirst().orElse(null);
      if (existing != null) {
        existing.set("text", text);
      } else {
        ret.notations().add(new Row()
            .set("ntn_id", null)
            .set("notation_type_code", NOTATION_TYPE)
            .set("version", ret.versionNo())
            .set("document_control_number", ret.dcn())
            .set("notation_number", SummaryReturnRules.AREA_BASED_NOTATION_NUMBER)
            .set("text", text)
            .set("entry_userid", user)
            .set("entry_timestamp", now)
            .set("update_userid", user)
            .set("update_timestamp", now));
      }
    }
  }

  /**
   * {@code createAreaBasedNotationText}: "&lt;area cut&gt; Ha on this invoice; &lt;total&gt; Ha Cut to date;
   * Appraised Area: &lt;merchantable&gt; Ha", numbers as Java doubles print (12.5, 40.0). The totals
   * are read from the stored return rows, as legacy did.
   */
  String notationText(SummaryReturn ret) {
    double areaCut = ret.version().num("area_cut").doubleValue();
    double totalAreaCut = lookups.areaBasedTotalAreaCut(ret.dcn(), ret.versionNo());
    double merchantable = lookups.areaBasedTotalMerchantableArea(ret.dcn(), ret.versionNo());
    return areaCut + " Ha on this invoice; " + totalAreaCut + " Ha Cut to date; Appraised Area: "
        + merchantable + " Ha";
  }
}
