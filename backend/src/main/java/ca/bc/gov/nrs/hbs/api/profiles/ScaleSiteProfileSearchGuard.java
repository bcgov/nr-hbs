package ca.bc.gov.nrs.hbs.api.profiles;

import ca.bc.gov.nrs.hbs.api.legacyrules.Inputs;
import ca.bc.gov.nrs.hbs.api.query.QueryRegistry;
import ca.bc.gov.nrs.hbs.api.query.QueryService;
import ca.bc.gov.nrs.hbs.api.query.Viewer;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.util.HashMap;
import java.util.Map;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The P320 "Search for Scale Site Profiles" pre-checks ({@code $V/cpm/P320Action.perform},
 * actionType Submit) on {@code profiles.scaleSites.search}: the legacy screen
 * answered a search it could not satisfy with a message instead of an empty
 * list. An {@code @Before} advice on {@code QueryService} (as
 * {@code submissions.SearchCriteriaGuard}), so the messages come back as a 400.
 *
 * <ul>
 *   <li>By Scale Site: the site must exist ({@code error.scales.invalid2}).</li>
 *   <li>By Trading Partner / By Site Owner ({@code validateClientInput}): client
 *       number and location both given ({@code error.clientnumber.required}); the
 *       client exists ({@code error.clientnumloc.invalid}); the client location is a
 *       TRADING_PARTNER ({@code error.clientnumloc.notSetup}); and there is at least one
 *       profile — HBS2R321's count for the partner, or for the sites the owner owns
 *       ({@code error.tradingpartnersubmitter.notexists}).</li>
 * </ul>
 */
@Aspect
@Component
public class ScaleSiteProfileSearchGuard {

  static final String QUERY_ID = "profiles.scaleSites.search";
  /** error.scales.invalid2 */
  static final String SCALE_SITE_NOT_FOUND = "The scale site entered does not exist.";
  /** error.clientnumber.required */
  static final String CLIENT_AND_LOCATION_REQUIRED = "Client number and location are required.";
  /** error.clientnumloc.invalid */
  static final String CLIENT_NOT_FOUND = "Client does not exist.";
  /** error.clientnumloc.notSetup */
  static final String NOT_TRADING_PARTNER = "Client has not been setup as trading partner.";
  /** error.tradingpartnersubmitter.notexists */
  static final String NO_PROFILES = "This client has no current scale site profiles";

  /** The look-ups and the profile count, separated for tests. */
  public interface Lookups {
    boolean scaleSiteExists(String scaleSite);

    boolean clientExists(String clientNumber);

    boolean tradingPartnerExists(String clientNumber, String location);

    /** Rows {@code profiles.scaleSites.search} returns for these criteria (HBS2R321 count). */
    long profileCount(Map<String, String> criteria);
  }

  private final Lookups lookups;
  /** Checks the caller may run the query before any look-up answers (null in unit tests). */
  private final Runnable authorize;

  @org.springframework.beans.factory.annotation.Autowired
  public ScaleSiteProfileSearchGuard(NamedParameterJdbcTemplate jdbc, @Lazy QueryService queries,
      @Lazy QueryRegistry registry) {
    this(new Lookups() {
      @Override
      public boolean scaleSiteExists(String scaleSite) {
        return count(jdbc, "SELECT COUNT(*) FROM scale_site WHERE scale_site_id_nmbr = :v",
            new MapSqlParameterSource("v", scaleSite)) > 0;
      }

      @Override
      public boolean clientExists(String clientNumber) {
        // ClientReader.findClientByNumber (FOREST_CLIENT; read through V_CLIENT_PUBLIC, database.md).
        return count(jdbc, "SELECT COUNT(*) FROM v_client_public WHERE client_number = :v",
            new MapSqlParameterSource("v", clientNumber)) > 0;
      }

      @Override
      public boolean tradingPartnerExists(String clientNumber, String location) {
        // ClientProfileManager.findTradingPartnerByPrimaryKey
        return count(jdbc,
            "SELECT COUNT(*) FROM trading_partner WHERE client_number = :c AND client_locn_code = :l",
            new MapSqlParameterSource("c", clientNumber).addValue("l", location)) > 0;
      }

      @Override
      public long profileCount(Map<String, String> criteria) {
        // Not through QueryService.count(String, …): that would re-enter this advice.
        return queries.count(registry.query(QUERY_ID), Viewer.current(), criteria);
      }
    }, () -> registry.query(QUERY_ID).capability().require());
  }

  ScaleSiteProfileSearchGuard(Lookups lookups, Runnable authorize) {
    this.lookups = lookups;
    this.authorize = authorize;
  }

  private static long count(NamedParameterJdbcTemplate jdbc, String sql, MapSqlParameterSource p) {
    Long n = jdbc.queryForObject(sql, p, Long.class);
    return n == null ? 0 : n;
  }

  @Before("execution(* ca.bc.gov.nrs.hbs.api.query.QueryService.*(String, java.util.Map, ..))")
  public void guard(JoinPoint jp) {
    Object[] args = jp.getArgs();
    if (args.length < 2 || !QUERY_ID.equals(args[0]) || !(args[1] instanceof Map<?, ?> raw)) {
      return;
    }
    @SuppressWarnings("unchecked")
    Map<String, ?> params = (Map<String, ?>) raw;
    authorize.run();
    validate(params);
  }

  /** P320Action: the first search type given decides, as the legacy radio did. */
  void validate(Map<String, ?> p) {
    String site = Inputs.upper(p, "scaleSiteId");
    String tpClient = Inputs.text(p, "clientNumber");
    String tpLoc = Inputs.upper(p, "clientLocnCode");
    String ownerClient = Inputs.text(p, "ownerClientNumber");
    String ownerLoc = Inputs.upper(p, "ownerClientLocnCode");
    if (site != null) {
      if (!lookups.scaleSiteExists(site)) {
        throw new WorkflowValidationException("scaleSiteId", SCALE_SITE_NOT_FOUND);
      }
      return;
    }
    if (tpClient != null || tpLoc != null) {
      client(tpClient, tpLoc, "clientNumber");
      Map<String, String> criteria = new HashMap<>();
      criteria.put("clientNumber", tpClient);
      criteria.put("clientLocnCode", tpLoc);
      if (lookups.profileCount(criteria) == 0) {
        throw new WorkflowValidationException("clientNumber", NO_PROFILES);
      }
      return;
    }
    if (ownerClient != null || ownerLoc != null) {
      client(ownerClient, ownerLoc, "ownerClientNumber");
      Map<String, String> criteria = new HashMap<>();
      criteria.put("ownerClientNumber", ownerClient);
      criteria.put("ownerClientLocnCode", ownerLoc);
      if (lookups.profileCount(criteria) == 0) {
        throw new WorkflowValidationException("ownerClientNumber", NO_PROFILES);
      }
    }
  }

  /** P320Action.validateClientInput. */
  private void client(String number, String loc, String key) {
    if (number == null || loc == null) {
      throw new WorkflowValidationException(key, CLIENT_AND_LOCATION_REQUIRED);
    }
    if (!lookups.clientExists(number)) {
      throw new WorkflowValidationException(key, CLIENT_NOT_FOUND);
    }
    if (!lookups.tradingPartnerExists(number, loc)) {
      throw new WorkflowValidationException(key, NOT_TRADING_PARTNER);
    }
  }
}
