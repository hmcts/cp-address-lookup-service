package uk.gov.hmcts.cp.addresslookup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import jakarta.annotation.Resource;
import uk.gov.hmcts.cp.addresslookup.client.OsPlacesClient;
import uk.gov.hmcts.cp.addresslookup.config.CacheConfig;
import uk.gov.hmcts.cp.addresslookup.exception.DegradedModeException;
import uk.gov.hmcts.cp.openapi.model.al.AddressSearchResponse;
import uk.gov.hmcts.cp.openapi.model.al.DegradedReason;

/**
 * SB-05 cache behaviour, proved through a real Spring context so the {@code @Cacheable} AOP proxy
 * is actually active (unlike {@link AddressSearchServiceImplTest}, which constructs the service
 * directly and therefore bypasses the proxy entirely). {@code addressSearchService} here is the
 * proxied bean, injected via the interface.
 */
@SpringBootTest
class AddressSearchCachingTest {

    @MockitoBean
    private OsPlacesClient osPlacesClient;

    @Resource
    private AddressSearchService addressSearchService;

    @Resource
    private CacheManager cacheManager;

    @BeforeEach
    void clearCache() {
        // The Spring context (and therefore the cache) is shared across every test method in this
        // class; without this, whichever test runs first for a given key silently pre-warms the
        // cache for every other test reusing it.
        cacheManager.getCache(CacheConfig.ADDRESS_LOOKUP_CACHE).clear();
    }

    @Test
    void repeating_the_same_postcode_search_only_calls_os_places_once() {
        when(osPlacesClient.searchByPostcode("SW1A 1AA")).thenReturn(List.of());

        addressSearchService.searchByPostcode("SW1A 1AA", false);
        addressSearchService.searchByPostcode("SW1A 1AA", false);

        verify(osPlacesClient, times(1)).searchByPostcode("SW1A 1AA");
    }

    @Test
    void postcode_cache_key_is_case_and_whitespace_insensitive() {
        when(osPlacesClient.searchByPostcode("SW1A 1AA")).thenReturn(List.of());

        addressSearchService.searchByPostcode("SW1A 1AA", false);
        addressSearchService.searchByPostcode("sw1a  1aa", false);

        verify(osPlacesClient, times(1)).searchByPostcode("SW1A 1AA");
    }

    @Test
    void repeating_the_same_free_text_search_only_calls_os_places_once() {
        when(osPlacesClient.searchByAddress("10 Downing Street")).thenReturn(List.of());

        addressSearchService.searchByAddress("10 Downing Street", false);
        addressSearchService.searchByAddress("10 Downing Street", false);

        verify(osPlacesClient, times(1)).searchByAddress("10 Downing Street");
    }

    @Test
    void repeating_the_same_match_request_only_calls_os_places_once() {
        when(osPlacesClient.findBestMatch("10 Downing Street", new BigDecimal("0.7"))).thenReturn(List.of());

        addressSearchService.findMatch("10 Downing Street", new BigDecimal("0.7"));
        addressSearchService.findMatch("10 Downing Street", new BigDecimal("0.70"));

        verify(osPlacesClient, times(1)).findBestMatch("10 Downing Street", new BigDecimal("0.7"));
    }

    @Test
    void varying_include_dpa_produces_two_separate_os_calls_and_responses() {
        final Map<String, Object> dpa = new HashMap<>();
        dpa.put("UPRN", "10033544886");
        dpa.put("BUILDING_NUMBER", "10");
        dpa.put("THOROUGHFARE_NAME", "Downing Street");
        dpa.put("POSTCODE", "SW1A 1AA");
        when(osPlacesClient.searchByPostcode("SW1A 1AA")).thenReturn(List.of(dpa));

        final AddressSearchResponse withoutDpa = addressSearchService.searchByPostcode("SW1A 1AA", false);
        final AddressSearchResponse withDpa = addressSearchService.searchByPostcode("SW1A 1AA", true);

        assertThat(withoutDpa.getResults().get(0).getDpa()).isNull();
        assertThat(withDpa.getResults().get(0).getDpa()).containsEntry("UPRN", "10033544886");
        verify(osPlacesClient, times(2)).searchByPostcode("SW1A 1AA");
    }

    @Test
    void a_degraded_upstream_failure_is_never_cached() {
        when(osPlacesClient.searchByPostcode("SW1A 1AA"))
                .thenThrow(new DegradedModeException(DegradedReason.UPSTREAM_TIMEOUT, null, "OS Places timed out"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> addressSearchService.searchByPostcode("SW1A 1AA", false))
                .isInstanceOf(DegradedModeException.class);
        final AddressSearchResponse response = addressSearchService.searchByPostcode("SW1A 1AA", false);

        assertThat(response.getResults()).isEmpty();
        verify(osPlacesClient, times(2)).searchByPostcode("SW1A 1AA");
    }

    @Test
    void concurrent_requests_for_the_same_uncached_key_collapse_into_one_os_call() throws InterruptedException {
        final int callerCount = 20;
        final CountDownLatch releaseOsCall = new CountDownLatch(1);
        final AtomicInteger osCallCount = new AtomicInteger();
        when(osPlacesClient.searchByPostcode("SW1A 1AA")).thenAnswer(invocation -> {
            osCallCount.incrementAndGet();
            releaseOsCall.await(5, TimeUnit.SECONDS);
            return List.of();
        });

        final ExecutorService pool = Executors.newFixedThreadPool(callerCount);
        try {
            final CountDownLatch allStarted = new CountDownLatch(callerCount);
            for (int i = 0; i < callerCount; i++) {
                pool.submit(() -> {
                    allStarted.countDown();
                    addressSearchService.searchByPostcode("SW1A 1AA", false);
                });
            }
            allStarted.await(5, TimeUnit.SECONDS);
            releaseOsCall.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(osCallCount.get()).isEqualTo(1);
    }
}
