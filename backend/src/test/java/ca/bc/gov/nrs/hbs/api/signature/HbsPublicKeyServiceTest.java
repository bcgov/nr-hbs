package ca.bc.gov.nrs.hbs.api.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ca.bc.gov.nrs.hbs.api.signature.HbsPublicKeyService.EncryptionKey;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

/** DownloadPublicKeyServlet / PublicKeyMarshaller output. */
class HbsPublicKeyServiceTest {

  /** A certificate holding just a public key (enough for getPublicKey()). */
  static Certificate cert(PublicKey key) {
    return new Certificate("X.509") {
      @Override public byte[] getEncoded() { return new byte[0]; }
      @Override public void verify(PublicKey k) { }
      @Override public void verify(PublicKey k, String p) { }
      @Override public String toString() { return "test"; }
      @Override public PublicKey getPublicKey() { return key; }
    };
  }

  static final List<EncryptionKey> ROWS = List.of(
      new EncryptionKey(1, "HBS key 2017", "hbs2017", Timestamp.valueOf("2017-01-01 00:00:00"),
          Timestamp.valueOf("2027-12-31 00:00:00")),
      new EncryptionKey(2, "Not in keystore", "missing", Timestamp.valueOf("2018-01-01 00:00:00"),
          Timestamp.valueOf("2028-12-31 00:00:00")));

  @Test
  void marshalsKeysWithCertificatesInLegacyFormat() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    RSAPublicKey pub = (RSAPublicKey) gen.generateKeyPair().getPublic();
    Map<String, Certificate> store = Map.of("hbs2017", cert(pub));

    var keys = HbsPublicKeyService.downloadableKeys(ROWS, store::get);
    assertThat(keys).hasSize(1);
    var k = keys.get(0);
    assertThat(k.identifier()).isEqualTo("01");
    assertThat(k.fromSigningDate()).isEqualTo("2017-01-01");
    assertThat(k.toSigningDate()).isEqualTo("2027-12-31");
    assertThat(k.modulus()).isEqualTo(pub.getModulus().toString(16).toUpperCase());
    assertThat(k.exponent()).isEqualTo("010001"); // 65537 = 0x10001, padded to even length

    String xml = new String(HbsPublicKeyService.marshal(keys), StandardCharsets.UTF_8);
    assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<HBS_Public_Key ")
        .contains("xsi:noNamespaceSchemaLocation=\"HBS_Public_Key.xsd\"")
        .contains("<KeyValue><Identifier>01</Identifier><Description>HBS key 2017</Description>"
            + "<FromSigningDate>2017-01-01</FromSigningDate><ToSigningDate>2027-12-31</ToSigningDate>"
            + "<PublicKey_Modulus>" + k.modulus() + "</PublicKey_Modulus>"
            + "<PublicKey_Exponent>010001</PublicKey_Exponent></KeyValue></HBS_Public_Key>");

    // round trip: the published values rebuild the same public key
    var dbf = DocumentBuilderFactory.newInstance();
    dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    var doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    String mod = doc.getElementsByTagName("PublicKey_Modulus").item(0).getTextContent();
    assertThat(new BigInteger(mod, 16)).isEqualTo(pub.getModulus());
  }

  @Test
  void notConfiguredIs503() {
    var service = new HbsPublicKeyService(null, new SignatureKeyProperties("", "", "", "JKS", 365));
    assertThatThrownBy(service::publicKeyXml).isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("503").hasMessageContaining("HBS_SIGNATURE_KEYSTORE_PATH");
  }

  /** A real JKS made by keytool (self-signed RSA-1024), password = alias as legacy. */
  @Test
  void readsCertificatesFromJksKeystore(@TempDir Path dir) throws Exception {
    Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
    assumeTrue(Files.isExecutable(keytool), "keytool not available");
    Path jks = dir.resolve("hbs.jks");
    Process p = new ProcessBuilder(keytool.toString(), "-genkeypair", "-keystore", jks.toString(),
        "-storetype", "JKS", "-storepass", "storepass", "-alias", "hbs2017", "-keypass", "hbs2017",
        "-keyalg", "RSA", "-keysize", "1024", "-dname", "CN=HBS Test", "-validity", "30")
        .redirectErrorStream(true).start();
    assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0, "keytool failed");

    var source = HbsPublicKeyService.load(jks, "JKS", "storepass".toCharArray());
    var keys = HbsPublicKeyService.downloadableKeys(ROWS, source);
    assertThat(keys).extracting(HbsPublicKeyService.DownloadablePublicKey::identifier).containsExactly("01");
    assertThat(keys.get(0).modulus()).hasSize(256); // 1024-bit modulus, 256 hex digits (Hex512Dig)

    assertThatThrownBy(() -> HbsPublicKeyService.load(jks, "JKS", "wrong".toCharArray()))
        .isInstanceOf(ResponseStatusException.class);
  }
}
