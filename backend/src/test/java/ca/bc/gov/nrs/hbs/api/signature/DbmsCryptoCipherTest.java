package ca.bc.gov.nrs.hbs.api.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * DBMS_CRYPTO.encrypt(src, 4096+256+1, key) = AES-128-CBC, PKCS5, zero IV.
 * Expected values are independent of this code: FIPS-197 appendix C.1 (first
 * block, zero IV ⇒ CBC = ECB) and {@code openssl enc -aes-128-cbc -K … -iv 0…0}.
 * Still to confirm against Oracle on TEST (docs/questions.md Q9).
 */
class DbmsCryptoCipherTest {

  private static final HexFormat HEX = HexFormat.of();

  @Test
  void firstBlockMatchesFips197() {
    var cipher = new DbmsCryptoCipher("000102030405060708090a0b0c0d0e0f");
    byte[] out = cipher.encryptRaw(HEX.parseHex("00112233445566778899aabbccddeeff"));
    assertThat(out).hasSize(32); // one data block + one PKCS5 padding block
    assertThat(HEX.formatHex(out, 0, 16)).isEqualTo("69c4e0d86a7b0430d8cdb78070b4c55a");
  }

  @ParameterizedTest
  @CsvSource({
      "000102030405060708090a0b0c0d0e0f, ABCDEFGE, 530825426ad591f45c783f5b9cc0cf34",
      "2b7e151628aed2a6abf7158809cf4f3c, KEY12345T, 15a9856fc372c0ce50935c7905e996ca",
      "2b7e151628aed2a6abf7158809cf4f3c, 0123456789ABCDEF, 0757816030cb78c3b96e6fe53e595df2e38865214bb8b0a6167a972ac2930f3f",
  })
  void matchesOpensslAes128CbcZeroIv(String key, String plain, String expectedHex) {
    var cipher = new DbmsCryptoCipher(key);
    byte[] out = cipher.encrypt(plain);
    assertThat(HEX.formatHex(out)).isEqualTo(expectedHex);
    assertThat(cipher.decrypt(out)).isEqualTo(plain);
  }

  @Test
  void deterministicLikeOracleWithoutIv() {
    var cipher = new DbmsCryptoCipher("2B7E151628AED2A6ABF7158809CF4F3C");
    assertThat(cipher.encrypt("ABC1234XQ")).isEqualTo(cipher.encrypt("ABC1234XQ"));
    // a 10-character key (9 + check digit) fits RAW(64) easily
    assertThat(cipher.encrypt("ABCDEFGHIJ")).hasSize(16);
  }

  @Test
  void longerKeyUsesFirst16Bytes() {
    var a = new DbmsCryptoCipher("2b7e151628aed2a6abf7158809cf4f3c");
    var b = new DbmsCryptoCipher("2b7e151628aed2a6abf7158809cf4f3cffeeddccbbaa99887766554433221100");
    assertThat(b.encrypt("KEY12345T")).isEqualTo(a.encrypt("KEY12345T"));
  }

  @Test
  void rejectsShortOrNonHexKeys() {
    assertThatThrownBy(() -> new DbmsCryptoCipher("0011")).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("shorter");
    assertThatThrownBy(() -> new DbmsCryptoCipher("not-a-hex-key-at-all-not-a-hex-k"))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("hex");
  }
}
