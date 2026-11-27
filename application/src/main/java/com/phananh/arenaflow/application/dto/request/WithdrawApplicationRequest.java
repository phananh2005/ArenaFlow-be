package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.validation.OneOf;
import jakarta.validation.constraints.NotNull;

/** {@code expectedStatus} = trạng thái đơn FE đang hiển thị; khác trạng thái hiện tại → không rút. */
public record WithdrawApplicationRequest(@NotNull @OneOf({"PENDING", "APPROVED"}) String expectedStatus) {
}
