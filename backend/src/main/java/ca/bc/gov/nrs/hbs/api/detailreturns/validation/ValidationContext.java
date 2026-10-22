package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import ca.bc.gov.nrs.hbs.api.detailreturns.store.DetailStore;
import java.time.LocalDate;

/**
 * What every validator gets: the reference lookups, the document finders and
 * "today" (Pacific) for the date edits.
 */
public record ValidationContext(ReferenceData ref, DetailStore store, LocalDate today) {
}
