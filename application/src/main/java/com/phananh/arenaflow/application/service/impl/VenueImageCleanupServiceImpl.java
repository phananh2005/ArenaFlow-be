package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.service.VenueImageCleanupService;
import com.phananh.arenaflow.application.support.storage.AssetAdminRateLimitedException;
import com.phananh.arenaflow.application.support.storage.AssetPage;
import com.phananh.arenaflow.application.support.storage.ImageAssetAdmin;
import com.phananh.arenaflow.application.support.storage.ImageUploadSigner;
import com.phananh.arenaflow.application.support.storage.SignedPublicId;
import com.phananh.arenaflow.application.support.venue.VenueImageCleanupProperties;
import com.phananh.arenaflow.domain.repository.VenueImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class VenueImageCleanupServiceImpl implements VenueImageCleanupService {

    private static final int DELETE_BATCH = 100;

    private final ImageAssetAdmin assetAdmin;
    private final ImageUploadSigner imageUploadSigner;
    private final VenueImageRepository venueImageRepository;
    private final TransactionTemplate readOnlyTransaction;
    private final VenueImageCleanupProperties properties;
    private final Clock clock;

    @Autowired
    public VenueImageCleanupServiceImpl(ImageAssetAdmin assetAdmin, ImageUploadSigner imageUploadSigner,
                                        VenueImageRepository venueImageRepository,
                                        PlatformTransactionManager transactionManager,
                                        VenueImageCleanupProperties properties) {
        this(assetAdmin, imageUploadSigner, venueImageRepository, readOnly(transactionManager), properties,
                Clock.systemUTC());
    }

    public VenueImageCleanupServiceImpl(ImageAssetAdmin assetAdmin, ImageUploadSigner imageUploadSigner,
                                        VenueImageRepository venueImageRepository, TransactionTemplate readOnlyTransaction,
                                        VenueImageCleanupProperties properties, Clock clock) {
        this.assetAdmin = assetAdmin;
        this.imageUploadSigner = imageUploadSigner;
        this.venueImageRepository = venueImageRepository;
        this.readOnlyTransaction = readOnlyTransaction;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Duyệt từng trang folder địa điểm; chỉ xoá asset khi thời điểm ký đã quá thời gian ân hạn (không thể xác nhận
     * nữa) và không còn dòng venue_image nào tham chiếu. Lời gọi Admin API chạy ngoài mọi transaction; lỗi storage
     * hoặc database bất kỳ đều kết thúc lượt chạy và lượt sau chạy lại từ đầu.
     */
    @Override
    public int cleanupOrphanAssets() {
        Instant now = clock.instant();
        String prefix = imageUploadSigner.rootPrefix("venues");
        Counts counts = new Counts();
        String cursor = null;
        try {
            do {
                if (counts.adminCalls >= properties.maxAdminCallsPerRun()) {
                    break;
                }
                AssetPage page = assetAdmin.listAssets(prefix, cursor, properties.pageSize());
                counts.adminCalls++;
                counts.scanned += page.publicIds().size();
                List<String> candidates = expiredCandidates(page.publicIds(), now, counts);
                if (!candidates.isEmpty() && !deleteOrphans(candidates, counts)) {
                    break;
                }
                cursor = page.nextCursor();
            } while (cursor != null);
        } catch (AssetAdminRateLimitedException exception) {
            log.warn("Venue image cleanup stopped: storage admin API rate limited until {}", exception.getResetAt());
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden exception) {
            log.error("Venue image cleanup stopped: storage admin API rejected the credentials ({})",
                    exception.getStatusCode().value());
        } catch (RestClientResponseException exception) {
            // Chỉ log status: không ghi response body và request URL.
            log.warn("Venue image cleanup stopped: storage admin API returned HTTP {}", exception.getStatusCode().value());
        } catch (ResourceAccessException exception) {
            // Chỉ log class của nguyên nhân gốc (timeout, DNS, refused): message của nó có thể chứa request URL.
            log.warn("Venue image cleanup stopped: storage admin API unreachable ({})",
                    exception.getMostSpecificCause().getClass().getSimpleName());
        } catch (DataAccessException exception) {
            log.error("Venue image cleanup stopped: database check failed, nothing deleted from that page", exception);
        } catch (RuntimeException exception) {
            log.error("Venue image cleanup stopped: unexpected failure", exception);
        }
        log.info("Venue image cleanup finished: scanned={}, candidates={}, deleted={}, malformed={}, notDeleted={}",
                counts.scanned, counts.candidates, counts.deleted, counts.malformed, counts.notDeleted);
        return counts.deleted;
    }

    private List<String> expiredCandidates(List<String> publicIds, Instant now, Counts counts) {
        List<String> candidates = new ArrayList<>();
        for (String publicId : publicIds) {
            Optional<SignedPublicId> parsed = imageUploadSigner.parsePublicId(publicId);
            if (parsed.isEmpty() || parsed.get().signedAt().isAfter(now.plus(properties.clockSkew()))) {
                counts.malformed++;
            } else if (parsed.get().signedAt().plus(properties.grace()).isBefore(now)) {
                candidates.add(publicId);
            }
        }
        return candidates;
    }

    /** Trả về false khi hết hạn mức lời gọi admin giữa chừng một trang. */
    private boolean deleteOrphans(List<String> candidates, Counts counts) {
        Set<String> referenced = new HashSet<>(readOnlyTransaction.execute(
                status -> venueImageRepository.findExistingPublicIds(candidates)));
        List<String> orphans = candidates.stream().filter(publicId -> !referenced.contains(publicId)).toList();
        counts.candidates += orphans.size();
        for (int from = 0; from < orphans.size(); from += DELETE_BATCH) {
            if (counts.adminCalls >= properties.maxAdminCallsPerRun()) {
                return false;
            }
            List<String> batch = orphans.subList(from, Math.min(from + DELETE_BATCH, orphans.size()));
            Set<String> deleted = assetAdmin.deleteAssets(batch);
            counts.adminCalls++;
            counts.deleted += deleted.size();
            counts.notDeleted += batch.size() - deleted.size();
        }
        return true;
    }

    private static TransactionTemplate readOnly(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        return template;
    }

    private static final class Counts {
        int adminCalls;
        int scanned;
        int candidates;
        int deleted;
        int malformed;
        int notDeleted;
    }
}
