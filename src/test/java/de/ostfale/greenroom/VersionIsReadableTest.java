package de.ostfale.greenroom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What is running on the Pi has to be answerable without the shell. The image tag moves
 * under {@code latest} and the banner scrolls away, so the version goes into the jar at
 * build time and comes out of {@code /mgmt/info}.
 *
 * <p>Three things have to hold together for that, and none of them is visible in one
 * place: the {@code build-info} goal in the pom writes the properties, the {@code build}
 * contributor renders them, and {@code info} is on the exposure list in
 * {@code application.yml}. This test fails when any of them goes away.
 *
 * <p>A real server rather than MockMvc, because the management endpoints sit on a port of
 * their own — that is the arrangement the Pi runs, and a mock context would answer on the
 * wrong one.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class VersionIsReadableTest {

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private BuildProperties build;

    @Test
    void theInfoEndpointNamesTheVersionThatWasBuilt() throws Exception {
        JsonNode stamped = get("/mgmt/info").path("build");

        assertThat(stamped.isMissingNode()).isFalse();
        assertThat(stamped.path("version").asText()).isEqualTo(build.getVersion());
        assertThat(stamped.path("artifact").asText()).isEqualTo("greenroom");
        // Two images of the same version are told apart by when they were made.
        assertThat(stamped.path("time").asText()).isNotBlank();
    }

    private JsonNode get(String path) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + managementPort + path)).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        return JsonMapper.builder().build().readTree(response.body());
    }
}
