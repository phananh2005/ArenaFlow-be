# MERCENARY-001 — phạm vi & câu cần chốt

Nhánh: `feature/MERCENARY-001-mercenary-recruitment` (từ `origin/main` `191bec3`, 2026-11-13).
SRS: SYSTEM-001 v1.8 — UC-ME01..ME06, BR-ME01..ME04, BC-ME01..ME05, Business_Rules §2.4, §2.5, §3.1 (mức trừ cá nhân), §3.3 (Completion Rate cá nhân), §5.1/§5.2; AC-ME01..ME03. data-model §3.6, §3.7.
Schema có sẵn (V1): `mercenary_recruitment` (1 dòng = 1 vị trí + `quantity_needed`/`quantity_approved`, status OPEN/FILLED/EXPIRED/CLOSED), `mercenary_application` (PENDING/APPROVED/REJECTED/WITHDRAWN, unique active theo (recruitment, applicant)), `user.reliability_score` (mặc định 80), `rating` có `rater_type`/`ratee_type` TEAM|INDIVIDUAL.
`administrative_unit` chỉ có cấp `province`/`ward` (không có `district`).

## Câu cần chốt (hỏi từng câu)
| # | Chủ đề | Trạng thái |
|---|---|---|
| Q1 | Một tin tuyển = 1 vị trí hay nhiều vị trí | chốt |
| Q2 | Ai được ứng tuyển (thành viên 2 đội trong trận? trùng giờ trận khác?) | chốt |
| Q3 | Tin FILLED/CLOSED/EXPIRED → đơn PENDING còn lại | chốt |
| Q4 | Kèo bị huỷ / rút sau CONFIRMED → tin tuyển + đơn APPROVED | chốt |
| Q5 | Sửa tin tuyển (số lượng, vị trí) | chốt |
| Q6 | Tìm kiếm: toạ độ trận, miền bán kính, dự phòng khu vực (cùng phường / cùng tỉnh) | chốt |
| Q7 | "Đã tham gia trận" xác định thế nào; mercenary không đến | chốt |
| Q8 | Report giữa đội trưởng ↔ mercenary có trong phạm vi không | chốt |
| Q9 | Reliability cá nhân: +2 hoàn thành, chuỗi trận sạch, thưởng 5 sao | chốt |
| Q10 | Mercenary đánh giá đội: tính vào sao trung bình / +1 Reliability đội? | chốt |
| Q11 | Badge cá nhân khi đổi SĐT (hoãn từ REPUTATION-001) | chốt |
| Q12 | Đội trưởng huỷ duyệt / loại mercenary đã APPROVED | chốt |
| Q13 | Mercenary đã duyệt thấy địa chỉ sân chính xác? | chốt |

