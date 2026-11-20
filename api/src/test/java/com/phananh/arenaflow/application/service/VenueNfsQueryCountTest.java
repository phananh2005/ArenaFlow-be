package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.AddVenueImageRequest;
import com.phananh.arenaflow.application.dto.request.CreateVenueRequest;
import com.phananh.arenaflow.application.dto.request.ReportVenueRequest;
import com.phananh.arenaflow.application.dto.request.ResolveVenueReportRequest;
import com.phananh.arenaflow.application.dto.request.SearchVenueRequest;
import com.phananh.arenaflow.application.dto.request.UpdateVenueRequest;
import com.phananh.arenaflow.application.support.security.CurrentActor;
import com.phananh.arenaflow.application.support.storage.ImageUploadSigner;
import com.phananh.arenaflow.application.support.storage.SignedPublicId;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Đo số câu SQL thật qua Hibernate Statistics cho các luồng service của API lõi VENUE.
 * Fixture danh sách có nhiều dòng hơn kích thước trang, buộc Spring Data chạy truy vấn count.
 * Upload signer được mock: nó kiểm kết quả Cloudinary cục bộ, không chạy SQL nên không ảnh hưởng số đếm.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class VenueNfsQueryCountTest {

    private static final int DETAIL_LIMIT = 3;
    private static final int LIST_LIMIT = 4;
    private static final int WRITE_LIMIT = 6;

    @Autowired private VenueService venueService;
    @Autowired private VenueImageService venueImageService;
    @Autowired private AdminVenueService adminVenueService;
    @MockitoBean private ImageUploadSigner imageUploadSigner;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Statistics statistics;
    private User creator;
    private UUID wardId;
    private UUID venueId;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        creator = userRepository.save(User.builder().displayName("Venue NFS Creator").build());
        wardId = insertWard();
        entityManager.flush();
        entityManager.clear();

        venueId = venueService.createVenue(creator.getId(), createRequest("Venue NFS One")).venueId();
        venueService.createVenue(creator.getId(), createRequest("Venue NFS Two"));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void coreVenuePathsStayWithinApprovedQueryBudgetsWithoutNPlusOne() {
        assertThat(measure(() -> venueService.createVenue(creator.getId(), createRequest("Venue NFS Three"))))
                .as("POST /api/v1/venues")
                .isEqualTo(3)
                .isLessThanOrEqualTo(WRITE_LIMIT);

        assertThat(measure(() -> venueService.listVenues(new SearchVenueRequest(
                null, null, null, null, null, 1, 2))))
                .as("GET /api/v1/venues")
                .isEqualTo(2)
                .isLessThanOrEqualTo(LIST_LIMIT);

        assertThat(measure(() -> venueService.getVenue(venueId)))
                .as("GET /api/v1/venues/{venueId}")
                .isEqualTo(2)
                .isLessThanOrEqualTo(DETAIL_LIMIT);

        assertThat(measure(() -> venueService.updateVenue(venueId, new CurrentActor(creator.getId(), false),
                new UpdateVenueRequest("Venue NFS Updated", null, null, null, null, null, null, null))))
                .as("PATCH /api/v1/venues/{venueId}")
                .isBetween(3L, 5L)
                .isLessThanOrEqualTo(WRITE_LIMIT);
    }

    @Test
    void imageWritePathsStayWithinApprovedWriteBudget() {
        UUID oldPrimary = insertImage(true, 1);
        UUID other = insertImage(false, 2);
        CurrentActor actor = new CurrentActor(creator.getId(), false);
        when(imageUploadSigner.isConfigured()).thenReturn(true);
        when(imageUploadSigner.parsePublicId(anyString())).thenReturn(Optional.of(
                new SignedPublicId("venues/" + venueId, Instant.now(), "nonce", "tag")));
        when(imageUploadSigner.isUploadedBy(any(), any())).thenReturn(true);
        when(imageUploadSigner.verifyUploadResponse(anyString(), anyLong(), anyString())).thenReturn(true);
        when(imageUploadSigner.deliveryUrl(anyString(), anyLong(), anyString())).thenReturn("https://img.test/nfs");

        assertThat(measure(() -> venueImageService.addImage(venueId, actor, new AddVenueImageRequest(
                "nfs/venues/" + UUID.randomUUID(), 1L, "png", "signature", true))))
                .as("POST /api/v1/venues/{venueId}/images replacing primary %s", oldPrimary)
                .isEqualTo(5)
                .isLessThanOrEqualTo(WRITE_LIMIT);

        assertThat(measure(() -> venueImageService.setPrimaryImage(venueId, other, actor)))
                .as("PATCH /api/v1/venues/{venueId}/images/{imageId}/set-primary")
                .isEqualTo(5)
                .isLessThanOrEqualTo(WRITE_LIMIT);

        assertThat(measure(() -> venueImageService.deleteImage(venueId, other, actor)))
                .as("DELETE /api/v1/venues/{venueId}/images/{imageId} deleting primary")
                .isEqualTo(6)
                .isLessThanOrEqualTo(WRITE_LIMIT);
    }

    @Test
    void reportAndAdminPathsStayWithinApprovedQueryBudgets() {
        User reporter = userRepository.save(User.builder().displayName("Venue NFS Reporter").build());
        User admin = userRepository.save(User.builder().displayName("Venue NFS Admin").role(User.Role.ADMIN).build());
        CurrentActor reporterActor = new CurrentActor(reporter.getId(), false);

        assertThat(measure(() -> venueService.reportVenue(venueId, reporterActor, new ReportVenueRequest("CLOSED", null))))
                .as("POST /api/v1/venues/{venueId}/reports opening closure alert")
                .isEqualTo(3)
                .isLessThanOrEqualTo(WRITE_LIMIT);
        UUID wrongInfoReport = venueService.reportVenue(venueId, reporterActor,
                new ReportVenueRequest("WRONG_INFO", null)).reportId();
        assertThat(measure(() -> venueService.reportVenue(venueId, reporterActor, new ReportVenueRequest("OTHER", null))))
                .as("POST /api/v1/venues/{venueId}/reports without closure gate")
                .isEqualTo(2)
                .isLessThanOrEqualTo(WRITE_LIMIT);

        assertThat(measure(() -> adminVenueService.listReports(VenueReportStatusFilter.PENDING, venueId, 1, 1)))
                .as("GET /api/v1/admin/venue-reports")
                .isEqualTo(2)
                .isLessThanOrEqualTo(LIST_LIMIT);

        assertThat(measure(() -> adminVenueService.resolveReport(wrongInfoReport, admin.getId(),
                new ResolveVenueReportRequest("REJECTED"))))
                .as("PATCH /api/v1/admin/venue-reports/{reportId}/resolve non-CLOSED")
                .isEqualTo(3)
                .isLessThanOrEqualTo(WRITE_LIMIT);

        assertThat(measure(() -> adminVenueService.markInactive(venueId, admin.getId())))
                .as("PATCH /api/v1/admin/venues/{venueId}/mark-inactive")
                .isEqualTo(5)
                .isLessThanOrEqualTo(WRITE_LIMIT);

        assertThat(measure(() -> adminVenueService.reactivate(venueId, admin.getId())))
                .as("PATCH /api/v1/admin/venues/{venueId}/reactivate")
                .isEqualTo(4)
                .isLessThanOrEqualTo(WRITE_LIMIT);

        UUID closedReport = venueService.reportVenue(venueId, reporterActor, new ReportVenueRequest("CLOSED", null)).reportId();
        assertThat(measure(() -> adminVenueService.resolveReport(closedReport, admin.getId(),
                new ResolveVenueReportRequest("VERIFIED"))))
                .as("PATCH /api/v1/admin/venue-reports/{reportId}/resolve CLOSED")
                .isEqualTo(4)
                .isLessThanOrEqualTo(WRITE_LIMIT);
    }

    private UUID insertImage(boolean primary, int displayOrder) {
        UUID imageId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO venue_image (id, venue_id, image_url, public_id, is_primary, display_order, uploaded_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", imageId, venueId, "https://img.test/" + imageId,
                "nfs/venues/" + imageId, primary, displayOrder, creator.getId());
        return imageId;
    }

    private long measure(Supplier<?> operation) {
        entityManager.flush();
        entityManager.clear();
        statistics.clear();
        operation.get();
        entityManager.flush();
        return statistics.getPrepareStatementCount();
    }

    private CreateVenueRequest createRequest(String name) {
        return new CreateVenueRequest(name, "NFS Address", new BigDecimal("10.7881234"),
                new BigDecimal("106.6991234"), wardId, null, null, null);
    }

    private UUID insertWard() {
        UUID provinceId = UUID.randomUUID();
        UUID newWardId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO administrative_unit (id, name, level) VALUES (?, 'NFS Province', 'province')",
                provinceId);
        jdbcTemplate.update("INSERT INTO administrative_unit (id, name, level, parent_id) VALUES (?, 'NFS Ward', 'ward', ?)",
                newWardId, provinceId);
        return newWardId;
    }
}
