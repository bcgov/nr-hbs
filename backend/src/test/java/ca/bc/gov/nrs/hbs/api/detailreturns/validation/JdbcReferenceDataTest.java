package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.ClientLocationRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.PopulationRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.RateCheck;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.ScaleSiteRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.StratumRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.TimberMarkRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.WorksheetRef;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * {@link JdbcReferenceData} SQL against an in-memory H2 (Oracle mode) with the
 * columns of the nr-mof-db tables it reads. Checks the queries parse, bind and
 * map; Oracle itself is not available to unit tests.
 */
class JdbcReferenceDataTest {

  JdbcTemplate db;
  JdbcReferenceData ref;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource ds = new DriverManagerDataSource(
        "jdbc:h2:mem:ref" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", "");
    db = new JdbcTemplate(ds);
    db.execute("""
        CREATE TABLE timber_mark (timber_mark VARCHAR2(6), forest_file_id VARCHAR2(10), forest_district NUMBER(10),
          quota_type_code VARCHAR2(1), cruise_based_ind VARCHAR2(1), mark_status_st VARCHAR2(3),
          mark_issue_date DATE, mark_expiry_date DATE, mark_extend_date DATE)""");
    db.execute("""
        CREATE TABLE mark_billed_cli (timber_mark VARCHAR2(6), forest_file_id VARCHAR2(10), file_type_code VARCHAR2(3),
          forest_region NUMBER(10), region_code VARCHAR2(3), client_number VARCHAR2(8), client_locn_code VARCHAR2(2))""");
    db.execute("CREATE TABLE harvest_sale (forest_file_id VARCHAR2(10), payment_method_cd VARCHAR2(1))");
    db.execute("""
        CREATE TABLE scale_site (scale_site_id_nmbr VARCHAR2(4), org_unit_no NUMBER(10), owner_cli_number VARCHAR2(8),
          owner_cli_locn_cd VARCHAR2(2), site_name VARCHAR2(40), timber_brand VARCHAR2(7))""");
    db.execute("CREATE TABLE cut_block (cut_block_id VARCHAR2(10), timber_mark VARCHAR2(10))");
    db.execute("CREATE TABLE quant_license (license_number VARCHAR2(10), quant_lic_type_cd VARCHAR2(1))");
    db.execute("""
        CREATE TABLE stratum (population_number VARCHAR2(4), sampling_year NUMBER(5), stratum_number VARCHAR2(2),
          client_number VARCHAR2(8), client_locn_code VARCHAR2(2), grade_schedule_code VARCHAR2(1),
          effective_date DATE, expiry_date DATE)""");
    db.execute("""
        CREATE TABLE population (population_number VARCHAR2(4), sampling_year NUMBER(5), client_number VARCHAR2(8),
          client_locn_code VARCHAR2(2), ratio_type_code VARCHAR2(1))""");
    db.execute("CREATE TABLE v_client_public (client_number VARCHAR2(8), client_status_code VARCHAR2(3))");
    db.execute("""
        CREATE TABLE client_location (client_number VARCHAR2(8), client_locn_code VARCHAR2(2),
          locn_expired_ind VARCHAR2(1))""");
    db.execute("""
        CREATE TABLE override_rating_rule (ovrrdrtrl_type VARCHAR2(10), timber_mark VARCHAR2(6),
          field_scale_deck_id VARCHAR2(1), override_rule_status_code VARCHAR2(3), processing_effective_date DATE,
          processing_expiry_date DATE, scale_effective_date DATE, scale_expiry_date DATE, override_rate_date DATE)""");
    db.execute("""
        CREATE TABLE app_worksheet (timber_mark VARCHAR2(6), app_effective_date DATE, expiry_date DATE,
          appraisal_mthd_cd VARCHAR2(1), appraisal_sts_st VARCHAR2(3), conif_stand_rate_elig_code VARCHAR2(1),
          decid_stand_rate_elig_code VARCHAR2(1))""");
    db.execute("CREATE TABLE app_stmpg_rate (timber_mark VARCHAR2(6), app_effective_date DATE)");
    db.execute("CREATE TABLE org_unit (org_unit_no NUMBER(10), org_unit_code VARCHAR2(6))");
    ref = new JdbcReferenceData(db);
  }

