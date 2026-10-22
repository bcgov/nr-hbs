package ca.bc.gov.nrs.hbs.api.legacyrules;

import org.mockito.Mockito;

/** Mocks whose unstubbed reads return null / false / 0 (Mockito's default empty Map would read as a found row). */
public final class NullMocks {

  private NullMocks() {
  }

  public static <T> T of(Class<T> type) {
    return Mockito.mock(type, invocation -> {
      Class<?> r = invocation.getMethod().getReturnType();
      if (r == boolean.class) return false;
      if (r == long.class) return 0L;
      if (r == int.class) return 0;
      return null;
    });
  }
}
