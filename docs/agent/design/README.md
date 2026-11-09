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

## Publish cho FE

- Merge vào `main` → workflow `publish-spec` tự chép `design/<MÃ>/API_Spec.md` và `CHANGELOG.md` (nếu có) sang `api/<MÃ>/` của repo chỉ-đọc `ArenaFlow-spec`. `Component_Class_Design.md`, `Processing_Flow.md` **không** publish; `<MÃ>` chưa có `API_Spec.md` bị bỏ qua.
- FE chỉ thấy thay đổi **sau khi merge vào `main`**. **NEVER** sửa trực tiếp ArenaFlow-spec — sửa ở đây.
- Sửa API_Spec phá vỡ hợp đồng → entry **MAJOR** trong `CHANGELOG.md`: đây là tín hiệu để FE xem lại. Giữ hàng "Phiên bản" (`hiện tại **vX.Y**`) và hàng "Spec nghiệp vụ" (`docs/agent/business-spec/<MÃ>/`) trong header API_Spec — mục lục ở ArenaFlow-spec đọc phiên bản và link SRS từ đó.
- Gom theo cụm (`design/<CỤM>/<MÃ>/`) → phải sửa script publish trước. Chi tiết: `docs/agent/README.md` mục "Publish tài liệu cho FE".

## Ownership

- **Tạo/sửa:** qua `solution-architect` → `architect-reviewer` → duyệt → commit.
- **Không tự sửa tay** nếu chưa qua review.
- **Liên kết:** khi di chuyển/đổi tên thư mục, grep mọi chỗ trỏ tới (knowledge, ADR, `architecture.md`) và sửa cùng lúc.
