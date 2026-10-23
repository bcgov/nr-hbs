package ca.bc.gov.nrs.hbs.api.rating;

import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** "Today" in Pacific time for the date rules (7-year limit, process dates from today). Fixed in tests. */
@Component
public class RatingClock {

  private final Clock clock;

  public RatingClock() {
    this(Clock.system(Audit.ZONE));
  }

  RatingClock(Clock clock) {
    this.clock = clock;
  }

  public LocalDate today() {
    return LocalDate.now(clock);
  }
}
