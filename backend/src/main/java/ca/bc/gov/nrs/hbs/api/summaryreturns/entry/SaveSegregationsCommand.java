package ca.bc.gov.nrs.hbs.api.summaryreturns.entry;

import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules.CATEGORY_BEACHCOMB;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules.CATEGORY_OTHER;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules.CATEGORY_PRIMARY;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules.CATEGORY_WASTE;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.summaryreturns.EditError;
import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryCaller;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException.Problem;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * P057 (piece) / P157 (sample) Update Segregations: {@code $V/dac/summary/P057Action} and
 * {@code P157Action} ({@code setValidateSegregations}, {@code validateSegregation}, {@code validateForm},
 * {@code addUpdateSegregations}, {@code addSegregation}), then the save path of
 * {@code updateSummaryScaleReturnAndChildren}, which both actions always called.
 *
 * <p>Body {@code segregations}: the full new list. A stored segregation missing from it is deleted
 * (the legacy Delete box); an all-empty new row is ignored, as the legacy blank add row was.
 * Row fields: {@code segregationId?, species, product, grade, otherDescription, pieces, volume,
 * sb4, nmv, avoidable (Y/N), bcombMark, inputBcombMark}. The check boxes and beachcomb marks only
 * count where the page showed them for the return category (SB4: PR, OT; NMV: BC, PR, OT;
 * avoidable: WA; marks: BC); elsewhere they are N / unchanged, as an unposted field was.
 */
@Component
public class SaveSegregationsCommand extends EntryCommand {

  public SaveSegregationsCommand(Deps deps) {
    super(deps);
  }

  @Override
  public String id() {
    return "summaryReturns.saveSegregations";
  }

  @Override
  public String legacy() {
    return "P057/P157 Update Segregations — P057Action / P157Action, updateSummaryScaleReturnAndChildren";
  }

  @Override
  public Capability capability() {
    return Capability.SUMMARY_RETURN_EDIT;
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    SummaryCaller caller = caller();
    SummaryReturn ret = loadEditable(body, caller);
    // P053 has no Segregations button on an area-based return (its segregations are generated), and
    // weight returns have none
    if (!ret.type().hasSegregations() || ret.areaBased() || !segregationLinkShown(ret)) {
      throw refuse(EntryMessages.SEGREGATIONS_NOT_AVAILABLE);
    }
    List<Map<String, Object>> rows = list(body, "segregations");
    Map<Long, Row> stored = new HashMap<>();
    ret.segregations().forEach(s -> stored.put(s.lng("segregation_id"), s));
    List<Problem> problems = new ArrayList<>();
    List<Row> kept = new ArrayList<>();
    List<Row> before = new ArrayList<>();
    List<Row> added = new ArrayList<>();
    boolean emptiedAny = false;
    String category = ret.version().str("scl_rtn_category_code");
    for (int i = 0; i < rows.size(); i++) {
      Map<String, Object> in = rows.get(i);
      String prefix = "segregations[" + i + "].";
      Object rawId = in.get("segregationId");
      if (str(rawId) != null) {
        Long id = parseId(rawId);
        Row seg = id == null ? null : stored.remove(id);
        if (seg == null) {
          problems.add(new Problem(prefix + "segregationId", EntryMessages.UNKNOWN_SEGREGATION));
          continue;
        }
        before.add(seg.copy());
        boolean keep = ret.type() == SummaryReturnType.PIECE
            ? editPiece(seg, in, category, prefix, problems)
            : editSample(seg, in, prefix, problems);
        if (keep) {
          kept.add(seg);
        } else {
          before.remove(before.size() - 1); // an emptied row is deleted
          emptiedAny = true;
        }
      } else {
        Row seg = ret.type() == SummaryReturnType.PIECE
            ? newPiece(ret, in, category, prefix, problems)
            : newSample(ret, in, prefix, problems);
        if (seg != null) added.add(seg);
      }
    }
    throwIfAny(problems);

    boolean changed = !stored.isEmpty() || !added.isEmpty() || emptiedAny
        || UpdateReturnCommand.segregationsChanged(ret.type(), before, kept);
    ret.segregations().clear();
    ret.segregations().addAll(kept);
    ret.segregations().addAll(added);
    List<EditError> errors = saveAndValidate(ret, changed);
    return saved(ret, true, errors);
  }

  private static Long parseId(Object raw) {
    try {
      return new BigDecimal(raw.toString().trim()).longValueExact();
    } catch (NumberFormatException | ArithmeticException ex) {
      return null;
    }
  }

