package uk.gov.hmcts.cp.addresslookup.client.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One entry in OS Places' {@code results} array. OS always queries with {@code dataset=DPA}, so
 * only the {@code DPA} record is modelled; its fields are kept as a raw map since the contract's
 * {@code include=dpa} option nests them verbatim and the set of fields OS returns is not fixed.
 */
public record OsPlacesResult(@JsonProperty("DPA") Map<String, Object> dpa) {
}
