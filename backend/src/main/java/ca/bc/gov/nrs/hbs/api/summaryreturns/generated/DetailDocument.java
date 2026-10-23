package ca.bc.gov.nrs.hbs.api.summaryreturns.generated;

import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * One detail scale document with one of its versions, as the legacy
 * {@code findDetailScaleDocumentAndVersionByPrimaryKey} assembled it: the
 * {@code DETAIL_SCALE_DOCUMENT} row, the version row of its family's table and the version's
 * transactions ({@code getTransactions()}). The state lives in {@link #state} until
 * {@link DetailDocumentWriter#update} copies it back into the rows.
 */
final class DetailDocument {

  final DetailFamily family;
  final Row document;
  final Row version;
  final List<Row> txns;
  final DetailState state;

  DetailDocument(DetailFamily family, Row document, Row version, List<Row> txns) {
    this.family = family;
    this.document = document;
    this.version = version;
    this.txns = txns;
    this.state = new DetailState(document.str("scale_return_state_code"),
        version.str("scl_rtn_version_state_code"), version.flag("active_version_ind"));
  }

  String ddn() {
    return document.str("detail_document_number");
  }

  long versionNo() {
    return version.lng("version");
  }

  /** {@code DetailScaleDocumentVersion.getLatestTxn()}: the txn with the highest event sequence. */
  Optional<Row> latestTxn() {
    return txns.stream().max(Comparator.comparing((Row t) -> t.lng("event_sequence")));
  }

  @Override
  public String toString() {
    return ddn() + "/" + versionNo() + " " + state;
  }
}
