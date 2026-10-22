package ca.bc.gov.nrs.hbs.api.detailreturns.store;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import java.util.List;
import java.util.Optional;

/**
 * Raw row reads the detail workflows need, kept behind an interface so the
 * workflows can be unit-tested without Oracle ({@code JdbcDetailRows} in
 * production, an in-memory map in tests). Every read is by key with bound
 * parameters; the table names come from {@link DocType}, never from input.
 */
public interface DetailRows {

  /** DETAIL_SCALE_DOCUMENT row. */
  Optional<Row> documentRow(String ddn);

  /** Every version row of the document, by version number. */
  List<Row> versionRows(DocType type, String ddn);

  /** Log / piece lines of one version (log number order). */
  List<Row> lineRows(DocType type, String ddn, long version);

  /** Defects of one log line (defect number order). */
  List<Row> defectRows(DocType type, long logdtlId);

  /** ERROR_IDs of the version-level edit errors. */
  List<Long> versionErrorIds(DocType type, String ddn, long version);

  /** ERROR_IDs of the edit errors of one log line. */
  List<Long> lineErrorIds(DocType type, long logdtlId);
}
