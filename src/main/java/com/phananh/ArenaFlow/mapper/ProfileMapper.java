package com.phananh.ArenaFlow.mapper;

import com.phananh.ArenaFlow.dto.response.ProfileResponse;
import com.phananh.ArenaFlow.dto.response.ProviderResponse;
import com.phananh.ArenaFlow.dto.response.WardResponse;
import com.phananh.ArenaFlow.entity.user.OAuthAccount;
import com.phananh.ArenaFlow.repository.projection.ProfileView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.Locale;

@Mapper(componentModel = "spring", uses = MapperSupport.class, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProfileMapper {

    @Mapping(target = "providers", source = "accounts")
    @Mapping(target = "ward", source = "view")
    ProfileResponse toResponse(ProfileView view, List<OAuthAccount> accounts);

    ProviderResponse toProvider(OAuthAccount account);

    /** DB lưu lowercase (google/facebook/phone) → API UPPERCASE. */
    default String provider(OAuthAccount.Provider provider) {
        return provider == null ? null : provider.name().toUpperCase(Locale.ROOT);
    }

    /** ward = null khi user chưa chọn phường/xã. */
    default WardResponse ward(ProfileView view) {
        if (view == null || view.getWardId() == null) {
            return null;
        }
        return new WardResponse(view.getWardId(), view.getWardName(), view.getProvinceId(), view.getProvinceName());
    }
}
