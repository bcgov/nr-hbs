package ca.bc.gov.nrs.hbs.api.rating;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.rating.RatingInput.DateValue;
import ca.bc.gov.nrs.hbs.api.rating.RatingInput.NumberValue;
import ca.bc.gov.nrs.hbs.api.rating.RatingInput.Problems;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * District default rates (P212 Add / P213 Update). Two phases, as Struts ran
 * them: the validation.xml rules first (P212Form / P213Form — when any fails
 * the action never runs), then the action's {@code validateForm}.
 */
final class DistrictRateCommands {

  private static final BigDecimal MAX_RATE = new BigDecimal("9999.99");

  private DistrictRateCommands() {
  }

  /** validation.xml {@code effective} (dateRequired, hbsDate, fromBeforeTo) and {@code expiry}. */
  private static boolean validatorDates(Problems p, DateValue eff, DateValue exp) {
    boolean effOk = RatingRules.validatorDate(p, "effectiveDate", "Effective Date", eff);
    boolean expOk = RatingRules.validatorDate(p, "expiryDate", "Expiry Date", exp);
    if (effOk && expOk && eff.date().isAfter(exp.date())) {
      p.add("effectiveDate", RatingMessages.EFF_ON_OR_BEFORE_EXP);
      return false;
    }
    return effOk && expOk;
  }

  /**
   * P212 Add Default Rate ({@code $V/plu/P212Action}; validation.xml P212Form;
   * {@code RateManagerBean.createAndStoreHbsDistrictDefaultRate}). Species
   * blank = All (stored null); the rate is created active.
   */
  @Component
  static class Create extends RatingCommand {
    Create(RatingRepository repo, TableApi tableApi, Audit audit, RatingClock clock) {
      super(repo, tableApi, audit, clock);
    }

    @Override
    public String id() {
      return "rating.districtRate.create";
    }

    @Override
    public String legacy() {
      return "P212 Add Default Rate (P212Action.validateForm, createAndStoreHbsDistrictDefaultRate)";
    }

    @Override
    public Capability capability() {
      return Capability.RATE_ADMIN;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      DateValue eff = RatingInput.date(body, "effectiveDate");
      DateValue exp = RatingInput.date(body, "expiryDate");
      String orgUnit = RatingInput.text(body, "orgUnitNo");
      String method = RatingInput.upper(body, "appraisalMthdCode");
      String species = RatingInput.allOrCode(body, "scaleSpeciesCode");
      String product = RatingInput.product(body, "scaleProductCode");
      String grade = RatingInput.upper(body, "scaleGradeCode");
      NumberValue rate = RatingInput.number(body, "rate");

      // Phase 1 — validation.xml P212Form (+ the selects that had no empty option).
      Problems p = new Problems();
      boolean datesOk = validatorDates(p, eff, exp);
      Long orgUnitNo = null;
      if (orgUnit == null) {
        p.add("orgUnitNo", RatingMessages.required("District"));
      } else {
        try {
          orgUnitNo = Long.valueOf(orgUnit);
        } catch (NumberFormatException ex) {
          p.add("orgUnitNo", "District is invalid.");
        }
      }
      if (method == null) p.add("appraisalMthdCode", RatingMessages.required("Appraisal Method"));
      if (product == null) p.add("scaleProductCode", RatingMessages.required("Product"));
      if (grade == null) p.add("scaleGradeCode", RatingMessages.required("Grade"));
      RatingRules.validatorRate(p, "rate", rate, MAX_RATE, "$9,999.99");
      p.throwIfAny();

      // Phase 2 — P212Action.validateForm.
      LocalDate today = today();
      if (datesOk) {
        if (RatingRules.olderThanSevenYears(eff.date(), today)) p.add("effectiveDate", RatingMessages.DDR_EFF_OLD);
        if (RatingRules.olderThanSevenYears(exp.date(), today)) p.add("expiryDate", RatingMessages.DDR_EXP_OLD);
        RatingRules.districtIntervals(p, eff.date(), exp.date(),
            repo.activeDistrictRates(orgUnitNo, method, species, product, grade), null);
      }
      if (species != null && repo.speciesProductGrade(species, product, grade).isEmpty()) {
        p.add("scaleSpeciesCode", RatingMessages.SPG_INVALID);
      }
      RatingRules.districtGradeSchedule(p, method, product, repo.scaleGrade(grade, method).orElse(null),
          eff.date(), exp.date(), true);
      p.throwIfAny();

      long id = tableApi.nextId("HBS_DISTRICT_DEFAULT_RATE_SEQ");
      Timestamp now = audit.now();
      String user = audit.user();
      tableApi.call("HBS_CREATE_HBS_DISTRICT_RATE", ProcArgs.of()
          .put("i_distrt_id", id)
          .put("i_org_unit_no", orgUnitNo)
          .put("i_scale_species_code", species)
          .put("i_scale_product_code", product)
          .put("i_scale_grade_code", grade)
          .put("i_rate", rate.value())
          .put("i_active_ind", "Y")
          .put("i_effective_date", eff.date())
          .put("i_expiry_date", exp.date())
          .put("i_entry_userid", user)
          .put("i_entry_timestamp", now)
          // P212Action passed UPDATEUSERID/UPDATETIMESTAMP null; the table API proc stamps them as given.
          .put("i_update_userid", user)
          .put("i_update_timestamp", now)
          .put("i_appraisal_mthd_code", method));
      return Map.of("distrtId", id);
    }
  }