  // ── Piece (P057Action) ───────────────────────────────────────────────────

  private static boolean sb4Shown(String category) {
    return CATEGORY_PRIMARY.equals(category) || CATEGORY_OTHER.equals(category);
  }

  private static boolean nmvShown(String category) {
    return CATEGORY_BEACHCOMB.equals(category) || sb4Shown(category);
  }

  private static String upper(String s) {
    return s == null ? null : s.toUpperCase(Locale.ROOT);
  }

  /**
   * {@code P057Action.setValidateSegregations} + {@code validateSegregation} for a stored row.
   * Returns false when the row is empty and is deleted ({@code addUpdateSegregations}).
   */
  private boolean editPiece(Row seg, Map<String, Object> in, String category, String prefix, List<Problem> p) {
    boolean beachcomb = CATEGORY_BEACHCOMB.equals(category);
    if (beachcomb) {
      String bcomb = str(in.get("bcombMark"));
      if (bcomb != null) seg.set("timber_mark", upper(bcomb)); // Ticket 1173: a blank keeps the billed mark
      if (in.containsKey("inputBcombMark")) seg.set("input_bcomb_timber_mark", upper(str(in.get("inputBcombMark"))));
    }
    if (in.containsKey("species")) seg.set("scale_species_code", upper(str(in.get("species"))));
    String product = str(in.get("product"));
    seg.set("scale_product_code", product == null ? EntryRules.PRODUCT_LOG : upper(product));
    String grade = str(in.get("grade"));
    seg.set("scale_grade_code", grade == null ? EntryRules.GRADE_UNGRADED : upper(grade));
    if (in.containsKey("otherDescription")) seg.set("other_description_type_code", str(in.get("otherDescription")));
    seg.set("sb4_ind", sb4Shown(category) && yes(in.get("sb4")) ? "Y" : "N");
    seg.set("nmv_ind", nmvShown(category) && yes(in.get("nmv")) ? "Y" : "N");
    seg.set("avoidable_ind", CATEGORY_WASTE.equals(category) && yes(in.get("avoidable")) ? "Y" : "N");
    if (in.containsKey("pieces")) seg.set("number_of_pieces", longOrNull(str(in.get("pieces"))));
    if (in.containsKey("volume")) {
      String volume = str(in.get("volume"));
      Double d = doubleOrNull(volume);
      if (d == null && EntryRules.PRODUCT_XMAS_TREES.equals(seg.str("scale_product_code")) && volume == null) {
        d = 0.0;
      }
      seg.set("volume", d == null ? null : BigDecimal.valueOf(d));
    }
    if (emptyPieceRow(seg.flag("sb4_ind"), seg.flag("nmv_ind"), seg.flag("avoidable_ind"), seg.str("timber_mark"),
        seg.str("input_bcomb_timber_mark"), seg.str("scale_species_code"), seg.str("scale_product_code"),
        seg.str("scale_grade_code"), seg.str("other_description_type_code"), seg.get("number_of_pieces"),
        seg.get("volume"))) {
      return false;
    }
    if (beachcomb) validateBcombMark(seg.str("timber_mark"), prefix, p);
    validatePieceRow(seg.flag("nmv_ind"), seg.flag("sb4_ind"), seg.str("scale_species_code"),
        seg.str("scale_product_code"), seg.str("scale_grade_code"), seg.lng("number_of_pieces"), prefix, p);
    BigDecimal volume = seg.num("volume");
    addVolumeProblem(p, prefix, seg.str("scale_product_code"),
        volume == null ? null : Double.toString(volume.doubleValue()));
    if (volume == null) seg.set("volume", BigDecimal.valueOf(0.0)); // addUpdateSegregations
    return true;
  }

