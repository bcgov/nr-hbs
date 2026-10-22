package ca.bc.gov.nrs.hbs.api.intake.xml;

/**
 * Weigh slip documents: {@code dac.detail.xml.WeighSlipDocument} and its
 * billable ({@code WSBDocument}), red tag ({@code WSRDocument}) and other
 * ({@code WSODocument}) subclasses, with their per-event-type field rules and
 * hash strings.
 */
public abstract sealed class WeighSlipDocument extends Document
    permits WeighSlipDocument.Billable, WeighSlipDocument.RedTag, WeighSlipDocument.Other {

  public Commons.WeighSlipCommon common;
  public String populationStrataYear;
  public String destination;
  public String outgoingLdsNumber;

  @Override
  public char documentType() {
    return 'W';
  }

  @Override
  protected Parts.Digest digest() {
    return common.digest;
  }

  @Override
  public String scaleSite() {
    return common.scaleSite;
  }

  /** The source code of the slip, for the edit-error row (H11). */
  public abstract String sourceCode();

  @Override
  public void validate(String batchScaleSite, ReferenceLookups lookups) throws FormatEditException {
    validateFieldInclusion(); // FWAH01
    validateDocumentType(FormatEdit.WeighSlip.WEIGHT_DOCUMENT_TYPE, lookups); // FWAH01
    Edits.assertEquals(FormatEdit.WeighSlip.SCALE_SITE, batchScaleSite, common.scaleSite); // FWAH02
    common.validate(); // FWAH04, FWAH05
    validateUniqueness(FormatEdit.WeighSlip.DOCUMENT_VERSION, lookups); // FWAH24
    Edits.assertEquals(FormatEdit.WeighSlip.HASH_TOTAL, hashValue(), common.digest.hashValue()); // FWAH28
  }

  /** Common tail of every weigh slip hash string: comment, software, DDN/DV, scalers. */
  protected void appendTail(StringBuilder sb) {
    if (common.comment != null) {
      sb.append(common.comment);
    }
    sb.append(common.software.product);
    sb.append(common.software.version);
    sb.append(common.software.revision);
    sb.append(detailDocumentNumber);
    sb.append(documentVersion);
    sb.append(common.primaryScalerLicence);
    if (common.secondaryScalerLicence != null) {
      sb.append(common.secondaryScalerLicence);
    }
    if (common.signature != null) {
      sb.append(common.signature.signingScalerLicence2());
    }
  }

  protected StringBuilder head() {
    StringBuilder sb = new StringBuilder();
    sb.append(eventType);
    sb.append(common.scaleSite);
    sb.append(common.weighSlipNumber);
    sb.append(common.scaleDate2());
    sb.append(common.scaleTime2());
    return sb;
  }

  /** {@code WSBDocument}: PS, SS, RS, PD. */
  public static final class Billable extends WeighSlipDocument {
    public Parts.WeighSlipWeights weights;
    public Parts.WeighSlipSource source;
    public char fieldScaleFlag;
    public String fieldScaleDeckId;
    public char sampleType;

    public boolean isFieldScale() {
      return fieldScaleFlag == 'Y';
    }

    @Override
    public String sourceCode() {
      return source.source;
    }

    @Override
    protected void checkFields() throws FormatEditException {
      switch (String.valueOf(eventType)) {
        case PRIMARY_SCALE -> {
          Edits.assertNull(FormatEdit.WeighSlip.SAMPLE_DEDUCTION_NOT_PERMITTED, weights.sampleDeduction);
          Edits.assertNull(FormatEdit.WeighSlip.OUTGOING_LDS_NUMBER_NOT_PERMITTED, outgoingLdsNumber);
          Edits.assertNull(FormatEdit.WeighSlip.DESTINATION_NOT_PERMITTED, destination);
        }
        case SAMPLE_SCALE -> {
          Edits.assertNull(FormatEdit.WeighSlip.OUTGOING_LDS_NUMBER_NOT_PERMITTED, outgoingLdsNumber);
          Edits.assertNull(FormatEdit.WeighSlip.DESTINATION_NOT_PERMITTED, destination);
        }
        case REMOTE_SAMPLE_SCALE, PRIMARY_DEPARTURE -> {
          Edits.assertNull(FormatEdit.WeighSlip.SAMPLE_DEDUCTION_NOT_PERMITTED, weights.sampleDeduction);
          Edits.assertNotNull(FormatEdit.WeighSlip.OUTGOING_LDS_NUMBER_REQUIRED, outgoingLdsNumber);
          Edits.assertNotNull(FormatEdit.WeighSlip.DESTINATION_REQUIRED, destination);
        }
        default -> throw unknown(eventType);
      }
    }

    @Override
    protected String hashString() {
      StringBuilder sb = head();
      sb.append(source.incomingLdsNumber);
      sb.append(source.transportIdentifier);
      sb.append(weights.gross);
      sb.append(weights.tare);
      if (SAMPLE_SCALE.equals(eventType) && weights.sampleDeduction != null) {
        sb.append(weights.sampleDeduction);
      }
      sb.append(source.source);
      sb.append(source.cutblockId);
      sb.append(fieldScaleFlag);
      if (fieldScaleDeckId != null) {
        sb.append(fieldScaleDeckId);
      }
      sb.append(populationStrataYear);
      sb.append(sampleType);
      if (REMOTE_SAMPLE_SCALE.equals(eventType) || PRIMARY_DEPARTURE.equals(eventType)) {
        sb.append(outgoingLdsNumber);
        sb.append(destination);
      }
      appendTail(sb);
      return sb.toString();
    }
  }

  /** {@code WSRDocument}: RT, RR. */
  public static final class RedTag extends WeighSlipDocument {
    public Parts.WeighSlipWeights weights;
    public Parts.WeighSlipSource source;
    public char fieldScaleFlag;
    public String fieldScaleDeckId;

    public boolean isFieldScale() {
      return fieldScaleFlag == 'Y';
    }

    @Override
    public String sourceCode() {
      return source.source;
    }

    @Override
    protected void checkFields() throws FormatEditException {
      switch (String.valueOf(eventType)) {
        case RED_TAG -> {
          Edits.assertNull(FormatEdit.WeighSlip.SAMPLE_DEDUCTION_NOT_PERMITTED, weights.sampleDeduction);
          Edits.assertNull(FormatEdit.WeighSlip.OUTGOING_LDS_NUMBER_NOT_PERMITTED, outgoingLdsNumber);
          Edits.assertNull(FormatEdit.WeighSlip.DESTINATION_NOT_PERMITTED, destination);
        }
        case REMOTE_RED_TAG -> {
          Edits.assertNull(FormatEdit.WeighSlip.SAMPLE_DEDUCTION_NOT_PERMITTED, weights.sampleDeduction);
          Edits.assertNotNull(FormatEdit.WeighSlip.OUTGOING_LDS_NUMBER_REQUIRED, outgoingLdsNumber);
          Edits.assertNotNull(FormatEdit.WeighSlip.DESTINATION_REQUIRED, destination);
        }
        default -> throw unknown(eventType);
      }
    }

    @Override
    protected String hashString() {
      StringBuilder sb = head();
      sb.append(source.incomingLdsNumber);
      sb.append(source.transportIdentifier);
      sb.append(weights.gross);
      sb.append(weights.tare);
      sb.append(source.source);
      sb.append(source.cutblockId);
      sb.append(fieldScaleFlag);
      if (fieldScaleDeckId != null) {
        sb.append(fieldScaleDeckId);
      }
      sb.append(populationStrataYear);
      if (REMOTE_RED_TAG.equals(eventType)) {
        sb.append(outgoingLdsNumber);
        sb.append(destination);
      }
      appendTail(sb);
      return sb.toString();
    }
  }

  /** {@code WSODocument}: 4R, DP, CU. */
  public static final class Other extends WeighSlipDocument {
    public String incomingLdsNumber;
    public String transportIdentifier;
    public String source;

    @Override
    public String sourceCode() {
      return source;
    }

    @Override
    protected void checkFields() throws FormatEditException {
      switch (String.valueOf(eventType)) {
        case SB4_RESCALE -> {
          Edits.assertNotNull(FormatEdit.WeighSlip.INCOMING_LDS_NUMBER_REQUIRED, incomingLdsNumber);
          Edits.assertNotNull(FormatEdit.WeighSlip.TRANSPORT_IDENTIFIER_REQUIRED, transportIdentifier);
          Edits.assertNotNull(FormatEdit.WeighSlip.SOURCE_REQUIRED, source);
          Edits.assertNotNull(FormatEdit.WeighSlip.PSY_REQUIRED, populationStrataYear);
          Edits.assertNull(FormatEdit.WeighSlip.OUTGOING_LDS_NUMBER_NOT_PERMITTED, outgoingLdsNumber);
          Edits.assertNull(FormatEdit.WeighSlip.DESTINATION_NOT_PERMITTED, destination);
        }
        case DEPARTURE -> {
          Edits.assertNull(FormatEdit.WeighSlip.INCOMING_LDS_NUMBER_NOT_PERMITTED, incomingLdsNumber);
          Edits.assertNotNull(FormatEdit.WeighSlip.TRANSPORT_IDENTIFIER_REQUIRED, transportIdentifier);
          Edits.assertNull(FormatEdit.WeighSlip.SOURCE_NOT_PERMITTED, source);
          Edits.assertNotNull(FormatEdit.WeighSlip.PSY_REQUIRED, populationStrataYear);
          Edits.assertNotNull(FormatEdit.WeighSlip.OUTGOING_LDS_NUMBER_REQUIRED, outgoingLdsNumber);
          Edits.assertNotNull(FormatEdit.WeighSlip.DESTINATION_REQUIRED, destination);
        }
        case COMPANY_USE -> {
          Edits.assertNull(FormatEdit.WeighSlip.INCOMING_LDS_NUMBER_NOT_PERMITTED, incomingLdsNumber);
          Edits.assertNull(FormatEdit.WeighSlip.TRANSPORT_IDENTIFIER_NOT_PERMITTED, transportIdentifier);
          Edits.assertNull(FormatEdit.WeighSlip.SOURCE_NOT_PERMITTED, source);
          Edits.assertNull(FormatEdit.WeighSlip.PSY_NOT_PERMITTED, populationStrataYear);
          Edits.assertNull(FormatEdit.WeighSlip.OUTGOING_LDS_NUMBER_NOT_PERMITTED, outgoingLdsNumber);
          Edits.assertNull(FormatEdit.WeighSlip.DESTINATION_NOT_PERMITTED, destination);
        }
        default -> throw unknown(eventType);
      }
    }

    @Override
    protected String hashString() {
      StringBuilder sb = head();
      switch (eventType) {
        case SB4_RESCALE -> {
          sb.append(incomingLdsNumber);
          sb.append(transportIdentifier);
          sb.append(source);
          sb.append(populationStrataYear);
        }
        case DEPARTURE -> {
          sb.append(transportIdentifier);
          sb.append(populationStrataYear);
          sb.append(outgoingLdsNumber);
          sb.append(destination);
        }
        default -> {
          // COMPANY_USE: nothing between the time and the tail
        }
      }
      appendTail(sb);
      return sb.toString();
    }
  }
}
