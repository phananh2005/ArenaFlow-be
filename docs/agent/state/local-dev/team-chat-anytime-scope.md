# Chat giữa hai đội "bất kỳ lúc nào, chỉ cần tham gia kèo" — phạm vi

Yêu cầu user 2026-11-13: sửa lại — 2 đội có thể chat với nhau bất kỳ lúc nào, chỉ cần tham gia kèo.
Hiện trạng (SRS v1.8/1.9 UC-N01, BR-N01/N03; MATCH-002 API_Spec §1, 2.8–2.10): chỉ luồng B, chat thuộc Pairing, gửi được khi Pairing NEGOTIATING; CONFIRMED/SUPERSEDED/EXPIRED chỉ xem; luồng A không có chat.
Loại: sửa thiết kế đã merge (chưa code) MATCH-001/MATCH-002 + SRS → nhánh riêng (`docs/MATCH-002-...` hoặc theo user), làm sau MERCENARY-001 (đang solution-architect trên `feature/MERCENARY-001-mercenary-recruitment`).

## Câu cần chốt (từng câu)
| # | Chủ đề | Trạng thái |
|---|---|---|
| T1 | "Tham gia kèo" gồm ai (luồng A: đội gửi request PENDING? luồng B: mọi Pairing?) | chốt |
| T2 | Chat tới khi nào (sau trận kết thúc / huỷ / hết hạn: chỉ xem?) | chốt |
| T3 | Một phòng chat / kèo hay giữ chat theo Pairing + thêm phòng sau CONFIRMED | chốt (theo T1) |
| T4 | Ai trong đội được nhắn | chốt |

## Đã chốt (2026-11-13)
- T1: chat mở từ lúc gửi yêu cầu (luồng A, mỗi đội gửi một chat riêng với đội tạo kèo, kể cả PENDING) / nhận kèo (luồng B, mỗi Pairing một chat như hiện tại).
- T2: hai đội đã ghép nhắn được trước/trong/sau trận và cả khi kèo bị huỷ, đến khi chat bị xoá (90 ngày sau khi trận kết thúc); đội không được ghép (bị từ chối / thay thế / rút / hết hạn) → chỉ xem.
- T3: mỗi cặp (kèo, đội gửi/nhận) một cuộc chat; sau khi ghép nhắn tiếp trong chính cuộc chat đó.
- T4: chỉ đội trưởng hiện tại của mỗi đội nhắn và xem (như hiện tại).

## Đã chốt (2026-11-15)
- Q1: đội gửi lại request sau khi rút / hết hạn → dùng tiếp cuộc chat cũ (giữ lịch sử; nhắn được lại khi request mới PENDING) — BR-N04.
- Report: cuộc chat hai đội có ≥1 tin nhắn → hai đội trưởng report nhau loại "giao tiếp tệ", bất kể trạng thái request / Pairing / kèo (giống chat mercenary BR-ME14); mỗi chiều 1 lần mỗi kèo; kể cả hai đội đã ghép khi kèo CONFIRMED trước trận — sửa BR-R07.
- Nhánh: `docs/MATCH-002-team-chat-anytime`; SRS SYSTEM-001 v1.10 (business-analyst). Tiếp: solution-architect sửa MATCH-001 / MATCH-002 / REPUTATION-001 (+ MERCENARY-001 nếu đụng `conversation`).
- Lưu trữ: gộp mọi chat (luồng A, luồng B, mercenary) về mô hình `conversation` + `conversation_message` của MERCENARY-001; MATCH-002 bỏ `chat_message`, bảng conversation lên V7; API chat pairing chuyển sang `/conversations/{id}` (user chọn 2026-11-15).
