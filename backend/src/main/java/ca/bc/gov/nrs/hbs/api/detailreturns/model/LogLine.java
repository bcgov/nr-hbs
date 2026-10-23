package ca.bc.gov.nrs.hbs.api.detailreturns.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One log / piece line of a tally (LOG_TALLY_DETAIL, SAMPLE_TALLY_DETAIL or
 * SFP_LOG_DETAIL) with its defects (LOG_TALLY_DETAIL_DEFECT /
 * SAMPLE_TALLY_DETAIL_DEFECT). {@code logdtl_id} is null until the line is
 * first written.
 */
public final class LogLine {

  private final Row row;
  private final List<Row> defects = new ArrayList<>();

  public LogLine(Row row) {
    this.row = row;
  }

  public Row row() {
    return row;
  }

  public List<Row> defects() {
    return defects;
  }

  public Long id() {
    return row.lng("logdtl_id");
  }

  /** Copy for a new version (legacy clone in WorkflowDocumentFactory.newVersion): ids are reassigned on write. */
  public LogLine copyForNewVersion() {
    LogLine copy = new LogLine(row.copy().set("logdtl_id", null));
    defects.forEach(d -> copy.defects.add(d.copy().set("log_defect_id", null).set("logdtl_id", null)));
    return copy;
  }
}
