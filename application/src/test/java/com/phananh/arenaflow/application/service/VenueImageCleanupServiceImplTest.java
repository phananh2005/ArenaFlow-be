package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.service.impl.VenueImageCleanupServiceImpl;
import com.phananh.arenaflow.application.support.storage.AssetAdminRateLimitedException;
import com.phananh.arenaflow.application.support.storage.AssetPage;
import com.phananh.arenaflow.application.support.storage.ImageAssetAdmin;
import com.phananh.arenaflow.application.support.storage.ImageUploadSigner;
import com.phananh.arenaflow.application.support.storage.SignedPublicId;
import com.phananh.arenaflow.application.support.venue.VenueImageCleanupProperties;
import com.phananh.arenaflow.domain.repository.VenueImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Dọn asset ảnh mồ côi: chỉ xoá asset cũ không còn tham chiếu, theo batch, dừng an toàn khi lỗi, log không lộ dữ liệu. */
@ExtendWith(OutputCaptureExtension.class)
class VenueImageCleanupServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-19T20:00:00Z");
    private static final String PREFIX = "arenaflow-dev/venues/";

    private final ImageAssetAdmin assetAdmin = mock(ImageAssetAdmin.class);
    private final ImageUploadSigner signer = mock(ImageUploadSigner.class);
    private final VenueImageRepository imageRepository = mock(VenueImageRepository.class);
    private final TransactionTemplate readOnlyTransaction = mock(TransactionTemplate.class);
    private final Map<String, Instant> signedAt = new HashMap<>();

    @BeforeEach
    void setUp() {
        when(signer.rootPrefix("venues")).thenReturn(PREFIX);
        when(signer.parsePublicId(anyString())).thenAnswer(invocation -> Optional.ofNullable(
                signedAt.get(invocation.getArgument(0, String.class)))
                .map(at -> new SignedPublicId("venues/v", at, "nonce", "tag")));
        when(readOnlyTransaction.execute(any(TransactionCallback.class))).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
    }

    @Test
    void deletesOnlyOldUnreferencedWellFormedAssetsAcrossPages() {
        String oldOrphan = asset("old-orphan", NOW.minus(Duration.ofHours(25)));
        String oldReferenced = asset("old-referenced", NOW.minus(Duration.ofHours(30)));
        String young = asset("young", NOW.minus(Duration.ofHours(23)));
        String future = asset("future", NOW.plus(Duration.ofMinutes(6)));
        String malformed = PREFIX + "not-signed";
        when(assetAdmin.listAssets(eq(PREFIX), isNull(), eq(500)))
                .thenReturn(new AssetPage(List.of(oldOrphan, oldReferenced, young, future, malformed), "c1"));
        when(assetAdmin.listAssets(PREFIX, "c1", 500)).thenReturn(new AssetPage(List.of(), null));
        when(imageRepository.findExistingPublicIds(List.of(oldOrphan, oldReferenced))).thenReturn(List.of(oldReferenced));
        when(assetAdmin.deleteAssets(List.of(oldOrphan))).thenReturn(Set.of(oldOrphan));

        assertThat(service(100).cleanupOrphanAssets()).isEqualTo(1);

        verify(assetAdmin).deleteAssets(List.of(oldOrphan));
        verify(assetAdmin, times(2)).listAssets(anyString(), any(), anyInt());
    }

    @Test
    void pageWithoutCandidatesSkipsDatabaseAndDelete() {
        when(assetAdmin.listAssets(eq(PREFIX), isNull(), eq(500)))
                .thenReturn(new AssetPage(List.of(asset("young", NOW.minus(Duration.ofHours(1)))), null));

        assertThat(service(100).cleanupOrphanAssets()).isZero();

        verifyNoInteractions(imageRepository);
        verify(assetAdmin, never()).deleteAssets(anyList());
    }

    @Test
    void deletesInBatchesOfOneHundred() {
        List<String> orphans = IntStream.range(0, 150).mapToObj(i -> asset("orphan-" + i, NOW.minus(Duration.ofDays(2))))
                .toList();
        when(assetAdmin.listAssets(eq(PREFIX), isNull(), eq(500))).thenReturn(new AssetPage(orphans, null));
        when(imageRepository.findExistingPublicIds(orphans)).thenReturn(List.of());
        when(assetAdmin.deleteAssets(anyList())).thenAnswer(invocation -> Set.copyOf(invocation.getArgument(0)));

        assertThat(service(100).cleanupOrphanAssets()).isEqualTo(150);

        verify(assetAdmin).deleteAssets(orphans.subList(0, 100));
        verify(assetAdmin).deleteAssets(orphans.subList(100, 150));
    }

    @Test
    void stopsWhenAdminCallBudgetIsUsed() {
        String orphan = asset("orphan", NOW.minus(Duration.ofDays(2)));
        when(assetAdmin.listAssets(eq(PREFIX), isNull(), eq(500))).thenReturn(new AssetPage(List.of(orphan), "c1"));
        when(imageRepository.findExistingPublicIds(List.of(orphan))).thenReturn(List.of());
        when(assetAdmin.deleteAssets(List.of(orphan))).thenReturn(Set.of(orphan));

        assertThat(service(2).cleanupOrphanAssets()).isEqualTo(1);

        verify(assetAdmin, never()).listAssets(PREFIX, "c1", 500);
    }

    @Test
    void stopsWithoutDeletingWhenDatabaseCheckFails() {
        String orphan = asset("orphan", NOW.minus(Duration.ofDays(2)));
        when(assetAdmin.listAssets(eq(PREFIX), isNull(), eq(500))).thenReturn(new AssetPage(List.of(orphan), "c1"));
        when(imageRepository.findExistingPublicIds(List.of(orphan))).thenThrow(new QueryTimeoutException("db"));

        assertThat(service(100).cleanupOrphanAssets()).isZero();

        verify(assetAdmin, never()).deleteAssets(anyList());
        verify(assetAdmin, never()).listAssets(PREFIX, "c1", 500);
    }

    @Test
    void stopsOnRateLimitOrNetworkFailureWithoutThrowing() {
        when(assetAdmin.listAssets(eq(PREFIX), isNull(), eq(500))).thenThrow(new AssetAdminRateLimitedException("soon"));
        assertThat(service(100).cleanupOrphanAssets()).isZero();

        String orphan = asset("orphan", NOW.minus(Duration.ofDays(2)));
        when(assetAdmin.listAssets(eq(PREFIX), isNull(), eq(500))).thenReturn(new AssetPage(List.of(orphan), "c1"));
        when(imageRepository.findExistingPublicIds(List.of(orphan))).thenReturn(List.of());
        when(assetAdmin.deleteAssets(List.of(orphan))).thenThrow(new ResourceAccessException("timeout"));
        assertThat(service(100).cleanupOrphanAssets()).isZero();
        verify(assetAdmin, never()).listAssets(PREFIX, "c1", 500);
    }

    @Test
    void logsHttpStatusButNotBodyWhenStorageReturnsServerError(CapturedOutput output) {
        when(assetAdmin.listAssets(eq(PREFIX), isNull(), eq(500))).thenThrow(HttpServerErrorException.create(
                HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", HttpHeaders.EMPTY,
                "{\"error\":\"body-detail\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        assertThat(service(100).cleanupOrphanAssets()).isZero();

        assertThat(output).contains("WARN").contains("storage admin API returned HTTP 503").doesNotContain("body-detail");
    }

    @Test
    void logsUnreachableWithoutRequestUrlOnTimeout(CapturedOutput output) {
        when(assetAdmin.listAssets(eq(PREFIX), isNull(), eq(500))).thenThrow(new ResourceAccessException(
                "I/O error on GET request for \"https://api.cloudinary.com/v1_1/cloud-x/resources\": Read timed out",
                new SocketTimeoutException("Read timed out")));

        assertThat(service(100).cleanupOrphanAssets()).isZero();

        assertThat(output).contains("WARN").contains("storage admin API unreachable (SocketTimeoutException)")
                .doesNotContain("api.cloudinary.com");
    }

    @Test
    void logsUnexpectedFailureAsErrorWithStackTrace(CapturedOutput output) {
        when(assetAdmin.listAssets(eq(PREFIX), isNull(), eq(500))).thenThrow(new IllegalStateException("cleanup-bug"));

        assertThat(service(100).cleanupOrphanAssets()).isZero();

        assertThat(output).contains("ERROR").contains("Venue image cleanup stopped: unexpected failure")
                .contains("java.lang.IllegalStateException: cleanup-bug")
                .contains("VenueImageCleanupServiceImpl.cleanupOrphanAssets");
        verify(assetAdmin, never()).deleteAssets(anyList());
    }

    private String asset(String name, Instant at) {
        String publicId = PREFIX + name;
        signedAt.put(publicId, at);
        return publicId;
    }

    private VenueImageCleanupService service(int maxAdminCalls) {
        return new VenueImageCleanupServiceImpl(assetAdmin, signer, imageRepository, readOnlyTransaction,
                new VenueImageCleanupProperties(Duration.ofHours(24), Duration.ofHours(6), Duration.ofMinutes(5),
                        500, maxAdminCalls, null, null),
                Clock.fixed(NOW, ZoneId.systemDefault()));
    }
}
