package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.AddVenueImageRequest;
import com.phananh.arenaflow.application.dto.request.CreateVenueRequest;
import com.phananh.arenaflow.application.dto.request.ReportVenueRequest;
import com.phananh.arenaflow.application.dto.request.ResolveVenueReportRequest;
import com.phananh.arenaflow.application.dto.request.UpdateVenueRequest;
import com.phananh.arenaflow.application.event.VenueAdminAlertEvent;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
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
 * Các thao tác ghi Report và Admin tranh chấp trên dữ liệu đã commit (mỗi loại {@value #ROUNDS} vòng, fixture mới,
 * xuất phát qua barrier). Mỗi vòng kiểm bất biến đã lưu và tập kết quả cho phép; mọi exception không phải nghiệp vụ
 * (≈ 500) làm test fail. Sự kiện cảnh báo Admin do listener test sau commit ghi lại nên chỉ đếm sự kiện đã commit.
 */
@ExtendWith(OutputCaptureExtension.class)
class VenueReportAdminRaceTest extends CommittedDataIntegrationTest {

    private static final int ROUNDS = 10;
    private static final String OK = "OK";

    @Autowired private VenueService venueService;
    @Autowired private VenueImageService venueImageService;
    @Autowired private AdminVenueService adminVenueService;
    @Autowired private AlertRecorder alertRecorder;
    @MockitoBean private ImageUploadSigner imageUploadSigner;

    private final List<UUID> createdVenues = new ArrayList<>();
    private final List<UUID> createdWards = new ArrayList<>();
    private ExecutorService executor;

    @TestConfiguration
    static class AlertRecorderConfig {
        @Bean
        AlertRecorder alertRecorder() {
            return new AlertRecorder();
        }
    }

    static class AlertRecorder {
        final List<VenueAdminAlertEvent> events = new CopyOnWriteArrayList<>();

        @TransactionalEventListener
        void on(VenueAdminAlertEvent event) {
            events.add(event);
        }

        long countFor(UUID venueId) {
            return events.stream().filter(event -> event.venueId().equals(venueId)).count();
        }
    }

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
    void doubleSubmitReportLetsExactlyOnePendingReportThrough() throws Exception {
        Map<String, Integer> stats = new TreeMap<>();
        for (int round = 0; round < ROUNDS; round++) {
            User reporter = user("Report Race Double " + round);
            UUID venueId = venue(user("Report Race Double Creator " + round));
            CurrentActor actor = new CurrentActor(reporter.getId(), false);

            List<String> outcomes = race(() -> venueService.reportVenue(venueId, actor, new ReportVenueRequest("CLOSED", null)),
                    () -> venueService.reportVenue(venueId, actor, new ReportVenueRequest("CLOSED", null)));

            record(stats, outcomes);
            assertThat(outcomes).as("round %d", round).containsExactlyInAnyOrder(OK, "VENUE_014");
            assertThat(count("SELECT COUNT(*) FROM venue_report WHERE venue_id = ?", venueId)).isEqualTo(1);
            assertThat(alertRecorder.countFor(venueId)).as("round %d", round).isEqualTo(1);
        }
        report("double-submit report", stats);
    }

    @Test
    void twoClosedReportsFromDifferentUsersPublishOneAdminAlert() throws Exception {
        Map<String, Integer> stats = new TreeMap<>();
        for (int round = 0; round < ROUNDS; round++) {
            CurrentActor first = new CurrentActor(user("Report Race Closed A " + round).getId(), false);
            CurrentActor second = new CurrentActor(user("Report Race Closed B " + round).getId(), false);
            UUID venueId = venue(user("Report Race Closed Creator " + round));

            List<String> outcomes = race(() -> venueService.reportVenue(venueId, first, new ReportVenueRequest("CLOSED", null)),
                    () -> venueService.reportVenue(venueId, second, new ReportVenueRequest("CLOSED", null)));

            record(stats, outcomes);
            assertThat(outcomes).as("round %d", round).containsExactly(OK, OK);
            assertThat(alertRecorder.countFor(venueId)).as("round %d", round).isEqualTo(1);
        }
        report("two CLOSED reports", stats);
    }

    @Test
    void twoAdminsResolvingSameReportLetExactlyOneWin() throws Exception {
        Map<String, Integer> stats = new TreeMap<>();
        for (int round = 0; round < ROUNDS; round++) {
            UUID venueId = venue(user("Resolve Race Creator " + round));
            UUID reportId = venueService.reportVenue(venueId, new CurrentActor(user("Resolve Race Reporter " + round).getId(),
                    false), new ReportVenueRequest("WRONG_INFO", null)).reportId();
            UUID adminA = user("Resolve Race Admin A " + round, User.Role.ADMIN).getId();
            UUID adminB = user("Resolve Race Admin B " + round, User.Role.ADMIN).getId();

            List<String> outcomes = race(
                    () -> adminVenueService.resolveReport(reportId, adminA, new ResolveVenueReportRequest("VERIFIED")),
                    () -> adminVenueService.resolveReport(reportId, adminB, new ResolveVenueReportRequest("REJECTED")));

            record(stats, outcomes);
            assertThat(outcomes).as("round %d", round).containsExactlyInAnyOrder(OK, "VENUE_010");
            String expected = OK.equals(outcomes.get(0)) ? "VERIFIED" : "REJECTED";
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM venue_report WHERE id = ?", String.class, reportId))
                    .as("round %d", round).isEqualTo(expected);
        }
        report("double resolve", stats);
    }

    @Test
    void resolveClosedReportVersusMarkInactiveNeverFailsWith500() throws Exception {
        Map<String, Integer> stats = new TreeMap<>();
        for (int round = 0; round < ROUNDS; round++) {
            UUID venueId = venue(user("Resolve Inactive Creator " + round));
            UUID reportId = venueService.reportVenue(venueId, new CurrentActor(user("Resolve Inactive Reporter " + round)
                    .getId(), false), new ReportVenueRequest("CLOSED", null)).reportId();
            UUID adminA = user("Resolve Inactive Admin A " + round, User.Role.ADMIN).getId();
            UUID adminB = user("Resolve Inactive Admin B " + round, User.Role.ADMIN).getId();

            List<String> outcomes = race(
                    () -> adminVenueService.resolveReport(reportId, adminA, new ResolveVenueReportRequest("REJECTED")),
                    () -> adminVenueService.markInactive(venueId, adminB));

            record(stats, outcomes);
            assertThat(outcomes.get(0)).as("resolve, round %d", round).isIn(OK, "VENUE_010", "VENUE_011");
            assertThat(outcomes.get(1)).as("mark-inactive, round %d", round).isIn(OK, "VENUE_011");
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM venue_report WHERE id = ?", String.class, reportId))
                    .as("round %d", round).isIn("VERIFIED", "REJECTED");
        }
        report("resolve CLOSED vs mark-inactive", stats);
    }

    @Test
    void imageWriteVersusMarkInactiveRejectsUserOnlyWhenInactiveCommittedFirstAndAuditsOnce(CapturedOutput output)
            throws Exception {
        Map<String, Integer> stats = new TreeMap<>();
        for (int round = 0; round < ROUNDS; round++) {
            User creator = user("Image Inactive Creator " + round);
            UUID venueId = venue(creator);
            UUID adminId = user("Image Inactive Admin " + round, User.Role.ADMIN).getId();

            List<String> outcomes = race(() -> addImage(venueId, new CurrentActor(creator.getId(), false)),
                    () -> adminVenueService.markInactive(venueId, adminId));

            record(stats, outcomes);
            assertThat(outcomes.get(0)).as("add image, round %d", round).isIn(OK, "VENUE_002");
            assertThat(outcomes.get(1)).as("mark-inactive, round %d", round).isIn(OK, "VENUE_011");
            if (OK.equals(outcomes.get(1))) {
                assertThat(venueStatus(venueId)).isEqualTo("INACTIVE");
                assertThat(output.getAll()).as("round %d", round).containsOnlyOnce(
                        "Admin action: action=MARK_INACTIVE, adminId=" + adminId + ", venueId=" + venueId);
            }

            assertThat(outcome(() -> addImage(venueId, new CurrentActor(adminId, true))))
                    .as("admin image write after race, round %d", round).isEqualTo(OK);
        }
        report("image write vs mark-inactive", stats);
    }

    @Test
    void updateVenueVersusImageWriteRetriesWithoutLosingEitherChange() throws Exception {
        Map<String, Integer> stats = new TreeMap<>();
        for (int round = 0; round < ROUNDS; round++) {
            User creator = user("Update Image Creator " + round);
            UUID venueId = venue(creator);
            CurrentActor actor = new CurrentActor(creator.getId(), false);
            String newName = "Updated " + round;

            List<String> outcomes = race(() -> venueService.updateVenue(venueId, actor,
                            new UpdateVenueRequest(newName, null, null, null, null, null, null, null)),
                    () -> addImage(venueId, actor));

            record(stats, outcomes);
            assertThat(outcomes).as("round %d", round).containsExactly(OK, OK);
            assertThat(jdbcTemplate.queryForObject("SELECT name FROM venue WHERE id = ?", String.class, venueId))
                    .isEqualTo(newName);
            assertThat(count("SELECT COUNT(*) FROM venue_image WHERE venue_id = ?", venueId)).isEqualTo(1);
        }
        report("update vs image write", stats);
    }

    private Object addImage(UUID venueId, CurrentActor actor) {
        return venueImageService.addImage(venueId, actor, new AddVenueImageRequest(
                "race/venues/" + venueId + "/" + UUID.randomUUID(), 1L, "png", "signature", false));
    }

    private String venueStatus(UUID venueId) {
        return jdbcTemplate.queryForObject("SELECT status FROM venue WHERE id = ?", String.class, venueId);
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
