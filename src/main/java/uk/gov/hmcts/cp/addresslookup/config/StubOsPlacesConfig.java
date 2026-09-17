package uk.gov.hmcts.cp.addresslookup.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.client.RestClient;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * SB-07: when the {@code stub} profile is active, starts an in-process WireMock server loaded
 * from the {@code stub-fixtures} module's fixture corpus, and provides a {@code @Primary}
 * {@link RestClient} pointed at it - so every SB-03/04/05/06 behaviour is provable with zero OS
 * Places egress. Everything below {@link uk.gov.hmcts.cp.addresslookup.client.OsPlacesClient} is
 * completely unaware of this - it still makes a real HTTP call over a real socket, just to
 * {@code localhost} instead of {@code api.os.uk}.
 *
 * <p><b>Deliberately does not use {@code os-places.client.base-url}</b> for the stub's own target,
 * even though that looked like the obvious knob: that property binds from the
 * {@code OS_PLACES_BASE_URL} environment variable, and Spring Boot resolves OS environment
 * variables at <em>higher</em> precedence than a profile-packaged {@code application-stub.yaml}.
 * Every real deployment (this repo's own {@code docker-compose.yml}, and the Helm charts in
 * {@code cpp-flux-config}) sets {@code OS_PLACES_BASE_URL} as a container env var - so a YAML
 * override of it would have been silently ignored everywhere it actually matters. A dedicated,
 * fixed-port {@code RestClient} bean sidesteps that precedence problem entirely instead of trying
 * to win a fight against it.
 *
 * <p>Environments that never set {@code SPRING_PROFILES_ACTIVE=stub} never create either bean
 * here, so the ordinary {@code OsPlacesRestClientConfig} bean (pointed at the real
 * {@code os-places.client.base-url}) is the only one in play - hitting the real OS Places API is
 * the default, not something this class has to arrange.
 */
@Configuration
@Profile("stub")
@EnableConfigurationProperties(StubProperties.class)
public class StubOsPlacesConfig {

    private static final int STUB_PORT = 9561;

    @Bean(destroyMethod = "stop")
    public WireMockServer stubOsPlacesServer(final OsPlacesClientProperties osPlacesProperties,
            final StubProperties stubProperties) {
        if (osPlacesProperties.keyRequired()) {
            // key-required=true is this app's existing, established signal for "this is an
            // OS-backed (real) tier" (see OsPlacesKeyHealthIndicator). Seeing it alongside the
            // stub profile means a misconfiguration somewhere - fail loudly rather than silently
            // serving fixture data on what's supposed to be a real environment.
            throw new IllegalStateException(
                    "stub profile is active but os-places.client.key-required=true - refusing to start");
        }
        final Path fixturesDir = extractFixtures(stubProperties.fixtureSet());
        final WireMockServer server = new WireMockServer(WireMockConfiguration.wireMockConfig()
                .port(STUB_PORT)
                .usingFilesUnderDirectory(fixturesDir.toString()));
        server.start();
        return server;
    }

    @Bean
    @Primary
    @DependsOn("stubOsPlacesServer")
    public RestClient stubOsPlacesRestClient() {
        return RestClient.builder().baseUrl("http://localhost:" + STUB_PORT).build();
    }

    /**
     * WireMock's own {@code usingFilesUnderClasspath} does a raw filesystem/ZipFile open under
     * the hood, which cannot read resources out of Spring Boot's packaged fat-jar nested-jar
     * layout ({@code jar:nested:/app/app.jar!...}) - it only works when running from an exploded
     * classpath (e.g. {@code bootRun}, or JUnit). Copying the fixture files out to a real temp
     * directory first - via Spring's own boot-loader-aware resource resolver, which *can* read
     * nested jars - sidesteps that incompatibility rather than fighting it.
     *
     * <p>The temp directory is created with owner-only permissions ({@code rwx------}): the
     * default system temp location it lives under (e.g. {@code /tmp}) is world-writable and
     * shared by every user/process on the host, so without this, another local user could reach
     * (or - via a pre-placed symlink - redirect) these files. Restricting just this one top-level
     * directory is sufficient: Unix directory traversal requires execute permission on every
     * ancestor directory in a path, so nothing else on the host can reach anything created under
     * it regardless of the individual files' own permissions.
     */
    // Package-private (not private) so the temp-directory permission hardening below is directly
    // unit-testable without needing a full WireMockServer/Spring context.
    /* default */ static Path extractFixtures(final String fixtureSet) {
        final String basePath = "wiremock/" + fixtureSet + "/";
        try {
            final FileAttribute<Set<PosixFilePermission>> ownerOnly = PosixFilePermissions.asFileAttribute(
                    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE));
            final Path targetDir = Files.createTempDirectory("stub-wiremock-" + fixtureSet + "-", ownerOnly);
            final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            final Resource[] resources = resolver.getResources("classpath*:" + basePath + "**/*");
            for (final Resource resource : resources) {
                final String url = resource.getURL().toString();
                final Path targetFile = targetDir.resolve(url.substring(url.indexOf(basePath) + basePath.length()));
                Files.createDirectories(targetFile.getParent());
                try (InputStream in = resource.getInputStream()) {
                    Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return targetDir;
        } catch (final IOException ex) {
            throw new IllegalStateException("Failed to extract stub fixtures for " + fixtureSet, ex);
        }
    }
}