  static Date pacific(int y, int m, int d) {
    // noon wall time: the same calendar day in any JVM zone
    return Timestamp.valueOf(LocalDateTime.of(y, m, d, 12, 0));
  }

  void mark(String mark, String fileFile, String region) {
    db.update("INSERT INTO timber_mark VALUES (?, ?, 5, 'A', 'Y', 'HI', DATE '2020-01-01', DATE '2030-12-31', NULL)",
        mark, fileFile);
    db.update("INSERT INTO mark_billed_cli VALUES (?, ?, 'A01', 7, ?, '00012345', '00')", mark, fileFile, region);
  }

  @Test
  void timberMarkMapsEveryComponent() {
    mark("ABC123", "F1", "RCO");
    db.update("INSERT INTO harvest_sale VALUES ('F1', 'C')");

    TimberMarkRef m = ref.timberMark("ABC123").orElseThrow();

    assertThat(m).isEqualTo(new TimberMarkRef("ABC123", "A01", "Y", "C", "00012345", "00", 5L, 7L, "RCO", "A",
        "HI", Timestamp.valueOf("2020-01-01 00:00:00"), Timestamp.valueOf("2030-12-31 00:00:00"), null));
    assertThat(m.isCashSale()).isTrue();
  }

  @Test
  void timberMarkWithoutHarvestSaleHasNoPaymentMethod() {
    mark("ABC123", "F1", "RCO");
    assertThat(ref.timberMark("ABC123").orElseThrow().paymentMethodCode()).isNull();
  }

  @Test
  void timberMarkNotBilledIsNotFound() {
    db.update("INSERT INTO timber_mark (timber_mark, forest_file_id) VALUES ('NOBILL', 'F2')");
    assertThat(ref.timberMark("NOBILL")).isEmpty();
    assertThat(ref.timberMark("MISSING")).isEmpty();
    assertThat(ref.timberMark(null)).isEmpty();
  }

  @Test
  void scaleSiteByIdAndByBrand() {
    db.update("INSERT INTO scale_site VALUES ('S001', 3, '00012345', '00', 'Main', 'BR1')");
    db.update("INSERT INTO scale_site VALUES ('S002', 3, '00012345', '00', 'Two', 'DUP')");
    db.update("INSERT INTO scale_site VALUES ('S003', 3, '00012345', '00', 'Three', 'DUP')");

    assertThat(ref.scaleSite("S001")).contains(new ScaleSiteRef("S001", 3L, "00012345", "00", "Main", "BR1"));
    assertThat(ref.scaleSite("NONE")).isEmpty();
    assertThat(ref.scaleSiteByTimberBrand("BR1")).map(ScaleSiteRef::scaleSiteIdNmbr).contains("S001");
    // singleFindBySql: several rows is a FinderException → not found
    assertThat(ref.scaleSiteByTimberBrand("DUP")).isEmpty();
    assertThat(ref.scaleSiteByTimberBrand("")).isEmpty();
    assertThat(ref.scaleSiteByTimberBrand("NONE")).isEmpty();
  }

  @Test
  void cutBlockExists() {
    db.update("INSERT INTO cut_block VALUES ('CB1', 'ABC123')");
    assertThat(ref.cutBlockExists("CB1", "ABC123")).isTrue();
    assertThat(ref.cutBlockExists("CB1", "OTHER")).isFalse();
    assertThat(ref.cutBlockExists(null, "ABC123")).isFalse();
  }

  @Test
  void scalerLicenceMustBeOfTypeS() {
    db.update("INSERT INTO quant_license VALUES ('1234', 'S')");
    db.update("INSERT INTO quant_license VALUES ('9999', 'R')");
    assertThat(ref.scalerLicenceExists("1234")).isTrue();
    assertThat(ref.scalerLicenceExists("9999")).isFalse();
    assertThat(ref.scalerLicenceExists(null)).isFalse();
  }

