package ca.bc.gov.nrs.hbs.api.detailreturns;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** In-memory {@link UserDomains}: site domains, district domains, scaler keys, site org units, mark districts. */
public class StubUserDomains implements UserDomains {
  public final Set<String> siteDomains = new HashSet<>();      // user/site
  public final Map<String, Set<Long>> districts = new HashMap<>();
  public final Set<String> scalerKeys = new HashSet<>();       // user/licence
  public final Map<String, Long> siteOrgUnits = new HashMap<>();
  public final Map<String, Long> markDistricts = new HashMap<>();
  public final Set<String> profiles = new HashSet<>();         // site/type
  public final Set<String> clientSites = new HashSet<>();      // client/site

  @Override
  public boolean hasSiteDomain(String userId, String scaleSiteId) {
    return siteDomains.contains(userId + "/" + scaleSiteId);
  }

  @Override
  public Set<Long> districtDomains(String userId) {
    return districts.getOrDefault(userId, Set.of());
  }

  @Override
  public boolean hasScalerKeyForLicence(String userId, String licenseNumber) {
    return scalerKeys.contains(userId + "/" + licenseNumber);
  }

  @Override
  public Long scaleSiteOrgUnit(String scaleSiteId) {
    return siteOrgUnits.get(scaleSiteId);
  }

  @Override
  public Long markDistrict(String timberMark) {
    return markDistricts.get(timberMark);
  }

  @Override
  public boolean hasDetailSiteProfile(String scaleSite, String returnTypeCode, Date date) {
    return profiles.contains(scaleSite + "/" + returnTypeCode);
  }

  @Override
  public boolean visibleToClient(String client, String scaleSite, String timberMark, String population,
                                 String stratum, Long samplingYear, String primaryLicence) {
    return clientSites.contains(client + "/" + scaleSite);
  }
}
