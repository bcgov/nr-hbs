package ca.bc.gov.nrs.hbs.api.scaledata;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * Opaque paging cursors (keyset, not offsets, so a page never repeats or skips
 * a row when new ones arrive). The client passes {@code next} back unchanged;
 * anything else is a 400.
 */
final class Cursors {

  private Cursors() {
  }

  /** A keyset position: a (Pacific) time and an id. */
  record Position(LocalDateTime at, long id) {
  }

  static String encode(LocalDateTime at, long id) {
    return b64("t1:" + at + ":" + id);
  }

  static Position decode(String cursor) {
    String raw = unb64(cursor);
    int first = raw.indexOf(':');
    int last = raw.lastIndexOf(':');
    if (!raw.startsWith("t1:") || first == last) {
      throw invalid();
    }
    try {
      return new Position(LocalDateTime.parse(raw.substring(first + 1, last)), Long.parseLong(raw.substring(last + 1)));
    } catch (DateTimeParseException | NumberFormatException ex) {
      throw invalid();
    }
  }

  static String encodeId(long id) {
    return b64("i1:" + id);
  }

  static long decodeId(String cursor) {
    String raw = unb64(cursor);
    if (!raw.startsWith("i1:")) {
      throw invalid();
    }
    try {
      return Long.parseLong(raw.substring(3));
    } catch (NumberFormatException ex) {
      throw invalid();
    }
  }

  private static String b64(String s) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
  }

  private static String unb64(String cursor) {
    if (cursor == null || cursor.length() > 200) {
      throw invalid();
    }
    try {
      return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException ex) {
      throw invalid();
    }
  }

  private static ScaleDataApiException invalid() {
    return ScaleDataApiException.badRequest("The cursor is not valid. Pass back the 'next' value exactly as received.");
  }
}
