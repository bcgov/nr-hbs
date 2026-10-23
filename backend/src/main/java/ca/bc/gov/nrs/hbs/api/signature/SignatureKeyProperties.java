package ca.bc.gov.nrs.hbs.api.signature;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * The scaler-signature secrets the legacy app read from WebADE preferences in
 * {@code DigitalSignatureService.init()} ({@code $E/infrastructure/signature}):
 *
 * <ul>
 *   <li>{@code dbms-crypto-key} → {@code hbs.signature.dbms-crypto-key}: the
 *       DBMS_CRYPTO AES-128 key (hex) that encrypts
 *       {@code HBS_SCALER_AUTH_KEY.ENCRYPTED_AUTHENTICATION_KEY};</li>
 *   <li>{@code keystore-filename} / {@code keystore-password} →
 *       {@code hbs.signature.keystore-path} / {@code -password} (/{@code -type},
 *       JKS as legacy): the HBS RSA signing keystore whose certificates are the
 *       downloadable HBS public keys (aliases in HBS_ENCRYPTION_KEY).</li>
 * </ul>
 *
 * All optional: they come from an OpenShift Secret Security releases
 * (docs/questions.md Q9). Without them the scaler-key commands and the
 * public-key download answer 503; nothing is written. The values are never
 * logged and never put into SQL text (legacy inlined the key in SQL).
 */
@Component
public class SignatureKeyProperties {

  public static final String NOT_CONFIGURED_CRYPTO =
      "Scaler authentication keys can't be issued yet: the HBS key-encryption secret "
          + "(HBS_SIGNATURE_DBMS_CRYPTO_KEY) is not configured. Contact HBS support.";
  public static final String NOT_CONFIGURED_KEYSTORE =
      "The HBS public key is not available yet: the HBS signing keystore "
          + "(HBS_SIGNATURE_KEYSTORE_PATH / _PASSWORD) is not configured. Contact HBS support.";

  private final String dbmsCryptoKey;
  private final String keystorePath;
  private final String keystorePassword;
  private final String keystoreType;
  private final int authKeyAgingDays;

  public SignatureKeyProperties(
      @Value("${hbs.signature.dbms-crypto-key:}") String dbmsCryptoKey,
      @Value("${hbs.signature.keystore-path:}") String keystorePath,
      @Value("${hbs.signature.keystore-password:}") String keystorePassword,
      @Value("${hbs.signature.keystore-type:JKS}") String keystoreType,
      @Value("${hbs.signature.authkey-aging-days:365}") int authKeyAgingDays) {
    this.dbmsCryptoKey = trim(dbmsCryptoKey);
    this.keystorePath = trim(keystorePath);
    this.keystorePassword = keystorePassword == null ? "" : keystorePassword;
    this.keystoreType = trim(keystoreType).isEmpty() ? "JKS" : trim(keystoreType);
    this.authKeyAgingDays = authKeyAgingDays;
  }

  private static String trim(String s) {
    return s == null ? "" : s.trim();
  }

  public boolean cryptoKeyConfigured() {
    return !dbmsCryptoKey.isEmpty();
  }

  /**
   * A path is set and the file is there. OpenShift always sets the path; the
   * optional Secret volume leaves it absent until Security provides the
   * keystore.
   */
  public boolean keystoreConfigured() {
    return !keystorePath.isEmpty() && java.nio.file.Files.isRegularFile(java.nio.file.Path.of(keystorePath));
  }

  /** The DBMS_CRYPTO key, or 503 when it is not configured. */
  public String requireDbmsCryptoKey() {
    if (!cryptoKeyConfigured()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, NOT_CONFIGURED_CRYPTO);
    }
    return dbmsCryptoKey;
  }

  public String keystorePath() {
    return keystorePath;
  }

  public char[] keystorePassword() {
    return keystorePassword.toCharArray();
  }

  public String keystoreType() {
    return keystoreType;
  }

  /** Legacy {@code HBSConfiguration.getScalerAuthenticationKeyAgingDays()} ({@code authkey.aging.days=365}). */
  public int authKeyAgingDays() {
    return authKeyAgingDays;
  }

  @Override
  public String toString() {
    return "SignatureKeyProperties[cryptoKey=" + (cryptoKeyConfigured() ? "set" : "unset")
        + ", keystore=" + (keystoreConfigured() ? keystorePath : "unset") + "]";
  }
}
