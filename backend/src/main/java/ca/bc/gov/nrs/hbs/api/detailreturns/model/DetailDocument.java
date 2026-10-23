package ca.bc.gov.nrs.hbs.api.detailreturns.model;

import ca.bc.gov.nrs.hbs.api.detailreturns.state.DocumentState;
import java.util.ArrayList;
import java.util.List;

/**
 * A detail scale document (DETAIL_SCALE_DOCUMENT row) with one of its versions
 * attached, as the legacy {@code DetailScaleDocument} carried it through the
 * workflow and state managers. {@code savedState} is the document state as
 * read, which the writer compares with the database before it stores the
 * document (legacy {@code DetailScaleDocumentEntityBean.setScaleReturnStateCode}
 * → {@code StaleStateException}).
 */
public final class DetailDocument {

  private final DocType type;
  private final Row row;
  private DocumentState state;
  private String savedState;
  private DetailVersion version;
  private final List<EditError> errors = new ArrayList<>();
  private boolean persisted;

  public DetailDocument(DocType type, Row row, DocumentState state, DetailVersion version, boolean persisted) {
    this.type = type;
    this.row = row;
    this.state = state;
    this.version = version;
    this.persisted = persisted;
    this.savedState = persisted ? state.value() : null;
    if (version != null) {
      state.setVersionState(version.state());
    }
  }

  public DocType type() {
    return type;
  }

  public Row row() {
    return row;
  }

  public String ddn() {
    return row.str("detail_document_number");
  }

  public DocumentState state() {
    return state;
  }

  public String savedState() {
    return savedState;
  }

  public DetailVersion version() {
    return version;
  }

  /** Attach another version (legacy {@code DetailScaleDocumentFactory.assembleDocument}). */
  public void setVersion(DetailVersion version) {
    this.version = version;
    state.setVersionState(version.state());
  }

  public List<EditError> errors() {
    return errors;
  }

  public boolean persisted() {
    return persisted;
  }

  /** Legacy {@code syncState}: the stored state is now the current one. */
  public void markPersisted() {
    persisted = true;
    savedState = state.value();
  }

  /** The state the database holds for this document (a copy made after a write). */
  public void syncSavedState(String saved) {
    this.savedState = saved;
  }

  public boolean isTally() {
    return type.isTally();
  }
}
