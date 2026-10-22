package ca.bc.gov.nrs.hbs.api.intake;

import ca.bc.gov.nrs.hbs.api.intake.xml.Commons;
import ca.bc.gov.nrs.hbs.api.intake.xml.Document;
import ca.bc.gov.nrs.hbs.api.intake.xml.HashValues;
import ca.bc.gov.nrs.hbs.api.intake.xml.LedgerEntry;
import ca.bc.gov.nrs.hbs.api.intake.xml.LogTallyDocument;
import ca.bc.gov.nrs.hbs.api.intake.xml.Parts;
import ca.bc.gov.nrs.hbs.api.intake.xml.WeighSlipDocument;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;

/**
 * Builds the version rows B1012 created for each document and ledger entry
 * ({@code B1012.composeWS/WSB/WSR/WSO/LT/PPS/PSS/POT/SFP/ARL/DPL} plus
 * {@code WorkflowDocumentFactory.newBatch*}), as arguments of the
 * {@code HBS_CREATE_*} procs the legacy entity beans called.
 *
 * <p>Every new version starts as {@code WorkflowDocumentFactory} made it:
 * state ACC, not active, signature not (yet) valid, DTL_DOC_SEQ from
 * DETAIL_SCALE_DOCUMENT_SEQ, entry and update by the batch user now. The
 * signature itself is stored, not verified: legacy B1021 verifies it.
 */
final class VersionComposer {

  /** Version state of a new batch version ({@code VersionState.getInitialBatchState()}). */
  static final String STATE_ACCEPTED = "ACC";

  /** A child row (log detail, defect, SFP item) created after its version. */
  record Child(String proc, ProcArgs args) {}

  /**
   * A composed version: the create proc and its arguments (original version
   * indicator and batch-document id are set by the writer), the children, and
   * for log/sample tallies the follow-up store the legacy entity bean issued.
   */
  record ComposedVersion(String docTypeCode, String ddn, long version, String createProc, ProcArgs args,
      List<Child> children, String storeProc, Date scaleDate, Date arrivalDate, Date departureDate) {}

  private final UnpackContext ctx;

  VersionComposer(UnpackContext ctx) {
    this.ctx = ctx;
  }

  ComposedVersion compose(Document document) throws ParseException {
    return switch (document) {
      case WeighSlipDocument ws -> weighSlip(ws);
      case LogTallyDocument.PieceScale pps -> pieceScale(pps);
      case LogTallyDocument.OtherTally pot -> otherTally(pot);
      case LogTallyDocument.SampleScale pss -> sampleScale(pss);
      case LogTallyDocument.SpecialForestProduct sfp -> sfp(sfp);
    };
  }

  // ── common header fields (newBatch*) ──────────────────────────────────

  private ProcArgs header(String proc, String ddn, int dv, boolean nullUpdate) {
    ProcArgs a = ctx.nulls(proc);
    a.put("i_detail_document_number", ddn);
    a.put("i_version", dv);
    a.put("i_scl_rtn_version_state_code", STATE_ACCEPTED);
    a.put("i_active_version_ind", "N");
    a.put("i_valid_signature_ind", "N");
    a.put("i_dtl_doc_seq", ctx.nextId(UnpackContext.SEQ_DETAIL_SCALE_DOCUMENT));
    a.put("i_entry_userid", ctx.userId());
    a.put("i_entry_timestamp", ctx.now());
    // LOG_TALLY / SAMPLE_LOG_TALLY DAO inserts bound null update columns (BT 4654).
    a.put("i_update_userid", nullUpdate ? null : ctx.userId());
    a.put("i_update_timestamp", nullUpdate ? null : ctx.now());
    return a;
  }

