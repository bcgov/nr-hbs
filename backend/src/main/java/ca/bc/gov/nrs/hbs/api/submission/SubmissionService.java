package ca.bc.gov.nrs.hbs.api.submission;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.security.HbsRoles;
import ca.bc.gov.nrs.hbs.api.service.v1.VirusScanner;
import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;

/**
 * P505 "Submit File of Detail Returns" — same contract as the legacy
 * {@code SubmitDocumentAction}:
 * <ol>
 *   <li>virus scan (new — the legacy app didn't scan uploads),</li>
 *   <li>schema-validate against HBS_Schema_V6_1b,</li>
 *   <li>create the DTL_SCL_DOC_TRANSMISSION row via
 *       {@code HBS_CREATE_DTL_SCL_DOC_TRANS} in step RCD / status PRC,</li>
 *   <li>store the file as {@code <client>/<transmissionId>.xml} for the
 *       intake job (legacy share {@code XMLinput}),</li>
 *   <li>mark receiving complete — status CSA via
 *       {@code HBS_STORE_DTL_SCL_DOC_TRANS} (legacy {@code receivingComplete()}).</li>
 * </ol>
 * Unpacking is this app's {@code intake.UnpackJob} (B1012 port); edits,
 * summarization and billing stay in the legacy batch, which picks the
 * transmission up at UNPACKING/SUCCESS.
 */
@Service
@Slf4j
public class SubmissionService {

  static final String STEP_RECEIVING = "RCD";
  static final String STATUS_PROCESSING = "PRC";
  static final String STATUS_SUCCESS = "CSA";

  public record SubmissionResult(long transmissionId, String fileName, List<String> errors) {}

  private final JdbcTemplate jdbc;
  private final VirusScanner virusScanner;
  private final ScaleDataXmlValidator validator;
  private final SubmissionStorage storage;

  public SubmissionService(JdbcTemplate jdbc, VirusScanner virusScanner,
      ScaleDataXmlValidator validator, SubmissionStorage storage) {
    this.jdbc = jdbc;
    this.virusScanner = virusScanner;
    this.validator = validator;
    this.storage = storage;
  }

  public SubmissionResult submit(MultipartFile file) throws IOException {
    Capability.XML_SUBMIT.require();
    String fileName = sanitizeName(file.getOriginalFilename());
    byte[] bytes = file.getBytes();
    virusScanner.scanOrThrow(bytes, fileName);

    List<String> errors = validator.validate(bytes);
    if (!errors.isEmpty()) {
      log.info("Scale data file {} failed schema validation ({} errors)", fileName, errors.size());
      return new SubmissionResult(0, fileName, errors);
    }

    String client = RequestUtil.isMinistryUser() ? null : RequestUtil.getCurrentClientNumber();
    long transmissionId = nextTransmissionId();
    receive(new Incoming(transmissionId, bytes, fileName, client, RequestUtil.getCurrentAuditUserId(),
        SubmissionStorage.Channel.WEB, RequestUtil.getCurrentRoles().contains(HbsRoles.SPC_SFTWR_VNDR)));
    return new SubmissionResult(transmissionId, fileName, List.of());
  }

  /**
   * A file that passed the virus scan and the schema check, ready to record.
   *
   * @param recdClientNumber the client it was received for (null for a ministry web upload)
   * @param softwareVendor   for the unpack's edit FXA04 (web uploads only)
   */
  public record Incoming(long transmissionId, byte[] bytes, String fileName, String recdClientNumber,
      String auditUser, SubmissionStorage.Channel channel, boolean softwareVendor) {}

  /** A new DTL_SCL_DOC_TRANSMISSION id (the caller may need it before {@link #receive}). */
  public long nextTransmissionId() {
    Long id = jdbc.queryForObject("SELECT DTL_SCL_DOC_TRANSMISSION_SEQ.NEXTVAL FROM DUAL", Long.class);
    if (id == null || id <= 0) {
      throw new IllegalStateException("DTL_SCL_DOC_TRANSMISSION_SEQ returned no id");
    }
    return id;
  }

