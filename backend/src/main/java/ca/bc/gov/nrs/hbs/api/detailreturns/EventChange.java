package ca.bc.gov.nrs.hbs.api.detailreturns;

import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.DetailValidator.*;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.PopStratYear;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.util.Set;

/**
 * "Change Event Type" (P562/P554A, P612/P604A, P662/P654A): the new scale event
 * type is set and the fields that do not apply to it are cleared; the caller
 * then saves the document as the legacy SCALE_EVENT action did. Ported from
 * {@code $V/dac/detail/P554AAction.doHandle} (log tally),
 * {@code P604AAction.revampScaleReturn} (weigh slip) and
 * {@code P654AAction.doHandle} (sample tally).
 */
public final class EventChange {

  /** ApplicationResources {@code errors.event-change.equal}. */
  public static final String SAME_EVENT = "Cannot change to same Event Type";

  /** {@code EMPTY_STRING} the legacy actions put in an input field of a submitted version. */
  private static final String BLANK = " ";

  private EventChange() {
  }

  public static void apply(DetailDocument doc, String newEvent) {
    Row v = doc.version().row();
    String original = v.str("scale_event_type_code");
    if (newEvent == null || newEvent.equals(original)) {
      throw new WorkflowValidationException("scaleEventTypeCode", SAME_EVENT);
    }
    doc.errors().clear(); // BT5825
    v.set("scale_event_type_code", newEvent);
    switch (doc.type()) {
      case LOG_TALLY -> logTally(doc, v, newEvent);
      case WEIGH_SLIP -> weighSlip(v, newEvent);
      case SAMPLE_LOG_TALLY -> sampleTally(v, newEvent);
      default -> throw new WorkflowValidationException("scaleEventTypeCode",
          "The event type of this return cannot be changed.");
    }
  }

  private static boolean in(String e, String... codes) {
    return Set.of(codes).contains(e);
  }

  /** {@code P554AAction.doHandle}. */
  static void logTally(DetailDocument doc, Row v, String e) {
    boolean submitted = v.get("batdoc_id") != null;
    if (in(e, SB4_RESCALE, COMPANY_USE)) v.set("beachcomb_ind", null);
    if (in(e, FROM_SITE_DECK, SB4_RESCALE, COMPANY_USE)) v.set("cut_block_id", null);
    if (in(e, SB4_RESCALE, COMPANY_USE)) {
      v.set("field_scale_deck_id", null);
      v.set("field_scale_ind", null);
    }
    if (in(e, FROM_SITE_DECK, RED_TAG, CHECK_SCALE, COMPANY_USE)) v.set("incoming_lds_number", null);
    if (in(e, RED_TAG, CHECK_SCALE, COMPANY_USE)) v.set("load_arrival_number", null);
    if (!REMOTE_RED_TAG.equals(e)) v.set("orig_redtag_scale_site_id_nmbr", null);
    if (!CHECK_SCALE.equals(e)) {
      v.set("original_ddn", null);
      v.set("original_ddv", null);
      v.set("orig_chk_scl_license_number", null);
      v.set("orig_chk_scl_return_number", null);
      v.set("check_replaces_original_ind", null);
    }
    if (in(e, FROM_SITE_DECK, CHECK_SCALE, COMPANY_USE)) {
      v.set("parcel_count", null);
      v.set("parcel_identifier", null);
    }
    if (in(e, PRIMARY_SCALE, FROM_SITE_DECK, RED_TAG, REMOTE_RED_TAG, CHECK_SCALE)) {
      v.set("timber_brand", null);
    } else if (SB4_RESCALE.equals(e)) {
      v.set("timber_mark", null);
    } else if (COMPANY_USE.equals(e)) {
      v.set("timber_brand", null);
      v.set("timber_mark", null);
    }
    if (in(e, FROM_SITE_DECK, RED_TAG, CHECK_SCALE, COMPANY_USE)) v.set("transport_identifier", null);
    if (in(e, PRIMARY_SCALE, FROM_SITE_DECK, CHECK_SCALE, SB4_RESCALE, COMPANY_USE)) v.set("red_tag_weigh_slip", null);
    if (!REMOTE_RED_TAG.equals(e)) {
      v.set("input_orig_redtag_scl_site", null);
    } else if (submitted && v.empty("input_orig_redtag_scl_site")) {
      v.set("input_orig_redtag_scl_site", v.empty("orig_redtag_scale_site_id_nmbr") ? BLANK
          : v.str("orig_redtag_scale_site_id_nmbr"));
    }
    if (in(e, FROM_SITE_DECK, SB4_RESCALE, COMPANY_USE)) {
      v.set("input_cutblock_id", null);
    } else if (submitted && v.empty("input_cutblock_id")) {
      v.set("input_cutblock_id", v.empty("cut_block_id") ? BLANK : v.str("cut_block_id"));
    }
    if (!CHECK_SCALE.equals(e)) {
      v.set("input_orig_chk_scl_scaler_lic", null);
    } else if (submitted && v.empty("input_orig_chk_scl_scaler_lic")) {
      v.set("input_orig_chk_scl_scaler_lic", v.empty("orig_chk_scl_license_number") ? BLANK
          : v.str("orig_chk_scl_license_number"));
    }
    if (COMPANY_USE.equals(e)) {
      v.set("input_source", null);
    } else if (submitted && v.empty("input_source")) {
      if (v.empty("timber_mark") && v.empty("timber_brand")) {
        v.set("input_source", BLANK);
      } else if (v.empty("timber_mark")) {
        v.set("input_source", v.str("timber_brand"));
      } else if (v.empty("timber_brand")) {
        v.set("input_source", v.str("timber_mark"));
      }
    }
    if (in(e, SB4_RESCALE, COMPANY_USE)) {
      v.set("volume_calc_method_type", null);
      v.set("log_count", 0L);
      doc.version().logs().clear(); // deleteLogDetails
    }
  }

