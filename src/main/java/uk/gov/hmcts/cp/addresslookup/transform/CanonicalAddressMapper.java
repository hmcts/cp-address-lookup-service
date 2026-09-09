package uk.gov.hmcts.cp.addresslookup.transform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import uk.gov.hmcts.cp.addresslookup.exception.DegradedModeException;
import uk.gov.hmcts.cp.openapi.model.al.AddressCandidate;
import uk.gov.hmcts.cp.openapi.model.al.DegradedReason;

/**
 * Maps a raw OS Places DPA record to the canonical CP {@link AddressCandidate} contract
 * (address1-5, postcode, uprn). Pure function - no Spring context needed to test it.
 *
 * <p>OS Places does not have a single "address line" field; DPA splits a premise across several
 * fields (sub-building, building, thoroughfare, locality, town). This mapper packs those fields,
 * in the order below, into address1-5, dropping any that are blank - e.g. for "10 Downing Street"
 * (BUILDING_NUMBER="10", THOROUGHFARE_NAME="Downing Street", everything else blank) this yields
 * address1="10", address2="Downing Street", matching the contract's own example.
 */
public final class CanonicalAddressMapper {

    private static final int MAX_LINE_LENGTH = 35;

    private CanonicalAddressMapper() {
    }

    public static AddressCandidate toCandidate(final Map<String, Object> dpa, final boolean includeDpa) {
        final String uprn = fieldValue(dpa, "UPRN");
        final String postcode = fieldValue(dpa, "POSTCODE");
        if (uprn == null || postcode == null) {
            throw new DegradedModeException(DegradedReason.UPSTREAM_CONTRACT, null,
                    "OS Places DPA record is missing UPRN or POSTCODE");
        }

        final List<String> lines = addressLines(dpa);
        if (lines.isEmpty()) {
            throw new DegradedModeException(DegradedReason.UPSTREAM_CONTRACT, null,
                    "OS Places DPA record has no usable address lines");
        }

        final AddressCandidate candidate = new AddressCandidate()
                .address1(truncate(lines.get(0)))
                .postcode(postcode)
                .uprn(uprn);
        if (lines.size() > 1) {
            candidate.address2(truncate(lines.get(1)));
        }
        if (lines.size() > 2) {
            candidate.address3(truncate(lines.get(2)));
        }
        if (lines.size() > 3) {
            candidate.address4(truncate(lines.get(3)));
        }
        if (lines.size() > 4) {
            candidate.address5(truncate(lines.get(4)));
        }
        // The generated model's dpa field defaults to an empty (not null) HashMap, which would
        // otherwise serialize as "dpa":{} even when include=dpa wasn't requested; set it
        // explicitly to null so Jackson's non_null inclusion policy omits it.
        candidate.setDpa(includeDpa ? dpa : null);
        return candidate;
    }

    private static List<String> addressLines(final Map<String, Object> dpa) {
        final List<String> lines = new ArrayList<>();
        addIfNotBlank(lines, fieldValue(dpa, "SUB_BUILDING_NAME"));
        addIfNotBlank(lines, joinNonBlank(fieldValue(dpa, "BUILDING_NUMBER"), fieldValue(dpa, "BUILDING_NAME")));
        addIfNotBlank(lines,
                joinNonBlank(fieldValue(dpa, "DEPENDENT_THOROUGHFARE_NAME"), fieldValue(dpa, "THOROUGHFARE_NAME")));
        addIfNotBlank(lines, fieldValue(dpa, "DOUBLE_DEPENDENT_LOCALITY"));
        addIfNotBlank(lines, fieldValue(dpa, "DEPENDENT_LOCALITY"));
        addIfNotBlank(lines, fieldValue(dpa, "POST_TOWN"));
        return lines;
    }

    private static void addIfNotBlank(final List<String> lines, final String value) {
        if (value != null && !value.isBlank() && lines.size() < 5) {
            lines.add(value);
        }
    }

    private static String joinNonBlank(final String first, final String second) {
        final boolean hasFirst = first != null && !first.isBlank();
        final boolean hasSecond = second != null && !second.isBlank();
        if (hasFirst && hasSecond) {
            return first.trim() + " " + second.trim();
        }
        if (hasFirst) {
            return first.trim();
        }
        if (hasSecond) {
            return second.trim();
        }
        return null;
    }

    private static String fieldValue(final Map<String, Object> dpa, final String field) {
        final Object value = dpa.get(field);
        if (value == null) {
            return null;
        }
        final String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String truncate(final String value) {
        return value.length() <= MAX_LINE_LENGTH ? value : value.substring(0, MAX_LINE_LENGTH);
    }
}