  @Test
  void stratumAndPopulation() {
    db.update("INSERT INTO stratum VALUES ('P1', 2024, '01', '00012345', '00', 'C', DATE '2024-01-01', "
        + "DATE '2024-12-31')");
    db.update("INSERT INTO population VALUES ('P1', 2024, '00012345', '00', 'D')");

    assertThat(ref.stratum("P1", "01", 2024L)).contains(new StratumRef("P1", "01", 2024L, "00012345", "00", "C",
        Timestamp.valueOf("2024-01-01 00:00:00"), Timestamp.valueOf("2024-12-31 00:00:00")));
    assertThat(ref.stratum("P1", "02", 2024L)).isEmpty();
    PopulationRef pop = ref.population("P1", 2024L).orElseThrow();
    assertThat(pop).isEqualTo(new PopulationRef("P1", 2024L, "00012345", "00", null, null, "D"));
    assertThat(pop.isDefaultRatio()).isTrue();
    assertThat(ref.population("P1", 2023L)).isEmpty();
  }

  @Test
  void clientLocationPadsTheKeysAndReadsStatusFromVClientPublic() {
    db.update("INSERT INTO v_client_public VALUES ('00012345', 'DAC')");
    db.update("INSERT INTO client_location VALUES ('00012345', '01', 'Y')");

    ClientLocationRef loc = ref.clientLocation("12345", "1").orElseThrow();

    assertThat(loc).isEqualTo(new ClientLocationRef("00012345", "01", "DAC", "Y"));
    assertThat(loc.isDeactivated()).isTrue();
    assertThat(loc.isExpired()).isTrue();
    assertThat(ref.clientLocation("00012345", "02")).isEmpty();
    assertThat(ref.clientLocation(null, "01")).isEmpty();
  }

  @Test
  void padding() {
    assertThat(JdbcReferenceData.padClientNumber("12")).isEqualTo("00000012");
    assertThat(JdbcReferenceData.padClientNumber("123456789")).isEqualTo("123456789");
    assertThat(JdbcReferenceData.padLocationCode("")).isEqualTo("00");
    assertThat(JdbcReferenceData.padLocationCode("A")).isEqualTo("0A");
  }

  void worksheet(String from, String to, String method) {
    db.update("INSERT INTO app_worksheet VALUES ('ABC123', DATE '" + from + "', DATE '" + to + "', ?, 'CNF', 'A', "
        + "'B')", method);
  }

  @Test
  void confirmedWorksheetsOnTheScaleDay() {
    worksheet("2024-01-01", "2024-06-30", "I");
    db.update("INSERT INTO app_worksheet VALUES ('ABC123', DATE '2024-01-01', DATE '2024-06-30', 'C', 'PND', 'A', "
        + "'B')");

    assertThat(ref.confirmedWorksheets("ABC123", pacific(2024, 6, 30)))
        .containsExactly(new WorksheetRef("I", "A", "B"));
    assertThat(ref.confirmedWorksheets("ABC123", pacific(2024, 1, 1))).hasSize(1);
    assertThat(ref.confirmedWorksheets("ABC123", pacific(2024, 7, 1))).isEmpty();
    assertThat(ref.confirmedWorksheets("ABC123", null)).isEmpty();
  }

  void rule(String type, String deck, String rateDate) {
    db.update("INSERT INTO override_rating_rule VALUES (?, 'ABC123', ?, 'ACT', DATE '2000-01-01', DATE '9999-12-31', "
        + "DATE '2024-05-01', DATE '2024-05-31', " + (rateDate == null ? "NULL" : "DATE '" + rateDate + "'") + ")",
        type, deck);
  }

  @Test
  void rateCheckWithAppraisalAndStumpage() {
    worksheet("2024-01-01", "2024-06-30", "I");
    db.update("INSERT INTO app_stmpg_rate VALUES ('ABC123', TIMESTAMP '2024-01-01 10:00:00')");

    assertThat(ref.rateCheck("ABC123", pacific(2024, 5, 15), null)).isEqualTo(RateCheck.HAS_RATE);
    assertThat(ref.rateCheck("ABC123", pacific(2024, 7, 15), null)).isEqualTo(RateCheck.NO_RATE);
  }

