package ca.bc.gov.nrs.hbs.api.workflow;

import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Who and when for ENTRY_/UPDATE_ audit columns. In a request it is the
 * caller's legacy user id ({@code IDIR\NAME}); in a batch job (no JWT) it is
 * the configured batch user, as the legacy {@code AbstractBatch.BATCH_USER}
 * ({@code batch.user.id}) was.
 */
@Component
public class Audit {

  public static final ZoneId ZONE = ZoneId.of("America/Vancouver");

  private final String batchUser;

  public Audit(@Value("${hbs.batch.audit-user:HBSBATCH}") String batchUser) {
    this.batchUser = batchUser;
  }

  public String user() {
    String u = RequestUtil.getCurrentAuditUserId();
    return u == null || u.isBlank() ? batchUser : u;
  }

  public String batchUser() {
    return batchUser;
  }

  /** Now in Pacific time, to the second (Oracle DATE), as the legacy app stamped it. */
  public Timestamp now() {
    return Timestamp.valueOf(LocalDateTime.now(ZONE).withNano(0));
  }
}
