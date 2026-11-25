package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.validation.OneOf;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code expectedStatus}: trạng thái yêu cầu client đang hiển thị — khác trạng thái hiện tại → MATCH_015. */
public record WithdrawMatchRequestRequest(
        @NotNull @OneOf({"PENDING", "ACCEPTED"}) String expectedStatus,
        @Size(max = 500) String reason) {
}
