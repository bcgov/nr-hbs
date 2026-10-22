package ca.bc.gov.nrs.hbs.api.profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.profiles.ProfileReads.Next;
import ca.bc.gov.nrs.hbs.api.profiles.ProfileReads.Range;
import ca.bc.gov.nrs.hbs.api.workflow.StaleUpdateException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** P342–P344 mark holder and P372/P373 statement delivery profile workflows. */
class MarkHolderAndDeliveryProfileTest extends ProfileFixtures {

  private MarkHolderProfileCreate mhpCreate;
  private MarkHolderProfileUpdate mhpUpdate;
  private MarkHolderProfileDelete mhpDelete;
  private DeliveryProfileCreate cdpCreate;
  private DeliveryProfileUpdate cdpUpdate;

  @BeforeEach
  void setUp() {
    mhpCreate = new MarkHolderProfileCreate(api, reads, audit);
    mhpUpdate = new MarkHolderProfileUpdate(api, reads, audit);
    mhpDelete = new MarkHolderProfileDelete(api, reads, audit);
    cdpCreate = new DeliveryProfileCreate(api, reads, audit);
    cdpUpdate = new DeliveryProfileUpdate(api, reads, audit);
    reads.clientLocations.add("00012345|00");
  }

  // ------------------------------------------------------------ P343

  private Map<String, Object> mhp(String effective, String expiry) {
    return body("clientNumber", "12345", "clientLocnCode", "0", "returnType", "W", "frequency", "MTH",
        "effectiveDate", effective, "expiryDate", expiry);
  }

  @Test
  void mhpAdd_happyPath() {
    Map<String, Object> out = mhpCreate.execute(mhp("2026-11-01", "2027-10-31"));
    assertThat(api.names()).containsExactly("HBS_CREATE_HBS_MARK_PROFILE");
    var call = api.calls.get(0);
    assertThat(call.arg("i_client_number")).isEqualTo("00012345");
    assertThat(call.arg("i_forced_summarization_date")).isNull();
    assertThat(call.arg("i_hbs_frequency_type_code")).isEqualTo("MTH");
    assertThat(out).containsEntry("hbsMarkHolderProfileId", 1001L);
  }

  @Test
  void mhpAdd_dateRules() {
    assertThat(messages(() -> mhpCreate.execute(mhp("2026-11-02", "2027-10-30"))))
        .as("the expiry checks only run once the earlier ones passed (legacy errors.empty() gate)")
        .containsExactly("Effective date must be the first day of the month");
    // the 1st of this month is past; and an expiry today at 00:00 is already "before today"
    assertThat(messages(() -> mhpCreate.execute(mhp("2026-10-01", "2026-10-31"))))
        .containsExactly("Effective date must be a future date");
    assertThat(messages(() -> mhpCreate.execute(mhp("2026-12-01", "2026-11-30"))))
        .containsExactly("Expiry date cannot be before effective date.");
    assertThat(messages(() -> mhpCreate.execute(body("clientNumber", "1", "clientLocnCode", "00",
        "effectiveDate", "x", "expiryDate", "2027-10-31"))))
        .containsExactly("Client Number and Location is invalid.", "A return type is required.",
            "Invalid Effective Date", "Summarization Frequency is required");
  }

  @Test
  void mhpAdd_expiryBeforeToday() {
    audit.now = LocalDateTime.of(2026, 10, 31, 0, 0, 1);
    // 2026-10-31 00:00 is a second in the past
    assertThat(messages(() -> mhpCreate.execute(mhp("2026-11-01", "2026-10-31"))))
        .containsExactly("Expiry date cannot occur before today");
  }

  @Test
  void mhpAdd_overlaps() {
    reads.markHolderRanges.add(new Range(LocalDateTime.parse("2026-06-01T00:00"), LocalDateTime.parse("2026-11-01T00:00")));
    assertThat(messages(() -> mhpCreate.execute(mhp("2026-11-01", "2027-10-31"))))
        .containsExactly("Effective date cannot occur in an existing mark holder profile range.");
    reads.markHolderRanges.clear();
    reads.nextMarkHolder = LocalDateTime.parse("2027-10-31T00:00");
    assertThat(messages(() -> mhpCreate.execute(mhp("2026-11-01", "2027-10-31"))))
        .containsExactly("Date range cannot overlap an existing valid range");
  }

