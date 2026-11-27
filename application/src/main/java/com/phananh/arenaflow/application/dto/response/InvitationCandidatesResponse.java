package com.phananh.arenaflow.application.dto.response;

import java.util.List;

/** Tối đa 20 gợi ý; {@code hasMore} = còn người khớp ngoài các gợi ý này (không có trang tiếp). */
public record InvitationCandidatesResponse(List<InvitationCandidateResponse> items, boolean hasMore) {
}