  /**
   * P213 Update Default Rate ({@code $V/plu/P213Action}; validation.xml
   * P213Form; {@code RateManagerBean.updateHbsDistrictDefaultRate}): the rate,
   * effective and expiry dates change; district, method and
   * species-product-grade are fixed. Dates on file older than 7 years cannot
   * be changed (BT2973 / ticket 4101).
   */
  @Component
  static class Update extends RatingCommand {
    Update(RatingRepository repo, TableApi tableApi, Audit audit, RatingClock clock) {
      super(repo, tableApi, audit, clock);
    }

    @Override
    public String id() {
      return "rating.districtRate.update";
    }

    @Override
    public String legacy() {
      return "P213 Update Default Rate (P213Action.validateForm, updateHbsDistrictDefaultRate)";
    }

    @Override
    public Capability capability() {
      return Capability.RATE_ADMIN;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      long id = RatingInput.id(body, "distrtId");
      Map<String, Object> row = repo.districtRate(id);
      RatingInput.checkLock(col(row, "update_timestamp"), body);
      DateValue eff = RatingInput.date(body, "effectiveDate");
      DateValue exp = RatingInput.date(body, "expiryDate");
      NumberValue rate = RatingInput.number(body, "rate");

      Problems p = new Problems();
      boolean datesOk = validatorDates(p, eff, exp);
      RatingRules.validatorRate(p, "rate", rate, MAX_RATE, "$9,999.99");
      p.throwIfAny();

      LocalDate today = today();
      LocalDate effOnFile = day(row, "effective_date");
      LocalDate expOnFile = day(row, "expiry_date");
      String method = str(row, "appraisal_mthd_code");
      String product = (String) col(row, "scale_product_code");
      // The rate bean's values, untrimmed (P213Action passed rate.getScaleGradeCode() as stored).
      String grade = (String) col(row, "scale_grade_code");
      String species = (String) col(row, "scale_species_code");
      if (datesOk) {
        boolean effChanged = !eff.date().equals(effOnFile);
        boolean expChanged = !exp.date().equals(expOnFile);
        if (effChanged && RatingRules.olderThanSevenYears(effOnFile, today)) {
          p.add("effectiveDate", RatingMessages.DDR_EFF_ON_FILE_OLD);
        }
        if (expChanged && RatingRules.olderThanSevenYears(expOnFile, today)) {
          p.add("expiryDate", RatingMessages.DDR_EXP_ON_FILE_OLD);
        }
        if (effChanged && !RatingRules.olderThanSevenYears(effOnFile, today)
            && RatingRules.olderThanSevenYears(eff.date(), today)) {
          p.add("effectiveDate", RatingMessages.DDR_EFF_OLD);
        }
        if (expChanged && !RatingRules.olderThanSevenYears(expOnFile, today)
            && RatingRules.olderThanSevenYears(exp.date(), today)) {
          p.add("expiryDate", RatingMessages.DDR_EXP_OLD);
        }
        // P213 dereferenced the grade lookup only for logs (and failed when it was missing).
        RatingRules.districtGradeSchedule(p, method, product, repo.scaleGrade(grade, method).orElse(null),
            eff.date(), exp.date(), false);
        RatingRules.districtIntervals(p, eff.date(), exp.date(),
            repo.activeDistrictRates(((Number) col(row, "org_unit_no")).longValue(), method, species, product,
                grade), id);
      }
      p.throwIfAny();

      String proc = "HBS_STORE_HBS_DISTRICT_RATE";
      tableApi.call(proc, storeArgs(proc, row)
          .put("i_rate", rate.value())
          .put("i_effective_date", eff.date())
          .put("i_expiry_date", exp.date()));
      return Map.of("distrtId", id);
    }
  }
}
