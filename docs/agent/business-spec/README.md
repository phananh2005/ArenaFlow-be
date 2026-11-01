# Thư mục Spec Nghiệp vụ

> Chứa spec nghiệp vụ chi tiết cho từng chức năng, do `business-analyst` tạo ra.

## Bố cục

```text
business-spec/
├── README.md              # file này
├── _template/             # mẫu cho chức năng mới
│   ├── Overview.md
│   ├── Use_Cases.md
│   ├── Business_Rules.md
│   ├── Data_Model_Conceptual.md
│   └── Acceptance_Criteria.md
└── <MÃ>/                  # một chức năng
    ├── Overview.md
    ├── Use_Cases.md
    ├── Business_Rules.md
    ├── Data_Model_Conceptual.md
    └── Acceptance_Criteria.md
```

**Hiện tại:** spec đặt thẳng trong `business-spec/<MÃ>/`. Khi số chức năng tăng, sẽ gom theo domain:
- `business-spec/<DOMAIN>/<MÃ>/` — theo domain (vd `booking/`, `arena/`, `user/`)

## Quy trình

1. **Phân tích nghiệp vụ:** `business-analyst` nhận yêu cầu → hỏi làm rõ → sinh spec
2. **Thiết kế kỹ thuật:** `solution-architect` đọc spec → sinh thiết kế kỹ thuật
3. **Code:** `developer` đọc thiết kế → code

## Ngôn ngữ

Spec nghiệp vụ viết **ngôn ngữ nghiệp vụ**, KHÔNG có thuật ngữ kỹ thuật:
- ✅ Dùng: User, Booking, Arena, trạng thái, use case, business rule
- ❌ Không dùng: API, endpoint, controller, DTO, SQL, database schema

## Ownership

- **Tạo/sửa:** qua `business-analyst` → duyệt → commit.
- **Không tự sửa tay** nếu chưa qua review.
- Spec thay đổi → cập nhật thiết kế kỹ thuật tương ứng.
