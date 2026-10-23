package ca.bc.gov.nrs.hbs.api.summaryreturns;

import java.util.ArrayList;
import java.util.List;

/**
 * One summary scale return loaded for a workflow: the document
 * ({@code HBS_SCALE_RETURN}), one of its versions and that version's children.
 * The legacy equivalent is {@code SummaryScaleReturn} assembled with one
 * {@code Summary*ScaleReturnVersion} by {@code SummaryScaleReturnFactory}.
 *
 * <p>Rows are mutable: the release edits move input fields into the business
 * columns, and the workflows change states, exactly as the legacy value objects
 * were changed before {@code updateScaleReturnVersion} saved them.
 */
public final class SummaryReturn {

  private final SummaryReturnType type;
  private final Row header;
  private final Row version;
  private final List<Row> segregations;
  private final List<Row> notations;
  private final List<Row> dailyLoads;
  private Row headerSaved;
  private Row versionSaved;

  public SummaryReturn(SummaryReturnType type, Row header, Row version,
      List<Row> segregations, List<Row> notations, List<Row> dailyLoads) {
    this.type = type;
    this.header = header;
    this.version = version;
    this.segregations = new ArrayList<>(segregations);
    this.notations = new ArrayList<>(notations);
    this.dailyLoads = new ArrayList<>(dailyLoads);
    markSaved();
  }

  /**
   * Remembers the current header and version as saved, as the legacy
   * {@code SummaryScaleReturn.clearEdits()} did after an audit was written;
   * {@link #versionEdits()} reports changes made since.
   */
  public void markSaved() {
    this.headerSaved = header.copy();
    this.versionSaved = version.copy();
  }

  /** The field edits since the return was loaded or last saved ({@code getScaleReturnEdits()}). */
  public List<AuditTrail.Detail> versionEdits() {
    return AuditTrail.versionEdits(type, headerSaved, header, versionSaved, version);
  }

  /** The version row as it was loaded (before this workflow's changes). */
  public Row versionAsLoaded() {
    return versionSaved;
  }

  public SummaryReturnType type() {
    return type;
  }

  /** {@code HBS_SCALE_RETURN} row. */
  public Row header() {
    return header;
  }

  /** {@code PIECE_SCALE_SUMMARY} / {@code WEIGHT_SCALE_SUMMARY} / {@code SAMPLE_SCALE_SUMMARY} row. */
  public Row version() {
    return version;
  }

  /** {@code PIECE_SCL_SEGREGATION} or {@code SAMPLE_SEGREGATION} rows (empty for weight). */
  public List<Row> segregations() {
    return segregations;
  }

  public List<Row> notations() {
    return notations;
  }

  /** {@code WEIGHT_SCL_DAILY_LOAD_SUMMARY} rows (empty for piece and sample). */
  public List<Row> dailyLoads() {
    return dailyLoads;
  }

  public long dcn() {
    return header.lng("document_control_number");
  }

  public long versionNo() {
    return version.lng("version");
  }

  public boolean generated() {
    return header.flag("generated_summary_ind");
  }

  public String docState() {
    return header.str("scale_return_state_code");
  }

  public String versionState() {
    return version.str("scl_rtn_version_state_code");
  }

  public boolean active() {
    return version.flag("active_version_ind");
  }

  public ScaleReturnState state() {
    return new ScaleReturnState(docState(), versionState(), active());
  }

  /** {@code SummaryScaleReturn.setState}: document and version state move together. */
  public void setState(ScaleReturnState state) {
    header.set("scale_return_state_code", state.doc());
    version.set("scl_rtn_version_state_code", state.version());
    version.set("active_version_ind", state.active() ? "Y" : "N");
  }

  /** {@code PIECE_SCALE_SUMMARY.PSSMRY_TYPE} (piece only). */
  public String pssmryType() {
    return type == SummaryReturnType.PIECE ? version.str("pssmry_type") : null;
  }

  /** BT15212: piece "Area Based Estimate" return. */
  public boolean areaBased() {
    return SummaryReturnRules.PSSMRY_AREA_TYPE.equals(pssmryType());
  }

  /** BT17364: area-based return with an Area Cut of exactly 0 goes to NTI instead of RDY. */
  public boolean areaBasedWithZeroAreaCut() {
    var areaCut = version.num("area_cut");
    return areaBased() && areaCut != null && areaCut.signum() == 0;
  }

  /** Volume estimate: a piece return whose type is neither SMRYDOC nor SFPSMRY ({@code P052.jsp} matchconstant). */
  public boolean volumeEstimate() {
    String t = pssmryType();
    return t != null && !SummaryReturnRules.PSSMRY_DEFAULT_TYPE.equals(t) && !SummaryReturnRules.PSSMRY_SFP_TYPE.equals(t);
  }

  /** {@code SummaryScaleReturn.isPieceSpecialForestProduct}. */
  public boolean specialForestProduct() {
    return SummaryReturnRules.PSSMRY_SFP_TYPE.equals(pssmryType());
  }

  @Override
  public String toString() {
    return type.code() + " " + dcn() + "/" + versionNo() + " " + state();
  }
}
