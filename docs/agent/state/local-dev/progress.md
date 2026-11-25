---
task: MATCH-002 — thương lượng ghép kèo luồng B (pairing, tiền cọc) + nhánh luồng B trong MATCH-001 (V8)
created: 2026-11-21
status: in_progress
---

## Tiến độ
Giai đoạn 2 xong (P0–P6) — dừng trước `code-reviewer` theo chỉ đạo coordinator. Còn: giai đoạn tích hợp (P7 + chạy test tích hợp), review, đồng bộ design.

## Đã xong (2026-11-21)
- P1: `ConcurrencyConflictException` (sở hữu) + retrier bắt và thử lại. Red 2 test fail → Green `ConcurrencyConflictRetrierTest` 12/12.
- P2: V8, 5 entity, `PAIRING_001..008`. Red biên dịch fail → Green `ErrorCodeTest` 5/5, `PairingEntityTest` 4/4, `MatchFlowBEntityTest` 3/3.
- P3: 7 projection, `PairingRepository`, `MatchVenueOptionRepository`, `MatchTimeOptionRepository`, `CostCustomItemRepository` (pairing), `MatchRepository.confirmPairing`; test tích hợp đã viết, chưa chạy.
- P4: `PairingGuard`, `PairingSide`, filter, `PairingRules`, `CostAgreementFields`, `@ValidDeposit`, 4 request + 8 response DTO, `PairingMapper`, `PairingCreatedEvent`, `RealtimeFrame.PairingUpdated` + `PairingChange`. Red biên dịch fail / 1 ca holder rỗng fail → Green 19/19.
- P5: `PairingService` + `PairingServiceImpl`. Red 36/36 fail (`UnsupportedOperationException`) → Green 36/36.
- P6: `MatchPairingController`, `TeamPairingController`, `PairingController`; `PairingControllerTest` viết, chưa chạy.
- P0: phụ thuộc tạm SYSTEM-002 / MATCH-001 (danh sách trong `current-task.md`).
- Lần chạy cuối: 105/105 unit PASS; `./mvnw test-compile` toàn repo PASS.

## Còn lại
- Giai đoạn tích hợp: rebase sau SYSTEM-002 + MATCH-001; thay phụ thuộc tạm; `PairingReturningSpikeTest` đầu tiên; P7 (11 điểm cắm); chạy toàn bộ test tích hợp; viết test còn thiếu.
- `code-reviewer` → đối chiếu design (bảng "Lệch thiết kế" 5 dòng) → `architect-reviewer`.
