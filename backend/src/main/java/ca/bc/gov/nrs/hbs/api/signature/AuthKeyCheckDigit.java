package ca.bc.gov.nrs.hbs.api.signature;

/**
 * Check digit appended to a requested scaler authentication key — legacy
 * {@code ca.bc.gov.mof.hbs.util.math.CheckDigit.calculateCheckDigit}, called
 * by {@code P362Action.suffixAuthKey(scalerLicence, requestedKey)} on
 * {@code scalerLicence + requestedKey}: {@code 'A' + (Σ char × position) mod 26}.
 * Scale software recomputes it, so it must not change.
 */
public final class AuthKeyCheckDigit {

  private AuthKeyCheckDigit() {
  }

  public static char of(String domain) {
    int sum = 0;
    for (int x = 0; x < domain.length(); x++) {
      sum += domain.charAt(x) * (x + 1);
    }
    return (char) (65 + (sum % 26));
  }

  /** {@code P362Action.suffixAuthKey}: requested key + check digit of licence + requested key. */
  public static String suffix(String scalerLicence, String requestedKey) {
    return requestedKey + of(scalerLicence + requestedKey);
  }
}
