---
description: >
  Review nhanh code theo 5 chiều (Security, Performance, Correctness, Style,
  Reuse & Duplication) trong phiên, CHỈ KHI USER YÊU CẦU ("review nhanh", "kiểm tra code").
  Không phải bước bắt buộc trước PR — cổng review trước PR là agent code-reviewer (CLAUDE.md §8).
---

# Code Review (nhanh, theo yêu cầu)

> Tiêu chí cụ thể lấy từ knowledge dự án. Skill chỉ định nghĩa 5 chiều — cùng bộ chiều với
> agent `code-reviewer`, bản rút gọn để chạy trong phiên.

## Khi nào dùng
- User yêu cầu review nhanh trong phiên (vd trước khi quyết định có cần review độc lập).
- **Không** thay cho `code-reviewer`: nhánh có code vẫn MUST qua `code-reviewer` trước PR (`CLAUDE.md` §8).

## Không chạy
- Như một cổng trước commit/PR — đã bỏ (2026-11-05, gộp vào `code-reviewer`; cổng dời sang trước PR 2026-11-10).

## Quy trình
1. **Phạm vi:** diff nào? Người dùng đã chỉ định → bắt đầu luôn.
2. **5 chiều:**
   - **Security** — secret, log lộ dữ liệu, truy cập dữ liệu tham số hoá, phân quyền đúng Spring Security.
   - **Performance** — ngưỡng số truy vấn `docs/agent/non-functional-standards.md`, N+1, phân trang, I/O trong vòng lặp.
   - **Correctness** — null/biên, lỗi, transaction, test đã chạy PASS.
   - **Style** — convention Java / Spring Boot, ranh giới module.
   - **Reuse** — Reuse Report có chưa (khi tạo thành phần dùng chung), trùng với thứ đã có, logic lặp.
3. **Phân loại:** CRITICAL (security hole, mất dữ liệu, logic sai) · MAJOR (hiệu năng, bảo trì) · MINOR (style).
4. **Đề xuất sửa:** mỗi CRITICAL/MAJOR kèm đoạn code gợi ý + `file:line`.

## Liên kết
- Cổng review trước PR (nhánh có code) → agent `code-reviewer`