  /**
   * Records a checked file, the same way for the P505 page and the scale data
   * API: stores it for the unpack ({@code input/<client>/<id>.xml} plus the
   * submitter sidecar), creates the transmission at RCD/PRC and marks it
   * RCD/CSA ({@code receivingComplete()}). The caller has already scanned and
   * schema-checked {@code bytes}.
   *
   * @return the storage key (the transmission's RECD_DIRECTORY)
   */
  public String receive(Incoming in) {
    long transmissionId = in.transmissionId();
    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
    String key = storage.store(SubmissionStorage.Area.INPUT, in.recdClientNumber(), transmissionId,
        new ByteArrayInputStream(in.bytes()));
    storage.storeSubmitter(in.recdClientNumber(), transmissionId, in.softwareVendor(), in.channel());

    callTransmissionProc("HBS_CREATE_DTL_SCL_DOC_TRANS", transmissionId, STATUS_PROCESSING,
        in.fileName(), in.auditUser(), now, key, in.recdClientNumber(), null, null);
    // receivingComplete(): RCD/PRC → RCD/CSA. The app role has no UPDATE
    // grant, so this goes through the store proc (as the legacy
    // TransmissionEntityBean.ejbStore did).
    callTransmissionProc("HBS_STORE_DTL_SCL_DOC_TRANS", transmissionId, STATUS_SUCCESS,
        in.fileName(), in.auditUser(), now, key, in.recdClientNumber(), in.auditUser(),
        Timestamp.valueOf(LocalDateTime.now()));

    log.info("Received scale data file {} as transmission {} ({}, {})", in.fileName(), transmissionId, key,
        in.channel());
    return key;
  }

  /**
   * HBS_CREATE_DTL_SCL_DOC_TRANS and HBS_STORE_DTL_SCL_DOC_TRANS share one
   * 24-argument signature; positional args in their declared order.
   */
  private void callTransmissionProc(String proc, long transmissionId, String status, String fileName,
      String user, Timestamp entryTs, String key, String client, String updateUser, Timestamp updateTs) {
    jdbc.update(con -> {
      var cs = con.prepareCall("{call " + proc + "(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)}");
      int i = 1;
      cs.setLong(i++, transmissionId);                 // i_transmission_id
      cs.setString(i++, STEP_RECEIVING);               // i_hbs_xml_trans_step_code
      cs.setString(i++, status);                       // i_hbs_processing_status_code
      cs.setString(i++, "N");                          // i_accepted_ind
      cs.setNull(i++, Types.VARCHAR);                  // i_client_number (document client — set by unpack)
      cs.setString(i++, user);                         // i_input_userid
      cs.setNull(i++, Types.VARCHAR);                  // i_user_id
      cs.setNull(i++, Types.VARCHAR);                  // i_client_locn_code
      cs.setString(i++, fileName);                     // i_file_name
      cs.setTimestamp(i++, entryTs);                   // i_file_datetime
      cs.setLong(i++, 0);                              // i_batch_count
      cs.setLong(i++, 0);                              // i_record_count
      cs.setNull(i++, Types.VARCHAR);                  // i_test_file_ind
      cs.setString(i++, user);                         // i_entry_userid
      cs.setTimestamp(i++, entryTs);                   // i_entry_timestamp
      cs.setString(i++, key);                          // i_recd_directory
      if (client == null) cs.setNull(i++, Types.VARCHAR); else cs.setString(i++, client); // i_recd_client_number
      if (updateUser == null) cs.setNull(i++, Types.VARCHAR); else cs.setString(i++, updateUser); // i_update_userid
      if (updateTs == null) cs.setNull(i++, Types.DATE); else cs.setTimestamp(i++, updateTs);      // i_update_timestamp
      cs.setNull(i++, Types.VARCHAR);                  // i_hbs_xml_purge_status_code
      cs.setNull(i++, Types.DATE);                     // i_hbs_xml_purge_timestamp
      cs.setNull(i++, Types.VARCHAR);                  // i_software_product
      cs.setNull(i++, Types.VARCHAR);                  // i_software_version
      cs.setNull(i, Types.VARCHAR);                    // i_software_revision
      return cs;
    });
  }

  /**
   * A FILE_NAME (VARCHAR2(100 BYTE)) from a client-supplied name: printable
   * ASCII only (so 100 characters fit 100 bytes), no path separators.
   */
  public static String sanitizeName(String original) {
    String name = original == null ? "" : original.replaceAll("[^\\x20-\\x7E]|[\\\\/]", "_").trim();
    if (name.isEmpty()) {
      name = "upload.xml";
    }
    return name.length() > 100 ? name.substring(name.length() - 100) : name;
  }
}
