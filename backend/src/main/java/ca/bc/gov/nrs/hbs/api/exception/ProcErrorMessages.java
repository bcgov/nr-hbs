package ca.bc.gov.nrs.hbs.api.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Curated user-facing messages + HTTP status for the legacy proc error
 * codes and Oracle errors. Used by {@link RestExceptionHandler}, which
 * substring-matches against the Oracle error string to translate exceptions.
 *
 * <p>Single source of truth for the curated codes — add a new entry
 * here and both consumers pick it up.
 */
public final class ProcErrorMessages {

  private ProcErrorMessages() {}

  public record Info(HttpStatus status, String message) {}

  /** Look up the full info for a curated code, or {@code null} if unknown. */
  public static Info infoFor(String code) {
    if (code == null) return null;
    return CODES.get(code);
  }

  /** Look up the user-facing message, or {@code null} if the code isn't curated. */
  public static String messageFor(String code) {
    Info info = infoFor(code);
    return info == null ? null : info.message();
  }

  /**
   * Substring search across the curated codes — used by the exception
   * handler against full Oracle error strings that wrap the code in
   * package paths and call stacks. Returns the LONGEST (most specific)
   * matching code so overlapping codes resolve correctly: an Oracle
   * message must map to the most specific code that it contains.
   * CODES is an unordered map, so a plain first-match would be
   * non-deterministic whenever two keys both match.
   */
  public static String mostSpecificMatchedCode(String oracleMessage) {
    if (oracleMessage == null) return null;
    String best = null;
    for (String code : CODES.keySet()) {
      if (oracleMessage.contains(code)
          && (best == null || code.length() > best.length())) {
        best = code;
      }
    }
    return best;
  }

  /** Read-only view of every curated code → info pair. */
  public static Map<String, Info> all() {
    return CODES;
  }

  private static final Map<String, Info> CODES = Map.ofEntries(
      // The HBS_CREATE_* / HBS_STORE_* / HBS_REMOVE_* procs are thin
      // single-table wrappers (generated from the legacy CMP entity beans)
      // and raise almost no business codes of their own — failures surface
      // as raw ORA- errors. These are the ones a user can actually trigger.
      Map.entry("ORA-00001", new Info(CONFLICT,
          "A record with the same identifying values already exists.")),
      Map.entry("ORA-02291", new Info(BAD_REQUEST,
          "One of the referenced values (code, client, timber mark, scale site …) doesn't exist.")),
      Map.entry("ORA-02292", new Info(CONFLICT,
          "This record is still referenced by other records and can't be removed.")),
      Map.entry("ORA-01403", new Info(NOT_FOUND,
          "The requested record could not be found.")),
      Map.entry("ORA-01400", new Info(BAD_REQUEST,
          "A required value is missing.")),
      Map.entry("ORA-12899", new Info(BAD_REQUEST,
          "One of the values is too long for its field.")),
      // HBS_CREATE_DTL_SCL_DOC_TRANS — the same file submitted twice in one
      // transmission.
      Map.entry("This file is already a part of this transmission", new Info(CONFLICT,
          "This file has already been submitted as part of this transmission.")),
      // Optimistic locking — HBS rows carry REVISION_COUNT / UPDATE_TIMESTAMP
      // and the service layer raises this code when the row moved underneath
      // the caller (legacy "record modified by another user" message).
      Map.entry("hbs.record.modified", new Info(CONFLICT,
          "This record was modified by someone else after you opened it. "
              + "Reload and try again.")),
      Map.entry("hbs.no_access_right", new Info(FORBIDDEN,
          "You don't have access to this record.")));
}
