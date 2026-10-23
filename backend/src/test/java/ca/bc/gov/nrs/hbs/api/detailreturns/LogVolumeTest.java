package ca.bc.gov.nrs.hbs.api.detailreturns;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.LogLine;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** LogUtil.netVolume (smalian, float/BigDecimal mix), values from an independent re-computation. */
class LogVolumeTest {

  @ParameterizedTest
  @CsvSource({
      "5.3,  20,  30,  0.0,   1.082",
      "10.0, 15,  15,  0.0,   0.707",
      "12.4, 22,  28,  0.123, 2.347",
      "0.0,  0,   0,   0.0,   0.0",
      "99.9, 199, 199, 0.0,   1242.858"
  })
  void netVolume(float length, int top, int butt, float defects, float expected) {
    assertThat(LogVolume.netVolume(length, top, butt, defects)).isEqualTo(expected);
  }

  @Test
  void lineUsesDefectsAndNullDimensions() {
    LogLine line = new LogLine(new Row().set("length", new BigDecimal("12.4")).set("top", 22).set("butt", 28));
    line.defects().add(new Row().set("volume", new BigDecimal("0.100")));
    line.defects().add(new Row().set("volume", new BigDecimal("0.023")));
    assertThat(LogVolume.netVolume(line)).isEqualTo(2.347f);
    assertThat(LogVolume.netVolume(new LogLine(new Row()))).isEqualTo(0.0f);
  }

  @Test
  void totalNetVolume() {
    LogLine a = new LogLine(new Row().set("net_volume", new BigDecimal("1.082")));
    LogLine b = new LogLine(new Row().set("net_volume", new BigDecimal("0.707")));
    assertThat(LogVolume.decimal(LogVolume.totalNetVolume(List.of(a, b)))).isEqualByComparingTo("1.789");
    assertThat(LogVolume.totalNetVolume(List.of())).isEqualTo(0.0f);
  }
}
