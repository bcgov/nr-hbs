package ca.bc.gov.nrs.hbs.api.catalog;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.query.CommandDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryCatalog;
import ca.bc.gov.nrs.hbs.api.query.QueryDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter.Type;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Administration area — what remains of legacy User Services (P009–P019)
 * once FAM owns identity, roles, enrolment and request approval: the HBS
 * data-domain maintenance (which districts / scale sites / client locations a
 * user's role applies to).
 *
 * <p>Client and SPC administrators only see users of their own client and
 * user type (legacy {@code searchUsersForClients} / {@code
 * getHBSUserForCurrentClient}). Commands are additionally guarded by
 * {@code ca.bc.gov.nrs.hbs.api.admin.UserDataDomainGuard}, because a
 * CommandDefinition has no client fence of its own.
 */
@Component
public class AdminCatalog implements QueryCatalog {

  /** HBS_USER + display names (BuildUserQuery / P011 User List columns). */
  private static final String USER_SQL = """
      SELECT u.user_id,
             u.last_name || ', ' || u.first_name
               || CASE WHEN u.middle_initial IS NOT NULL AND u.middle_initial <> '*'
                       THEN ' ' || u.middle_initial || '.' END AS user_name,
             u.last_name, u.first_name, u.middle_initial, u.email, u.phone,
             u.hbs_user_status_code, usc.description AS status,
             u.hbs_user_type_code, utc.description AS user_type,
             CASE WHEN u.hbs_user_type_code = 'MOF' THEN 'Ministry of Forests' ELSE fc.client_name END AS organization,
             CASE WHEN u.hbs_user_type_code = 'MOF' THEN ou.org_unit_name ELSE cl.client_locn_name END
               AS sub_organization,
             u.client_number, u.client_locn_code, u.org_unit_no
        FROM hbs_user u, hbs_user_status_code usc, hbs_user_type_code utc, v_client_public fc,
             client_location cl, org_unit ou
       WHERE usc.hbs_user_status_code(+) = u.hbs_user_status_code
         AND utc.hbs_user_type_code(+) = u.hbs_user_type_code
         AND fc.client_number(+) = u.client_number
         AND cl.client_number(+) = u.client_number
         AND cl.client_locn_code(+) = u.client_locn_code
         AND ou.org_unit_no(+) = u.org_unit_no""";

  /** Client/SPC admins: own client and own user type only. */
  private static final String USER_FENCE =
      "AND u.client_number = :scopeClientNumber AND u.hbs_user_type_code = :hbsUserType";

  @Override
  public List<QueryDefinition> queries() {
    return List.of(
        // P015 HBS User Search / P011 User List (BuildUserQuery.java), HBS_USER only —
        // the WebADE merge is gone (FAM).
        QueryDefinition.builder("admin.users.search")
            .legacy("P015/P011")
            .capability(Capability.USER_ADMIN)
            .sql(USER_SQL)
            .filter(QueryFilter.like("userId", "AND UPPER(u.user_id) LIKE :userId"))
            .filter(QueryFilter.like("lastName", "AND UPPER(u.last_name) LIKE :lastName"))
            .filter(QueryFilter.like("firstName", "AND UPPER(u.first_name) LIKE :firstName"))
            .filter(QueryFilter.like("email", "AND UPPER(u.email) LIKE :email"))
            .filter(QueryFilter.upper("userType", "AND u.hbs_user_type_code = :userType"))
            .filter(QueryFilter.upper("status", "AND u.hbs_user_status_code = :status"))
            .filter(QueryFilter.eq("clientNumber", "AND u.client_number = :clientNumber"))
            .filter(QueryFilter.eq("clientLocnCode", "AND u.client_locn_code = :clientLocnCode"))
            .filter(QueryFilter.number("orgUnit", "AND u.org_unit_no = :orgUnit"))
            .clientScope(USER_FENCE)
            .sort("userId", "u.user_id")
            .sort("userName", "u.last_name")
            .sort("status", "usc.description")
            .sort("userType", "utc.description")
            .orderBy("u.last_name, u.first_name")
            .build(),

        QueryDefinition.builder("admin.users.detail")
            .legacy("P010")
            .capability(Capability.USER_ADMIN)
            .sql(USER_SQL)
            .filter(QueryFilter.required("userId", "AND u.user_id = :userId", Type.UPPER))
            .clientScope(USER_FENCE)
            .orderBy("u.user_id")
            .maxRows(1)
            .build(),

        // P019 Update Associated Districts (DistrictDataDomainQuery.selectByCriteria).
        QueryDefinition.builder("admin.userDistricts.list")
            .legacy("P019")
            .capability(Capability.USER_ADMIN)
            .sql("""
                SELECT d.user_id, d.org_unit_no, ou.org_unit_code, ou.org_unit_name, ou.org_level_code
                  FROM hbs_user_dist_data_domain d, org_unit ou, hbs_user u
                 WHERE ou.org_unit_no(+) = d.org_unit_no
                   AND u.user_id = d.user_id""")
            .filter(QueryFilter.required("userId", "AND d.user_id = :userId", Type.UPPER))
            .clientScope(USER_FENCE)
            .sort("orgUnitName", "ou.org_unit_name")
            .orderBy("ou.org_unit_name")
            .maxRows(1000)
            .build(),

        // P017/P018 Update Associated Sites (SiteDataDomainQuery).
        QueryDefinition.builder("admin.userSites.list")
            .legacy("P017/P018")
            .capability(Capability.USER_ADMIN)
            .sql("""
                SELECT s.user_id, s.scale_site_id_nmbr, ss.site_name, ss.owner_cli_number,
                       ss.owner_cli_locn_cd, ss.owner_cli_number || '-' || ss.owner_cli_locn_cd AS site_owner,
                       ou.org_unit_code, ou.org_unit_name
                  FROM hbs_user_site_data_domain s, scale_site ss, org_unit ou, hbs_user u
                 WHERE ss.scale_site_id_nmbr(+) = s.scale_site_id_nmbr
                   AND ou.org_unit_no(+) = ss.org_unit_no
                   AND u.user_id = s.user_id""")
            .filter(QueryFilter.required("userId", "AND s.user_id = :userId", Type.UPPER))
            .clientScope(USER_FENCE)
            .sort("siteName", "ss.site_name")
            .orderBy("ss.site_name")
            .maxRows(2000)
            .build(),

        // AssociatedLocations.jsp (LocationDomainQuery).
        QueryDefinition.builder("admin.userLocations.list")
            .legacy("P017 (Associated Locations)")
            .capability(Capability.USER_ADMIN)
            .sql("""
                SELECT l.user_id, l.client_number, l.client_locn_code, cl.client_locn_name
                  FROM hbs_user_cli_loc_data_domain l, client_location cl, hbs_user u
                 WHERE cl.client_number(+) = l.client_number
                   AND cl.client_locn_code(+) = l.client_locn_code
                   AND u.user_id = l.user_id""")
            .filter(QueryFilter.required("userId", "AND l.user_id = :userId", Type.UPPER))
            .clientScope(USER_FENCE)
            .sort("clientLocnCode", "l.client_locn_code")
            .orderBy("l.client_number, l.client_locn_code")
            .maxRows(1000)
            .build(),

        // Scale sites selectable as a site domain (P018 listed the admin's client's sites).
        QueryDefinition.builder("codes.admin.scaleSites")
            .legacy("P017/P018")
            .capability(Capability.USER_ADMIN)
            .sql("""
                SELECT ss.scale_site_id_nmbr AS code, ss.site_name AS description
                  FROM scale_site ss
                 WHERE 1=1""")
            .clientScope("AND ss.owner_cli_number = :scopeClientNumber")
            .orderBy("ss.site_name")
            .maxRows(5000)
            .build()
    );
  }

  @Override
  public List<CommandDefinition> commands() {
    return List.of(
        CommandDefinition.builder("admin.userDataDomains.addDistrict")
            .legacy("P019")
            .capability(Capability.USER_ADMIN)
            .procedure("HBS_CREATE_DIST_DATA_DOMAIN")
            .requiredBody("userId", Type.UPPER)
            .requiredBody("orgUnitNo", Type.NUMBER)
            .build(),
        CommandDefinition.builder("admin.userDataDomains.removeDistrict")
            .legacy("P019")
            .capability(Capability.USER_ADMIN)
            .procedure("HBS_REMOVE_DIST_DATA_DOMAIN")
            .requiredBody("userId", Type.UPPER)
            .requiredBody("orgUnitNo", Type.NUMBER)
            .build(),
        CommandDefinition.builder("admin.userDataDomains.addSite")
            .legacy("P017/P018")
            .capability(Capability.USER_ADMIN)
            .procedure("HBS_CREATE_SITE_DATA_DOMAIN")
            .requiredBody("userId", Type.UPPER)
            .requiredBody("scaleSiteIdNmbr", Type.UPPER)
            .build(),
        CommandDefinition.builder("admin.userDataDomains.removeSite")
            .legacy("P017/P018")
            .capability(Capability.USER_ADMIN)
            .procedure("HBS_REMOVE_SITE_DATA_DOMAIN")
            .requiredBody("userId", Type.UPPER)
            .requiredBody("scaleSiteIdNmbr", Type.UPPER)
            .build(),
        CommandDefinition.builder("admin.userDataDomains.addLocation")
            .legacy("P017 (Associated Locations)")
            .capability(Capability.USER_ADMIN)
            .procedure("HBS_CREATE_LOCATION_DOMAIN")
            .requiredBody("userId", Type.UPPER)
            .clientNumber("clientNumber")
            .requiredBody("clientLocnCode", Type.STRING)
            .build(),
        CommandDefinition.builder("admin.userDataDomains.removeLocation")
            .legacy("P017 (Associated Locations)")
            .capability(Capability.USER_ADMIN)
            .procedure("HBS_REMOVE_LOCATION_DOMAIN")
            .requiredBody("userId", Type.UPPER)
            .clientNumber("clientNumber")
            .requiredBody("clientLocnCode", Type.STRING)
            .build()
    );
  }
}
