package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CreateVenueRequest;
import com.phananh.arenaflow.application.dto.request.ReportVenueRequest;
import com.phananh.arenaflow.application.dto.request.SearchVenueRequest;
import com.phananh.arenaflow.application.dto.request.UpdateVenueRequest;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.VenueReportResponse;
import com.phananh.arenaflow.application.dto.response.VenueResponse;
import com.phananh.arenaflow.application.dto.response.VenueSummaryResponse;
import com.phananh.arenaflow.application.support.security.CurrentActor;

import java.util.UUID;

public interface VenueService {

    VenueResponse createVenue(UUID callerId, CreateVenueRequest request);

    VenueResponse getVenue(UUID venueId);

    PageResponse<VenueSummaryResponse> listVenues(SearchVenueRequest request);

    VenueResponse updateVenue(UUID venueId, CurrentActor actor, UpdateVenueRequest request);

    VenueReportResponse reportVenue(UUID venueId, CurrentActor actor, ReportVenueRequest request);
}