  /** {@code P057Action.validateForm} + {@code addSegregation} for the new row(s). Null when empty. */
  private Row newPiece(SummaryReturn ret, Map<String, Object> in, String category, String prefix, List<Problem> p) {
    boolean beachcomb = CATEGORY_BEACHCOMB.equals(category);
    boolean sb4 = sb4Shown(category) && yes(in.get("sb4"));
    boolean nmv = nmvShown(category) && yes(in.get("nmv"));
    boolean avoidable = CATEGORY_WASTE.equals(category) && yes(in.get("avoidable"));
    String bcomb = beachcomb ? upper(str(in.get("bcombMark"))) : null;
    String inputBcomb = beachcomb ? upper(str(in.get("inputBcombMark"))) : null;
    String species = upper(str(in.get("species")));
    String product = upper(str(in.get("product")));
    String grade = upper(str(in.get("grade")));
    String otherDesc = str(in.get("otherDescription"));
    String piecesText = str(in.get("pieces"));
    String volumeText = str(in.get("volume"));
    Long pieces = longOrNull(piecesText);
    Double volume = doubleOrNull(volumeText);
    if (pieces == null || volume == null) { // the legacy parsed both together
      pieces = null;
      volume = null;
    }
    if (emptyPieceRow(sb4, nmv, avoidable, bcomb, inputBcomb, species, product, grade, otherDesc, pieces, volume)) {
      return null;
    }
    if (beachcomb) validateBcombMark(bcomb, prefix, p);
    Long parsedPieces;
    try {
      parsedPieces = EntryRules.toLong(piecesText);
    } catch (NumberFormatException ex) {
      parsedPieces = null;
    }
    validatePieceRow(nmv, sb4, species, product, grade, parsedPieces, prefix, p);
    addVolumeProblem(p, prefix, product, volumeText);

    String segType = switch (category == null ? "" : category) {
      case CATEGORY_WASTE -> SummaryReturnRules.SEG_TYPE_WASTE;
      case CATEGORY_BEACHCOMB -> SummaryReturnRules.SEG_TYPE_BEACH_COMB;
      default -> SummaryReturnRules.SEG_TYPE_PRIMARY;
    };
    return new Row().set("segregation_id", null)
        .set("document_control_number", ret.dcn()).set("version", ret.versionNo())
        .set("psseg_type", segType).set("other_description_type_code", otherDesc)
        .set("scale_species_code", species).set("scale_product_code", product).set("scale_grade_code", grade)
        .set("timber_mark", bcomb).set("number_of_pieces", parsedPieces)
        .set("volume", volumeText == null ? BigDecimal.valueOf(0.0) : parsedVolume(volumeText))
        .set("nmv_ind", nmv ? "Y" : "N").set("sb4_ind", sb4 ? "Y" : "N").set("avoidable_ind", avoidable ? "Y" : "N")
        .set("input_species", null).set("input_product", null).set("input_grade", null)
        .set("input_bcomb_timber_mark", inputBcomb).set("archive_timber_mark", null).set("input_other_desc", null);
  }

  /** {@code error.bcombmark.invalid}: a beachcomb mark entered must be a known timber mark. */
  private void validateBcombMark(String bcombMark, String prefix, List<Problem> p) {
    if (bcombMark != null && lookups.timberMark(bcombMark).isEmpty()) {
      p.add(new Problem(prefix + "bcombMark", lookups.editErrorMessage(EntryMessages.BCOMB_MARK_INVALID_CODE,
          EntryMessages.BCOMB_MARK_INVALID_CATEGORY)));
    }
  }

  private void validatePieceRow(boolean nmv, boolean sb4, String species, String product, String grade, Long pieces,
      String prefix, List<Problem> p) {
    if (nmv && sb4) p.add(new Problem(prefix + "nmv", EntryMessages.SB4_AND_NMV));
    if (species == null) p.add(new Problem(prefix + "species", EntryMessages.SPECIES_REQUIRED));
    if (product == null) p.add(new Problem(prefix + "product", EntryMessages.PRODUCT_REQUIRED));
    if (grade == null) p.add(new Problem(prefix + "grade", EntryMessages.GRADE_REQUIRED));
    if (species != null && product != null && grade != null
        && !lookups.speciesProductGradeExists(species, product, grade)) {
      p.add(new Problem(prefix + "species", EntryMessages.SPG_INVALID));
    }
    if (pieces == null) {
      p.add(new Problem(prefix + "pieces", EntryMessages.PIECES_REQUIRED));
    } else if (pieces < 0) {
      p.add(new Problem(prefix + "pieces", EntryMessages.PIECES_NEGATIVE));
    }
  }

  /** {@code P057Action.emptySegRow}. */
  private static boolean emptyPieceRow(boolean sb4, boolean nmv, boolean avoidable, String bcomb, String inputBcomb,
      String species, String product, String grade, String otherDesc, Object pieces, Object volume) {
    return !sb4 && !nmv && !avoidable && bcomb == null && inputBcomb == null && species == null
        && product == null && grade == null && otherDesc == null && pieces == null && volume == null;
  }

  // ── Sample (P157Action) ──────────────────────────────────────────────────

