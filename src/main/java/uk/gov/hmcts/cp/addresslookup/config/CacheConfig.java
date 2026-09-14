package uk.gov.hmcts.cp.addresslookup.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import uk.gov.hmcts.cp.openapi.model.al.AddressSearchResponse;

/**
 * SB-05: a single in-process cache shared by all three address-lookup operations (postcode
 * search, free-text search, match), keyed on an operation-tagged, normalised string built by each
 * caller in {@code AddressSearchServiceImpl}. Caffeine's synchronous {@code get(key, mappingFn)}
 * gives stampede protection (concurrent misses for the same key collapse into one computation)
 * and never caches a thrown exception, so a {@code DegradedModeException} is never cached - both
 * for free, without extra code.
 */
@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig {

    @Bean
    public Cache<String, AddressSearchResponse> addressLookupCache(final CacheProperties properties) {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(properties.ttlMinutes()))
                .maximumSize(properties.maximumSize())
                .build();
    }
}
