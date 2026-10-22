package ca.bc.gov.nrs.hbs.api.detailreturns.model;

import ca.bc.gov.nrs.hbs.api.detailreturns.state.VersionState;
import java.util.ArrayList;
import java.util.List;

/**
 * One version of a detail document: its row in the type's version table, its
 * {@link VersionState} (kept apart from the row so every transition goes through
 * the ported guards; the writer copies it into SCL_RTN_VERSION_STATE_CODE /
 * ACTIVE_VERSION_IND) and, for tallies, its log lines.
 *
 * <p>{@code loaded} is a snapshot of the row as read, used to audit the field
 * edits (legacy {@code recordFieldEdit}) and to detect a concurrent change.
 */
public final class DetailVersion {

  private final Row row;
  private VersionState state;
  private final List<LogLine> logs = new ArrayList<>();
  private Row loaded;
  private boolean persisted;

  public DetailVersion(Row row, VersionState state, boolean persisted) {
    this.row = row;
    this.state = state;
    this.persisted = persisted;
    this.loaded = persisted ? row.copy() : null;
  }

  public Row row() {
    return row;
  }

  public VersionState state() {
    return state;
  }

  public void setState(VersionState state) {
    this.state = state;
  }

  public List<LogLine> logs() {
    return logs;
  }

  /** Row as read from the database (null for a version not yet written). */
  public Row loaded() {
    return loaded;
  }

  public boolean persisted() {
    return persisted;
  }

  /** Called by the writer once the row is in the database. */
  public void markPersisted() {
    persisted = true;
    loaded = row.copy();
    state.resetOriginalValues();
  }

  public Long number() {
    return row.lng("version");
  }

  public String str(String column) {
    return row.str(column);
  }
}
