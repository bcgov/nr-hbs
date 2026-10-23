package ca.bc.gov.nrs.hbs.api.summaryreturns.generated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** Eligibility per type and the P054 preview amounts ({@link CorrectableDetails}), and the bean's delegation. */
class LegacyGeneratedSummaryDetailsTest {

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final CorrectableDetails correctable = new CorrectableDetails(jdbc);

  private SummaryReturn ret(SummaryReturnType type) {
    DetailFixtures f = new DetailFixtures();
    f.s.header(700, type, "OCM", true);
    f.s.version(type, 700, 3, "ISS", true);
    return f.s.repo.load(700, 3);
  }

  private void count(String txnTable, long n) {
    when(jdbc.queryForObject(argThat((String sql) -> sql != null && sql.contains("FROM " + txnTable.toLowerCase() + " txn")),
        any(SqlParameterSource.class), eq(Long.class))).thenReturn(n);
  }

  @Test
  void pieceIsEligibleThroughLogTalliesOrElseSfpTallies() {
    count("LOG_TALLY_TXN", 0);
    count("SFP_TALLY_TXN", 2);
    assertThat(correctable.any(SummaryReturnType.PIECE, 700, 1)).isTrue();
  }

  @Test
  void weightIsEligibleThroughWeighSlipsOnly() {
    count("WEIGH_SLIP_TXN", 0);
    assertThat(correctable.any(SummaryReturnType.WEIGHT, 700, 1)).isFalse();
    count("WEIGH_SLIP_TXN", 1);
    assertThat(correctable.any(SummaryReturnType.WEIGHT, 700, 1)).isTrue();
  }

  @Test
  void sampleIsNeverEligible() {
    assertThat(correctable.any(SummaryReturnType.SAMPLE, 700, 1)).isFalse();
  }

  @Test
  void theCorrectablePredicateIsHbsCountCorrectableDetails() {
    String sql = CorrectableDetails.correctableTxns(CorrectableDetails.WEIGH_SLIP);
    assertThat(sql).contains("FROM weigh_slip_txn txn", "txn.hbs_txn_status_code = 'ISS'",
        "txn2.hbs_txn_status_code IN ('CRT', 'FAL')", "FROM weigh_slip v",
        "v.scl_rtn_version_state_code NOT IN ('ISS')", "doc.scale_return_state_code NOT IN ('OCM', 'RCC', 'RNC')",
        ":dcn", ":version");
  }

  @Test
  void amountsAreRoundedSumsOfTheCorrectableTxnSegregations() {
    when(jdbc.queryForMap(argThat((String sql) -> sql != null && sql.contains("FROM weigh_slip_txn_seg")),
        any(SqlParameterSource.class)))
        .thenReturn(Map.of("VOLUME", new BigDecimal("12.3456"), "VALUE", new BigDecimal("99.995")));
    BigDecimal[] a = correctable.amounts(SummaryReturnType.WEIGHT, 700, 1);
    assertThat(a[0]).isEqualTo(new BigDecimal("12.346"));
    assertThat(a[1]).isEqualTo(new BigDecimal("100.00"));
  }

  @Test
  void theBeanAsksForSummaryVersionOne() {
    count("WEIGH_SLIP_TXN", 1);
    when(jdbc.queryForMap(any(String.class), any(SqlParameterSource.class)))
        .thenReturn(Map.of("VOLUME", BigDecimal.ONE, "VALUE", BigDecimal.TEN));
    DetailFixtures f = new DetailFixtures();
    LegacyGeneratedSummaryDetails bean = new LegacyGeneratedSummaryDetails(correctable, f.reset, f.extension);
    SummaryReturn weight = ret(SummaryReturnType.WEIGHT);

    assertThat(bean.isEligibleForRateCorrection(weight)).isTrue();
    var amounts = bean.correctableAmounts(weight);
    assertThat(amounts.volume()).isEqualTo(new BigDecimal("1.000"));
    assertThat(amounts.value()).isEqualTo(new BigDecimal("10.00"));
    verify(jdbc).queryForObject(any(String.class),
        argThat((SqlParameterSource p) -> p instanceof MapSqlParameterSource m
            && Long.valueOf(700).equals(m.getValue("dcn")) && Long.valueOf(1).equals(m.getValue("version"))),
        eq(Long.class));
  }
}
