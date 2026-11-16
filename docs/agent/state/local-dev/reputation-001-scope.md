# REPUTATION-001 — phạm vi đề xuất & câu hỏi chốt (solution-architect, 2026-11-12)

Nhánh `feature/REPUTATION-001-rating-report-dispute` (từ origin/main `bbdfc42`). Chưa có file design.
Lưu ý: code MATCH-001/002, VENUE-001 chưa implement → REPUTATION-001 dùng lại thành phần chỉ có trong thiết kế; migration dự kiến V8 (decisions-log ghi "dispute (V5)" đã cũ).

## Endpoint đề xuất
2.1 `POST /matches/{matchId}/ratings` (201) · 2.2 `GET /matches/{matchId}/ratings?teamId=` · 2.3 `POST /matches/{matchId}/reports` (201) · 2.4 `GET /teams/{teamId}/reports?direction=RECEIVED|SENT` · 2.5 `POST /reports/{reportId}/dispute` (201) · 2.6 `GET /admin/disputes` · 2.7 `GET /admin/disputes/{id}` · 2.8 `PATCH /admin/disputes/{id}/resolve` · 2.9 `GET /teams/{teamId}/reputation`.
Uy tín cá nhân + đánh giá/report INDIVIDUAL → MERCENARY-001. Event → NOTIFICATION-001 nghe.

## Câu hỏi (trạng thái chốt)
| Q | Chủ đề | Đề xuất SA | User chốt |
|---|---|---|---|
| Q1 | "Đội kia không đến": lưu rating dạng no-show? +2 cho ai? | A: lưu no-show, COMPLETED, đội chọn +2, đội vắng 0 | **A** (2026-11-12) |
| Q2 | Admin xác nhận bùng có trừ thêm -10 (BR:283,540; Overview:91) vs giữ nguyên (UC:686, BR:502, AC:374) | A: không trừ thêm, sửa SRS | **A** (2026-11-12) |
| Q3 | Trận sạch/+2 chốt lúc COMPLETED; report muộn reset chuỗi không thu hồi; huỷ CONFIRMED cắt chuỗi (sửa MATCH-001) | A | **A** (2026-11-12) |
| Q4 | Loại bùng khỏi tử số Completion Rate ngay khi report; ARCHIVED có report bùng vào mẫu số | A | **A** (2026-11-12) |
| Q5 | Completion Rate/avg rating: tính khi đọc, bỏ cột V1 `total_matches_*`, `average_rating` | A | **A** (2026-11-12) |
| Q6 | Badge: `verified_at` + listener AFTER_COMMIT + job đối soát hằng ngày | B | **B** (2026-11-12) |
| Q7 | Phone linked: cá nhân phoneNumber≠null; đội = đội trưởng hiện tại có SĐT; liên kết SĐT → backlog | A | **A** (2026-11-12) |
| Q8 | Hiển thị uy tín: endpoint mới 2.9, không sửa hợp đồng cũ | A | **A** (2026-11-12) |
| Q9 | Bác report: hoàn mức đã trừ thật + giảm bộ đếm 1 | B | **B** (2026-11-12) |
| Q10 | Bonus lặp mỗi bội số 5 (15 → +5, 20 → +10) | A | **A** (2026-11-12) |
| Q11 | Report kèo CANCELLED: chỉ "giao tiếp tệ" nếu huỷ sau CONFIRMED | A | **A** (2026-11-12) |
| Q12 | Chặn đánh giá/report khi người gọi là đội trưởng cả 2 đội | A | **A** (2026-11-12) |
| Q13 | Bằng chứng tranh chấp chỉ văn bản; giới hạn độ dài | A | **A** (2026-11-12) |
| Q14 | Sao nguyên 1..5 | A | **A** + sửa SRS "≥4.5 sao" → "5 sao" được +1 (2026-11-12) |

Giả định chính & ranh giới: xem báo cáo SA trong hội thoại 2026-11-12 (đội INACTIVE vẫn thao tác, DISBANDED không; rating không sửa/xoá; mức trừ theo bộ đếm sau tăng; badge >3.0 sao, >5 trận; 0–49 chỉ hiển thị; tên đội trưởng đã xoá trong tranh chấp bị che).
Rủi ro truy vấn: 2.1 ~5/6 (gộp 1 `UPDATE team … RETURNING` cho 2 đội); Q6-A sẽ vượt N_WRITE.

