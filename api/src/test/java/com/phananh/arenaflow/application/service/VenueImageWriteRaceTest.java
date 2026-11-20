package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.AddVenueImageRequest;
import com.phananh.arenaflow.application.dto.request.CreateVenueRequest;
import com.phananh.arenaflow.application.support.security.CurrentActor;
import com.phananh.arenaflow.application.support.storage.ImageUploadSigner;
import com.phananh.arenaflow.application.support.storage.SignedPublicId;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.domain.entity.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Các thao tác ghi ảnh trên cùng một địa điểm tranh chấp trên dữ liệu đã commit: hai thread cùng xuất phát qua
 * {@link CyclicBarrier} trong {@value #ROUNDS} vòng, mỗi vòng dùng fixture mới. Kết quả không tất định nên mỗi vòng
 * kiểm bất biến (tối đa năm ảnh, đúng một ảnh chính khi còn ảnh) và tập kết quả cho phép; mọi exception khác
 * {@link BusinessException} (≈ 500) làm test fail. Upload signer được mock vì nó chỉ kiểm kết quả Cloudinary cục bộ
 * và không chạm database.
 */
@ExtendWith(OutputCaptureExtension.class)
class VenueImageWriteRaceTest extends CommittedDataIntegrationTest {

    private static final int ROUNDS = 10;
    private static final String OK = "OK";

    @Autowired private VenueService venueService;
    @Autowired private VenueImageService venueImageService;
    @MockitoBean private ImageUploadSigner imageUploadSigner;

    private final List<UUID> createdVenues = new ArrayList<>();
    private final List<UUID> createdWards = new ArrayList<>();
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        when(imageUploadSigner.isConfigured()).thenReturn(true);
        when(imageUploadSigner.parsePublicId(anyString())).thenAnswer(invocation -> Optional.of(new SignedPublicId(
                "venues/" + invocation.getArgument(0, String.class).split("/")[2], Instant.now(), "nonce", "tag")));
        when(imageUploadSigner.isUploadedBy(any(), any())).thenReturn(true);
        when(imageUploadSigner.verifyUploadResponse(anyString(), anyLong(), anyString())).thenReturn(true);
        when(imageUploadSigner.deliveryUrl(anyString(), anyLong(), anyString())).thenReturn("https://img.test/race");
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        JdbcTemplate cleanup = new JdbcTemplate(jdbcTemplate.getDataSource());
        cleanup.setQueryTimeout(30);
        createdVenues.forEach(venueId -> cleanup.update("DELETE FROM venue WHERE id = ?", venueId));
        createdWards.forEach(wardId -> cleanup.update(
                "DELETE FROM administrative_unit WHERE id = ? OR id = (SELECT parent_id FROM administrative_unit WHERE id = ?)",
                wardId, wardId));
    }

    @Test
    void twoUsersAddingFifthImageKeepAtMostFiveImages() throws Exception {
        Map<String, Integer> stats = new TreeMap<>();
        for (int round = 0; round < ROUNDS; round++) {
            User creator = user("Venue Race Fifth Creator " + round);
            User other = user("Venue Race Fifth Other " + round);
            UUID venueId = venue(creator);
            for (int order = 1; order <= 4; order++) {
                insertImage(venueId, creator, order == 1, order);
            }

            List<String> outcomes = race(() -> addImage(venueId, creator, false), () -> addImage(venueId, other, false));

            record(stats, outcomes);
            assertThat(outcomes).as("round %d", round).containsExactlyInAnyOrder(OK, "VENUE_003");
            assertThat(imageCount(venueId)).as("round %d", round).isEqualTo(5);
            assertThat(primaryCount(venueId)).as("round %d", round).isEqualTo(1);
        }
        report("fifth image", stats);
    }

    @Test
    void twoPrimaryAddsLeaveExactlyOnePrimary() throws Exception {
        Map<String, Integer> stats = new TreeMap<>();
        for (int round = 0; round < ROUNDS; round++) {
            User creator = user("Venue Race Primary Creator " + round);
            UUID venueId = venue(creator);
            insertImage(venueId, creator, true, 1);

            List<String> outcomes = race(() -> addImage(venueId, creator, true), () -> addImage(venueId, creator, true));

            record(stats, outcomes);
            assertThat(outcomes).as("round %d", round).containsExactly(OK, OK);
            assertThat(imageCount(venueId)).as("round %d", round).isEqualTo(3);
            assertThat(primaryCount(venueId)).as("round %d", round).isEqualTo(1);
        }
        report("two primary adds", stats);
    }

    @Test
    void addPrimaryVersusDeletePrimaryLeavesExactlyOnePrimary() throws Exception {
        Map<String, Integer> stats = new TreeMap<>();
        for (int round = 0; round < ROUNDS; round++) {
            User creator = user("Venue Race AddDelete Creator " + round);
            UUID venueId = venue(creator);
            UUID primary = insertImage(venueId, creator, true, 1);
            insertImage(venueId, creator, false, 2);
            CurrentActor actor = new CurrentActor(creator.getId(), false);

            List<String> outcomes = race(() -> addImage(venueId, creator, true),
                    () -> venueImageService.deleteImage(venueId, primary, actor));

            record(stats, outcomes);
            assertThat(outcomes).as("round %d", round).containsExactly(OK, OK);
            assertThat(imageCount(venueId)).as("round %d", round).isEqualTo(2);
            assertThat(primaryCount(venueId)).as("round %d", round).isEqualTo(1);
        }
        report("add primary vs delete primary", stats);
    }

    @Test
    void setPrimaryVersusDeleteOfThatImageLeavesExactlyOnePrimary() throws Exception {
        Map<String, Integer> stats = new TreeMap<>();
        for (int round = 0; round < ROUNDS; round++) {
            User creator = user("Venue Race SetDelete Creator " + round);
            UUID venueId = venue(creator);
            insertImage(venueId, creator, true, 1);
            UUID target = insertImage(venueId, creator, false, 2);
            CurrentActor actor = new CurrentActor(creator.getId(), false);

            List<String> outcomes = race(() -> venueImageService.setPrimaryImage(venueId, target, actor),
                    () -> venueImageService.deleteImage(venueId, target, actor));

            record(stats, outcomes);
            assertThat(outcomes.get(0)).as("set-primary, round %d", round).isIn(OK, "VENUE_004");
            assertThat(outcomes.get(1)).as("delete, round %d", round).isEqualTo(OK);
            assertThat(imageCount(venueId)).as("round %d", round).isEqualTo(1);
            assertThat(primaryCount(venueId)).as("round %d", round).isEqualTo(1);
        }
        report("set-primary vs delete", stats);
    }

    @Test
    void doubleDeleteOfSameImageLetsExactlyOneWin() throws Exception {
        Map<String, Integer> stats = new TreeMap<>();
        for (int round = 0; round < ROUNDS; round++) {
            User creator = user("Venue Race DoubleDelete Creator " + round);
            UUID venueId = venue(creator);
            UUID primary = insertImage(venueId, creator, true, 1);
            insertImage(venueId, creator, false, 2);
            CurrentActor actor = new CurrentActor(creator.getId(), false);

            List<String> outcomes = race(() -> venueImageService.deleteImage(venueId, primary, actor),
                    () -> venueImageService.deleteImage(venueId, primary, actor));

            record(stats, outcomes);
            assertThat(outcomes).as("round %d", round).containsExactlyInAnyOrder(OK, "VENUE_004");
            assertThat(imageCount(venueId)).as("round %d", round).isEqualTo(1);
            assertThat(primaryCount(venueId)).as("round %d", round).isEqualTo(1);
        }
        report("double delete", stats);
    }

    @Test
    void failedSetPrimaryRollsBackClearedPrimary() {
        User creator = user("Venue Rollback Creator");
        UUID venueId = venue(creator);
        UUID primary = insertImage(venueId, creator, true, 1);
        UUID otherVenueImage = insertImage(venue(creator), creator, true, 1);

        String outcome = outcome(() -> venueImageService.setPrimaryImage(venueId, otherVenueImage,
                new CurrentActor(creator.getId(), false)));

        assertThat(outcome).isEqualTo("VENUE_004");
        assertThat(jdbcTemplate.queryForObject("SELECT is_primary FROM venue_image WHERE id = ?", Boolean.class, primary))
                .isTrue();
    }

    @Test
    void adminDeleteOfOthersImageWritesAuditOnlyAfterCommit(CapturedOutput output) {
        User creator = user("Venue Audit Creator");
        User admin = user("Venue Audit Admin", User.Role.ADMIN);
        UUID venueId = venue(creator);
        UUID image = insertImage(venueId, creator, true, 1);
        CurrentActor adminActor = new CurrentActor(admin.getId(), true);
        String auditLine = "Admin action: action=ADMIN_DELETE_IMAGE, adminId=" + admin.getId() + ", venueId=" + venueId;

        assertThat(outcome(() -> venueImageService.deleteImage(venueId, UUID.randomUUID(), adminActor)))
                .isEqualTo("VENUE_004");
        assertThat(output.getAll()).doesNotContain(auditLine);

        assertThat(outcome(() -> venueImageService.deleteImage(venueId, image, adminActor))).isEqualTo(OK);
        assertThat(output.getAll()).containsOnlyOnce(auditLine);
        assertThat(output.getAll().lines().filter(line -> line.contains("Admin action:")))
                .singleElement().asString().doesNotContain(admin.getDisplayName());
    }

    private Object addImage(UUID venueId, User actor, boolean primary) {
        return venueImageService.addImage(venueId, new CurrentActor(actor.getId(), false), new AddVenueImageRequest(
                "race/venues/" + venueId + "/" + UUID.randomUUID(), 1L, "png", "signature", primary));
    }

    private UUID venue(User creator) {
        UUID provinceId = UUID.randomUUID();
        UUID wardId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO administrative_unit (id, name, level) VALUES (?, 'Race Province', 'province')",
                provinceId);
        jdbcTemplate.update("INSERT INTO administrative_unit (id, name, level, parent_id) VALUES (?, 'Race Ward', 'ward', ?)",
                wardId, provinceId);
        createdWards.add(wardId);
        UUID venueId = venueService.createVenue(creator.getId(), new CreateVenueRequest("Race Venue", "Race Address",
                new BigDecimal("10.7881234"), new BigDecimal("106.6991234"), wardId, null, null, null)).venueId();
        createdVenues.add(venueId);
        return venueId;
    }

    private UUID insertImage(UUID venueId, User uploader, boolean primary, int displayOrder) {
        UUID imageId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO venue_image (id, venue_id, image_url, public_id, is_primary, display_order, uploaded_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", imageId, venueId, "https://img.test/" + imageId,
                "race/venues/" + venueId + "/" + imageId, primary, displayOrder, uploader.getId());
        return imageId;
    }

    private int imageCount(UUID venueId) {
        return count("SELECT COUNT(*) FROM venue_image WHERE venue_id = ?", venueId);
    }

    private int primaryCount(UUID venueId) {
        return count("SELECT COUNT(*) FROM venue_image WHERE venue_id = ? AND is_primary", venueId);
    }

    private List<String> race(Callable<?> first, Callable<?> second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        Future<String> a = executor.submit(() -> {
            barrier.await(30, SECONDS);
            return outcome(first);
        });
        Future<String> b = executor.submit(() -> {
            barrier.await(30, SECONDS);
            return outcome(second);
        });
        return List.of(a.get(60, SECONDS), b.get(60, SECONDS));
    }

    private static String outcome(Callable<?> call) {
        try {
            call.call();
            return OK;
        } catch (BusinessException exception) {
            return exception.getErrorCode().name();
        } catch (Exception unexpected) {
            throw new AssertionError("unexpected non-business failure (≈ 500)", unexpected);
        }
    }

    private static void record(Map<String, Integer> stats, List<String> outcomes) {
        stats.merge(String.join(" | ", outcomes), 1, Integer::sum);
    }

    private static void report(String scenario, Map<String, Integer> stats) {
        System.out.println("[RACE] " + scenario + " → " + stats);
    }
}
