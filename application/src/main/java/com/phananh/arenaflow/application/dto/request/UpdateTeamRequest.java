package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.validation.ValidSkillLevels;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.List;

/** PATCH /teams/{id}: như CreateTeamRequest nhưng mọi field tuỳ chọn; teamName nếu có thì không blank. */
public record UpdateTeamRequest(
        @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "Không được để trống") String teamName,
        @URL(regexp = "^https?://.*") @Size(max = 500) String avatarUrl,
        @Size(max = 1000) String description,
        @ValidSkillLevels List<String> skillLevels) {
}