  /** H33 hash total, crypt method, signing scaler and the signature fields ({@code setRSASignatureValues}). */
  private void signature(ProcArgs a, Parts.Digest digest, Parts.Signature sig, boolean upperSigner) {
    a.put("i_hash_total", digest.hashValue());
    a.put("i_hbs_crypt_method_id", HashValues.CRC32.equals(digest.method)
        ? HashValues.CRC32_CRYPT_METHOD_ID : HashValues.SHA256_CRYPT_METHOD_ID);
    if (sig == null) {
      return;
    }
    if (!UnpackContext.empty(sig.signingScalerLicence)) {
      a.put("i_input_signing_scaler_lic", upperSigner ? sig.signingScalerLicence.toUpperCase()
          : sig.signingScalerLicence);
    }
    if (HashValues.CRC32.equals(sig.method)) {
      a.put("i_decrypted_signature", sig.authenticationKeyEncryptedHashTotal.toString());
      return;
    }
    a.put("i_signing_date_time", UnpackContext.ts(sig.signingDateTime()));
    a.put("i_encrypted_signature", sig.ecEncrSig == null ? null : HexFormat.of().parseHex(sig.ecEncrSig));
    if (sig.identifier != null && !sig.identifier.isEmpty() && sig.identifier.chars().allMatch(Character::isDigit)) {
      a.put("i_input_hbs_encr_key_id", Long.valueOf(sig.identifier));
    }
    // DigitalSignatureService.getHbsRsaPaddingScheme(method): HBS_RSA_PADDING_SCHEME by description.
    a.put("i_hbs_rsa_padding_id", ctx.repository().rsaPaddingId(sig.method));
  }

  // ── weigh slips (composeWS + WSB / WSR / WSO) ─────────────────────────

  private ComposedVersion weighSlip(WeighSlipDocument d) throws ParseException {
    String ddn = d.detailDocumentNumber.toUpperCase();
    ProcArgs a = header("HBS_CREATE_WEIGH_SLIP", ddn, d.documentVersion, false);
    Commons.WeighSlipCommon c = d.common;
    a.put("i_scale_event_type_code", d.eventType); // H1
    a.put("i_scale_site_id_nmbr", UnpackContext.upper(c.scaleSite)); // H2
    a.put("i_input_scale_site", UnpackContext.upperOrNull(c.scaleSite));
    a.put("i_weigh_slip_number", UnpackContext.leftTrimZeros(Integer.toString(c.weighSlipNumber))); // H3
    Date scaleDateTime = c.scaleDateTime();
    a.put("i_scale_date", UnpackContext.ts(scaleDateTime)); // H4 & H5
    a.put("i_input_psy", UnpackContext.upperOrNull(d.populationStrataYear)); // H15
    a.put("i_outgoing_lds_number", d.outgoingLdsNumber); // H17
    a.put("i_destination", d.destination); // H18
    a.put("i_weigh_slip_comment", c.comment); // H19
    a.put("i_input_primary_scaler_lic", UnpackContext.upperOrNull(c.primaryScalerLicence)); // H25
    a.put("i_input_secondary_scaler_lic", UnpackContext.upperOrNull(c.secondaryScalerLicence)); // H26
    signature(a, c.digest, c.signature, true);
    switch (d) {
      case WeighSlipDocument.Billable b -> {
        a.put("i_incoming_lds_number", b.source.incomingLdsNumber); // H6
        a.put("i_transport_identifier", b.source.transportIdentifier); // H7
        a.put("i_gross_weight", b.weights.gross); // H8
        a.put("i_tare_weight", b.weights.tare); // H9
        a.put("i_sample_deduction_weight", b.weights.sampleDeduction); // H10
        a.put("i_input_source", UnpackContext.upperOrNull(b.source.source)); // H11
        a.put("i_input_cutblock_id", UnpackContext.upperOrNull(b.source.cutblockId)); // H12
        a.put("i_field_scale_ind", UnpackContext.flag(b.isFieldScale())); // H13
        a.put("i_field_scale_deck_id", b.fieldScaleDeckId); // H14
        a.put("i_hbs_sample_type_code", String.valueOf(b.sampleType)); // H16
      }
      case WeighSlipDocument.RedTag r -> {
        a.put("i_incoming_lds_number", r.source.incomingLdsNumber);
        a.put("i_transport_identifier", r.source.transportIdentifier);
        a.put("i_gross_weight", r.weights.gross);
        a.put("i_tare_weight", r.weights.tare);
        a.put("i_input_source", UnpackContext.upperOrNull(r.source.source));
        a.put("i_input_cutblock_id", UnpackContext.upperOrNull(r.source.cutblockId));
        a.put("i_field_scale_ind", UnpackContext.flag(r.isFieldScale()));
        a.put("i_field_scale_deck_id", r.fieldScaleDeckId);
      }
      case WeighSlipDocument.Other o -> {
        a.put("i_incoming_lds_number", o.incomingLdsNumber);
        a.put("i_transport_identifier", o.transportIdentifier);
        a.put("i_input_source", UnpackContext.upperOrNull(o.source));
      }
    }
    return new ComposedVersion("W", ddn, d.documentVersion, "HBS_CREATE_WEIGH_SLIP", a, List.of(), null,
        scaleDateTime, null, null);
  }

