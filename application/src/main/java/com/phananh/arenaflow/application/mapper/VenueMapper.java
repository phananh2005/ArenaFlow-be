package com.phananh.arenaflow.application.mapper;

import com.phananh.arenaflow.application.dto.response.AdminVenueResponse;
import com.phananh.arenaflow.application.dto.response.VenueImageResponse;
import com.phananh.arenaflow.application.dto.response.VenueResponse;
import com.phananh.arenaflow.application.dto.response.VenueSummaryResponse;
import com.phananh.arenaflow.application.dto.response.WardResponse;
import com.phananh.arenaflow.domain.entity.venue.VenueImage;
import com.phananh.arenaflow.domain.projection.VenueDetailView;
import com.phananh.arenaflow.domain.projection.VenueSummaryView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Mapper(componentModel = "spring", uses = MapperSupport.class, imports = MapperSupport.class,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface VenueMapper {

    @Mapping(target = "venueId", source = "view.venueId")
    @Mapping(target = "venueName", source = "view.venueName")
    @Mapping(target = "createdByName",
            expression = "java(MapperSupport.maskName(view.getCreatedByName(), view.getCreatedByDeleted()))")
    @Mapping(target = "ward", expression = "java(toWard(view))")
    @Mapping(target = "lastSelectedAt", expression = "java(MapperSupport.toUtc(view.getLastSelectedAt()))")
    @Mapping(target = "adminVerifiedInactiveAt", expression = "java(MapperSupport.toUtc(view.getAdminVerifiedInactiveAt()))")
    @Mapping(target = "createdAt", expression = "java(MapperSupport.toUtc(view.getCreatedAt()))")
    @Mapping(target = "updatedAt", expression = "java(MapperSupport.toUtc(view.getUpdatedAt()))")
    VenueResponse toResponse(VenueDetailView view, List<VenueImage> images);

    @Mapping(target = "venueId", source = "view.venueId")
    @Mapping(target = "venueName", source = "view.venueName")
    @Mapping(target = "isCreatedByDeleted", source = "view.createdByDeleted")
    @Mapping(target = "ward", expression = "java(toWard(view))")
    @Mapping(target = "lastSelectedAt", expression = "java(MapperSupport.toUtc(view.getLastSelectedAt()))")
    @Mapping(target = "adminVerifiedInactiveAt", expression = "java(MapperSupport.toUtc(view.getAdminVerifiedInactiveAt()))")
    @Mapping(target = "createdAt", expression = "java(MapperSupport.toUtc(view.getCreatedAt()))")
    @Mapping(target = "updatedAt", expression = "java(MapperSupport.toUtc(view.getUpdatedAt()))")
    AdminVenueResponse toAdminResponse(VenueDetailView view, List<VenueImage> images);

    @Mapping(target = "venueId", source = "view.venueId")
    @Mapping(target = "venueName", source = "view.venueName")
    @Mapping(target = "distanceKm", expression = "java(roundDistance(view.getDistanceKm()))")
    @Mapping(target = "lastSelectedAt", expression = "java(MapperSupport.toUtc(view.getLastSelectedAt()))")
    VenueSummaryResponse toSummary(VenueSummaryView view);

    @Mapping(target = "imageId", source = "id")
    @Mapping(target = "venueId", source = "venueId")
    @Mapping(target = "uploadedById", source = "uploadedBy")
    @Mapping(target = "createdAt", expression = "java(image.getCreatedAt())")
    VenueImageResponse toImage(VenueImage image);

    default WardResponse toWard(VenueDetailView view) {
        return view.getWardId() == null ? null
                : new WardResponse(view.getWardId(), view.getWardName(), view.getProvinceId(), view.getProvinceName());
    }

    default BigDecimal roundDistance(Double distanceKm) {
        return distanceKm == null ? null : BigDecimal.valueOf(distanceKm).setScale(2, RoundingMode.HALF_UP);
    }
}
