package ca.bc.gov.nrs.hbs.api.intake.xml;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tally documents: {@code dac.detail.xml.LogTallyDocument} and its piece
 * scale ({@code PPSDocument}), other tally ({@code POTDocument}), sample scale
 * ({@code PSSDocument}) and special forest product ({@code SFPDocument})
 * subclasses, with their per-event-type field rules and hash strings.
 */
public abstract sealed class LogTallyDocument extends Document
    permits LogTallyDocument.PieceScale, LogTallyDocument.OtherTally, LogTallyDocument.SampleScale,
    LogTallyDocument.SpecialForestProduct {

  public Commons.PieceScaleCommon common;
  public String loadArrivalNumber;
  public String incomingLdsNumber;
  public String transportIdentifier;
  public String source;

  @Override
  protected Parts.Digest digest() {
    return common.digest;
  }

  @Override
  public String scaleSite() {
    return common.scaleSite;
  }

  protected void validateHashTotal() throws FormatEditException {
    Edits.assertEquals(FormatEdit.LogTally.HASH_TOTAL, hashValue(), common.digest.hashValue()); // FTAH33
  }

  protected void validateScaleSite(String batchScaleSite) throws FormatEditException {
    Edits.assertEquals(FormatEdit.LogTally.SCALE_SITE, batchScaleSite, common.scaleSite); // FTAH02
  }

  /** Software, DDN/DV, secondary scaler, signing scaler: the tail every tally hash shares. */
  protected void appendSoftwareAndIds(StringBuilder sb) {
    if (common.comment != null) {
      sb.append(common.comment);
    }
    sb.append(common.software.product);
    sb.append(common.software.version);
    sb.append(common.software.revision);
    sb.append(detailDocumentNumber);
    sb.append(documentVersion);
    if (common.secondaryScalerLicence != null) {
      sb.append(common.secondaryScalerLicence);
    }
    if (common.signature != null) {
      sb.append(common.signature.signingScalerLicence2());
    }
  }

  protected void appendScaler(StringBuilder sb) {
    sb.append(common.scaleDate2());
    sb.append(common.primaryScalerLicence);
    sb.append(common.primaryScalerReturnNumber);
  }

  /** {@code <PSMeasure>}. */
  public static class PieceScaleMeasure {
    public List<Log> logs;
    public short logCount;
    public char scaleMethod;

    /** FTAH23. Missing logs count as none (the legacy code failed with a NullPointerException). */
    void validate() throws FormatEditException {
      Edits.assertEquals(FormatEdit.LogTally.LOG_COUNT, logCount, logs == null ? 0 : logs.size());
    }
  }

  /** {@code <Log>} of a piece or sample tally. */
  public static class Log {
    public short number;
    public String timbermark;
    public Character nmv;
    public String species;
    public char grade;
    public short lengthValue;
    public short top;
    public short butt;
    public int volumeValue;
    public byte defectCount;
    public List<Defect> defects;

    public boolean isMarkVisible() {
      return nmv == null;
    }

    public float length() {
      return lengthValue / 10F;
    }

    public float volume() {
      return volumeValue / 1000F;
    }

    float totalDefectVolume() {
      float defectVolume = 0.0F;
      if (defects != null) {
        for (Defect d : defects) {
          defectVolume += d.volume();
        }
      }
      return defectVolume;
    }

    String hashTotalString() {
      StringBuilder sb = new StringBuilder();
      sb.append(number);
      if (timbermark != null) {
        sb.append(timbermark);
      }
      if (nmv != null) {
        sb.append(nmv);
      }
      sb.append(species);
      sb.append(grade);
      sb.append(lengthValue);
      sb.append(top);
      sb.append(butt);
      sb.append(volumeValue);
      sb.append(defectCount);
      if (defects != null) {
        for (Defect d : defects) {
          sb.append(d.hashTotalString());
        }
      }
      return sb.toString();
    }

    /** {@code Log.validate}: FTAL01, FTAL02, FTAL10, FTAD01, FTAL09. */
    void validate(Set<Short> logNumbers, boolean beachcomb) throws FormatEditException {
      if (!logNumbers.add(number)) {
        Edits.fail(FormatEdit.LogTally.LOG_NUMBER);
      }
      if (!beachcomb && timbermark != null) {
        Edits.fail(FormatEdit.LogTally.LOG_TIMBERMARK);
      }
      byte actual = defects != null ? (byte) defects.size() : 0;
      Edits.assertEquals(FormatEdit.LogTally.LOG_DEFECT_COUNT, defectCount, actual);
      if (actual > 0) {
        Set<Byte> defectNumbers = new HashSet<>();
        for (Defect d : defects) {
          if (!defectNumbers.add(d.number)) {
            Edits.fail(FormatEdit.LogTally.DEFECT_NUMBER);
          }
        }
      }
      Edits.assertEquals(FormatEdit.LogTally.LOG_VOLUME,
          LogVolume.netVolume(top, butt, length(), totalDefectVolume()), volume(), LogVolume.VOLUME_TOLERANCE);
    }
  }

  /** {@code <Defect>}. */
  public static class Defect {
    public byte number;
    public String code;
    public int volumeValue;
    public String description;

    public float volume() {
      return volumeValue / 1000F;
    }

    String hashTotalString() {
      return new StringBuilder().append(number).append(code).append(volumeValue).append(description).toString();
    }
  }

  static void validateLogs(PieceScaleMeasure measure, boolean beachcomb) throws FormatEditException {
    if (measure.logCount > 0) {
      Set<Short> logNumbers = new HashSet<>();
      for (Log log : measure.logs == null ? List.<Log>of() : measure.logs) {
        log.validate(logNumbers, beachcomb);
      }
    }
  }

  static void appendLogs(StringBuilder sb, List<Log> logs) {
    if (logs != null) {
      for (Log log : logs) {
        sb.append(log.hashTotalString());
      }
    }
  }

  /** {@code PPSDocument}: PS, FD, RT, RR, CS. */
  public static final class PieceScale extends LogTallyDocument {
    public String cutblockId;
    public char fieldScaleFlag;
    public Character fieldScaleDeckId;
    public String weighSlipNumber;
    public String originatingScaleSiteNumber;
    public Character beachcomb;
    public Parts.SplitLoad splitLoad;
    public Parts.CheckScale checkScale;
    public PieceScaleMeasure pieceScaleMeasure;

    public boolean isFieldScale() {
      return fieldScaleFlag == 'Y';
    }

    public boolean isBeachcomb() {
      return Character.valueOf('Y').equals(beachcomb);
    }

    @Override
    public char documentType() {
      return 'P';
    }

    @Override
    public void validate(String batchScaleSite, ReferenceLookups lookups) throws FormatEditException {
      validateFieldInclusion();
      validateDocumentType(FormatEdit.LogTally.PIECE_DOCUMENT_TYPE, lookups); // FTBH01
      validateScaleSite(batchScaleSite); // FTAH02
      common.validate(); // FTAH14
      pieceScaleMeasure.validate(); // FTAH23
      validateUniqueness(FormatEdit.LogTally.DOCUMENT_VERSION, lookups); // FTAH29
      validateLogs(pieceScaleMeasure, isBeachcomb());
      validateHashTotal(); // FTAH33
    }

    @Override
    protected void checkFields() throws FormatEditException {
      switch (String.valueOf(eventType)) {
        case PRIMARY_SCALE -> {
          Edits.assertNotNull(FormatEdit.LogTally.LOAD_ARRIVAL_NUMBER_REQUIRED, loadArrivalNumber);
          Edits.assertNotNull(FormatEdit.LogTally.INCOMING_LSD_NUMBER_REQUIRED, incomingLdsNumber);
          Edits.assertNotNull(FormatEdit.LogTally.TRANSPORT_IDENTIFIER_REQUIRED, transportIdentifier);
          Edits.assertNotNull(FormatEdit.LogTally.CUTBLOCK_ID_REQUIRED, cutblockId);
          Edits.assertNull(FormatEdit.LogTally.WEIGH_SLIP_NUMBER_NOT_PERMITTED, weighSlipNumber);
          Edits.assertNull(FormatEdit.LogTally.ORIGINATING_SCALE_SITE_NUMBER_NOT_PERMITTED,
              originatingScaleSiteNumber);
          Edits.assertNull(FormatEdit.LogTally.CHECK_SCALE_NOT_PERMITTED, checkScale);
        }
        case FROM_SITE_DECK -> {
          Edits.assertNotNull(FormatEdit.LogTally.LOAD_ARRIVAL_NUMBER_REQUIRED, loadArrivalNumber);
          Edits.assertNull(FormatEdit.LogTally.INCOMING_LSD_NUMBER_NOT_PERMITTED, incomingLdsNumber);
          Edits.assertNull(FormatEdit.LogTally.TRANSPORT_IDENTIFIER_NOT_PERMITTED, transportIdentifier);
          Edits.assertNull(FormatEdit.LogTally.CUTBLOCK_ID_NOT_PERMITTED, cutblockId);
          Edits.assertNull(FormatEdit.LogTally.WEIGH_SLIP_NUMBER_NOT_PERMITTED, weighSlipNumber);
          Edits.assertNull(FormatEdit.LogTally.ORIGINATING_SCALE_SITE_NUMBER_NOT_PERMITTED,
              originatingScaleSiteNumber);
          Edits.assertNull(FormatEdit.LogTally.SPLIT_LOAD_NOT_PERMITTED, splitLoad);
          Edits.assertNull(FormatEdit.LogTally.CHECK_SCALE_NOT_PERMITTED, checkScale);
        }
        case RED_TAG -> {
          Edits.assertNull(FormatEdit.LogTally.LOAD_ARRIVAL_NUMBER_NOT_PERMITTED, loadArrivalNumber);
          Edits.assertNull(FormatEdit.LogTally.INCOMING_LSD_NUMBER_NOT_PERMITTED, incomingLdsNumber);
          Edits.assertNull(FormatEdit.LogTally.TRANSPORT_IDENTIFIER_NOT_PERMITTED, transportIdentifier);
          Edits.assertNotNull(FormatEdit.LogTally.CUTBLOCK_ID_REQUIRED, cutblockId);
          Edits.assertNotNull(FormatEdit.LogTally.WEIGH_SLIP_NUMBER_REQUIRED, weighSlipNumber);
          Edits.assertNull(FormatEdit.LogTally.ORIGINATING_SCALE_SITE_NUMBER_NOT_PERMITTED,
              originatingScaleSiteNumber);
          Edits.assertNull(FormatEdit.LogTally.CHECK_SCALE_NOT_PERMITTED, checkScale);
        }
        case REMOTE_RED_TAG -> {
          Edits.assertNotNull(FormatEdit.LogTally.LOAD_ARRIVAL_NUMBER_REQUIRED, loadArrivalNumber);
          Edits.assertNotNull(FormatEdit.LogTally.INCOMING_LSD_NUMBER_REQUIRED, incomingLdsNumber);
          Edits.assertNotNull(FormatEdit.LogTally.TRANSPORT_IDENTIFIER_REQUIRED, transportIdentifier);
          Edits.assertNotNull(FormatEdit.LogTally.CUTBLOCK_ID_REQUIRED, cutblockId);
          Edits.assertNotNull(FormatEdit.LogTally.WEIGH_SLIP_NUMBER_REQUIRED, weighSlipNumber);
          Edits.assertNotNull(FormatEdit.LogTally.ORIGINATING_SCALE_SITE_NUMBER_REQUIRED,
              originatingScaleSiteNumber);
          Edits.assertNull(FormatEdit.LogTally.CHECK_SCALE_NOT_PERMITTED, checkScale);
        }
        case CHECK_SCALE -> {
          Edits.assertNull(FormatEdit.LogTally.LOAD_ARRIVAL_NUMBER_NOT_PERMITTED, loadArrivalNumber);
          Edits.assertNull(FormatEdit.LogTally.INCOMING_LSD_NUMBER_NOT_PERMITTED, incomingLdsNumber);
          Edits.assertNull(FormatEdit.LogTally.TRANSPORT_IDENTIFIER_NOT_PERMITTED, transportIdentifier);
          Edits.assertNotNull(FormatEdit.LogTally.CUTBLOCK_ID_REQUIRED, cutblockId);
          Edits.assertNull(FormatEdit.LogTally.WEIGH_SLIP_NUMBER_NOT_PERMITTED, weighSlipNumber);
          Edits.assertNull(FormatEdit.LogTally.ORIGINATING_SCALE_SITE_NUMBER_NOT_PERMITTED,
              originatingScaleSiteNumber);
          Edits.assertNull(FormatEdit.LogTally.SPLIT_LOAD_NOT_PERMITTED, splitLoad);
          Edits.assertNotNull(FormatEdit.LogTally.CHECK_SCALE_REQUIRED, checkScale);
        }
        default -> throw unknown(eventType);
      }
    }

    @Override
    protected String hashString() {
      StringBuilder sb = new StringBuilder();
      sb.append(eventType);
      sb.append(common.scaleSite);
      switch (eventType) {
        case PRIMARY_SCALE, REMOTE_RED_TAG -> {
          sb.append(loadArrivalNumber);
          sb.append(incomingLdsNumber);
          sb.append(transportIdentifier);
          sb.append(source);
          sb.append(cutblockId);
        }
        case FROM_SITE_DECK -> {
          sb.append(loadArrivalNumber);
          sb.append(source);
        }
        default -> { // RT, CS
          sb.append(source);
          sb.append(cutblockId);
        }
      }
      sb.append(fieldScaleFlag);
      if (fieldScaleDeckId != null) {
        sb.append(fieldScaleDeckId);
      }
      if (RED_TAG.equals(eventType)) {
        sb.append(weighSlipNumber);
      } else if (REMOTE_RED_TAG.equals(eventType)) {
        sb.append(weighSlipNumber);
        sb.append(originatingScaleSiteNumber);
      }
      appendScaler(sb);
      if (CHECK_SCALE.equals(eventType)) {
        sb.append(checkScale.originalScalerLicence);
        sb.append(checkScale.originalScalerReturnNumber);
        sb.append(checkScale.checkReplacesOriginal);
      } else if (!FROM_SITE_DECK.equals(eventType) && splitLoad != null) {
        sb.append(splitLoad.parcelCount);
        sb.append(splitLoad.parcelId);
      }
      sb.append(pieceScaleMeasure.scaleMethod);
      sb.append(pieceScaleMeasure.logCount);
      appendSoftwareAndIds(sb);
      if (beachcomb != null) {
        sb.append(beachcomb);
      }
      appendLogs(sb, pieceScaleMeasure.logs);
      return sb.toString();
    }
  }

  /** {@code POTDocument}: 4R, CU. */
  public static final class OtherTally extends LogTallyDocument {
    public Parts.SplitLoad splitLoad;

    @Override
    public char documentType() {
      return 'P';
    }

    @Override
    public void validate(String batchScaleSite, ReferenceLookups lookups) throws FormatEditException {
      validateFieldInclusion();
      validateDocumentType(FormatEdit.LogTally.PIECE_DOCUMENT_TYPE, lookups);
      validateScaleSite(batchScaleSite);
      common.validate();
      validateUniqueness(FormatEdit.LogTally.DOCUMENT_VERSION, lookups);
      validateHashTotal();
    }

    @Override
    protected void checkFields() throws FormatEditException {
      switch (String.valueOf(eventType)) {
        case SB4_RESCALE -> {
          Edits.assertNotNull(FormatEdit.LogTally.LOAD_ARRIVAL_NUMBER_REQUIRED, loadArrivalNumber);
          Edits.assertNotNull(FormatEdit.LogTally.INCOMING_LSD_NUMBER_REQUIRED, incomingLdsNumber);
          Edits.assertNotNull(FormatEdit.LogTally.TRANSPORT_IDENTIFIER_REQUIRED, transportIdentifier);
          Edits.assertNotNull(FormatEdit.LogTally.SOURCE_REQUIRED, source);
        }
        case COMPANY_USE -> {
          Edits.assertNull(FormatEdit.LogTally.LOAD_ARRIVAL_NUMBER_NOT_PERMITTED, loadArrivalNumber);
          Edits.assertNull(FormatEdit.LogTally.INCOMING_LSD_NUMBER_NOT_PERMITTED, incomingLdsNumber);
          Edits.assertNull(FormatEdit.LogTally.TRANSPORT_IDENTIFIER_NOT_PERMITTED, transportIdentifier);
          Edits.assertNull(FormatEdit.LogTally.SOURCE_NOT_PERMITTED, source);
          Edits.assertNull(FormatEdit.LogTally.SPLIT_LOAD_NOT_PERMITTED, splitLoad);
        }
        default -> throw unknown(eventType);
      }
    }

    @Override
    protected String hashString() {
      StringBuilder sb = new StringBuilder();
      sb.append(eventType);
      sb.append(common.scaleSite);
      if (SB4_RESCALE.equals(eventType)) {
        sb.append(loadArrivalNumber);
        sb.append(incomingLdsNumber);
        sb.append(transportIdentifier);
        sb.append(source);
      }
      appendScaler(sb);
      if (SB4_RESCALE.equals(eventType) && splitLoad != null) {
        sb.append(splitLoad.parcelCount);
        sb.append(splitLoad.parcelId);
      }
      appendSoftwareAndIds(sb);
      return sb.toString();
    }
  }

  /** {@code PSSDocument}: SS, RS, CS. */
  public static final class SampleScale extends LogTallyDocument {
    public String populationStrataYear;
    public String weighSlipNumber;
    public int sampleWeight;
    public String originatingScaleSiteNumber;
    public Parts.CheckScale checkScale;
    public PieceScaleMeasure pieceScaleMeasure;

    @Override
    public char documentType() {
      return 'S';
    }

    @Override
    public void validate(String batchScaleSite, ReferenceLookups lookups) throws FormatEditException {
      validateFieldInclusion();
      validateDocumentType(FormatEdit.LogTally.SAMPLE_DOCUMENT_TYPE, lookups); // FTAH01
      validateScaleSite(batchScaleSite);
      common.validate();
      pieceScaleMeasure.validate();
      validateUniqueness(FormatEdit.LogTally.DOCUMENT_VERSION, lookups);
      validateHashTotal();
      validateLogs(pieceScaleMeasure, false); // logs in a sample never have a timber mark
    }

    @Override
    protected void checkFields() throws FormatEditException {
      switch (String.valueOf(eventType)) {
        case SAMPLE_SCALE -> {
          Edits.assertNull(FormatEdit.LogTally.LOAD_ARRIVAL_NUMBER_NOT_PERMITTED, loadArrivalNumber);
          Edits.assertNull(FormatEdit.LogTally.INCOMING_LSD_NUMBER_NOT_PERMITTED, incomingLdsNumber);
          Edits.assertNull(FormatEdit.LogTally.TRANSPORT_IDENTIFIER_NOT_PERMITTED, transportIdentifier);
          Edits.assertNull(FormatEdit.LogTally.ORIGINATING_SCALE_SITE_NUMBER_NOT_PERMITTED,
              originatingScaleSiteNumber);
          Edits.assertNull(FormatEdit.LogTally.CHECK_SCALE_NOT_PERMITTED, checkScale);
        }
        case REMOTE_SAMPLE_SCALE -> {
          Edits.assertNotNull(FormatEdit.LogTally.LOAD_ARRIVAL_NUMBER_REQUIRED, loadArrivalNumber);
          Edits.assertNotNull(FormatEdit.LogTally.INCOMING_LSD_NUMBER_REQUIRED, incomingLdsNumber);
          Edits.assertNotNull(FormatEdit.LogTally.TRANSPORT_IDENTIFIER_REQUIRED, transportIdentifier);
          Edits.assertNotNull(FormatEdit.LogTally.ORIGINATING_SCALE_SITE_NUMBER_REQUIRED,
              originatingScaleSiteNumber);
          Edits.assertNull(FormatEdit.LogTally.CHECK_SCALE_NOT_PERMITTED, checkScale);
        }
        case CHECK_SCALE -> {
          Edits.assertNull(FormatEdit.LogTally.LOAD_ARRIVAL_NUMBER_NOT_PERMITTED, loadArrivalNumber);
          Edits.assertNull(FormatEdit.LogTally.INCOMING_LSD_NUMBER_NOT_PERMITTED, incomingLdsNumber);
          Edits.assertNull(FormatEdit.LogTally.TRANSPORT_IDENTIFIER_NOT_PERMITTED, transportIdentifier);
          Edits.assertNull(FormatEdit.LogTally.ORIGINATING_SCALE_SITE_NUMBER_NOT_PERMITTED,
              originatingScaleSiteNumber);
          Edits.assertNotNull(FormatEdit.LogTally.CHECK_SCALE_REQUIRED, checkScale);
        }
        default -> throw unknown(eventType);
      }
    }

    @Override
    protected String hashString() {
      StringBuilder sb = new StringBuilder();
      sb.append(eventType);
      sb.append(common.scaleSite);
      if (REMOTE_SAMPLE_SCALE.equals(eventType)) {
        sb.append(loadArrivalNumber);
        sb.append(incomingLdsNumber);
        sb.append(transportIdentifier);
      }
      sb.append(source);
      sb.append(populationStrataYear);
      sb.append(weighSlipNumber);
      sb.append(sampleWeight);
      if (REMOTE_SAMPLE_SCALE.equals(eventType)) {
        sb.append(originatingScaleSiteNumber);
      }
      appendScaler(sb);
      if (CHECK_SCALE.equals(eventType) && checkScale != null) {
        sb.append(checkScale.originalScalerLicence);
        sb.append(checkScale.originalScalerReturnNumber);
        sb.append(checkScale.checkReplacesOriginal);
      }
      sb.append(pieceScaleMeasure.scaleMethod);
      sb.append(pieceScaleMeasure.logCount);
      appendSoftwareAndIds(sb);
      appendLogs(sb, pieceScaleMeasure.logs);
      return sb.toString();
    }
  }

  /** {@code SFPDocument}: PS, CS. */
  public static final class SpecialForestProduct extends LogTallyDocument {
    public String cutblockId;
    public char fieldScaleFlag;
    public Character fieldScaleDeckId;
    public Parts.SplitLoad splitLoad;
    public Parts.CheckScale checkScale;
    public SfpMeasure measure;

    public boolean isFieldScale() {
      return fieldScaleFlag == 'Y';
    }

    @Override
    public char documentType() {
      return 'F';
    }

    @Override
    public void validate(String batchScaleSite, ReferenceLookups lookups) throws FormatEditException {
      validateFieldInclusion();
      validateDocumentType(FormatEdit.LogTally.PIECE_DOCUMENT_TYPE, lookups); // FTBH01
      validateScaleSite(batchScaleSite);
      common.validate();
      measure.validate(); // FTAH23, FTAH32/35/36/37
      validateUniqueness(FormatEdit.LogTally.DOCUMENT_VERSION, lookups);
      if (measure.detailRecordCount > 0) {
        Set<Short> logNumbers = new HashSet<>();
        for (SfpItem item : measure.items()) {
          if (!logNumbers.add(item.number)) {
            Edits.fail(FormatEdit.LogTally.LOG_NUMBER);
          }
          item.validateVolume();
        }
      }
      validateHashTotal();
    }

    @Override
    protected void checkFields() throws FormatEditException {
      switch (String.valueOf(eventType)) {
        case PRIMARY_SCALE -> {
          Edits.assertNotNull(FormatEdit.LogTally.LOAD_ARRIVAL_NUMBER_REQUIRED, loadArrivalNumber);
          Edits.assertNotNull(FormatEdit.LogTally.INCOMING_LSD_NUMBER_REQUIRED, incomingLdsNumber);
          Edits.assertNotNull(FormatEdit.LogTally.TRANSPORT_IDENTIFIER_REQUIRED, transportIdentifier);
          Edits.assertNull(FormatEdit.LogTally.CHECK_SCALE_NOT_PERMITTED, checkScale);
        }
        case CHECK_SCALE -> {
          Edits.assertNull(FormatEdit.LogTally.LOAD_ARRIVAL_NUMBER_NOT_PERMITTED, loadArrivalNumber);
          Edits.assertNull(FormatEdit.LogTally.INCOMING_LSD_NUMBER_NOT_PERMITTED, incomingLdsNumber);
          Edits.assertNull(FormatEdit.LogTally.TRANSPORT_IDENTIFIER_NOT_PERMITTED, transportIdentifier);
          Edits.assertNull(FormatEdit.LogTally.SPLIT_LOAD_NOT_PERMITTED, splitLoad);
          Edits.assertNotNull(FormatEdit.LogTally.CHECK_SCALE_REQUIRED, checkScale);
        }
        default -> throw unknown(eventType);
      }
    }

    @Override
    protected String hashString() {
      StringBuilder sb = new StringBuilder();
      sb.append(eventType);
      sb.append(common.scaleSite);
      if (PRIMARY_SCALE.equals(eventType)) {
        sb.append(loadArrivalNumber);
        sb.append(incomingLdsNumber);
        sb.append(transportIdentifier);
      }
      sb.append(source);
      sb.append(cutblockId);
      sb.append(fieldScaleFlag);
      if (fieldScaleDeckId != null) {
        sb.append(fieldScaleDeckId);
      }
      appendScaler(sb);
      if (CHECK_SCALE.equals(eventType)) {
        sb.append(checkScale.originalScalerLicence);
        sb.append(checkScale.originalScalerReturnNumber);
        sb.append(checkScale.checkReplacesOriginal);
      } else if (splitLoad != null) {
        sb.append(splitLoad.parcelCount);
        sb.append(splitLoad.parcelId);
      }
      sb.append(measure.scaleMethod);
      sb.append(measure.detailRecordCount);
      appendSoftwareAndIds(sb);
      if (measure.stacks != null) {
        measure.stacks.forEach(s -> sb.append(s.hashTotalString()));
      }
      if (measure.bundles != null) {
        measure.bundles.forEach(b -> sb.append(b.hashTotalString()));
      }
      if (measure.logMeasure != null) {
        if (measure.logMeasure.proratedPieceCount != null) {
          sb.append(measure.logMeasure.proratedPieceCount);
        }
        if (measure.logMeasure.logs != null) {
          measure.logMeasure.logs.forEach(l -> sb.append(l.hashTotalString()));
        }
      }
      return sb.toString();
    }
  }

  /** Scale methods of an SFP measure ({@code LogTallyCommon.ScaleMethod}). */
  public static final String SCALE_METHOD_LOG = "L";
  public static final String SCALE_METHOD_PRORATED = "P";
  public static final String SCALE_METHOD_STACK = "K";
  public static final String SCALE_METHOD_PIECE_COUNT = "C";

  /** {@code <SFPMeasure>}. */
  public static class SfpMeasure {
    public char scaleMethod;
    public short detailRecordCount;
    public List<SfpStack> stacks;
    public List<SfpBundle> bundles;
    public SfpLogMeasure logMeasure;

    /** The detail items in the legacy order of precedence: stacks, else bundles, else logs. */
    public List<SfpItem> items() {
      if (stacks != null) {
        return new ArrayList<>(stacks);
      }
      if (bundles != null) {
        return new ArrayList<>(bundles);
      }
      if (logMeasure != null && logMeasure.logs != null) {
        return new ArrayList<>(logMeasure.logs);
      }
      return List.of();
    }

    void validate() throws FormatEditException {
      int count = 0;
      if (stacks != null) {
        count = stacks.size();
      } else if (bundles != null) {
        count = bundles.size();
      } else if (logMeasure != null && logMeasure.logs != null) {
        count = logMeasure.logs.size();
      }
      Edits.assertEquals(FormatEdit.LogTally.LOG_COUNT, detailRecordCount, count);

      String method = String.valueOf(scaleMethod);
      if (SCALE_METHOD_STACK.equals(method)) {
        Edits.assertNull(FormatEdit.LogTally.BUNDLE_NOT_PERMITTED, bundles);
        Edits.assertNull(FormatEdit.LogTally.SFP_LOG_MEASURE_NOT_PERMITTED, logMeasure);
      } else if (SCALE_METHOD_PIECE_COUNT.equals(method)) {
        Edits.assertNull(FormatEdit.LogTally.STACK_NOT_PERMITTED, stacks);
        Edits.assertNull(FormatEdit.LogTally.SFP_LOG_MEASURE_NOT_PERMITTED, logMeasure);
      } else {
        Edits.assertNull(FormatEdit.LogTally.STACK_NOT_PERMITTED, stacks);
        Edits.assertNull(FormatEdit.LogTally.BUNDLE_NOT_PERMITTED, bundles);
        if (logMeasure != null) {
          if (SCALE_METHOD_PRORATED.equals(method)) {
            Edits.assertNotNull(FormatEdit.LogTally.PRORATED_PIECE_COUNT_REQUIRED, logMeasure.proratedPieceCount);
          } else {
            Edits.assertNull(FormatEdit.LogTally.PRORATED_PIECE_COUNT_NOT_PERMITTED, logMeasure.proratedPieceCount);
          }
        }
      }
    }
  }

  /** {@code <SFPLogMeasure>}. */
  public static class SfpLogMeasure {
    public List<SfpLog> logs;
    public Long proratedPieceCount;
  }

  /** {@code SFPLogCommon}: a stack, bundle or log of an SFP tally. */
  public abstract static sealed class SfpItem permits SfpStack, SfpBundle, SfpLog {
    public short number;
    public String species;
    public String product;

    abstract String hashTotalString();

    void validateVolume() throws FormatEditException {
      // bundles have no volume check
    }
  }

  /** {@code <Stack>}. */
  public static final class SfpStack extends SfpItem {
    public short lengthValue;
    public short widthValue;
    public short heightValue;
    public short conversionFactor;
    public int volumeValue;

    public double conversion() {
      return conversionFactor / 100.0;
    }

    public float volume() {
      return volumeValue / 1000F;
    }

    @Override
    void validateVolume() throws FormatEditException {
      Edits.assertEquals(FormatEdit.LogTally.LOG_VOLUME,
          LogVolume.stackVolume(lengthValue, widthValue, heightValue, conversion()), volume(),
          LogVolume.VOLUME_TOLERANCE);
    }

    @Override
    String hashTotalString() {
      return new StringBuilder().append(product).append(species).append(number).append(widthValue)
          .append(heightValue).append(lengthValue).append(conversionFactor).append(volumeValue).toString();
    }
  }

  /** {@code <Bundle>}. */
  public static final class SfpBundle extends SfpItem {
    public short pieceCount;
    public char grade;

    @Override
    String hashTotalString() {
      return new StringBuilder().append(product).append(species).append(grade).append(number)
          .append(pieceCount).toString();
    }
  }

  /** {@code <Log>} of an SFP log measure. */
  public static final class SfpLog extends SfpItem {
    public short lengthValue;
    public short top;
    public short butt;
    public int volumeValue;

    public float length() {
      return lengthValue / 10F;
    }

    public float volume() {
      return volumeValue / 1000F;
    }

    @Override
    void validateVolume() throws FormatEditException {
      Edits.assertEquals(FormatEdit.LogTally.LOG_VOLUME, LogVolume.netVolume(top, butt, length(), 0.0f), volume(),
          LogVolume.VOLUME_TOLERANCE);
    }

    @Override
    String hashTotalString() {
      return new StringBuilder().append(product).append(number).append(species).append(lengthValue).append(top)
          .append(butt).append(volumeValue).toString();
    }
  }
}
