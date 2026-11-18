package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.AddVenueImageRequest;
import com.phananh.arenaflow.application.dto.response.VenueImageResponse;
import com.phananh.arenaflow.application.dto.response.VenueImageUploadSignatureResponse;
import com.phananh.arenaflow.application.support.security.CurrentActor;

import java.util.UUID;

public interface VenueImageService {

    VenueImageUploadSignatureResponse issueUploadSignature(UUID venueId, CurrentActor actor);

    VenueImageResponse addImage(UUID venueId, CurrentActor actor, AddVenueImageRequest request);
}
