package ca.bc.gov.nrs.hbs.api.signature;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Java equivalent of the Oracle call the legacy app used for scaler
 * authentication keys ({@code $E/cpm/HbsScalerAuthKeyQuery.createByPK /
 * updateByPK / selectByPK}):
 *
 * <pre>
 *   DBMS_CRYPTO.encrypt(UTL_RAW.CAST_TO_RAW(:plainKey), 4096 + 256 + 1, '&lt;dbms-crypto-key&gt;')
 *   UTL_RAW.CAST_TO_VARCHAR2(DBMS_CRYPTO.decrypt(encrypted_authentication_key, 4096 + 256 + 1, '&lt;key&gt;'))
 * </pre>
 *
 * <ul>
 *   <li>typ 4096 + 256 + 1 = {@code PAD_PKCS5 + CHAIN_CBC + ENCRYPT_AES128}
 *       → {@code AES/CBC/PKCS5Padding};</li>
 *   <li>no {@code iv} argument → DBMS_CRYPTO uses an all-zero IV, so the
 *       output is deterministic (re-encrypting the decrypted key in the legacy
 *       {@code updateByPK} rewrote the same bytes);</li>
 *   <li>the key is a VARCHAR2 literal bound to the RAW {@code key} parameter,
 *       which Oracle converts implicitly with HEXTORAW — the preference is a
 *       hex string. AES128 uses 16 bytes; a longer key is truncated to its
 *       first 16 bytes (Oracle behaviour, to confirm on TEST — see below);</li>
 *   <li>{@code UTL_RAW.CAST_TO_RAW} takes the database-charset bytes of the
 *       text; keys are upper-case A–Z/0–9 (P362 validation), identical in
 *       ASCII and AL32UTF8.</li>
 * </ul>
 *
 * <p><b>To verify on TEST</b> (docs/questions.md Q9): encrypt a known key here
 * and compare with {@code SELECT DBMS_CRYPTO.encrypt(UTL_RAW.CAST_TO_RAW('ABC1234X'),
 * 4353, '<key>') FROM dual}, and decrypt an existing row — legacy batch B1021
 * verifies FTP and web submissions against these same rows.
 */
public final class DbmsCryptoCipher {

  /** {@code DBMS_CRYPTO.ENCRYPT_AES128 + CHAIN_CBC + PAD_PKCS5}. */
  public static final int TYP = 4096 + 256 + 1;

  private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
  private static final int AES128_KEY_BYTES = 16;

  private final SecretKeySpec key;

  /** @param hexKey the legacy WebADE {@code dbms-crypto-key} preference value (hex) */
  public DbmsCryptoCipher(String hexKey) {
    byte[] raw;
    try {
      raw = HexFormat.of().parseHex(hexKey.trim());
    } catch (IllegalArgumentException ex) {
      // Oracle raises ORA-06502 (hex to raw conversion) for a non-hex key.
      throw new IllegalStateException("hbs.signature.dbms-crypto-key is not a hex string", ex);
    }
    if (raw.length < AES128_KEY_BYTES) {
      // DBMS_CRYPTO: ORA-28234 key length too short.
      throw new IllegalStateException("hbs.signature.dbms-crypto-key is shorter than 16 bytes (32 hex digits)");
    }
    this.key = new SecretKeySpec(Arrays.copyOf(raw, AES128_KEY_BYTES), "AES");
  }

  /** {@code DBMS_CRYPTO.encrypt(UTL_RAW.CAST_TO_RAW(text), 4353, key)}. */
  public byte[] encrypt(String text) {
    return encryptRaw(text.getBytes(StandardCharsets.UTF_8));
  }

  /** {@code UTL_RAW.CAST_TO_VARCHAR2(DBMS_CRYPTO.decrypt(raw, 4353, key))}. */
  public String decrypt(byte[] encrypted) {
    return new String(decryptRaw(encrypted), StandardCharsets.UTF_8);
  }

  /** {@code DBMS_CRYPTO.encrypt(src, 4353, key)} on raw bytes. */
  public byte[] encryptRaw(byte[] src) {
    return run(Cipher.ENCRYPT_MODE, src);
  }

  public byte[] decryptRaw(byte[] src) {
    return run(Cipher.DECRYPT_MODE, src);
  }

  private byte[] run(int mode, byte[] src) {
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(mode, key, new IvParameterSpec(new byte[16]));
      return cipher.doFinal(src);
    } catch (GeneralSecurityException ex) {
      // Never include the key or the data in the message.
      throw new IllegalStateException("DBMS_CRYPTO-compatible " + (mode == Cipher.ENCRYPT_MODE ? "encrypt" : "decrypt")
          + " failed: " + ex.getClass().getSimpleName(), ex);
    }
  }
}