  /** {@code P157Action.setValidateSegregations} + {@code validateSegregation} for a stored row. */
  private boolean editSample(Row seg, Map<String, Object> in, String prefix, List<Problem> p) {
    if (in.containsKey("species")) seg.set("scale_species_code", upper(str(in.get("species"))));
    String grade = str(in.get("grade"));
    seg.set("scale_grade_code", grade == null ? EntryRules.GRADE_UNGRADED : upper(grade)); // TATA 3199
    if (in.containsKey("pieces")) seg.set("number_of_pieces", longOrNull(str(in.get("pieces"))));
    if (in.containsKey("volume")) {
      Double d = doubleOrNull(str(in.get("volume")));
      seg.set("volume", d == null ? null : BigDecimal.valueOf(d));
    }
    if (seg.str("scale_species_code") == null && seg.str("scale_grade_code") == null
        && seg.get("number_of_pieces") == null && seg.get("volume") == null) {
      return false; // emptySegRow
    }
    String species = seg.str("scale_species_code");
    if (species == null) p.add(new Problem(prefix + "species", EntryMessages.SPECIES_REQUIRED));
    if (seg.str("scale_grade_code") == null) p.add(new Problem(prefix + "grade", EntryMessages.GRADE_REQUIRED));
    if (species != null && seg.str("scale_grade_code") != null
        && !lookups.speciesProductGradeExists(species, EntryRules.PRODUCT_LOG, seg.str("scale_grade_code"))) {
      p.add(new Problem(prefix + "species", EntryMessages.SPECIES_GRADE_INVALID));
    }
    if (seg.get("number_of_pieces") == null) p.add(new Problem(prefix + "pieces", EntryMessages.PIECES_REQUIRED));
    BigDecimal volume = seg.num("volume");
    addVolumeProblem(p, prefix, seg.str("scale_product_code"),
        volume == null ? null : Double.toString(volume.doubleValue()));
    return true;
  }

  /** {@code P157Action.validateForm} + {@code addSegregation}: product LO, grade defaults to U. */
  private Row newSample(SummaryReturn ret, Map<String, Object> in, String prefix, List<Problem> p) {
    String species = upper(str(in.get("species")));
    String grade = upper(str(in.get("grade")));
    String piecesText = str(in.get("pieces"));
    String volumeText = str(in.get("volume"));
    Long pieces = longOrNull(piecesText);
    Double volume = doubleOrNull(volumeText);
    if (pieces == null || volume == null) {
      pieces = null;
      volume = null;
    }
    if (species == null && grade == null && pieces == null && volume == null) {
      return null;
    }
    if (species == null) p.add(new Problem(prefix + "species", EntryMessages.SPECIES_REQUIRED));
    if (grade == null) grade = EntryRules.GRADE_UNGRADED; // TATA 3199
    if (species != null && !lookups.speciesProductGradeExists(species, EntryRules.PRODUCT_LOG, grade)) {
      p.add(new Problem(prefix + "species", EntryMessages.SPECIES_GRADE_INVALID));
    }
    Long parsedPieces;
    try {
      parsedPieces = EntryRules.toLong(piecesText);
    } catch (NumberFormatException ex) {
      parsedPieces = null;
    }
    if (parsedPieces == null) p.add(new Problem(prefix + "pieces", EntryMessages.PIECES_REQUIRED));
    addVolumeProblem(p, prefix, EntryRules.PRODUCT_LOG, volumeText); // "Volume is hard coded to Log here."
    return new Row().set("segregation_id", null)
        .set("document_control_number", ret.dcn()).set("version", ret.versionNo())
        .set("scale_species_code", species).set("scale_product_code", EntryRules.PRODUCT_LOG)
        .set("scale_grade_code", grade).set("number_of_pieces", parsedPieces)
        .set("volume", volumeText == null ? BigDecimal.valueOf(0.0) : parsedVolume(volumeText))
        .set("input_species", null).set("input_product", null).set("input_grade", null);
  }

  // ── Shared ───────────────────────────────────────────────────────────────

  private static void addVolumeProblem(List<Problem> p, String prefix, String product, String volume) {
    String problem = EntryRules.volumeProblem(product, volume);
    if (problem != null) p.add(new Problem(prefix + "volume", problem));
  }

  private static Long longOrNull(String s) {
    try {
      return EntryRules.toLong(s);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static Double doubleOrNull(String s) {
    if (s == null) return null;
    try {
      return Double.valueOf(s);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static BigDecimal parsedVolume(String s) {
    Double d = doubleOrNull(s);
    return d == null ? null : BigDecimal.valueOf(d);
  }
}
