package uk.gov.hmcts.cp.addresslookup.transform;

import java.math.BigDecimal;
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
    private static final int ADDRESS2_LINE_INDEX = 1;
    private static final int ADDRESS3_LINE_INDEX = 2;
    private static final int ADDRESS4_LINE_INDEX = 3;
    private static final int ADDRESS5_LINE_INDEX = 4;

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
        if (lines.size() > ADDRESS2_LINE_INDEX) {
            candidate.address2(truncate(lines.get(ADDRESS2_LINE_INDEX)));
        }
        if (lines.size() > ADDRESS3_LINE_INDEX) {
            candidate.address3(truncate(lines.get(ADDRESS3_LINE_INDEX)));
        }
        if (lines.size() > ADDRESS4_LINE_INDEX) {
            candidate.address4(truncate(lines.get(ADDRESS4_LINE_INDEX)));
        }
        if (lines.size() > ADDRESS5_LINE_INDEX) {
            candidate.address5(truncate(lines.get(ADDRESS5_LINE_INDEX)));
        }
        // The generated model's dpa field defaults to an empty (not null) HashMap, which would
        // otherwise serialize as "dpa":{} even when include=dpa wasn't requested; set it
        // explicitly to null so Jackson's non_null inclusion policy omits it.
        candidate.setDpa(includeDpa ? dpa : null);

        // OS Places only includes a MATCH field on /find results (the postcode/free-text search
        // operations don't send minmatch, so their DPA records never carry one); a malformed
        // value is an OS contract surprise, not something worth failing the whole mapping over.
        final String matchValue = fieldValue(dpa, "MATCH");
        if (matchValue != null) {
            try {
                candidate.match(new BigDecimal(matchValue));
            } catch (final NumberFormatException ignored) {
                // leave match unset
            }
        }
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
        final String joined;
        if (hasFirst && hasSecond) {
            joined = first.trim() + " " + second.trim();
        } else if (hasFirst) {
            joined = first.trim();
        } else if (hasSecond) {
            joined = second.trim();
        } else {
            joined = null;
        }
        return joined;
    }

    private static String fieldValue(final Map<String, Object> dpa, final String field) {
        final Object value = dpa.get(field);
        final String text = value == null ? null : String.valueOf(value).trim();
        return text == null || text.isEmpty() ? null : text;
    }

    private static String truncate(final String value) {
        return value.length() <= MAX_LINE_LENGTH ? value : value.substring(0, MAX_LINE_LENGTH);
    }
}
