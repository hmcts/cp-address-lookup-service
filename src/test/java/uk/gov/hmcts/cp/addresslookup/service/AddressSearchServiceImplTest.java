package uk.gov.hmcts.cp.addresslookup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

import uk.gov.hmcts.cp.addresslookup.client.OsPlacesClient;
import uk.gov.hmcts.cp.addresslookup.exception.DegradedModeException;
import uk.gov.hmcts.cp.openapi.model.al.AddressSearchResponse;
import uk.gov.hmcts.cp.openapi.model.al.DegradedReason;

class AddressSearchServiceImplTest {

    private OsPlacesClient osPlacesClient;
    private Cache<String, AddressSearchResponse> cache;
    private AddressSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        osPlacesClient = mock(OsPlacesClient.class);
        cache = Caffeine.newBuilder().build();
        service = new AddressSearchServiceImpl(osPlacesClient, cache);
    }

    @Test
    void maps_client_results_into_a_search_response() {
        final Map<String, Object> dpa = new HashMap<>();
        dpa.put("UPRN", "10033544886");
        dpa.put("BUILDING_NUMBER", "10");
        dpa.put("THOROUGHFARE_NAME", "Downing Street");
        dpa.put("POSTCODE", "SW1A 1AA");
        when(osPlacesClient.searchByPostcode("SW1A 1AA")).thenReturn(List.of(dpa));

        final AddressSearchResponse response = service.searchByPostcode("SW1A 1AA", false);

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getAddress1()).isEqualTo("10 Downing Street");
        assertThat(response.getResults().get(0).getDpa()).isNull();
    }

    @Test
    void returns_empty_results_for_zero_matches() {
        when(osPlacesClient.searchByPostcode("ZZ99 1AA")).thenReturn(List.of());

        final AddressSearchResponse response = service.searchByPostcode("ZZ99 1AA", false);

        assertThat(response.getResults()).isEmpty();
    }

    @Test
    void trims_the_postcode_before_calling_the_client() {
        when(osPlacesClient.searchByPostcode(eq("SW1A 1AA"))).thenReturn(List.of());

        service.searchByPostcode(" SW1A 1AA ", false);

        verify(osPlacesClient).searchByPostcode("SW1A 1AA");
    }

    @Test
    void address_search_maps_client_results_into_a_search_response() {
        final Map<String, Object> dpa = new HashMap<>();
        dpa.put("UPRN", "10033544886");
        dpa.put("BUILDING_NUMBER", "10");
        dpa.put("THOROUGHFARE_NAME", "Downing Street");
        dpa.put("POSTCODE", "SW1A 1AA");
        when(osPlacesClient.searchByAddress("10 Downing Street")).thenReturn(List.of(dpa));

        final AddressSearchResponse response = service.searchByAddress("10 Downing Street", false);

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getAddress1()).isEqualTo("10 Downing Street");
    }

    @Test
    void address_search_returns_empty_results_for_zero_matches() {
        when(osPlacesClient.searchByAddress("nonsense")).thenReturn(List.of());

        final AddressSearchResponse response = service.searchByAddress("nonsense", false);

        assertThat(response.getResults()).isEmpty();
    }

    @Test
    void address_search_passes_the_address_through_unmodified() {
        when(osPlacesClient.searchByAddress(eq(" 10 Downing Street "))).thenReturn(List.of());

        service.searchByAddress(" 10 Downing Street ", false);

        verify(osPlacesClient).searchByAddress(" 10 Downing Street ");
    }

    @Test
    void find_match_maps_the_best_candidate_with_its_score() {
        final Map<String, Object> dpa = new HashMap<>();
        dpa.put("UPRN", "10033544886");
        dpa.put("BUILDING_NUMBER", "10");
        dpa.put("THOROUGHFARE_NAME", "Downing Street");
        dpa.put("POSTCODE", "SW1A 1AA");
        dpa.put("MATCH", "0.95");
        when(osPlacesClient.findBestMatch("10 Downing Street", new BigDecimal("0.7"))).thenReturn(List.of(dpa));

        final AddressSearchResponse response = service.findMatch("10 Downing Street", new BigDecimal("0.7"));

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getMatch()).isEqualByComparingTo(new BigDecimal("0.95"));
        assertThat(response.getResults().get(0).getDpa()).isNull();
    }

    @Test
    void find_match_returns_empty_results_for_a_nonsense_address() {
        when(osPlacesClient.findBestMatch("gibberish", new BigDecimal("0.7"))).thenReturn(List.of());

        final AddressSearchResponse response = service.findMatch("gibberish", new BigDecimal("0.7"));

        assertThat(response.getResults()).isEmpty();
    }

    @Test
    void find_match_caps_to_at_most_one_candidate_even_if_the_client_returns_more() {
        final Map<String, Object> first = new HashMap<>();
        first.put("UPRN", "10033544886");
        first.put("BUILDING_NUMBER", "10");
        first.put("THOROUGHFARE_NAME", "Downing Street");
        first.put("POSTCODE", "SW1A 1AA");
        final Map<String, Object> second = new HashMap<>();
        second.put("UPRN", "10033544887");
        second.put("BUILDING_NUMBER", "11");
        second.put("THOROUGHFARE_NAME", "Downing Street");
        second.put("POSTCODE", "SW1A 1AA");
        when(osPlacesClient.findBestMatch("10 Downing Street", null)).thenReturn(List.of(first, second));

        final AddressSearchResponse response = service.findMatch("10 Downing Street", null);

        assertThat(response.getResults()).hasSize(1);
    }

    @Test
    void find_match_passes_min_match_through_unmodified() {
        when(osPlacesClient.findBestMatch(eq("10 Downing Street"), eq(new BigDecimal("0.9")))).thenReturn(List.of());

        service.findMatch("10 Downing Street", new BigDecimal("0.9"));

        verify(osPlacesClient).findBestMatch("10 Downing Street", new BigDecimal("0.9"));
    }

    // ---- SB-05: caching ----

    @Test
    void repeating_the_same_postcode_search_only_calls_os_places_once() {
        when(osPlacesClient.searchByPostcode("SW1A 1AA")).thenReturn(List.of());

        service.searchByPostcode("SW1A 1AA", false);
        service.searchByPostcode("SW1A 1AA", false);

        verify(osPlacesClient, times(1)).searchByPostcode("SW1A 1AA");
    }

    @Test
    void postcode_cache_key_is_case_and_whitespace_insensitive() {
        when(osPlacesClient.searchByPostcode("SW1A 1AA")).thenReturn(List.of());

        service.searchByPostcode("SW1A 1AA", false);
        service.searchByPostcode("sw1a  1aa", false);

        verify(osPlacesClient, times(1)).searchByPostcode("SW1A 1AA");
    }

    @Test
    void repeating_the_same_free_text_search_only_calls_os_places_once() {
        when(osPlacesClient.searchByAddress("10 Downing Street")).thenReturn(List.of());

        service.searchByAddress("10 Downing Street", false);
        service.searchByAddress("10 Downing Street", false);

        verify(osPlacesClient, times(1)).searchByAddress("10 Downing Street");
    }

    @Test
    void repeating_the_same_match_request_only_calls_os_places_once() {
        when(osPlacesClient.findBestMatch("10 Downing Street", new BigDecimal("0.7"))).thenReturn(List.of());

        service.findMatch("10 Downing Street", new BigDecimal("0.7"));
        service.findMatch("10 Downing Street", new BigDecimal("0.70"));

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

        final AddressSearchResponse withoutDpa = service.searchByPostcode("SW1A 1AA", false);
        final AddressSearchResponse withDpa = service.searchByPostcode("SW1A 1AA", true);

        assertThat(withoutDpa.getResults().get(0).getDpa()).isNull();
        assertThat(withDpa.getResults().get(0).getDpa()).containsEntry("UPRN", "10033544886");
        verify(osPlacesClient, times(2)).searchByPostcode("SW1A 1AA");
    }

    @Test
    void a_degraded_upstream_failure_is_never_cached() {
        when(osPlacesClient.searchByPostcode("SW1A 1AA"))
                .thenThrow(new DegradedModeException(DegradedReason.UPSTREAM_TIMEOUT, null, "OS Places timed out"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.searchByPostcode("SW1A 1AA", false))
                .isInstanceOf(DegradedModeException.class);
        final AddressSearchResponse response = service.searchByPostcode("SW1A 1AA", false);

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
                    service.searchByPostcode("SW1A 1AA", false);
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

    @Test
    void a_cache_entry_expires_after_its_ttl() {
        when(osPlacesClient.searchByPostcode("SW1A 1AA")).thenReturn(List.of());
        final AtomicLong nanos = new AtomicLong();
        final Ticker fakeTicker = nanos::get;
        final Cache<String, AddressSearchResponse> shortTtlCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10))
                .ticker(fakeTicker)
                .build();
        final AddressSearchServiceImpl serviceWithFakeTicker =
                new AddressSearchServiceImpl(osPlacesClient, shortTtlCache);

        serviceWithFakeTicker.searchByPostcode("SW1A 1AA", false);
        nanos.addAndGet(Duration.ofMinutes(11).toNanos());
        serviceWithFakeTicker.searchByPostcode("SW1A 1AA", false);

        verify(osPlacesClient, times(2)).searchByPostcode("SW1A 1AA");
    }
}
