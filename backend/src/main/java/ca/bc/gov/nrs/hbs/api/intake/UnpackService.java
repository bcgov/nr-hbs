package ca.bc.gov.nrs.hbs.api.intake;

import ca.bc.gov.nrs.hbs.api.submission.SubmissionStorage;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unpacks one transmission in one database transaction (see
 * {@link TransmissionUnpacker}). Moving the file to the archive area happens
 * in {@link UnpackJob} after the commit, as legacy B1012 moved it after
 * {@code process} returned.
 */
@Service
public class UnpackService {

  private final TableApi tableApi;
  private final UnpackRepository repository;
  private final SubmissionStorage storage;
  private final Audit audit;

  public UnpackService(TableApi tableApi, UnpackRepository repository, SubmissionStorage storage, Audit audit) {
    this.tableApi = tableApi;
    this.repository = repository;
    this.storage = storage;
    this.audit = audit;
  }

  @Transactional
  public TransmissionUnpacker.Outcome unpack(UnpackRepository.Pending pending, SubmissionStorage.Key key) {
    byte[] xml;
    Boolean vendor;
    try (InputStream in = storage.open(SubmissionStorage.Area.INPUT, key.clientNumber(), key.transmissionId())) {
      xml = in.readAllBytes();
      vendor = storage.submitterIsSoftwareVendor(key.clientNumber(), key.transmissionId());
    } catch (IOException ex) {
      throw new UncheckedIOException("Cannot read " + pending.recdDirectory(), ex);
    }
    // No sidecar: an upload from before the sidecar existed. Legacy skipped FXA04 when the user
    // could not be found ("user was deleted some time between p505 and b1012").
    TransmissionUnpacker.Submitter submitter =
        new TransmissionUnpacker.Submitter(true, Boolean.TRUE.equals(vendor));
    UnpackContext ctx = new UnpackContext(tableApi, repository, audit.batchUser(), audit.now());
    return new TransmissionUnpacker(ctx).process(pending.transmissionId(), xml, submitter);
  }
}
