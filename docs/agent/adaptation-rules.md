# Bản đồ thích ứng — tín hiệu dự án → cách điều chỉnh hạ tầng agent

> Bản sao từ `.agent-bootstrap/adapters/adaptation-rules.md` — sống trong repo để agent tiếp tục
> tự điều chỉnh sau khi `.agent-bootstrap/` bị xoá. Dùng bởi skill `workflow-adapt`.

## 1. Nguyên tắc

1. **Bằng chứng trước, mẫu sau.** Mẫu chỉ là điểm khởi đầu. Repo có quy ước khác → theo repo.
2. **Bỏ được nghi thức, không bỏ được guardrail.** Có thể bỏ/gộp agent, skill, bước duyệt. **Không** bỏ:
   cổng "không báo pass khi chưa chạy test", ranh giới an toàn (secret, thao tác phá huỷ), quy tắc "không đoán — ghi `[cần xác nhận]`".
3. **Thu hẹp, không suy rộng.** Không rõ → cấu hình nhỏ hơn + `[cần xác nhận]`.
4. **Một nguồn cho mỗi sự thật.** Sửa giá trị ở kênh tuỳ biến, không nhân bản mẫu.
5. **Điều chỉnh có dấu vết.** Mọi điều chỉnh đảo ngược được và có lý do (hồ sơ mục 12 hoặc ADR).

## 2. Bảng tín hiệu → điều chỉnh

### 2.1 Hình dạng repo

| Tín hiệu (bằng chứng) | Điều chỉnh |
|---|---|
| Một tầng duy nhất | Bỏ hậu tố tầng; bỏ bước "xác định tầng" trong routing |
| Nhiều tầng / monorepo | Mỗi tầng một dòng; procedure/skill có hậu tố; routing theo **thư mục** |
| Thư viện / SDK | Review nhấn public API & tương thích ngược; thêm cổng changelog/semver |
| Prototype / spike | Mức `minimal`; không playbook; giữ cổng test nếu có test |

### 2.2 Kiểm thử & chất lượng

| Tín hiệu | Điều chỉnh |
|---|---|
| Có test + lệnh test xác nhận được | Cổng test bắt buộc |
| Có test nhưng không xác nhận được lệnh | `[cần xác nhận]`; cổng test vẫn giữ |
| Không có test | Không TDD; cổng đổi thành "nêu cách kiểm chứng thủ công" |
| Có lint/format/static analysis | Đưa vào cổng "sau sửa" |
| CI đã chạy secret scan / lint | Không tạo hook lặp lại CI |

### 2.3 Dữ liệu & phi chức năng

| Tín hiệu | Điều chỉnh |
|---|---|
| Có ORM/DB/migration | Bật chuẩn phi chức năng: phân trang, ngưỡng số truy vấn, index |
| Không có DB | Bỏ mục truy vấn |
| Có migration framework | Agent soạn migration nhưng **không chạy** |

### 2.4 Quy trình đội

| Tín hiệu | Điều chỉnh |
|---|---|
| Có spec nghiệp vụ / thiết kế trước code | Bật tầng thiết kế + ADR |
| Không có quy trình thiết kế | Bỏ tầng thiết kế; giữ cổng duyệt kế hoạch |
| Đội 1–3 người | Gộp `code-reviewer` vào skill; một thư mục state |
| Đội lớn | State theo namespace dev |

### 2.5 Tri thức & công cụ

| Tín hiệu | Điều chỉnh |
|---|---|
| Có nguồn tra cứu ngoài | Thêm quy tắc tra cứu ngoài + hook đầu phiên nhắc |
| Không có nguồn ngoài | Bỏ phần tra cứu ngoài |
| Harness không có hook | Dùng git hook + CI + checklist |

### 2.6 Rủi ro & tuân thủ

| Tín hiệu | Điều chỉnh |
|---|---|
| Dữ liệu nhạy cảm / tài chính | Mức `full`; thêm chiều security vào mọi review |
| Repo công khai | Không ghi tên nội bộ vào file agent |

## 3. Thích ứng sau bootstrap (vòng tự điều chỉnh)

```text
Phát hiện lệch ──► Xác minh bằng chứng ──► Đề xuất điều chỉnh ──► Người dùng duyệt ──► Sửa ở kênh tuỳ biến ──► Ghi dấu vết
```

**Tín hiệu kích hoạt** (skill `workflow-adapt`):
- Người dùng sửa cùng một hành vi ≥ 2 lần.
- Lệnh trong hồ sơ/hub chạy lỗi vì lệnh đã đổi.
- Quy tắc trong hub mâu thuẫn với code đang dùng.
- Hook chặn nhầm lặp lại.
- Repo thêm/bỏ tầng, đổi framework test, thêm DB, thêm nguồn ngoài.

**Giới hạn:**
- Không tự sửa file org-owned.
- Không tự nới guardrail (mục 1.2).
- Một lần điều chỉnh = một thay đổi nhỏ, có lý do, đảo ngược được.
