package uk.gov.hmcts.cp.addresslookup.client.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * OS Places' search response envelope, shared by the {@code /search/places/v1/postcode} and
 * {@code /search/places/v1/find} operations - both return the same shape. Only {@code results} is
 * modelled - the {@code header} block (query echo, result counts) is not needed by this service.
 * {@code results} is {@code null}, not an empty array, when OS finds no matches for an otherwise
 * well-formed request - callers must treat both as "zero matches".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OsPlacesSearchResponse(List<OsPlacesResult> results) {
}
