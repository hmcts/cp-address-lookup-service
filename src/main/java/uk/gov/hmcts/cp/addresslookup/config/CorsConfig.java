package uk.gov.hmcts.cp.addresslookup.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final long DEFAULT_MAX_AGE_SECONDS = 3600L;

    @Value("${app.cors.allowed-origins:*}")
    private List<String> allowedOrigins;

    @Override
    public void addCorsMappings(final CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins.toArray(String[]::new))
                // GET-only - this service has no
                // POST/PUT/PATCH/DELETE mappings, so allowing them cross-origin wouldn't unlock
                // anything (an unmapped method still 404/405s).
                .allowedMethods(HttpMethod.GET.name())
                .allowedHeaders("*")
                .maxAge(DEFAULT_MAX_AGE_SECONDS);
    }
}