  @Test
  void sfpReturnTypeSharesThePieceScaleProfiles() {
    assertThat(MarkHolderProfileCreate.returnTypeForSummarization("F")).isEqualTo("P");
    assertThat(MarkHolderProfileCreate.returnTypeForSummarization("W")).isEqualTo("W");
  }

  // ------------------------------------------------------------ P342 / P344

  private void storedMhp(String effective) {
    Map<String, Object> row = new HashMap<>();
    row.put("mhprof_id", BigDecimal.valueOf(9));
    row.put("client_number", "00012345");
    row.put("client_locn_code", "00");
    row.put("hbs_return_type_code", "W");
    row.put("hbs_frequency_type_code", "MTH");
    row.put("forced_summarization_date", ts("2026-12-15T13:00:00"));
    row.put("effective_date", day(effective));
    row.put("expiry_date", day("2027-12-31"));
    row.put("entry_userid", "IDIR\\FIRST");
    row.put("entry_timestamp", ts("2026-09-01T07:00:00"));
    row.put("update_userid", "IDIR\\FIRST");
    row.put("update_timestamp", ts("2026-09-01T07:00:00"));
    reads.markHolders.put(9L, row);
  }

  @Test
  void mhpUpdate_futureProfileExpiryChanges() {
    storedMhp("2026-11-01");
    mhpUpdate.execute(body("mhprofId", "9", "expiryDate", "2027-06-30", "updateTimestamp", "2026-09-01T07:00:00"));
    var call = api.callsTo("HBS_STORE_HBS_MARK_PROFILE").get(0);
    assertThat(call.arg("i_expiry_date")).isEqualTo(day("2027-06-30"));
    assertThat(call.arg("i_effective_date")).isEqualTo(day("2026-11-01"));
    assertThat(call.arg("i_forced_summarization_date")).as("legacy P342 cleared it").isNull();
    assertThat(call.arg("i_entry_timestamp")).isEqualTo(ts("2026-09-01T07:00:00"));
  }

  @Test
  void mhpUpdateAndDelete_onlyWhileEffectiveIsAfterToday() {
    storedMhp("2026-10-21"); // today
    assertThat(messages(() -> mhpUpdate.execute(body("mhprofId", "9", "expiryDate", "2027-06-30"))))
        .containsExactly("Effective date must be a future date to allow updating of the expiry date");
    assertThat(messages(() -> mhpDelete.execute(body("mhprofId", "9"))))
        .containsExactly("Effective date must be a future date to allow updating of the expiry date");
    storedMhp("2026-10-22"); // tomorrow
    mhpDelete.execute(body("mhprofId", "9", "updateTimestamp", "2026-09-01T07:00:00"));
    assertThat(api.names()).containsExactly("HBS_REMOVE_HBS_MARK_PROFILE");
  }

  @Test
  void mhpUpdate_rules() {
    storedMhp("2026-11-01");
    assertThat(messages(() -> mhpUpdate.execute(body("mhprofId", "9", "expiryDate", "2027-06-29"))))
        .containsExactly("Expiry date must be the last day of the month");
    reads.nextMarkHolder = LocalDateTime.parse("2027-06-30T00:00");
    assertThat(messages(() -> mhpUpdate.execute(body("mhprofId", "9", "expiryDate", "2027-06-30"))))
        .containsExactly("Date range cannot overlap an existing valid range");
    assertThatThrownBy(() -> mhpUpdate.execute(body("mhprofId", "9", "expiryDate", "2027-05-31",
        "updateTimestamp", "2026-09-01"))).isInstanceOf(StaleUpdateException.class);
  }

  // ------------------------------------------------------------ P373 / P372

  private Map<String, Object> cdp(String method, String effective, String expiry) {
    return body("clientNumber", "00012345", "clientLocnCode", "00", "deliveryMethod", method,
        "effectiveDate", effective, "expiryDate", expiry);
  }

  @Test
  void cdpAdd_printNeedsNoReceivers() {
    Map<String, Object> out = cdpCreate.execute(cdp("P", "2026-10-22", "2027-10-31"));
    assertThat(api.names()).containsExactly("HBS_CREATE_HBS_CLI_DEL_PROFILE");
    assertThat(api.calls.get(0).arg("i_hbs_delivery_method_code")).isEqualTo("P");
    assertThat(out).containsEntry("hbsCliDelProfileId", 1001L);
  }

