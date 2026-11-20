package com.phananh.arenaflow.application.support.storage;

import com.phananh.arenaflow.application.support.venue.VenueImageCleanupProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CloudinaryImageAssetAdminTest {

    private static final String BASE = "https://api.cloudinary.com/v1_1/demo-cloud/resources/image/upload";
    private static final String BASIC = "Basic " + Base64.getEncoder()
            .encodeToString("key:secret".getBytes(StandardCharsets.UTF_8));

    private MockRestServiceServer server;
    private ImageAssetAdmin admin;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        admin = new CloudinaryImageAssetAdmin(new CloudinaryProperties("demo-cloud", "key", "secret", "arenaflow-dev"),
                defaults(), builder);
    }

    @Test
    void listAssetsSendsPrefixPageSizeAndCursorWithBasicAuthAndParsesPage() {
        server.expect(request -> {
                    assertThat(request.getURI().toString()).startsWith(BASE + "?");
                    assertThat(request.getURI().getQuery())
                            .isEqualTo("prefix=arenaflow-dev/venues/&max_results=500&next_cursor=abc");
                })
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, BASIC))
                .andRespond(withSuccess("""
                        {"resources":[{"public_id":"arenaflow-dev/venues/a","version":1},
                                      {"public_id":"arenaflow-dev/venues/b"}],"next_cursor":"def"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(request -> assertThat(request.getURI().getQuery()).doesNotContain("next_cursor"))
                .andRespond(withSuccess("{\"resources\":[]}", MediaType.APPLICATION_JSON));

        AssetPage first = admin.listAssets("arenaflow-dev/venues/", "abc", 500);
        AssetPage last = admin.listAssets("arenaflow-dev/venues/", null, 500);

        assertThat(first.publicIds()).containsExactly("arenaflow-dev/venues/a", "arenaflow-dev/venues/b");
        assertThat(first.nextCursor()).isEqualTo("def");
        assertThat(last.publicIds()).isEmpty();
        assertThat(last.nextCursor()).isNull();
        server.verify();
    }

    @Test
    void deleteAssetsSendsRepeatedPublicIdsAndReturnsOnlyDeletedOnes() {
        server.expect(request -> assertThat(request.getURI().getQuery())
                        .isEqualTo("public_ids[]=arenaflow-dev/venues/a&public_ids[]=arenaflow-dev/venues/b"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, BASIC))
                .andRespond(withSuccess("""
                        {"deleted":{"arenaflow-dev/venues/a":"deleted","arenaflow-dev/venues/b":"not_found"},
                         "deleted_counts":{},"partial":false}
                        """, MediaType.APPLICATION_JSON));

        assertThat(admin.deleteAssets(List.of("arenaflow-dev/venues/a", "arenaflow-dev/venues/b")))
                .containsExactly("arenaflow-dev/venues/a");
        server.verify();
    }

    @Test
    void deleteAssetsSkipsHttpForEmptyInputAndRejectsMoreThanOneHundredIds() {
        assertThat(admin.deleteAssets(List.of())).isEmpty();
        List<String> tooMany = IntStream.range(0, 101).mapToObj(i -> "id" + i).toList();
        assertThatThrownBy(() -> admin.deleteAssets(tooMany)).isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @Test
    void rateLimitStatusesMapToRateLimitedExceptionWithResetHeader() {
        for (int status : List.of(420, 429)) {
            server.reset();
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-FeatureRateLimit-Reset", "Wed, 19 Nov 2026 21:00:00 GMT");
            server.expect(request -> { })
                    .andRespond(withStatus(HttpStatusCode.valueOf(status)).headers(headers));

            assertThatThrownBy(() -> admin.listAssets("arenaflow-dev/venues/", null, 500))
                    .isInstanceOfSatisfying(AssetAdminRateLimitedException.class,
                            exception -> assertThat(exception.getResetAt()).isEqualTo("Wed, 19 Nov 2026 21:00:00 GMT"));
        }
    }

    @Test
    void otherErrorsPropagateUnchanged() {
        server.expect(request -> { }).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> admin.deleteAssets(Collections.singletonList("arenaflow-dev/venues/a")))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    private static VenueImageCleanupProperties defaults() {
        return new VenueImageCleanupProperties(null, null, null, null, null, null, null);
    }
}
