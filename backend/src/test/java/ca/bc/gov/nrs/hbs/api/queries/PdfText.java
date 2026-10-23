package ca.bc.gov.nrs.hbs.api.queries;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Minimal text probe for the PDFs FOP writes with the base-14 fonts (no PDFBox
 * in the build): inflates every Flate content stream and concatenates the
 * literal strings shown by Tj / TJ. Whitespace is dropped, because FOP spaces
 * words by positioning rather than by space characters.
 */
final class PdfText {

  private static final Pattern STREAM = Pattern.compile("stream\r?\n");
  private static final Pattern LITERAL = Pattern.compile("\\(((?:\\\\.|[^\\\\)])*)\\)");

  private PdfText() {
  }

  static String of(byte[] pdf) {
    String raw = new String(pdf, StandardCharsets.ISO_8859_1);
    StringBuilder text = new StringBuilder();
    Matcher m = STREAM.matcher(raw);
    while (m.find()) {
      int start = m.end();
      int end = raw.indexOf("endstream", start);
      if (end < 0) break;
      String content = inflate(pdf, start, end - start);
      if (content == null) continue;
      Matcher lit = LITERAL.matcher(content);
      while (lit.find()) {
        text.append(lit.group(1).replace("\\(", "(").replace("\\)", ")").replace("\\\\", "\\"));
      }
    }
    return text.toString().replaceAll("\\s+", "");
  }

  private static String inflate(byte[] pdf, int offset, int length) {
    Inflater inflater = new Inflater();
    inflater.setInput(pdf, offset, length);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    try {
      while (!inflater.finished()) {
        int n = inflater.inflate(buf);
        if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
        out.write(buf, 0, n);
      }
    } catch (DataFormatException ex) {
      return null; // not a Flate stream (e.g. the logo image)
    } finally {
      inflater.end();
    }
    return out.toString(StandardCharsets.ISO_8859_1);
  }
}
