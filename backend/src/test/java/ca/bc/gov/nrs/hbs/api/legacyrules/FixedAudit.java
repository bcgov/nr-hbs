package ca.bc.gov.nrs.hbs.api.legacyrules;

import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/** Audit with a fixed caller and clock, for workflow unit tests. */
public class FixedAudit extends Audit {

  public static final String USER = "IDIR\\TESTER";

  private final LocalDateTime now;

  public FixedAudit(LocalDateTime now) {
    super("HBSBATCH");
    this.now = now;
  }

  @Override
  public String user() {
    return USER;
  }

  @Override
  public Timestamp now() {
    return Timestamp.valueOf(now);
  }
}
