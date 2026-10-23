package ca.bc.gov.nrs.hbs.api.intake.xml;

import java.util.Date;

/**
 * The small value elements of the scale-data XML, one class each as in the
 * legacy Castor mapping ({@code Submission.mapping} / {@code
 * Submission_V3.mapping}). Field types are the legacy ones (primitive
 * {@code char}/{@code short}/{@code int} versus wrapper) because the hash
 * strings append them as such: an absent primitive is {@code 0}/{@code '\0'},
 * an absent wrapper or String is {@code null} (appended as "null").
 */
public final class Parts {

  private Parts() {
  }

  /** {@code <Software>}. */
  public static class Software {
    public String product;
    public String version;
    public String revision;
  }

  /** {@code <EC_Digest>}. V1/V2 carry a CRC32 {@code EC_HT}, V3 a SHA-256 {@code EC_HTX64}. */
  public static class Digest {
    public String method;
    public long hashTotal;
    public String ecHtx64;

    /** {@code Digest.getHashValue}. */
    public String hashValue() {
      return HashValues.CRC32.equals(method) ? Long.toString(hashTotal) : ecHtx64;
    }
  }

  /** {@code <EC_Signature>}. */
  public static class Signature {
    public String signingScalerLicence;
    public String signingDateIso;
    public String signingTimeIso;
    public String ecEncrSig;
    public String method;
    public String identifier;
    /** V1/V2 {@code EC_AKEHT}. */
    public Long authenticationKeyEncryptedHashTotal;

    /** {@code getSigningDateTime}: lenient "yyyy-MM-dd'T'HH:mm:ss" (null when unparseable). */
    public Date signingDateTime() {
      if (signingDateIso == null) {
        return null;
      }
      return LegacyDates.parseLenient(LegacyDates.DATE_TIME_ISO, signingDateIso + "T" + signingTimeIso);
    }

    /** {@code getSigningScalerLicence2}: only part of the hash string for CRC32 signatures. */
    public String signingScalerLicence2() {
      return HashValues.CRC32.equals(method) ? signingScalerLicence : "";
    }
  }

  /** {@code <SplitLoad>}. */
  public static class SplitLoad {
    public byte parcelId;
    public byte parcelCount;
  }

  /** {@code <CheckScale>}. */
  public static class CheckScale {
    public String originalScalerLicence;
    public short originalScalerReturnNumber;
    public char checkReplacesOriginal;

    public boolean isCheckReplacingOriginal() {
      return checkReplacesOriginal == 'Y';
    }
  }

  /** {@code <WSWeights>}. */
  public static class WeighSlipWeights {
    public int gross;
    public int tare;
    public Integer sampleDeduction;
  }

  /** {@code <WSSource>}. */
  public static class WeighSlipSource {
    public String source;
    public String incomingLdsNumber;
    public String transportIdentifier;
    public String cutblockId;
  }

  /** {@code <Arrival>} of an arrival ledger entry. */
  public static class Arrival {
    public String arrivalDateString;
    public String loadArrivalNumber;
    public String incomingLdsNumber;
    public String transportIdentifier;

    /** {@code getArrivalDate}: lenient yyyyMMdd (validated strictly first). */
    public Date arrivalDate() {
      return LegacyDates.parseLenient(LegacyDates.DATE_FORMAT_NO_TIME, arrivalDateString);
    }

    void validate() throws FormatEditException {
      try {
        LegacyDates.parseStrict(LegacyDates.DATE_FORMAT_NO_TIME, arrivalDateString);
      } catch (Exception ex) {
        throw new FormatEditException(FormatEdit.Ledger.ARRIVAL_DATE_VALID);
      }
    }
  }

  /** {@code <Scale>} of an arrival ledger entry. */
  public static class Scale {
    public String scaleDateString;
    public String primaryScalerLicence;
    public short primaryScalerReturnNumber;
    public SplitLoad splitLoad;

    public Date scaleDate() {
      return LegacyDates.parseLenient(LegacyDates.DATE_FORMAT_NO_TIME, scaleDateString);
    }

    void validate() throws FormatEditException {
      try {
        LegacyDates.parseStrict(LegacyDates.DATE_FORMAT_NO_TIME, scaleDateString);
      } catch (Exception ex) {
        Edits.fail(FormatEdit.Ledger.SCALE_DATE_VALID);
      }
    }
  }
}
