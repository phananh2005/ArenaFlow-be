package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.response.ProvinceResponse;
import com.phananh.arenaflow.application.dto.response.WardSummaryResponse;
import com.phananh.arenaflow.application.mapper.AdministrativeUnitMapper;
import com.phananh.arenaflow.application.service.impl.AdministrativeUnitServiceImpl;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.projection.ProvinceView;
import com.phananh.arenaflow.domain.projection.ProvinceWardView;
import com.phananh.arenaflow.domain.repository.AdministrativeUnitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdministrativeUnitServiceImplTest {

    @Mock
    private AdministrativeUnitRepository administrativeUnitRepository;
    @Spy
    private AdministrativeUnitMapper administrativeUnitMapper = Mappers.getMapper(AdministrativeUnitMapper.class);
    @InjectMocks
    private AdministrativeUnitServiceImpl service;

    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final UUID provinceId = UUID.randomUUID();

    private ProvinceWardView row(UUID wardId, String wardName) {
        Map<String, Object> values = new HashMap<>();
        values.put("provinceId", provinceId);
        values.put("wardId", wardId);
        values.put("wardName", wardName);
        return projections.createProjection(ProvinceWardView.class, values);
    }

    @Test
    void listsProvincesInRepositoryOrder() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(administrativeUnitRepository.findProvinces()).thenReturn(List.of(
                projections.createProjection(ProvinceView.class, Map.of("provinceId", first, "provinceName", "Cần Thơ")),
                projections.createProjection(ProvinceView.class, Map.of("provinceId", second, "provinceName", "Đà Nẵng"))));

        assertThat(service.listProvinces()).containsExactly(
                new ProvinceResponse(first, "Cần Thơ"), new ProvinceResponse(second, "Đà Nẵng"));
    }

    @Test
    void emptyCatalogGivesEmptyList() {
        when(administrativeUnitRepository.findProvinces()).thenReturn(List.of());

        assertThat(service.listProvinces()).isEmpty();
    }

    @Test
    void wardsOfUnknownProvinceOrWardIdFailWithUser004() {
        when(administrativeUnitRepository.findWardsOfProvince(provinceId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.listWards(provinceId))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.USER_004);
    }

    @Test
    void provinceWithoutWardsGivesEmptyList() {
        when(administrativeUnitRepository.findWardsOfProvince(provinceId)).thenReturn(List.of(row(null, null)));

        assertThat(service.listWards(provinceId)).isEmpty();
    }

    @Test
    void mapsWardsInRepositoryOrder() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(administrativeUnitRepository.findWardsOfProvince(provinceId))
                .thenReturn(List.of(row(a, "Phường Bến Nghé"), row(b, "Phường Đa Kao")));

        List<WardSummaryResponse> wards = service.listWards(provinceId);

        assertThat(wards).containsExactly(
                new WardSummaryResponse(a, "Phường Bến Nghé"), new WardSummaryResponse(b, "Phường Đa Kao"));
    }
}
