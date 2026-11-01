# Thư mục thiết kế kỹ thuật

> Chứa tài liệu thiết kế cho từng chức năng/module, do `solution-architect` tạo ra.

## Bố cục

```text
design/
├── README.md              # file này
├── _template/             # mẫu cho chức năng mới
│   ├── API_Spec.md
│   ├── Component_Class_Design.md
│   └── Processing_Flow.md
└── <MÃ>/                  # một chức năng
    ├── API_Spec.md
    ├── Component_Class_Design.md
    ├── Processing_Flow.md
    └── CHANGELOG.md       # chỉ tạo khi sửa bản đã có baseline
```

**Hiện tại:** thiết kế đặt thẳng trong `design/<MÃ>/`. Khi số chức năng tăng, sẽ gom theo cụm:
- `design/<CỤM>/<MÃ>/` — theo cụm chức năng (vd `booking/`, `arena/`, `user/`)
- `design/SHARED/<MÃ>/` — khung kỹ thuật dùng chung nhiều cụm

## Chuẩn viết

Xem `docs/agent/design-standards.md`.

## Ownership

- **Tạo/sửa:** qua `solution-architect` → `architect-reviewer` → duyệt → commit.
- **Không tự sửa tay** nếu chưa qua review.
- **Liên kết:** khi di chuyển/đổi tên thư mục, grep mọi chỗ trỏ tới (knowledge, ADR, `architecture.md`) và sửa cùng lúc.