  // ── log tallies (composeLT + PPS / POT) ───────────────────────────────

  private ProcArgs logTallyHeader(LogTallyDocument d, String ddn) {
    ProcArgs a = header("HBS_CREATE_LOG_TALLY", ddn, d.documentVersion, true);
    Commons.PieceScaleCommon c = d.common;
    a.put("i_scale_site_id_nmbr", UnpackContext.upper(c.scaleSite)); // H2
    a.put("i_scale_event_type_code", d.eventType); // H1
    a.put("i_input_scale_site", UnpackContext.upperOrNull(c.scaleSite));
    a.put("i_load_arrival_number", d.loadArrivalNumber); // H3
    a.put("i_incoming_lds_number", d.incomingLdsNumber); // H4
    a.put("i_transport_identifier", d.transportIdentifier); // H5
    a.put("i_input_source", UnpackContext.upperOrNull(d.source)); // H6
    a.put("i_scale_date", UnpackContext.ts(c.scaleDate)); // H14
    a.put("i_input_primary_scaler_lic", UnpackContext.upperOrNull(c.primaryScalerLicence)); // H15
    a.put("i_return_number", (long) c.primaryScalerReturnNumber); // H16
    a.put("i_log_tally_comment", c.comment); // H24
    a.put("i_input_secondary_scaler_lic", UnpackContext.upperOrNull(c.secondaryScalerLicence)); // H30
    signature(a, c.digest, c.signature, true);
    return a;
  }

