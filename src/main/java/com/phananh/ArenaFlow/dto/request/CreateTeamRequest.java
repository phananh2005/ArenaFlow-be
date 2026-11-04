package com.phananh.ArenaFlow.dto.request;

import com.phananh.ArenaFlow.validation.ValidSkillLevels;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.List;

public record CreateTeamRequest(
        @NotBlank @Size(max = 100) String teamName,
        @URL(regexp = "^https?://.*") @Size(max = 500) String avatarUrl,
        @Size(max = 1000) String description,
        @ValidSkillLevels List<String> skillLevels) {
}
