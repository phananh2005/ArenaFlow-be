package com.phananh.arenaflow.application.support.storage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.phananh.arenaflow.application.support.venue.VenueImageCleanupProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Cloudinary Admin API: liệt kê resource theo prefix và xoá theo public id (HTTP 420/429 = bị giới hạn tần suất). */
@Component
public class CloudinaryImageAssetAdmin implements ImageAssetAdmin {

    static final int MAX_DELETE_BATCH = 100;
    private static final String RESOURCES_PATH = "/resources/image/upload";

    private final RestClient restClient;

    @Autowired
    public CloudinaryImageAssetAdmin(CloudinaryProperties cloudinary, VenueImageCleanupProperties cleanup) {
        this(cloudinary, cleanup, RestClient.builder().requestFactory(timeouts(cleanup)));
    }

    CloudinaryImageAssetAdmin(CloudinaryProperties cloudinary, VenueImageCleanupProperties cleanup,
                              RestClient.Builder builder) {
        String credentials = cloudinary.apiKey() + ":" + cloudinary.apiSecret();
        this.restClient = builder
                .baseUrl("https://api.cloudinary.com/v1_1/" + cloudinary.cloudName())
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
                .defaultStatusHandler(status -> status.value() == 420 || status.value() == 429, (request, response) -> {
                    throw new AssetAdminRateLimitedException(response.getHeaders().getFirst("X-FeatureRateLimit-Reset"));
                })
                .build();
    }

    @Override
    public AssetPage listAssets(String prefix, String cursor, int maxResults) {
        Map<String, Object> values = new HashMap<>(Map.of("prefix", prefix, "maxResults", maxResults));
        ListResponse response = restClient.get()
                .uri(uri -> {
                    uri.path(RESOURCES_PATH).queryParam("prefix", "{prefix}").queryParam("max_results", "{maxResults}");
                    if (cursor != null) {
                        values.put("cursor", cursor);
                        uri.queryParam("next_cursor", "{cursor}");
                    }
                    return uri.build(values);
                })
                .retrieve()
                .body(ListResponse.class);
        List<String> publicIds = response == null || response.resources() == null ? List.of()
                : response.resources().stream().map(Resource::publicId).toList();
        return new AssetPage(publicIds, response == null ? null : response.nextCursor());
    }

    @Override
    public Set<String> deleteAssets(List<String> publicIds) {
        if (publicIds.size() > MAX_DELETE_BATCH) {
            throw new IllegalArgumentException("At most " + MAX_DELETE_BATCH + " assets per delete call");
        }
        if (publicIds.isEmpty()) {
            return Set.of();
        }
        Map<String, Object> values = new HashMap<>();
        DeleteResponse response = restClient.delete()
                .uri(uri -> {
                    uri.path(RESOURCES_PATH);
                    for (int i = 0; i < publicIds.size(); i++) {
                        values.put("id" + i, publicIds.get(i));
                        uri.queryParam("public_ids[]", "{id" + i + "}");
                    }
                    return uri.build(values);
                })
                .retrieve()
                .body(DeleteResponse.class);
        Set<String> deleted = new LinkedHashSet<>();
        if (response != null && response.deleted() != null) {
            response.deleted().forEach((publicId, outcome) -> {
                if ("deleted".equals(outcome)) {
                    deleted.add(publicId);
                }
            });
        }
        return deleted;
    }

    private static SimpleClientHttpRequestFactory timeouts(VenueImageCleanupProperties cleanup) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(cleanup.connectTimeout());
        factory.setReadTimeout(cleanup.readTimeout());
        return factory;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ListResponse(List<Resource> resources, @JsonProperty("next_cursor") String nextCursor) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Resource(@JsonProperty("public_id") String publicId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DeleteResponse(Map<String, String> deleted) {
    }
}
