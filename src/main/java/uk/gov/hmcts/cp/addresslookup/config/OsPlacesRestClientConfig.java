package uk.gov.hmcts.cp.addresslookup.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(OsPlacesClientProperties.class)
public class OsPlacesRestClientConfig {

    @Bean
    public RestClient osPlacesRestClient(final OsPlacesClientProperties properties) {
        final HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());
        final ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * SB-06: backs the {@code CompletableFuture}s that {@link OsPlacesRemoteCaller}'s
     * {@code @TimeLimiter}/{@code @CircuitBreaker}-decorated methods run on. OS Places calls are
     * I/O-bound - a virtual thread per call avoids sharing/blocking the JVM-wide common
     * {@code ForkJoinPool} without needing to size a fixed pool.
     */
    @Bean(destroyMethod = "close")
    public ExecutorService osPlacesExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