  private ComposedVersion pieceScale(LogTallyDocument.PieceScale d) {
    String ddn = d.detailDocumentNumber.toUpperCase();
    ProcArgs a = logTallyHeader(d, ddn);
    a.put("i_input_cutblock_id", UnpackContext.upperOrNull(d.cutblockId)); // H7
    a.put("i_field_scale_ind", UnpackContext.flag(d.isFieldScale())); // H8
    a.put("i_field_scale_deck_id", d.fieldScaleDeckId == null ? null : String.valueOf(d.fieldScaleDeckId)); // H9
    a.put("i_red_tag_weigh_slip", UnpackContext.leftTrimZeros(d.weighSlipNumber)); // H11
    a.put("i_input_orig_redtag_scl_site", UnpackContext.upperOrNull(d.originatingScaleSiteNumber)); // H13
    if (d.splitLoad != null) {
      a.put("i_parcel_count", (long) d.splitLoad.parcelCount); // H17
      a.put("i_parcel_identifier", (long) d.splitLoad.parcelId); // H18
    }
    if (d.checkScale != null) {
      a.put("i_input_orig_chk_scl_scl_lic", UnpackContext.upperOrNull(d.checkScale.originalScalerLicence)); // H19
      a.put("i_orig_chk_scl_return_number", (long) d.checkScale.originalScalerReturnNumber); // H20
      a.put("i_check_replaces_original_ind", UnpackContext.flag(d.checkScale.isCheckReplacingOriginal())); // H21
    }
    a.put("i_volume_calc_method_type", String.valueOf(d.pieceScaleMeasure.scaleMethod)); // H22
    a.put("i_log_count", (long) d.pieceScaleMeasure.logCount); // H23
    a.put("i_beachcomb_ind", UnpackContext.flag(d.isBeachcomb())); // H32

    List<Child> children = new ArrayList<>();
    String inputSource = (String) a.get("i_input_source");
    for (LogTallyDocument.Log log : logs(d.pieceScaleMeasure)) {
      // composePPSLog: a beachcomb log without a visible mark takes the source as its timber mark.
      String timberMark = log.timbermark;
      if ((log.timbermark == null || log.timbermark.trim().isEmpty()) && d.isBeachcomb() && !log.isMarkVisible()
          && !Document.SB4_RESCALE.equals(d.eventType) && !Document.COMPANY_USE.equals(d.eventType)) {
        timberMark = inputSource;
      }
      long logId = ctx.nextId(UnpackContext.SEQ_LOG_TALLY_DETAIL);
      ProcArgs detail = ctx.nulls("HBS_CREATE_LOG_TALLY_DETAIL");
      detail.put("i_logdtl_id", logId);
      detail.put("i_version", (long) d.documentVersion);
      detail.put("i_detail_document_number", ddn);
      putLog(detail, log);
      detail.put("i_input_bcomb_timber_mark", timberMark);
      children.add(new Child("HBS_CREATE_LOG_TALLY_DETAIL", detail));
      defects(children, "HBS_CREATE_LOG_TALLY_DTL_DEF", UnpackContext.SEQ_LOG_TALLY_DETAIL_DEFECT, logId, log);
    }
    return new ComposedVersion("P", ddn, d.documentVersion, "HBS_CREATE_LOG_TALLY", a, children,
        "HBS_STORE_LOG_TALLY", d.common.scaleDate, null, null);
  }

  private ComposedVersion otherTally(LogTallyDocument.OtherTally d) {
    String ddn = d.detailDocumentNumber.toUpperCase();
    ProcArgs a = logTallyHeader(d, ddn);
    if (d.splitLoad != null) {
      a.put("i_parcel_count", (long) d.splitLoad.parcelCount);
      a.put("i_parcel_identifier", (long) d.splitLoad.parcelId);
    }
    a.put("i_log_count", 0L); // H23
    return new ComposedVersion("P", ddn, d.documentVersion, "HBS_CREATE_LOG_TALLY", a, List.of(),
        "HBS_STORE_LOG_TALLY", d.common.scaleDate, null, null);
  }

  /** Log detail columns shared by piece and sample tallies ({@code composePPSLog / composePSSLog}). */
  private void putLog(ProcArgs detail, LogTallyDocument.Log log) {
    char grade = log.grade == 0 ? ' ' : log.grade;
    detail.put("i_log_number", (long) log.number);
    detail.put("i_length", log.length());
    detail.put("i_top", (long) log.top);
    detail.put("i_butt", (long) log.butt);
    detail.put("i_net_volume", log.volume());
    detail.put("i_nmv_ind", log.isMarkVisible() ? "N" : "Y"); // "really!"
    detail.put("i_deduction_count", (long) log.defectCount);
    detail.put("i_input_species", log.species);
    detail.put("i_input_grade", String.valueOf(grade));
    detail.put("i_entry_userid", ctx.userId());
    detail.put("i_entry_timestamp", ctx.now());
  }

