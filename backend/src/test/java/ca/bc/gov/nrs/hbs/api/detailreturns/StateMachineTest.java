package ca.bc.gov.nrs.hbs.api.detailreturns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailVersion;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.DocumentState;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.StateManager;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.VersionState;
import java.util.function.Consumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/** StateManager commands over VersionState/DocumentState, table-driven from the legacy rules. */
class StateMachineTest {

  private static DetailDocument doc(String docState, String status, boolean active) {
    VersionState vs = new VersionState(status, active);
    return new DetailDocument(DocType.LOG_TALLY, new Row(), new DocumentState(docState, vs),
        new DetailVersion(new Row(), vs, true), true);
  }

  private static Consumer<DetailDocument> command(String name) {
    return switch (name) {
      case "cancel" -> StateManager::cancel;
      case "approve" -> StateManager::approve;
      case "reject" -> StateManager::reject;
      case "discard" -> StateManager::discard;
      case "replaceWithChanges" -> StateManager::replaceWithChanges;
      case "replaceWithNoChanges" -> StateManager::replaceWithNoChanges;
      case "valid" -> StateManager::valid;
      case "invalid" -> StateManager::invalid;
      case "hold" -> StateManager::hold;
      case "supercede" -> StateManager::supercede;
      case "release" -> StateManager::release;
      case "process" -> StateManager::process;
      case "nonBillable" -> StateManager::nonBillable;
      default -> throw new IllegalArgumentException(name);
    };
  }

  @ParameterizedTest
  @CsvSource({
      // command, doc, version, active → doc, version, active
      "cancel, OCM, ISS, true, CIP, AWP, true",
      "cancel, RNP, RDY, true, RCP, CAN, false",
      "approve, CIP, AWP, true, CIP, RDY, true",
      "approve, CWC, AWP, true, CWC, PND, false",
      "approve, CWC, ISS, false, CWC, RDY, true",
      "approve, RCP, AWP, true, RCP, PND, true",
      "approve, RNP, AWP, true, RNP, RDY, true",
      "reject, CIP, AWP, true, OCM, ISS, true",
      "reject, CWC, AWP, true, OCM, DEL, false",
      "reject, RCP, AWP, true, RCP, DEL, false",
      "reject, RNP, AWP, true, CCM, CAN, true",
      "reject, OCM, ISS, false, OCM, ISS, true",
      "reject, CCM, CAN, false, CCM, CAN, true",
      "discard, OIP, INC, true, OIP, DEL, true",
      "discard, RCP, ERR, true, CCM, DEL, false",
      "discard, CWC, INC, true, OCM, DEL, false",
      "replaceWithChanges, OCM, ISS, true, CWC, ISS, false",
      "replaceWithChanges, CCM, CAN, true, RCP, CAN, false",
      "replaceWithNoChanges, OCM, ISS, true, CWR, AWP, true",
      "replaceWithNoChanges, CCM, CAN, true, RNP, AWP, true",
      "valid, OIP, INC, true, OIP, RDY, true",
      "valid, RCP, INC, true, RCP, AWP, true",
      "invalid, OIP, INC, true, OIP, ERR, true",
      "hold, OIP, RDY, true, OIP, HLD, true",
      "supercede, OIP, RDY, true, OIP, SPR, false",
      "release, CWC, INC, true, CWC, AWP, true",
      "release, RCP, HLD, true, RCP, AWP, true",
      "process, OIP, PNV, false, OIP, RDY, true",
      "process, OCM, ACC, false, CWC, AWP, true",
      "process, CIP, ACC, false, CIP, PNV, false",
      "nonBillable, OIP, RDY, true, OIP, NTI, true"
  })
  void transitions(String cmd, String docState, String status, boolean active,
                   String expDoc, String expStatus, boolean expActive) {
    DetailDocument d = doc(docState, status, active);
    command(cmd).accept(d);
    assertThat(d.state().value()).isEqualTo(expDoc);
    assertThat(d.version().state().value()).isEqualTo(expStatus);
    assertThat(d.version().state().isActive()).isEqualTo(expActive);
  }

  @ParameterizedTest
  @CsvSource({
      "cancel, OIP, RDY, true, cancelRequested(): Illegal transition from ready (RDY) active",
      "approve, OIP, AWP, true, Cannot approve a document with state original in progress (OIP) awaiting approval (AWP) active",
      "discard, OCM, ISS, true, Document cannot be discarded.",
      "hold, OIP, RDY, false, Document version is not active.",
      "release, OIP, INC, true, Cannot release document with state original in progress (OIP) incomplete (INC) active",
      "reject, OIP, RDY, true, Document version is not awaiting approval or is not active."
  })
  void refusedWithLegacyMessages(String cmd, String docState, String status, boolean active, String message) {
    DetailDocument d = doc(docState, status, active);
    assertThatThrownBy(() -> command(cmd).accept(d)).hasMessage(message);
  }

  @Test
  void unknownStatesAreRejected() {
    assertThatThrownBy(() -> new VersionState("XXX", true)).hasMessage("Unknown version state value XXX");
  }
}
