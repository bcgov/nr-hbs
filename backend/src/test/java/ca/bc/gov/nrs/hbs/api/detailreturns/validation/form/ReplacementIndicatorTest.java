package ca.bc.gov.nrs.hbs.api.detailreturns.validation.form;

import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.FormFixtures.messages;
import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.DetailFormValidator.Context;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** P650Form {@code replacementInd} ({@code HBSValidator.validateReplacementIndicator}). */
class ReplacementIndicatorTest {

  private final DetailFormValidator validator = new DetailFormValidator(new PermissiveFormLookups());

  private List<String> save(Object indicator, Context ctx) {
    return messages(validator.validate(DocType.SAMPLE_LOG_TALLY, false,
        new java.util.HashMap<>(Map.of("checkReplacesOriginalInd", indicator)), List.of(), ctx));
  }

  @ParameterizedTest
  @CsvSource({
      // document state, previous replaces original, previous version state, refused
      "CIP, true, ISS, true",
      "RNP, true, HLD, true",
      "OIP, true, ISS, false",
      "CIP, true, RDY, false",
      "CIP, true, ERR, false",
      "CIP, false, ISS, false",
  })
  void untickingIsRefusedWhenThePreviousVersionReplacedTheOriginal(String state, boolean previous,
      String previousState, boolean refused) {
    assertThat(save("N", new Context(state, previous, previousState)))
        .isEqualTo(refused ? List.of("Replacement indicator cannot be unchecked.") : List.of());
  }

  @Test
  void tickedNoPreviousVersionOrAdd_pass() {
    assertThat(save("Y", new Context("CIP", true, "ISS"))).isEmpty();
    assertThat(save(false, new Context("CIP", null, null))).isEmpty();
    assertThat(save(false, Context.NONE)).isEmpty();
    assertThat(messages(validator.validate(DocType.SAMPLE_LOG_TALLY, true, FormFixtures.sampleTally(), List.of(),
        new Context("CIP", true, "ISS")))).isEmpty();
  }

  @Test
  void otherDocumentTypes_notChecked() {
    assertThat(messages(validator.validate(DocType.WEIGH_SLIP, false,
        new java.util.HashMap<>(Map.of("checkReplacesOriginalInd", "N")), List.of(), new Context("CIP", true, "ISS"))))
        .isEmpty();
  }
}
