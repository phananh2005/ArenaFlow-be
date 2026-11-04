package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.ProfileUpdateRequest;
import com.phananh.arenaflow.application.dto.response.ProfileResponse;

import java.util.UUID;

public interface ProfileService {

    ProfileResponse getProfile(UUID userId);

    ProfileResponse updateProfile(UUID userId, ProfileUpdateRequest request);

    void deleteAccount(UUID userId);
}
