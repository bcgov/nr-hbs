package ca.bc.gov.nrs.hbs.api.query;

import java.util.ArrayList;
import java.util.List;

/**
 * A whitelisted write: one call to a legacy {@code HBS_CREATE_* / HBS_STORE_* /
 * HBS_REMOVE_*} table-API procedure. The legacy app's DB role only had SELECT on
 * the HBS tables — every write went through these procs — so the new app keeps
 * that contract (see docs/database.md).
 *
 * <p>Arguments are positional, in the proc's declared order. Audit columns,
 * timestamps, surrogate keys and the caller's client number are filled
 * server-side, never from the request body.
 *
 * @param id           stable id the SPA posts to, e.g. {@code markHolderProfile.update}
 * @param legacyScreen legacy screen id
 * @param capability   who may run it
 * @param procedure    fully-qualified proc, e.g. {@code THE.HBS_STORE_HBS_MARK_PROFILE}
 * @param args         positional argument sources
 */
public record CommandDefinition(
    String id,
    String legacyScreen,
    Capability capability,
    String procedure,
    List<Arg> args,
    String existingRowSql) {

  /** Where each positional argument's value comes from. */
  public enum Source {
    /** {@code body[name]} converted by {@link QueryFilter.Type}. */
    BODY,
    /** {@code <name>.NEXTVAL} — a new surrogate key, echoed back in the response. */
    SEQUENCE,
    /** The caller's legacy audit user id (IDIR\X / BCEID\Y). */
    AUDIT_USER,
    /** Current timestamp. */
    NOW,
    /** A literal constant (e.g. a state code a legacy action always set). */
    CONSTANT,
    /** The caller's active client number (industry) or the body value (ministry). */
    CLIENT_NUMBER,
    /**
     * Read from the record's CURRENT database row ({@code existingRowSql}),
     * never from the request — for immutable columns the legacy STORE procs
     * overwrite on every update (ENTRY_USERID / ENTRY_TIMESTAMP, keys, ids).
     */
    EXISTING
  }

  public record Arg(Source source, String name, QueryFilter.Type type, boolean required) {}

  public static Builder builder(String id) {
    return new Builder(id);
  }

  public static final class Builder {
    private final String id;
    private String legacyScreen = "";
    private Capability capability;
    private String procedure;
    private final List<Arg> args = new ArrayList<>();
    private String existingRowSql;

    private Builder(String id) {
      this.id = id;
    }

    public Builder legacy(String s) { this.legacyScreen = s; return this; }
    public Builder capability(Capability c) { this.capability = c; return this; }
    public Builder procedure(String p) { this.procedure = p; return this; }

    public Builder body(String name, QueryFilter.Type type) { args.add(new Arg(Source.BODY, name, type, false)); return this; }
    public Builder requiredBody(String name, QueryFilter.Type type) { args.add(new Arg(Source.BODY, name, type, true)); return this; }
    public Builder text(String name) { return body(name, QueryFilter.Type.STRING); }
    public Builder upper(String name) { return body(name, QueryFilter.Type.UPPER); }
    public Builder number(String name) { return body(name, QueryFilter.Type.NUMBER); }
    public Builder date(String name) { return body(name, QueryFilter.Type.DATE); }
    public Builder sequence(String sequenceName) { args.add(new Arg(Source.SEQUENCE, sequenceName, QueryFilter.Type.NUMBER, true)); return this; }
    public Builder auditUser() { args.add(new Arg(Source.AUDIT_USER, "auditUser", QueryFilter.Type.STRING, true)); return this; }
    public Builder now() { args.add(new Arg(Source.NOW, "now", QueryFilter.Type.DATE, true)); return this; }
    public Builder constant(String value) { args.add(new Arg(Source.CONSTANT, value, QueryFilter.Type.STRING, true)); return this; }
    public Builder clientNumber(String name) { args.add(new Arg(Source.CLIENT_NUMBER, name, QueryFilter.Type.STRING, true)); return this; }
    /** A column of the current row (camelCase alias in {@link #existingRow}). */
    public Builder existing(String name) { args.add(new Arg(Source.EXISTING, name, QueryFilter.Type.STRING, true)); return this; }
    /**
     * SELECT returning the record's current immutable columns, binding named
     * parameters from the request body (its keys), e.g.
     * {@code SELECT entry_userid, entry_timestamp FROM hbs_mark_holder_profile
     * WHERE mhprof_id = :mhprofId}. Required when any {@link #existing} arg is used;
     * a missing row is a 404.
     */
    public Builder existingRow(String sql) { this.existingRowSql = sql; return this; }

    public CommandDefinition build() {
      if (capability == null || procedure == null) {
        throw new IllegalStateException("Command " + id + " needs a capability and procedure");
      }
      if (!procedure.matches("[A-Z0-9_$.]+")) {
        throw new IllegalStateException("Command " + id + " has an invalid procedure name");
      }
      boolean usesExisting = args.stream().anyMatch(a -> a.source() == Source.EXISTING);
      if (usesExisting && (existingRowSql == null || !existingRowSql.stripLeading().toUpperCase().startsWith("SELECT"))) {
        throw new IllegalStateException("Command " + id + " uses existing() args but has no existingRow SELECT");
      }
      return new CommandDefinition(id, legacyScreen, capability, procedure, List.copyOf(args), existingRowSql);
    }
  }
}
