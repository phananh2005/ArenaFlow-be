package com.phananh.arenaflow.application.support.storage;

import com.phananh.arenaflow.application.support.venue.VenueImageCleanupProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm tra thật trên Cloudinary cloud-dev: upload một ảnh PNG 1×1 vào folder spike dùng một lần, rồi liệt kê và chỉ
 * xoá đúng asset đó qua {@link CloudinaryImageAssetAdmin}. Dùng 2 lời gọi Admin API; không chạm asset khác.
 */
@EnabledIfEnvironmentVariable(named = "CLOUDINARY_API_SECRET", matches = ".+")
class CloudinaryImageAssetAdminLiveTest {

    private static final byte[] PIXEL = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLxEQAAAABJRU5ErkJggg==");

    @Test
    void listsAndDeletesOwnSpikeAssetWithEncodedQueryParameters() throws Exception {
        CloudinaryProperties cloudinary = new CloudinaryProperties(System.getenv("CLOUDINARY_CLOUD_NAME"),
                System.getenv("CLOUDINARY_API_KEY"), System.getenv("CLOUDINARY_API_SECRET"),
                System.getenv("CLOUDINARY_FOLDER"));
        String spikeFolder = cloudinary.folder() + "/venues/spike-admin-live-" + UUID.randomUUID() + "/";
        String publicId = spikeFolder + "pixel";
        upload(cloudinary, publicId);
        ImageAssetAdmin admin = new CloudinaryImageAssetAdmin(cloudinary,
                new VenueImageCleanupProperties(null, null, null, null, null, null, null));

        AssetPage page = admin.listAssets(spikeFolder, null, 10);
        assertThat(page.publicIds()).containsExactly(publicId);
        assertThat(page.nextCursor()).isNull();

        assertThat(admin.deleteAssets(List.of(publicId, spikeFolder + "missing"))).containsExactly(publicId);
    }

    /** Một batch xoá đầy đủ gồm public ID có chữ ký với độ dài thực tế vừa trong URL của một request DELETE. */
    @Test
    void acceptsFullDeleteBatchOfRealLengthMissingPublicIds() {
        CloudinaryProperties cloudinary = new CloudinaryProperties(System.getenv("CLOUDINARY_CLOUD_NAME"),
                System.getenv("CLOUDINARY_API_KEY"), System.getenv("CLOUDINARY_API_SECRET"),
                System.getenv("CLOUDINARY_FOLDER"));
        ImageAssetAdmin admin = new CloudinaryImageAssetAdmin(cloudinary,
                new VenueImageCleanupProperties(null, null, null, null, null, null, null));
        List<String> missing = IntStream.range(0, CloudinaryImageAssetAdmin.MAX_DELETE_BATCH)
                .mapToObj(i -> cloudinary.folder() + "/venues/spike-missing-" + UUID.randomUUID() + "/"
                        + (1_700_000_000L + i) + "-" + "A".repeat(22) + "-" + "b".repeat(22))
                .toList();

        assertThat(admin.deleteAssets(missing)).isEmpty();
    }

    private static void upload(CloudinaryProperties cloudinary, String publicId) throws Exception {
        long timestamp = System.currentTimeMillis() / 1000;
        String toSign = "public_id=" + publicId + "&timestamp=" + timestamp + cloudinary.apiSecret();
        String signature = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-1").digest(toSign.getBytes(StandardCharsets.UTF_8)));
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(PIXEL) {
            @Override public String getFilename() { return "pixel.png"; }
        });
        form.add("api_key", cloudinary.apiKey());
        form.add("timestamp", Long.toString(timestamp));
        form.add("public_id", publicId);
        form.add("signature", signature);
        RestClient.create().post().uri("https://api.cloudinary.com/v1_1/" + cloudinary.cloudName() + "/image/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().toBodilessEntity();
    }
}
