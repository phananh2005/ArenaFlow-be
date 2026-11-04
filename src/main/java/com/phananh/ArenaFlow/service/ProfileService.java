package com.phananh.ArenaFlow.service;

import com.phananh.ArenaFlow.dto.request.ProfileUpdateRequest;
import com.phananh.ArenaFlow.dto.response.ProfileResponse;

import java.util.UUID;

public interface ProfileService {

    ProfileResponse getProfile(UUID userId);

    ProfileResponse updateProfile(UUID userId, ProfileUpdateRequest request);

    void deleteAccount(UUID userId);
}
