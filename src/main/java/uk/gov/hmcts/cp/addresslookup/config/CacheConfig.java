package uk.gov.hmcts.cp.addresslookup.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * SB-05: a single named Spring cache ("addressLookup") shared by all three address-lookup
 * operations (postcode search, free-text search, match), backed by Caffeine. Composite,
 * operation-tagged key normalisation logic (postcode case/whitespace, {@code minMatch} scale)
 * lives in {@link AddressLookupCacheKeys}'s static helpers, called from each cached method's
 * {@code @Cacheable(key = ...)} SpEL expression.
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig {

    public static final String ADDRESS_LOOKUP_CACHE = "addressLookup";

    @Bean
    public CacheManager cacheManager(final CacheProperties properties) {
        final CaffeineCacheManager cacheManager = new CaffeineCacheManager(ADDRESS_LOOKUP_CACHE);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(properties.ttlMinutes()))
                .maximumSize(properties.maximumSize()));
        return cacheManager;
    }
}
