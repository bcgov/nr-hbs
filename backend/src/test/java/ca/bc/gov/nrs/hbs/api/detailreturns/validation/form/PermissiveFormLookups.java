package ca.bc.gov.nrs.hbs.api.detailreturns.validation.form;

import ca.bc.gov.nrs.hbs.api.detailreturns.DetailAuthority.Caller;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * A {@link FormLookups} for tests: by default everything exists, every profile
 * and authority is granted and the caller has no role. Tests switch individual
 * answers off through the public sets/flags.
 */
public class PermissiveFormLookups implements FormLookups {

  public final Set<String> unknownMarks = new HashSet<>();
  public final Set<String> unknownSites = new HashSet<>();
  public final Set<String> unknownLicences = new HashSet<>();
  public final Set<String> unknownDdns = new HashSet<>();
  public final Set<String> unknownSpg = new HashSet<>();
  public final Set<String> nonBillableMarks = new HashSet<>();
  /** pop/stratum/year keys on file; null = every stratum exists. */
  public Set<String> strata;
  public boolean siteProfile = true;
  public boolean siteInOrgUnit = true;
  public boolean scalerAuthority = true;
  public boolean siteAuthority = true;
  public String editErrorDescription = "Beachcomb mark not valid";
  public Set<String> roles = Set.of();
  public LocalDate today = LocalDate.of(2026, 6, 15);
  public final java.util.List<String> spgCalls = new java.util.ArrayList<>();

  @Override
  public boolean timberMarkExists(String timberMark) {
    return !unknownMarks.contains(timberMark);
  }

  @Override
  public boolean scaleSiteExists(String scaleSiteId) {
    return !unknownSites.contains(scaleSiteId);
  }

  @Override
  public boolean scalerLicenceExists(String licenseNumber) {
    return !unknownLicences.contains(licenseNumber);
  }

  @Override
  public boolean stratumExists(String population, String stratum, Long samplingYear) {
    if (population == null || stratum == null || samplingYear == null) return false;
    return strata == null || strata.contains(population + "/" + stratum + "/" + samplingYear);
  }

  @Override
  public boolean isBillableMark(String timberMark) {
    return !unknownMarks.contains(timberMark) && !nonBillableMarks.contains(timberMark);
  }

  @Override
  public boolean speciesProductGradeExists(String species, String product, String grade) {
    String key = species + "/" + product + "/" + grade;
    spgCalls.add(key);
    return !unknownSpg.contains(key);
  }

  @Override
  public boolean activeDocumentExists(String ddn, DocType type) {
    return !unknownDdns.contains(ddn);
  }

  @Override
  public boolean hasDetailSiteProfile(String scaleSiteId, DocType type, Date scaleDate) {
    return siteProfile;
  }

  @Override
  public String editErrorDescription(String code, String category) {
    return editErrorDescription;
  }

  @Override
  public Caller caller() {
    return new Caller("IDIR\\TESTER", roles);
  }

  @Override
  public boolean isScaleSiteAssociatedWithOrgUnit(String scaleSiteId, Caller caller) {
    return siteInOrgUnit;
  }

  @Override
  public boolean hasScaleSiteAuthorization(String scaleSiteId, Caller caller) {
    return siteAuthority;
  }

  @Override
  public boolean hasScalerAuthorization(Caller caller, String... licences) {
    return scalerAuthority;
  }

  @Override
  public LocalDate today() {
    return today;
  }
}