## Đã chốt
- Q1 (2026-11-13): 1 lần submit nhiều vị trí → tạo 1 tin/vị trí (khớp V1), mỗi tin trạng thái riêng.
- Q2: chặn ứng tuyển nếu là thành viên ACTIVE của 1 trong 2 đội trong trận, hoặc đã có đơn APPROVED ở trận khác trùng giờ; kiểm lúc ứng tuyển và lúc duyệt.
- Q3: FILLED → đơn PENDING giữ nguyên (không duyệt được; tin về OPEN thì duyệt tiếp); CLOSED/EXPIRED → PENDING tự REJECTED + báo ứng viên.
- Q4: trận CANCELLED → mọi tin của trận tự CLOSED, PENDING → REJECTED, APPROVED giữ nguyên (lịch sử); báo cả hai; không trừ điểm, không tính Completion Rate cá nhân, không đánh giá. Không đổi schema.
- Q5: chỉ sửa số lượng (≥ số đã duyệt; tự FILLED/OPEN theo số mới), không đổi vị trí; chỉ khi tin OPEN/FILLED.
- Q6: GPS mặc định 3 km, chỉnh 1–20 km, sắp theo khoảng cách; không GPS → kèo có sân cùng tỉnh với phường hồ sơ, cùng phường lên đầu rồi theo giờ đá, không trả khoảng cách; khu vực hiển thị = "phường, tỉnh" của sân.
- Q7: đơn còn APPROVED lúc trận kết thúc = đã tham gia (không check-in); vắng → đánh giá "không đến" và/hoặc report bùng như đội–đội.
- Q8: report hai chiều đội trưởng ↔ mercenary trong phạm vi, dùng cơ chế REPUTATION-001 (4 loại, tự chấp nhận, trừ theo số lần, reset sau 5 trận sạch, bùng tranh chấp 24h → Admin). Bổ sung UC/AC vào SRS.
- Q9: Reliability cá nhân giống hệt luật đội: lúc trận COMPLETED mọi mercenary còn APPROVED +2, chuỗi trận sạch (+5/+10 mốc bội số), reset bộ đếm report sau 5 trận sạch, 5 sao +1, rút lui -1/-3/-5 + chuỗi về 0; ARCHIVED không ai đánh giá → không +2.
- Q10: mercenary đánh giá đội chỉ hiển thị riêng, không vào sao trung bình đội, không +1 Reliability đội.
- Q11: badge cá nhân — đổi SĐT cập nhật ở đối soát hằng ngày; đánh giá/report/tranh chấp/rút lui cập nhật ngay.
- Q12: đội trưởng không huỷ duyệt / loại mercenary APPROVED; chỉ mercenary tự rút.
- Q13: tên sân, địa chỉ, toạ độ, tên đội trưởng chỉ hiện khi đơn APPROVED; trước đó chỉ khoảng cách + "phường, tỉnh".
- C1: rút lui sau APPROVED chỉ trước giờ bắt đầu trận.
- C2: đánh giá liên quan mercenary không làm Match COMPLETED.
- C3: rút lui <24h trước trận → vào mẫu số Completion Rate cá nhân (giống đội).
- C4: report "giao tiếp tệ" giữa đội trưởng ↔ ứng viên khi đơn đã có tin nhắn chat, bất kể trạng thái đơn/trận.
- Chat: 1–1 theo đơn ứng tuyển, mở từ PENDING; gửi khi đơn PENDING/APPROVED — APPROVED nhắn cả sau trận / khi trận hủy đến hết hạn lưu (sửa 2026-11-13, giống chat hai đội); REJECTED/WITHDRAWN chỉ xem; 1..1000 ký tự; 90 ngày; dùng lại hạ tầng chat realtime MATCH-002.
- Giả định: cả đội tạo kèo và đội đã ghép đều tuyển được cho trận CONFIRMED của mình (UC-ME01 "đội có Match CONFIRMED").

## Tiến độ
- 2026-11-13: SRS SYSTEM-001 lên v1.9 (coordinator làm inline sau 2 lần business-analyst nền bị ngắt): BR-ME03..BR-ME15, §2.4/§2.5/§3.1/§3.3/§5/§8.2, BC-ME03/04, UC-ME01..ME08, AC-ME01..ME09, Data_Model, Overview. Chưa commit.
- C1–C4 + chat đã chốt và ghi vào SRS v1.9 (BR-ME11, BR-ME13, BR-ME14, BR-ME16, §3.3, UC-ME08/09, AC-ME08..10, Data_Model Chat_Message).
- 2026-11-13: solution-architect xong MERCENARY-001 v1.0 (27 endpoint, V9, bảng conversation/conversation_message chung), sửa REPUTATION-001 v1.1 (+CHANGELOG), MATCH-001 v2.3, MATCH-002 v1.2, ADR-0014 Sửa đổi 2026-11-13. Chưa commit. Chờ user chốt B1–B12 (đáp án tạm đang trong design), rồi architect-reviewer. Framework (api-convention, convention-backend, docs/agent/CLAUDE.md §2) để nhánh chore riêng.