  private void defects(List<Child> children, String proc, String sequence, long logId, LogTallyDocument.Log log) {
    if (log.defects == null) {
      return;
    }
    for (LogTallyDocument.Defect defect : log.defects) {
      ProcArgs def = ctx.nulls(proc);
      def.put("i_log_defect_id", ctx.nextId(sequence));
      def.put("i_logdtl_id", logId);
      def.put("i_defect_number", (long) defect.number);
      def.put("i_defect_type", defect.code);
      def.put("i_volume", defect.volume());
      def.put("i_descriptor", defect.description);
      def.put("i_entry_userid", ctx.userId());
      def.put("i_entry_timestamp", ctx.now());
      def.put("i_update_userid", ctx.userId());
      def.put("i_update_timestamp", ctx.now());
      children.add(new Child(proc, def));
    }
  }

  private static List<LogTallyDocument.Log> logs(LogTallyDocument.PieceScaleMeasure m) {
    return m.logs == null ? List.of() : m.logs;
  }

  // ── sample tallies (composePSS) ───────────────────────────────────────

  private ComposedVersion sampleScale(LogTallyDocument.SampleScale d) {
    String ddn = d.detailDocumentNumber.toUpperCase();
    ProcArgs a = header("HBS_CREATE_SAMPLE_LOG_TALLY", ddn, d.documentVersion, true);
    Commons.PieceScaleCommon c = d.common;
    a.put("i_scale_site_id_nmbr", UnpackContext.upper(c.scaleSite));
    a.put("i_scale_event_type_code", d.eventType); // H1
    a.put("i_input_scale_site", UnpackContext.upperOrNull(c.scaleSite)); // H2
    a.put("i_load_arrival_number", d.loadArrivalNumber); // H3
    a.put("i_incoming_lds_number", d.incomingLdsNumber); // H4
    a.put("i_transport_identifier", d.transportIdentifier); // H5
    a.put("i_input_source", UnpackContext.upperOrNull(d.source)); // H6
    a.put("i_input_psy", UnpackContext.upperOrNull(d.populationStrataYear)); // H10
    a.put("i_sampled_weigh_slip", UnpackContext.leftTrimZeros(d.weighSlipNumber)); // H11
    a.put("i_sample_weight", (long) d.sampleWeight); // H12
    a.put("i_input_orig_redtag_scl_site", UnpackContext.upperOrNull(d.originatingScaleSiteNumber)); // H13
    a.put("i_scale_date", UnpackContext.ts(c.scaleDate)); // H14
    a.put("i_input_primary_scaler_lic", UnpackContext.upperOrNull(c.primaryScalerLicence)); // H15
    a.put("i_return_number", (long) c.primaryScalerReturnNumber); // H16
    if (d.checkScale != null) {
      a.put("i_input_orig_chk_scl_sclr_lic", UnpackContext.upperOrNull(d.checkScale.originalScalerLicence)); // H19
      a.put("i_orig_chk_scl_return_number", (long) d.checkScale.originalScalerReturnNumber); // H20
      a.put("i_check_replaces_original_ind", UnpackContext.flag(d.checkScale.isCheckReplacingOriginal())); // H21
    }
    a.put("i_volume_calc_method_type", String.valueOf(d.pieceScaleMeasure.scaleMethod)); // H22
    a.put("i_log_count", (long) d.pieceScaleMeasure.logCount); // H23
    a.put("i_sample_log_tally_comment", c.comment); // H24
    a.put("i_input_secondary_scaler_lic", UnpackContext.upperOrNull(c.secondaryScalerLicence)); // H30
    signature(a, c.digest, c.signature, true);

    List<Child> children = new ArrayList<>();
    for (LogTallyDocument.Log log : logs(d.pieceScaleMeasure)) {
      long logId = ctx.nextId(UnpackContext.SEQ_SAMPLE_TALLY_DETAIL);
      ProcArgs detail = ctx.nulls("HBS_CREATE_SAMPLE_TALLY_DETAIL");
      detail.put("i_logdtl_id", logId);
      detail.put("i_detail_document_number", ddn);
      detail.put("i_version", (long) d.documentVersion);
      putLog(detail, log);
      children.add(new Child("HBS_CREATE_SAMPLE_TALLY_DETAIL", detail));
      defects(children, "HBS_CREATE_SMPL_TALLY_DTL_DEF", UnpackContext.SEQ_SAMPLE_TALLY_DETAIL_DEFECT, logId, log);
    }
    return new ComposedVersion("S", ddn, d.documentVersion, "HBS_CREATE_SAMPLE_LOG_TALLY", a, children,
        "HBS_STORE_SAMPLE_LOG_TALLY", c.scaleDate, null, null);
  }