  @Test
  void cdpAdd_electronicDeliveryNeedsAnActiveDocumentReceiver() {
    assertThat(messages(() -> cdpCreate.execute(cdp("E", "2026-10-22", "2027-10-31"))))
        .containsExactly("There are no users with the document receiver role for this client location.");
    reads.receivers = 2;
    assertThat(messages(() -> cdpCreate.execute(cdp("F", "2026-10-22", "2027-10-31"))))
        .containsExactly("There are no active users with the document receiver role for this client location.");
    reads.activeReceivers = 1;
    cdpCreate.execute(cdp("E", "2026-10-22", "2027-10-31"));
    assertThat(api.calls).hasSize(1);
  }

  @Test
  void cdpAdd_dateRules() {
    assertThat(messages(() -> cdpCreate.execute(cdp("P", "2026-10-21", "2027-10-31"))))
        .containsExactly("Effective date must be a future date");
    assertThat(messages(() -> cdpCreate.execute(cdp("P", "2026-12-01", "2026-11-30"))))
        .containsExactly("Expiry date cannot be before effective date.");
    reads.deliveryRanges.add(new Range(LocalDateTime.parse("2026-01-01T00:00"), LocalDateTime.parse("2026-12-31T00:00")));
    assertThat(messages(() -> cdpCreate.execute(cdp("P", "2026-12-31", "2027-10-31"))))
        .containsExactly("Effective date cannot occur in an existing delivery profile range.");
    reads.deliveryRanges.clear();
    reads.nextDelivery = new Next(3L, LocalDateTime.parse("2027-01-01T00:00"));
    assertThat(messages(() -> cdpCreate.execute(cdp("P", "2026-11-01", "2027-01-01"))))
        .containsExactly("Date range cannot overlap an existing valid range");
  }

  private void storedCdp() {
    Map<String, Object> row = new HashMap<>();
    row.put("dlvprof_id", BigDecimal.valueOf(4));
    row.put("client_number", "00012345");
    row.put("client_locn_code", "00");
    row.put("hbs_delivery_method_code", "P");
    row.put("effective_date", day("2026-01-01"));
    row.put("expiry_date", day("2026-12-31"));
    row.put("entry_userid", "IDIR\\FIRST");
    row.put("entry_timestamp", ts("2025-12-01T10:00:00"));
    row.put("update_userid", "IDIR\\FIRST");
    row.put("update_timestamp", ts("2025-12-01T10:00:00"));
    reads.deliveries.put(4L, row);
  }

  @Test
  void cdpUpdate_nextProfileCheckSkipsTheProfileItself() {
    storedCdp();
    reads.nextDelivery = new Next(4L, LocalDateTime.parse("2026-01-01T00:00"));
    cdpUpdate.execute(body("dlvprofId", "4", "clientNumber", "00000001", "deliveryMethod", "P",
        "effectiveDate", "2026-01-01", "expiryDate", "2027-12-31", "updateTimestamp", "2025-12-01T10:00:00"));
    var call = api.callsTo("HBS_STORE_HBS_CLI_DEL_PROFILE").get(0);
    assertThat(call.arg("i_client_number")).isEqualTo("00012345");
    assertThat(call.arg("i_expiry_date")).isEqualTo(day("2027-12-31"));
    assertThat(call.arg("i_entry_userid")).isEqualTo("IDIR\\FIRST");
    assertThat(call.arg("i_update_userid")).isEqualTo("IDIR\\TESTER");

    api.calls.clear();
    reads.nextDelivery = new Next(8L, LocalDateTime.parse("2027-01-01T00:00"));
    assertThat(messages(() -> cdpUpdate.execute(body("dlvprofId", "4", "deliveryMethod", "P",
        "effectiveDate", "2026-01-01", "expiryDate", "2027-01-01"))))
        .containsExactly("Date range cannot overlap an existing valid range");
  }

  @Test
  void cdpUpdate_rules() {
    storedCdp();
    assertThat(messages(() -> cdpUpdate.execute(body("dlvprofId", "4", "deliveryMethod", "E",
        "effectiveDate", "1899-01-01", "expiryDate", "bad"))))
        .containsExactly("There are no users with the document receiver role for this client location.",
            "Invalid Expiry Date", "Year cannot be prior to 1900");
    assertThatThrownBy(() -> cdpUpdate.execute(body("dlvprofId", "4", "deliveryMethod", "P",
        "effectiveDate", "2026-01-01", "expiryDate", "2026-12-31", "updateTimestamp", "2025-12-02T10:00:00")))
        .isInstanceOf(StaleUpdateException.class);
  }
}
