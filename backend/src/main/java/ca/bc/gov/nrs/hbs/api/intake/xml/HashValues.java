package ca.bc.gov.nrs.hbs.api.intake.xml;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Document hash totals ({@code util.DigitalSignature.getHashValue}): CRC32
 * (Howell's variant, schema V1/V2) or SHA-256 as upper-case hex (V3). The
 * hash string is turned into bytes with the legacy batch host's default
 * charset (windows-1252; the legacy code called {@code String.getBytes()}),
 * so documents with non-ASCII text hash the same as before.
 */
public final class HashValues {

  public static final String CRC32 = "CRC32";
  public static final String SHA256 = "SHA256";
  public static final String RSA1024 = "RSA1024";

  /** HBS_CRYPTOGRAPHY_METHOD ids (legacy {@code DigitalSignature.*_CRYPT_METHOD_ID}). */
  public static final long CRC32_CRYPT_METHOD_ID = 1L;
  public static final long SHA256_CRYPT_METHOD_ID = 2L;

  static final Charset LEGACY_CHARSET = Charset.forName("windows-1252");

  private HashValues() {
  }

  public static String hashValue(String method, String domain) {
    byte[] bytes = domain.getBytes(LEGACY_CHARSET);
    if (CRC32.equals(method)) {
      return Long.toString(howellCrc32(bytes));
    }
    try {
      return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /**
   * {@code util.math.HowellCRC32.crc32}: Gary Brown's CRC-32 table, starting
   * at 0, over printable ASCII (32..126) bytes only, returned as a signed int
   * widened to long.
   */
  static long howellCrc32(byte[] s) {
    int crc = 0;
    for (byte b : s) {
      if (b >= 32 && b <= 126) {
        crc = TABLE[(crc ^ b) & 0xff] ^ ((crc >>> 8) & 0x00FFFFFF);
      }
    }
    return crc;
  }

  private static final int[] TABLE = new int[256];

  static {
    // Same values as the legacy literal table (the standard reflected 0xEDB88320 table).
    for (int n = 0; n < 256; n++) {
      int c = n;
      for (int k = 0; k < 8; k++) {
        c = (c & 1) != 0 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
      }
      TABLE[n] = c;
    }
  }
}