  @Test
  void rateCheckWithoutStumpage() {
    worksheet("2024-01-01", "2024-06-30", "I");
    db.update("INSERT INTO app_stmpg_rate VALUES ('ABC123', DATE '2024-01-02')");
    assertThat(ref.rateCheck("ABC123", pacific(2024, 5, 15), null)).isEqualTo(RateCheck.NO_STUMPAGE);
  }

  @Test
  void rateCheckOverlappingAppraisals() {
    worksheet("2024-01-01", "2024-06-30", "I");
    worksheet("2024-05-01", "2024-12-31", "I");
    assertThat(ref.rateCheck("ABC123", pacific(2024, 5, 15), null)).isEqualTo(RateCheck.NO_RATE);
  }

  @Test
  void overrideRuleMatchesTheDeckOrNoDeck() {
    rule(RateCheckRules.TYPE_OVERRIDE_RULE, "A", null);

    assertThat(ref.rateCheck("ABC123", pacific(2024, 5, 15), "A")).isEqualTo(RateCheck.HAS_RATE);
    // a deck-specific rule does not apply to a deck-less return (and no appraisal)
    assertThat(ref.rateCheck("ABC123", pacific(2024, 5, 15), null)).isEqualTo(RateCheck.NO_RATE);
    // outside the rule's scale period
    assertThat(ref.rateCheck("ABC123", pacific(2024, 6, 1), "A")).isEqualTo(RateCheck.NO_RATE);
  }

  @Test
  void overlappingOverrideRules() {
    rule(RateCheckRules.TYPE_OVERRIDE_RULE, null, null);
    rule(RateCheckRules.TYPE_RATEDATE, null, "2023-03-01");
    assertThat(ref.rateCheck("ABC123", pacific(2024, 5, 15), null)).isEqualTo(RateCheck.OVERLAPPING_RULES);
  }

  @Test
  void rateDateRuleRatesOnTheRuleDate() {
    rule(RateCheckRules.TYPE_RATEDATE, null, "2023-03-01");
    worksheet("2023-01-01", "2023-06-30", "C");
    db.update("INSERT INTO app_stmpg_rate VALUES ('ABC123', DATE '2023-01-01')");

    assertThat(ref.rateCheck("ABC123", pacific(2024, 5, 15), null)).isEqualTo(RateCheck.HAS_RATE);
  }

  @Test
  void gradeScheduleFromTheAppraisalElseTheRegion() {
    mark("ABC123", "F1", "RSI");
    db.update("INSERT INTO org_unit VALUES (7, 'RSK')");
    worksheet("2024-01-01", "2024-06-30", "C");

    assertThat(ref.gradeScheduleType("ABC123", pacific(2024, 5, 15))).isEqualTo("C");
    // no appraisal on the day: region RSK (MARK_BILLED_CLI.FOREST_REGION = ORG_UNIT_NO 7) is interior
    assertThat(ref.gradeScheduleType("ABC123", pacific(2024, 8, 15))).isEqualTo("I");
    assertThat(ref.gradeScheduleType("NOMARK", pacific(2024, 8, 15))).isNull();
    assertThat(ref.gradeScheduleType("", pacific(2024, 8, 15))).isEmpty();
    assertThat(ref.gradeScheduleType("ABC123", null)).isEmpty();
  }

  @Test
  void dayBinding() {
    assertThat(JdbcReferenceData.at(LocalDate.of(2024, 5, 15))).isEqualTo(Timestamp.valueOf("2024-05-15 00:00:00"));
    assertThat(JdbcReferenceData.at(null)).isNull();
    assertThat(JdbcReferenceData.wallDay(Timestamp.valueOf("2024-05-15 23:59:59")))
        .isEqualTo(LocalDate.of(2024, 5, 15));
  }
}
