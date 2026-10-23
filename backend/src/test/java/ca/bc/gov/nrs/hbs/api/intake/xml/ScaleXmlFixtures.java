package ca.bc.gov.nrs.hbs.api.intake.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Test XML: loads a fixture from {@code src/test/resources/intake} and fills
 * each {@code @HASH@} with the SHA-256 hash total its document must carry, as
 * a vendor's scale software would.
 */
public final class ScaleXmlFixtures {

  private ScaleXmlFixtures() {
  }

  public static String raw(String name) {
    try (InputStream in = ScaleXmlFixtures.class.getResourceAsStream("/intake/" + name)) {
      if (in == null) {
        throw new IllegalArgumentException("No fixture " + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  /** The fixture with every placeholder hash replaced by its document's real hash total. */
  public static String signed(String name) {
    return sign(raw(name));
  }

  /** Replaces the {@code @HASH@} placeholders (document order) with the documents' hash totals. */
  public static String sign(String xml) {
    try {
      Submission s = new SubmissionUnmarshaller().unmarshal(xml.getBytes(StandardCharsets.UTF_8));
      List<Document> docs = new ArrayList<>();
      for (List<Submission.DocumentBatch> list : List.of(s.weighSlipBillables, s.weighSlipRedTags,
          s.weighSlipOthers, s.pieceScales, s.sampleScales, s.otherTallies, s.specialForestProducts)) {
        list.forEach(b -> docs.addAll(b.documents));
      }
      String out = xml;
      for (Document d : docs) {
        out = out.replaceFirst("@HASH@", d.hashValue());
      }
      return out;
    } catch (FormatEditException ex) {
      throw new IllegalStateException("Fixture does not unmarshal: " + ex.editNumber(), ex);
    }
  }

  public static byte[] bytes(String xml) {
    return xml.getBytes(StandardCharsets.UTF_8);
  }
}
