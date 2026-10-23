package ca.bc.gov.nrs.hbs.api.profiles;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** P320Action pre-checks on the scale site profile search. */
class ScaleSiteProfileSearchGuardTest {

  private final Set<String> sites = Set.of("A12");
  private final Set<String> clients = Set.of("00001234", "00005678");
  private final Set<String> partners = Set.of("00001234/00", "00005678/01");
  private final Map<String, Long> counts = new HashMap<>(Map.of("00001234", 2L));

  private final ScaleSiteProfileSearchGuard guard = new ScaleSiteProfileSearchGuard(
      new ScaleSiteProfileSearchGuard.Lookups() {
        @Override
        public boolean scaleSiteExists(String s) {
          return sites.contains(s);
        }

        @Override
        public boolean clientExists(String c) {
          return clients.contains(c);
        }

        @Override
        public boolean tradingPartnerExists(String c, String l) {
          return partners.contains(c + "/" + l);
        }

        @Override
        public long profileCount(Map<String, String> criteria) {
          String c = criteria.getOrDefault("clientNumber", criteria.get("ownerClientNumber"));
          return counts.getOrDefault(c, 0L);
        }
      }, () -> { });

  @Test
  void byScaleSite() {
    assertThatCode(() -> guard.validate(Map.of("scaleSiteId", "a12"))).doesNotThrowAnyException();
    assertThatThrownBy(() -> guard.validate(Map.of("scaleSiteId", "ZZZ")))
        .hasMessage(ScaleSiteProfileSearchGuard.SCALE_SITE_NOT_FOUND);
  }

  @Test
  void byTradingPartner() {
    assertThatThrownBy(() -> guard.validate(Map.of("clientNumber", "00001234")))
        .hasMessage("Client number and location are required.");
    assertThatThrownBy(() -> guard.validate(Map.of("clientNumber", "00000001", "clientLocnCode", "00")))
        .hasMessage("Client does not exist.");
    assertThatThrownBy(() -> guard.validate(Map.of("clientNumber", "00001234", "clientLocnCode", "05")))
        .hasMessage("Client has not been setup as trading partner.");
    assertThatThrownBy(() -> guard.validate(Map.of("clientNumber", "00005678", "clientLocnCode", "01")))
        .hasMessage("This client has no current scale site profiles");
    assertThatCode(() -> guard.validate(Map.of("clientNumber", "00001234", "clientLocnCode", "00")))
        .doesNotThrowAnyException();
  }

  @Test
  void bySiteOwner() {
    assertThatThrownBy(() -> guard.validate(Map.of("ownerClientNumber", "00005678", "ownerClientLocnCode", "01")))
        .hasMessage("This client has no current scale site profiles");
    assertThatCode(() -> guard.validate(Map.of("ownerClientNumber", "00001234", "ownerClientLocnCode", "00")))
        .doesNotThrowAnyException();
  }
}
