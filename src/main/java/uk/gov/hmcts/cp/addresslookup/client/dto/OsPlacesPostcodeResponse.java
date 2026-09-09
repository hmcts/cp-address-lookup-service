package uk.gov.hmcts.cp.addresslookup.client.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * OS Places' {@code /search/places/v1/postcode} response envelope. Only {@code results} is
 * modelled - the {@code header} block (query echo, result counts) is not needed by this service.
 * {@code results} is {@code null}, not an empty array, when OS finds a well-formed postcode with
 * no matches - callers must treat both as "zero matches".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OsPlacesPostcodeResponse(List<OsPlacesResult> results) {
}
