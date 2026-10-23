package ca.bc.gov.nrs.hbs.api.summaryreturns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Table tests of the ported state classes (ScaleReturnStateFactory + ScaleReturn*State). */
class ScaleReturnStateTest {

  private static ScaleReturnState s(String doc, String ver) {
    return new ScaleReturnState(doc, ver, true);
  }

  @ParameterizedTest
  @CsvSource({
      "OIP,INC,HELD", "OIP,HLD,HELD", "CIP,INC,HELD", "CWC,INC,HELD", "RCP,HLD,HELD",
      "OCM,ISS,INVOICED", "CCM,CAN,INVOICED", "RCP,CAN,INVOICED",
      "OIP,LCK,LOCKED", "OIP,RDY,READY", "OIP,ERR,READY", "OIP,NTI,READY", "OIP,SPR,READY",
      "OIP,ACC,RELEASABLE", "CWC,ISS,RELEASABLE", "CWC,PND,RELEASABLE",
      "CWR,AWP,AWAITING_APPROVAL", "DIS,DEL,DISCARDED", "OCM,DEL,INVALID"})
  void kinds(String doc, String ver, ScaleReturnState.Kind kind) {
    assertThat(s(doc, ver).kind()).isEqualTo(kind);
  }

  @ParameterizedTest
  @CsvSource({
      // doc, ver, generated → release
      "OIP,INC,false,OIP,RDY", "OIP,HLD,false,OIP,RDY", "CWC,INC,false,CWC,PND", "CWC,INC,true,CWC,RDY",
      "RCP,INC,false,RCP,RDY", "CWR,AWP,true,CWR,RDY", "CWC,ISS,false,CWC,RDY"})
  void release(String doc, String ver, boolean generated, String doc2, String ver2) {
    assertThat(s(doc, ver).release(generated)).isEqualTo(s(doc2, ver2));
  }

  @ParameterizedTest
  @CsvSource({"OIP,INC,NTI", "OIP,HLD,NTI", "OIP,ERR,NTI", "OIP,SPR,RDY", "CWC,INC,PND"})
  void releaseAreaBased(String doc, String ver, String ver2) {
    assertThat(s(doc, ver).releaseAreaBased(false).version()).isEqualTo(ver2);
  }

  @ParameterizedTest
  @CsvSource({"OIP,INC,ERR", "CWC,INC,PND", "RCP,HLD,ERR"})
  void failRelease(String doc, String ver, String ver2) {
    assertThat(s(doc, ver).failRelease().version()).isEqualTo(ver2);
  }

  @ParameterizedTest
  @CsvSource({
      // held discards
      "OIP,INC,DIS,DEL", "CWC,INC,OCM,DEL", "CWR,INC,OCM,ISS", "CIP,INC,OCM,ISS", "RCP,HLD,CCM,DEL",
      "RNP,INC,CCM,CAN",
      // ready discards
      "OIP,RDY,DIS,DEL", "CWC,RDY,OCM,ISS", "CWC,NTI,OCM,DEL", "RCP,ERR,CCM,DEL", "RNP,RDY,CCM,CAN",
      // awaiting approval
      "CWR,AWP,OCM,DEL"})
  void discard(String doc, String ver, String doc2, String ver2) {
    assertThat(s(doc, ver).discard(false)).isEqualTo(s(doc2, ver2));
  }

  @ParameterizedTest
  @CsvSource({"OCM,ISS", "CCM,CAN", "RNC,ISS"})
  void invoicedTransitions(String doc, String ver) {
    ScaleReturnState st = s(doc, ver);
    assertThat(st.cancel()).isEqualTo(s("CIP", "RDY"));
    assertThat(st.cancelAreaBased()).isEqualTo(s("CIP", "NTI"));
    assertThat(st.replaceWithoutChanges()).isEqualTo(s("RNP", "RDY"));
    assertThat(st.replaceWithChanges()).isEqualTo(s("RCP", "INC"));
    assertThat(st.cancelAndReplaceWithChanges()).isEqualTo(s("CWC", "INC"));
    assertThat(st.cancelAndReplaceWithoutChanges()).isEqualTo(s("CWR", "RDY"));
    assertThat(st.cancelAndReplaceWithoutChangesAreaBased()).isEqualTo(s("CWR", "NTI"));
    assertThat(st.requestRateChange()).isEqualTo(s("CWR", "AWP"));
  }

  @ParameterizedTest
  @CsvSource({"OCM,ISS,true", "CCM,CAN,true", "RNC,ISS,false", "RCC,ISS,false"})
  void replaceWithChangesAllowed(String doc, String ver, boolean allowed) {
    assertThat(s(doc, ver).replaceWithChangesAllowed()).isEqualTo(allowed);
  }

  @ParameterizedTest
  @CsvSource({"OIP,RDY,true", "RCP,ERR,true", "CIP,RDY,false", "OIP,INC,false"})
  void holdAllowed(String doc, String ver, boolean allowed) {
    assertThat(s(doc, ver).holdAllowed()).isEqualTo(allowed);
  }

  @ParameterizedTest
  @CsvSource({"OIP,INC", "OIP,LCK", "DIS,DEL", "OCM,DEL"})
  void unavailableOperationsThrow(String doc, String ver) {
    assertThatThrownBy(() -> s(doc, ver).cancel()).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("operation is not available");
  }

  @org.junit.jupiter.api.Test
  void holdSupersedesAndDeactivates() {
    assertThat(s("OIP", "RDY").hold()).isEqualTo(new ScaleReturnState("OIP", "SPR", false));
    assertThatThrownBy(() -> s("OIP", "INC").hold()).isInstanceOf(IllegalStateException.class);
  }
}