  // ── SFP tallies (composeSFPT + composeSFP + composeSFPLog) ────────────

  private ComposedVersion sfp(LogTallyDocument.SpecialForestProduct d) {
    String ddn = d.detailDocumentNumber.toUpperCase();
    ProcArgs a = header("HBS_CREATE_SFP_TALLY", ddn, d.documentVersion, false);
    Commons.PieceScaleCommon c = d.common;
    // composeSFPT neither upper-cases nor sets SCALE_SITE_ID_NMBR (left to the edits).
    a.put("i_scale_event_type_code", d.eventType); // H1
    a.put("i_input_scale_site", UnpackContext.upper(c.scaleSite)); // H2 (the setter upper-cases)
    a.put("i_load_arrival_number", d.loadArrivalNumber); // H3
    a.put("i_incoming_lds_number", d.incomingLdsNumber); // H4
    a.put("i_transport_identifier", d.transportIdentifier); // H5
    a.put("i_input_source", d.source); // H6
    a.put("i_scale_date", UnpackContext.ts(c.scaleDate)); // H14
    a.put("i_input_primary_scaler_lic", c.primaryScalerLicence); // H15
    a.put("i_return_number", (long) c.primaryScalerReturnNumber); // H16
    a.put("i_sfp_tally_comment", c.comment); // H24
    a.put("i_input_secondary_scaler_lic", c.secondaryScalerLicence); // H30
    signature(a, c.digest, c.signature, false);
    a.put("i_input_cut_block_id", d.cutblockId);
    a.put("i_field_scale_ind", UnpackContext.flag(d.isFieldScale()));
    a.put("i_field_scale_deck_id", d.fieldScaleDeckId == null ? null : String.valueOf(d.fieldScaleDeckId));
    if (d.splitLoad != null) {
      a.put("i_parcel_count", (long) d.splitLoad.parcelCount); // H17
      a.put("i_parcel_identifier", (long) d.splitLoad.parcelId); // H18
    }
    // SpecialForestProductTallyEntityBean bound a null return number as 0 (VARCHAR2 column).
    a.put("i_orig_chk_scl_return_number", "0");
    if (d.checkScale != null) {
      a.put("i_input_orig_chk_scl_sclr_lic", d.checkScale.originalScalerLicence); // H19
      a.put("i_orig_chk_scl_return_number", Long.toString(d.checkScale.originalScalerReturnNumber)); // H20
      a.put("i_check_replaces_original_ind", UnpackContext.flag(d.checkScale.isCheckReplacingOriginal())); // H21
    }
    a.put("i_hbs_sfp_scale_type_code", String.valueOf(d.measure.scaleMethod)); // H22
    a.put("i_volume_calc_method_type", "N"); // VolumeCalcTypeCode.NET
    a.put("i_log_count", (long) d.measure.detailRecordCount); // H23

    List<LogTallyDocument.SfpItem> items;
    if (d.measure.stacks != null) {
      items = new ArrayList<>(d.measure.stacks);
    } else if (d.measure.bundles != null) {
      items = new ArrayList<>(d.measure.bundles);
    } else {
      a.put("i_sample_piece_count", d.measure.logMeasure.proratedPieceCount);
      items = d.measure.logMeasure.logs == null ? List.of() : new ArrayList<>(d.measure.logMeasure.logs);
    }
    List<Child> children = new ArrayList<>();
    for (LogTallyDocument.SfpItem item : items) {
      ProcArgs x = ctx.nulls("HBS_CREATE_SFP_LOG_DETAIL");
      // SpecialForestProductTallyHelper.getId: DETAIL_SCALE_DOCUMENT_SEQ (not SFP_LOG_DETAIL_SEQ).
      x.put("i_logdtl_id", ctx.nextId(UnpackContext.SEQ_DETAIL_SCALE_DOCUMENT));
      x.put("i_detail_document_number", ddn);
      x.put("i_version", (long) d.documentVersion);
      x.put("i_sfp_number", (long) item.number);
      x.put("i_input_species", item.species);
      x.put("i_input_product", item.product);
      x.put("i_piece_count", 1L); // SFPLogDetail.getPieceCount defaults to 1
      switch (item) {
        case LogTallyDocument.SfpLog log -> {
          x.put("i_net_volume", log.volume());
          // SFPLogDetailEntityBean bound new BigDecimal(length.longValue()): the length lost its decimal.
          x.put("i_prod_log_length", (long) log.length());
          x.put("i_prod_log_top", (long) log.top);
          x.put("i_prod_log_butt", (long) log.butt);
          x.put("i_input_grade", " "); // Grade.UNGRADED
        }
        case LogTallyDocument.SfpStack stack -> {
          x.put("i_net_volume", stack.volume());
          x.put("i_stack_length", (long) stack.lengthValue);
          x.put("i_stack_height", (long) stack.heightValue);
          x.put("i_stack_width", (long) stack.widthValue);
          x.put("i_conversion_factor", stack.conversion());
          x.put("i_input_grade", " ");
        }
        case LogTallyDocument.SfpBundle bundle -> {
          x.put("i_piece_count", (long) bundle.pieceCount);
          x.put("i_input_grade", String.valueOf(bundle.grade == 0 ? ' ' : bundle.grade));
          x.put("i_net_volume", 0.000f); // MR04 6492: no volume for the piece-count method
        }
      }
      x.put("i_entry_userid", ctx.userId());
      x.put("i_entry_timestamp", ctx.now());
      x.put("i_update_userid", ctx.userId());
      x.put("i_update_timestamp", ctx.now());
      children.add(new Child("HBS_CREATE_SFP_LOG_DETAIL", x));
    }
    return new ComposedVersion("F", ddn, d.documentVersion, "HBS_CREATE_SFP_TALLY", a, children, null,
        c.scaleDate, null, null);
  }

