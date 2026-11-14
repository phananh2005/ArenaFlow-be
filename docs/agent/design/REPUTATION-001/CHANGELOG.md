# CHANGELOG — REPUTATION-001 Đánh giá, report, tranh chấp & uy tín đội

## [v1.1] — 2026-11-13 (cập nhật 2026-11-14)

**Type:** MINOR (thêm mã lỗi `REPORT_009` cho 2.5, ghi rõ phạm vi 2.4 / 2.6–2.8 / 2.9; không đổi endpoint, field, mã thành công, mã lỗi đã có) · **Tóm tắt:** Mở rộng cho MERCENARY-001: tranh chấp report bùng nhắm vào mercenary dùng chung 2.5; report / tranh chấp / đánh giá giữa đội và mercenary nằm ở API của MERCENARY-001, không lẫn vào danh sách giữa hai đội.

### Thay đổi

| # | Vị trí | Loại | Cũ → Mới | Lý do | FE cần làm gì |
|---|---|---|---|---|---|
| 1 | 2.5, §5 | Thêm | Chỉ đội trưởng đội bị report tranh chấp → thêm report nhắm vào **cá nhân** (đội trưởng report mercenary): chính người bị report tranh chấp; người khác → `403 REPORT_009` "Chỉ người bị report được tranh chấp" | Mercenary tranh chấp report bùng nhắm vào mình (Business_Rules §5.1, BR-ME14) | Màn hình report cá nhân (MERCENARY-001 2.22) gọi 2.5 khi `canDispute = true`; xử lý mã mới |
| 2 | 2.4 | Ghi rõ | — → chỉ gồm report giữa hai đội; report giữa đội và mercenary ở MERCENARY-001 2.21 | Giữ hình dạng response (`counterpartTeam`) | Thêm tab mercenary dùng MERCENARY-001 2.21 nếu cần |
| 3 | 2.6–2.8 | Ghi rõ | — → chỉ tranh chấp report giữa hai đội; tranh chấp liên quan mercenary ở MERCENARY-001 2.25–2.27 (2.7 / 2.8 với id đó → `404 DISPUTE_001`) | Giữ hình dạng response (`reporterTeam`, `reportedTeam`) | Admin panel thêm hàng chờ mercenary |
| 3a | §1.3, §1.4, §1.7 | Ghi rõ | — → mercenary đá thuê cho đội report được đội (MERCENARY-001 2.20): trừ điểm đội theo cùng bảng (chung bộ đếm số lần), cắt chuỗi trận sạch, report bùng loại kèo khỏi tử số tỉ lệ hoàn thành; đội tranh chấp bằng 2.5 | BR-ME14 (user chốt 2026-11-14) | Không đổi cách gọi; hiển thị điểm / tỉ lệ đã gồm tác động này |
| 4 | 2.9 `rating.averageRating` | Ghi rõ | — → chỉ tính đánh giá của các đội đối phương; đánh giá của mercenary dành cho đội không tính | BR-ME13 | Không |
| 5 | Tài liệu nội bộ BE | Sửa | Cập nhật tài liệu nội bộ BE (điểm cá nhân, huy hiệu cá nhân, kiểm tồn tại tranh chấp theo hàng chờ ở 2.8), không ảnh hưởng FE ngoài các mục trên | MERCENARY-001 | — |
| 6 | Header 3 tài liệu | Ghi nhận | v1.0 → v1.1 | Đánh phiên bản theo entry này | — |

### Ảnh hưởng
- API contract: thêm mã lỗi `REPORT_009` cho 2.5 (tương thích ngược); phạm vi dữ liệu 2.4, 2.6–2.9 ghi rõ.
- Nghiệp vụ / SRS: theo SRS `SYSTEM-001` v1.9 (BR-ME13, BR-ME14) — không sửa SRS trong thay đổi này.