  /** {@code P604AAction.revampScaleReturn}. */
  static void weighSlip(Row v, String e) {
    boolean billable = in(e, PRIMARY_SCALE, SAMPLE_SCALE, REMOTE_SAMPLE_SCALE, PRIMARY_DEPARTURE);
    if (billable) {
      if (in(e, PRIMARY_SCALE, SAMPLE_SCALE)) {
        v.set("destination", null);
        v.set("outgoing_lds_number", null);
      }
      if (in(e, REMOTE_SAMPLE_SCALE, PRIMARY_DEPARTURE, PRIMARY_SCALE)) {
        v.set("sample_deduction_weight", null);
      }
      swapTimberMarkAndBrand(v, e);
      revampInputFields(v);
    } else {
      swapTimberMarkAndBrand(v, e);
      v.set("sample_deduction_weight", null);
      v.set("hbs_sample_type_code", null);
      if (RED_TAG.equals(e)) {
        v.set("destination", null);
        v.set("outgoing_lds_number", null);
        revampInputFields(v);
      } else if (REMOTE_RED_TAG.equals(e)) {
        revampInputFields(v);
      } else {
        v.set("cut_block_id", null);
        v.set("field_scale_deck_id", null);
        v.set("field_scale_ind", null);
        v.set("gross_weight", null);
        v.set("tare_weight", null);
        v.set("input_cutblock_id", null);
        if (in(e, SB4_RESCALE, COMPANY_USE)) {
          v.set("destination", null);
          v.set("outgoing_lds_number", null);
        }
        if (in(e, DEPARTURE, COMPANY_USE)) {
          v.set("incoming_lds_number", null);
          v.set("input_source", null);
        }
        if (COMPANY_USE.equals(e)) {
          v.set("population_number", null);
          v.set("stratum_number", null);
          v.set("sampling_year", null);
          v.set("company_use_stratum", null);
          v.set("transport_identifier", null);
          v.set("input_psy", null);
        }
      }
    }
  }

