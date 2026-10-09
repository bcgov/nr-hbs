package ca.bc.gov.nrs.hbs.api.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HbsRolesTest {

  @Test
  void canonicalRoleFor_matchesExactAndClientSuffixed() {
    assertThat(HbsRoles.canonicalRoleFor("HBS_MOF_USER")).isEqualTo(HbsRoles.MOF_USER);
    assertThat(HbsRoles.canonicalRoleFor("HBS_CLI_SCALER_FOREST_CLIENT-00012345")).isEqualTo(HbsRoles.CLI_SCALER);
    assertThat(HbsRoles.canonicalRoleFor("HBS_BILL_ADMIN")).isEqualTo(HbsRoles.BILL_ADMIN);
  }

  @Test
  void canonicalRoleFor_prefersLongestRole() {
    // HBS_CLI_SITE_ADMIN must not resolve to a shorter role sharing its prefix.
    assertThat(HbsRoles.canonicalRoleFor("HBS_CLI_SITE_ADMIN_FOREST_CLIENT-00012345")).isEqualTo(HbsRoles.CLI_SITE_ADMIN);
    assertThat(HbsRoles.canonicalRoleFor("HBS_SUMM_DATA_CORR")).isEqualTo(HbsRoles.SUMM_DATA_CORR);
    assertThat(HbsRoles.canonicalRoleFor("HBS_MOF_SCALER")).isEqualTo(HbsRoles.MOF_SCALER);
  }

  @Test
  void canonicalRoleFor_rejectsUnknownGroups() {
    assertThat(HbsRoles.canonicalRoleFor("FSPTS_ADMINISTRATOR")).isNull();
    assertThat(HbsRoles.canonicalRoleFor("HBS")).isNull();
    assertThat(HbsRoles.canonicalRoleFor(null)).isNull();
    // FAM bookkeeping roles never grant anything.
    assertThat(HbsRoles.canonicalRoleFor("FAM:EXPIRES:HBS_MOF_USER")).isNull();
  }

  @Test
  void everyRoleIsClassifiedExactlyOnce() {
    for (String role : HbsRoles.ALL) {
      int buckets = (HbsRoles.MINISTRY.contains(role) ? 1 : 0)
          + (HbsRoles.CLIENT.contains(role) ? 1 : 0)
          + (HbsRoles.SPECIAL.contains(role) ? 1 : 0);
      assertThat(buckets).as(role).isEqualTo(1);
    }
    assertThat(HbsRoles.ALL).hasSize(28);
  }

  @Test
  void legacyUserType_ministryWinsWhenMixed() {
    assertThat(HbsRoles.legacyUserType(List.of(HbsRoles.CLI_USER, HbsRoles.MOF_USER))).isEqualTo("MOF");
    assertThat(HbsRoles.legacyUserType(List.of(HbsRoles.CLI_USER))).isEqualTo("CLI");
    assertThat(HbsRoles.legacyUserType(List.of(HbsRoles.SPC_SUBM_AGNT))).isEqualTo("SPC");
    assertThat(HbsRoles.legacyUserType(List.of())).isEqualTo("PUB");
  }
}
