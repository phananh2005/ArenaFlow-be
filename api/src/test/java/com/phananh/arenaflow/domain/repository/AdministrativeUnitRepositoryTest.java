package com.phananh.arenaflow.domain.repository;

import com.phananh.arenaflow.domain.projection.ProvinceView;
import com.phananh.arenaflow.domain.projection.ProvinceWardView;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdministrativeUnitRepositoryTest extends RepositoryIntegrationTest {

    private static final UUID HCM_PROVINCE = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID HA_NOI_PROVINCE = UUID.fromString("00000000-0000-7000-8000-000000000002");
    private static final UUID BEN_NGHE_WARD = UUID.fromString("10000000-0000-7000-8000-000000000001");

    @Autowired
    private AdministrativeUnitRepository administrativeUnitRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void listsProvincesInVietnameseAlphabeticalOrderInOneQuery() {
        Statistics statistics = resetStatistics();

        List<ProvinceView> provinces = administrativeUnitRepository.findProvinces();

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(provinces).extracting(ProvinceView::getProvinceName)
                .containsSubsequence("Cần Thơ", "Đà Nẵng", "Hà Nội", "Hải Phòng", "TP. Hồ Chí Minh");
        assertThat(provinces).extracting(ProvinceView::getProvinceId).doesNotContain(BEN_NGHE_WARD);
    }

    @Test
    void listsOnlyWardsOfRequestedProvinceSortedByName() {
        List<ProvinceWardView> wards = administrativeUnitRepository.findWardsOfProvince(HCM_PROVINCE);

        assertThat(wards).isNotEmpty();
        assertThat(wards).allSatisfy(ward -> {
            assertThat(ward.getProvinceId()).isEqualTo(HCM_PROVINCE);
            assertThat(ward.getWardId()).isNotNull();
        });
        assertThat(wards).extracting(ProvinceWardView::getWardName).contains("Phường Bến Nghé");
        assertThat(administrativeUnitRepository.findWardsOfProvince(HA_NOI_PROVINCE))
                .extracting(ProvinceWardView::getWardId).doesNotContain(BEN_NGHE_WARD);
        List<String> names = wards.stream().map(ProvinceWardView::getWardName).toList();
        assertThat(names.indexOf("Phường Đa Kao")).isLessThan(names.indexOf("Phường Sài Gòn"));
    }

    @Test
    void returnsNoRowForWardIdOrUnknownId() {
        assertThat(administrativeUnitRepository.findWardsOfProvince(BEN_NGHE_WARD)).isEmpty();
        assertThat(administrativeUnitRepository.findWardsOfProvince(UUID.randomUUID())).isEmpty();
    }

    @Test
    void returnsSingleRowWithNullWardForProvinceWithoutWards() {
        UUID emptyProvince = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO administrative_unit (id, name, level) VALUES (?, 'Tỉnh Kiểm Thử', 'province')",
                emptyProvince);

        List<ProvinceWardView> rows = administrativeUnitRepository.findWardsOfProvince(emptyProvince);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getProvinceId()).isEqualTo(emptyProvince);
            assertThat(row.getWardId()).isNull();
        });
    }
}