  // ── ledgers (composeARL / composeDPL) ─────────────────────────────────

  ComposedVersion arrival(LedgerEntry.ArrivalEntry e, long batchDocumentId) {
    ProcArgs a = ctx.nulls("HBS_CREATE_SCL_SITE_ARR_LEDGER");
    long dtlDocSeq = ctx.nextId(UnpackContext.SEQ_DETAIL_SCALE_DOCUMENT);
    String ddn = "ARL" + leftPad(ctx.nextId(UnpackContext.SEQ_SCL_SITE_LEDGER));
    Date scaleDate = null;
    Date arrivalDate = null;
    if (e.arrival != null) {
      a.put("i_transport_identifier", e.arrival.transportIdentifier);
      a.put("i_incoming_lds_number", e.arrival.incomingLdsNumber);
      a.put("i_load_arrival_number", e.arrival.loadArrivalNumber);
      arrivalDate = e.arrival.arrivalDate();
    }
    if (e.scale != null) {
      scaleDate = e.scale.scaleDate();
      a.put("i_input_primary_scaler_lic", UnpackContext.upperOrNull(e.scale.primaryScalerLicence));
      a.put("i_return_number", (long) e.scale.primaryScalerReturnNumber);
      if (e.scale.splitLoad != null) {
        a.put("i_parcel_identifier", (long) e.scale.splitLoad.parcelId);
        a.put("i_parcel_count", (long) e.scale.splitLoad.parcelCount);
      }
    }
    String fieldScale = null;
    if (e.fieldScaleFlag != null && (e.fieldScaleFlag == 'Y' || e.fieldScaleFlag == 'N')) {
      fieldScale = String.valueOf(e.fieldScaleFlag);
    }
    String origSite = e.originatingScaleSiteNumber;
    if (!Document.REMOTE_RED_TAG.equals(e.eventType) && !Document.REMOTE_SAMPLE_SCALE.equals(e.eventType)) {
      origSite = null; // BT5646
    }
    a.put("i_detail_document_number", ddn);
    a.put("i_version", 1L);
    a.put("i_batdoc_id", batchDocumentId);
    a.put("i_scale_event_type_code", e.eventType);
    a.put("i_scl_rtn_version_state_code", STATE_ACCEPTED);
    a.put("i_scale_date", UnpackContext.ts(scaleDate));
    a.put("i_arrival_date", UnpackContext.ts(arrivalDate));
    a.put("i_arrival_ledger_weigh_slip", UnpackContext.leftTrimZeros(e.weighSlipNumber));
    a.put("i_valid_signature_ind", "Y");
    a.put("i_original_version_ind", "Y");
    a.put("i_active_version_ind", "N");
    a.put("i_field_scale_ind", fieldScale);
    a.put("i_field_scale_deck_id", e.fieldScaleDeckId == null ? null : e.fieldScaleDeckId.toString());
    a.put("i_orig_scale_site", origSite);
    a.put("i_dtl_doc_seq", dtlDocSeq);
    a.put("i_input_scale_site", UnpackContext.upper(e.scaleSite));
    a.put("i_input_source", UnpackContext.upper(e.source));
    a.put("i_input_cutblock_id", UnpackContext.upper(e.cutblockId));
    a.put("i_entry_userid", ctx.userId());
    a.put("i_entry_timestamp", ctx.now());
    a.put("i_update_userid", ctx.userId());
    a.put("i_update_timestamp", ctx.now());
    return new ComposedVersion("A", ddn, 1, "HBS_CREATE_SCL_SITE_ARR_LEDGER", a, List.of(), null, scaleDate,
        arrivalDate, null);
  }

