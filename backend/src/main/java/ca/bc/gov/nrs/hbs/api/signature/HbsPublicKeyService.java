package ca.bc.gov.nrs.hbs.api.signature;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * "Download HBS Public Key" — port of {@code $V/presentation/signature/
 * PublicKeyMarshaller} over {@code $E/infrastructure/signature/
 * DigitalSignatureService.getDownloadablePublicKeys()}: every RSA1024 key in
 * {@code HBS_ENCRYPTION_KEY} whose alias has a certificate in the HBS signing
 * keystore, as the {@code HBS_Public_Key.xsd} document scale software uses to
 * encrypt submission signatures (identifier, description, signing date window,
 * modulus and exponent in upper-case hex).
 *
 * <p>Only certificates (public keys) are read from the keystore; private keys
 * stay untouched (verification is legacy batch B1021's job).
 */
@Service
@Slf4j
public class HbsPublicKeyService {

  /** Legacy {@code DigitalSignature.SIGN_DIGEST_METHOD_RSA1024}. */
  static final String METHOD_RSA1024 = "RSA1024";
  public static final String FILE_NAME = "HBS_Public_Key.xml";
  static final String SCHEMA = "HBS_Public_Key.xsd";
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  /** {@code HBS_ENCRYPTION_KEY} row (legacy {@code HbsEncryptionKey}). */
  public record EncryptionKey(long id, String description, String alias, Timestamp effectiveDate,
      Timestamp expiryDate) {}

  /** One {@code KeyValue} element (legacy {@code DownloadablePublicKey}). */
  public record DownloadablePublicKey(String identifier, String description, String fromSigningDate,
      String toSigningDate, String modulus, String exponent) {}

  /** Certificates by alias (the keystore in production; a map in tests). */
  @FunctionalInterface
  public interface CertificateSource {
    Certificate certificate(String alias) throws GeneralSecurityException;
  }

  private final NamedParameterJdbcTemplate jdbc;
  private final SignatureKeyProperties keys;
  private volatile CertificateSource keystore;

  public HbsPublicKeyService(NamedParameterJdbcTemplate jdbc, SignatureKeyProperties keys) {
    this.jdbc = jdbc;
    this.keys = keys;
  }

  /** The HBS_Public_Key.xml document; 503 when the keystore is not configured. */
  public byte[] publicKeyXml() {
    if (!keys.keystoreConfigured()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, SignatureKeyProperties.NOT_CONFIGURED_KEYSTORE);
    }
    return marshal(downloadableKeys(encryptionKeys(), keystore()));
  }

  /** {@code HbsEncryptionKeyQuery.selectByMethod("RSA1024")}, in id order (legacy HashMap order). */
  List<EncryptionKey> encryptionKeys() {
    return jdbc.query("""
        SELECT hek.hbs_encr_key_id, hek.description, hek.keystore_alias, hek.effective_date, hek.expiry_date
          FROM hbs_encryption_key hek
          JOIN hbs_cryptography_method hcm ON hcm.hbs_crypt_method_id = hek.hbs_crypt_method_id
         WHERE hcm.description = :method
         ORDER BY hek.hbs_encr_key_id""",
        new MapSqlParameterSource("method", METHOD_RSA1024),
        (rs, i) -> new EncryptionKey(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4),
            rs.getTimestamp(5)));
  }

  /** {@code DigitalSignatureService.getDownloadablePublicKeys}: keys without a certificate are skipped. */
  static List<DownloadablePublicKey> downloadableKeys(List<EncryptionKey> rows, CertificateSource certs) {
    List<DownloadablePublicKey> out = new ArrayList<>();
    for (EncryptionKey k : rows) {
      Certificate cert;
      try {
        cert = certs.certificate(k.alias());
      } catch (GeneralSecurityException ex) {
        throw new IllegalStateException("Cannot read the HBS signing keystore", ex);
      }
      if (cert == null || !(cert.getPublicKey() instanceof RSAPublicKey rsa)) {
        continue;
      }
      String modulus = rsa.getModulus().toString(16).toUpperCase(Locale.ROOT);
      String exponent = rsa.getPublicExponent().toString(16).toUpperCase(Locale.ROOT);
      if (exponent.length() % 2 != 0) {
        exponent = "0" + exponent; // legacy: "adding 0 if the digit count is odd"
      }
      out.add(new DownloadablePublicKey(String.format("%02d", k.id()), k.description(),
          DAY.format(k.effectiveDate().toLocalDateTime()), DAY.format(k.expiryDate().toLocalDateTime()),
          modulus, exponent));
    }
    return out;
  }

  /** The Castor output of {@code publickey_mapping.xml} (no namespace, schema location HBS_Public_Key.xsd). */
  static byte[] marshal(List<DownloadablePublicKey> keys) {
    StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        .append("<HBS_Public_Key xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
        .append("xsi:noNamespaceSchemaLocation=\"").append(SCHEMA).append("\">");
    for (DownloadablePublicKey k : keys) {
      xml.append("<KeyValue>");
      element(xml, "Identifier", k.identifier());
      element(xml, "Description", k.description());
      element(xml, "FromSigningDate", k.fromSigningDate());
      element(xml, "ToSigningDate", k.toSigningDate());
      element(xml, "PublicKey_Modulus", k.modulus());
      element(xml, "PublicKey_Exponent", k.exponent());
      xml.append("</KeyValue>");
    }
    xml.append("</HBS_Public_Key>");
    return xml.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static void element(StringBuilder xml, String name, String value) {
    if (value == null) return; // Castor omits null fields
    xml.append('<').append(name).append('>').append(escape(value)).append("</").append(name).append('>');
  }

  private static String escape(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private CertificateSource keystore() {
    CertificateSource local = keystore;
    if (local == null) {
      synchronized (this) {
        local = keystore;
        if (local == null) {
          local = load(Path.of(keys.keystorePath()), keys.keystoreType(), keys.keystorePassword());
          keystore = local;
        }
      }
    }
    return local;
  }

  /** {@code DigitalSignatureService.init}: KeyStore.getInstance("JKS").load(file, password). */
  static CertificateSource load(Path path, String type, char[] password) {
    try (InputStream in = Files.newInputStream(path)) {
      KeyStore ks = KeyStore.getInstance(type);
      ks.load(in, password);
      log.info("HBS signing keystore loaded ({} entries)", ks.size());
      return ks::getCertificate;
    } catch (IOException | GeneralSecurityException ex) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "The HBS public key is not available: the HBS signing keystore could not be read ("
              + ex.getClass().getSimpleName() + ").", ex);
    }
  }
}
