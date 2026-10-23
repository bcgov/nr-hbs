package ca.bc.gov.nrs.hbs.api.intake.xml;

import java.util.Objects;

/** The legacy {@code dac.detail.xml.ValidationHelper} assertions. */
public final class Edits {

  private Edits() {
  }

  public static void fail(String edit) throws FormatEditException {
    throw new FormatEditException(edit);
  }

  public static void assertEquals(String edit, long expected, long actual) throws FormatEditException {
    if (expected != actual) {
      fail(edit);
    }
  }

  /** Float comparison with a tolerance, as {@code assertEquals(String, float, float, float)}. */
  public static void assertEquals(String edit, float expected, float actual, float delta)
      throws FormatEditException {
    if (Math.abs(expected - actual) > Math.abs(delta)) {
      fail(edit);
    }
  }

  public static void assertEquals(String edit, Object expected, Object actual) throws FormatEditException {
    if (!Objects.equals(expected, actual)) {
      fail(edit);
    }
  }

  public static void assertNull(String edit, Object actual) throws FormatEditException {
    if (actual != null) {
      fail(edit);
    }
  }

  public static void assertNotNull(String edit, Object actual) throws FormatEditException {
    if (actual == null) {
      fail(edit);
    }
  }
}