  /** {@code P604AAction.swapTimberMarkAndBrand}. */
  private static void swapTimberMarkAndBrand(Row v, String e) {
    String mark = v.str("timber_mark");
    String brand = v.str("timber_brand");
    if (mark != null && SB4_RESCALE.equals(e)) {
      v.set("timber_brand", mark);
      v.set("timber_mark", null);
    } else if (brand != null && !SB4_RESCALE.equals(e)) {
      v.set("timber_brand", null);
      v.set("timber_mark", brand);
    }
    if (in(e, DEPARTURE, COMPANY_USE)) {
      v.set("timber_brand", null);
      v.set("timber_mark", null);
    }
  }

  /** {@code P604AAction.revampInputFields}: a submitted weigh slip keeps its input_* values in step. */
  private static void revampInputFields(Row v) {
    if (v.get("batdoc_id") == null) return;
    if (v.empty("input_source")) {
      if (v.empty("timber_mark") && v.empty("timber_brand")) {
        v.set("input_source", null);
      } else if (v.empty("timber_mark")) {
        v.set("input_source", v.str("timber_brand"));
      } else if (v.empty("timber_brand")) {
        v.set("input_source", v.str("timber_mark"));
      }
    }
    if (!v.empty("cut_block_id") && v.empty("input_cutblock_id")) {
      v.set("input_cutblock_id", v.str("cut_block_id"));
    }
    if (v.empty("company_use_stratum")) {
      if (v.empty("input_psy")) {
        v.set("input_psy", PopStratYear.of(v.str("population_number"), v.str("stratum_number"),
            v.lng("sampling_year")).psySansCentury());
      }
    } else if (v.empty("population_number") && v.empty("input_psy")) {
      v.set("input_psy", v.str("company_use_stratum"));
    }
  }

  /** {@code P654AAction.doHandle}. */
  static void sampleTally(Row v, String e) {
    boolean submitted = v.get("batdoc_id") != null;
    if (in(e, CHECK_SCALE, SAMPLE_SCALE)) {
      v.set("incoming_lds_number", null);
      v.set("load_arrival_number", null);
      v.set("orig_redtag_scale_site_id_nmbr", null);
    }
    if (!CHECK_SCALE.equals(e)) {
      v.set("original_ddn", null);
      v.set("original_ddv", null);
      v.set("orig_chk_scl_license_number", null);
      v.set("orig_chk_scl_return_number", null);
      v.set("check_replaces_original_ind", null);
    }
    if (!REMOTE_SAMPLE_SCALE.equals(e)) v.set("transport_identifier", null);
    if (!REMOTE_SAMPLE_SCALE.equals(e)) {
      v.set("input_orig_redtag_scl_site", null);
    } else if (submitted && v.empty("input_orig_redtag_scl_site")) {
      v.set("input_orig_redtag_scl_site", v.empty("orig_redtag_scale_site_id_nmbr") ? BLANK
          : v.str("orig_redtag_scale_site_id_nmbr"));
    }
    if (!CHECK_SCALE.equals(e)) {
      v.set("input_orig_chk_scl_scaler_lic", null);
    } else if (submitted && v.empty("input_orig_chk_scl_scaler_lic")) {
      v.set("input_orig_chk_scl_scaler_lic", v.empty("orig_chk_scl_license_number") ? BLANK
          : v.str("orig_chk_scl_license_number"));
    }
  }

  /** Event types each type offers on the change-event page (P562 / P612 / P662). */
  public static Set<String> eventTypes(DocType type) {
    return switch (type) {
      case LOG_TALLY -> Set.of(PRIMARY_SCALE, FROM_SITE_DECK, RED_TAG, REMOTE_RED_TAG, CHECK_SCALE, SB4_RESCALE,
          COMPANY_USE);
      case WEIGH_SLIP -> Set.of(PRIMARY_SCALE, SAMPLE_SCALE, REMOTE_SAMPLE_SCALE, PRIMARY_DEPARTURE, RED_TAG,
          REMOTE_RED_TAG, SB4_RESCALE, DEPARTURE, COMPANY_USE);
      case SAMPLE_LOG_TALLY -> Set.of(SAMPLE_SCALE, REMOTE_SAMPLE_SCALE, CHECK_SCALE);
      default -> Set.of();
    };
  }
}
