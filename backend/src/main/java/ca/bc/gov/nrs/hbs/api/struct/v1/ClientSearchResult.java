package ca.bc.gov.nrs.hbs.api.struct.v1;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A forest client — or, from the locations lookup, one of its locations —
 * as the client picker shows it. Built from nr-forest-client-api's
 * {@code ClientPublicViewDto} (+ {@code ClientLocationDto}).
 *
 * <p>{@code clientName} is the display form: "SURNAME, FIRST MIDDLE" for an
 * individual, the company name otherwise. An individual's name is "Not
 * Releasable" to industry viewers other than that client (the FOI rule the
 * HBS queries apply). The location fields are null on a client-level row.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientSearchResult {
  private String clientNumber;
  private String clientAcronym;
  private String clientName;
  private String legalFirstName;
  private String legalMiddleName;
  private String clientTypeCode;
  private String clientStatusCode;
  private String clientLocnCode;
  private String clientLocnName;
  private String city;
  /** True when the location is expired (location rows only). */
  private Boolean locationExpired;
}