## Kết quả solution-architect (2026-11-12, chưa commit)
- Tạo `docs/agent/design/REPUTATION-001/{API_Spec,Component_Class_Design,Processing_Flow}.md` (baseline v1.0, chưa có CHANGELOG theo design-standards §10).
- Sửa: MATCH-001 v2.2 (Q3 huỷ đã ghép → `clean_streak = 0` trong cùng UPDATE; `ReliabilityChange.breaksCleanStreak`; scheduling pool 3→4; đóng cần xác nhận #3), ADR-0013 Sửa đổi 2026-11-12 (bỏ `total_matches_*`/`average_rating`, RETURNING, `verified_at`), ADR-0003 Sửa đổi 2026-11-12 (CAS report/dispute), `docs/database/data-model.md` §3.4.
- Endpoint 2.1–2.9; 2.1/2.3 không nhận teamId (suy từ người gọi). Admin 2.6–2.8 tên thật + `isReportedByDeleted`, `isDisputedByDeleted`, `isResolvedByDeleted`. Mã lỗi mới RATING_001–003, REPORT_001–008, DISPUTE_001–002.
- Migration V8 `V8__reputation_rating_report_dispute.sql` (chỉ trong thiết kế); áp được sau khi V5–V7 có code (cần xác nhận #8). Neon dev: Flyway V4, rating/report/match 0 dòng.
- NFS: 2.1 5/W6, 2.2 2/D3, 2.3 5/W6, 2.4 3/L4, 2.5 3/W6, 2.6 2/L4, 2.7 2/D3, 2.8 REJECTED 6/W6, 2.9 1/D3. Chưa kiểm chứng native `UPDATE … RETURNING` không @Modifying (nếu không chạy → 2.8 = 7).
- publish-spec dry run exit 0; `git diff --check` sạch.

### Câu A–F — ĐÃ CHỐT 2026-11-12: A1, B giữ nguyên, C có tính, D chỉ đội huỷ (sửa SRS), E không khôi phục, F giữ số
- A (chặn listener huy hiệu, PF #13): huỷ kèo đã ghép bị trừ điểm → cập nhật huy hiệu lúc nào? A1 job hằng ngày (đề xuất, giữ W6, trễ ≤ ~1 ngày) · A2 ngay sau commit (MATCH-001 2.10 = 7 > W6) · A3 @Async.
- B: rating "không đến" → chuỗi trận sạch đội vắng: giữ nguyên (đề xuất) / về 0.
- C: đội bị rating "không đến" nhưng không bị report bùng → vẫn vào tử số Completion Rate? Có (đề xuất) / không.
- D: kèo huỷ <24h trước trận tính vào mẫu số cả hai đội (đúng SRS, thiết kế đang theo) hay chỉ đội huỷ?
- E: Admin bác report có khôi phục chuỗi trận sạch? Không (đề xuất).
- F: giới hạn độ dài tự đặt: reason/evidence ≤1000, note ≤500, comment ≤500.

### SRS SYSTEM-001 cần sửa → v1.8 (business-analyst, sau khi chốt A–F)
BR=Business_Rules, UC=Use_Cases, AC=Acceptance_Criteria, OV=Overview, DM=Data_Model_Conceptual (số dòng theo bản lúc SA đọc).
- Q2: xoá BR:283, BR:540 (-10 bùng xác nhận), OV:91.
- Q14: "≥4.5 sao" → "5 sao (sao nguyên 1–5) +1": BR:272, BR:523, UC:600, UC:606, UC:701, AC:343, AC:383, OV:88; UC:893 (MERCENARY) sửa cùng; DM Rating "Score" ghi sao nguyên.
- Q1: UC-R01 bước 5/8 (UC:596–599) + AC-R01: rating "không đến" → COMPLETED, đội đánh giá +2, đội vắng 0, không sao/tỷ số.
- Q3: BR:286, BR:525: +2/thưởng chốt lúc COMPLETED; report sau cắt chuỗi không thu hồi; huỷ CONFIRMED / rút ACCEPTED cắt chuỗi đội huỷ.
- Q4: tử số BR:329 → "không bị report bùng (chưa bị Admin bác) — loại ngay khi report"; mẫu số BR:323–325 thêm ARCHIVED có report bùng; tương tự UC:728–729, AC:400–401, BC-R05 (BR:367), BR:480, BR:499, DM:490–491.
- Q5: DM §2.7 Completion_Rate (DM:483–498) "tính khi hiển thị, không lưu".
- Q6/Q7: BR-R04 (BR:51), BC-R07 (BR:369), UC:749, DM:515 phone linked → "đội: đội trưởng hiện tại có SĐT; cá nhân: tài khoản có SĐT"; BR:558, UC:1204, AC:809, OV:202, DM:694 thêm "đổi đội trưởng/SĐT cập nhật ở đối soát hằng ngày"; ">5 trận" = số trận tính vào tử số.
- Q9: BR:503, UC:687, AC:375, AC:751 → "hoàn đúng điểm đã thực trừ (có thể < bảng nếu đã chạm 0) và giảm 1 lần bị report bùng".
- Q10: BR:270–271, BR:521–522, UC:708–710, AC-R04, OV:87 → mỗi bội số 5: bội 10 → +10, còn lại +5.
- Q11: UC:622 CANCELLED chỉ khi huỷ sau CONFIRMED, chỉ giao tiếp tệ.
- Q12: UC-R01, UC-R02 exception: đội trưởng cả hai đội không được rating/report.
- Q13: UC:659 bằng chứng chỉ văn bản ≤1000; AC:748 "ảnh check-in sân" → văn bản.
- Khác: BR:294 bỏ "hạn chế chức năng" (0–49 chỉ hiển thị); đội INACTIVE vẫn rating/report/tranh chấp.
- Ghi chú NOTIFICATION-001: thông báo "rating nhận" (BR §8.6) mâu thuẫn BC-R04.

### Ngoài nhánh này
- Nhánh `chore/agent-framework-*`: 9 endpoint vào bảng 2xx + domain RATING/REPORT/DISPUTE trong `api-convention.md`; package `application/listener/` trong `convention-backend.md`; dòng điều hướng REPUTATION-001 trong `docs/agent/CLAUDE.md` §2.
- Khi implement: cập nhật `schema.dbml` / `schema-design.md`.

## Áp A–F + SRS v1.8 (2026-11-12, chưa commit)
- solution-architect đã áp A–F vào design REPUTATION-001 (bỏ `TeamVerificationListener`/package `application/listener/`; D thêm điều kiện `cancelled_by_team_id = :teamId` vào truy vấn 2.9; CCD §13 "Quyết định đã chốt"); data-model §3.4 thêm dòng D. MATCH-001 2.10 giữ 6/W6. publish-spec dry run thành công, `git diff --check` sạch.
- [cần xác nhận] design còn lại (kỹ thuật, không cần user chốt lúc design): #5 `UPDATE … RETURNING` không @Modifying (chặn nếu MATCH-002 chưa kiểm; không chạy → 2.8 = 7 > W6) · #6 CTE `FOR NO KEY UPDATE` · #7 bind mảng native · #8 V8 cần V5–V7 có code (CHẶN code) · #9 `REQUIRES_NEW` trong afterCommit · #10 pool = 4 · #11 thông báo "rating nhận" vs BC-R04 → NOTIFICATION-001 · #12 race report/rating làm chuỗi +1 sai (đề xuất chấp nhận).
- business-analyst đã sửa SRS SYSTEM-001 lên v1.8 (5 file: BR, UC, AC, OV, DM; +174/−123; `git diff --check` sạch). Thêm BR-R07 (CANCELLED sau CONFIRMED chỉ giao tiếp tệ), BR-R08 (đội trưởng cả hai đội), BR-R09 (độ dài F), BR-R10 (INACTIVE vẫn thao tác). Không đổi: reset bộ đếm report "sau 5 trận sạch liên tiếp" (tương đương bội số 5); UC-ME06 bước 4 (MERCENARY).

### Câu BA hỏi — ĐÃ CHỐT (2026-11-13)
1. Đội INACTIVE không được rating/report/xem report/tranh chấp như DISBANDED; mức 0–49 chỉ hiển thị, không hạn chế chức năng.
2. Quy tắc huy hiệu cá nhân khi đổi SĐT để MERCENARY-001 chốt riêng.
3. Lý do report, bằng chứng tranh chấp và ghi chú quyết định Admin bắt buộc nhập; giữ giới hạn reason/evidence 1–1000, note 1–500; nhận xét rating vẫn tùy chọn ≤500.

SRS SYSTEM-001 v1.8 và design REPUTATION-001 cập nhật theo quyết định trên (đang hoàn thiện/đồng bộ tài liệu). [Cần architect-reviewer sau khi toàn bộ tài liệu đồng bộ.]