## B1–B12 (câu nghiệp vụ từ solution-architect, user chốt 2026-11-13)
- B1: thêm phường cho sân, BẮT BUỘC với sân mới / sửa sân; sân cũ chưa có phường vẫn hoạt động, tìm GPS bình thường, không hiện khu vực và không ra trong tìm không GPS tới khi bổ sung (khác đáp án tạm "tuỳ chọn").
- B2: tối đa 1 đơn APPROVED mỗi trận (khớp đáp án tạm).
- B3: đội không ACTIVE → chặn tạo/sửa/duyệt; INACTIVE vẫn đóng tin / từ chối đơn; tin ẩn khỏi tìm, không nhận ứng tuyển mới; APPROVED giữ (khớp đáp án tạm).
- B4: KHÔNG cho tin trùng: mỗi (trận, đội, vị trí) tối đa 1 tin OPEN/FILLED; muốn thêm thì tăng số lượng (khác đáp án tạm).
- B5: số lượng 1–10 (khác đáp án tạm 1..20).
- B6: từ giờ bắt đầu trận tin ẩn khỏi tìm, không ứng tuyển / duyệt; PENDING tự REJECTED khi EXPIRED (khác đáp án tạm).
- B7: không GPS + hồ sơ chưa có phường → báo lỗi nhắc bật GPS / chọn phường (khớp đáp án tạm `422 MERCENARY_018`).
- B8: đánh giá của lính trên hồ sơ đội chỉ hiện sau khi đội đã đánh giá lại lính đó (khớp đáp án tạm, giữ BC-R03).
- B9: report bùng của lính với đội tác động như report của đội: trừ điểm, loại khỏi tử số Completion Rate, cắt chuỗi; đội tranh chấp 24h (khớp đáp án tạm).
- B10: hàng chờ tranh chấp lính riêng cho Admin; REPUTATION-001 giữ nguyên (khớp đáp án tạm).
- B11: chấp nhận thông báo tự đóng / tự từ chối trễ tối đa ~5 phút; trạng thái hiển thị đúng ngay (khớp đáp án tạm).
- B12: xoá tài khoản → đơn PENDING/APPROVED của trận chưa bắt đầu tự WITHDRAWN, không trừ điểm, tin FILLED về OPEN, báo đội trưởng; sửa luồng xoá tài khoản USER-001 (khác đáp án tạm).
- Khác đáp án tạm → phải sửa design: B1, B4, B5, B6, B12.
- B12 biên (2026-11-14): đơn APPROVED của trận đã huỷ giữ nguyên khi xoá tài khoản; chỉ trận CONFIRMED chưa bắt đầu tự rút (BR-ME18 làm rõ).

