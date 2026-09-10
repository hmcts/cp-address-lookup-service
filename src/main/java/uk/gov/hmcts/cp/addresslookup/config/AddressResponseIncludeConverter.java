package uk.gov.hmcts.cp.addresslookup.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import uk.gov.hmcts.cp.openapi.model.al.AddressResponseInclude;

/**
 * Binds the {@code include} query parameter (wire value {@code "dpa"}) to
 * {@link AddressResponseInclude}. Needed because Spring's default enum conversion matches by
 * constant name ({@code DPA}), not the generated enum's {@code @JsonValue} - without this, a
 * request with {@code include=dpa} fails binding and is rejected with 400.
 */
@Component
public class AddressResponseIncludeConverter implements Converter<String, AddressResponseInclude> {

    @Override
    public AddressResponseInclude convert(final String source) {
        return AddressResponseInclude.fromValue(source);
    }
}
