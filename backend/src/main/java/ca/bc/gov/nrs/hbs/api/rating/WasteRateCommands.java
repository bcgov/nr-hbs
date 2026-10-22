package ca.bc.gov.nrs.hbs.api.rating;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.rating.RatingInput.DateValue;
import ca.bc.gov.nrs.hbs.api.rating.RatingInput.NumberValue;
import ca.bc.gov.nrs.hbs.api.rating.RatingInput.Problems;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Waste &amp; residue rates (P222 Add, P223 Save / Delete). Since BT 5705 the
 * rate has a single "Scale Date": expiry = effective. The product is always
 * logs (' ', {@code P222Form.chcProduct}) and avoidable is always Y (issue
 * #2989). Struts ran validation.xml (P222Form / P223Form) first, then the
 * action's {@code validateForm}.
 */
final class WasteRateCommands {

  private static final BigDecimal MAX_RATE = new BigDecimal("999.99");
  private static final String STORE = "HBS_STORE_WASTE_RESIDUE_RATE";

  private WasteRateCommands() {
  }

  /** Species / grade / rate source selects (no empty option on P222/P223). */
  private static void selects(Problems p, String species, String grade, String source) {
    if (species == null) p.add("scaleSpeciesCode", RatingMessages.required("Species"));
    if (grade == null) p.add("scaleGradeCode", RatingMessages.required("Grade"));
    if (source == null) p.add("wasteRateSourceCode", RatingMessages.required("Rate Source"));
  }

  /** {@code P222Action/P223Action.validateForm}: species-product-grade in SPECIES_PRODUCT_GRADE_XREF (product logs). */
  private static void speciesProductGrade(Problems p, RatingRepository repo, String species, String grade) {
    if (repo.speciesProductGrade(species, RatingInput.LOGS, grade).isEmpty()) {
      p.add("scaleSpeciesCode", RatingMessages.SPG_INVALID);
    }
  }

  /** P223.getActInd(): the inactive-row guard around Save and Delete. */
  private static void requireActive(Map<String, Object> row) {
    if (!"Y".equals(RatingCommand.str(row, "active_ind"))) {
      throw new WorkflowValidationException("activeInd", RatingMessages.WRR_INACTIVE);
    }
  }

  /**
   * P222 "Activate" ({@code $V/plu/P222Action}, {@code RateHelper.createWasteResidueRate}).
   * Phase 1 (validation.xml P222Form): timber mark required and in
   * TIMBER_MARK + MARK_BILLED_CLI, cut block required, scale date, rate
   * 0–999.99 with 2 decimals. Phase 2: scale date not older than 7 years; a
   * mark with cut blocks needs one of them, a mark without any needs a cut
   * block made of letters, digits and {@code < > : - ( / '}; no identical
   * active rate (same mark, cut block, species, grade, source, in effect on the
   * date); a valid species-product-grade.
   */
  @Component
  static class Create extends RatingCommand {
    Create(RatingRepository repo, TableApi tableApi, Audit audit, RatingClock clock) {
      super(repo, tableApi, audit, clock);
    }

    @Override
    public String id() {
      return "rating.wasteRate.create";
    }

    @Override
    public String legacy() {
      return "P222 Activate (P222Action.validateForm, RateHelper.createWasteResidueRate)";
    }

    @Override
    public Capability capability() {
      return Capability.WASTE_RATE_ADMIN;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      String timberMark = RatingInput.upper(body, "timberMark");
      String cutBlock = RatingInput.upper(body, "cutBlockId");
      DateValue scaleDate = RatingInput.date(body, "effectiveDate");
      String species = RatingInput.upper(body, "scaleSpeciesCode");
      String grade = RatingInput.upper(body, "scaleGradeCode");
      String source = RatingInput.upper(body, "wasteRateSourceCode");
      NumberValue rate = RatingInput.number(body, "rate");

      Problems p = new Problems();
      if (timberMark == null) {
        p.add("timberMark", RatingMessages.required("Timber Mark"));
      } else if (repo.billedMarkFileType(timberMark).isEmpty()) {
        p.add("timberMark", RatingMessages.TIMBER_MARK_REF);
      }
      if (cutBlock == null) p.add("cutBlockId", RatingMessages.required("Cut Block"));
      boolean dateOk = RatingRules.validatorDate(p, "effectiveDate", "Scale Date", scaleDate);
      RatingRules.validatorRate(p, "rate", rate, MAX_RATE, "999.99");
      selects(p, species, grade, source);
      p.throwIfAny();

      LocalDate day = scaleDate.date();
      if (dateOk && RatingRules.olderThanSevenYears(day, today())) {
        p.add("effectiveDate", RatingMessages.WRR_EFF_OLD);
      }
      if (repo.countCutBlocks(timberMark) == 0) {
        if (!RatingRules.cutBlockCharacters(cutBlock)) p.add("cutBlockId", RatingMessages.CUT_BLOCK_CHARS);
      } else if (!repo.cutBlockExists(timberMark, cutBlock)) {
        p.add("cutBlockId", RatingMessages.CUT_BLOCK_NOT_ON_MARK);
      }
      // P222Action: findWasteResidueRate(...) == 1 → error.Record.exists. Ported as written (exactly one).
      if (repo.activeWasteRates(timberMark, cutBlock, species, grade, day, source).size() == 1) {
        p.add("effectiveDate", RatingMessages.RECORD_EXISTS);
      }
      speciesProductGrade(p, repo, species, grade);
      p.throwIfAny();

      long id = tableApi.nextId("WASTE_RESIDUE_RATE_SEQ");
      Timestamp now = audit.now();
      String user = audit.user();
      tableApi.call("HBS_CREATE_WASTE_RESIDUE_RATE", ProcArgs.of()
          .put("i_wstrt_id", id)
          .put("i_timber_mark", timberMark)
          .put("i_cut_block_id", cutBlock)
          .put("i_scale_species_code", species)
          .put("i_scale_product_code", RatingInput.LOGS)
          .put("i_scale_grade_code", grade)
          .put("i_active_ind", "Y")
          .put("i_avoidable_ind", "Y")
          .put("i_rate", rate.value())
          .put("i_effective_date", day)
          .put("i_expiry_date", day)
          .put("i_entry_userid", user)
          .put("i_entry_timestamp", now)
          .put("i_update_userid", user)
          .put("i_update_timestamp", now)
          .put("i_waste_rate_source_code", source));
      return Map.of("wstrtId", id);
    }
  }

  /**
   * P223 "Save" ({@code $V/plu/P223Action} ACTION_UPDATE_RATE,
   * {@code RateHelper.update} + {@code updateWasteResidueRate}): scale date,
   * species, grade, rate and rate source change; an inactive rate cannot be
   * updated; a scale date on file older than 7 years cannot be changed, nor
   * moved more than 7 years back. Before storing, the legacy
   * {@code RateHelper.update} looked up the active avoidable log rates of the
   * mark / cut block / species / grade in effect on the new scale date and set
   * the first one inactive ({@code updateWasteResidueRate1} stored only
   * {@code rates[0]}); then the edited row is stored active. When that first
   * row is the edited one, its inactivation is overwritten by the store, so
   * only the edited row changes. The date-overlap rejection in the legacy
   * {@code validateForm} is commented out (BT 5705) and not ported.
   */
  @Component
  static class Update extends RatingCommand {
    Update(RatingRepository repo, TableApi tableApi, Audit audit, RatingClock clock) {
      super(repo, tableApi, audit, clock);
    }

    @Override
    public String id() {
      return "rating.wasteRate.update";
    }

    @Override
    public String legacy() {
      return "P223 Save (P223Action.validateForm, RateHelper.update, updateWasteResidueRate)";
    }

    @Override
    public Capability capability() {
      return Capability.WASTE_RATE_ADMIN;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      long id = RatingInput.id(body, "wstrtId");
      Map<String, Object> row = repo.wasteRate(id);
      RatingInput.checkLock(col(row, "update_timestamp"), body);
      DateValue scaleDate = RatingInput.date(body, "effectiveDate");
      String species = RatingInput.upper(body, "scaleSpeciesCode");
      String grade = RatingInput.upper(body, "scaleGradeCode");
      String source = RatingInput.upper(body, "wasteRateSourceCode");
      NumberValue rate = RatingInput.number(body, "rate");

      Problems p = new Problems();
      boolean dateOk = RatingRules.validatorDate(p, "effectiveDate", "Scale Date", scaleDate);
      RatingRules.validatorRate(p, "rate", rate, MAX_RATE, "999.99");
      selects(p, species, grade, source);
      p.throwIfAny();

      LocalDate day = scaleDate.date();
      LocalDate onFile = day(row, "effective_date");
      LocalDate today = today();
      if (dateOk && !day.equals(onFile)) {
        if (RatingRules.olderThanSevenYears(onFile, today)) {
          p.add("effectiveDate", RatingMessages.WRR_EFF_ON_FILE_OLD);
        } else if (RatingRules.olderThanSevenYears(day, today)) {
          p.add("effectiveDate", RatingMessages.WRR_EFF_OLD);
        }
      }
      speciesProductGrade(p, repo, species, grade);
      if (!"Y".equals(str(row, "active_ind"))) {
        p.add("activeInd", RatingMessages.WRR_INACTIVE);
      }
      p.throwIfAny();

      String timberMark = str(row, "timber_mark");
      String cutBlock = str(row, "cut_block_id");
      List<Map<String, Object>> matching = repo.activeWasteRates(timberMark, cutBlock, species, grade, day, null);
      if (!matching.isEmpty()) {
        Map<String, Object> first = matching.get(0);
        if (((Number) col(first, "wstrt_id")).longValue() != id) {
          // Stamped with this update (legacy re-stored the row's old UPDATE_ values) so RowLock sees the change.
          tableApi.call(STORE, storeArgs(STORE, first).put("i_active_ind", "N"));
        }
      }
      tableApi.call(STORE, storeArgs(STORE, row)
          .put("i_scale_species_code", species)
          .put("i_scale_product_code", RatingInput.LOGS)
          .put("i_scale_grade_code", grade)
          .put("i_active_ind", "Y")
          .put("i_avoidable_ind", "Y")
          .put("i_rate", rate.value())
          .put("i_effective_date", day)
          .put("i_expiry_date", day)
          .put("i_waste_rate_source_code", source));
      return Map.of("wstrtId", id);
    }
  }

  /**
   * P223 "Delete" ({@code $V/plu/P223Action} ACTION_DELETE_RATE): a soft
   * delete — the row is stored with ACTIVE_IND N. Refused for an inactive rate
   * (the same guard as Save).
   */
  @Component
  static class Deactivate extends RatingCommand {
    Deactivate(RatingRepository repo, TableApi tableApi, Audit audit, RatingClock clock) {
      super(repo, tableApi, audit, clock);
    }

    @Override
    public String id() {
      return "rating.wasteRate.deactivate";
    }

    @Override
    public String legacy() {
      return "P223 Delete (P223Action ACTION_DELETE_RATE, updateWasteResidueRate)";
    }

    @Override
    public Capability capability() {
      return Capability.WASTE_RATE_ADMIN;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      long id = RatingInput.id(body, "wstrtId");
      Map<String, Object> row = repo.wasteRate(id);
      RatingInput.checkLock(col(row, "update_timestamp"), body);
      requireActive(row);
      tableApi.call(STORE, storeArgs(STORE, row).put("i_active_ind", "N"));
      return Map.of("wstrtId", id);
    }
  }
}
