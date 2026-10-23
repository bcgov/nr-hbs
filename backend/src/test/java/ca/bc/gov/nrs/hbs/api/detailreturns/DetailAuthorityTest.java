package ca.bc.gov.nrs.hbs.api.detailreturns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.detailreturns.DetailAuthority.Button;
import ca.bc.gov.nrs.hbs.api.detailreturns.DetailAuthority.Caller;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailVersion;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.DocumentState;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.VersionState;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.access.AccessDeniedException;

/** FunctionsComparator.isFieldAvailable / isAuthorized, Buttons.jsp site tag, isDetailDocumentEditable. */
class DetailAuthorityTest {

  private final StubUserDomains domains = new StubUserDomains();
  private final DetailAuthority authority = new DetailAuthority(domains);

  static DetailDocument doc(String docState, String status, boolean active) {
    VersionState vs = new VersionState(status, active);
    Row v = new Row().set("detail_document_number", "D1").set("version", 1).set("scale_site_id_nmbr", "S001")
        .set("primary_license_number", "1234").set("timber_mark", "ABC123");
    return new DetailDocument(DocType.LOG_TALLY, new Row().set("detail_document_number", "D1"),
        new DocumentState(docState, vs), new DetailVersion(v, vs, true), true);
  }

  @ParameterizedTest
  @CsvSource({
      // button, doc, version, active, available
      "HOLD, OIP, RDY, true, true",
      "HOLD, OIP, ERR, true, true",
      "HOLD, OCM, RDY, true, false",
      "HOLD, OCM, NTI, true, true",
      "REEDIT, RCP, FAL, true, true",
      "CANCEL, OCM, ISS, true, true",
      "CANCEL, OIP, ISS, true, false",
      "CANCEL_REPLACE, RCC, ISS, true, true",
      "REPLACE, CCM, CAN, true, true",
      "REPLACE, CCM, CAN, false, false",
      "RELEASE, OIP, INC, true, true",
      "RELEASE, OCM, HLD, true, true",
      "RELEASE, CIP, INC, true, false",
      "SAVE, CWC, INC, true, true",
      "APPROVE_CHANGE, CIP, AWP, true, true",
      "CLEAR_DIGITAL_SIGNATURE_FAILURE, OIP, DSF, true, true",
      "DISCARD, OIP, INC, true, true",
      "DISCARD, RCP, AWP, true, false",
      "DISCARD, CWC, RDY, true, false",
      "DISCARD, CWC, ERR, true, true",
      "DISCARD, OCM, ERR, true, false"
  })
  void availability(Button button, String docState, String status, boolean active, boolean expected) {
    VersionState v = new VersionState(status, active);
    assertThat(DetailAuthority.isAvailable(button, new DocumentState(docState, v), v)).isEqualTo(expected);
  }

  @Test
  void dataEntryMayReleaseButNotHold() {
    Caller c = new Caller("IDIR\\DE", Set.of("HBS_DTL_DATA_ENT", "HBS_MOF_USER"));
    assertThat(authority.isShown(Button.RELEASE, doc("OIP", "INC", true), c)).isTrue();
    assertThat(authority.isShown(Button.HOLD, doc("OIP", "RDY", true), c)).isFalse();
  }

  @Test
  void siteAdminNeedsTheSiteInDomain() {
    Caller c = new Caller("BCEID\\SA", Set.of("HBS_CLI_SITE_ADMIN", "HBS_CLI_USER"));
    assertThat(authority.isShown(Button.HOLD, doc("OIP", "RDY", true), c)).isFalse();
    domains.siteDomains.add("BCEID\\SA/S001");
    assertThat(authority.isShown(Button.HOLD, doc("OIP", "RDY", true), c)).isTrue();
  }

  @Test
  void scaleAdminUsesDistrictDomainOfTheSite() {
    Caller c = new Caller("IDIR\\ADM", Set.of("HBS_SCALE_ADMIN", "HBS_MOF_USER"));
    domains.siteOrgUnits.put("S001", 7L);
    assertThat(authority.isShown(Button.CLEAR_DIGITAL_SIGNATURE_FAILURE, doc("OIP", "DSF", true), c)).isFalse();
    domains.districts.put("IDIR\\ADM", Set.of(7L));
    assertThat(authority.isShown(Button.CLEAR_DIGITAL_SIGNATURE_FAILURE, doc("OIP", "DSF", true), c)).isTrue();
  }

  @Test
  void scalerNeedsAKeyForTheLicence() {
    Caller c = new Caller("BCEID\\SC", Set.of("HBS_CLI_SCALER", "HBS_CLI_USER"));
    assertThat(authority.isShown(Button.CANCEL, doc("OCM", "ISS", true), c)).isFalse();
    domains.scalerKeys.add("BCEID\\SC/1234");
    assertThat(authority.isShown(Button.CANCEL, doc("OCM", "ISS", true), c)).isTrue();
  }

  @Test
  void approverNeedsRoleAndMarkDistrict() {
    Caller c = new Caller("IDIR\\APP", Set.of("HBS_INV_CORR_APP", "HBS_MOF_USER"));
    domains.markDistricts.put("ABC123", 3L);
    assertThat(authority.isShown(Button.APPROVE_CHANGE, doc("CIP", "AWP", true), c)).isFalse();
    domains.districts.put("IDIR\\APP", Set.of(3L));
    assertThat(authority.isShown(Button.APPROVE_CHANGE, doc("CIP", "AWP", true), c)).isTrue();
    assertThatThrownBy(() -> authority.require(Button.APPROVE_CHANGE, doc("CIP", "ISS", true), c))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void editableIncompleteForDataEntryHeldOnlyWithAuthority() {
    Caller de = new Caller("IDIR\\DE", Set.of("HBS_DTL_DATA_ENT"));
    assertThat(authority.isEditable(doc("OIP", "INC", true), de)).isTrue();
    assertThat(authority.isEditable(doc("OIP", "HLD", true), de)).isFalse();
    Caller sc = new Caller("BCEID\\SC", Set.of("HBS_CLI_SCALER"));
    domains.scalerKeys.add("BCEID\\SC/1234");
    assertThat(authority.isEditable(doc("OIP", "HLD", true), sc)).isTrue();
    assertThat(authority.isEditable(doc("OIP", "RDY", true), sc)).isFalse();
  }
}