## architect-reviewer vòng 1 (2026-11-14): 0 CRITICAL, 6 MAJOR, 15 MINOR, 3 ADR — chưa sửa gì
- M1 thứ tự khoá xoá tài khoản (đơn→tin) ngược duyệt (tin→đơn); CCD:16/PF:301 khẳng định sai. Đề xuất đảo: khoá tin trước (UPDATE recruitment theo subquery đơn APPROVED), rồi rút đơn RETURNING; đóng #11.
- M2 2.7 ứng tuyển không chặn user đã xoá (token còn ≤1h) → thêm caller_deleted vào findApplyContext, 404 USER_001 đầu chuỗi. (MINOR 14: cân nhắc cùng guard cho chat/đánh giá/report.)
- M3 [NGHIỆP VỤ] tên đội trưởng lộ trong chat khi đơn PENDING (trái BR-ME04/Q13 vs BR-ME16 người gửi thật).
- M4 [NGHIỆP VỤ] 2.1 tạo tin / 2.5 tăng số lượng sau giờ bắt đầu → tin chết; đề xuất chặn bằng MERCENARY_013.
- M5 CAS 0 dòng kiểm tồn tại không lọc phạm vi → MERCENARY 2.27 / REPUTATION 2.8 trả 409 thay vì 404 khi id thuộc hàng chờ kia.
- M6 B9 chưa có trong hợp đồng publish: REPUTATION API §1.3/§1.4/§1.7 + CHANGELOG, MERCENARY §1.5.
- MINOR 1–15: đánh số/tham chiếu PF/CCD; ErrorCode thiếu MERCENARY_023; chữ ký/tên method lệch; tên index lệch; race đóng tin↔ứng tuyển để PENDING mồ côi không báo; #8 race phá cả B2; guard REPUTATION 2.5 chép 2 nơi; câu "chờ B9" cũ; header SRS version VENUE/REPUTATION/USER-001; VENUE CCD §11; dấu reliabilityPenalty âm/dương lệch; event đóng tin không mang id đơn; SRS (Overview:111 "rút bất kỳ lúc nào", thứ tự BR-ME15..18, [NGHIỆP VỤ] BR-ME15 mercenary "không đến" vẫn +2?); guard user đã xoá cho 2.15/2.18/2.20; 2.4 distanceKm 2 chữ số làm lộ vị trí sân.
- ADR: ADR-0003 thiếu Sửa đổi cho CAS mercenary_*; ADR-0007 Sửa đổi phạm vi khoá deleteAccount; ADR-0014 Liên quan + ghi mô hình conversation.
- Chốt review (2026-11-14): M3 → hiện tên đội trưởng trong chat (cả khi PENDING); Q13 chỉ áp cho tên sân/địa chỉ/toạ độ; ghi ngoại lệ BR-ME04. M4 → chặn tạo tin và tăng số lượng từ giờ bắt đầu trận (giảm số / đóng vẫn được).
- YÊU CẦU MỚI (2026-11-14): đăng tin tuyển cho trận NGOÀI hệ thống (không do app ghép). Đang hỏi E1..En.
- E1: tin cho trận ngoài hệ thống — đội trưởng chọn sân có sẵn (chưa có thì thêm sân trước), nhập giờ bắt đầu/kết thúc, trình độ, tên đối thủ (tuỳ chọn), rồi vị trí + số lượng; tìm GPS/khu vực, ẩn địa chỉ tới khi duyệt như trận trong hệ thống.
- E2: trận ngoài — sau giờ kết thúc đánh giá/report hai chiều như trận trong hệ thống; lính hoàn thành (+2, chuỗi, Completion Rate cá nhân) khi đội trưởng đánh giá lính bằng sao; đội không cộng/trừ điểm vì trận ngoài (trừ report của lính theo B9).
- E3: chỉ đội trưởng của đội ACTIVE đăng tin trận ngoài; tin gắn đội.
- E4: đội trưởng "Huỷ trận ngoài" → xử lý như BR-ME09 (tin đóng, PENDING bị từ chối, APPROVED giữ lịch sử, không ai bị trừ điểm, báo lính); sửa giờ/sân/trình độ chỉ khi chưa có đơn APPROVED; huỷ trận ngoài không trừ điểm đội.
- 13c (đổi Q9 cho lính, mọi trận): lính +2/chuỗi/hoàn thành KHI đội trưởng đánh giá lính bằng sao; bị đánh giá "không đến" → không cộng; không đánh giá → không cộng.
- Chốt sửa review: M1 (đảo thứ tự khoá, đóng #11), M2 (+MINOR 14: chặn user đã xoá ở ứng tuyển/chat/đánh giá/report, 404 USER_001), M5 (kiểm tồn tại theo hàng chờ → 404), M6 (B9 vào API REPUTATION/MERCENARY + CHANGELOG); MINOR 1–15 tất cả; ADR 0003/0007/0014.
- 2026-11-14: solution-architect áp xong review vòng 1 + E1–E4 + 13c (bảng external_match, 2.28–2.33, MERCENARY_024/025). Chờ user: #2 ràng buộc trận ngoài (≥ now+1h, ≤12h, đối thủ ≤100, sửa trước giờ bắt đầu), #3 trận ngoài có tính 'sân được chọn' cho cảnh báo 180 ngày (BC-V05).
- Chốt (2026-11-14): trận ngoài giữ giới hạn như tạo kèo (≥ now+1h, ≤12h, đối thủ ≤100, sửa chỉ trước giờ bắt đầu và chưa có APPROVED); khai báo trận ngoài tính 'sân được chọn' cho cảnh báo 180 ngày (BC-V05 sửa).

## architect-reviewer vòng 2 (2026-11-14): vòng 1 đạt hết trừ MINOR 3 (lệch mới) + MINOR 9 (USER-001 API header thiếu SRS v1.9/BR-ME18). Mới: 0 CRITICAL, 3 MAJOR, 8 MINOR, 3 ADR
- N-M1 API_Spec §4.5/4.6/4.7/4.10 + 2.24: matchId không nullable, thiếu externalMatchId → đổi UUID|null + externalMatchId (+source).
- N-M2 mercenary APPROVED không được báo huỷ trận khi tin đã CLOSED → event theo trận (MercenaryGameCancelledEvent) hoặc giao NOTIFICATION-001.
- N-M3 [NGHIỆP VỤ] huỷ trận ngoài được tới giờ kết thúc (kèo hệ thống chỉ trước giờ bắt đầu).
- MINOR: N-m1 thứ tự khoá 2.31 (tin→trận ngoài) ngược 2.32; N-m2 2.31 thắng race duyệt vẫn dùng giờ cũ; N-m3 tên/chữ ký method PF↔CCD; N-m4 BR-ME18 câu chữ chưa gồm trận ngoài; N-m5 bảng mã lỗi §5 thiếu 2.28–2.33 + 2.28 thiếu MATCH_014; N-m6 SRS §3.3 chưa theo 13c; N-m7 AC-ME11 trước AC-ME10; N-m8 ngày REPUTATION v1.1.
- ADR: ADR-0011 Sửa đổi (payload rejectedApplicationIds / event theo trận); ADR-0013 Liên quan trỏ MERCENARY; ADR-0003 thêm external_match vào thứ tự khoá. Theo dõi: ADR-0012 ORDER BY; cân nhắc ADR mô hình XOR match_id/external_match_id.
- Chốt N-M3 (2026-11-14): huỷ trận ngoài chỉ trước giờ bắt đầu (như BR-M11); 2.32 thêm MERCENARY_013, CAS match_start_time > now.
- Chốt sửa vòng 2: N-M1, N-M2, 8 MINOR + MINOR 3/9 vòng 1, 3 ADR. SRS đã sửa: BR-ME18 (gồm trận ngoài), BR-ME19 + AC-ME11 (huỷ trước giờ bắt đầu), §3.3 công thức cá nhân, thứ tự AC-ME10/11.
- 2026-11-14: solution-architect áp xong vòng 2 (N-M1/2/3, MINOR, ADR 0003/0011/0013; MercenaryGameCancelledEvent, game_cancel_notified_at, [cần xác nhận #14 #15]). Đang architect-reviewer vòng 3 (hẹp).
- architect-reviewer vòng 3 (2026-11-14): SẴN SÀNG — 0 CRITICAL, 0 MAJOR, 6 MINOR: (1) JSON ví dụ thiếu source/externalMatchId; (2) MATCH-001 CCD:232 thiếu notifyCancelledGames(); (3) lệch nhỏ PF↔CCD (findDetail 'response 2.31', token 'B4'); (4) USER-001 CHANGELOG:5 câu BR-ME18 hẹp; (5) #15 cách giảm chưa đủ (đề xuất bước 3 xuất phát từ trận CANCELLED gần đây, hoặc đánh dấu theo trận + đổi tên cột); (6) độ trễ ~5 phút báo huỷ kèo cho lính APPROVED chưa ghi trong BR/API. Thư mục tạm /tmp/tmp.BjHaGZIPtr còn (hook chặn rm -r).
- Chốt MINOR vòng 3: sửa cả 6; #6 chấp nhận trễ ~5 phút báo huỷ kèo cho lính APPROVED (đã ghi BR-ME09).
- 2026-11-14: solution-architect sửa xong 6 MINOR vòng 3 (#15 chọn luôn: bước 3 job xuất phát từ trận CANCELLED trong 7 ngày, index idx_external_match_cancelled, cấu hình cancel-notice-lookback-days=7). Đang architect-reviewer vòng 4 (kiểm nhanh). Sau đó: chờ user yêu cầu commit/PR + quyết định tách nhánh.
- Vòng 4 (2026-11-14): ĐẠT (6/6). User: giữ bảng external_match riêng; sửa MINOR-1 (CCD §4.9 :268 chữ ký markGameCancelNotified + lookback + OF a2) và MINOR-2 tách câu job báo huỷ thành UNION ALL hai nhánh (match_id / external_match_id).
- Vòng 5 (hẹp, 2026-11-14): ĐẠT. MINOR tuỳ chọn đã thêm: IS NOT NULL ở 2 nhánh job báo huỷ (PF §3.12). THIẾT KẾ MERCENARY-001 HOÀN TẤT — chờ user yêu cầu commit/PR (đề xuất: 2 commit SRS + design trên nhánh hiện tại, 1 PR; framework để chore riêng).
- 2026-11-14: commit 6faa81b (SRS v1.9) + 452c8ba (design), push, PR #61 https://github.com/phananh2005/ArenaFlow-be/pull/61 — chờ user merge.
- PR #61 squash-merge 2026-11-14 (c627d46).