  ComposedVersion departure(LedgerEntry.DepartureEntry e, long batchDocumentId) {
    ProcArgs a = ctx.nulls("HBS_CREATE_DEPARTURE_LEDGER");
    long dtlDocSeq = ctx.nextId(UnpackContext.SEQ_DETAIL_SCALE_DOCUMENT);
    String ddn = "DEP" + leftPad(ctx.nextId(UnpackContext.SEQ_SCL_SITE_LEDGER));
    Date departureDate = e.departureDate();
    a.put("i_detail_document_number", ddn);
    a.put("i_version", 1L);
    a.put("i_batdoc_id", batchDocumentId);
    a.put("i_scale_event_type_code", e.eventType);
    a.put("i_scl_rtn_version_state_code", STATE_ACCEPTED);
    a.put("i_departure_date", UnpackContext.ts(departureDate));
    a.put("i_outgoing_transport_id", e.outgoingTransportIdentifier);
    a.put("i_active_version_ind", "N");
    a.put("i_original_version_ind", "Y");
    a.put("i_valid_signature_ind", "Y");
    a.put("i_outgoing_lds_number", e.outgoingLdsNumber);
    a.put("i_load_departure_number", e.departureNumber);
    a.put("i_destination", e.destination);
    a.put("i_dtl_doc_seq", dtlDocSeq);
    a.put("i_input_scale_site", UnpackContext.upper(e.scaleSite));
    a.put("i_entry_userid", ctx.userId());
    a.put("i_entry_timestamp", ctx.now());
    a.put("i_update_userid", ctx.userId());
    a.put("i_update_timestamp", ctx.now());
    return new ComposedVersion("D", ddn, 1, "HBS_CREATE_DEPARTURE_LEDGER", a, List.of(), null, null, null,
        departureDate);
  }

  /** {@code StringUtil.leftPad(id, 10, '0')}. */
  static String leftPad(long id) {
    return String.format("%010d", id);
  }
}
