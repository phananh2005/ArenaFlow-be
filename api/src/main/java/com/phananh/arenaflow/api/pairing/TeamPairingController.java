package com.phananh.arenaflow.api.pairing;

import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.PairingSummaryResponse;
import com.phananh.arenaflow.application.service.PairingRoleFilter;
import com.phananh.arenaflow.application.service.PairingService;
import com.phananh.arenaflow.application.service.PairingStatusFilter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Danh sách thương lượng của đội (đội trưởng của đội; đội tạm ngưng vẫn xem được). */
@RestController
@RequestMapping("/api/v1/teams/{teamId}/pairings")
@RequiredArgsConstructor
@Validated
public class TeamPairingController {

    private final PairingService pairingService;

    @GetMapping
    public ResponseEntity<PageResponse<PairingSummaryResponse>> listTeamPairings(@PathVariable UUID teamId,
            @CurrentUser UUID currentUserId,
            @RequestParam(defaultValue = "ALL") PairingRoleFilter role,
            @RequestParam(defaultValue = "ALL") PairingStatusFilter status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ResponseEntity.ok(pairingService.listTeamPairings(teamId, currentUserId, role, status, page, pageSize));
    }
}
